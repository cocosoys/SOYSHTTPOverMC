package soys.soyshttpovermc.exception;

/** 能力组 4（工具：JSON / Content-Type）专用异常。 */
public class ToolkitException extends SoysHttpException {

    public ToolkitException(String code, String message) {
        super(Module.TOOLKIT, code, message);
    }

    public ToolkitException(String code, String message, Throwable cause) {
        super(Module.TOOLKIT, code, message, cause);
    }

    public ToolkitException(String message) {
        super(Module.TOOLKIT, "TOOLKIT_ERR", message);
    }

    public ToolkitException(String message, Throwable cause) {
        super(Module.TOOLKIT, "TOOLKIT_ERR", message, cause);
    }


    public ToolkitException(String code, String fmt, Object... args) {
        super(Module.TOOLKIT, code, fmt, args);
    }

    public ToolkitException(String fmt, Object... args) {
        super(Module.TOOLKIT, "TOOLKIT_ERR", fmt, args);
    }

    public ToolkitException(String code, String fmt, Throwable cause, Object... args) {
        super(Module.TOOLKIT, code, fmt, cause, args);
    }
}
