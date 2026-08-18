package com.dlz.db.convertor.rowMapper;

import java.sql.*;

/**
 * JDBC 原始值读取工具。
 * <p>用于替代 Spring 的 {@code org.springframework.jdbc.support.JdbcUtils}，保证核心模块零 Spring 依赖。</p>
 */
public final class JdbcValueUtils {

    private JdbcValueUtils() {
    }

    /**
     * 获取列名（优先 columnLabel，退回 columnName）。
     * 与 Spring {@code JdbcUtils.lookupColumnName} 行为一致。
     */
    public static String lookupColumnName(ResultSetMetaData rsmd, int columnIndex) throws SQLException {
        String name = rsmd.getColumnLabel(columnIndex);
        if (name == null || name.isEmpty()) {
            name = rsmd.getColumnName(columnIndex);
        }
        return name;
    }

    /**
     * 读取 JDBC 值，处理常见厂商/类型差异：
     * <ul>
     *   <li>CLOB → String</li>
     *   <li>BLOB → byte[]</li>
     *   <li>Oracle {@code oracle.sql.TIMESTAMP} → {@link java.sql.Timestamp}</li>
     *   <li>列元数据声明为 Timestamp 但实际是 Date → {@link java.sql.Timestamp}</li>
     * </ul>
     * 与 Spring {@code JdbcUtils.getResultSetValue} 行为一致。
     */
    public static Object getResultSetValue(ResultSet rs, int index) throws SQLException {
        Object obj = rs.getObject(index);
        if (obj == null) {
            return null;
        }

        // CLOB 统一转字符串
        if (obj instanceof Clob) {
            return rs.getString(index);
        }
        // BLOB 统一转字节数组
        if (obj instanceof Blob) {
            return rs.getBytes(index);
        }

        String className = obj.getClass().getName();

        // Oracle 专用时间类型
        if (className.startsWith("oracle.sql.TIMESTAMP") || className.startsWith("oracle.sql.DATE")) {
            String metaClassName = rs.getMetaData().getColumnClassName(index);
            if ("java.sql.Timestamp".equals(metaClassName) || "oracle.sql.TIMESTAMP".equals(metaClassName)) {
                return rs.getTimestamp(index);
            }
            return rs.getDate(index);
        }

        // 列元数据声明为 Timestamp 但 getObject 返回 Date 的情况
        if (obj instanceof Date) {
            String metaClassName = rs.getMetaData().getColumnClassName(index);
            if ("java.sql.Timestamp".equals(metaClassName)) {
                return rs.getTimestamp(index);
            }
        }
        return obj;
    }
}
