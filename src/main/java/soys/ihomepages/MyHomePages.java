package soys.ihomepages;
import lombok.CustomLog;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.api.SoysHttpOverMcApi;
import soys.ihomepages.api.HomeApi;
import soys.ihomepages.api.impl.HomeApiImpl;
import soys.ihomepages.config.IHomeConfigExporter;
import soys.ihomepages.config.JsonHomeConfigExporter;
import soys.ihomepages.config.MainConfigReader;
import soys.ihomepages.config.YamlHomeConfigSource;
import soys.ihomepages.homepage.HomepageRegistry;
import soys.ihomepages.homepage.HomepageState;
import soys.ihomepages.command.HomepageSubCommand;
import soys.ihomepages.spring.controller.HomeApiController;
import soys.ihomepages.spring.impl.GiftServiceImpl;
import soys.ihomepages.spring.service.IGiftService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 自定义主页插件（内嵌于 SOYSHTTPOverMC，未来可整体抽离为独立插件）。
 *
 * <p>设计极简：本插件只<b>记录不同位置的页面</b>（相对/绝对路径或网络 URL），并把当前主页的位置
 * 写入 SOYSHTTPOverMC 的 {@code config.yml} 的 {@code web.home}，由框架的 {@code web.home} 机制伺服；
 * <b>不持有/不存储网页字节内容</b>，也不把 HTML 塞进 {@code GET /} 路由。</p>
 *
 * <p>一键入口 {@link #register(JavaPlugin)}：读逻辑配置 → 读 ihomepage 自身 config.yml 的主页位置映射 →
 * 把当前主页位置应用到 web.home → 注册 /soyshttp homepage 子指令与 /api/homepage/* 接口 → 注册 reload 钩子。
 * 所有与 SOYSHTTPOverMC 的耦合仅限本方法（经门面写 web.home / 注册子指令 / 注册 reload 钩子）。</p>
 */
@CustomLog
public final class MyHomePages {

    private MyHomePages() {
    }

    private static volatile HomeApi homeApi;

    /** 公开首页门面：初始化完成后方可用；未初始化或自定义主页被禁用时为 null。 */
    public static HomeApi getHomeApi() {
        return homeApi;
    }

    /** 一键注册自定义主页。host 即 SOYSHTTPOverMC 本体。 */
    public static void register(JavaPlugin host) {
        if(Bukkit.getPluginManager().getPlugin("SOYSHTTPOverMC")==null){
            log.warnT("mhp.host-missing", "[ihomepages] 未找到 SOYSHTTPOverMC 宿主插件，跳过自定义主页注册");
            return;
        }

        SoysHttpOverMcApi api = HttpOverMcPlugin.getInstance().getApi();

        // 1) 逻辑配置（仅 enabled 开关）
        MainConfigReader cfgReader = new MainConfigReader(host);
        cfgReader.load();
        if (!cfgReader.isEnabled()) {
            homeApi = null;
            log.infoT("mhp.disabled",
                    "[ihomepages] 已在 config.yml 中禁用（enabled: false），跳过注册。");
            return;
        }

        // 2) 从 ihomepage 自身 config.yml 读取主页位置映射 homepage.pages（name -> 相对/绝对路径或 URL）
        HomepageState hpState = new HomepageState(host);
        org.bukkit.configuration.file.YamlConfiguration ihCfg = hpState.loadConfig();
        Map<String, String> specs = new LinkedHashMap<>();
        org.bukkit.configuration.ConfigurationSection pages = ihCfg.getConfigurationSection("homepage.pages");
        if (pages != null) {
            for (String key : pages.getKeys(false)) {
                String v = pages.getString(key);
                if (v != null && !v.trim().isEmpty()) specs.put(key, v.trim());
            }
        }
        if (!specs.containsKey("default")) {
            specs.put("default", "ihomepages/dist/index.html");
        }
        String current = ihCfg.getString("homepage.current", "");
        if (current == null || current.isEmpty() || !specs.containsKey(current)) {
            current = "default";
        }

        // 3) 轻量登记表（仅记录位置，不持有字节）
        HomepageRegistry registry = new HomepageRegistry();
        for (Map.Entry<String, String> e : specs.entrySet()) {
            registry.register(e.getKey(), e.getValue());
        }

        HomeApi apiFacade = new HomeApiImpl((HttpOverMcPlugin) host, registry, hpState);
        homeApi = apiFacade;

        // 3.0) 释放 dist / language 目录到数据目录（供管理/自定义），并优先伺服磁盘副本
        File hpDir = new File(host.getDataFolder(), "ihomepages");
        extractResourceDir(host, "ihomepages/dist", new File(hpDir, "dist"));
        extractResourceDir(host, "ihomepages/language", new File(hpDir, "language"));

        // 3.1) 启动即把当前主页位置写入 web.home 并应用（不触发全量 reload，仅应用运行中的 WebFrontendHandler）
        String curSpec = registry.getSpec(current);
        if (curSpec != null) {
            String value = resolveWebHomeSpec((HttpOverMcPlugin) host, curSpec);
                        host.getConfig().set("web.home", value);
            host.saveConfig();
            HttpOverMcPlugin.getInstance().getFrontendHandler().setHomeSpec(value);
            log.infoT("mhp.apply-home", "[ihomepages] 已应用当前主页到 web.home: {0} -> {1}", current, value);
        }

        // 3.2) 注册 /soyshttp homepage 子指令（宿主 initCommand 之后再注入，命令方可生效）
        api.getExtension().registerSubCommand(new HomepageSubCommand((HttpOverMcPlugin) host, apiFacade));

        // 3.3) reload 钩子：/soyshttp reload 时按 homepage.current 重新应用 web.home（两种自动检测机制之一）
        api.registerReloadHook(() -> {
            String cur = hpState.readCurrent();
            if (cur != null && !cur.isEmpty()) {
                String s = registry.getSpec(cur);
                if (s != null) {
                    HttpOverMcPlugin.getInstance().getFrontendHandler()
                            .setHomeSpec(resolveWebHomeSpec(HttpOverMcPlugin.getInstance(), s));
                }
            }
        });

        // 4) 接口（配置 JSON / 实时数据 / 礼包领取 / 状态查询）—— 与主页_switch 机制无关，保留
        YamlHomeConfigSource home = new YamlHomeConfigSource(host);
        home.load();
        IHomeConfigExporter exporter = new JsonHomeConfigExporter(api.getHttpClient(), host.getDataFolder());
        IGiftService gift = new GiftServiceImpl(cfgReader, home, host);
        HomeApiController controller = new HomeApiController(home, exporter, gift, host);
        api.getApiRegistration().registerController(controller, host);

        log.infoT("mhp.registered",
                "[ihomepages] 已注册自定义主页：主页位置 {0} 个（current={1}），API: /api/homepage/{{config,live,gift/claim,gift/status}",
                specs.size(), current);
    }

    /** 把主页位置描述解析为 web.home 取值：URL/绝对路径原样；相对路径按插件数据目录解析为绝对路径。 */
    public static String resolveWebHomeSpec(HttpOverMcPlugin plugin, String spec) {
        if (spec == null) return "";
        String s = spec.trim();
        if (s.isEmpty()) return "";
        if (s.startsWith("http://") || s.startsWith("https://")) return s;
        File f = new File(s);
        if (f.isAbsolute()) return s;
        return new File(plugin.getDataFolder(), s).getAbsolutePath();
    }

    /** 从插件 jar 资源读取字节（用于伺服 dist/index.html）。 */
    public static byte[] readResource(JavaPlugin plugin, String path) {
        String res = path.startsWith("/") ? path.substring(1) : path;
        try (InputStream in = plugin.getClass().getClassLoader().getResourceAsStream(res)) {
            if (in == null) {
                return new byte[0];
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** 从磁盘文件读取全部字节（优先于 jar 资源，便于运营自定义已释放的首页）。 */
    private static byte[] readFile(File file) {
        if (file == null || !file.isFile()) {
            return new byte[0];
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /**
     * 把 jar 内某个资源目录（含子目录）整棵释放到磁盘目标目录。
     * <p>语义对齐 {@link soys.soyshttpovermc.config.AbstractConfigSource}：仅补齐<b>缺失</b>的文件（不覆盖已存在的用户自定义副本），
     * 使后续插件更新能补充新文件、同时保留运营对 dist/index.html 的手动修改。</p>
     *
     * @param plugin   宿主插件（取其 jar 位置）
     * @param resDir   jar 内资源目录，如 {@code ihomepages/dist}
     * @param outDir   磁盘目标目录，如 {@code <dataFolder>/ihomepages/dist}
     */
    private static void extractResourceDir(JavaPlugin plugin, String resDir, File outDir) {
        File jar = null;
        try {
            URI loc = plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            jar = new File(loc);
        } catch (Exception e) {
            jar = null;
        }
        if (jar == null || !jar.isFile()) {
            log.warnT("log.homepage.extract-jar-missing",
                    "[ihomepages] 无法定位插件 jar，跳过 {0} 目录释放", resDir);
            return;
        }
        String prefix = resDir.endsWith("/") ? resDir : resDir + "/";
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String name = e.getName();
                if (e.isDirectory() || !name.startsWith(prefix)) {
                    continue;
                }
                String rel = name.substring(prefix.length());
                if (rel.isEmpty()) {
                    continue;
                }
                File out = new File(outDir, rel);
                if (out.exists()) {
                    continue; // 已存在则跳过，保留用户自定义
                }
                out.getParentFile().mkdirs();
                try (InputStream in = jf.getInputStream(e)) {
                    Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            log.infoT("log.homepage.extracted",
                    "[ihomepages] 已释放目录 {0} → {1}", resDir, outDir.getAbsolutePath());
        } catch (IOException ex) {
            log.warnT("log.homepage.extract-fail",
                    "[ihomepages] 释放目录 {0} 失败: {1}", resDir, ex.getMessage());
        }
    }
}
