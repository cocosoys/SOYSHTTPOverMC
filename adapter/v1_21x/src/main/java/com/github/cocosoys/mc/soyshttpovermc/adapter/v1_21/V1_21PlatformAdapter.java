package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_21;

import com.github.cocosoys.mc.soyshttpovermc.platform.PlatformBukkitImpl;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 1.21.x 平台的 {@link com.github.cocosoys.mc.soyshttpovermc.spi.Platform} 覆盖实现
 * （继承 core {@link PlatformBukkitImpl}，初始化骨架）。
 *
 * <p>1.21.x 的 Bukkit YAML 读写默认 UTF-8，无需覆写 loadYaml/saveYaml。
 * 预留此类以便后续接入 1.21 特有的平台差异（如组件系统、现代材料 API 等）。</p>
 */
public class V1_21PlatformAdapter extends PlatformBukkitImpl {

    public V1_21PlatformAdapter() {
        super();
    }

    public V1_21PlatformAdapter(JavaPlugin plugin) {
        super(plugin);
    }
}
