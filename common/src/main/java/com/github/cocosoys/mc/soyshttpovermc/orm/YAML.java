package com.github.cocosoys.mc.soyshttpovermc.orm;

/**
 * YAML 存储门面（仿 dlz-db-core 的 {@code DB} 门面，存储为本地 YAML 文件）。
 * <pre>
 *   List&lt;User&gt; users = YAML.Pojo.select(User.class);
 *   User u = YAML.Pojo.get(User.class, "id-1");
 *   YamlConfiguration raw = YAML.Pojo.get(User.class);
 * </pre>
 * 由宿主插件 onEnable 调用 {@code YAML.Pojo.init(getDataFolder())} 装配；
 * 未装配时按默认 {@code data/} 目录懒初始化。
 */
public final class YAML {

    private YAML() {
    }

    /**
     * 基于 Pojo + 条件链操作 YAML 存储（仿 {@code DB.Pojo}）。
     */
    public static final YamlPojo Pojo = new YamlPojo();
}
