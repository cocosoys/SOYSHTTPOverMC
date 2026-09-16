# 第2章 注解式WebAPI开发

## 2.1 概念

SOYSHTTPOverMC 提供一套类似 Spring MVC 的**注解式 API 框架**：你在普通 POJO 类的方法上标注路由注解，调用 `ApiRegistry.register(实例)` 或门面 `api.getApiRegistration().registerController(实例)`，网关就会把匹配的 HTTP 请求分发到该方法。

关键事实（对照源码）：

- 全局 API 前缀恒为 `/api`（`gateway/config.yml` 的 `api-prefix`，默认 `/api`），`@GetMapping("/ping")` 一律映射为 `/api/ping`；**该前缀与 auth 是否启用无关**，保证 API 地址恒定；
- 第三方插件注册的控制器**自动追加 `/plugins/<插件名>` 前缀**：插件 Foo 注册 `/ping` → 实际 `/api/plugins/Foo/ping`；`registerProxyController` 可去掉该前缀（以主插件名义代理，owner 仍记录为真实插件）；
- 重复路由默认拒绝，`force=true` 可强制覆盖并打印原登记插件；
- 插件禁用时其名下端点自动卸载。

## 2.2 注解清单

| 注解 | 目标 | 说明 |
| --- | --- | --- |
| `@RequestMapping` | 类 / 方法 | 通用路由（`value` 或 `path` 指定路径） |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` / `@PatchMapping` | 方法 | HTTP 方法限定路由 |
| `@ApiName("名称")` | 类 / 方法 | 端点显示名（事件 / /soyshttp api 展示） |
| `@ApiPermission("权限")` | 类 / 方法 | 所需权限节点（方法级优先；判定走权限服务） |
| `@ApiPublic` | 类 / 方法 | 公开端点：跳过权限判定（豁免鉴权） |
| `@RequestParam(name, required, defaultValue)` | 参数 | 查询参数绑定 |
| `@RequestBody` | 参数 | 请求体绑定（String / 对象 JSON） |

映射注解的 `value` 与 `path` 等价（如 `@GetMapping("/ping")` 或 `@GetMapping(path = "/ping")`）。

## 2.3 最小示例

```java
public class MyApi {

    @GetMapping("/ping")
    public ApiResponse ping() {
        return ApiResponse.success("pong");
    }

    @ApiName("问候")
    @GetMapping("/hello")
    public ApiResponse hello(@RequestParam(name = "name", defaultValue = "player") String name) {
        return ApiResponse.success("Hello, " + name);
    }

    @PostMapping("/echo")
    public ApiResponse echo(@RequestBody String body) {
        return ApiResponse.success(body);
    }
}
```

注册：

```java
api.getApiRegistration().registerController(new MyApi());
// 访问：GET /api/plugins/你的插件名/ping → {"code":0,"msg":"ok","data":"pong"}
```

## 2.4 统一返回体

`com.github.cocosoys.mc.soyshttpovermc.util.ApiResponse`：

| 字段 | 说明 |
| --- | --- |
| `code` | 业务码（0=成功） |
| `msg` | 消息 |
| `data` | 数据（任意对象，自动 JSON 序列化） |

静态工厂：`ApiResponse.success(data)`、`ApiResponse.error(msg)`、`ApiResponse.error(code, msg)` 等。返回任意对象也会被自动序列化为 JSON；返回 `null` 时网关输出空 204/200 视实现而定（建议始终返回 `ApiResponse` 或 `AjaxResult` 保证结构一致）。

`AjaxResult`（`util.AjaxResult`）提供另一套 `success(...)` / `error(...)` 便捷构造，二者均可直接作为返回类型。

## 2.5 参数绑定

### 2.5.1 查询参数：@RequestParam

```java
@GetMapping("/status")
public ApiResponse status(@RequestParam(name = "player", required = false) String player) {
    // GET /api/.../status?player=Steve
}
```

`required=false` 缺省时传 `defaultValue`（或 null）；`required=true` 缺参返回 400。

### 2.5.2 请求体：@RequestBody

```java
@PostMapping("/save")
public ApiResponse save(@RequestBody String raw) { ... }   // 原始字符串

