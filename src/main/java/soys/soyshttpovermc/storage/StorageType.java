package soys.soyshttpovermc.storage;

/**
 * 数据存储后端类型（多数据库抽象，参考 SOYSMyLoot 的 StorageType）。
 */
public enum StorageType {

    /** 未启用任何后端（内存运行） */
    NONE("none"),
    /** MySQL（URL 直连） */
    MYSQL("mysql");

    private final String id;

    StorageType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static StorageType fromId(String id) {
        if (id == null) return NONE;
        for (StorageType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return NONE;
    }
}
