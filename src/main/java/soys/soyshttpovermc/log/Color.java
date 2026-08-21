package soys.soyshttpovermc.log;

import org.bukkit.ChatColor;

/**
 * 日志调色板：继承 Bukkit 原本的 {@link ChatColor} 16 标准色（标准 RGB 值），
 * 并以 24bit 真彩色 ANSI 转义输出到控制台；同时提供彩虹渐变所需的 HSV→RGB 辅助。
 *
 * <p>{@link #ENABLE_ANSI} 为全局开关：关闭后所有上色方法退化为纯文本（便于写入日志文件 / 非终端环境）。</p>
 *
 * <p>颜色输出/渐变控制逻辑见 {@link StringColor}；本类只负责<b>颜色代码</b>的定义与 ANSI 编码。</p>
 */
public enum Color {

    BLACK(0x000000),
    DARK_BLUE(0x0000AA),
    DARK_GREEN(0x00AA00),
    DARK_AQUA(0x00AAAA),
    DARK_RED(0xAA0000),
    DARK_PURPLE(0xAA00AA),
    GOLD(0xFFAA00),
    GRAY(0xAAAAAA),
    DARK_GRAY(0x555555),
    BLUE(0x5555FF),
    GREEN(0x55FF55),
    AQUA(0x55FFFF),
    RED(0xFF5555),
    LIGHT_PURPLE(0xFF55FF),
    YELLOW(0xFFFF55),
    WHITE(0xFFFFFF);

    private final int rgb;

    Color(int rgb) {
        this.rgb = rgb;
    }

    /** 打包的 0xRRGGBB 值。 */
    public int rgb() {
        return rgb;
    }

    public int r() {
        return (rgb >> 16) & 0xFF;
    }

    public int g() {
        return (rgb >> 8) & 0xFF;
    }

    public int b() {
        return rgb & 0xFF;
    }

    /** 是否输出 ANSI 转义（关闭后所有上色方法退化为纯文本）。 */
    public static boolean ENABLE_ANSI = true;

    /** ANSI 重置码（关闭时为空串）。 */
    public static String reset() {
        return ENABLE_ANSI ? "\u001B[0m" : "";
    }

    /** 由 r/g/b 生成 24bit 前景色 ANSI 码（关闭时为纯文本）。 */
    public static String fg(int r, int g, int b) {
        if (!ENABLE_ANSI) return "";
        return "\u001B[38;2;" + r + ';' + g + ';' + b + 'm';
    }

    /** 由本枚举色生成前景色 ANSI 码。 */
    public static String fg(Color c) {
        return c == null ? "" : fg(c.r(), c.g(), c.b());
    }

    /** 由 HSV 转换为 RGB，返回 {@code [r,g,b]}（供渐变色相旋转）。 */
    public static int[] hsvToRgb(float hue, float sat, float val) {
        float c = val * sat;
        float hh = ((hue % 360f) + 360f) % 360f;
        float x = c * (1f - Math.abs((hh / 60f) % 2f - 1f));
        float m = val - c;
        float r = 0, g = 0, b = 0;
        int sector = ((int) (hh / 60f)) % 6;
        switch (sector) {
            case 0: r = c; g = x; break;
            case 1: r = x; g = c; break;
            case 2: g = c; b = x; break;
            case 3: g = x; b = c; break;
            case 4: r = x; b = c; break;
            default: r = c; b = x; break;
        }
        return new int[]{(int) ((r + m) * 255f), (int) ((g + m) * 255f), (int) ((b + m) * 255f)};
    }

    /** 继承 Bukkit 调色板：由 {@link ChatColor} 取对应颜色；非标准色（如 RESET/格式码）返回 {@code null}。 */
    public static Color of(ChatColor c) {
        if (c == null) return null;
        switch (c) {
            case BLACK: return BLACK;
            case DARK_BLUE: return DARK_BLUE;
            case DARK_GREEN: return DARK_GREEN;
            case DARK_AQUA: return DARK_AQUA;
            case DARK_RED: return DARK_RED;
            case DARK_PURPLE: return DARK_PURPLE;
            case GOLD: return GOLD;
            case GRAY: return GRAY;
            case DARK_GRAY: return DARK_GRAY;
            case BLUE: return BLUE;
            case GREEN: return GREEN;
            case AQUA: return AQUA;
            case RED: return RED;
            case LIGHT_PURPLE: return LIGHT_PURPLE;
            case YELLOW: return YELLOW;
            case WHITE: return WHITE;
            default: return null;
        }
    }
}