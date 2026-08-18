package com.dlz.db.convertor.rowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 行映射器：将 {@link ResultSet} 的当前行映射为对象。
 * <p>DLZ-DB 自定义接口，不依赖 Spring JDBC。函数签名与 Spring 的 {@code org.springframework.jdbc.core.RowMapper} 兼容，
 * 便于后续按需做适配桥接。</p>
 *
 * @param <T> 映射产出的对象类型
 */
@FunctionalInterface
public interface IRowMapper<T> {

    /**
     * 将 {@link ResultSet} 当前游标所在行映射为对象。
     *
     * @param rs     已就绪的 ResultSet（调用方负责游标定位）
     * @param rowNum 当前行号（0 开始）
     * @return 映射后的对象
     * @throws SQLException JDBC 访问异常
     */
    T mapRow(ResultSet rs, int rowNum) throws SQLException;
}
