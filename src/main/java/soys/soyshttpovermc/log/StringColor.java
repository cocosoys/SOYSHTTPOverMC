package soys.soyshttpovermc.log;

/**
 * 字符串上色控制：单色、两色/多色渐变、彩虹色。所有方法对 {@code null} / 空串直接原样返回，
 * 且在 {@link Color#ENABLE_ANSI} 关闭时退化为纯文本。本类只负责<b>颜色应用逻辑</b>，
 * 颜色代码/ANSI 编码由 {@link Color} 提供。
 */
public final class StringColor {

    private StringColor() {
    }

    /** 整串单色（含 ANSI 重置）。 */
    public static String colorize(String text, Color c) {
        if (text == null || text.isEmpty() || c == null) return text;
        return Color.fg(c) + text + Color.reset();
    }

    /** 两色渐变：逐字符在 {@code from}→{@code to} 之间线性插值。 */
    public static String gradient(String text, Color from, Color to) {
        if (text == null || text.isEmpty() || from == null || to == null) return text;
        return gradient(text, new Color[]{from, to});
    }

    /** 多色渐变：跨给定色板均匀过渡。 */
    public static String gradient(String text, Color... colors) {
        if (text == null || text.isEmpty() || colors == null || colors.length == 0) return text;
        if (colors.length == 1) return colorize(text, colors[0]);
        StringBuilder sb = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            float t = (float) i / Math.max(len - 1, 1) * (colors.length - 1);
            int a = (int) t;
            float w = t - a;
            if (a >= colors.length - 1) {
                a = colors.length - 2;
                w = 1f;
            }
            Color c0 = colors[a];
            Color c1 = colors[Math.min(a + 1, colors.length - 1)];
            int r = (int) (c0.r() * (1 - w) + c1.r() * w);
            int g = (int) (c0.g() * (1 - w) + c1.g() * w);
            int b = (int) (c0.b() * (1 - w) + c1.b() * w);
            sb.append(Color.fg(r, g, b)).append(text.charAt(i));
        }
        sb.append(Color.reset());
        return sb.toString();
    }

    /** 彩虹色：HSV 色相在 0°→360° 上逐字符旋转。 */
    public static String rainbow(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            float hue = (float) i / Math.max(len - 1, 1) * 360f;
            int[] rgb = Color.hsvToRgb(hue, 1f, 1f);
            sb.append(Color.fg(rgb[0], rgb[1], rgb[2])).append(text.charAt(i));
        }
        sb.append(Color.reset());
        return sb.toString();
    }
}