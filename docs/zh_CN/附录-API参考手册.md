# 附录 API参考手册

本附录按"能力组"汇总开放给第三方开发者的全部 API，签名与行为均对照当前源码核实。

## A.1 总门面：SoysHttpOverMcApi

获取方式：

```java
SoysHttpOverMcApi api = HttpOverMcPlugin.getInstance().getApi();
// 事件场景：SoysReadyEvent e -> e.getApi()
```

| 能力组 | 获取方法 | 职责 |
| --- | --- | --- |
| 1 API 注册 | `getApiRegistration()` | 注册/卸载注解式控制器、权限服务 |
| 2 网页托管 | `getWebPage()` | 注册网页/资源/目录/网络页/错误页/CORS |
| 3 鉴权凭证 | `getAuthCredential()` | 注册颁发器、签发凭证 |
| 4 工具 | `getToolkit()` | JSON、Content-Type、链接消息 |
| 5 HTTP 客户端 | `getHttpClient()` | 对外请求、本地回环、环境自适配 |
| 6 扩展接入 | `getExtension()` | 登录提供者、子指令、拦截器、自定义策略 |

其它门面能力：`registerReloadHook(Runnable)`、`serverPrefix()`、`apiPrefix()`、`getConfigSection(...)` 等。

## A.2 能力组 1：ApiRegistrationApi

| 方法 | 说明 |
| --- | --- |
| `void registerController(Object controller)` | 注册控制器（非主插件自动加 `/plugins/<插件名>` 前缀） |
| `void registerController(Object controller, Plugin owner)` | 显式指定所属插件 |
| `void registerController(Object controller, boolean force)` | force=true 强制覆盖重复路由 |
| `void registerProxyController(Object controller)` | 以主插件名义代理注册（无插件名前缀） |
| `void unregisterController(Object controller)` | 卸载该控制器全部端点 |
| `void unregisterPluginControllers(String pluginName)` | 卸载指定插件名的全部端点 |
| `void setPermissionService(PermissionService service)` | 接入自定义权限判定服务 |
| `List<ApiInfo> getRegisteredApis()` | 全部端点快照 |
| `String getApiPrefix()` | 全局前缀（/api） |

`ApiInfo`：`method` / `path` / `apiName` / `permission` / `ownerClass` / `ownerPlugin`。

## A.3 能力组 2：WebPageApi

| 方法 | 说明 |
| --- | --- |
| `registerPage(Plugin, String path, byte[] content)` | 网页（Content-Type 按扩展名推断） |
| `registerPage(Plugin, String path, byte[] content, String contentType)` | 网页（显式 Content-Type） |
| `registerPage(Plugin, String path, byte[] content, String contentType, boolean force)` | force 覆盖 |
| `registerPage(Plugin, String path, HttpMethod method, byte[] content, String contentType, boolean force, String desc, List<String> nicknames)` | 非 GET 静态响应 + 昵称 |
| `registerResource(Plugin, String path, ClassLoader, String resourcePath)` | jar 内资源（按需读取） |
| `registerResource(..., String contentType)` / `(..., boolean force)` | 资源重载 |
| `registerProxyPage(...)` / `registerProxyResource(...)` | 无插件名前缀版本 |
| `registerDirectory(Plugin, String basePath, File dir)` / `(..., boolean proxy)` | 磁盘目录批量 |
| `registerResourceDirectory(Plugin, String basePath, ClassLoader, String resourceRoot)` / `(..., boolean proxy)` | jar 资源目录批量 |
| `registerNetworkPage(Plugin, NetworkPage page)` | 网络页（自定义加载/加密传输） |
| `registerLargeFileLoader(LargeFileLoader)` | 自定义大文件加载器 |
| `setDefaultLargeFileLoader(String name)` | 切换默认大文件加载器 |
| `setLargeFileLoader(String pathPrefix, String name)` | 按路径前缀指定 |
| `registerErrorPage(Plugin, int status, byte[] content)` | 自定义错误页 |
| `registerCors(Plugin, String pathPrefix, String origin, String methods, String headers, boolean credentials)` | CORS 声明 |
| `unregisterPluginPages(String pluginName)` | 卸载某插件名下全部网页 |

