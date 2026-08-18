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

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
