package com.dlz.db.convertor.dbtype;

import com.dlz.db.core.ISqlExecutor;
import com.dlz.db.support.PojoCache;
import com.dlz.kit.util.ValUtil;
import lombok.AllArgsConstructor;

import java.sql.Types;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unused") // sqlExecutor 保留供未来扩展使用
public class TableColumnMapper implements ITableColumnMapper {
    @Override
    public Object converObj4Db(String tableName, String columnName, Object value) {
        Map<String, Integer> map = PojoCache.getTableColumnsInfo(tableName);
        if (map != null) {
            Integer dbClass = map.get(columnName.toUpperCase(Locale.ROOT));
            if(dbClass==null){
                return value;
            }
            return cover(dbClass, value);
        }
//		final List<ResultMap> list = dao.getList("select * from information_schema.columns where table_name = '" + tableName + "'", new ResultMapRowMapper(new ColumnNameCamel()));
        return value;
    }

    public static Object cover(Integer dbClass, Object obj) {
        switch (dbClass) {
            // 整数族：统一 Long，避免 Integer 溢出问题，也避免 BigDecimal 的 overkill
            case Types.TINYINT:
                if(obj instanceof Boolean){
                    return (Boolean)obj ? 1 : 0;
                }
                return ValUtil.toInt(obj);
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
                return ValUtil.toLong(obj);

            // 定点/高精度小数：必须 BigDecimal
            case Types.DECIMAL:
            case Types.NUMERIC:
                return ValUtil.toBigDecimal(obj);

            // 浮点：保持 Double，保留 Infinity/NaN 语义，也避免 PG 的精度回差
            case Types.FLOAT:
            case Types.REAL:
            case Types.DOUBLE:
                return ValUtil.toDouble(obj);
            case Types.CHAR:
            case Types.LONGVARCHAR:
            case Types.VARCHAR:
            case Types.CLOB:
            case Types.BLOB:
                return ValUtil.toStr(obj);
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
                return ValUtil.toDate(obj);
            default:
                break;
        }
        return obj;
    }
}