另：`registerPage(..., List<String> permissions)` / `registerProxyPage(..., permissions)` 可带 AND 语义权限（见第 3 章 3.5）。

## A.4 能力组 3：AuthCredentialApi

| 方法 | 说明 |
| --- | --- |
| `void registerCredentialIssuer(String name, Supplier<CredentialIssuer> factory)` | 注册凭证颁发器工厂（对应 gateway/issuers/<name>.yml） |
| `boolean isAuthEnabled()` | auth 策略是否启用 |
| `List<String> getIssuerNames()` | 已启用颁发器名 |
| `IssuedCredential issueCredential(String subject)` | 用首个启用颁发器签发 |
| `IssuedCredential issueCredential(String issuerName, String subject)` | 用指定颁发器签发 |
| `IssuedCredential issueCredential(String subject, Map<String,String> claims)` | 签发携带自定义 claims（保留键 sub/mode/exp/iat/jti/adm 不可用；键限 `[a-zA-Z0-9_-]{1,32}`、值限 256 字符） |
| `IssuedCredential issueCredential(String issuerName, String subject, Map<String,String> claims)` | 指定颁发器 + claims |

## A.5 能力组 4：ApiToolkitApi

| 方法 | 说明 |
| --- | --- |
| `String toJson(Object obj)` | 对象 → JSON（复用 JsonWriter） |
| `String guessContentType(String path)` | 扩展名 → Content-Type（复用 MimeTypes） |
| `void registerMimeType(String ext, String contentType)` | 注册/覆盖扩展名 Content-Type（全局，线程安全；自定义扩展名如 vue/ts/json5 需先注册浏览器才按正确类型渲染） |
| `void sendLink(Player, String url, String display)` | 发送可点击链接消息（display 支持 `%url%` / `%url_标签%`，`&` 代替 `§` 颜色码） |
| `void sendLink(Collection<? extends Player>, String url, String display)` | 批量发送 |
| `String apiPrefix()` | 全局 API 前缀（网关 api-prefix，默认 `/api`） |
| `String pluginsPrefix(String pluginName)` | 插件命名空间前缀（如 `/plugins/MCER`）；主插件自身 → 空串 |
| `String pluginsPrefix(Plugin plugin)` | 同上，传插件主类实例 |
| `String apiFullPrefix(String pluginName)` | `apiPrefix() + pluginsPrefix()` 完整路径前缀（API 正常登记，如 `/api/plugins/MCER`） |
| `String apiFullPrefix(Plugin plugin)` | 同上，传插件主类实例 |
| `String pageFullPrefix(String pluginName)` | 页面完整前缀 = "/web" + pluginsPrefix()（如 `/web/plugins/MCER`）；主插件 → 空串 |
| `String pageFullPrefix(Plugin plugin)` | 同上，传插件主类实例 |
| `String webResourcePrefix(String pluginName)` | Web 极简登记的页面 URL 前缀（`/web/plugins/<插件名>/page`） |
| `String webResourcePrefix(Plugin plugin)` | 同上，传插件主类实例 |
| `String serverPrefix()` | 群组服跨服前缀（如 `/server/lobby`）；独立服返回空串 |
| `String fullPathPrefix(String pluginName)` | 完整三段 = `serverPrefix() + apiFullPrefix()`（如 `/server/lobby/api/plugins/Foo`） |

## A.6 能力组 5：HttpClientApi

| 方法 | 说明 |
| --- | --- |
| `HttpResponse sendHttp(String method, String url, Map<String,String> headers, byte[] body)` | 对外真实请求（失败抛 `HttpClientException`） |
| `HttpResponse sendGet(String url)` / `sendGet(String url, Map headers)` | GET |
| `HttpResponse sendPost(String url, byte[] body)` / `sendPost(String url, Map headers, byte[] body)` | POST |
| `Object callLocalApi(String method, String path, Map headers, byte[] body)` | 本地回环（绕过网络，等价本地分发） |
| `Object sendApi(String method, String logicalPath, Map headers, byte[] body)` | resolveUrl 补全前缀后本地分发；注解层权限判定照常生效；未命中返回 null |
| `String resolveUrl(String logicalPath)` | 补全 /api + /server/<本服名>（群组服）+ /plugins/<插件名>（第三方） |
| `String getApiPrefix()` | 注解式 API 全局前缀 |
| `boolean isAuthEnabled()` | 网关 auth 是否开启 |

