package soys.soyshttpovermc.proxy;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

/** 加载插件数据目录下的 proxy.yml（首次运行写出默认配置）。 */
public class ProxyConfig {

    private boolean enabled;
    private String homeServer;
    private String webRoot;
    private int pipeTimeoutMs;
    private int pipeConcurrency;
    private int pipeReleaseDelayMs;
    private String tlsCert;
    private String tlsKey;
    // 反向代理后端连接调优：每条客户端连接新建一条后端 TLS 连接，串行最小间隔建连消除 spike-drop。
    private long poolAcquireMs;        // 后端 TLS 建连超时(ms)
    private long connectionSpacingMs;  // 当前生效：并发建连串行最小间隔(ms)

    public static ProxyConfig load(Plugin plugin) {
        File data = plugin.getDataFolder();
        if (!data.exists()) data.mkdirs();
        File cfgFile = new File(data, "proxy.yml");
        if (!cfgFile.exists()) {
            try (InputStream in = plugin.getResourceAsStream("proxy.yml")) {
                if (in != null) Files.copy(in, cfgFile.toPath());
            } catch (Exception e) {
                plugin.getLogger().warning("[SOYS-Proxy] 写出默认 proxy.yml 失败: " + e);
            }
        }
        Configuration cfg;
        try {
            cfg = ConfigurationProvider.getProvider(YamlConfiguration.class).load(cfgFile);
        } catch (Exception e) {
            plugin.getLogger().warning("[SOYS-Proxy] 读取 proxy.yml 失败，使用默认: " + e);
            cfg = new Configuration();
        }
        ProxyConfig c = new ProxyConfig();
        c.enabled = cfg.getBoolean("enabled", false);
        c.homeServer = cfg.getString("home-server", "self");
        c.webRoot = cfg.getString("web-root", "web");
        c.pipeTimeoutMs = cfg.getInt("pipe-timeout-ms", 30000);
        c.pipeConcurrency = cfg.getInt("pipe-concurrency", 4);
        c.pipeReleaseDelayMs = cfg.getInt("pipe-release-delay-ms", 250);
        c.tlsCert = cfg.getString("tls-cert", "");
        c.tlsKey = cfg.getString("tls-key", "");
        c.poolAcquireMs = cfg.getLong("pool-acquire-ms", 5000);
        c.connectionSpacingMs = cfg.getLong("connection-spacing-ms", 50);
        return c;
    }

    public boolean isEnabled() { return enabled; }
    public String getHomeServer() { return homeServer; }
    public String getWebRoot() { return webRoot; }
    public int getPipeTimeoutMs() { return pipeTimeoutMs; }
    public int getPipeConcurrency() { return pipeConcurrency; }
    public int getPipeReleaseDelayMs() { return pipeReleaseDelayMs; }
    public String getTlsCert() { return tlsCert; }
    public String getTlsKey() { return tlsKey; }
    public long getPoolAcquireMs() { return poolAcquireMs; }
    public long getConnectionSpacingMs() { return connectionSpacingMs; }
}
