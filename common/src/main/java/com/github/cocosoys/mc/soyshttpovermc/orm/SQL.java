package com.github.cocosoys.mc.soyshttpovermc.orm;

/**
 * SQL 存储门面（与 {@link YAML} 门面对应，后端为 dlz-db-core + HikariCP）：
 * <pre>
 *   List&lt;User&gt; users = SQL.Pojo.select(User.class);
 *   User u = SQL.Pojo.get(User.class, "id-1");
 * </pre>
 * 由宿主插件 onEnable 装配（{@code SQL.Pojo} 在 {@code storage.backends.mysql/sqlite} 启用时可用）。
 */
public final class SQL {

    private SQL() {
    }

    /**
     * 基于 Pojo + 条件链操作 SQL 存储（仿 {@code YAML.Pojo}）。
     */
    public static final SqlPojo Pojo = new SqlPojo();
}
