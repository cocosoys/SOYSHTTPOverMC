package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_7;

import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.AbstractNettyHttpSniffer;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferDeps;

import java.util.List;

/**
 * 1.7.10 relocate netty 反射桥同端口嗅探器。
 *
 * <p>1.7.10 服务端内嵌 netty 被重打包为 {@code net.minecraft.util.io.netty.*}
 * （craftbukkit-1.7.10 实证标准 {@code io.netty.*} 类为 0）。通用注入/分流/HTTP 处理逻辑
 * 已上移至 {@link AbstractNettyHttpSniffer}，本类只提供 relocate 前缀与
 * 父 ServerChannel 定位（复用 {@link V1_7SocketSnifferAdapter} 双通道定位）。</p>
 */
public class V1_7HttpSniffer extends AbstractNettyHttpSniffer {

    /** 1.7.10 netty relocate 前缀。 */
    private static final String N = "net.minecraft.util.io.netty.";

    public V1_7HttpSniffer(HttpSnifferDeps deps) {
        super(deps);
    }

    @Override
    protected String nettyPackagePrefix() {
        return N;
    }

    @Override
    protected String logTag() {
        return "v1_7";
    }

    @Override
    protected List<?> locateListenerChannels() {
        return new V1_7SocketSnifferAdapter().locateListenerChannels();
    }
}
