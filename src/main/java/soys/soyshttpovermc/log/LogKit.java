package soys.soyshttpovermc.log;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 统一日志门面：全插件日志一律经此类打印，支持运行时级别过滤与热重载。
 *
 * <p>级别（由高到低过滤）：OFF &gt; ERROR &gt; WARN &gt; INFO &gt; DEBUG &gt; TRACE（默认 INFO）。
 * <ul>
 *   <li>OFF   —— 关闭所有输出；</li>
 *   <li>ERROR —— 仅严重错误（severe）；</li>
 *   <li>WARN  —— 错误 + 警告；</li>
 *   <li>INFO  —— 以上 + 常规运行信息（启动/策略加载/请求事件等）；</li>
 *   <li>DEBUG —— 以上 + 调试明细；</li>
 *   <li>TRACE —— 以上 + 最细粒度追踪。</li>
 * </ul>
 *
 * <p>配置：主 config.yml 的 {@code log.level}（/soyshttp reload 热重载）。
 * 消息格式由调用方给定（规范为 {@code [HTTP-Over-MC] [模块] 内容}），本类不追加前缀，
 * 避免与既有消息重复；Bukkit 日志本身已带 {@code [SOYSHTTPOverMC]} 插件名。
 */
public final class LogKit {

    public static final int OFF = 0;
    public static final int ERROR = 1;
    public static final int WARN = 2;
    public static final int INFO = 3;
    public static final int DEBUG = 4;
    public static final int TRACE = 5;

    private static volatile Logger logger = Logger.getLogger("SOYSHTTPOverMC");
    private static volatile int level = INFO;

    private LogKit() {
    }

    /** 绑定插件 Logger 并设置级别（onEnable 时调用）。 */
    public static void init(Logger pluginLogger, String levelName) {
        if (pluginLogger != null) {
            logger = pluginLogger;
        }
        setLevel(levelName);
    }

    /** 热重载级别（/soyshttp reload 时调用）；非法值回退 INFO。 */
    public static void setLevel(String name) {
        int lv = parse(name);
        level = lv;
        // OFF 表示完全静默：连切换提示本身也不输出（避免"关了还冒一行"的违和）；
        // 其余级别照常打印确认行（命令方的 sender.sendMessage 也会回显，不依赖此行）。
        if (lv != OFF) {
            logger.info("[HTTP-Over-MC] 日志级别 -> " + name(lv));
        }
    }

    public static int currentLevel() {
        return level;
    }

    /** 当前级别名（OFF/ERROR/WARN/INFO/DEBUG/TRACE） */
    public static String levelName() {
        return name(level);
    }

    public static boolean isDebugEnabled() {
        return level >= DEBUG;
    }

    // 注意：不用 logger.fine()——Bukkit 的 Logger/ConsoleHandler 默认级别为 INFO，
    // FINE 消息会被 JUL 层直接丢弃。级别过滤统一由本类完成，DEBUG/TRACE 走 logger.info 输出。
    public static void trace(String msg) {
        if (level >= TRACE) logger.info(fmt(msg));
    }

    public static void debug(String msg) {
        if (level >= DEBUG) logger.info(fmt(msg));
    }

    public static void debug(String msg, Throwable t) {
        if (level >= DEBUG) logger.log(Level.INFO, fmt(msg), t);
    }

    public static void info(String msg) {
        if (level >= INFO) logger.info(fmt(msg));
    }

    public static void warn(String msg) {
        if (level >= WARN) logger.warning(fmt(msg));
    }

    public static void warn(String msg, Throwable t) {
        if (level >= WARN) logger.log(Level.WARNING, fmt(msg), t);
    }

    public static void error(String msg) {
        if (level >= ERROR) logger.severe(fmt(msg));
    }

    public static void error(String msg, Throwable t) {
        if (level >= ERROR) logger.log(Level.SEVERE, fmt(msg), t);
    }

    /** 统一前缀：消息已带 [HTTP-Over-MC] 则不重复添加 */
    private static String fmt(String msg) {
        if (msg == null) return "[HTTP-Over-MC] null";
        return msg.startsWith("[HTTP-Over-MC]") ? msg : "[HTTP-Over-MC] " + msg;
    }

    private static int parse(String name) {
        if (name == null) return INFO;
        String s = name.trim().toUpperCase();
        if (s.isEmpty()) return INFO;
        switch (s) {
            // YAML 1.1 把未加引号的 OFF 解析为布尔 false，getString 会得到 "false"，
            // 这里兜底映射回 OFF，避免用户写 `level: OFF` 时静默退化为 INFO。
            case "OFF":
            case "FALSE": return OFF;
            case "ERROR":
            case "SEVERE": return ERROR;
            case "WARN":
            case "WARNING": return WARN;
            case "INFO": return INFO;
            case "DEBUG":
            case "FINE": return DEBUG;
            case "TRACE":
            case "FINER":
            case "FINEST":
            case "ALL": return TRACE;
            default: return INFO;
        }
    }

    private static String name(int lv) {
        switch (lv) {
            case OFF: return "OFF";
            case ERROR: return "ERROR";
            case WARN: return "WARN";
            case DEBUG: return "DEBUG";
            case TRACE: return "TRACE";
            default: return "INFO";
        }
    }
}
