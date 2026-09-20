package com.github.cocosoys.mc.soyshttpovermc.enums;

/**
 * 本地权限主体类型（{@code soys_perm_permission.owner_type} 字段取值）。
 * 落库值为 {@link #code()}（大写英文，兼容既有数据）。
 */
public enum SoysPermOwnerType {

    /** 主体类型：组（ownerId = 组名）。 */
    GROUP("GROUP"),

    /** 主体类型：用户（ownerId = 玩家 UUID）。 */
    USER("USER"),

    /** 主体类型：X-API-Key（本地表 soys_api_key，ownerId = 主键 id）。 */
    APIKEY("APIKEY");

    private final String code;

    SoysPermOwnerType(String code) {
        this.code = code;
    }

    /**
     * 落库代码（如 {@code "GROUP"}）。
     */
    public String code() {
        return code;
    }

    /**
     * 按代码解析（忽略大小写）；未匹配返回 null。
     */
    public static SoysPermOwnerType fromCode(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toUpperCase();
        for (SoysPermOwnerType t : values()) {
            if (t.code.equals(normalized)) {
                return t;
            }
        }
        return null;
    }
}
