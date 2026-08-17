package soys.soyshttpovermc.gateway.policy.auth.issuer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 轻量 JWT（HS256）编解码器：零第三方依赖，纯 JDK 实现（Java 8+）。
 *
 * <p>结构：{@code <prefix>header.payload.signature}（header/payload/signature 均 base64url 无填充）。
 * <ul>
 *   <li>header：{@code {"alg":"HS256","typ":"JWT"}}；</li>
 *   <li>payload：{@code {"sub":玩家名,"mode":"ONLINE|OFFLINE","exp":过期毫秒,"iat":签发毫秒,"jti":随机ID}}；</li>
 *   <li>signature：HMAC-SHA256(header.payload, secret)，常量时间比较防时序攻击。</li>
 * </ul>
 * 校验失败（签名不符 / 过期 / 格式错误）返回 null；调用方（{@link SessionTokenIssuer}）
 * 负责用 jti 维护"已注销"黑名单实现退出登录。
 */
public final class JwtCodec {

    private JwtCodec() {
    }

    /**
     * 签发 JWT。
     * @param secret     HMAC 密钥（建议 ≥32 字节）
     * @param subject    主体（玩家名）
     * @param mode       登录模式（ONLINE / OFFLINE）
     * @param ttlMillis  有效期毫秒
     * @param jti        令牌唯一 ID（退出登录黑名单用）
     * @param prefix     令牌前缀（如 "st_"；可空）
     */
    public static String create(byte[] secret, String subject, String mode,
                                long ttlMillis, String jti, String prefix) {
        return create(secret, subject, mode, ttlMillis, jti, prefix, false);
    }

    /**
     * 签发 JWT。
     * @param secret     HMAC 密钥（建议 ≥32 字节）
     * @param subject    主体（玩家名）
     * @param mode       登录模式（ONLINE / OFFLINE）
     * @param ttlMillis  有效期毫秒
     * @param jti        令牌唯一 ID（退出登录黑名单用）
     * @param prefix     令牌前缀（如 "st_"；可空）
     * @param admin      是否服主最高权限 key（adm 标记，仅 /soyshttp key 命令颁发）
     */
    public static String create(byte[] secret, String subject, String mode,
                                long ttlMillis, String jti, String prefix, boolean admin) {
        return create(secret, subject, mode, ttlMillis, jti, prefix, admin, null);
    }

    /**
     * 签发 JWT（支持自定义 claims）。
     * @param claims 附加到 payload 的自定义键值（键限 [a-zA-Z0-9_-]，值限长度 256；可空）。
     *               保留键 sub/mode/exp/iat/jti/adm 不可用。供业务方携带自定义声明（权限范围/标签等）。
     */
    public static String create(byte[] secret, String subject, String mode,
                                long ttlMillis, String jti, String prefix, boolean admin,
                                java.util.Map<String, String> claims) {
        long now = System.currentTimeMillis();
        StringBuilder payload = new StringBuilder();
        payload.append("{\"sub\":\"").append(esc(subject)).append("\",\"mode\":\"").append(esc(mode))
                .append("\",\"exp\":").append(now + ttlMillis).append(",\"iat\":").append(now)
                .append(",\"jti\":\"").append(esc(jti)).append('"')
                .append(admin ? ",\"adm\":1" : "");
        if (claims != null) {
            for (java.util.Map.Entry<String, String> e : claims.entrySet()) {
                String k = e.getKey();
                if (k == null || !k.matches("[A-Za-z0-9_-]{1,32}")) continue;
                if ("sub".equals(k) || "mode".equals(k) || "jti".equals(k)
                        || "exp".equals(k) || "iat".equals(k) || "adm".equals(k)) continue;
                String v = e.getValue();
                if (v == null || v.length() > 256) continue;
                payload.append(",\"").append(k).append("\":\"").append(esc(v)).append('"');
            }
        }
        payload.append('}');
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String p = b64(payload.toString());
        String signing = header + "." + p;
        String sig = b64(hmac(secret, signing));
        return (prefix == null ? "" : prefix) + signing + "." + sig;
    }

    /**
     * 解析并验签 JWT（含过期检查）；无效返回 null。
     * @param secret HMAC 密钥（与签发时一致）
     * @param token  完整令牌（可带前缀，如 st_eyJ...）
     */
    public static Payload parse(byte[] secret, String token) {
        return parse(secret, token, null, 0);
    }

    /**
     * 解析并验签 JWT（含过期检查）；无效返回 null。
     * @param prefix 签发时使用的前缀（如 "st_"）；token 以该前缀开头时先剥离再验签。可空。
     */
    public static Payload parse(byte[] secret, String token, String prefix) {
        return parse(secret, token, prefix, 0);
    }

