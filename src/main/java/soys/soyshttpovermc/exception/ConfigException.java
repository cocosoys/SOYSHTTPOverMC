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


    /** i18n 无兜底版（兜底参数可省略）：{@code new ConfigException("CONFIG_ERR", "异常.key", v)}，未命中语言表时以 key 自身作模板回退填 {0}。 */
    public ConfigException(String code, String i18nKey, Object... args) {
        super(Module.CONFIG, code, i18nKey, args);
    }

    /** 默认错误码、i18n 无兜底版：{@code new ConfigException("异常.key", v)}。 */
    public ConfigException(String i18nKey, Object... args) {
        super(Module.CONFIG, "CONFIG_ERR", i18nKey, args);
    }

    /** i18n 无兜底 + 根因版：{@code new ConfigException("CONFIG_ERR", "异常.key", e, v)}。 */
    public ConfigException(String code, String i18nKey, Throwable cause, Object... args) {
        super(Module.CONFIG, code, i18nKey, cause, args);
    }

    /** i18n 显式兜底版：{@code new ConfigException("CONFIG_ERR", "异常.key", "兜底 {0}", v)}。 */
    public ConfigException(String code, String i18nKey, String fallback, Object... args) {
        super(Module.CONFIG, code, i18nKey, fallback, args);
    }

    /** i18n 显式兜底 + 根因版：{@code new ConfigException("CONFIG_ERR", "异常.key", "兜底 {0}", e, v)}。 */
    public ConfigException(String code, String i18nKey, String fallback, Throwable cause, Object... args) {
        super(Module.CONFIG, code, i18nKey, fallback, cause, args);
    }
}
