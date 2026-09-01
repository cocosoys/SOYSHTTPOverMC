package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.util;

import com.github.cocosoys.mc.soyshttpovermc.web.gateway.Credential;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialIssuer;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialPresentation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/**
 * 鉴权工具类：集中提供凭证头解析、Cookie 解析、路径匹配、常量时间比较与令牌生成，
 * 供 AuthPolicy / SessionTokenIssuer / 注解式 API 框架（权限判定）共用。
 */
public final class AuthUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private AuthUtils() {
    }

    // ===== 凭证头解析 =====

    /**
     * 从 Authorization 头提取 Bearer token；不是 Bearer 或无值返回 null。
     */
    public static String extractBearer(String authorization) {
        if (authorization == null) return null;
        String t = authorization.trim();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = t.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    /**
     * 从 Authorization 头提取 Basic 用户名/密码；返回 [user, pass]，非 Basic 返回 null。
     */
    public static String[] extractBasic(String authorization) {
        if (authorization == null) return null;
        String t = authorization.trim();
        if (!t.regionMatches(true, 0, "Basic ", 0, 6)) return null;
        try {
            String decoded = new String(Base64.getDecoder().decode(t.substring(6).trim()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon >= 0) {
                return new String[]{decoded.substring(0, colon), decoded.substring(colon + 1)};
            }
            return new String[]{decoded, ""};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 Cookie 头为 k→v 映射（大小写保留，取值 trim）。
     */
    public static Map<String, String> parseCookies(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) return Collections.emptyMap();
        Map<String, String> cookies = new HashMap<>();
        for (String part : cookieHeader.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                String k = part.substring(0, eq).trim();
                String v = part.substring(eq + 1).trim();
                if (!k.isEmpty()) cookies.put(k, v);
            }
        }
        return cookies;
    }

    /**
     * 从请求头统一提取凭证表示（X-API-Key / Bearer / Basic / Cookie 四来源）。
     * AuthPolicy 与注解式 API 框架共用。
     */
    public static CredentialPresentation extractPresentation(Map<String, String> headers,
                                                             String apiKeyHeader,
                                                             boolean acceptHeader,
                                                             boolean acceptBearer,
                                                             boolean acceptBasic,
                                                             boolean acceptCookie) {
        String apiKey = acceptHeader ? getHeader(headers, apiKeyHeader) : null;
        if (apiKey != null && apiKey.isEmpty()) apiKey = null;

        String bearer = null;
        String basicUser = null;
        String basicPass = null;
        String authorization = getHeader(headers, "Authorization");
        if (acceptBearer || acceptBasic) {
            String b = acceptBearer ? extractBearer(authorization) : null;
            if (b != null) {
                bearer = b;
            } else if (acceptBasic) {
                String[] basic = extractBasic(authorization);
                if (basic != null) {
                    basicUser = basic[0];
                    basicPass = basic[1];
                }
            }
        }

        Map<String, String> cookies = acceptCookie ? parseCookies(getHeader(headers, "Cookie"))
                : Collections.<String, String>emptyMap();
        return new CredentialPresentation(apiKey, bearer, basicUser, basicPass, cookies);
    }

    /**
     * 解析请求携带的凭证为 {@link Credential}（权限控制抽象载体）。
     * 复用与 AuthPolicy 一致的校验逻辑：静态 keys（常量时间比较）+ 启用的颁发器。
     * 有效返回非 null（含脱敏 subject 与 source），无效返回 null。
     * 这是「带有效 X-API-Key 可旁路 HTTPS 强制升级」与「未来按权限细分」共用的唯一校验入口。
     */
    public static Credential resolveCredential(Map<String, String> headers,
                                               String apiKeyHeader,
                                               boolean acceptHeader,
                                               boolean acceptBearer,
                                               boolean acceptBasic,
                                               boolean acceptCookie,
                                               java.util.List<CredentialIssuer> issuers,
                                               Set<String> keys) {
        CredentialPresentation p = extractPresentation(headers, apiKeyHeader,
                acceptHeader, acceptBearer, acceptBasic, acceptCookie);
        // 1) 静态 key：X-API-Key 头 / Bearer / Basic 用户名=key
        if (acceptHeader && matchAnyKey(keys, p.getApiKey())) {
            return new Credential("api-key:" + fingerprint(p.getApiKey()), "api-key");
        }
        if (acceptBearer && matchAnyKey(keys, p.getBearer())) {
            return new Credential("bearer:" + fingerprint(p.getBearer()), "bearer");
        }
        if (acceptBasic && matchAnyKey(keys, p.getBasicUser())) {
            return new Credential("basic:" + fingerprint(p.getBasicUser()), "basic");
        }
        // 2) 启用的颁发器校验（Bearer / X-API-Key / Cookie 均可识别）
        if (issuers != null) {
            for (CredentialIssuer issuer : issuers) {
                if (!issuer.isEnabled()) continue;
                try {
                    if (issuer.validate(p)) {
                        return new Credential("issuer:" + issuer.name(), "issuer:" + issuer.name());
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    /**
     * 常量时间匹配任一静态 key
     */
    private static boolean matchAnyKey(Set<String> keys, String presented) {
        if (keys == null || presented == null) return false;
        for (String k : keys) {
            if (constantTimeEquals(k, presented)) return true;
        }
        return false;
    }

    /**
     * 密钥指纹（SHA-256 前 8 位），用于 subject 脱敏，避免日志泄露原始密钥。
     */
    private static String fingerprint(String v) {
        if (v == null) return "?";
        String h = sha256Hex(v);
        return h.length() > 8 ? h.substring(0, 8) : h;
    }

    /**
     * 大小写不敏感读取请求头
     */
    public static String getHeader(Map<String, String> headers, String name) {
        if (headers == null || name == null) return null;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    // ===== 路径匹配 =====

    /**
     * 路径匹配：* 全匹配；/api/* 前缀匹配；/api/ping 精确匹配（其子路径 /api/ping/... 也匹配）；
     * /api/ 目录前缀匹配（匹配 /api/...）。
     */
    public static boolean matchesPath(String path, String pattern) {
        if (pattern == null || pattern.isEmpty()) return true;
        if (pattern.equals("*")) return true;
        if (path == null) return false;
        if (pattern.endsWith("/*")) {
            return path.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        if (pattern.endsWith("/")) {
            return path.startsWith(pattern);
        }
        // 无通配且无尾斜杠：先精确匹配，再按目录前缀兜底（/api → /api 或 /api/...）
        return path.equals(pattern) || path.startsWith(pattern + "/");
    }

    // ===== 安全比较 / 令牌 =====

    /**
     * 常量时间字符串比较，防时序侧信道（用于 key/token 比对）。
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        int diff = a.length() ^ b.length();
        int len = Math.max(a.length(), b.length());
        for (int i = 0; i < len; i++) {
            char ca = i < a.length() ? a.charAt(i) : 0;
            char cb = i < b.length() ? b.charAt(i) : 0;
            diff |= ca ^ cb;
        }
        return diff == 0;
    }

    /**
     * 生成随机 hex 令牌，如 generateToken("st_", 24)。
     */
    public static String generateToken(String prefix, int byteCount) {
        byte[] b = new byte[Math.max(4, byteCount)];
        RANDOM.nextBytes(b);
        StringBuilder sb = new StringBuilder(prefix == null ? "" : prefix);
        for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }

    /**
     * SHA-256 十六进制摘要（用于日志脱敏/存指纹，不用于密码存储安全场景）。
     */
    public static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x & 0xFF));
            return sb.toString();
        } catch (Exception e) {
            return s;
        }
    }
}
