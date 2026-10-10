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
| 4 工具 | `getToolkit()` | JSON、Content-Type、链接消息、前缀家族、契约原语 |
| 5 HTTP 客户端 | `getHttpClient()` | 对外请求、本地回环、环境自适配 |
| 6 扩展接入 | `getExtension()` | 登录提供者、子指令、拦截器、自定义策略 |
| 7 数据运维 | `getDataRegistration()` | 数据层自动化运维（安装/更新/重装/清理事务，见第 5 章） |

另：`registerReloadHook(ReloadHttpConfigHandler)` 注册热重载钩子（/soyshttp reload 时随本插件刷新自身配置，等价实现 `ReloadHttpConfigHandler` 接口）。前缀/契约原语（`serverPrefix()` / `apiPrefix()` / `scheme()` 等）不在总门面，统一在能力组 4 `ApiToolkitApi`。

## A.2 能力组 1：ApiRegistrationApi

| 方法 | 说明 |
| --- | --- |
| `void registerController(Object controller)` | 注册控制器（非主插件自动加 `/plugins/<插件名>` 前缀；另有 `(Object, Plugin owner)` / `(Object, boolean force)` / `(Object, Plugin, boolean)` 重载） |
| `void registerProxyController(Object controller)` | 以主插件名义代理注册（无插件名前缀；同样有 owner/force 重载） |
| `List<ApiInfo> unregisterController(Object controller)` | 卸载该控制器全部端点，返回被卸载的端点快照 |
| `List<ApiInfo> unregisterPluginControllers(String pluginName)` | 卸载指定插件名的全部端点，返回快照 |
| `void setPermissionService(PermissionService service)` | 接入自定义权限判定服务 |
| `PermissionService getPermissionService()` | 当前权限服务 |
| `List<ApiInfo> getRegisteredApis()` | 全部端点快照 |
| `String getApiPrefix()` | 全局前缀（/api） |

`ApiInfo`：`method` / `path` / `apiName` / `permission` / `ownerClass` / `ownerPlugin`。

## A.3 能力组 2：WebPageApi

> 返回类型：**所有登记方法返回 `WebRegistry.Entry`**（失败返回 `null`），批量目录登记返回 `Set<Entry>`；另含极简便捷登记区（单/双参数，owner 沿调用栈自动识别）。

**极简登记区**（接口最上方）：

| 方法 | 说明 |
| --- | --- |
| `Entry registerPage(byte[] content)` | path 自动 = `web/plugins/<插件>/page/page-<序号>`，Content-Type 默认 `text/html; charset=utf-8` |
| `Entry registerPage(String resourcePath)` | jar 内资源，path = `web/plugins/<插件>/page/<文件名不含后缀>` |
| `Entry registerPage(String path, byte[] content)` | 显式路径 + 内容（自动补 `/web/plugins/<插件名>` 前缀） |
| `Entry registerResource(String resourcePath)` / `(String path, String resourcePath)` | jar 内资源（惰性读取） |
| `Entry registerProxyPage(String path, byte[] content)` / `registerProxyResource(String path, String resourcePath)` | 无 `/web/plugins/<插件名>` 前缀 |
| `Entry registerErrorPage(byte[] content)` / `(String html)` | 默认 404 错误页 |
| `Set<Entry> registerDirectory(File dir)` | basePath 自动 = `web/plugins/<插件>/<目录名>`，批量磁盘目录 |

**完整版登记**（显式 owner）：

