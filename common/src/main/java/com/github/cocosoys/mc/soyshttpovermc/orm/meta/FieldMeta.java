package com.github.cocosoys.mc.soyshttpovermc.orm.meta;

import com.dlz.db.annotation.TableField;
import com.dlz.db.annotation.TableId;

import java.lang.reflect.Field;

/**
 * 实体字段元数据（与 dlz-db-core PojoCache 共享 {@code com.dlz.db.annotation} 注解）。
 */
public class FieldMeta {

    public final Field field;
    public final String fieldName;
    public final String columnName;      // 存储键名/列名（列名转换器输出或 @TableField.value）
    public final boolean primaryKey;     // @TableId
    public final boolean ignored;        // @TableField(exist=false)
    public final Class<?> type;          // 字段类型

    FieldMeta(Field field, String columnName, boolean primaryKey) {
        this.field = field;
        this.fieldName = field.getName();
        this.columnName = columnName;
        this.primaryKey = primaryKey;
        this.type = field.getType();
        TableField tf = field.getAnnotation(TableField.class);
        this.ignored = tf != null && !tf.exist();
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public boolean isIgnored() {
        return ignored;
    }

    /**
     * 是否为标量类型（YAML 直接存标量 / SQL 直接映射列）；否则为嵌套类型（YAML Section / SQL JSON）。
     */
    public boolean isScalar() {
        Class<?> t = type;
        return t == String.class || t == Integer.class || t == int.class
                || t == Long.class || t == long.class || t == Double.class || t == double.class
                || t == Float.class || t == float.class || t == Boolean.class || t == boolean.class
                || t == java.util.Date.class || t.isEnum();
    }

    /**
     * 表主键标识（@TableId 存在与否；供 PojoMeta 主键检索）。
     */
    boolean annotatedId() {
        return field.getAnnotation(TableId.class) != null;
    }
}
