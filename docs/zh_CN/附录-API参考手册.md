# 附录 API 参考手册

本附录集中列出 SOYSHTTPOverMC 开放给开发者的全部 API。所有方法签名均对照源码核实。

**获取总门面：**

```java
SoysHttpOverMcApi api = HttpOverMcPlugin.getInstance().getApi();
```

总门面 `SoysHttpOverMcApi` 提供 9 个能力组 getter：

| 方法 | 能力组 | 返回类型 |
| --- | --- | --- |
| `getApiRegistration()` | 注解式 API 注册 | `ApiRegistrationApi` |
| `getWebPage()` | 网页登记 | `WebPageApi` |
| `getAuthCredential()` | 鉴权与凭证 | `AuthCredentialApi` |
| `getToolkit()` | 工具（JSON / Content-Type） | `ApiToolkitApi` |
| `getLogger()` | 日志 | `PluginLoggerApi` |
| `getBotManagement()` | Bot 管理 | `BotManagementApi` |
| `getHttpClient()` | HTTP 请求 / 本地回环 | `HttpClientApi` |
| `getExtension()` | 扩展接入 | `ExtensionApi` |
| `getCrossServer()` | 跨服调用 | `CrossServerHttpClient` |

---

## A.1 ApiRegistrationApi（能力组 1：注解式 API 注册）

| 方法 | 说明 |
| --- | --- |
| `void registerController(Object instance)` | 注册注解式控制器（自动标记所属插件；非主插件自动加 `/plugins/<插件名>` 前缀） |
| `void registerController(Object instance, Plugin owner)` | 注册并显式指定所属插件 |
| `void registerProxyController(Object instance)` | 强制以主插件代理注册（无 `/plugins/<插件名>` 前缀，owner 仍标记真实插件） |
| `void registerProxyController(Object instance, Plugin owner)` | 强制代理注册并显式指定所属插件 |
| `List<ApiInfo> unregisterController(Object instance)` | 卸载某控制器实例注册的全部端点 |
| `List<ApiInfo> unregisterPluginControllers(String pluginName)` | 卸载指定插件名注册的全部端点 |
| `void setPermissionService(PermissionService ps)` | 接入权限判定服务（`@ApiPermission` 生效前提） |
| `PermissionService getPermissionService()` | 读取当前权限判定服务 |
| `String getApiPrefix()` | 网关全局 API 前缀（通常为 `/api`） |
| `List<ApiInfo> getRegisteredApis()` | 当前已注册端点快照（门面层） |

> 查看命令用的是 `ApiRegistry.listEndpoints()`（不是 `getRegisteredApis()`），二者命名不同。

---

## A.2 WebPageApi（能力组 2：网页登记）

| 方法 | 说明 |
| --- | --- |
| `void registerPage(Plugin owner, String path, byte[] content)` | 登记网页（byte[]） |
| `void registerPage(Plugin owner, String path, byte[] content, String contentType)` | 登记网页并指定 Content-Type |
| `void registerResource(Plugin owner, String path, ClassLoader cl, String resourcePath)` | 登记 jar 内单资源 |
| `void registerResource(Plugin owner, String path, ClassLoader cl, String resourcePath, String contentType)` | 登记 jar 内单资源（指定类型） |
| `void registerProxyPage(Plugin owner, String path, byte[] content)` | 强制代理无前缀登记网页 |
| `void registerProxyPage(Plugin owner, String path, byte[] content, String contentType)` | 同上（指定类型） |
| `void registerProxyResource(Plugin owner, String path, ClassLoader cl, String resourcePath)` | 强制代理无前缀登记资源 |
| `void registerProxyResource(Plugin owner, String path, ClassLoader cl, String resourcePath, String contentType)` | 同上（指定类型） |
| `void registerDirectory(Plugin owner, String basePath, File dir)` | 批量登记磁盘目录（递归；请求时惰性读，支持热替换） |
| `void registerDirectory(Plugin owner, String basePath, File dir, boolean proxy)` | 批量登记磁盘目录（显式是否强制代理无前缀） |
| `void registerResourceDirectory(Plugin owner, String basePath, ClassLoader cl, String resourceRoot)` | 批量登记 jar 内资源目录 |
| `void registerResourceDirectory(Plugin owner, String basePath, ClassLoader cl, String resourceRoot, boolean proxy)` | 批量登记 jar 内资源目录（显式代理） |
| `void registerNavItem(Plugin owner, String label, String path)` | 登记门户导航项（默认 order=100） |
| `void registerNavItem(Plugin owner, String label, String path, String icon, String permission, int order)` | 登记导航项（含图标/权限/排序） |
| `void unregisterPluginPages(String pluginName)` | 卸载指定插件名登记的全部网页 |
| `void registerLargeFileLoader(LargeFileLoader loader)` | 注册自定义大文件加载器（同名覆盖） |
| `boolean setDefaultLargeFileLoader(String loaderName)` | 切换全局默认大文件加载器（未知名返回 false） |
| `void setLargeFileLoader(String pathPrefix, String loaderName)` | 为某路径前缀强行指定加载器（最长前缀优先） |
| `void registerErrorPage(Plugin owner, int status, byte[] content)` | 注册自定义错误页（HTML/文本字节） |
| `void registerErrorPage(Plugin owner, int status, String html)` | 注册自定义错误页（HTML 字符串） |
| `void registerCors(Plugin owner, String pathPrefix, String origin, String methods, String headers, boolean credentials)` | 声明 CORS（`pathPrefix` 空或 `/` 表示全局） |

