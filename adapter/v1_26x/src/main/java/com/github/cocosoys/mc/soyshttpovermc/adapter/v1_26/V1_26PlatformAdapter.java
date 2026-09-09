package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_26;

import com.github.cocosoys.mc.soyshttpovermc.platform.PlatformBukkitImpl;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 1.26.x 平台的 {@link com.github.cocosoys.mc.soyshttpovermc.spi.Platform} 覆盖实现
 * （继承 core {@link PlatformBukkitImpl}，未来版本预留骨架）。
 */
public class V1_26PlatformAdapter extends PlatformBukkitImpl {

    public V1_26PlatformAdapter() {
        super();
    }

    public V1_26PlatformAdapter(JavaPlugin plugin) {
        super(plugin);
    }
}