| 方法 | 说明 |
| --- | --- |
| `Entry registerPage(Plugin, String path, byte[] content [, String contentType] [, boolean force])` | 网页（Content-Type 按扩展名推断或显式指定） |
| `Entry registerResource(Plugin, String path, ClassLoader, String resourcePath [, String contentType] [, boolean force])` | jar 内资源（按需读取） |
| `Entry registerProxyPage(Plugin, String path, byte[] content [, String contentType])` / `registerProxyResource(Plugin, String path, ClassLoader, String resourcePath [, String contentType])` | 无插件名前缀版本 |
| `Set<Entry> registerDirectory(Plugin, String basePath, File dir [, boolean proxy])` | 磁盘目录批量（递归，惰性读盘，支持热替换） |
| `Set<Entry> registerResourceDirectory(Plugin, String basePath, ClassLoader, String resourceRoot [, boolean proxy])` | jar 资源目录批量 |
| `Entry registerErrorPage(Plugin, int status, byte[] content)` / `(Plugin, int status, String html)` | 自定义错误页 |
| `Entry registerNetworkPage(Plugin, NetworkPage page)` | 网络页（自定义加载/加密传输） |
| `NetworkTransport registerNetworkTransport(NetworkTransport)` | 登记网络传输提供者（当前仅占位存储 + 告警，未接入加载链路） |
| `LargeFileLoader registerLargeFileLoader(LargeFileLoader)` | 注册自定义大文件加载器（返回实例） |
| `LargeFileLoader setDefaultLargeFileLoader(String name)` / `setLargeFileLoader(String pathPrefix, String name)` | 切换默认 / 按路径前缀指定 |
| `void unregisterPluginPages(String pluginName)` / `int unregisterByTag(String tag)` / `void unregisterCors(String pluginName)` | 按插件名 / 来源 tag / CORS 卸载 |
| `void setIndexRule(String ownerName, boolean enabled, String indexFile)` / `removeIndexRule(String ownerName)` | 目录索引兜底规则（默认全局启用，目标 `index`） |
| `void setSpaFallback(String ownerName, boolean enabled)` / `removeSpaFallback(String ownerName)` | SPA 回退声明（无扩展名路径回退 index.html，带扩展名仍 404） |

> 非 GET 静态响应、昵称+描述+**权限**等全参重载在 `WebRegistry` 层提供（如 `registerPage(owner, path, httpMethod, content, contentType, force, desc, nicknames, permissions)` / `registerCors(...)` / `registerRedirect(...)` / `registerProxyRedirect(...)`），门面 `WebPageApi` 未逐位转发——需要这些能力时直接操作 `WebRegistry`（详见第 3 章 3.2 / 3.5）。

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
| `String scheme()` | 传输协议（TLS 启用 → `https`，否则 `http`；与契约文件 `__SOYS_CONTEXT__.js` 的 scheme 同源） |
| `String host()` | 服务器对外地址（`mc.public-host` → `mc.host` → server-ip → `localhost` 回退） |
| `int port()` | 服务器对外端口（`mc.public-port` → `mc.port` → server-port → `25565` 回退） |
| `boolean spaFallback(String pluginName)` | 插件是否声明 SPA 回退（前端据此决定 history/hash 模式；未声明/注册表不可用 → false） |
| `String pageBase(String pluginName)` | vue-router base：主插件 `/`；附属插件 = `pageFullPrefix + "/"`（与契约 pageBase 同源） |
| `boolean fpEnabled()` | 设备指纹双因子开关（auth.yml `auto.login.fp.enabled`；未接入登录桥 → false） |
| `boolean fpStrict()` | 设备指纹严格模式（auth.yml `auto.login.fp.strict`；未接入登录桥 → 默认 true） |

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

全部位于 `com.github.cocosoys.mc.soyshttpovermc.api.event`（主插件 `api/event` 包），均为**抽象基类下嵌套类**：

- `SoysReadyEvent`（`getApi()`）、`HttpConfigReloadEvent` —— 顶层类；
- `ApiEvent` 下：`ApiRegisteredEvent` / `ApiUnregisteredEvent`（`getApis()` → `List<ApiInfo>`）、
  `ApiAccessEvent`（`getPlayerName()`/`getPlayer()`/`getAsyncPlayer()`/`getRequestParams()`/`getBody()`，子类
  `ApiGetEvent`/`ApiPostEvent`/`ApiPutEvent`/`ApiDeleteEvent`/`ApiPatchEvent`/`ApiOtherEvent`）、
  `ApiAccessCompletedEvent`（追加 `getResponseBody()`/`getStatusCode()`/`getReason()`/`getHeaders()`，子类
  `ApiGetCompletedEvent` 等）—— 注册/卸载为同步，访问/完成经网关切回主线程发射；