---

## A.3 AuthCredentialApi（能力组 3：鉴权与凭证）

| 方法 | 说明 |
| --- | --- |
| `void registerCredentialIssuer(String name, Supplier<CredentialIssuer> factory)` | 注册凭证颁发器工厂（对应 `gateway/issuers/<name>.yml`） |
| `boolean isAuthEnabled()` | 是否有启用的 auth 策略（决定 API 是否需鉴权） |
| `List<String> getIssuerNames()` | 列出所有启用的颁发器名 |
| `IssuedCredential issueCredential(String subject)` | 用首个启用颁发器为已认证主体签发凭证（无启用颁发器返回 null） |
| `IssuedCredential issueCredential(String issuerName, String subject)` | 用指定颁发器签发（`issuerName` 为 null 取首个） |
| `IssuedCredential issueCredential(String subject, Map<String,String> claims)` | 签发携带自定义 claims 的凭证（键限 `[a-zA-Z0-9_-]{1,32}`，值限 256 字符；保留键 `sub/mode/exp/iat/jti/adm` 不可用） |
| `IssuedCredential issueCredential(String issuerName, String subject, Map<String,String> claims)` | 指定颁发器 + 自定义 claims |

---

## A.4 ApiToolkitApi（能力组 4：工具）

| 方法 | 说明 |
| --- | --- |
| `String toJson(Object obj)` | 任意对象 → JSON 字符串 |
| `String guessContentType(String path)` | 扩展名 → Content-Type |
| `void registerMimeType(String ext, String contentType)` | 注册/覆盖扩展名 Content-Type（全局、线程安全；`ext` 不含点） |
| `void sendLink(Player player, String url, String display)` | 向玩家发送可点击链接（`%url%` / `%url_标签%`，`&`→§） |
| `void sendLink(Collection<? extends Player> players, String url, String display)` | 批量发送链接 |

---

## A.5 BotManagementApi（能力组 6：Bot 管理）

| 方法 | 说明 |
| --- | --- |
| `BotManager.ManagedBot addBot(String name, String channel)` | 创建额外受管 Bot（独立通道 + 隧道）回连本服；名称以 `bot.name-prefix`（默认 `__bot__`）开头则自动受保护 |
| `BotManager.ManagedBot addBot(String name)` | 复用主通道创建受管 Bot（同名已存在返回既有项） |
| `void kickBot(String name) throws Exception` | 踢出并断开额外 Bot（主 Bot 不可踢） |
| `void registerChannel(String channel, InternalBot.RawMessageListener listener)` | 在主 Bot 注册自定义通道并监听下行消息 |
| `void unregisterChannel(String channel)` | 注销自定义通道监听 |
| `BotManager.ManagedBot getBot(String name)` | 获取额外受管 Bot（不存在返回 null） |

---

## A.6 ExtensionApi（能力组 8：扩展接入）

| 方法 | 说明 |
| --- | --- |
| `void registerLoginProvider(LoginProvider provider)` | 注册登录插件提供者（LoginProvider SPI） |
| `void registerSubCommand(SubCommand subCommand)` | 注册 `/soyshttp` 子指令（继承 `SubCommand`） |
| `void registerWebInterceptor(WebInterceptor interceptor)` | 注册请求级拦截器（网关策略后、业务路由前；可改写 path/头或短路返回） |
| `void registerPolicy(SecurityPolicy policy)` | 注册自定义安全策略（注入网关策略链，按 order 排序，DENY 短路，异常 fail-closed，`reload` 后保留） |

> 事件监听：监听 `ApiAccessEvent` 及 `ApiGetEvent` / `ApiPostEvent` / `ApiPutEvent` / `ApiDeleteEvent` / `ApiPatchEvent` / `ApiOtherEvent`（携带 `playerName` 与 `player`）。

---

