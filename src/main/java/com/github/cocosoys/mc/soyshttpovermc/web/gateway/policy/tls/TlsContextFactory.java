package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.tls;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import lombok.CustomLog;
import org.bukkit.configuration.ConfigurationSection;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/**
 * TLS 上下文工厂：为 MC 端口就地 TLS 升级提供服务端 SSLEngine（每个连接一个，线程安全）。
 *
 * <p>证书来源优先级（config gateway.https.*）：
 * <ol>
 *   <li>{@code keystore}：PKCS12 keystore 路径（keytool 默认导出格式）；</li>
 *   <li>{@code cert} + {@code key}：PEM 证书链 + PKCS8 私钥（openssl pkcs8 -topk8 -nocrypt 导出）；</li>
 *   <li>全部留空：用 JDK keytool 本地自动生成自签证书（纯离线，无需联网）。</li>
 * </ol>
 */
@CustomLog
public class TlsContextFactory {

    private final File dataFolder;
    private final ConfigurationSection https;
    private SSLContext sslContext;
    private final List<String> enabledProtocols = new ArrayList<>();

    public TlsContextFactory(File dataFolder, ConfigurationSection https) {
        this.dataFolder = dataFolder;
        this.https = https;
    }

    public void init() throws Exception {
        String keystore = https.getString("keystore", "");
        String cert = https.getString("cert", "");
        String key = https.getString("key", "");
        String storePass = https.getString("keystore-pass", "");
        String keyPass = https.getString("key-pass", storePass);

        // 协议范围：优先 enabled-protocols 列表；否则兼容旧 min-tls（作为最低允许协议过滤）；二者皆无则全开 TLS1.0~1.3
        List<String> conf = https.getStringList("enabled-protocols");
        enabledProtocols.clear();
        enabledProtocols.addAll(conf.isEmpty() ? defaultProtocols(https.getString("min-tls", "")) : conf);

        KeyManagerFactory kmf;
        if (!keystore.trim().isEmpty()) {
            kmf = loadKeyStore(resolvePath(keystore), storePass, keyPass);
        } else if (!cert.trim().isEmpty() && !key.trim().isEmpty()) {
            kmf = loadPem(resolvePath(cert), resolvePath(key), keyPass);
        } else {
            kmf = generateSelfSigned();
        }
        try {
            // 使用通用 TLS 上下文（涵盖所有已注册的 TLS 协议），由 newServerEngine 按需筛选启用范围
            sslContext = SSLContext.getInstance("TLS");
        } catch (Exception e) {
            log.warnT("log.tls.ctx-fail", "创建 TLS 上下文失败: {0}", e.getMessage());
            throw e;
        }
        sslContext.init(kmf.getKeyManagers(), null, null);
        log.infoT("log.tls.context-ready", "TLS 上下文就绪 (服务端模式, protocols={0})", String.join(",", enabledProtocols));
    }

    /**
     * 每个新 TLS 连接调用一次，返回已配置为服务端模式的新引擎（线程安全）。
     */
    public SSLEngine newServerEngine() {
        SSLEngine e = sslContext.createSSLEngine();
        e.setUseClientMode(false);
        // 仅启用 JVM 真正支持的协议；开放多协议协商（TLS1.0~TLS1.3），兼容旧客户端握手
        Set<String> supported = new HashSet<>(Arrays.asList(e.getSupportedProtocols()));
        List<String> enabled = new ArrayList<>(enabledProtocols.size());
        for (String p : enabledProtocols) {
            if (supported.contains(p)) {
                enabled.add(p);
            }
        }
        if (!enabled.isEmpty()) {
            e.setEnabledProtocols(enabled.toArray(new String[enabled.size()]));
        }
        return e;
    }

    /**
     * 默认协议范围：自 minTls 起开放全部更高版本的 TLS；minTls 为空/未知则全开 TLS1.0~1.3。
     */
    private static List<String> defaultProtocols(String minTls) {
        String[] all = {"TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1"};
        int floor = protocolIndex(minTls);
        List<String> out = new ArrayList<>(all.length);
        for (String p : all) {
            if (protocolIndex(p) >= floor) {
                out.add(p);
            }
        }
        return out;
    }

    private static int protocolIndex(String p) {
        if (p == null) return 1;
        switch (p) {
            case "TLSv1.3":
                return 4;
            case "TLSv1.2":
                return 3;
            case "TLSv1.1":
                return 2;
            case "TLSv1":
                return 1;
            default:
                return 1; // 未知协议 → 一律放开
        }
    }

