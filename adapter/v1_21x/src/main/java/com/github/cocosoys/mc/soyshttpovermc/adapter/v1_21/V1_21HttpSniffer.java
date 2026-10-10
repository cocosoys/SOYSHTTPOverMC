package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_21;

import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.AbstractNettyHttpSniffer;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferDeps;

import java.util.List;

/**
 * 1.21.x netty 反射桥同端口嗅探器。
 *
 * <p>1.21 使用标准 {@code io.netty}；ServerConnection 改名 ServerConnectionListener
 * 已由 {@link V1_21SocketSnifferAdapter} 双通道回退处理。通用逻辑见
 * {@link AbstractNettyHttpSniffer}。</p>
 *
 * <p><b>实测提示</b>：1.21 跑 Java 17/21，JDK 动态代理在模块系统下可能抛
 * IllegalAccessError；若出现需在启动参数追加 {@code --add-opens java.base/java.lang=ALL-UNNAMED}。</p>
 */
public class V1_21HttpSniffer extends AbstractNettyHttpSniffer {

    private static final String NETTY = "io.netty.";

    public V1_21HttpSniffer(HttpSnifferDeps deps) {
        super(deps);
    }

    @Override
    protected String nettyPackagePrefix() {
        return NETTY;
    }

    @Override
    protected String logTag() {
        return "v1_21";
    }

    @Override
    protected List<?> locateListenerChannels() {
        return new V1_21SocketSnifferAdapter().locateListenerChannels();
    }
}
