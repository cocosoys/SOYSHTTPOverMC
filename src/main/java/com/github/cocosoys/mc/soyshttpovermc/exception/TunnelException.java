package com.github.cocosoys.mc.soyshttpovermc.exception;

/** 隧道 / PluginMessage 通道专用异常（Bot 回连、McLink 多路复用等内部错误）。 */
public class TunnelException extends SoysHttpException {

    public TunnelException(String code, String message) {
        super(Module.TUNNEL, code, message);
    }

    public TunnelException(String code, String message, Throwable cause) {
        super(Module.TUNNEL, code, message, cause);
    }

    public TunnelException(String message) {
        super(Module.TUNNEL, "TUNNEL_ERR", message);
    }

    public TunnelException(String message, Throwable cause) {
        super(Module.TUNNEL, "TUNNEL_ERR", message, cause);
    }


    /** i18n 无兜底版（兜底参数可省略）：{@code new TunnelException("TUNNEL_ERR", "异常.key", v)}，未命中语言表时以 key 自身作模板回退填 {0}。 */
    public TunnelException(String code, String i18nKey, Object... args) {
        super(Module.TUNNEL, code, i18nKey, args);
    }

    /** 默认错误码、i18n 无兜底版：{@code new TunnelException("异常.key", v)}。 */
    public TunnelException(String i18nKey, Object... args) {
        super(Module.TUNNEL, "TUNNEL_ERR", i18nKey, args);
    }

    /** i18n 无兜底 + 根因版：{@code new TunnelException("TUNNEL_ERR", "异常.key", e, v)}。 */
    public TunnelException(String code, String i18nKey, Throwable cause, Object... args) {
        super(Module.TUNNEL, code, i18nKey, cause, args);
    }

    /** i18n 显式兜底版：{@code new TunnelException("TUNNEL_ERR", "异常.key", "兜底 {0}", v)}。 */
    public TunnelException(String code, String i18nKey, String fallback, Object... args) {
        super(Module.TUNNEL, code, i18nKey, fallback, args);
    }

    /** i18n 显式兜底 + 根因版：{@code new TunnelException("TUNNEL_ERR", "异常.key", "兜底 {0}", e, v)}。 */
    public TunnelException(String code, String i18nKey, String fallback, Throwable cause, Object... args) {
        super(Module.TUNNEL, code, i18nKey, fallback, cause, args);
    }
}
