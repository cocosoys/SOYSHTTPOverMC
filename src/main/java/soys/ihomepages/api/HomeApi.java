package soys.ihomepages.api;

import java.util.List;

/**
 * 公开首页管理门面：供第三方插件注册/切换/注销/查看详情首页。
 * <p>获取方式：{@code soys.ihomepages.MyHomePages.getHomeApi()}（本模块初始化后可用，
 * 为 null 表示自定义主页已禁用或尚未初始化）。</p>
 *
 * <p>首页分两类：</p>
 * <ul>
 *   <li><b>字节型</b>：{@link #register(String, String, byte[], String)} —— 内容直接给出；</li>
 *   <li><b>来源型</b>：{@link #registerSource(String, String, String)} —— 内容为一个相对路径/绝对路径/网络 URL，
 *       切换时由模块解析（复用 SOYSHTTPOverMC 框架的 {@code HomePageResolver} 语义）。</li>
 * </ul>
 */
public interface HomeApi {

    /** 注册字节型首页（不自动切换）。 */
    void register(String name, String ownerPlugin, byte[] content, String contentType);

    /** 注册来源型首页（不自动切换）。 */
    boolean registerSource(String name, String ownerPlugin, String sourceSpec);

    /** 切换到指定首页并安装到 {@code GET /}；来源型首页解析失败返回 false。 */
    boolean switchTo(String name);

    /** 注销指定首页。 */
    boolean unregister(String name);

    /** 注销全部首页，返回注销数量。 */
    int unregisterAll();

    /** 列出所有已注册首页名称（按注册顺序）。 */
    List<String> list();

    /** 当前首页名称（可能为 null）。 */
    String getCurrent();

    /** 把当前首页选择持久化（服务器重启后自动恢复）。 */
    void persist(String name);
}