- `GatewayEvent` 下：`GatewayRequestEvent` / `GatewayRequestServedEvent`（`getStatusCode()`/`getLatencyMs()`）、
  `GatewayAccessDeniedEvent`（`getStatusCode()`/`getPolicyName()`）、`GatewayCredentialIssuedEvent`、
  `GatewayLoginResultEvent`（`getPlayer()`/`isSuccess()`/`getReason()`/`getIp()`）；
- `WebResourcesEvent` 下：`WebResourcesAccessEvent`（`getPath()`/`isHtml()`/`isJs()`/`getResult()`，可跳转拦截/替换）、
  `WebResourcesLoadedEvent`（`getResources()` → `List<WebResourceAccess>`）。

## A.9 配置索引（全部真实路径）

| 文件 | 关键节点 |
| --- | --- |
| `config.yml` | `upload` / `mc.public-host` / `mc.public-port` / `mc.trust-proxy` / `proxy.server-name` / `proxy.proxy-address` / `sniffer` / `http-backend.mode` / `log.level` / `permission.providers` / `permission.offline-fallback` / `storage.*` / `auto.ops.*` |
| `pages.yml` | `web.root` / `web.home` / `web.cache.*` / `web.large-file-*` / `pages.page` / `pages.auto` / `pages.alias` / `permissions` |
| `language.yml` | `current` / `rule` / `sources` |
| `EULA.yml` | `eula`（true 同意） |
| `gateway/config.yml` | `enabled` / `api-prefix` / `debug-events` |
| `gateway/https.yml` | `enabled` / `keystore` / `cert` / `key` / `enabled-protocols` / `min-tls` |
| `gateway/policies/tls.yml` | `enabled` / `host` |
| `gateway/policies/ip-allowlist.yml` | `default` / `list` / `trust-proxy` |
| `gateway/policies/auth.yml` | `enabled` / `header` / `login-provider` / `api-key.local-fallback-all` / `paths` / `exempt` / `accept.*` / `auto.login.ttl.*` / `auto.login.ip.enabled` / `auto.login.fp.*` / `auto.login.ticket.*`（静态 `keys` 已移除，走本地表 `soys_api_key`） |
| `gateway/policies/rate-limit.yml` | `scope` / `rpm` / `burst` |
| `gateway/policies/access-limiter.yml` | `path-patterns`（name/scope/limit/window-seconds） |
| `gateway/issuers/session-token.yml` | `enabled` / `cookie-name` / `ttl-seconds` / `clock-skew-seconds` |

## A.10 命令速查

```bash
/soyshttp eula | status | report | reload | help [子指令|页码]
/soyshttp key <主体>
/soyshttp send <url|/page> [显示文字] [玩家]
/soyshttp pages [all] [页码]
/soyshttp api [插件名]
/soyshttp tokens
/soyshttp lang [代码]            # lang sources on|off|download|update|remove|info <索引>
/soyshttp log [级别]             # OFF|ERROR|WARN|INFO|DEBUG|TRACE
/soyshttp perm group|user|check|reload ...
/soyshttp apikey                 # X-API-Key 本地表管理（生成/启停/过期/绑定/权限）
/soyshttp migrate <后端> <后端> [confirm]     # ORM 后端迁移（合并语义）
/soyshttp sync [<from> <to> [confirm]]        # 后端覆盖迁移（无参=主→全部辅助）
/soyshttp data <插件> status|update [版本]|reinstall|uninstall   # 数据层自动化运维
```

简写 `/shttp`；主权限 `soyshttp.admin`（默认 OP）。
