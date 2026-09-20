package com.github.cocosoys.mc.soyshttpovermc.enums;

/**
 * 自动运维元数据状态（{@code soys_schema_meta.state} 字段取值）。
 * 落库值为 {@link #code()}（大写英文，兼容既有数据）。
 */
public enum SchemaState {

    /** 已安装（记录存在即视为已初始化）。 */
    INSTALLED("INSTALLED"),

    /** 已卸载（数据保留；预留）。 */
    UNINSTALLED("UNINSTALLED");

    private final String code;

    SchemaState(String code) {
        this.code = code;
    }

    /**
     * 落库代码（如 {@code "INSTALLED"}）。
     */
    public String code() {
        return code;
    }

    /**
     * 按代码解析（忽略大小写）；未匹配返回 null。
     */
    public static SchemaState fromCode(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toUpperCase();
        for (SchemaState s : values()) {
            if (s.code.equals(normalized)) {
                return s;
            }
        }
        return null;
    }
}
