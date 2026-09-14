# Chapter 6 Advanced Capabilities & Best Practices

## 6.1 Facade Group 5: HTTP Client (HttpClientApi)

`api.getHttpClient()`:

| Method | Description |
| --- | --- |
| `sendHttp(method, url, headers, body)` | real outbound HTTP request (any method/URL/headers/body); returns `HttpResponse` (status + headers + body); throws `HttpClientException` on connection failure |
| `sendGet(url [, headers])` | GET convenience |
| `sendPost(url [, headers], body)` | POST convenience |
| `callLocalApi(method, path, headers, body)` | in-process loopback: call the plugin's own registered APIs directly (bypasses the network; equivalent to local dispatch) |
| `sendApi(method, logicalPath, headers, body)` | generic send: prefixes via `resolveUrl`, then **local dispatch** (annotation-level `@ApiPublic/@ApiPermission` still apply); returns null on no route |
| `resolveUrl(logicalPath)` | resolve the full locally accessible HTTP path (auto-adds three prefixes, below) |
| `serverPrefix()` / `apiPrefix()` | proxy-server prefix (e.g. `/server/lobby`) / API prefix (`/api`) |
| `isAuthEnabled()` | whether gateway auth is enabled |

### 6.1.1 resolveUrl's Three Prefixes

`resolveUrl("/status")` auto-completes:

