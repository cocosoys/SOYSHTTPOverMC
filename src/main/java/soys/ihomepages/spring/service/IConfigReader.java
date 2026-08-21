package soys.ihomepages.spring.service;

/**
 * 逻辑配置读取接口：读取独立 {@code ihomepages/config.yml}（仅 enabled 等逻辑开关）。
 */
public interface IConfigReader {

    /** 功能是否启用（config.yml 的 enabled）。 */
    boolean isEnabled();

    /** 配置 JSON 缓存秒数（0 = 不缓存）。 */
    int cacheSeconds();
}
