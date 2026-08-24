package soys.ihomepages.api;

import java.util.List;

/**
 * 公开首页管理门面：供第三方插件记录 / 切换 / 注销 / 查看主页。
 *
 * <p>获取方式：{@code soys.ihomepages.MyHomePages.getHomeApi()}（本模块初始化后可用，
 * 为 null 表示自定义主页已禁用或尚未初始化）。</p>
 *
 * <p>设计极简：ihomepage 只“记录不同位置的页面”，切换时把对应位置写入 SOYSHTTPOverMC 的
 * {@code web.home}（{@code config.yml}）并触发 reload，由框架的 {@code web.home} 机制伺服，
 * 不持有/不存储网页字节内容。</p>
 */
public interface HomeApi {

    /** 记录一个主页位置（相对/绝对路径或网络 URL）；不自动切换。 */
    void registerPage(String name, String spec);

    /**
     * 切换到指定主页：写 ihomepage {@code config.yml} 的 {@code homepage.current} +
     * 同步 SOYSHTTPOverMC {@code config.yml} 的 {@code web.home} + reload 使其生效。
     *
     * @return true 切换成功；false 名称不存在
     */
    boolean switchTo(String name);

    /** 注销指定主页。 */
    boolean unregister(String name);

    /** 注销全部主页，返回注销数量。 */
    int unregisterAll();

    /** 列出所有已注册主页名称（按注册顺序）。 */
    List<String> list();

    /** 当前主页名称（可能为 null）。 */
    String getCurrent();
}
