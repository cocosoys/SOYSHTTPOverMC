package soys.soyshttpovermc.enums;

import soys.soyshttpovermc.i18n.I18n;

/**
 * 存储后端类型：
 * priority 决定主存储选取——所有已启用后端中 priority 最高者作为主存储（承担全部读操作），
 * 其余作为辅助存储被镜像写入（热备份）。固定优先级 MYSQL > SQLITE > YAML。
 */
public enum StorageType {

    YAML("yaml", 10, "YAML 文件"),
    SQLITE("sqlite", 20, "SQLite 数据库"),
    MYSQL("mysql", 30, "MySQL 数据库");

    private final String id;
    private final int priority;
    private final String displayName;

    StorageType(String id, int priority, String displayName) {
        this.id = id;
        this.priority = priority;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public int getPriority() {
        return priority;
    }

    public String getDisplayName() {
        return I18n.t("storage.type." + id, displayName);
    }

    /** 按 id 解析（忽略大小写）；未匹配返回 null。 */
    public static StorageType fromId(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase();
        for (StorageType type : values()) {
            if (type.id.equals(normalized)) return type;
        }
        return null;
    }
}