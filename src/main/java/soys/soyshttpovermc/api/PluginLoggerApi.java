package soys.soyshttpovermc.api;

/**
 * 能力组 5：日志（包装 LogKit，自动识别调用插件并加 [插件名] 前缀）。
 * 由 {@link SoysHttpOverMcApi#getLogger()} 跳转获取。
 */
public interface PluginLoggerApi {

    void logInfo(String msg);

    void logWarn(String msg);

    void logError(String msg);

    void logDebug(String msg);

    void logTrace(String msg);

    /** 当前日志级别是否包含 DEBUG 及以上 */
    boolean isDebugEnabled();
}
