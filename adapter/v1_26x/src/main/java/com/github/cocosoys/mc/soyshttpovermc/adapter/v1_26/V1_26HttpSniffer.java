package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_26;

import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.AbstractNettyHttpSniffer;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferDeps;

import java.util.List;

/**
 * 26.x netty 反射桥同端口嗅探器。
 *
 * <p>基于 1.20.5+ 架构（ServerConnectionListener），标准 {@code io.netty}；
 * 父 ServerChannel 定位复用 {@link V1_26SocketSnifferAdapter}。通用逻辑见
 * {@link AbstractNettyHttpSniffer}。</p>
 *
 * <p><b>实测提示</b>：26.x 跑 Java 21/25，JDK 动态代理可能被模块系统拦截；
 * 若 install() 抛 IllegalAccessError，需追加 {@code --add-opens java.base/java.lang=ALL-UNNAMED}。</p>
 */
public class V1_26HttpSniffer extends AbstractNettyHttpSniffer {

    private static final String NETTY = "io.netty.";

    public V1_26HttpSniffer(HttpSnifferDeps deps) {
        super(deps);
    }

    @Override
    protected String nettyPackagePrefix() {
        return NETTY;
    }

    @Override
    protected String logTag() {
        return "v1_26";
    }

    @Override
    protected List<?> locateListenerChannels() {
        return new V1_26SocketSnifferAdapter().locateListenerChannels();
    }
}
