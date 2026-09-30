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
| `getApiPrefix()` | annotation API global prefix (`/api`) |
| `isAuthEnabled()` | whether gateway auth is enabled |

> The prefix family (`serverPrefix()` / `apiPrefix()` / `pluginsPrefix()` / `apiFullPrefix()` / `pageFullPrefix()` / `webResourcePrefix()` / `fullPathPrefix()` / `scheme()` / `host()` / `port()` / `pageBase()` / `spaFallback()` / `fpEnabled()` / `fpStrict()`) lives on **capability group 4 ApiToolkitApi** (`api.getToolkit()`); full signatures are in Appendix A.5.

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
- `/soyshttp log [level]` or `/soyshttp reload` adjusts dynamically;
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

The repository ships two tracks: **1.8+ is produced by the core single-source multi-target profiles**; **1.6.4 / 1.7.10 are produced by dedicated adapter modules as full fat jars**.

### 6.8.1 Track 1: core single-source multi-target (1.8 – 1.21+)

The same core source stays **Java-8 bytecode** (core/common syntax baseline unchanged); Maven profiles switch the spigot-api compile baseline and compile JDK, producing several jars at once:

| Supported versions | Profile | Compile JDK | spigot-api baseline | plugin.yml `api-version` | Artifact |
| --- | --- | --- | --- | --- | --- |
| 1.8 | `-P1_8` | Java 8 | 1.8.8 | (empty) | `SOYSHTTPOverMC-1_8-<version>.jar` |
| 1.9 – 1.12.2 | `-P1_12` | Java 8 | 1.12.2 | (empty) | `SOYSHTTPOverMC-1_12-<version>.jar` |
| 1.13 – 1.16.5 | `-P1_16` | Java 8 | 1.16.5 | `1.13` | `SOYSHTTPOverMC-1_16-<version>.jar` |
| 1.17 – 1.20.4 | `-P1_17` | Java 17 | 1.17 | `1.17` | `SOYSHTTPOverMC-1_17-<version>.jar` |
| 1.20.5 – 1.21+ | `-P1_20_5` | Java 21 | 1.20.5 | `1.20.5` | `SOYSHTTPOverMC-1_20_5-<version>.jar` |

Build examples (run at the repository root; `-pl core -am` pulls in common automatically):

```bash
mvn -B clean package -DskipTests -P1_12 -pl core -am      # → SOYSHTTPOverMC-1_12-<version>.jar
mvn -B clean package -DskipTests -P1_17 -pl core -am      # → SOYSHTTPOverMC-1_17-<version>.jar (needs JDK17 toolchain)
mvn -B clean package -DskipTests -P1_20_5 -pl core -am    # → SOYSHTTPOverMC-1_20_5-<version>.jar (needs JDK21 toolchain)
```

Compile JDKs are switched via Maven Toolchains (`~/.m2/toolchains.xml` declaring each JDK, or a project-local `toolchains.xml` for local use); CI (GitHub Actions) switches `setup-java` versions per profile in a job matrix (see `.github/workflows/release.yml`).

### 6.8.2 Track 2: adapter full fat jars (1.6.4 / 1.7.10)

