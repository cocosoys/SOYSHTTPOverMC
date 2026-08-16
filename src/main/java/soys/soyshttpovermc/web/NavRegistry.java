package soys.soyshttpovermc.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 门户导航项登记处（第三方插件接入点）。
 *
 * <p>插件经 {@code WebPageApi.registerNavItem(...)} 把自己的页面登记到门户首页导航条，
 * 玩家打开 {@code /} 时即可看到并一键进入，免去记 URL。插件禁用时由
 * {@code ApiLifecycleListener} 自动清理其名下导航项。</p>
 */
public class NavRegistry {

    /** 单个导航项（只读快照，避免插件卸载后泄漏）。 */
    public static final class NavItem {
        public final String owner;
        public final String label;
        public final String path;     // 完整 web 路径（如 /plugins/Foo/dash）
        public final String icon;     // 可选：emoji 或图片 URL
        public final String permission; // 可选：预留（当前不在服务端强制，前端按需灰显）
        public final int order;       // 排序，越小越靠前

        public NavItem(String owner, String label, String path, String icon, String permission, int order) {
            this.owner = owner == null ? "?" : owner;
            this.label = label == null ? path : label;
            this.path = path == null ? "/" : path;
            this.icon = icon;
            this.permission = permission;
            this.order = order;
        }
    }

    private final List<NavItem> items = new CopyOnWriteArrayList<>();

    /** 登记一个导航项（重复登记不校验，按 owner+path+label 允许并存）。 */
    public void register(NavItem item) {
        if (item != null) items.add(item);
    }

    /** 卸载指定插件名登记的全部导航项（监听 PluginDisableEvent 时调用）。 */
    public void unregisterPlugin(String pluginName) {
        if (pluginName == null || pluginName.isEmpty()) return;
        items.removeIf(i -> pluginName.equals(i.owner));
    }

    /** 当前全部导航项（按 order 升序快照）。 */
    public List<NavItem> snapshot() {
        List<NavItem> s = new ArrayList<>(items);
        s.sort(Comparator.comparingInt(a -> a.order));
        return s;
    }

    /** 渲染为门户首页注入用的 HTML 片段；无项返回空串。 */
    public String renderHtml() {
        List<NavItem> s = snapshot();
        if (s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"soys-plugin-nav\" style=\"background:rgba(10,12,24,.92);color:#7fefff;")
          .append("font:13px/1.6 monospace;padding:6px 12px;box-shadow:0 1px 6px rgba(0,0,0,.5);")
          .append("display:flex;gap:14px;flex-wrap:wrap;align-items:center;border-bottom:1px solid #1b2a4a\">");
        sb.append("<span style=\"color:#9aa;font-weight:bold\">插件面板</span>");
        for (NavItem it : s) {
            String icon = (it.icon == null || it.icon.isEmpty()) ? "" : it.icon + " ";
            sb.append("<a href=\"").append(esc(it.path)).append("\" style=\"color:#7fefff;text-decoration:none\">")
              .append(esc(icon)).append(esc(it.label)).append("</a>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
