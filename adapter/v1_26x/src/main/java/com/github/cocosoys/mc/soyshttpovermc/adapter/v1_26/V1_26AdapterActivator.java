package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_26;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivator;

/**
 * 26.x 版本适配激活入口（v1_26x 模块，目标 Minecraft 26.2）。
 *
 * <p>注意：从 2026 年起 Mojang 改用年度版本号（26.1 / 26.2 等），
 * 不再是传统的 1.x 格式。{@link ServerVersion} 解析 26.2 时
 * major=26, minor=2, patch=0，因此本模块判断 {@code major()==26}。</p>
 *
 * <p>经 {@code META-INF/services/...AdapterActivator} 注册，由
 * {@link com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivators#findFor(ServerVersion)}
 * 在 26.x 服务器上选中并 {@link #activate}。</p>
 *
 * <p>本模块的差异点：</p>
 * <ul>
 *   <li><b>版本号</b>：年度格式 26.x（major=26），非传统 1.x；</li>
 *   <li><b>NMS 包</b>：无版本包前缀，直接使用
 *       {@code net.minecraft.server.MinecraftServer}；</li>
 *   <li><b>ServerConnectionListener</b>：1.20.5+ 重命名后的类名，26.x 延续；</li>
 *   <li><b>Java</b>：强制 Java 21 编译（26.x 服务端自身可能要求 Java 25+，
 *       但插件字节码向下兼容）；</li>
 *   <li><b>api-version</b>：plugin.yml 标注 {@code 26.2}。</li>
 * </ul>
 */
public class V1_26AdapterActivator implements AdapterActivator {

    @Override
    public String id() {
        return "v1_26x";
    }

    @Override
    public boolean supports(ServerVersion version) {
        // 年度版本号：26.1 / 26.1.1 / 26.1.2 / 26.2 → major()==26
        return version != null && version.major() == 26;
    }

    @Override
    public void activate(Object plugin, ServerVersion version) {
        // 预留
    }

    @Override
    public void deactivate(Object plugin) {
        // 预留
    }
}
