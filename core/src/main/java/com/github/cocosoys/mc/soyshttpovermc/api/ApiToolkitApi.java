package com.github.cocosoys.mc.soyshttpovermc.api;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

/**
 * 能力组 4：工具（对象 → JSON、扩展名 → Content-Type、发送链接消息）。
 * 由 {@link SoysHttpOverMcApi#getToolkit()} 跳转获取。
 */
public interface ApiToolkitApi {

    /**
     * 任意对象 → JSON 字符串（复用 JsonWriter）
     */
    String toJson(Object obj);

    /**
     * 扩展名 → Content-Type（复用 MimeTypes）
     */
    String guessContentType(String path);

    /**
     * 注册/覆盖扩展名的 Content-Type（全局生效，线程安全）。自定义扩展名（如 vue / ts / json5）
     * 需先注册，浏览器才会按正确类型渲染/执行对应静态资源。
     *
     * @param ext         扩展名（不含点，如 {@code "vue"}）
     * @param contentType 完整 Content-Type（如 {@code "text/html; charset=utf-8"}）
     */
    void registerMimeType(String ext, String contentType);

    /**
     * 向玩家发送一条可点击链接消息（url 应为完整 URL；display 支持 %url% 与 %url_标签%（标签到下一个 % 结束，
     * 旧写法 %url_[标签]% 仍兼容），{@code &} 代替 {@code §} 颜色码）。详见 {@code soys.soyshttpovermc.util.LinkMessageUtil}。
     */
    void sendLink(Player player, String url, String display);

    /**
     * 批量向多名玩家发送同一条链接消息。
     */
    void sendLink(Collection<? extends Player> players, String url, String display);

    /**
     * 全局 API 前缀（网关配置 api-prefix，默认 /api）。
     * 注解式 API 与网页始终挂载在该前缀之下，auth 开关不影响其地址。
     */
    String apiPrefix();

    /**
     * 插件命名空间前缀：非主插件自动注册时网关会前置 {@code /plugins/<插件名>}；
     * 主插件自身（或代理注册）无此前缀，返回空字符串 {@code ""}。
     *
     * @param pluginName 插件名（如 {@code "MCER"}）
     * @return 如 {@code "/plugins/MCER"}；空 / 主插件 → {@code ""}
     */
    String pluginsPrefix(String pluginName);

    /**
     * 完整路径前缀 = apiPrefix() + pluginsPrefix(pluginName)。
     * 例：api-prefix=/api、插件 MCER → {@code "/api/plugins/MCER"}；主插件 → {@code "/api"}。
     * 用于拼写插件自身 API / 网页的实际访问地址，无需手写常量。
     */
    String fullPrefix(String pluginName);

    /**
     * 插件命名空间前缀（插件实例重载）：等价 {@link #pluginsPrefix(String)} 传 {@code plugin.getName()}。
     *
     * @param plugin 插件主类实例（JavaPlugin）
     * @return 如 {@code "/plugins/MCER"}；null / 主插件 → {@code ""}
     */
    String pluginsPrefix(Plugin plugin);

    /**
     * 完整路径前缀（插件实例重载）：等价 {@link #fullPrefix(String)} 传 {@code plugin.getName()}。
     * 开发者直接传入自身插件主类实例即可，无需再写插件名字符串。
     */
    String fullPrefix(Plugin plugin);

    /**
     * Web 极简登记的 jar 资源前缀（resourcePath 约定）：
     * 极简登记 {@code registerPage(content)} / {@code registerPage(resourcePath)} 自动生成的资源路径
     * 位于 {@code web/plugins/<插件名>/page/} 之下。这是插件 jar 内的资源存放位置（resourcePath），
     * 与 URL 访问前缀（{@link #fullPrefix(String)}）无关。
     *
     * @param pluginName 插件名（如 {@code "MCER"}）
     * @return 如 {@code "web/plugins/MCER/page/"}（含尾斜杠，可直接拼接文件名）；null / 空 → {@code ""}
     */
    String webResourcePrefix(String pluginName);

    /**
     * Web 极简 jar 资源前缀（插件实例重载）：等价 {@link #webResourcePrefix(String)} 传 {@code plugin.getName()}。
     *
     * @param plugin 插件主类实例（JavaPlugin）
     * @return 如 {@code "web/plugins/MCER/page/"}；null → {@code ""}
     */
    String webResourcePrefix(Plugin plugin);

    /**
     * 群组服跨服前缀：群组服（BungeeCord / Velocity）下为 {@code /server/<本服名>}
     * （本服名 = config.yml 的 proxy.server-name，用于跨服路由到本子服）；
     * 独立服无此概念，返回空字符串 {@code ""}。
     */
    String serverPrefix();

    /**
     * 完整三段拼接 = {@link #serverPrefix()} + {@link #fullPrefix(String)}。
     * 例：群组服插件 Foo → {@code /server/lobby/api/plugins/Foo}；独立服 → {@code /api/plugins/Foo}。
     * 用于拼写群组服下插件自身 API / 网页的完整访问地址。
     */
    String fullPathPrefix(String pluginName);
}
