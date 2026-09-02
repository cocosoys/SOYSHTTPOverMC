package com.github.cocosoys.mc.soyshttpovermc.exception;

import lombok.CustomLog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 异常管理总线：统一的异常登记与分发中枢。
 *
 * <p>任何模块在捕获到异常、需要“直接报错处理”时，调用 {@link #report(Throwable)} 或
 * {@link #fire(SoysHttpException)}：总线会先做<b>结构化日志</b>（模块 / 错误码 / 消息 / 根因），
 * 再广播给所有已注册的 {@link ExceptionHandler}。第三方插件可注册自有处理器，避免异常被静默吞掉，
 * 也便于集中维护。</p>
 *
 * <p>惯用法（登记并继续抛出，让调用方感知）：</p>
 * <pre>
 *   try {
 *       apiRegistry.register(instance);
 *   } catch (Exception ex) {
 *       throw ExceptionBus.fire(new ApiException("E_REGISTER", "注册控制器失败: " + ex.getMessage(), ex));
 *   }
 * </pre>
 * <p>
 * 非本体系异常（普通 {@link RuntimeException}/{@link Exception}）经 {@link #report(Throwable)} 会被包成
 * {@link SoysHttpException}（模块 {@code UNKNOWN}），统一走总线。
 */
@CustomLog
public final class ExceptionBus {

    private static final List<ExceptionHandler> handlers = new CopyOnWriteArrayList<>();

    private ExceptionBus() {
    }

    /**
     * 注册一个异常处理器（重复注册会被忽略去重由 List 自身保证唯一引用）
     */
    public static void registerHandler(ExceptionHandler h) {
        if (h != null) handlers.add(h);
    }

    /**
     * 注销一个异常处理器
     */
    public static void unregisterHandler(ExceptionHandler h) {
        if (h != null) handlers.remove(h);
    }

    /**
     * 清空全部处理器（保留总线自身日志能力）
     */
    public static void clearHandlers() {
        handlers.clear();
    }

    /**
     * 登记任意异常（非本体系异常会被包成 {@link SoysHttpException}）
     */
    public static void report(Throwable t) {
        dispatch(toSoys(t));
    }

    /**
     * 登记本体系异常（不发生类型转换，保留原始模块/错误码）
     */
    public static void report(SoysHttpException e) {
        dispatch(e);
    }

    /**
     * 登记并原样返回，便于 {@code throw ExceptionBus.fire(e)} 写法
     */
    public static <T extends SoysHttpException> T fire(T e) {
        dispatch(e);
        return e;
    }

    private static SoysHttpException toSoys(Throwable t) {
        if (t instanceof SoysHttpException) return (SoysHttpException) t;
        return new UnknownException(t == null ? "null" : t.getMessage(), t);
    }

    private static void dispatch(SoysHttpException e) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(e.getModule()).append("] ").append(e.getCode()).append(' ').append(e.getMessage());
        if (e.getPlugin() != null) sb.append(" (plugin=").append(e.getPlugin()).append(')');
        log.error(sb.toString());
        Throwable cause = e.getCause();
        if (cause != null) log.errorT("log.exception.caused-by", "  Caused by: {0}", cause);

        for (ExceptionHandler h : handlers) {
            try {
                h.handle(e);
            } catch (Throwable ignore) {
                // 处理器自身异常不应影响主流程与总线
            }
        }
    }
}