    // ===== 证书源 1：PKCS12 keystore =====
    private KeyManagerFactory loadKeyStore(File f, String storePass, String keyPass) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(f)) {
            ks.load(in, storePass.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyPass.toCharArray());
        log.infoT("log.tls.keystore-loaded", "已加载 keystore: {0}", f.getAbsolutePath());
        return kmf;
    }

    // ===== 证书源 2：PEM（X509 证书链 + PKCS8 私钥） =====
    private KeyManagerFactory loadPem(File certFile, File keyFile, String keyPass) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<Certificate> chain = new ArrayList<>();
        try (InputStream in = new FileInputStream(certFile)) {
            for (Certificate c : cf.generateCertificates(in)) chain.add(c);
        }
        if (chain.isEmpty())
            throw new IllegalArgumentException(I18n.t("exception.tls.pem-empty", "PEM 证书为空: {0}", certFile));
        PrivateKey priv = parsePrivateKey(readAll(keyFile), keyFile);
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        ks.setKeyEntry("soys", priv, keyPass.toCharArray(), chain.toArray(new Certificate[0]));
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyPass.toCharArray());
        log.infoT("log.tls.pem-loaded", "已加载 PEM: cert={0} key={1}", certFile, keyFile);
        return kmf;
    }

    private static PrivateKey parsePrivateKey(String pem, File keyFile) throws Exception {
        String body = pem.replaceAll("-----[A-Z ]*-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(body);
        String algo = (pem.contains("EC PRIVATE KEY") || pem.contains("BEGIN EC")) ? "EC" : "RSA";
        try {
            return KeyFactory.getInstance(algo).generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException(I18n.t("exception.tls.private-key-parse-fail", "私钥解析失败（要求 PKCS8，可用 openssl pkcs8 -topk8 -nocrypt -in key.pem -out key.p8 转换）: {0}", keyFile), e);
        }
    }

    private static String readAll(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.US_ASCII))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    // ===== 证书源 3：keytool 本地自动自签（纯离线） =====
    private KeyManagerFactory generateSelfSigned() throws Exception {
        String host = https.getString("host", "");
        if (host.trim().isEmpty()) host = "127.0.0.1";
        String safe = host.replaceAll("[^A-Za-z0-9.-]", "_");
        File p12 = new File(dataFolder, "selfsigned-" + safe + ".p12");
        String pass = "soyshttp";
        if (!p12.isFile()) {
            String san = host.matches("\\d{1,3}(\\.\\d{1,3}){3}") ? "ip:" + host : "dns:" + host;
            if (!"127.0.0.1".equals(host)) san += ",ip:127.0.0.1";
            List<String> cmd = new ArrayList<>();
            cmd.add(keytoolPath());
            cmd.add("-genkeypair");
            cmd.add("-alias");
            cmd.add("soyshttp");
            cmd.add("-keyalg");
            cmd.add("RSA");
            cmd.add("-keysize");
            cmd.add("2048");
            cmd.add("-validity");
            cmd.add("3650");
            cmd.add("-dname");
            cmd.add("CN=" + host);
            cmd.add("-ext");
            cmd.add("SAN=" + san);
            cmd.add("-storetype");
            cmd.add("PKCS12");
            cmd.add("-keystore");
            cmd.add(p12.getAbsolutePath());
            cmd.add("-storepass");
            cmd.add(pass);
            cmd.add("-keypass");
            cmd.add(pass);
            cmd.add("-noprompt");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder out = new StringBuilder();
            try (InputStream in = proc.getInputStream()) {
                byte[] buf = new byte[1024];
                int n;
                while ((n = in.read(buf)) > 0) out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            int rc = proc.waitFor();
            if (rc != 0 || !p12.isFile()) {
                throw new IllegalStateException(I18n.t("exception.tls.keytool-fail", "keytool 自签失败(rc={0}): {1}", rc, out));
            }
            log.infoT("log.tls.selfsigned-generated", "已自动生成自签证书: {0} (CN={1}, SAN={2})", p12.getAbsolutePath(), host, san);
        }
        return loadKeyStore(p12, pass, pass);
    }

    /**
     * 解析证书/私钥路径：绝对路径直接用；相对路径相对于插件数据目录（与自签证书生成位置一致）。
     */
    private File resolvePath(String path) {
        File f = new File(path);
        return f.isAbsolute() ? f : new File(dataFolder, path);
    }

    private static String keytoolPath() {
        String home = System.getProperty("java.home");
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        return home + File.separator + "bin" + File.separator + (win ? "keytool.exe" : "keytool");
    }
}
