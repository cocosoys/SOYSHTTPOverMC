package com.dlz.db.core;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 基于数据库号段模式的分布式 ID 生成器，支持零浪费拼接。
 *
 * <p>核心设计：
 * <ul>
 *   <li><b>乐观锁 CAS</b> — WHERE max_id = ? 条件避免多实例并发冲突</li>
 *   <li><b>零浪费拼接</b> — 本地号段不足时，旧号段剩余与新号段首部连续拼接，不丢弃</li>
 *   <li><b>自动建表 + 自愈</b> — 首次使用或表丢失时自动创建 table_id_segment，并从业务表 MAX(id) 接续</li>
 *   <li><b>多数据源跟随</b> — key 仅用 tableName，数据源切换自动路由到对应库</li>
 * </ul>
 */
@Slf4j
public class NativeSqlUtil {

    public static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+$");

    /**
     * 根据连接所属数据库的方言返回最后插入的自增 ID。
     * <p>仅作为 {@link PreparedStatement#getGeneratedKeys()} 不可用时的兜底。</p>
     */
    public static Long queryLastInsertIdByDialect(Connection conn) {
        String selectLastId;
        try {
            String product = conn.getMetaData().getDatabaseProductName();
            if (product == null) return null;
            String p = product.toLowerCase(Locale.ROOT);
            if (p.contains("sqlite")) {
                selectLastId = "SELECT last_insert_rowid()";
            } else if (p.contains("mysql") || p.contains("mariadb")) {
                selectLastId = "SELECT LAST_INSERT_ID()";
            } else if (p.contains("h2") || p.contains("hsql")) {
                selectLastId = "CALL IDENTITY()";
            } else {
                return null;
            }
        } catch (Exception e) {
            log.debug("获取数据库产品名失败", e);
            return null;
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(selectLastId)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.debug("方言兜底查询最后插入ID失败", e);
        }
        return null;
    }



    public static void bindArgs(PreparedStatement ps, Object... args) throws SQLException {
        if (args == null) return;
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }
}
