package com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer;

/**
 * 版本兼容嗅探器安装器 SPI：由各版本 adapter（v1_7x / v1_6x）通过
 * {@code META-INF/services} 注册，core 在 {@code initSniffer} 装配时先查询，
 * 命中且安装成功则替换内置 {@link SocketSniffer}。
 *
 * <p>契约约束：</p>
 * <ul>
 *   <li>{@link #install} 返回安装句柄（任意对象，供 {@link #uninstall} 使用）；安装失败抛异常返回 null。</li>
 *   <li>实现类不得持有对标准 {@code io.netty.*} 的编译期依赖（传输层自实现）；</li>
 *   <li>版本判断、relocate netty 反射等版本相关逻辑全部在 adapter 侧，core 不感知。</li>
 * </ul>
 */
public interface HttpSnifferInstaller {

    /**
     * 安装器标识（如 v1_7x / v1_6x），用于日志。
     */
    String id();

    /**
     * 当前服务端版本是否支持此安装器。
     */
    boolean supported();

    /**
     * 在当前服务端上安装同端口嗅探器，返回安装句柄（可为任意对象，仅作 uninstall 凭据）。
     *
     * @param deps core 提供的依赖容器
     * @return 安装句柄；安装失败返回 null 或抛异常，由调用方回退内置嗅探器
     */
    Object install(HttpSnifferDeps deps) throws Exception;

    /**
     * 卸载先前安装的嗅探器。
     */
    void uninstall(Object handle);

    /**
     * 不支持时的默认实现（保证 ServiceLoader 无命中时调用方仍有非 null 返回值）。
     */
    final class Unsupported implements HttpSnifferInstaller {
        private final String reason;

        public Unsupported(String reason) {
            this.reason = reason;
        }

        @Override
        public String id() {
            return "unsupported";
        }

        @Override
        public boolean supported() {
            return false;
        }

        @Override
        public Object install(HttpSnifferDeps deps) {
            return null;
        }

        @Override
        public void uninstall(Object handle) {
        }

        public String reason() {
            return reason;
        }
    }
}
