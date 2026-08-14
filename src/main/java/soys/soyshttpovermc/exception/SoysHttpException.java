package soys.soyshttpovermc.exception;

/**
 * HTTP-Over-MC 异常体系的根抽象类。
 *
 * <p>所有对开发者可见的异常均继承此类（{@code ApiException} / {@code WebPageException} /
 * {@code AuthException} / {@code BotException} / {@code HttpClientException} /
 * {@code ConfigException} / {@code TunnelException} / {@code ToolkitException}），
 * 统一携带 {@link Module 模块}、错误码 {@code code} 与可选触发插件名，
 * 便于在 {@link ExceptionBus} 中按模块集中登记、日志与分发，方便维护。</p>
 *
 * <p>本类继承 {@link RuntimeException}，因此均为<b>非受检异常</b>：
 * 第三方插件无需强制 try/catch；若需精准处理某类异常，可 {@code catch (ApiException e)}。</p>
 */
public abstract class SoysHttpException extends RuntimeException {

    /** 异常所属模块（与门面能力组一一对应，便于分类维护） */
    public enum Module {
        CORE, API, WEB, AUTH, BOT, HTTP, CONFIG, TUNNEL, TOOLKIT, UNKNOWN
    }

    private final Module module;
    private final String code;
    private String plugin; // 触发异常的插件名（可能为 null）

    protected SoysHttpException(Module module, String code, String message) {
        super(message);
        this.module = module;
        this.code = code;
    }

    protected SoysHttpException(Module module, String code, String message, Throwable cause) {
        super(message, cause);
        this.module = module;
        this.code = code;
    }

    public Module getModule() { return module; }

    public String getCode() { return code; }

    public String getPlugin() { return plugin; }

    /** 回填触发异常插件名（可选，便于维护时定位来源）；返回 this 支持链式。 */
    public SoysHttpException withPlugin(String plugin) {
        this.plugin = plugin;
        return this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + module + ":" + code + "] " + getMessage()
                + (plugin != null ? " (plugin=" + plugin + ")" : "");
    }
}