1. **/api prefix**: the global annotation API prefix (always applied);
2. **server prefix**: on a proxy network, `/server/<thisServerName>` (cross-server routing); standalone has no such concept → empty;
3. **plugin prefix**: when the caller is a third-party plugin, `/plugins/<pluginName>` (detected via the call stack; the main plugin's own calls add none).

Example: third-party plugin Foo calling `resolveUrl("/status")` → standalone `/api/plugins/Foo/status`; proxy network `/server/lobby/api/plugins/Foo/status`. So **the same code works unchanged on both standalone and proxy networks**.

## 6.2 Facade Group 6: Extensions (ExtensionApi)

`api.getExtension()`:

| Method | Description |
| --- | --- |
| `registerLoginProvider(LoginProvider)` | register a login plugin provider (implement the SPI and integrate in one line; the gateway handles password validation / password-free login / player-token issuance) |
| `registerSubCommand(SubCommand)` | register a `/soyshttp` (or `/shttp`) subcommand (extend `SubCommand`, implement 4 methods; op checks / help aggregation are automatic) |
| `registerWebInterceptor(WebInterceptor)` | register a request-level interceptor: runs after the gateway policies and before business routing; may rewrite path/request headers or short-circuit (SSO checks, maintenance pages, gray release) |
| `registerPolicy(SecurityPolicy)` | inject a custom security policy (see Chapter 4, 4.7) |

### 6.2.1 Custom Subcommand

```java
public class MySub extends SubCommand {
    public String name() { return "mycmd"; }
    public String permission() { return "soyshttp.admin"; }
    public String usage() { return "/soyshttp mycmd <arg>"; }
    public boolean isHide() { return false; }

    public void execute(CommandSender sender, String[] args) {
        msgT(sender, "command.mycmd.done", "§aDone: {0}", args.length > 0 ? args[0] : "?");
    }
    public List<String> tabComplete(CommandSender sender, String[] args) { ... }
}

// register (after the host onEnable finishes)
api.getExtension().registerSubCommand(new MySub());
```

The `SubCommand` base class provides `msg` / `msgT` (auto-prepends `§a[SOYSHTTPOverMC]§r` + i18n) and other helpers.

### 6.2.2 Request-Level Interceptor

```java
api.getExtension().registerWebInterceptor((ctx, chain) -> {
    if (ctx.getIp().startsWith("127.")) return chain.next(ctx);   // allow
    return ApiResponse.error(403, "local access only");            // short-circuit
});
```

Interceptors run in registration order; you may rewrite `ctx`'s path / request headers inside.

## 6.3 Internationalization (I18n)

- `language.yml` `current` selects the language (default `zh_cn`); `/soyshttp lang <code>` switches and persists it;
- `rule`: `internationalization` (default; en_us base + target language overlay) / `clear` / `overlay`;
- `sources`: extra language sources (plugins/admins), supporting files, folders (`<dir>/<languageCode>.yml` convention) and network URLs (backtick-wrapped; `{0}` language placeholder); manage with `/soyshttp lang sources ...` (on/off/download/update/remove/info);
- In code: `I18n.t(key, fallback, args...)` for text; use `LogKit`'s `infoT/warnT/errorT/debugT(key, fallback, args...)` for logs;
- Language packs live in `plugins/SOYSHTTPOverMC/language/` (bundled `zh_cn.yml` / `en_us.yml`), flat dot-path keys.

## 6.4 Logging Control

- `config.yml log.level`: OFF / ERROR / WARN / INFO (default) / DEBUG / TRACE;
- `/soyshttp log-level <level>` or `/soyshttp reload` adjusts dynamically;
- All plugin logs go through the LogKit facade for unified filtering; `@CustomLog` generates the `log` object.

## 6.5 Hot Reload (/soyshttp reload)

`reloadHttpConfig()` in order:

1. re-read config.yml / language.yml / pages.yml;
2. rebuild the page-permission checker;
3. reset the log level;
4. rebuild storage backends (initStorage);
5. rebuild the gateway policy chain and TLS;
6. rebuild the combined permission service and inject it into ApiRegistry;
7. re-connect the login bridge (AuthLoginBridge) and login providers;
8. unregister old pages.yml pages by tag and re-register;
9. update the front-end home spec;
10. invoke all `ReloadHttpConfigHandler` hooks in order;
11. broadcast `HttpConfigReloadEvent`.

> Note: changing `api-prefix` in `gateway/config.yml` requires a server restart (reload does not re-register routes); changing `web.home` also requires a restart (or the hot swap by WebFrontendHandler after reload).

## 6.6 Proxy Networks (BungeeCord / Velocity)

`config.yml`:

```yaml
proxy:
  server-name: ""       # this backend server's unique name in the proxy (matches BungeeCord servers.<name>)
  proxy-address: ""     # address through the proxy as host:port (required; otherwise cross-server Forward is dropped)
mc:
  public-host: ""       # optional public address override
  public-port: 0
  trust-proxy: true     # trust X-Forwarded-For injected by the proxy (restore real visitor IPs)
```

- Each backend's `server-name` must be unique and match the proxy registration;
- Leaving `proxy-address` empty silently drops Forwards on direct connections → cross-server requests/discovery all break;
- Headers `X-Soys-Source-Server` / `X-Soys-Trace-Id` correlate cross-server requests (`ApiRequestContext.getSourceServer()` / `getTraceId()`);
- Enable MySQL shared storage for a consistent JWT secret across servers (see Chapter 5, 5.7).

## 6.7 Choosing the HTTP Backend Mode

| Mode | Characteristics | Suitable for |
| --- | --- | --- |
| `direct` | calls directly on the Netty IO thread; lowest latency (<1ms); blocking IO threads under high concurrency | low concurrency / minimal deployment |
| `netty-eventloop` (default) | submits to a dedicated event loop; low latency (~1-3ms); does not block gateway IO | general recommendation |
| `memory-queue` | bounded queue + workers; backpressure (503 when full); slightly higher latency (~2-5ms) | high concurrency anti-pileup |
| `standalone-server` | separate-port Netty HTTP server; no same-port sniffing; lowest latency, simplest | when you do not want to occupy the MC port |

Set `http-backend.mode`; per-mode parameters under `http-backend.<mode>.<key>` (old flat keys remain backward-compatible). Version limits: see Chapter 1, 1.5.

## 6.8 Multi-Version Packaging (Maven)

The repository builds multiple jars from a single source: the same core source stays **Java-8 bytecode**, and Maven profiles switch the spigot-api dependency and compile JDK, producing several jars at once (`SOYSHTTPOverMC-1_12-*.jar` / `-1_6-*.jar` / `-1_7-*.jar`, plus the v1_16x/v1_20x/v1_21x/v1_26x adapter modules).

- **1.8 – 1.12.2**: build with Java 8 (no `api-version`);
- **1.13 – 1.16.5**: build with Java 8 + `api-version: 1.13`;
- **1.17 – 1.20.4**: build with Java 17;
- **1.20.5 – 1.21+**: build with Java 21.

Locally, use Maven Toolchains (`~/.m2/toolchains.xml` declaring each JDK, or a project-local `toolchains.xml`) to switch compile JDKs; CI (GitHub Actions) can switch `setup-java` versions per profile in a job matrix (see `.github/workflows/release.yml`).

## 6.9 Best Practices Checklist

1. Third-party plugins **must** declare `softdepend: [SOYSHTTPOverMC]` and null-check in onEnable; when load order is uncertain, listen for `SoysReadyEvent` and register lazily;
2. Depend on `soyshttpovermc-common` (no Bukkit) to compile annotations and ORM layers — compilable on any JDK;
3. Controllers should return `ApiResponse` / `AjaxResult` to keep a consistent structure; do not do heavy work on the main thread (handlers run on worker threads);
4. `ApiRequestContext.getPlayer()` switches to the main thread — do not call it in loops; use `getPlayerName()` as the stable anchor;
5. Page permissions: prefer `pages.yml` single-page inline `permissions` (AND semantics); grant nodes via `/soyshttp perm`;
6. Add high-value resources to `web.cache.pinned`; watch `large-file-max-bytes` (default 128MB) for very large files;
7. Public deployments: enable at least one of `ip-allowlist` / `rate-limit`; use a formal `keystore(PKCS12)` certificate for HTTPS; keep `trust-proxy` true only when the backend truly sits behind a trusted proxy;
8. Keep version-specific changes in adapter modules (reflection bridges / JdbcCompat / Platform overrides); core/common stay Java-8 and Bukkit-free; version differences must not leak into business code.
