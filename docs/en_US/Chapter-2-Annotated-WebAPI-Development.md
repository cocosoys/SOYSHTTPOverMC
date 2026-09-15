# Chapter 2 Annotation-based Web API Development

## 2.1 Concept

SOYSHTTPOverMC provides a Spring-MVC-like **annotation-based API framework**: you annotate methods on a plain POJO with routing annotations, call `ApiRegistry.register(instance)` or the facade `api.getApiRegistration().registerController(instance)`, and the gateway dispatches matching HTTP requests to those methods.

Key facts (verified against source):

- The global API prefix is always `/api` (`api-prefix` in `gateway/config.yml`, default `/api`); `@GetMapping("/ping")` maps to `/api/ping`; **the prefix is independent of whether auth is enabled**, keeping API addresses stable;
- Controllers registered by third-party plugins get an **automatic `/plugins/<pluginName>` prefix**: plugin Foo registering `/ping` → `/api/plugins/Foo/ping`; `registerProxyController` drops that prefix (registers under the main plugin's name, though `owner` is still the real plugin);
- Duplicate routes are rejected by default; `force=true` overwrites and logs the previously registered plugin;
- Endpoints are unregistered automatically when the owning plugin is disabled.

## 2.2 Annotation Set

| Annotation | Target | Description |
| --- | --- | --- |
| `@RequestMapping` | class / method | Generic routing (`value` or `path`) |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` / `@PatchMapping` | method | HTTP-method-specific routing |
| `@ApiName("name")` | class / method | Display name of the endpoint (events / `/soyshttp api`) |
| `@ApiPermission("permission")` | class / method | Required permission node (method-level wins; checked via the permission service) |
| `@ApiPublic` | class / method | Public endpoint: skips permission checks (exempt from auth) |
| `@RequestParam(name, required, defaultValue)` | parameter | Query parameter binding |
| `@RequestBody` | parameter | Request body binding (String / JSON-deserialized object) |

`value` and `path` are equivalent on mapping annotations (e.g. `@GetMapping("/ping")` or `@GetMapping(path = "/ping")`).

## 2.3 Minimal Example

```java
public class MyApi {

    @GetMapping("/ping")
    public ApiResponse ping() {
        return ApiResponse.success("pong");
    }

    @ApiName("greeting")
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

Registration:

```java
api.getApiRegistration().registerController(new MyApi());
// GET /api/plugins/<yourPluginName>/ping → {"code":0,"msg":"ok","data":"pong"}
```

## 2.4 Unified Response Body

`com.github.cocosoys.mc.soyshttpovermc.util.ApiResponse`:

| Field | Description |
| --- | --- |
| `code` | business code (0 = success) |
| `msg` | message |
| `data` | payload (any object, auto-serialized to JSON) |

Static factories: `ApiResponse.success(data)`, `ApiResponse.error(msg)`, `ApiResponse.error(code, msg)` etc. Any returned object is auto-serialized to JSON; when returning `null` the gateway emits an empty 204/200 depending on the implementation (always return `ApiResponse` or `AjaxResult` to keep the structure consistent).

`AjaxResult` (`util.AjaxResult`) provides another set of `success(...)` / `error(...)` helpers; either works as a return type.

## 2.5 Parameter Binding

### 2.5.1 Query Parameters: @RequestParam

```java
@GetMapping("/status")
public ApiResponse status(@RequestParam(name = "player", required = false) String player) {
    // GET /api/.../status?player=Steve
}
```

With `required=false`, pass `defaultValue` when missing (or null); `required=true` with a missing param yields 400.

### 2.5.2 Request Body: @RequestBody

```java
@PostMapping("/save")
public ApiResponse save(@RequestBody String raw) { ... }   // raw string

@PostMapping("/save2")
public ApiResponse save2(@RequestBody MyPojo pojo) { ... } // auto JSON → object
```

### 2.5.3 Request Context: ApiRequestContext

When a handler parameter is of type `ApiRequestContext`, the gateway injects the full request context — **no need to parse headers / credentials / tokens yourself**:

```java
@GetMapping("/whoami")
public ApiResponse whoami(ApiRequestContext ctx) {
    return ApiResponse.success(Map.of(
        "ip", ctx.getIp(),                 // client IP
        "player", ctx.getPlayerName(),     // player name resolved from token/cookie (null if not logged in)
        "online", ctx.getPlayer() != null, // live player entity (null offline)
        "path", ctx.getPath()));
}
```

Key `ApiRequestContext` methods:

| Method | Description |
| --- | --- |
| `getHttpMethod()` / `getPath()` | request method / full path (including the /api prefix) |
| `getIp()` | client IP (the gateway injects an internal `X-Soys-Remote-Ip` header at the sniffer end) |
| `getHeaders()` | read-only request header map |
| `getCredential()` | credential resolved from the request (may be null) |
| `getPlayerName()` | player name resolved from the credential (a stable string anchor, never dangling; null if not logged in) |
| `getPlayer()` / `getSyncPlayer()` | **live** player entity (re-resolved on the main thread each call, reflecting the real state at call time; null offline). Calling from a worker thread blocks it until the next tick — do not use in loops |
| `getAsyncPlayer()` | **snapshot** taken at dispatch time (resolved once when the request entered the worker; zero blocking) |
| `isAuthenticated()` | whether the request carries a valid credential |
| `getSourceServer()` / `getTraceId()` | cross-server origin / trace ID (null on standalone) |

> **Thread note**: handlers run on worker threads (not the main thread). `getPlayer()` switches to the main thread to resolve and **blocks the current worker until the next tick** — avoid calling it repeatedly in loops; use `getPlayerName()` as the stable anchor for online status.

## 2.6 Permission Control

### 2.6.1 Annotations

```java
@ApiPermission("myapi.admin")      // requires this permission node
@GetMapping("/admin")
public ApiResponse admin() { ... }

@ApiPublic                          // requires login only: skips permission checks (auth gate still applies)
@GetMapping("/me")
public ApiResponse me() { ... }

@Anonymous                          // fully anonymous: both auth gate and permission gate allow
@GetMapping("/ping")
public ApiResponse ping() { ... }
@GetMapping("/public")
public ApiResponse pub() { ... }
```

Check logic (`PlayerPermissionService` / `CombinedPermissionService`, details in Chapter 4):

1. Static highest-privilege key (issued by `/soyshttp key`, carrying the `adm` marker) → allow;
2. Session token → resolve player → check Bukkit native permission (online) / permission-plugin combination / offline fallback policy;
3. No session issuer → fall back to open (compatible with legacy deployments).

### 2.6.2 Class-Level Defaults

`@ApiPermission` / `@ApiPublic` / `@Anonymous` on the class set the default for all endpoints in that class; method-level annotations override the class level.

## 2.7 Registering APIs (Facade Group 1)

`api.getApiRegistration()` provides:

| Method | Description |
| --- | --- |
| `registerController(Object)` | register a controller (non-main plugins automatically get the `/plugins/<pluginName>` prefix) |
| `registerController(Object, Plugin owner)` | explicitly specify the owning plugin |
| `registerController(Object, boolean force)` | force=true overwrites duplicate routes |
| `registerProxyController(...)` | register as a proxy under the main plugin's name (no prefix) |
| `unregisterController(Object)` | unregister all endpoints of a controller instance |
| `unregisterPluginControllers(String pluginName)` | unregister all endpoints of a plugin |
| `setPermissionService(PermissionService)` | plug in a custom permission-checking service |
| `getRegisteredApis()` | snapshot of all current endpoints (`ApiInfo` list) |
| `getApiPrefix()` | the global prefix (/api) |

## 2.8 Built-In System APIs

The core registers the following controllers automatically at startup:

| Endpoint | Description |
| --- | --- |
| `/api/status/...` | status queries (online players, TPS, request stats, etc., `StatusController`) |
| `/api/system/...` | system info (`SystemController`) |
| `/api/auth/...` | login window (`AuthController`: login / issue / mode / status etc., see Chapter 4) |
| `/api/homepage/config`, `/api/homepage/live` | public portal homepage config / live data |

These endpoints are exempted from auth by default in `gateway/policies/auth.yml` (`exempt`).

## 2.9 Request Lifecycle & Debugging

- `debug-events: true` in `gateway/config.yml` prints gateway events to the console (request entered / denied / served / credential issued / API registered / unregistered);
- Listen to events: `ApiAccessEvent` (after permission passes, before the handler runs), `ApiAccessDeniedEvent` (403), `ApiRegisteredEvent` / `ApiUnregisteredEvent` — see Chapter 7;
- Unmatched routes return a 404 JSON; insufficient permission returns 403; gateway rejections return the corresponding status code (401/426/429 etc.).

## 2.10 Version Notes

- On 1.12.2, firing async events from the main thread throws `IllegalStateException`; therefore **registration/unregistration events are forced synchronous** (see the `GatewayEvent` class comment);
- Annotations, `ApiRequestContext` and `ApiResponse` live in common / core and behave identically on all supported versions.