    /**
     * 解析并验签 JWT（含过期检查与时钟容差）；无效返回 null。
     * @param prefix          签发时使用的前缀（如 "st_"）；token 以该前缀开头时先剥离再验签。可空。
     * @param clockSkewMillis 时钟容差（毫秒）：跨服校验时容忍各服时钟偏移，过期判定为
     *                        {@code exp + skew < now}（防误拒）；签发时间在 {@code iat > now + skew}
     *                        视为无效（防时钟严重回拨的伪造/错乱）。
     */
    public static Payload parse(byte[] secret, String token, String prefix, long clockSkewMillis) {
        if (token == null || secret == null || secret.length == 0) return null;
        if (prefix != null && !prefix.isEmpty() && token.startsWith(prefix)) {
            token = token.substring(prefix.length());
        }
        int dot1 = token.indexOf('.');
        if (dot1 < 0) return null;
        int dot2 = token.indexOf('.', dot1 + 1);
        if (dot2 < 0) return null;
        String headerB64 = token.substring(0, dot1);
        String payloadB64 = token.substring(dot1 + 1, dot2);
        String sigB64 = token.substring(dot2 + 1);
        String signing = headerB64 + "." + payloadB64;

        // 验签（常量时间比较）
        byte[] expect = hmac(secret, signing);
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(sigB64);
        } catch (Exception e) {
            return null;
        }
        if (!MessageDigest.isEqual(expect, actual)) return null;

        // 解析 payload
        byte[] pj;
        try {
            pj = Base64.getUrlDecoder().decode(payloadB64);
        } catch (Exception e) {
            return null;
        }
        String json = new String(pj, StandardCharsets.UTF_8);
        Payload p = parsePayload(json);
        if (p == null) return null;
        long skew = Math.max(0, clockSkewMillis);
        long now = System.currentTimeMillis();
        if (p.exp + skew < now) return null;               // 过期（含容差）
        if (p.iat > 0 && p.iat > now + skew) return null;  // 签发时间在未来（超容差=时钟严重回拨/伪造）
        return p;
    }

    /** JWT payload 解析结果。 */
    public static final class Payload {
        public String subject;
        public String mode;
        public long exp;
        public long iat;
        public String jti;
        /** 服主最高权限 key（/soyshttp key 命令颁发，adm=1）。 */
        public boolean adm;
        /** 自定义 claims（签发时附加的非保留键值，字符串形式）。 */
        public final java.util.Map<String, String> claims = new java.util.HashMap<>();
    }

    // ===== 内部 =====

    /** payload 保留键（不视为自定义 claims）。 */
    private static final java.util.Set<String> RESERVED = new java.util.HashSet<>(
            java.util.Arrays.asList("sub", "mode", "exp", "iat", "jti", "adm"));
    /** 提取自定义 claims 用的顶层键值对匹配（字符串值）。 */
    private static final java.util.regex.Pattern CLAIM_PATTERN =
            java.util.regex.Pattern.compile("\"([A-Za-z0-9_\\-]+)\":\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static Payload parsePayload(String json) {
        if (json == null) return null;
        Payload p = new Payload();
        p.subject = str(json, "sub");
        p.mode = str(json, "mode");
        p.jti = str(json, "jti");
        p.exp = lng(json, "exp");
        p.iat = lng(json, "iat");
        p.adm = lng(json, "adm") == 1;
        if (p.subject == null || p.exp <= 0) return null;
        // 提取自定义 claims（跳过保留键）
        java.util.regex.Matcher m = CLAIM_PATTERN.matcher(json);
        while (m.find()) {
            String k = m.group(1);
            if (RESERVED.contains(k)) continue;
            String v = m.group(2).replace("\\\"", "\"").replace("\\\\", "\\");
            p.claims.put(k, v);
        }
        return p;
    }

    private static String str(String json, String key) {
        String k = "\"" + key + "\":\"";
        int i = json.indexOf(k);
        if (i < 0) return null;
        int start = i + k.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        String v = json.substring(start, end);
        return v.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static long lng(String json, String key) {
        String k = "\"" + key + "\":";
        int i = json.indexOf(k);
        if (i < 0) return 0;
        int start = i + k.length();
        int end = start;
        while (end < json.length() && json.charAt(end) >= '0' && json.charAt(end) <= '9') end++;
        if (end == start) return 0;
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static byte[] hmac(byte[] secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 不可用", e);
        }
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String b64(String s) {
        return b64(s.getBytes(StandardCharsets.UTF_8));
    }
}
