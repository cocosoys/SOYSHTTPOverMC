package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_16;

import com.github.cocosoys.mc.soyshttpovermc.platform.PlatformBukkitImpl;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 1.16.x 平台的 {@link com.github.cocosoys.mc.soyshttpovermc.spi.Platform} 覆盖实现
 * （继承 core {@link PlatformBukkitImpl}，初始化骨架）。
 *
 * <p>1.16.x 的 Bukkit YAML 读写已默认 UTF-8（1.13+ 修复了 1.7.10 的 GBK 问题），
 * 因此本模块暂不需要覆写 {@code loadYaml/saveYaml}。预留此类以便后续接入
 * 1.16 特有的平台差异（如 PersistentDataContainer、现代材料 API 等）。</p>
 *
 * <p><b>构造约定</b>：无参构造供 ServiceLoader 实例化（{@code Platforms.find()} 会优先采用本实现，
 * 其插件实例来自基类静态 {@code currentPlugin}，由 core onEnable 写入）。</p>
 */
public class V1_16PlatformAdapter extends PlatformBukkitImpl {

    /** ServiceLoader 无参构造（插件实例经基类 currentPlugin 获取）。 */
    public V1_16PlatformAdapter() {
        super();
    }

    public V1_16PlatformAdapter(JavaPlugin plugin) {
        super(plugin);
    }

    // 1.16.x 暂无需覆写的方法；预留后续版本特化差异点。
}
