# 第二章 注解式 Web API 开发

本章讲解如何用注解快速暴露 HTTP 接口。设计上完全借鉴 Spring MVC：你写一个普通类，用 `@GetMapping` 等标注方法，方法返回 `AjaxResult`，剩下的路由、参数解析、序列化、鉴权都由本插件完成。

## 2.1 注册控制器

通过能力组 1（`ApiRegistrationApi`）注册你的控制器实例：

```java
api.getApiRegistration().registerController(new HelloApi());          // 自动标记所属插件
api.getApiRegistration().registerController(new HelloApi(), this);   // 显式指定 owner
api.getApiRegistration().registerProxyController(new HelloApi());    // 强制主插件代理（无 /plugins/<名> 前缀）
```

### 2.1.1 路由前缀规则

- 默认情况下，非主插件的接口会**自动加上** `/plugins/<你的插件名>` 前缀。例如你的插件叫 `MyPlugin`，`@GetMapping("/hello")` 实际路由为 `/plugins/MyPlugin/hello`（开启鉴权后为 `/api/plugins/MyPlugin/hello`）。
- 若你希望接口像主插件一样**不带前缀**，使用 `registerProxyController(...)`。注意：即便用代理注册，`ownerPlugin` 仍会标记真实插件，便于禁用时自动卸载。
- 主插件（SOYSHTTPOverMC 自身）注册的接口天然无此前缀。

### 2.1.2 卸载

```java
api.getApiRegistration().unregisterController(instance);            // 卸载某控制器实例
api.getApiRegistration().unregisterPluginControllers("MyPlugin");  // 按插件名卸载（一般无需手动）
```

## 2.2 路由注解

### 2.2.1 通用映射 @RequestMapping

可标注在**类**或**方法**上：

- 标在类上：为该控制器下所有方法统一加路径前缀（位于全局 api-prefix 之后）；
- 标在方法上：声明具体路由。

```java
@RequestMapping("/admin")            // 类级前缀
public class AdminApi {
    @GetMapping("/users")            // → /api/admin/users（开启鉴权时）
    public AjaxResult users() { ... }
}
```

常用属性：

| 属性                     | 说明                                                                    |
| ------------------------ | ----------------------------------------------------------------------- |
| `value()` / `path()` | 路由路径（二选一；都为空则注册为`/`）                                 |
| `method()`             | 允许的 HTTP 方法（`RequestMethod[]`）；为空表示不限定（匹配任意方法） |

### 2.2.2 方法级组合注解

为减少样板，提供了五个方法级注解，分别对应常见 HTTP 动词：

| 注解                     | 等价                                 |
| ------------------------ | ------------------------------------ |
| `@GetMapping("/x")`    | `@RequestMapping(method = GET)`    |
| `@PostMapping("/x")`   | `@RequestMapping(method = POST)`   |
| `@PutMapping("/x")`    | `@RequestMapping(method = PUT)`    |
| `@DeleteMapping("/x")` | `@RequestMapping(method = DELETE)` |
| `@PatchMapping("/x")`  | `@RequestMapping(method = PATCH)`  |

它们都有 `value()` 与 `path()` 两个别名属性。

### 2.2.3 全局 API 前缀 /api

当网关的 `auth` 策略启用时，所有注解式 API 会**自动加上 `/api` 前缀**（即 `auth.yml` 中的 `api-prefix`）。关闭鉴权时则不附加。你写注解时只用写业务路径（如 `/hello`），前缀由系统管理。

```java
api.getApiRegistration().getApiPrefix();   // 读取当前全局前缀（通常为 /api）
```

## 2.3 方法参数绑定

### 2.3.1 查询参数 @RequestParam

从 URL query string 取值并做类型转换：

```java
@GetMapping("/hello")
public AjaxResult hello(
    @RequestParam(name = "name", required = false, defaultValue = "world") String name,
    @RequestParam(name = "age", required = false) Integer age) {
    return AjaxResult.success("hello " + name + ", age=" + age);
}
```

访问 `https://<地址>/hello?name=Steve&age=20` 即可拿到解析后的参数。属性：

| 属性               | 说明                         |
| ------------------ | ---------------------------- |
| `name()`         | query key（必填）            |
| `required()`     | 是否必填；缺失且必填返回 400 |
| `defaultValue()` | 缺失且非必填时的默认值       |

### 2.3.2 请求体 @RequestBody

标注在 `String` 参数上，绑定原始请求体（常用于 JSON / 表单原文）：

```java
@PostMapping("/echo")
public AjaxResult echo(@RequestBody String body) {
    return AjaxResult.success(body);
}
```

> 进阶：若需要把请求体直接反序列化为对象，可用 `api.getToolkit().toJson(...)` 的逆操作，或在处理器里用 JSON 库自行解析 `body`。

### 2.3.3 请求上下文 ApiRequestContext

当处理器有一个类型为 `ApiRequestContext` 的参数时，网关自动注入完整请求上下文，**开发者无需自行解析请求头 / 凭证 / 令牌**：

