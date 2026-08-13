package soys.soyshttpovermc.gateway;

import org.bukkit.configuration.ConfigurationSection;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

/**
 * TLS 上下文工厂：为 25564 就地 TLS 升级提供服务端 SSLEngine（每个连接一个，线程安全）。
 *
 * <p>证书来源优先级（config gateway.https.*）：
 * <ol>
 *   <li>{@code keystore}：PKCS12 keystore 路径（keytool 默认导出格式）；</li>
 *   <li>{@code cert} + {@code key}：PEM 证书链 + PKCS8 私钥（openssl pkcs8 -topk8 -nocrypt 导出）；</li>
 *   <li>全部留空：用 JDK keytool 本地自动生成自签证书（纯离线，无需联网）。</li>
 * </ol>
 */
public class TlsContextFactory {

    private final Logger log;
    private final File dataFolder;
    private final ConfigurationSection https;
    private SSLContext sslContext;
    private String minTls = "TLSv1.2";

    public TlsContextFactory(Logger log, File dataFolder, ConfigurationSection https) {
        this.log = log;
        this.dataFolder = dataFolder;
        this.https = https;
    }

    public void init() throws Exception {
        minTls = https.getString("min-tls", "TLSv1.2");
        String keystore = https.getString("keystore", "");
        String cert = https.getString("cert", "");
        String key = https.getString("key", "");
        String storePass = https.getString("keystore-pass", "");
        String keyPass = https.getString("key-pass", storePass);

        KeyManagerFactory kmf;
        if (!keystore.trim().isEmpty()) {
            kmf = loadKeyStore(new File(keystore), storePass, keyPass);
        } else if (!cert.trim().isEmpty() && !key.trim().isEmpty()) {
            kmf = loadPem(new File(cert), new File(key), keyPass);
        } else {
            kmf = generateSelfSigned();
        }
        try {
            sslContext = SSLContext.getInstance(minTls);
        } catch (Exception e) {
            log.warning("[HTTP-Over-MC] 不支持 min-tls=" + minTls + "，回退 TLSv1.2: " + e.getMessage());
            minTls = "TLSv1.2";
            sslContext = SSLContext.getInstance(minTls);
        }
        sslContext.init(kmf.getKeyManagers(), null, null);
        log.info("[HTTP-Over-MC] TLS 上下文就绪 (min=" + minTls + ", 服务端模式)");
    }

    /** 每个新 TLS 连接调用一次，返回已配置为服务端模式的新引擎（线程安全）。 */
    public SSLEngine newServerEngine() {
        SSLEngine e = sslContext.createSSLEngine();
        e.setUseClientMode(false);
        e.setEnabledProtocols(new String[]{minTls});
        return e;
    }

    // ===== 证书源 1：PKCS12 keystore =====
    private KeyManagerFactory loadKeyStore(File f, String storePass, String keyPass) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(f)) {
            ks.load(in, storePass.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyPass.toCharArray());
        log.info("[HTTP-Over-MC] 已加载 keystore: " + f.getAbsolutePath());
        return kmf;
    }

    // ===== 证书源 2：PEM（X509 证书链 + PKCS8 私钥） =====
    private KeyManagerFactory loadPem(File certFile, File keyFile, String keyPass) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<Certificate> chain = new ArrayList<>();
        try (InputStream in = new FileInputStream(certFile)) {
            for (Certificate c : cf.generateCertificates(in)) chain.add(c);
        }
        if (chain.isEmpty()) throw new IllegalArgumentException("PEM 证书为空: " + certFile);
        PrivateKey priv = parsePrivateKey(readAll(keyFile), keyFile);
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        ks.setKeyEntry("soys", priv, keyPass.toCharArray(), chain.toArray(new Certificate[0]));
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyPass.toCharArray());
        log.info("[HTTP-Over-MC] 已加载 PEM: cert=" + certFile + " key=" + keyFile);
        return kmf;
    }

    private static PrivateKey parsePrivateKey(String pem, File keyFile) throws Exception {
        String body = pem.replaceAll("-----[A-Z ]*-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(body);
        String algo = (pem.contains("EC PRIVATE KEY") || pem.contains("BEGIN EC")) ? "EC" : "RSA";
        try {
            return KeyFactory.getInstance(algo).generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("私钥解析失败（要求 PKCS8，可用 "
                    + "openssl pkcs8 -topk8 -nocrypt -in key.pem -out key.p8 转换）: " + keyFile, e);
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
            cmd.add("-alias"); cmd.add("soyshttp");
            cmd.add("-keyalg"); cmd.add("RSA");
            cmd.add("-keysize"); cmd.add("2048");
            cmd.add("-validity"); cmd.add("3650");
            cmd.add("-dname"); cmd.add("CN=" + host);
            cmd.add("-ext"); cmd.add("SAN=" + san);
            cmd.add("-storetype"); cmd.add("PKCS12");
            cmd.add("-keystore"); cmd.add(p12.getAbsolutePath());
            cmd.add("-storepass"); cmd.add(pass);
            cmd.add("-keypass"); cmd.add(pass);
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
                throw new IllegalStateException("keytool 自签失败(rc=" + rc + "): " + out);
            }
            log.info("[HTTP-Over-MC] 已自动生成自签证书: " + p12.getAbsolutePath() + " (CN=" + host + ", SAN=" + san + ")");
        }
        return loadKeyStore(p12, pass, pass);
    }

    private static String keytoolPath() {
        String home = System.getProperty("java.home");
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        return home + File.separator + "bin" + File.separator + (win ? "keytool.exe" : "keytool");
    }
}
