package com.dlz.db.support;

import com.dlz.db.annotation.TableField;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import com.dlz.db.annotation.proxy.AnnoProxies;
import com.dlz.db.core.DlzDbProperties;
import com.dlz.db.ds.DBDynamic;
import com.dlz.db.modal.DB;
import com.dlz.db.modal.wrapper.WrapperBuildUtil;
import com.dlz.db.support.bean.IdInfo;
import com.dlz.db.util.DbConvertUtil;
import com.dlz.db.util.DbLogUtil;
import com.dlz.kit.cache.CacheMap;
import com.dlz.kit.exception.SystemException;
import com.dlz.kit.fn.DlzFn;
import com.dlz.kit.util.StringUtils;
import com.dlz.kit.util.ValUtil;
import com.dlz.kit.util.system.FieldReflections;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * bean与数据库表信息保持器
 *
 * @author dk
 */
@Slf4j
public class PojoCache {
    private static final CacheMap<Class<?>, String> tableNameCache = new CacheMap<>();
    private static final CacheMap<Field, String> columnNameCache = new CacheMap<>();
    private static final CacheMap<String, List<Field>> tableFieldCache = new CacheMap<>();
    private static final CacheMap<String, HashMap<String, Integer>> tableColumnsInfoCache = new CacheMap<>();
    private static final CacheMap<Class<?>, IdInfo> idFieldCache = new CacheMap<>();
    private static final CacheMap<Class<?>, Field> deletedFieldCache = new CacheMap<>();
    private static final CacheMap<String, String> idNameCache = new CacheMap<>();

    public static void clearAll() {
        tableColumnsInfoCache.clear();
    }

    /**
     * （带缓存）取得字段对应的数据库字段名
     *
     * @param field
     */
    public static String getColumnName(Field field) {
        return columnNameCache.getAndSet(field, () -> {
            String columnName;
            String fieldName = field.getName();
            // 检查我们自己的 @TableId 注解
            final TableId annotation = field.getAnnotation(TableId.class);
            if (annotation != null) {
                columnName = annotation.value();
            } else {
                columnName = AnnoProxies.MybatisPlusIdType.value(field);
            }

            if (StringUtils.isEmpty(columnName)) {
                TableField name = field.getAnnotation(TableField.class);
                if (name != null && name.exist()) {
                    columnName = name.value();
                }
            }
            if (StringUtils.isEmpty(columnName)) {
                TableField name = field.getAnnotation(TableField.class);
                if (name != null) {
                    if (name.exist() && StringUtils.isNotEmpty(name.value())) {
                        columnName = name.value();
                    }
                } else {
                    if (ValUtil.toBoolean(AnnoProxies.MybatisPlusTableField.exist(field), true)) {
                        columnName = AnnoProxies.MybatisPlusTableField.value(field);
                    }
                }
            }
            if (StringUtils.isEmpty(columnName)) {
                columnName = fieldName;
            }
            columnName = getColumnName(columnName);
            if (log.isDebugEnabled()) {
                log.debug("字段：{} 对应数据库字段：{}", field.getDeclaringClass().getName() + "." + fieldName, columnName);
            }
            return columnName;
        });
    }

    /**
     * 判断字段是否存在
     *
     * @param tableName  表名
     * @param columnName 字段名：支持bean字段名，数据库字段名
     * @author dk 2018-09-28
     */
    public static boolean isColumnExists(String tableName, String columnName) {
        Map<String, Integer> map = getTableColumnsInfo(tableName);
        if (map == null) {
            return false;
        }
        return map.containsKey(DbConvertUtil.toDbColumnName(columnName.replaceAll("`", "")).toUpperCase());
    }
    /**
     * 判断字段是否存在
     *
     * @param tableName  表名
     * @param columnName 字段名：支持bean字段名，数据库字段名
     * @author dk 2018-09-28
     */
    public static Integer getTableColumnType(String tableName, String columnName) {
        Map<String, Integer> map = getTableColumnsInfo(tableName);
        if (map == null) {
            return null;
        }
        return map.get(columnName.replaceAll("`", ""));
    }


