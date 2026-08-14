package soys.soyshttpovermc.exception;

/** 配置类专用异常（gateway/ 配置解析、主配置读取等内部错误）。 */
public class ConfigException extends SoysHttpException {

    public ConfigException(String code, String message) {
        super(Module.CONFIG, code, message);
    }

    public ConfigException(String code, String message, Throwable cause) {
        super(Module.CONFIG, code, message, cause);
    }

    public ConfigException(String message) {
        super(Module.CONFIG, "CONFIG_ERR", message);
    }

    public ConfigException(String message, Throwable cause) {
        super(Module.CONFIG, "CONFIG_ERR", message, cause);
    }
}
