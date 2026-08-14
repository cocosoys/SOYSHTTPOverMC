package soys.soyshttpovermc.exception;

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
}
