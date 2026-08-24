package soys.soyshttpovermc.enums;

/**
 * Bot 隐藏方式（config.yml {@code bot.hide-mode}）。
 *
 * <ul>
 *   <li>{@link #HIDEPLAYER} —— {@code hideplayer}：对真实玩家 {@code viewer.hidePlayer(target)}（当前实现）；</li>
 *   <li>{@link #PLAYERINFO_REMOVE} —— {@code playerinfo-remove}：向所有客户端发包把 Bot 从玩家列表移除（预留）。</li>
 * </ul>
 *
 * <p>统一管理插件与 {@code BotGuardian} 中记录该状态的字符串字段，避免魔法字符串散落。</p>
 */
public enum BotHideMode {

    HIDEPLAYER("hideplayer"),
    PLAYERINFO_REMOVE("playerinfo-remove");

    private final String configName;

    BotHideMode(String configName) {
        this.configName = configName;
    }

    /** 对应 config.yml 的字符串值。 */
    public String configName() {
        return configName;
    }

    /** 从配置字符串解析；null / 空白 / 未知值一律回退 {@link #HIDEPLAYER}（保持旧行为）。 */
    public static BotHideMode from(String raw) {
        if (PLAYERINFO_REMOVE.configName.equalsIgnoreCase(raw == null ? "" : raw.trim())) {
            return PLAYERINFO_REMOVE;
        }
        return HIDEPLAYER;
    }

    @Override
    public String toString() {
        return configName;
    }
}