@PostMapping("/save2")
public ApiResponse save2(@RequestBody MyPojo pojo) { ... } // 自动 JSON → 对象
```

### 2.5.3 请求上下文：ApiRequestContext

处理器参数类型为 `ApiRequestContext` 时，网关自动注入完整请求上下文，**无需自行解析请求头 / 凭证 / 令牌**：

```java
@GetMapping("/whoami")
public ApiResponse whoami(ApiRequestContext ctx) {
    return ApiResponse.success(Map.of(
        "ip", ctx.getIp(),                 // 客户端 IP
        "player", ctx.getPlayerName(),     // 经 token/cookie 解析的玩家名（未登录=null）
        "online", ctx.getPlayer() != null, // 实时玩家实体（离线=null）
        "path", ctx.getPath()));
}
```

`ApiRequestContext` 主要方法：

| 方法 | 说明 |
| --- | --- |
| `getHttpMethod()` / `getPath()` | 请求方法 / 完整路径（含 /api 前缀） |
| `getIp()` | 客户端 IP（网关在嗅探端注入内部头 `X-Soys-Remote-Ip` 传递） |
| `getHeaders()` | 只读请求头 Map |
| `getCredential()` | 请求解析出的凭证（可为 null） |
| `getPlayerName()` | 经凭证解析的玩家名（稳定字符串锚点，永不悬空、永不过期；未登录为 null） |
| `getPlayer()` / `getSyncPlayer()` | **实时**玩家实体（每次调用切主线程重新解析，反映调用时刻真实状态；离线 null）。worker 线程调用会阻塞当前线程直至下一 tick，勿在循环内使用 |
| `getAsyncPlayer()` | 派发时刻**快照**：请求进入 worker 时一次性解析的玩家实体（不切主线程，零阻塞） |
| `isAuthenticated()` | 请求是否携带有效凭证 |
| `getSourceServer()` / `getTraceId()` | 群组服跨服来源服名 / 链路追踪 ID（独立服 null） |

> **线程注意**：处理器运行在 worker 线程（非主线程）。`getPlayer()` 每次调用会切回主线程取值，**会阻塞当前 worker 直至下一 tick**，请勿在循环内频繁调用；玩家在线状态用 `getPlayerName()` 做稳定锚点。

## 2.6 权限控制

### 2.6.1 注解

```java
@ApiPermission("myapi.admin")      // 需要权限节点
@GetMapping("/admin")
public ApiResponse admin() { ... }

@ApiPublic                          // 仅需登录：免权限判定（认证门仍需凭证）
@GetMapping("/me")
public ApiResponse me() { ... }

@Anonymous                          // 完全匿名：认证门 + 授权门均放行
@GetMapping("/ping")
public ApiResponse ping() { ... }
@GetMapping("/public")
public ApiResponse pub() { ... }
```

判定逻辑（`PlayerPermissionService` / `CombinedPermissionService`，详见第 4 章）：

1. 静态最高权限 Key（`/soyshttp key` 下发，带 `adm` 标记）→ 直接放行；
2. 会话令牌 → 解析出玩家 → 查 Bukkit 原生权限（在线）/ 权限插件组合 / 离线降级策略；
3. 无会话颁发器时回退开放（兼容旧部署）。

### 2.6.2 类级默认

### 2.6.3 端点级限流 / 防重 / 元数据

```java
@RateLimiter(count = 5, time = 10, by = RateLimiter.LimitBy.IP) // 同一 IP 10 秒最多 5 次，超限 429
@PostMapping("/submit")
public ApiResponse submit(@RequestBody String body) { ... }