## A.7 能力组 6：ExtensionApi

| 方法 | 说明 |
| --- | --- |
| `void registerLoginProvider(LoginProvider provider)` | 注册登录插件提供者（建议检测到对应插件已加载后调用） |
| `void registerSubCommand(SubCommand subCommand)` | 注册 `/soyshttp` 子指令（宿主 onEnable 完成后调用） |
| `void registerWebInterceptor(WebInterceptor interceptor)` | 注册请求级拦截器（网关策略后、业务路由前，可改写/短路） |
| `void registerPolicy(SecurityPolicy policy)` | 注入自定义安全策略（按 order 排序，DENY 短路，reload 后保留） |

## A.8 事件 API（详见第 7 章）

`SoysReadyEvent`（`getApi()`）、`HttpConfigReloadEvent`、`GatewayRequestEvent`、`GatewayRequestServedEvent`（`getStatusCode()`/`getDurationMs()`）、`GatewayAccessDeniedEvent`（`getStatusCode()`/`getPolicyName()`）、`GatewayCredentialIssuedEvent`、`ApiRegisteredEvent`/`ApiUnregisteredEvent`（`getApiInfo()`）、`ApiAccessEvent` 及其子类（`ApiGetEvent` 等，`getPlayerName()`/`getPlayer()`）、`ApiAccessDeniedEvent`。全部位于 `com.github.cocosoys.mc.soyshttpovermc.api.event`（主插件 `api/event` 包），`GatewayEvent` 为抽象基类。

## A.9 配置索引（全部真实路径）

| 文件 | 关键节点 |
| --- | --- |
| `config.yml` | `upload` / `mc.public-host` / `mc.public-port` / `mc.trust-proxy` / `proxy.server-name` / `proxy.proxy-address` / `sniffer` / `http-backend.mode` / `log.level` / `permission.providers` / `permission.offline-fallback` / `storage.*` |
| `pages.yml` | `web.root` / `web.home` / `web.cache.*` / `web.large-file-*` / `pages.page` / `pages.auto` / `permissions` |
| `language.yml` | `current` / `rule` / `sources` |
| `EULA.yml` | `eula`（true 同意） |
| `gateway/config.yml` | `enabled` / `api-prefix` / `debug-events` |
| `gateway/https.yml` | `enabled` / `keystore` / `cert` / `key` / `enabled-protocols` / `min-tls` |
| `gateway/policies/tls.yml` | `enabled` / `host` |
| `gateway/policies/ip-allowlist.yml` | `default` / `list` / `trust-proxy` |
| `gateway/policies/auth.yml` | `enabled` / `header` / `login-provider` / `keys` / `paths` / `exempt` / `accept.*` / `auto.login.*` |
| `gateway/policies/rate-limit.yml` | `scope` / `rpm` / `burst` |
| `gateway/policies/access-limiter.yml` | `path-patterns`（name/scope/limit/window-seconds） |
| `gateway/issuers/session-token.yml` | `enabled` / `cookie-name` / `ttl-seconds` / `clock-skew-seconds` |

## A.10 命令速查

```
/soyshttp reload
/soyshttp key <主体>
/soyshttp send <url|/page> [显示文字] [玩家]
/soyshttp pages [all]
/soyshttp api
/soyshttp tokens
/soyshttp lang [代码]            # lang sources on|off|download|update|remove|info <索引>
/soyshttp perm group|user|check|reload ...
/soyshttp log-level <OFF|ERROR|WARN|INFO|DEBUG|TRACE>
/soyshttp migrate <来源> <目标>
/soyshttp sync
/soyshttp status | report | eula | help
```

简写 `/shttp`；主权限 `soyshttp.admin`（默认 OP）。