## A.7 HttpClientApi（能力组 7：HTTP 请求）

| 方法 | 说明 |
| --- | --- |
| `HttpResponse sendHttp(String method, String url, Map<String,String> headers, byte[] body)` | 通用请求（任意方法/URL/头/体）；连接失败抛 `HttpClientException` |
| `HttpResponse sendGet(String url)` | GET |
| `HttpResponse sendGet(String url, Map<String,String> headers)` | GET（带头） |
| `HttpResponse sendPost(String url, byte[] body)` | POST（带体） |
| `HttpResponse sendPost(String url, Map<String,String> headers, byte[] body)` | POST（带头+体） |
| `Object callLocalApi(String method, String path, Map<String,String> headers, byte[] body)` | 本地回环调用本插件已注册 API（绕过网络；异常抛 `HttpClientException`） |

`HttpResponse`：含状态码、响应头、响应体。

---

## A.8 PluginLoggerApi（能力组 5：日志）

| 方法 | 说明 |
| --- | --- |
| `void logInfo(String msg)` | 信息日志（自动加 `[插件名]` 前缀） |
| `void logWarn(String msg)` | 警告日志 |
| `void logError(String msg)` | 错误日志 |
| `void logDebug(String msg)` | 调试日志 |
| `void logTrace(String msg)` | 追踪日志 |
| `boolean isDebugEnabled()` | 当前是否包含 DEBUG 及以上级别 |

---

## A.9 CrossServerHttpClient（能力组 9：跨服调用）

| 方法 | 说明 |
| --- | --- |
| `HttpResponse callRemoteApi(String serverName, String method, String path, Map<String,String> headers, byte[] body)` | 跨服调用目标服 API/path（经隧道，目标服无需暴露端口） |
| `HttpResponse sendGet(String serverName, String path)` | 跨服 GET |
| `HttpResponse sendPost(String serverName, String path, byte[] body)` | 跨服 POST |

---

## A.10 ORM 门面：YAML.Pojo / SQL.Pojo

两门面方法签名**完全一致**（`YAML.Pojo` 与 `SQL.Pojo`）。下表以 `Pojo` 代指：

| 方法 | 说明 |
| --- | --- |
| `void init(File dataFolder)` | （YAML 专用）装配数据目录；未装配按默认 `data/` 懒初始化 |
| `boolean isAvailable()` | （SQL 专用）SQL 后端是否可用 |
| `<T> List<T> select(Class<T> beanClass)` | 查询全部 → List |
| `<T> List<T> select(Class<T> beanClass, Consumer<Query<T>> condition)` | 按条件查询 |
| `<T> Query<T> selectW(Class<T> beanClass)` | 链式构建器（返回 `Query`） |
| `<T> T get(Class<T> beanClass, Object id)` | 按主键取单条（无返回 null） |
| `<T> Page<T> selectPage(Class<T> beanClass, long current, long size)` | 分页查询 |
| `YamlConfiguration get(Class<?> beanClass)` | （YAML 专用）原始文件视图（FileConfiguration）；改后需 `save` |
| `void save(Class<?> beanClass)` | （YAML 专用）把视图修改落盘（原子写） |
| `<T> boolean insert(T bean)` | 插入（主键须已赋值） |
| `<T> boolean updateById(T bean)` | 按主键更新（YAML 端整体覆盖 = upsert） |
| `<T> boolean deleteById(Class<T> beanClass, Object id)` | 按主键删除 |
| `<T> List<T> search(Class<T> beanClass, String keyword, String... fields)` | 跨端模糊搜索（YAML 端 contains，SQL 端 LIKE） |

`SQL.Pojo` 在未启用 SQL 后端时安全返回空 / false，不抛异常。

---

## A.11 Query 条件链（orm.query.Query）

通过 `Pojo.selectW(Class).xxx()` 或 `Pojo.select(Class, q -> q.xxx(...))` 使用。字段以 Lambda 引用（重构安全）。

**条件方法**（均返回 `Query` 自身，可链式）：

| 方法 | 含义 |
| --- | --- |
| `eq(column, value)` | 等于 = |
| `ne(column, value)` | 不等于 ≠ |
| `gt(column, value)` | 大于 > |
| `ge(column, value)` | 大于等于 ≥ |
| `lt(column, value)` | 小于 < |
| `le(column, value)` | 小于等于 ≤ |
| `like(column, value)` | 模糊 like |
| `in(column, values...)` / `in(column, List)` | 在集合中 IN |
| `isNull(column)` | 为空 IS NULL |
| `isNotNull(column)` | 非空 IS NOT NULL |
| `or(column, op, value)` | 与上一条件以 OR 相接 |
| `ands(Consumer<Query>)` | AND 分组条件 |
| `ors(Consumer<Query>)` | OR 分组条件（组内 OR 连接） |