@RepeatSubmit(interval = 5)        // 同一键（IP+方法+路径+请求体哈希）5 秒内重复提交 → 409；默认仅写方法
@PostMapping("/claim")
public ApiResponse claim(@RequestBody String body) { ... }

@Hidden(reason = "内部调试端点")     // 从 API 清单 / 自动文档隐藏
@Deprecated(since = "1.4.0", reason = "改用 /api/v2") // 废弃标记（SOYS 自定义，区别于 JDK 注解）
@GetMapping("/orders")
public ApiResponse orders() { ... }
```

- `@RateLimiter`：端点级限流（与网关全局 rate-limit 互补），按 IP / 玩家 / 双键计数，窗口内超限返回 429；
  客户端 IP 与玩家均不可得（本地回环调用）时不限流。
- `@RepeatSubmit`：防重复提交，写方法（POST/PUT/DELETE/PATCH）默认生效，`force=true` 可对 GET 生效。
- `@Hidden`：元数据隐藏——注册与路由照常，仅清单 / 文档不可见。
- `@Deprecated`：废弃标记（携带 since / reason），路由照常但清单标注废弃；与 `java.lang.Deprecated` 同名不同包，勿混淆。

以上注解均支持方法级缺失时回退到类级。

`@ApiPermission` / `@ApiPublic` / `@Anonymous` 标注在类上作为该类所有端点的默认；方法级注解覆盖类级。

## 2.7 注册 API（门面能力组 1）

`api.getApiRegistration()` 提供：

| 方法 | 说明 |
| --- | --- |
| `registerController(Object)` | 注册控制器（非主插件自动加 `/plugins/<插件名>` 前缀） |
| `registerController(Object, Plugin owner)` | 显式指定所属插件 |
| `registerController(Object, boolean force)` | force=true 强制覆盖重复路由 |
| `registerProxyController(...)` | 以主插件名义代理注册（无前缀） |
| `unregisterController(Object)` | 卸载某控制器实例的全部端点 |
| `unregisterPluginControllers(String pluginName)` | 卸载指定插件名的全部端点 |
| `setPermissionService(PermissionService)` | 接入自定义权限判定服务 |
| `getRegisteredApis()` | 当前全部端点快照（`ApiInfo` 列表） |
| `getApiPrefix()` | 全局前缀（/api） |

## 2.8 系统级内置 API

core 启动时自动注册以下控制器（`spring/controller`）：

| 端点 | 说明 |
| --- | --- |
| `/api/status/...` | 状态查询（在线玩家、TPS、请求统计等，`StatusController`） |
| `/api/system/...` | 系统信息（`SystemController`） |
| `/api/auth/...` | 登录窗口（`AuthController`：login / issue / mode / status 等，见第 4 章） |
| `/api/homepage/config`、`/api/homepage/live` | 门户首页公开配置 / 实时数据 |

这些端点在 `gateway/policies/auth.yml` 的 `exempt` 中默认豁免鉴权（公开）。

## 2.9 请求生命周期与调试

- 网关 `gateway/config.yml` 的 `debug-events: true` 会在控制台打印网关事件（请求进入 / 拒绝 / 完成 / 凭证下发 / API 注册 / 卸载）；
- 监听事件：`ApiAccessEvent`（请求通过权限判定后、处理器调用前）、`ApiAccessDeniedEvent`（权限不足 403）、`ApiRegisteredEvent` / `ApiUnregisteredEvent`（注册 / 卸载）——详见第 7 章；
- 未命中路由返回 404 JSON；权限不足返回 403；网关拒绝返回对应状态码（401/426/429 等）。

## 2.10 版本注意

- 1.12.2 主线程触发异步事件会抛 `IllegalStateException`，因此**注册 / 卸载事件强制同步**（见 `GatewayEvent` 类注释）；
- 注解、`ApiRequestContext`、`ApiResponse` 均位于 common / core，所有受支持版本行为一致。