1.6.4 / 1.7.10 are produced by adapter modules as **full fat jars** (embedding adapter-common + common + core + the module's own implementation + merged META-INF/services) — **drop them straight into that server version's plugins/** (craftbukkit is provided by the server and not bundled):

| Supported versions | Module | Compile baseline (vendored lib/) | Artifact | Sniffer capability difference |
| --- | --- | --- | --- | --- |
| 1.6.x (min 1.6.4) | `adapter/v1_6x` | craftbukkit-1.6.4 | `SOYSHTTPOverMC-1_6-<version>.jar` | 1.6.4 has no Netty → **same-port sniffing unsupported**; use `http-backend.mode: standalone-server` |
| 1.7.x (target 1.7.10) | `adapter/v1_7x` | craftbukkit-1.7.10 | `SOYSHTTPOverMC-1_7-<version>.jar` | 1.7.10 ships Netty → **same-port sniffing supported** (reflection resolves v1_7_R4 ServerConnection via method-name + field-type dual channel) |

Both modules carry version-specific adaptation: ① **Platform overrides** (1.6.4 / 1.7.10 `YamlConfiguration.loadConfiguration(File)` has no explicit encoding entry → `V1_6YamlIo` / `V1_7YamlIo` implement UTF-8 I/O themselves, wired in via `loadYaml/saveYaml` overrides in `V1_6PlatformAdapter` / `V1_7PlatformAdapter`); ② **AdapterActivator entry points** (registered via ServiceLoader, discovered by adapter/common `AdapterActivators`); ③ **JDBC compat** (`V1_6JdbcCompat` / `V1_7JdbcCompat`).

Build commands (install the root project first, then build the adapter aggregator; adapter modules depend on common/core in the local repo):

```bash
mvn -B install -DskipTests                     # root: install common + core + adapter/common
mvn -f adapter\pom.xml -B clean package        # adapter aggregator: all 1_6 / 1_7 fat jars at once
mvn -f adapter\v1_7x\pom.xml -B clean package  # or build a single module
```

Artifacts are copied to the repository root `output/`. Version differences (reflection bridges / JdbcCompat / Platform overrides) stay inside adapter modules; core/common remain Java-8 and Bukkit-free.

## 6.9 Best Practices Checklist

### 6.9.1 Third-party plugins: softdepend + readiness null-check / deferred registration

**Checklist**: third-party plugins **must** declare `softdepend: [SOYSHTTPOverMC]` and null-check in onEnable; when load order is uncertain, listen for `SoysReadyEvent` and register lazily.

**Why**: `softdepend` only guarantees "this plugin is loaded after SOYS when SOYS is present", not ordering — if your plugin loads first, `register()` will fail because the SOYS facade is not initialized yet.

**How**: the recommended path is extending `SoysExpansion` + a single `register()` call; when ordering is uncertain, listen for `SoysReadyEvent` (fired synchronously after the host's full onEnable).

plugin.yml:

```yaml
name: MyShop
version: 1.0.0
main: com.example.myshop.MyShopPlugin
api-version: 1.13
softdepend: [SOYSHTTPOverMC]
```

onEnable (null-check + event fallback):

```java
public final class MyShopPlugin extends JavaPlugin {
    private boolean registered;

    @Override
    public void onEnable() {
        JavaPlugin soys = (JavaPlugin) getServer().getPluginManager().getPlugin("SOYSHTTPOverMC");
        if (soys != null && soys.isEnabled()) {
            registerExpansion();                 // SOYS ready: register directly
        } else {
            getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onSoysReady(SoysReadyEvent e) { registerExpansion(); }
            }, this);                            // SOYS not ready: defer via ready event
        }
    }

    private void registerExpansion() {
        if (!registered) {
            registered = new ShopExpansion().register();   // returns false + warns on failure (e.g. identifier clash)
        }
    }
}
```

Expansion skeleton (SoysExpansion deep-dive in Chapter 8):

```java
public class ShopExpansion extends SoysExpansion {
    @Override public String getIdentifier() { return "shop"; }   // the only required metadata

    @Override protected String resourceRoot() { return "dist"; } // optional: auto-host dist (lazy registration)

    @Override protected java.util.List<Object> buildControllers() {
        return java.util.Collections.singletonList(this);        // this class is the controller
    }

    @GetMapping("/items")
    public AjaxResult items(ApiRequestContext ctx) { ... }
}
```

### 6.9.2 Dependencies: provided scope on common / core

**Checklist**: when compiling against annotations / ORM / host facade classes, use `provided` scope — everything is supplied by SOYS at runtime.

**Why**: `soyshttpovermc-common` has no Bukkit dependency (ORM / i18n / log facade etc. are pure logic) and compiles on any JDK; host facades like `SoysExpansion` live in `core`. Using `compile` scope would duplicate SOYS classes inside your plugin and clash with the host.

pom.xml:

```xml
<dependency>
    <groupId>com.github.cocosoys.mc</groupId>
    <artifactId>soyshttpovermc-common</artifactId>
    <version>1.4.0</version>
    <scope>provided</scope>          <!-- pure logic layer: ORM / i18n / logging -->
</dependency>
<dependency>
    <groupId>com.github.cocosoys.mc</groupId>
    <artifactId>soyshttpovermc-core</artifactId>
    <version>1.4.0</version>
    <scope>provided</scope>          <!-- host facade: SoysExpansion / events / API -->
</dependency>
<dependency>
    <groupId>org.spigotmc</groupId>
    <artifactId>spigot-api</artifactId>
    <version>1.16.5-R0.1-SNAPSHOT</version>
    <scope>provided</scope>          <!-- Bukkit API: compile-time only as well -->
</dependency>
```

Never use `compile` scope for common/core, and never shade them into your own jar.

### 6.9.3 Controllers: return AjaxResult / no heavy work on the main thread

**Checklist**: controllers should return `AjaxResult` (or `ApiResponse`) for a consistent structure; do not do heavy work on the main thread (handlers run on worker threads).

**Why**: the front end parses the unified `{code, msg, data}` envelope; handlers run on worker threads, so blocking main-thread APIs or `Thread.sleep` will drag the whole server.

```java
@RestController
@RequestMapping("/shop")
public class ShopController {

    @GetMapping("/items")
    public AjaxResult items(ApiRequestContext ctx) {
        List<Item> list = shopService.queryByOwner(ctx.getPlayerName());
        return AjaxResult.success(list);            // {"code":200,"msg":"操作成功","data":[...]}
    }

    @PostMapping("/buy")
    public AjaxResult buy(ApiRequestContext ctx, @RequestBody BuyRequest req) {
        if (!shopService.hasEnough(ctx.getPlayerName(), req.getPrice())) {
            return AjaxResult.forbidden("余额不足");  // {"code":403,"msg":"余额不足"}
        }
        shopService.buy(ctx.getPlayerName(), req);
        return AjaxResult.success("购买成功", null);   // custom top-level msg
    }
}
```

For expensive work (bulk queries, external calls), run `getServer().getScheduler().runTaskAsynchronously(...)` in the background and write results back via a callback; never synchronously wait on the network inside a controller.

### 6.9.4 ApiRequestContext: getPlayer() switches to the main thread — use getPlayerName() in loops

**Checklist**: `ApiRequestContext.getPlayer()` switches to the main thread — do not call it in loops; use `getPlayerName()` as the stable anchor; when a Player is truly needed, use `getSyncPlayer()` for a single synchronous fetch.

**Why**: `getPlayer()` synchronously hops to the Bukkit main thread to resolve the online player (null when offline); calling it repeatedly in loops / bulk scenarios causes a main-thread round-trip storm.

```java
// ❌ Wrong: getPlayer() per loop iteration, each hopping to the main thread
for (String itemId : itemIds) {
    Player p = ctx.getPlayer();
    if (p != null && p.hasPermission("shop.vip")) { ... }
}

// ✅ Correct: anchor on name (no main-thread hop); fetch Player once when needed
String name = ctx.getPlayerName();          // stable anchor, usable on any thread
if (!hasVip(name)) return AjaxResult.forbidden("需要 VIP");
Player me = ctx.getSyncPlayer();            // the only synchronous main-thread hop
if (me != null) me.sendMessage("购买成功");
```

### 6.9.5 Page permissions: pages.yml single-page inline permissions (AND) + /soyshttp perm

**Checklist**: page permissions should prefer `pages.yml` single-page inline `permissions` (all must match, AND semantics); grant nodes via `/soyshttp perm`; pages without inline permissions fall back to the `pages.permissions` global rule.

**Why**: page authorization runs before the page is opened (PagePermissionChecker); the single-page rule overrides the global rule; `permissions: []` means explicitly no interception, which can dodge an over-broad global rule.

pages.yml:

```yaml
pages:
  permissions:                       # global fallback rule
    - "soys.page"
  page:
    "/web/plugins/shop/admin":
      resource: web/plugins/shop/admin.html
      permissions:                   # single-page rule: AND, overrides global
        - "soys.admin"
        - "shop.manager"
    "/web/plugins/shop/vip":
      resource: web/plugins/shop/vip.html
      permissions: []                # explicitly empty = no permission gate for this page
```

Granting (op):

```
/soyshttp perm user Steve add shop.manager        # direct user node ('-' prefix = deny; ':' ≡ '.')
/soyshttp perm group create vip 50 VIP组          # create a permission group
/soyshttp perm group add vip shop.vip             # add a node to the group
/soyshttp perm user Steve group add vip           # join the user to the group
/soyshttp perm check Steve shop.manager           # debug: does the player have this node
```

### 6.9.6 Caching: pin hot resources, watch large-file thresholds

**Checklist**: add high-value resources to `web.cache.pinned`; watch `web.large-file-threshold` and `web.large-file-max-bytes` (default 128MB) for very large files.

**Why**: the LRU cache evicts cold-frequency entries; an evicted entry page / framework JS re-reads from disk or jar on every request, hurting both UX and performance; responses above the threshold go through the large-file lazy loader instead of the regular cache.

pages.yml:

```yaml
web:
  cache:
    max-bytes: 16777216              # total cache cap 16MB
    max-entries: 1024
    ttl-seconds: 300
    pinned:                          # resident, never evicted
      - "/web/plugins/shop/index.html"
      - "/web/plugins/shop/static/css/app.css"
      - "/web/plugins/shop/static/js/chunk-vendors.js"
  large-file-threshold: 16777216     # responses above 16MB go through the large-file channel
  large-file-max-bytes: 134217728    # max single file (default 128MB)
```

### 6.9.7 Public deployments: allowlist / rate limit / HTTPS / trusted proxy

**Checklist**: on public deployments enable at least one of `ip-allowlist` / `rate-limit`; prefer a formal `keystore(PKCS12)` certificate for HTTPS; keep `trust-proxy` true only when the backend truly sits behind a trusted proxy.

**Why**: the plugin exposes HTTP directly on the MC port — running naked in public gets hammered by scanners / crawlers; TLS terminates in place so all three protocols share the port; a wrongly enabled `trust-proxy` lets spoofed `X-Forwarded-For` bypass the allowlist.

gateway/policies/ip-allowlist.yml:

```yaml
enabled: true
default: deny                 # list acts as an allowlist
list: ["127.0.0.1", "192.168.1.0/24", "114.114.114.114"]
trust-proxy: true             # only when truly behind BungeeCord / Velocity
```

gateway/policies/rate-limit.yml:

```yaml
enabled: true
scope: ip                     # ip | key (per X-API-Key)
rpm: 60                       # refilled per minute
burst: 10                     # burst cap; overflow → 429 + Retry-After
```

gateway/https.yml:

```yaml
enabled: true
keystore: plugins/SOYSHTTPOverMC/keystore.p12
keystore-pass: "********"      # PKCS12 takes precedence over cert/key PEM
min-tls: TLSv1.1
```

config.yml:

```yaml
mc:
  trust-proxy: true            # same semantics as ip-allowlist.trust-proxy: only behind a real proxy
```

### 6.9.8 Multi-version compatibility: keep changes inside adapter

**Checklist**: keep version-specific changes in adapter modules (reflection bridges / JdbcCompat / Platform overrides); core/common stay Java-8 and Bukkit-free; version differences must not leak into business code.

**Why**: one core source produces every artifact from 1.8 to 1.21+; writing `if (version > 1.13)` branches in business code would flatten every version's differences into every module — a maintenance explosion.

**How**: land new version differences in adapter as the "triple": ① Platform override (host capability differences such as YAML encoding); ② AdapterActivator (ServiceLoader entry); ③ JdbcCompat / reflection bridges (API shape differences). Skeleton:

```java
// adapter/v1_7x/.../adapter/v1_7/V1_7PlatformAdapter.java (sketch: extend the default impl, override encoding)
public final class V1_7PlatformAdapter extends PlatformBukkitImpl {
    private final V1_7YamlIo yaml = new V1_7YamlIo();

    @Override
    public ConfigSection loadYaml(File file) {           // 1.7.10 has no explicit encoding entry → custom UTF-8
        return yaml.load(file);
    }

    @Override
    public void saveYaml(ConfigSection cfg, File file) throws IOException {
        yaml.save(cfg, file);
    }
}
```

```java
// adapter/v1_7x/.../adapter/v1_7/V1_7SocketSnifferAdapter.java (sketch: reflection into v1_7_R4 ServerConnection)
public final class V1_7SocketSnifferAdapter implements SocketSnifferAdapter {
    public boolean supported() { return ServerVersion.atLeast("1.7"); }  // see adapter/common

    // resolve ServerConnection via the method-name + field-type dual channel; never import v1_7_R4 classes directly
}
```

Declare `V1_7AdapterActivator` in `META-INF/services/com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivator`; adapter/common `AdapterActivators` discovers and activates it.
