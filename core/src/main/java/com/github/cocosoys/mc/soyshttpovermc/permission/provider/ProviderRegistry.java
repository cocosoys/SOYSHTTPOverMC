package com.github.cocosoys.mc.soyshttpovermc.permission.provider;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * 权限提供者注册表：管理所有支持的权限插件提供者，根据配置和插件可用性筛选活跃提供者。
 *
 * <p>使用方式：
 * <pre>
 *   ProviderRegistry registry = new ProviderRegistry(plugin);
 *   registry.reload();  // 根据 config.yml 重新加载活跃提供者
 *   List&lt;PermissionProvider&gt; active = registry.getActiveProviders();
 * </pre>
 *
 * <p>配置逻辑（config.yml 的 permission.providers）：
 * <ul>
 *   <li>留空（默认）：所有已安装且支持的权限插件自动加入组合；</li>
 *   <li>指定列表：只使用列表中的插件（需插件已安装，未安装的自动跳过并打印警告）。</li>
 * </ul>
 *
 * <p>当前支持的权限插件：
 * <ul>
 *   <li>luckperms  — LuckPerms（推荐，支持离线查询）</li>
 *   <li>essentials — Essentials（在线 Bukkit 原生，离线不支持）</li>
 *   <li>essentialx — EssentialsX（Essentials 活跃分支，同上）</li>
 *   <li>permsex    — PermissionsEx（老牌权限插件，支持离线查询）</li>
 * </ul>
 */
public class ProviderRegistry {

    private final JavaPlugin plugin;
    private final List<PermissionProvider> allProviders = new ArrayList<>();
    private volatile List<PermissionProvider> activeProviders = Collections.emptyList();

    public ProviderRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        // 注册所有支持的提供者（顺序即默认优先级，可通过配置调整）
        allProviders.addAll(Arrays.asList(
                new LuckPermsProvider(),
                new PermsExProvider(),
                new EssentialsProvider(),
                new EssentialsXProvider()
        ));
    }

    /**
     * 根据 config.yml 重新加载活跃提供者列表。
     * 调用时机：插件启用时 + /soyshttp reload 时。
     */
    public void reload() {
        List<String> configured = plugin.getConfig().getStringList("permission.providers");
        List<PermissionProvider> result = new ArrayList<>();
        Set<String> addedNames = new LinkedHashSet<>();

        if (configured == null || configured.isEmpty()) {
            // 默认：所有已安装的权限插件自动加入
            for (PermissionProvider p : allProviders) {
                if (p.isAvailable()) {
                    result.add(p);
                    addedNames.add(p.name());
                }
            }
        } else {
            // 指定：只使用配置的提供者（需插件已安装）
            for (String name : configured) {
                if (name == null || name.trim().isEmpty()) continue;
                String lower = name.trim().toLowerCase();
                PermissionProvider found = null;
                for (PermissionProvider p : allProviders) {
                    if (p.name().equalsIgnoreCase(lower)) {
                        found = p;
                        break;
                    }
                }
                if (found == null) {
                    plugin.getLogger().warning("[Permission] 未知的权限提供者: " + name
                            + "（支持: luckperms/essentials/essentialx/permsex），已跳过");
                    continue;
                }
                if (!found.isAvailable()) {
                    plugin.getLogger().warning("[Permission] 权限提供者 " + name
                            + " 对应的插件未安装或未启用，已跳过");
                    continue;
                }
                if (!addedNames.contains(found.name())) {
                    result.add(found);
                    addedNames.add(found.name());
                }
            }
        }

        this.activeProviders = Collections.unmodifiableList(result);
        if (!result.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < result.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(result.get(i).name());
            }
            plugin.getLogger().info("[Permission] 权限判断组合已加载: " + sb
                    + "（任一返回 true 即权限通过）");
        } else {
            plugin.getLogger().info("[Permission] 未启用任何权限插件提供者，"
                    + "将仅使用 Bukkit 原生权限（在线玩家）+ OP 降级（离线玩家）");
        }
    }

    /**
     * 获取当前活跃的权限提供者列表（不可修改）。
     */
    public List<PermissionProvider> getActiveProviders() {
        return activeProviders;
    }

    /**
     * 获取所有已注册的提供者（包括不可用的，用于调试/展示）。
     */
    public List<PermissionProvider> getAllProviders() {
        return Collections.unmodifiableList(allProviders);
    }

    /**
     * 按名称获取提供者（含不可用的）。
     */
    public PermissionProvider getProvider(String name) {
        if (name == null) return null;
        for (PermissionProvider p : allProviders) {
            if (p.name().equalsIgnoreCase(name)) return p;
        }
        return null;
    }
}
