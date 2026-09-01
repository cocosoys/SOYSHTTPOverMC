package com.github.cocosoys.mc.soyshttpovermc.enums;

/**
 * 语言加载策略（config.yml {@code language.rule}）。
 *
 * <ul>
 *   <li>{@link #CLEAR} —— {@code clear}：清空当前语言实例后再重新加载；</li>
 *   <li>{@link #OVERLAY} —— {@code overlay}：不清空，将新加载内容对既有同键覆盖、其余保持不变；</li>
 *   <li>{@link #INTERNATIONALIZATION} —— {@code internationalization}（默认）：以 {@code en_us} 为基底，
 *       每次重载先加载一次 en_us，再叠加目标语言（含额外语言源），确保目标语言文件不齐全的翻译项
 *       均由 en_us 保底。</li>
 * </ul>
 *
 * <p>统一管理 {@code I18n} 中的语言加载策略字符串字段，避免魔法字符串散落。</p>
 */
public enum LanguagePolicy {

    CLEAR("clear"),
    OVERLAY("overlay"),
    INTERNATIONALIZATION("internationalization");

    private final String configName;

    LanguagePolicy(String configName) {
        this.configName = configName;
    }

    /**
     * 对应 config.yml 的字符串值。
     */
    public String configName() {
        return configName;
    }

    /**
     * 从配置字符串解析；null / 空白 / 未知值一律回退 {@link #INTERNATIONALIZATION}（默认策略）。
     */
    public static LanguagePolicy from(String raw) {
        if (raw == null) {
            return INTERNATIONALIZATION;
        }
        String s = raw.trim().toLowerCase();
        if (CLEAR.configName.equals(s)) {
            return CLEAR;
        }
        if (OVERLAY.configName.equals(s)) {
            return OVERLAY;
        }
        return INTERNATIONALIZATION;
    }

    @Override
    public String toString() {
        return configName;
    }
}