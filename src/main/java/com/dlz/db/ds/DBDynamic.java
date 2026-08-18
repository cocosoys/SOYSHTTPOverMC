package com.dlz.db.ds;

import com.dlz.db.convertor.rowMapper.ResultMapRowMapper;
import com.dlz.db.enums.DbTypeEnum;
import com.dlz.db.exception.DbException;
import com.dlz.db.support.helper.SqlHelper;
import com.dlz.kit.exception.SystemException;
import com.dlz.kit.util.ExceptionUtils;
import com.dlz.kit.util.StringUtils;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 动态数据源上下文持有者
 * 使用 ThreadLocal 管理当前线程的数据源选择。
 * <p>本类只负责数据源的注册、切换、删除，不处理事务。事务由 {@link DBTx} 提供。</p>
 */
@Slf4j
public class DBDynamic {
    static final String DEFAULT_NAME = "default";
    final ThreadLocal<DataSourceConfig> HOLDER_config = new ThreadLocal<>();

    private final Map<String, DataSourceConfig> configPool = new ConcurrentHashMap<>();

    /**
     * 切换到指定数据源执行（不开启事务）
     */
    public <T> T use(String name, Supplier<T> c) {
        DataSourceConfig config = configPool.get(name);
        if (config == null) {
            throw new SystemException("数据源不存在: " + name);
        }
        DataSourceConfig previous = HOLDER_config.get();
        try {
            HOLDER_config.set(config);
            return c.get();
        } catch (Exception e) {
            if (e instanceof DbException) {
                throw (DbException) e;
            }
            throw new DbException("执行失败：" + e.getMessage(), 1003, e);
        } finally {
            if (previous == null) {
                HOLDER_config.remove();
            } else {
                HOLDER_config.set(previous);
            }
        }
    }

    /**
     * 切换到指定数据源执行（无返回值版本）
     */
    public void use(String name, Runnable r) {
        use(name, () -> {
            r.run();
            return null;
        });
    }
    /**
     * 获取当前线程的数据源配置（包级可见，供 {@link DBTx} 使用）
     */
    public DataSourceConfig getCurrentConfig() {
        DataSourceConfig config = HOLDER_config.get();
        if (config == null) {
            config = configPool.get(DEFAULT_NAME);
        }
        if (config == null) {
            throw new SystemException("数据源不存在:" + DEFAULT_NAME);
        }
        return config;
    }

    /**
     * 根据名称获取数据源配置（包级可见，供 {@link DBTx} 使用）
     */
    DataSourceConfig getConfigByName(String name) {
        DataSourceConfig config = configPool.get(name);
        if (config == null) {
            throw new SystemException("数据源不存在: " + name);
        }
        return config;
    }

    /**
     * 获取当前线程的数据源配置
     */
    private DataSourceConfig getConfig() {
        return getCurrentConfig();
    }

    /**
     * 获取当前线程的数据源
     */
    public DataSource getDataSource() {
        return getConfig().getDataSource();
    }

    /**
     * 获取当前线程的数据源名称
     */
    public String getUsedDataSourceName() {
        DataSourceConfig config = HOLDER_config.get();
        if (config == null) {
            return null;
        }
        return config.property.getName();
    }

    /**
     * 获取当前线程的数据源的列映射器
     */
    public ResultMapRowMapper getRowMapper() {
        return getConfig().getRowMapper();
    }

    /**
     * 获取当前线程的数据源名称
     */
    public DbTypeEnum getDbType() {
        return getConfig().getDbType();
    }

    /**
     * 获取当前线程的数据源名称
     */
    public SqlHelper getSqlHelper() {
        return getConfig().getSqlHelper();
    }


    /**
     * 更新数据源
     */
    public synchronized boolean setDefaultDataSource(DataSource dataSource) {
        if (dataSource != null) {
            final DataSourceProperty defaultProperties = new DataSourceProperty();
            String name = DEFAULT_NAME;
            defaultProperties.setName(name);
            final DataSourceConfig v = new DataSourceConfig(defaultProperties);
            v.setDataSource(dataSource);
            configPool.put(name, v);
            Connection connection = null;
            try {
                if (dataSource instanceof HikariDataSource) {
                    HikariDataSource hds = (HikariDataSource) dataSource;
                    defaultProperties.setUrl(hds.getJdbcUrl());
                    defaultProperties.setUsername(hds.getUsername());
                } else {
                    connection = dataSource.getConnection();
                    DatabaseMetaData metaData = connection.getMetaData();
                    defaultProperties.setDriverClassName(metaData.getDriverName());
                    defaultProperties.setUrl(metaData.getURL());
                    defaultProperties.setUsername(metaData.getUserName());
                    defaultProperties.setDbProductName(metaData.getDatabaseProductName());// 如 "MySQL", "Oracle", "PostgreSQL"
                }
            } catch (Exception e) {
                log.error("获取数据库类型失败: {}", name, e);
                // 忽略错误
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (Exception e) {
                        log.warn("关闭连接失败", e);
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 更新数据源
     */
    public synchronized boolean setDataSource(DataSourceProperty properties) {
        if (properties == null) {
            throw new SystemException("数据源配置不能为空");
        }
        String name = properties.getName();
        if (StringUtils.isEmpty(name)) {
            name = DEFAULT_NAME;
        }
        if (configPool.containsKey(name)) {
            // 关闭旧的数据源
            removeDataSource(name);
        }

        try {
            configPool.put(name, new DataSourceConfig(properties));
            if (name.equals(DEFAULT_NAME)) {
                log.warn("修改系统默认数据源: {}", properties.getUrl());
            }
            // 创建新的数据源
            return true;
        } catch (Exception e) {
            throw new RuntimeException("更新数据源失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除数据源
     */
    public synchronized boolean removeDataSource(String name) {
        DataSourceConfig config = configPool.remove(name);
        if (config != null) {
            try {
                config.close();
                return true;
            } catch (Exception e) {
                log.warn("关闭数据源时发生错误", e);
                return false;
            } finally {
                configPool.remove(name);
            }
        }
        return false;
    }

    /**
     * 测试数据源连接（不注册到 configPool，测完即关）。
     * <p>失败抛 {@link DbException}，成功返回 void。内部用 DriverManager 直连，不走连接池。</p>
     *
     * @param properties 数据源配置（url / username / password / driverClassName / testQuery）
     */
    public void testConnection(DataSourceProperty properties) {
        if (properties == null) {
            throw new SystemException("数据源配置不能为空");
        }
        if (StringUtils.isEmpty(properties.getUrl())) {
            throw new SystemException("数据源 url 不能为空");
        }
        java.sql.Connection conn = null;
        try {
            // 如指定驱动类则显式加载（兼容老驱动）
            if (StringUtils.isNotEmpty(properties.getDriverClassName())) {
                Class.forName(properties.getDriverClassName());
            }
            conn = java.sql.DriverManager.getConnection(
                    properties.getUrl(),
                    properties.getUsername(),
                    properties.getPassword()
            );
            // 简单校验连接可用
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute(properties.getTestQuery() != null ? properties.getTestQuery() : "SELECT 1");
            }
        } catch (Exception e) {
            throw new DbException("数据源连接测试失败: " + e.getMessage(), 1005, e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 获取所有数据源名称
     */
    public Set<String> getAllDataSourceNames() {
        return new HashSet<>(configPool.keySet());
    }

    /**
     * 获取当前线程的数据源名称
     */
    public DataSourceProperty getDataSourceProperty(String name) {
        DataSourceConfig config = configPool.get(name);
        if (config == null) {
            throw new SystemException("数据源不存在:" + name);
        }
        return config.property;
    }
}