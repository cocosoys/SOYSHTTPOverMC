package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_20;

import com.github.cocosoys.mc.soyshttpovermc.platform.PlatformBukkitImpl;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 1.20.x 平台的 {@link com.github.cocosoys.mc.soyshttpovermc.spi.Platform} 覆盖实现
 * （继承 core {@link PlatformBukkitImpl}，初始化骨架）。
 *
 * <p>1.20.x 的 Bukkit YAML 读写默认 UTF-8，无需覆写 loadYaml/saveYaml。
 * 预留此类以便后续接入 1.20 特有的平台差异（如现代材料 API、组件系统等）。</p>
 *
 * <p><b>构造约定</b>：无参构造供 ServiceLoader 实例化。</p>
 */
public class V1_20PlatformAdapter extends PlatformBukkitImpl {

    /** ServiceLoader 无参构造（插件实例经基类 currentPlugin 获取）。 */
    public V1_20PlatformAdapter() {
        super();
    }

    public V1_20PlatformAdapter(JavaPlugin plugin) {
        super(plugin);
    }

    // 1.20.x 暂无需覆写的方法；预留后续版本特化差异点。
}
