package soys.soyshttpovermc.proxy;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * home-server=self 时代理自身终止 TLS 所需的 {@link SslContext} 提供者（进程内缓存，懒加载）。
 * <ul>
 *   <li>配置了 {@code tls-cert}/{@code tls-key}（相对路径按插件数据目录解析）→ 用该证书；</li>
 *   <li>未配置 → 首次自动生成自签证书并<b>持久化</b>到数据目录（{@code self-cert.pem}/{@code self-key.pem}），
 *       重启后复用，避免每次重启证书指纹变化。</li>
 * </ul>
 */
public final class TlsContextProvider {

    private static volatile SslContext cached;

    private TlsContextProvider() {
    }

    public static synchronized SslContext get(Plugin plugin, ProxyConfig config) throws Exception {
        if (cached != null) return cached;
        File data = plugin.getDataFolder();
        if (!data.exists()) data.mkdirs();

        File cert;
        File key;
        String c = config.getTlsCert();
        String k = config.getTlsKey();
        if (c != null && !c.isEmpty() && k != null && !k.isEmpty()) {
            cert = resolve(data, c);
            key = resolve(data, k);
            if (!cert.isFile() || !key.isFile()) {
                throw new IllegalStateException("tls-cert/tls-key 指定的文件不存在: " + cert + " / " + key);
            }
            plugin.getLogger().info("[SOYS-Proxy] self 模式 TLS 使用配置证书: " + cert.getPath());
        } else {
            cert = new File(data, "self-cert.pem");
            key = new File(data, "self-key.pem");
            if (!cert.isFile() || !key.isFile()) {
                SelfSignedCertificate ssc = new SelfSignedCertificate("localhost");
                Files.copy(ssc.certificate().toPath(), cert.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(ssc.privateKey().toPath(), key.toPath(), StandardCopyOption.REPLACE_EXISTING);
                ssc.delete();
                plugin.getLogger().info("[SOYS-Proxy] self 模式 TLS 已生成自签证书并持久化: " + cert.getName()
                        + " / " + key.getName() + "（可在 proxy.yml 配置 tls-cert/tls-key 换成正式证书）");
            }
        }
        cached = SslContextBuilder.forServer(cert, key).build();
        return cached;
    }

    private static File resolve(File data, String p) {
        File f = new File(p);
        return f.isAbsolute() ? f : new File(data, p);
    }
}
