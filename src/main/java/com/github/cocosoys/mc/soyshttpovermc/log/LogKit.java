package com.github.cocosoys.mc.soyshttpovermc.log;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;

/**
 * 统一日志门面：全插件日志一律经此类打印，支持运行时级别过滤与热重载。
 *
 * <p>级别（由高到低过滤，默认 INFO）：OFF &gt; ERROR &gt; WARN &gt; INFO &gt; DEBUG &gt; TRACE。
 * 打印走 {@link Bukkit#getLogger()} 的 {@code log(...)}，以 JUL {@link Level} 表示严重级
 * （TRACE=FINEST / DEBUG=FINE / INFO=INFO / WARN=WARNING / ERROR=SEVERE），避免走
 * {@code ConsoleCommandSender#sendMessage} —— 那会被聊天相关监听器捕获而产生副作用。</p>
 *
 * <p>两种用法并存：</p>
 * <ul>
 *   <li><b>Lombok 实例写法（推荐）</b>：给类加 {@code @CustomLog}，生成 {@code static final LogKit log}，
 *       然后 {@code log.info("玩家 %s 加入", name)} 即以 {@code String.format} 风格打印；
 *       支持多参数与 {@code log.error(throwable, "…", arg)} 异常打印。</li>
 *   <li><b>静态控制层</b>：{@link #setLevel} / {@link #levelName} / {@link #isDebugEnabled} /
 *       {@link #init} 维护全局级别（{@code /soyshttp reload} 热重载），供宿主配置与检查。</li>
 * </ul>
 */
public class LogKit {

    // ========= 静态控制层：全局级别状态 + 热重载 =========
    private static volatile int tierIndex = 3; // INFO
    private static final String[] TIER_NAMES = {"OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE"};

    /** 初始化（宿主启动时调用；第一个参数仅兼容旧签名，级别判断以 JUL Level 映射为准）。 */
    public static synchronized void init(Object unusedLogger, String levelName) {
        setLevel(levelName);
    }

    /** 设置运行时级别（用于 /soyshttp reload 热重载）。非法值忽略。 */
    public static synchronized void setLevel(String raw) {
        if (raw == null) return;
        String up = raw.trim().toUpperCase();
        for (int i = 0; i < TIER_NAMES.length; i++) {
            if (TIER_NAMES[i].equals(up)) { tierIndex = i; return; }
        }
    }

    /** 当前级别名称（供指令/提示展示）。 */
    public static String levelName() {
        int idx = Math.min(Math.max(tierIndex, 0), TIER_NAMES.length - 1);
        return TIER_NAMES[idx];
    }

    /** 是否开启 DEBUG 级（含更细）输出（供条件分支避免拼接开销）。 */
    public static boolean isDebugEnabled() {
        return tierIndex >= 4;
    }

    // ========= 层次 → JUL Level / 级别色映射 =========

    private static Level levelFor(int tier) {
        switch (tier) {
            case 5:  return Level.FINEST;   // TRACE
            case 4:  return Level.FINE;     // DEBUG
            case 2:  return Level.WARNING;  // WARN
            case 1:  return Level.SEVERE;   // ERROR
            case 0:  return Level.OFF;      // OFF（不打印）
            default: return Level.INFO;
        }
    }

    // ========= 实例（@CustomLog 注入用） =========

    protected final String prefix;
    protected final Class<?> sourceClass;

    public LogKit(String prefix, Class<?> sourceClass) {
        this.prefix = prefix;
        this.sourceClass = sourceClass;
    }

    private Logger logger() {
        return Bukkit.getLogger();
    }