```java
@GetMapping("/whoami")
public AjaxResult whoami(ApiRequestContext ctx) {
    Map<String, Object> info = new java.util.LinkedHashMap<>();
    info.put("ip", ctx.getIp());               // 客户端 IP
    info.put("player", ctx.getPlayerName());   // 经 token/cookie 解析的玩家名（未登录=null）
    info.put("online", ctx.getPlayer() != null); // 在线玩家实体（离线=null）
    info.put("path", ctx.getPath());           // 完整路径（含 /api 前缀）
    info.put("source", ctx.getSourceServer());// 跨服来源服名（独立服=null）
    return AjaxResult.success(info);
}
```

`ApiRequestContext` 提供的字段：

| 方法                  | 说明                                         |
| --------------------- | -------------------------------------------- |
| `getHttpMethod()`   | 实际请求方法（GET/POST/...）                 |
| `getPath()`         | 完整路径（含 api-prefix）                    |
| `getIp()`           | 客户端 IP（0.0.0.0 表示未知）                |
| `getHeaders()`      | 请求头（只读视图）                           |
| `getCredential()`   | 请求解析出的凭证（可为 null）                |
| `getPlayerName()`   | 经 token/cookie 解析的玩家名（未登录/null）  |
| `getPlayer()`       | 在线玩家实体（离线=null）                    |
| `isAuthenticated()` | 请求是否携带有效凭证                         |
| `getSourceServer()` | 跨服请求来源服名（独立服 / 本服直连为 null） |
| `getTraceId()`      | 跨服链路追踪 ID（无关联为 null）             |

## 2.4 统一返回体 AjaxResult

所有注解式 API 建议返回 `AjaxResult`（仿 RuoYi / Spring 的 `{code, msg, data}` 结构）。网关会将其序列化为 JSON 响应。

```java
return AjaxResult.success();                     // {"code":200,"msg":"操作成功"}
return AjaxResult.success(data);                 // {"code":200,"msg":"操作成功","data":...}
return AjaxResult.success("已更新", id);           // 自定义 msg
return AjaxResult.error("服务器异常");             // {"code":500,"msg":"服务器异常"}
return AjaxResult.unauthorized("未登录");          // {"code":401,...}
return AjaxResult.forbidden("无权限");            // {"code":403,...}
return AjaxResult.notFound("资源不存在");          // {"code":404,...}
```

常用静态工厂：

| 方法                                                       | 含义                  |
| ---------------------------------------------------------- | --------------------- |
| `success()` / `success(data)` / `success(msg, data)` | 成功（code=200）      |
| `error()` / `error(msg)` / `error(code, msg)`        | 失败（默认 code=500） |
| `unauthorized(msg)`                                      | 401 认证失败          |
| `forbidden(msg)`                                         | 403 无权限            |
| `notFound(msg)`                                          | 404 资源不存在        |

> 注意：网关对**注解式 API** 默认以 HTTP 200 返回，业务状态码放在 `body.code` 中（这是 RuoYi 风格）。只有被网关安全策略拒绝时，才会返回真实的 HTTP 状态码（如 401/403/429）。

## 2.5 命名、权限与 PermissionService

### 2.5.1 @ApiName

给接口一个人类可读的名字，用于文档、日志与错误提示：

```java
@ApiName("隧道状态")
@GetMapping("/status")
public AjaxResult status() { ... }
```

### 2.5.2 @ApiPermission

声明调用该接口所需的权限标识：

```java
@ApiPermission("system:user:list")
@GetMapping("/users")
public AjaxResult users() { ... }
```

权限判定由你注册的 `PermissionService` 完成。把凭证映射为主体再查权限：

```java
api.getApiRegistration().setPermissionService((playerName, permission) -> {
    // 返回该玩家是否拥有 permission 标识
    return /* 你的判定逻辑 */ false;
});
```

未注册 `PermissionService` 时，`@ApiPermission` **不阻断**请求（仅记录），鉴权仍以网关 `auth` 策略的 401 为底线。`getPermissionService()` 可读取当前服务。

## 2.6 完整示例

下面给出一个完整的“用户管理”控制器，综合演示路由、参数、上下文、权限与返回体：

```java
@RequestMapping("/admin")
@ApiName("用户管理")
public class AdminApi {

    private final UserService userService;   // 你的业务服务（普通 Java 对象即可）

    public AdminApi(UserService userService) {
        this.userService = userService;
    }

    @ApiName("用户列表")
    @ApiPermission("admin:user:list")
    @GetMapping("/users")
    public AjaxResult listUsers(
            @RequestParam(name = "page", defaultValue = "1") int page,
            ApiRequestContext ctx) {
        // ctx.getPlayerName() 可拿到当前操作者
        return AjaxResult.success(userService.page(page));
    }

    @ApiName("创建用户")
    @ApiPermission("admin:user:create")
    @PostMapping("/users")
    public AjaxResult create(@RequestBody String body) {
        User u = parseUser(body);            // 自行 JSON 解析
        userService.save(u);
        return AjaxResult.success("已创建", u);
    }
}
```

注册：

```java
api.getApiRegistration().registerController(new AdminApi(myUserService));
```

## 2.7 调试与查看已注册端点

运行 `soyshttp api`（或简写 `shttp api`）可列出当前所有已注册的注解式端点（来自 `ApiRegistry.listEndpoints()`）。这对排查“我的接口为什么没出现”非常有用。

> 注意：查看命令用的是 `ApiRegistry.listEndpoints()`，而非 `ApiRegistrationApi.getRegisteredApis()`——后者是门面层快照，二者命名不同请勿混淆。

下一章讲解如何**托管网页与静态资源**。
