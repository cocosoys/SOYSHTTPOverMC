package soys.ihomepages.config;

/**
 * 内容配置源接口：读取 {@code home.yml} 并解析为 {@link HomeConfigEntity}。
 */
public interface IHomeConfigSource {

    /** 首次加载。 */
    void load();

    /** 热刷新（重新读取磁盘文件）。 */
    void refresh();

    /** 获取当前配置实体（未加载则触发加载）。 */
    HomeConfigEntity get();
}
