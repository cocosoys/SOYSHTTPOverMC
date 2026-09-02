package com.dlz.db.support.helper;

import com.dlz.db.annotation.TableName;
import com.dlz.db.modal.DB;
import com.dlz.db.support.PojoCache;
import com.dlz.db.support.resouce.DlzResourceLoader;
import com.dlz.kit.util.StringUtils;
import com.dlz.kit.util.system.FieldReflections;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

@Slf4j
public class HelperScan {
    public static void scan(String packageName) {
        if (StringUtils.isEmpty(packageName)) {
            return;
        }
        try {
            final SqlHelper helper = DB.Dynamic.getSqlHelper();
            Set<Class<?>> set = DlzResourceLoader.scan(packageName, TableName.class);
            set.stream().forEach(clazz -> initTable(clazz, helper));
        } catch (Exception e) {
            log.error("扫描包失败: {}", packageName, e);
        }
    }

    public static void initTable(Class<?> clazz, SqlHelper helper) {
        TableName table = clazz.getAnnotation(TableName.class);
        if (table != null) {
            String tableName = PojoCache.getTableName(clazz);
            Set<String> columns = helper.getTableColumnNames(tableName);
            if (columns.isEmpty()) {
                // 创建表
                helper.createTable(tableName, clazz);
                return;
            }
            // 建立字段
            List<Field> fields = FieldReflections.getFields(clazz);
            for (Field field : fields) {
                String columnName = PojoCache.getColumnName(field);
                if (columnName.equals("")
                        || columns.contains(columnName)
                        || columnName.equalsIgnoreCase("id")
                ) {
                    continue;
                }
                // 创建字段
                helper.createColumn(tableName, columnName, field);
            }
        }
    }
}
