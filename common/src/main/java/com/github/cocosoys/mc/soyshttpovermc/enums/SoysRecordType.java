package com.github.cocosoys.mc.soyshttpovermc.enums;

/**
 * 通用记录类型（{@code soys_records.type} 字段取值）。
 * 落库值为 {@link #code()}（大写英文，兼容既有数据）。
 */
public enum SoysRecordType {

    /** 令牌注销黑名单（key: {@code blacklist:<jti>}）。 */
    BLACKLIST("BLACKLIST"),

    /** 令牌签发审计（key: {@code audit:<jti>:<nonce>}，append-only）。 */
    AUDIT("AUDIT"),

    /** 实例心跳（key: {@code instance:<serverId>}）。 */
    INSTANCE("INSTANCE"),

    /** 全局 JWT 密钥（key: {@code meta:jwt_secret}）。 */
    META("META");

    private final String code;

    SoysRecordType(String code) {
        this.code = code;
    }

    /**
     * 落库代码（如 {@code "BLACKLIST"}）。
     */
    public String code() {
        return code;
    }

    /**
     * 按代码解析（忽略大小写）；未匹配返回 null。
     */
    public static SoysRecordType fromCode(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toUpperCase();
        for (SoysRecordType t : values()) {
            if (t.code.equals(normalized)) {
                return t;
            }
        }
        return null;
    }
}