    /**
     * 组装带色前缀与类名的整行日志：插件标签用渐变色点缀，消息用级别色。
     * 仅在全局日志级别调到 DEBUG 及以上（{@link #isDebugEnabled()}）时才打印类名：
     * DEBUG 打印短类名；TRACE 打印<b>完整类名（含包路径）+ 线程名</b>，便于精确定位来源与线程上下文；
     * INFO/WARN/ERROR 下保持输出简洁、不打印类名。
     */
    protected String formatMessage(String rawMessage, int tier) {
        if (rawMessage == null) rawMessage = "null";
        StringBuilder sb = new StringBuilder();
//        if (prefix != null && !prefix.isEmpty()) {
//            sb.append(StringColor.gradient(prefix, Color.AQUA, Color.LIGHT_PURPLE)).append(' ');
//        }
//        sb.append(Color.fg(colorFor(tier)));
        if (isDebugEnabled()) {
            if (tier == 5) { // TRACE：完整类名（含包路径）+ 线程名，便于精确定位日志来源与线程上下文
                sb.append('[').append(Thread.currentThread().getName()).append("] ")
                        .append('[').append(sourceClass.getName()).append("] ");
            } else {
                sb.append('[').append(sourceClass.getSimpleName()).append("] ");
            }
        }
        sb.append(rawMessage);
//        sb.append(Color.reset());
        return sb.toString();
    }

    private boolean enabled(int minTier) {
        return tierIndex >= minTier;
    }

    private void print(int tier, String i18nKey, String fmt, Object... args) {
        print(tier,i18nKey,fmt,null,args);
    }

    private void print(int tier, String i18nKey, String fmt, Throwable throwable, Object... args) {
        String msg = I18n.resolve(i18nKey, fmt, args);
        if(throwable==null){
            logger().log(levelFor(tier), formatMessage(msg, tier));
        }else{
            logger().log(levelFor(tier), formatMessage(msg, tier), throwable);
        }
    }

    public void trace(String fmt, Object... args) {
        if (enabled(5)) print(5, null, fmt, args);
    }

    public void debug(String fmt, Object... args) {
        if (enabled(4)) print(4, null, fmt, args);
    }

    public void info(String fmt, Object... args) {
        if (enabled(3)) print(3, null, fmt, args);
    }

    public void warn(String fmt, Object... args) {
        if (enabled(2)) print(2, null, fmt, args);
    }

    public void error(String fmt, Object... args) {
        if (enabled(1)) print(1, null, fmt, args);
    }

    public void error(Throwable throwable, String fmt, Object... args) {
        if (enabled(1)) print(1, null, fmt, throwable, args);
    }

    // ========= i18n 版方法（*T：key 首参，命中语言表翻译，未命中回退 fallback） =========

    public void traceT(String i18nKey, String fallback, Object... args) {
        if (enabled(5)) print(5, i18nKey, fallback, args);
    }

    public void debugT(String i18nKey, String fallback, Object... args) {
        if (enabled(4)) print(4, i18nKey, fallback, args);
    }

    public void infoT(String i18nKey, String fallback, Object... args) {
        if (enabled(3)) print(3, i18nKey, fallback, args);
    }

    public void warnT(String i18nKey, String fallback, Object... args) {
        if (enabled(2)) print(2, i18nKey, fallback, args);
    }

    public void errorT(String i18nKey, String fallback, Object... args) {
        if (enabled(1)) print(1, i18nKey, fallback, args);
    }

    public void errorT(Throwable throwable, String i18nKey, String fallback, Object... args) {
        if (enabled(1)) print(1, i18nKey, fallback, throwable, args);
    }

    // ========= 便捷单参（消息原样，不做占位符替换） =========
    public void trace(String msg) { print(5, null, msg); }
    public void debug(String msg) { print(4, null, msg); }
    public void info(String msg) { print(3, null, msg); }
    public void warn(String msg) { print(2, null, msg); }
    public void error(String msg) { print(1, null, msg); }
    public void error(String msg, Throwable throwable) { error(throwable, msg); }

    // ========= Lombok 两个重载工厂 =========
    public static LogKit getLogger(Class<?> clazz) {
        return new LogKit("[HTTP-Over-MC]", clazz);
    }

    public static LogKit getLogger(Class<?> clazz, String topic) {
        return new LogKit("[" + topic + "]", clazz);
    }
}