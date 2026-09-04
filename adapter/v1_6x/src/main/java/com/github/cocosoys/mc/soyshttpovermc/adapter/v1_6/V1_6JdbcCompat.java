package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_6;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 1.6.x 专用 JDBC 兼容：为服务端自带的 JDBC3 旧驱动补上
 * {@code Connection.isValid(int)}，无需手工 patch 服务端 jar。
 *
 * <p><b>背景</b>：1.6.4 / 1.7.10 服务端自带的 sqlite（org.sqlite.Conn）与 mysql
 * （com.mysql.jdbc.ConnectionImpl）驱动均为 JDBC3 时代（class major 49），未实现
 * {@code Connection.isValid(int)}。core 的 {@code SqlStorage.connection()/keepAlive()}
 * 与 HikariCP 都会调用 {@code isValid}，底层实现类缺方法 → AbstractMethodError。</p>
 *
 * <p><b>兼容方式</b>（参考 HikariCP 的 ProxyConnection 思路）：把 DriverManager 中已注册的
 * sqlite/mysql 驱动注销，改注册本模块的 {@link IsValidDriver}（内部持有原驱动委托）。
 * 其 {@code connect()} 返回的 Connection 用 {@link java.lang.reflect.Proxy} 包装一层，
 * 对 {@code isValid(timeout)} 特判为 {@code !isClosed()}，其余方法原样转发到真实连接。</p>
 *
 * <p><b>为什么能覆盖两条路径</b>：SqlStorage 与 Hikari 都经 DriverManager 拿连接；
 * 包装驱动注册后，后续 {@code DriverManager.getConnection} 优先命中本包装驱动，
 * 两者拿到的都是自带 isValid 的代理连接。</p>
 *
 * <p>本实现只在本模块（v1_6x / v1_6xJava7）内自持，不进入 core/common；
 * 与 v1_7x 不共享（各版本自行实现）。</p>
 */
final class V1_6JdbcCompat {

    private V1_6JdbcCompat() {
    }

    private static volatile boolean installed = false;

    /**
     * 安装 JDBC 兼容包装（幂等，可重复调用）。
     *
     * <p>先显式加载服务端自带驱动（触发其静态块向 DriverManager 注册），
     * 再遍历已注册驱动，把 sqlite / mysql 的原驱动替换为 {@link IsValidDriver}。</p>
     */
    static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        // 确保服务端自带驱动已注册（静态块 registerDriver），漏网则忽略
        loadIfPresent("org.sqlite.JDBC");
        loadIfPresent("com.mysql.jdbc.Driver");
        loadIfPresent("com.mysql.cj.jdbc.Driver");
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            String cn = driver.getClass().getName();
            if (cn.startsWith("org.sqlite.") || cn.startsWith("com.mysql.")) {
                try {
                    DriverManager.deregisterDriver(driver);
                    DriverManager.registerDriver(new IsValidDriver(driver));
                } catch (SQLException e) {
                    // 单个驱动包装失败不阻断其余驱动
                }
            }
        }
    }

    private static void loadIfPresent(String className) {
        try {
            Class.forName(className);
        } catch (Throwable ignored) {
            // 服务端未携带该驱动时忽略（如 1.6.4 无 mysql cj 驱动）
        }
    }

    /**
     * 包装驱动：委托原驱动创建连接，返回自带 isValid 的代理连接。
     */
    private static final class IsValidDriver implements Driver {

        private final Driver delegate;

        IsValidDriver(Driver delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection connection = delegate.connect(url, info);
            if (connection == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    new IsValidHandler(connection));
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }

    /**
     * 代理连接处理：isValid 特判为 !isClosed()，其余方法原样转发。
     *
     * <p>转发时对 {@link InvocationTargetException} 解包，保持底层 SQLException
     * 等受检异常的原始语义（否则调用方 catch(SQLException) 接不住）。</p>
     */
    private static final class IsValidHandler implements InvocationHandler {

        private final Connection delegate;

        IsValidHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("isValid".equals(method.getName())) {
                try {
                    return Boolean.valueOf(!delegate.isClosed());
                } catch (SQLException e) {
                    return Boolean.FALSE;
                }
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
