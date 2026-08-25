package com.github.cocosoys.mc.soyshttpovermc.orm.meta;

import com.dlz.db.annotation.TableField;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体元数据缓存（与 dlz-db-core 的 {@code PojoCache} 共享同一套 {@code com.dlz.db.annotation} 注解）：
 * 解析 {@code @TableName/@TableId/@TableField}，缓存表名/字段列表/列名映射/主键，
 * 双后端共用（YAML 端=文件根节点名+节点键名；SQL 端=表名+列名）。
 * <p><b>一套注解双端通解</b>：{@code @TableName("users")} 既是 SQL 表名也是 YAML 文件名
 * （data/users.yml 根节点 users）；{@code @TableField(exist=false)} 双端均忽略该字段。</p>
 */
public class PojoMeta {

    private static final Map<Class<?>, PojoMeta> CACHE = new ConcurrentHashMap<>();

    private final Class<?> beanClass;
    private final String tableName;
    private final List<FieldMeta> fields;
    private final Map<String, FieldMeta> byColumn = new ConcurrentHashMap<>();
    private final Map<String, FieldMeta> byField = new ConcurrentHashMap<>();
    private FieldMeta idField;

    private PojoMeta(Class<?> beanClass) {
        this.beanClass = beanClass;
        this.tableName = resolveTableName(beanClass);
        this.fields = resolveFields(beanClass);
        for (FieldMeta fm : fields) {
            byColumn.put(fm.columnName, fm);
            byField.put(fm.fieldName, fm);
            if (fm.isPrimaryKey() && idField == null) {
                idField = fm;
            }
        }
    }

    // ===== 解析 =====

    private static String resolveTableName(Class<?> c) {
        TableName tn = c.getAnnotation(TableName.class);
        if (tn != null && tn.value() != null && !tn.value().trim().isEmpty()) {
            return tn.value().trim();
        }
        // 默认：类名驼峰转小写（UserInfo → userinfo）
        return c.getSimpleName().toLowerCase();
    }

    private static List<FieldMeta> resolveFields(Class<?> c) {
        List<FieldMeta> list = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) {
                continue;
            }
            TableId tid = f.getAnnotation(TableId.class);
            TableField tf = f.getAnnotation(TableField.class);
            boolean pk = tid != null;
            if (tf != null && !tf.exist()) {
                continue; // @TableField(exist=false)：双端均忽略该字段
            }
            String column;
            if (tid != null && tid.value() != null && !tid.value().isEmpty()) {
                column = tid.value();
            } else if (tf != null && tf.value() != null && !tf.value().isEmpty()) {
                column = tf.value();
            } else {
                column = ColumnNameConvertor.camelToUnderline(f.getName());
            }
            list.add(new FieldMeta(f, column, pk));
        }
        return list;
    }

    // ===== 缓存访问 =====

    public static PojoMeta of(Class<?> beanClass) {
        return CACHE.computeIfAbsent(beanClass, PojoMeta::new);
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    /** 表名（YAML=文件根节点名；SQL=表名）。 */
    public String getTableName() {
        return tableName;
    }

    public List<FieldMeta> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public FieldMeta getIdField() {
        return idField;
    }

    public boolean hasId() {
        return idField != null;
    }

    public FieldMeta byColumn(String column) {
        return byColumn.get(column);
    }

    public FieldMeta byField(String fieldName) {
        return byField.get(fieldName);
    }

    // ===== 便捷（Query 条件链用） =====

    /** 字段名 → 列名（lambda 条件解析后转列名）。 */
    public static String columnOf(Class<?> beanClass, String fieldName) {
        PojoMeta meta = of(beanClass);
        FieldMeta fm = meta.byField(fieldName);
        return fm == null ? fieldName : fm.columnName;
    }

    /** 列名 → 字段（YAML 内存过滤取列值用）。 */
    public static Field fieldOfColumn(Class<?> beanClass, String column) {
        PojoMeta meta = of(beanClass);
        FieldMeta fm = meta.byColumn(column);
        return fm == null ? null : fm.field;
    }

    /** 列名转换器（Camel ↔ Underline，借鉴 dlz convertor/columnname）。 */
    public static final class ColumnNameConvertor {
        private ColumnNameConvertor() {
        }

        /** userName → user_name。 */
        public static String camelToUnderline(String name) {
            if (name == null || name.isEmpty()) return name;
            StringBuilder sb = new StringBuilder(name.length() + 4);
            for (int i = 0; i < name.length(); i++) {
                char ch = name.charAt(i);
                if (Character.isUpperCase(ch) && i > 0) {
                    sb.append('_').append(Character.toLowerCase(ch));
                } else {
                    sb.append(Character.toLowerCase(ch));
                }
            }
            return sb.toString();
        }
    }
}
