package com.github.cocosoys.mc.soyshttpovermc.exception;

/**
 * 异常处理器：注册到 {@link ExceptionBus} 后，所有经总线登记的异常都会回调 {@link #handle}。
 *
 * <p>典型用途：第三方插件把本插件异常转发到自身日志/面板、上报监控系统，
 * 或按 {@link SoysHttpException#getModule()} / {@link SoysHttpException#getCode()} 做差异化处理。</p>
 */
public interface ExceptionHandler {

    /** 处理一条已登记的异常（实现内请勿再向外抛异常，否则会被总线静默吞掉）。 */
    void handle(SoysHttpException e);
}