    /**
     * bean字段名转换成数据库字段名
     *
     * @param field
     */
    public static String getColumnName(String field) {
        return DbConvertUtil.toDbColumnName(field);
    }

    /**
     * 根据bean取得表注释
     *
     * @param clazz
     */
    public static String getTableComment(Class<?> clazz) {
        ApiModel name = clazz.getAnnotation(ApiModel.class);
        if (name != null && StringUtils.isNotEmpty(name.value())) {
            return name.value().replaceAll("[\\\"'`]", "");
        }
        return null;
    }

    /**
     * 根据bean字段取得字段注释
     *
     * @param field
     */
    public static String getColumnComment(Field field) {
        ApiModelProperty name = field.getAnnotation(ApiModelProperty.class);
        if (name != null && StringUtils.isNotEmpty(name.value())) {
            return name.value().replaceAll("[\\\"\\\n'`]", "");
        }
        return null;
    }

    /**
     * 根据bean字段判断是否pk
     *
     * @param field
     */
    public static boolean isColumnPk(Field field) {
        TableId name = field.getAnnotation(TableId.class);
        if (name != null) {
            return true;
        }
        return field.getName().equals("id");
    }

    /**
     * （带缓存）根据bean取得表名
     *
     * @param clazz
     */
    public static String getTableName(Class<?> clazz) {
        return tableNameCache.getAndSet(clazz, () -> {
            TableName name = clazz.getAnnotation(TableName.class);
            String tName;
            if (name != null) {
                // 统一注解协调：@TableName 值原样作为表名（SQL 表名 = YAML 文件名根节点名，双端同名同构）
                tName = name.value();
            } else {
                tName = AnnoProxies.MybatisPlusTableName.value(clazz);
            }

            if (StringUtils.isEmpty(tName)) {
                tName = clazz.getSimpleName();
                tName = getColumnName(tName).replaceAll("^_", "");
            }
            return tName;
        });
    }

    /**
     * （带缓存）取得数据库表字段信息
     *
     * @param tableName
     */
    public static HashMap<String, Integer> getTableColumnsInfo(String tableName) {
        return tableColumnsInfoCache.getAndSet(tableName, () ->
                DBHolder.getSqlExecutor().getTableColumnsInfo(tableName)
        );
    }

    /**
     * （带缓存）根据bean取得数据库对应的字段信息，如果字段在表中不存在，则不返回
     *
     * @param beanClass
     */
    public static List<Field> getBeanFields(Class<?> beanClass) {
        String tableName = getTableName(beanClass);
        return tableFieldCache.getAndSet(tableName, () -> {
            HashMap<String, Integer> tableColumnsInfo = getTableColumnsInfo(tableName);
            if (tableColumnsInfo == null) {
                DbLogUtil.setCaller(1);
                log.warn("get tableColumnsInfo fail：" + tableName);
                return null;
            }
            if (tableColumnsInfo.isEmpty()) {
                DbLogUtil.setCaller(1);
                throw new SystemException("get tableColumnsInfo fail：" + beanClass.getName());
            }
            return FieldReflections.getFields(beanClass).stream()
                    .filter(field -> tableColumnsInfo.containsKey(getColumnName(field.getName())))
                    .collect(Collectors.toList());
        });
    }

    /**
     * （带缓存）取得 bean 的主键字段。
     * <p>优先级：{@code @TableId} 注解 → MyBatis-Plus 的 {@code @TableId} → 名为 {@code "id"} 的字段 → null。
     * <p>仅在 {@link #getBeanFields(Class)} 返回的表内字段中查找，避免命中 transient 字段。
     *
     * @param beanClass bean 类
     * @return 主键 Field；若不存在返回 null
     */
    public static Field getIdField(Class<?> beanClass) {
        final IdInfo idInfo = getIdInfo(beanClass);
        return idInfo == null ? null : idInfo.getField();
    }


