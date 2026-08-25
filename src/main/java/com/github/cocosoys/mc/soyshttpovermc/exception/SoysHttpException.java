package com.github.cocosoys.mc.soyshttpovermc.exception;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;

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

    /**
     * i18n 无 fallback 版：{@code new ApiException("异常.key", v)}。
     * 以 {@code i18nKey} 查语言表翻译，未命中时回退 {@code i18nKey} 本身作模板再填 {@code {i}} 占位符。
     */
    protected SoysHttpException(Module module, String code, String i18nKey, Object... args) {
        this(module, code, i18nKey, i18nKey, args);
    }

    /** i18n 无 fallback + 根因版：{@code new ApiException("异常.key", e, v)}。 */
    protected SoysHttpException(Module module, String code, String i18nKey, Throwable cause, Object... args) {
        this(module, code, i18nKey, i18nKey, cause, args);
    }

    /** i18n 显式兜底版：{@code new ApiException("异常.key", "兜底模板 {0}", v)}。 */
    protected SoysHttpException(Module module, String code, String i18nKey, String fallback, Object... args) {
        this(module, code, I18n.resolve(i18nKey, fallback, args));
    }

    /** i18n 显式兜底 + 根因版：{@code new ApiException("异常.key", "兜底 {0}", e, v)}。 */
    protected SoysHttpException(Module module, String code, String i18nKey, String fallback, Throwable cause, Object... args) {
        this(module, code, I18n.resolve(i18nKey, fallback, args), cause);
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
