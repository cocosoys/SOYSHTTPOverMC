package com.dlz.db.core;

import com.dlz.db.convertor.rowMapper.IRowMapper;
import com.dlz.db.core.anno.ConnectionSupplier;
import com.dlz.db.core.anno.SqlAction;
import com.dlz.db.exception.DbException;
import com.dlz.db.modal.DB;
import com.dlz.db.modal.dto.ResultMap;
import com.dlz.db.util.DbConvertUtil;
import com.dlz.db.util.DbLogUtil;
import com.dlz.kit.exception.ValidateException;
import com.dlz.kit.fn.DlzFn2;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * SQL 执行器抽象：DLZ-DB 核心对 JDBC 操作的顶层接口。
 * <p>实现类由各 starter 提供：
 * <ul>
 *   <li>Spring Boot：基于 {@code JdbcTemplate}（{@code com.dlz.db.spring.SpringSqlExecutor}）</li>
 *   <li>Solon：基于自研 {@code SimpleJdbc}（规划中）</li>
 * </ul>
 * </p>
 * <p>用户通常无需直接使用，而是通过 {@code DB.Pojo} / {@code DB.Jdbc} / {@code DB.Sql} 等上层 API 访问。</p>
 *
 * @author dingkui
 * @since 7.0.0
 */
public interface ISqlExecutor {
    ConnectionSupplier getConnectionSupplier();

    default List<ResultMap> getList(String sql, Object... args) {
        return getList(sql, DB.Dynamic.getRowMapper(), args);
    }

    default <T> List<T> getList(String sql, IRowMapper<T> rowMapper, Object... args) {
        return doDb(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                NativeSqlUtil.bindArgs(ps, args);
                try (ResultSet rs = ps.executeQuery()) {
                    List<T> list = new ArrayList<>();
                    int rowNum = 0;
                    while (rs.next()) {
                        list.add(rowMapper.mapRow(rs, rowNum++));
                    }
                    return list;
                }
            }
        }, (t, r) -> DbLogUtil.generateSqlMessage(t, r, "getList", sql, args));
    }

    default int update(String sql, Object... args) {
        return doDb(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                NativeSqlUtil.bindArgs(ps, args);
                return ps.executeUpdate();
            }
        }, (t, r) -> DbLogUtil.generateSqlMessage(t, r, "update", sql, args));
    }

    default Long updateForId(String sql, Object... args) {
        return doDb(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                NativeSqlUtil.bindArgs(ps, args);
                ps.executeUpdate();
                // 1. 标准路径
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                } catch (SQLFeatureNotSupportedException ignore) {
                    // 驱动不支持（如部分版本的 SQLite），走兜底
                    DbLogUtil.debug("无标准自动增长主键", ignore);
                }
                // 2. SQLite 等兜底：按方言查最后插入 ID
                Long id = NativeSqlUtil.queryLastInsertIdByDialect(conn);
                if (id == null) {
                    throw new DbException("无自动增长主键", 1002);
                }
                return id;
            }
        }, (t, r) -> DbLogUtil.generateSqlMessage(t, r, "updateForId", sql, args));
    }
// ============== 默认工具方法 ==============

    default ResultMap getOne(String sql, boolean checkOne, Object... args) {
        List<ResultMap> list = getList(sql, args);
        if (list.isEmpty()) {
            return null;
        }
        if (checkOne && list.size() > 1) {
            throw new DbException("查询结果为多条", 1004);
        }
        return list.get(0);
    }

    default <T> T getFirstColumn(String sql, Class<T> requiredType, Object... args) {
        return DbConvertUtil.getFirstColumn(getOne(sql, false, args), requiredType);
    }
    default int[] batch(String sql, List<Object[]> batchArgs) {
        return doDb(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (Object[] args : batchArgs) {
                            NativeSqlUtil.bindArgs(ps, args);
                            ps.addBatch();
                        }
                        return ps.executeBatch();
                    }
                },
                (t, r) -> DbLogUtil.generateSqlMessage(t, "batch", sql, batchArgs));
    }


    default HashMap<String, Integer> getTableColumnsInfo(String tableName) {
        if (tableName == null || !NativeSqlUtil.TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new ValidateException("非法表名: " + tableName);
        }
        // 使用 WHERE 1=0 而非 LIMIT 0，确保跨数据库兼容性（Oracle不支持LIMIT语法）
        String sql = "select * from " + tableName + " where 1=0";
        return doDb(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                HashMap<String, Integer> infos = new HashMap<>();
                ResultSetMetaData md = rs.getMetaData();
                int count = md.getColumnCount();
                for (int i = 1; i <= count; i++) {
                    // getColumnLabel 优先返回 AS 别名，无别名时返回列名
                    // toUpperCase 统一处理不同数据库的大小写差异：
                    // - Oracle: 默认返回大写
                    // - PostgreSQL: 默认返回小写
                    // - MySQL: 保持原始大小写
                    String columnName = md.getColumnLabel(i);
                    if (columnName == null || columnName.isEmpty()) {
                        // 兜底：如果 getColumnLabel 返回空，使用 getColumnName
                        columnName = md.getColumnName(i);
                    }
                    infos.put(columnName.toUpperCase(Locale.ROOT), md.getColumnType(i));
                }
                return infos;
            }
        }, null);
    }

    /**
     * 带连接的执行：自动复用事务连接或创建/关闭短连接。
     */
    default <T> T doDb(SqlAction action,
                       DlzFn2<Long, T, String> msg
    ) {
        long t = System.currentTimeMillis();
        T re = null;
        Exception err = null;
        try (Connection conn = getConnectionSupplier().get()) {
            re = (T) action.run(conn);
            return re;
        } catch (Exception e) {
            err = e;
            if (e instanceof DbException) {
                throw (DbException) e;
            }
            throw new DbException("sql执行错误:"+e.getMessage(), 1001,e);
        } finally {
            if (msg != null) {
                DbLogUtil.logInfo(msg, t, re, err);
            }
        }
    }

//    /**
//     * 统一的 SQL 执行包装：日志、耗时、异常转换。
//     *
//     * @param s   真实执行逻辑
//     * @param msg 日志消息生成器（耗时 + 结果 -> 消息）
//     */
//    default <T> T doDb(Supplier<T> s, DlzFn2<Long, T, String> msg) {
//        if (msg == null) {
//            return s.get();
//        }
//        long t = System.currentTimeMillis();
//        T re = null;
//        Exception err = null;
//        try {
//            re = s.get();
//            return re;
//        } catch (Exception e) {
//            err = e;
//            if (e instanceof DbException) {
//                throw e;
//            }
//            throw new DbException("sql执行错误:", 1001);
//        } finally {
//            DbLogUtil.logInfo(msg, t, re, err);
//        }
//    }
}
