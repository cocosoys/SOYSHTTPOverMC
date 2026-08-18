package com.dlz.db.support;

import com.dlz.db.convertor.columnname.IColumnNameConvertor;
import com.dlz.db.convertor.columnname.IConvertorToFieldName;
import com.dlz.db.convertor.dbtype.ITableColumnMapper;
import com.dlz.db.ds.DataSourceConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据库执行，线程级配置
 */
@Slf4j
public class SqlRunThreadHolder {
    public static final ThreadLocal<DataSourceConfig> HOLDER_config = new ThreadLocal<>();
    public static final ThreadLocal<IConvertorToFieldName> HOLDER_ConvertorToFieldName = new ThreadLocal<>();
    public static final ThreadLocal<IColumnNameConvertor> HOLDER_ColumnNameConvertor = new ThreadLocal<>();
    public static final ThreadLocal<ITableColumnMapper> HOLDER_TableColumnMapper = new ThreadLocal<>();
    public static final ThreadLocal<Boolean> HOLDER_IgnoreLogicDelete = new ThreadLocal<>();
    public static DataSourceConfig getConfig() {
        return HOLDER_config.get();
    }
    public static void setConfig(DataSourceConfig config) {
        HOLDER_config.set(config);
    }
    public static void removeConfig() {
        HOLDER_config.remove();
    }

    public static IConvertorToFieldName getConvertorToFieldName(IConvertorToFieldName defaultConvertorToFieldName) {
        final IConvertorToFieldName iConvertorToFieldName = HOLDER_ConvertorToFieldName.get();
        if(iConvertorToFieldName == null){
            HOLDER_ConvertorToFieldName.set(defaultConvertorToFieldName);
            return defaultConvertorToFieldName;
        }
        return iConvertorToFieldName;
    }
    public static void setConvertorToFieldName(IConvertorToFieldName convertorToFieldName) {
        HOLDER_ConvertorToFieldName.set(convertorToFieldName);
    }
    public static void removeConvertorToFieldName() {
        HOLDER_ConvertorToFieldName.remove();
    }
    public static IColumnNameConvertor getColumnNameConvertor(IColumnNameConvertor defaultColumnNameConvertor) {
        final IColumnNameConvertor iColumnNameConvertor = HOLDER_ColumnNameConvertor.get();
        if(iColumnNameConvertor == null){
            HOLDER_ColumnNameConvertor.set(defaultColumnNameConvertor);
            return defaultColumnNameConvertor;
        }
        return iColumnNameConvertor;
    }
    public static void setColumnNameConvertor(IColumnNameConvertor convertor) {
        HOLDER_ColumnNameConvertor.set(convertor);
    }
    public static void removeColumnNameConvertor() {
        HOLDER_ColumnNameConvertor.remove();
    }
    public static ITableColumnMapper getTableColumnMapper(ITableColumnMapper defaultTableColumnMapper) {
        final ITableColumnMapper iTableColumnMapper = HOLDER_TableColumnMapper.get();
        if(iTableColumnMapper == null && defaultTableColumnMapper!=null){
            HOLDER_TableColumnMapper.set(defaultTableColumnMapper);
            return defaultTableColumnMapper;
        }
        return iTableColumnMapper;
    }
    public static void setTableColumnMapper(ITableColumnMapper tableColumnMapper) {
        HOLDER_TableColumnMapper.set(tableColumnMapper);
    }
    public static void removeTableColumnMapper() {
        HOLDER_TableColumnMapper.remove();
    }

    /**
     * 未设置默认支持逻辑删除
     * @return
     */
    public static Boolean isIgnoreLogicDelete() {
        final Boolean b = HOLDER_IgnoreLogicDelete.get();
        return b == Boolean.TRUE;
    }
    public static void setIgnoreLogicDelete(Boolean ignoreLogicDelete) {
        HOLDER_IgnoreLogicDelete.set(ignoreLogicDelete);
    }
    public static void removeLogicDeleteSetting() {
        HOLDER_IgnoreLogicDelete.remove();
    }
}