    /**
     * 获取实体的主键信息
     *
     * @param clazz 实体类
     * @return 主键信息
     * @throws SystemException 如果未设置可辨识的主键
     */
    public static String getIdName(Class<?> clazz) {
        final IdInfo idInfo = getIdInfo(clazz);
        if (idInfo == null) {
            throw new SystemException(clazz.getSimpleName() + "未设置可辨识的主键");
        }
        return idInfo.getName();
    }

    /**
     * （带缓存）取得 bean 的主键字段。
     * <p>优先级：{@code @TableId} 注解 → MyBatis-Plus 的 {@code @TableId} → 名为 {@code "id"} 的字段 → null。
     * <p>仅在 {@link #getBeanFields(Class)} 返回的表内字段中查找，避免命中 transient 字段。
     *
     * @param beanClass bean 类
     * @return 主键 Field；若不存在返回 null
     */
    public static IdInfo getIdInfo(Class<?> beanClass) {
        return idFieldCache.getAndSet(beanClass, () -> {
            List<Field> fields = getBeanFields(beanClass);
            if (fields == null) {
                return null;
            }
            // 1) @TableId（本项目注解）
            for (Field f : fields) {
                final TableId annotation = f.getAnnotation(TableId.class);
                if (annotation != null) {
                    final IdInfo idInfo = new IdInfo(f, getColumnName(f));
                    idInfo.setType(annotation.type());
                    return idInfo;
                }
            }
            // 2) MyBatis-Plus @TableId（代理调用，未引入 MP 时返回空）
            for (Field f : fields) {
                if (StringUtils.isNotEmpty(AnnoProxies.MybatisPlusIdType.value(f))) {
                    final IdInfo idInfo = new IdInfo(f, getColumnName(f));
                    idInfo.setType(AnnoProxies.MybatisPlusIdType.type(f));
                    return idInfo;
                }
            }
            // 3) 名为 id 的字段
            for (Field f : fields) {
                if ("id".equals(f.getName())) {
                    return new IdInfo(f, getColumnName(f));
                }
            }
            return null;
        });
    }


    /**
     * （带缓存）取得 bean 的主键字段。
     * <p>优先级：{@code @TableId} 注解 → MyBatis-Plus 的 {@code @TableId} → 名为 {@code "id"} 的字段 → null。
     * <p>仅在 {@link #getBeanFields(Class)} 返回的表内字段中查找，避免命中 transient 字段。
     *
     * @param tableName bean 类
     * @return 主键 Field；若不存在返回 null
     */
    public static String getIdName(String tableName) {
        return idNameCache.getAndSet(tableName, () -> {
            final List<String> primaryKeys = DB.Dynamic.getSqlHelper().getTableInfo(tableName).getPrimaryKeys();
            if(primaryKeys == null || primaryKeys.isEmpty()){
                throw new SystemException("表["+tableName+"]无主键");
            }
            if(primaryKeys.size()>1){
                throw new SystemException("表["+tableName+"]为复合主键，不支持此操作");
            }
            return DbConvertUtil.toFieldName(primaryKeys.get(0));
        });
    }

    public static Field getLogicDeleteInfo(Class<?> beanClass,String logicDeleteFileName) {
        final Field field = deletedFieldCache.computeIfAbsent(beanClass,k -> {
            List<Field> fields = getBeanFields(beanClass);
            if (fields == null) {
                return null;
            }
            for (Field f : fields) {
                if (logicDeleteFileName.equalsIgnoreCase(getColumnName(f))) {
                    return f;
                }
            }
            return null;
        });
        return field;
    }

    /**
     * 根据 lambda 表达式取得数据库字段名
     *
     * @param column
     * @param <T>
     */
    public static <T> String fnName(DlzFn<T, ?> column) {
        return getColumnName(FieldReflections.getFn(column).v2);
    }
}