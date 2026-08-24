package soys.ihomepages.config;
import lombok.CustomLog;

import soys.soyshttpovermc.api.HttpClientApi;
import soys.soyshttpovermc.web.MimeTypes;
import soys.ihomepages.spring.entity.GiftCommand;
import soys.ihomepages.spring.entity.GiftConfigEntity;
import soys.ihomepages.spring.entity.GiftItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置导出实现：构建「仅展示字段」的安全 JSON Map。
 * <p>关键安全点：{@code gift.items[].material/amount} 与 {@code gift.commands[].cmd} 属服务端执行参数，
 * <b>绝不</b>进入前端 JSON；前端仅拿到 {@code name}/{@code show}/{@code enable}/{@code icon} 等展示字段。</p>
 *
 * <p>图标处理：支持三种路径类型——网络 URL（通过 {@link HttpClientApi#sendGet} 下载）、
 * 绝对路径、相对路径（相对于插件 data 目录），加载后自动转换为 Base64 Data URI。</p>
 */
@CustomLog
public class JsonHomeConfigExporter implements IHomeConfigExporter {

    private final HttpClientApi httpClient;
    private final File dataFolder;

    public JsonHomeConfigExporter(HttpClientApi httpClient, File dataFolder) {
        this.httpClient = httpClient;
        this.dataFolder = dataFolder;
    }

    @Override
    public Map<String, Object> export(HomeConfigEntity cfg) {
        Map<String, Object> m = new LinkedHashMap<>(cfg.toDisplayMap());
        m.put("gift", safeGift(cfg.getGift()));
        return m;
    }

    /**
     * 构建安全的礼包视图：仅保留展示字段，服务端执行参数置空，图标转为 Base64。
     */
    private GiftConfigEntity safeGift(GiftConfigEntity g) {
        GiftConfigEntity out = new GiftConfigEntity();
        if (g == null) {
            out.setEnabled(false);
            return out;
        }

        // 顶层展示字段
        out.setEnabled(g.isEnabled());
        out.setTitle(g.getTitle());
        out.setDescription(g.getDescription());
        out.setShowButton(g.isShowButton());
        out.setPeriodMode(g.getPeriodMode());
        out.setPeriodStart(g.getPeriodStart());
        out.setPeriodEnd(g.getPeriodEnd());

        // items — 安全副本
        List<GiftItem> items = new ArrayList<>();
        if (g.getItems() != null) {
            for (GiftItem it : g.getItems()) {
                if (!it.isEnable()) {
                    continue; // 未启用的条目直接跳过
                }
                GiftItem item = new GiftItem();
                item.setName(it.getName());
                item.setShow(it.isShow());
                item.setEnable(it.isEnable());
                // icon: 原始路径 → Base64 Data URI
                item.setIcon(loadIconAsBase64(it.getIcon()));
                // 保护字段置空（安全导出）
                item.setMaterial(null);
                item.setAmount(0);
                items.add(item);
            }
        }
        out.setItems(items);

        // commands — 安全副本
        List<GiftCommand> commands = new ArrayList<>();
        if (g.getCommands() != null) {
            for (GiftCommand gc : g.getCommands()) {
                if (!gc.isEnable()) {
                    continue; // 未启用的指令直接跳过
                }
                GiftCommand cmd = new GiftCommand();
                cmd.setName(gc.getName());
                cmd.setShow(gc.isShow());
                cmd.setEnable(gc.isEnable());
                // 保护字段置空（安全导出）
                cmd.setCmd(null);
                commands.add(cmd);
            }
        }
        out.setCommands(commands);

        return out;
    }

    // ==================== 图标加载 ====================

    /**
     * 加载图标文件并转换为 Base64 Data URI。
     * <p>支持三种路径类型：</p>
     * <ul>
     *   <li><b>网络 URL</b>（以 {@code http://} 或 {@code https://} 开头）→ 通过 HttpClientApi 下载</li>
     *   <li><b>绝对路径</b>（{@link File#isAbsolute()} 返回 true）→ 直接读取文件</li>
     *   <li><b>相对路径</b>（其他情况）→ 相对于插件 data 目录读取</li>
     * </ul>
     *
     * @param iconPath YAML 中配置的 icon 值
     * @return Base64 Data URI 字符串（如 {@code data:image/png;base64,...}），加载失败返回空字符串
     */
    private String loadIconAsBase64(String iconPath) {
        if (iconPath == null || iconPath.isEmpty()) {
            return "";
        }

        try {
            byte[] data;

            // 1) 网络 URL
            if (iconPath.startsWith("http://") || iconPath.startsWith("https://")) {
                data = downloadIcon(iconPath);
            }
            // 2) 绝对路径
            else if (new File(iconPath).isAbsolute()) {
                data = readFile(new File(iconPath));
            }
            // 3) 相对路径（相对于 data 目录）
            else {
                data = readFile(new File(dataFolder, iconPath));
            }

            if (data == null || data.length == 0) {
                return "";
            }

            // 尝试推断 MIME 类型
            String mime = guessMimeType(iconPath);
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data);

        } catch (Exception e) {
            log.warnT("log.homepage.icon-load-failed",
                    "[ihomepages] 加载图标失败: {0} - {1}", iconPath, e.getMessage());
            return "";
        }
    }

    /** 通过网络 URL 下载图标字节 */
    private byte[] downloadIcon(String url) {
        try {
            soys.soyshttpovermc.HttpResponse resp = httpClient.sendGet(url);
            if (resp.getStatus() == 200) {
                return resp.getBody();
            }
            log.warnT("log.homepage.icon-download-failed",
                    "[ihomepages] 下载图标失败: {0} (HTTP {1})", url, resp.getStatus());
        } catch (Exception e) {
            log.warnT("log.homepage.icon-download-error",
                    "[ihomepages] 下载图标异常: {0} - {1}", url, e.getMessage());
        }
        return null;
    }

    /** 读取本地文件字节 */
    private byte[] readFile(File file) {
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            log.warnT("log.homepage.icon-read-failed",
                    "[ihomepages] 读取图标文件失败: {0} - {1}", file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    /** 根据文件扩展名猜测 MIME 类型（未知回退图片 PNG，供图标 Data URI）。 */
    private String guessMimeType(String path) {
        String t = MimeTypes.forPath(path);
        return MimeTypes.OCTET_STREAM.equals(t) ? MimeTypes.forExt("png") : t;
    }
}
