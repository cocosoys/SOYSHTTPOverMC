package soys.soyshttpovermc.log;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 统一日志门面：全插件日志一律经此类打印，支持运行时级别过滤与热重载。
 *
 * <p>级别（由高到低过滤，默认 INFO）：OFF &gt; ERROR &gt; WARN &gt; INFO &gt; DEBUG &gt; TRACE。</p>
 *
 * <p>两种用法并存：</p>
 * <ul>
 *   <li><b>Lombok 实例写法（推荐）</b>：给类加 {@code @CustomLog}，生成 {@code static final LogKit log}，
 *       然后 {@code log.info("玩家 %s 加入", name)} 即以 {@code String.format} 风格打印；
 *       支持多参数与 {@code log.error(throwable, "…", arg)} 异常打印。</li>
 *   <li><b>静态控制层</b>：{@link #setLevel} / {@link #levelName} / {@link #isDebugEnabled} /
 *       {@link #init} 维护全局级别（{@code /soyshttp reload} 热重载），供宿主配置与检查。</li>
 * </ul>
 *
 * <p>级别状态为静态、实例方法打印时读取静态级别做过滤，故热重载对所有 {@code log} 实例即时生效。</p>
 * <p>消息格式由调用方给定（规范为 {@code [HTTP-Over-MC] [模块] 内容}），本类不追加前缀，
 * 避免与既有消息重复；Bukkit 日志本身已带 {@code [SOYSHTTPOverMC]} 插件名。</p>
 */
import soys.soyshttpovermc.i18n.I18n;
import org.bukkit.Bukkit;

public class LogKit {
    public static boolean ENABLE_ANSI = true;

    // ========= 静态控制层：全局级别状态 + 热重载 =========
    private static volatile int tierIndex = 3; // INFO
    private static final String[] TIER_NAMES = {"OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE"};

    /** 初始化（宿主启动时调用；第一个参数为旧式 JUL Logger，本实现以控制台直出，忽略该参数，仅兼容旧签名）。 */
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

    // ========= 实例桥（@CustomLog 注入用） =========

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GRAY = "\u001B[38;2;128;128;128m";
    private static final String ANSI_CYAN = "\u001B[38;2;0;255;255m";
    private static final String ANSI_GREEN = "\u001B[38;2;80;255;120m";
    private static final String ANSI_YELLOW = "\u001B[38;2;255;220;0m";
    private static final String ANSI_RED = "\u001B[38;2;255;60;60m";

    protected final String prefix;
    protected final Class<?> sourceClass;

    public LogKit(String prefix, Class<?> sourceClass) {
        this.prefix = prefix;
        this.sourceClass = sourceClass;
    }

    private String ansiFg(int r, int g, int b) {
        if (!ENABLE_ANSI) return "";
        return String.format("\u001B[38;2;%d;%d;%dm", r, g, b);
    }

    public String gradient(String text, int r1, int g1, int b1, int r2, int g2, int b2) {
        if (!ENABLE_ANSI || text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            float t = (float) i / Math.max(len - 1, 1);
            int r = (int) (r1 * (1 - t) + r2 * t);
            int g = (int) (g1 * (1 - t) + g2 * t);
            int b = (int) (b1 * (1 - t) + b2 * t);
            sb.append(ansiFg(r, g, b)).append(text.charAt(i)).append(ANSI_RESET);
        }
        return sb.toString();
    }

    protected String formatMessage(String rawMessage, boolean isDebugLevel, String levelColor) {
        if (rawMessage == null) rawMessage = "null";
        StringBuilder sb = new StringBuilder();

        if (ENABLE_ANSI) sb.append(levelColor);
        if (prefix != null && !prefix.isEmpty()) {
            sb.append(prefix);
            if (ENABLE_ANSI) sb.append(ANSI_RESET);
            sb.append(" ");
        }

        // debug/trace 打短名，info/warn/error 打全限定类名，便于错误快速溯源；
        // 此前缀在当前方法中于 i18n 转译完成后追加，故不会污染翻译 key。
        String clsName = isDebugLevel ? sourceClass.getSimpleName() : sourceClass.getName();
        if (ENABLE_ANSI) sb.append(levelColor);
        sb.append("[").append(clsName).append("]");
        if (ENABLE_ANSI) sb.append(ANSI_RESET);
        sb.append(" ");

        if (ENABLE_ANSI) sb.append(levelColor);
        sb.append(rawMessage);
        if (ENABLE_ANSI) sb.append(ANSI_RESET);
        return sb.toString();
    }

    private boolean enabled(int minTier) {
        return tierIndex >= minTier;
    }

    private void print(String color, boolean debugLevel, String i18nKey, String fmt, Object... args) {
        String msg = I18n.resolve(i18nKey, fmt, args);
        Bukkit.getConsoleSender().sendMessage(formatMessage(msg, debugLevel, color));
    }

    public void trace(String fmt, Object... args) {
        if (enabled(5)) print(ANSI_CYAN, true, null, fmt, args);
    }

    public void debug(String fmt, Object... args) {
        if (enabled(4)) print(ANSI_CYAN, true, null, fmt, args);
    }

    public void info(String fmt, Object... args) {
        if (enabled(3)) print(ANSI_GREEN, false, null, fmt, args);
    }

    public void warn(String fmt, Object... args) {
        if (enabled(2)) print(ANSI_YELLOW, false, null, fmt, args);
    }

    public void error(String fmt, Object... args) {
        if (enabled(1)) print(ANSI_RED, false, null, fmt, args);
    }

    public void error(Throwable throwable, String fmt, Object... args) {
        if (!enabled(1)) return;
        print(ANSI_RED, false, null, fmt, args);
        if (throwable != null) throwable.printStackTrace();
    }

    // ========= i18n 版方法（*T：key 首参，命中语言表翻译，未命中回退 fallback） =========

    public void traceT(String i18nKey, String fallback, Object... args) {
        if (enabled(5)) print(ANSI_CYAN, true, i18nKey, fallback, args);
    }

    public void debugT(String i18nKey, String fallback, Object... args) {
        if (enabled(4)) print(ANSI_CYAN, true, i18nKey, fallback, args);
    }

    public void infoT(String i18nKey, String fallback, Object... args) {
        if (enabled(3)) print(ANSI_GREEN, false, i18nKey, fallback, args);
    }

    public void warnT(String i18nKey, String fallback, Object... args) {
        if (enabled(2)) print(ANSI_YELLOW, false, i18nKey, fallback, args);
    }

    public void errorT(String i18nKey, String fallback, Object... args) {
        if (enabled(1)) print(ANSI_RED, false, i18nKey, fallback, args);
    }

    public void errorT(Throwable throwable, String i18nKey, String fallback, Object... args) {
        if (!enabled(1)) return;
        print(ANSI_RED, false, i18nKey, fallback, args);
        if (throwable != null) throwable.printStackTrace();
    }

    // ========= 便捷单参（消息原样，不做占位符替换） =========
    public void trace(String msg) { print(ANSI_CYAN, true, null, msg); }
    public void debug(String msg) { print(ANSI_CYAN, true, null, msg); }
    public void info(String msg) { print(ANSI_GREEN, false, null, msg); }
    public void warn(String msg) { print(ANSI_YELLOW, false, null, msg); }
    public void error(String msg) { print(ANSI_RED, false, null, msg); }
    public void error(String msg, Throwable throwable) { error(throwable, msg); }

    // ========= Lombok 两个重载工厂 =========
    public static LogKit getLogger(Class<?> clazz) {
        return new LogKit("[HTTP-Over-MC]", clazz);
    }

    public static LogKit getLogger(Class<?> clazz, String topic) {
        return new LogKit("[" + topic + "]", clazz);
    }
}