**排序 / 分页：**

| 方法 | 说明 |
| --- | --- |
| `orderByAsc(column)` | 升序排序 |
| `orderByDesc(column)` | 降序排序 |
| `page(Page<?> page)` | 设置分页 |

**执行（委托后端）：**

| 方法 | 说明 |
| --- | --- |
| `T queryBean()` | 查询单条（取第一条），无结果返回 null |
| `List<T> queryBeanList()` | 查询列表 |
| `Page<T> queryBeanPage()` | 分页查询 |

---

## A.12 注解一览

### 路由注解（soys.soyshttpovermc.annotations）

| 注解 | 目标 | 关键属性 | 说明 |
| --- | --- | --- | --- |
| `@RequestMapping` | 类 / 方法 | `value()` / `path()`、`method()` | 通用映射；类级为前缀，方法级为路由 |
| `@GetMapping` | 方法 | `value()` / `path()` | GET（= `@RequestMapping(method=GET)`） |
| `@PostMapping` | 方法 | `value()` / `path()` | POST |
| `@PutMapping` | 方法 | `value()` / `path()` | PUT |
| `@DeleteMapping` | 方法 | `value()` / `path()` | DELETE |
| `@PatchMapping` | 方法 | `value()` / `path()` | PATCH |
| `@ApiName` | 方法 / 类 | `value()` | 接口人类可读名（文档/日志/错误提示） |
| `@ApiPermission` | 方法 / 类 | `value()` | 所需权限标识（如 `system:user:list`） |
| `@RequestParam` | 参数 | `name()`、`required()`、`defaultValue()` | 绑定 query string，类型自动转换 |
| `@RequestBody` | 参数 | — | 绑定原始请求体（String 参数） |

### ORM 注解（com.dlz.db.annotation）

| 注解 | 目标 | 关键属性 | 说明 |
| --- | --- | --- | --- |
| `@TableName` | 类 | `value()` | 表名 = YAML 文件名 = SQL 表名（原样使用，不做列名转换） |
| `@TableId` | 字段 | `value()`、`type()` | 主键；`type` 见 `IdType` |
| `@TableField` | 字段 | `value()`、`exist()`、`select()` | 字段映射；`exist=false` 不参与读写 |
| `IdType` | 枚举 | — | `AUTO` / `SEQ` / `INPUT` / `ASSIGN_ID` / `ASSIGN_UUID`；本项目推荐 `INPUT` |

**列名规则**：字段名默认转**小写下划线**（如 `userName → user_name`），YAML 端 / SQL 端一致。`@TableField("x")` 可覆盖。

---

## A.13 ApiRequestContext（请求上下文，自动注入）

处理器声明 `ApiRequestContext` 类型参数即自动注入：

| 方法 | 说明 |
| --- | --- |
| `getHttpMethod()` | 实际请求方法 |
| `getPath()` | 完整路径（含 api-prefix） |
| `getIp()` | 客户端 IP（0.0.0.0=未知） |
| `getHeaders()` | 请求头（只读） |
| `getCredential()` | 凭证（可为 null） |
| `getPlayerName()` | 经 token/cookie 解析的玩家名（未登录=null） |
| `getPlayer()` | 在线玩家实体（离线=null） |
| `isAuthenticated()` | 是否携带有效凭证 |
| `getSourceServer()` | 跨服来源服名（独立服=null） |
| `getTraceId()` | 跨服链路追踪 ID（无关联=null） |

---

## A.14 AjaxResult（统一返回体）

仿 RuoYi / Spring 的 `{code, msg, data}`，网关序列化为 JSON 响应。

**静态工厂：**

| 方法 | 含义 |
| --- | --- |
| `success()` / `success(data)` / `success(msg, data)` | 成功（code=200） |
| `error()` / `error(msg)` / `error(code, msg)` | 失败（默认 code=500） |
| `unauthorized(msg)` | 401 |
| `forbidden(msg)` | 403 |
| `notFound(msg)` | 404 |

**常量与访问器：** `SUCCESS=200`、`UNAUTHORIZED=401`、`FORBIDDEN=403`、`NOT_FOUND=404`、`ERROR=500`；`getCode()` / `getMsg()` / `getData()`；`toJson()` 序列化为字符串。

> 网关对注解式 API 默认以 HTTP 200 返回，业务状态码放在 `body.code`；仅被网关安全策略拒绝时返回真实 HTTP 状态码（401/403/429 等）。

---

*附录结束。返回[导言](导言.md)或[第六章](第6章-进阶能力与最佳实践.md)。*
