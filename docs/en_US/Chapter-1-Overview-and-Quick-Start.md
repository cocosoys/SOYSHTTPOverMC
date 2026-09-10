# Chapter 1 Overview & Quick Start

## 1.1 What This Project Is

SOYSHTTPOverMC is a plugin that runs on Spigot / Paper servers and lets the server serve, on the **same port**:

- Minecraft game connections (passed through untouched);
- Plain HTTP pages / APIs;
- HTTPS (the gateway hooks an `SslHandler` in place after sniffing the TLS first bytes; no separate port).

At its core is a **same-port sniffer (SocketSniffer)**: it reads the first packet on the server's listening socket and dispatches by protocol signature — MC / HTTP / TLS — to different processing paths. Different server versions achieve the same effect through `adapter` modules (see 1.5).

## 1.2 Architecture Overview

```
browser / curl ──┐
                 ├──> server listening port (e.g. 25565)
MC client ───────┘          │
                           ▼
                  same-port sniffer (first-byte dispatch)
                 MC  ────────────────> original MC pipeline (pass-through)
                 TLS ──> SslHandler ──> HTTPS request
                 plain HTTP ──────────> security gateway (GatewayFilter)
                                          │
        policy chain: TLS → IP allowlist → rate limit → auth → access limiter
                                          │
                    ┌─────────────────────┼─────────────────────┐
                    ▼                     ▼                     ▼
             annotation API route    web page route        standalone-server
           (/api/*, controllers)  (WebRegistry, pages.yml)  backend mode
```

Maven module layout:

| Module | Responsibility |
| --- | --- |
| `common` | Bukkit-free pure logic: annotations, ORM, storage abstraction, i18n, SPI (Platform / ConfigSection), proxy detection utilities |
| `core` | The main plugin (Bukkit-dependent): god class + delegate, API facade, gateway, web pages, commands, permissions, storage assembly |
| `adapter/common` | Version compatibility utilities (no Bukkit references, all reflection-based): ServerVersion, SocketSnifferAdapter, compat helpers |
| `adapter/v1_6x` etc. | Per-version adapter modules: sniffer installers, Platform overrides, JdbcCompat, YamlIo |

> The proxy module (SOYSHTTPOverMC-Proxy, BungeeCord) is a separate project built independently; it is not in this repository.

## 1.3 Installation & Artifacts

1. Pick the jar matching your server version (see the Introduction table) and drop it into `plugins/`;
2. First launch generates `EULA.yml`, `config.yml`, `pages.yml`, `language.yml`, the `gateway/` directory and the `web/` front-end directory;
3. **You must accept the EULA first**: edit `plugins/SOYSHTTPOverMC/EULA.yml` and set `eula: false` to `eula: true`, otherwise the plugin disables itself (the console prints the agreement);
4. Restart or `reload` the server. Success is announced by the startup banner.

At startup the log reports key facts: topology (standalone / BungeeCord / Velocity), HTTP backend mode, gateway & HTTPS status, API registration count, and the web root.

### 1.3.1 Quick Self-Check

```text
/soyshttp status      # this server's address/port/backend mode/sniffer/gateway/HTTPS/registration counts
/soyshttp pages       # registered web pages
/soyshttp api         # registered annotation-based API endpoints
/soyshttp tokens      # issued session tokens
```

Browse to `http://<server-ip>:<port>/` — the default homepage (`web/` or the jar-bundled `dist/`) should load.

## 1.4 Plugin Data Directory Layout

```
plugins/SOYSHTTPOverMC/
├── config.yml            # main config (mc/sniffer/http-backend/log/permission/storage)
├── EULA.yml              # usage agreement (eula: true to accept)
├── pages.yml             # front-end web.* config + manual page registration + page permissions
├── language.yml          # i18n (current/rule/sources)
├── language/             # language packs (zh_cn.yml / en_us.yml)
├── gateway/
│   ├── config.yml        # gateway master switch / api-prefix / debug-events
│   ├── https.yml         # TLS certificates and protocol settings
│   ├── policies/         # security policies (auth / ip-allowlist / rate-limit / access-limiter / tls)
│   └── issuers/          # credential issuers (session-token.yml)
├── web/                  # front-end disk root (extracted from jar /dist/ on first launch)
├── data/                 # YAML storage & ORM data dir (records.yml + per-table .yml)
├── data/records.db       # SQLite storage (when enabled)
└── token.key             # local JWT secret (auto-generated)
```

## 1.5 Per-Version Implementation Differences (Important)

| Capability | 1.12.2 | 1.7.10 | 1.6.4 |
| --- | --- | --- | --- |
| Same-port sniffing | Netty pipeline injection | Relocated-netty reflection bridge | Connection-level interception (reflection-replaced ServerSocket) |
| `http-backend.mode` | all four modes | direct / memory-queue / netty-eventloop (standalone also works) | direct / memory-queue / netty-eventloop (standalone also works) |
| SQLite / MySQL | server-bundled modern driver | adapter JdbcCompat auto-compat with old drivers | adapter JdbcCompat auto-compat with old drivers |
| TLS | per runtime JDK | per runtime JDK | per runtime JDK (JDK7 caps at TLSv1.1/1.2) |
| config.yml encoding | UTF-8 | must be saved as UTF-8 | must be saved as UTF-8 |

> On 1.6.4 / 1.7.10, Bukkit's `YamlConfiguration` reads by platform encoding (GBK on Windows); the adapter already overrides this to explicit UTF-8 reads, but **save config files as UTF-8 yourself** — avoid Notepad's default ANSI/GBK.

## 1.6 /soyshttp Command Overview

Main command `/soyshttp` (short alias `/shttp`), requires `soyshttp.admin` (OP by default).

| Subcommand | Purpose |
| --- | --- |
| `/soyshttp reload` | Hot reload: log level, language, pages.yml, storage, gateway policies & TLS, login bridge, page registration |
| `/soyshttp key <subject>` | Issue a highest-privilege credential for a subject (static key, carries the `adm` marker) |
| `/soyshttp send <url\|/page> [display text] [player]` | Send a clickable link to a player |
| `/soyshttp pages [all]` | List registered pages (default: only .html pages and redirects) |
| `/soyshttp api` | List registered annotation-based API endpoints (method/path/owner/permission) |
| `/soyshttp tokens` | List all issued session tokens |
| `/soyshttp lang [code]` | View / switch language (`lang sources ...` manages extra language sources) |
| `/soyshttp perm ...` | Local permission table management (group/user CRUD + check; pairs with `offline-fallback: local`) |
| `/soyshttp log-level <level>` | Dynamically adjust log level (OFF/ERROR/WARN/INFO/DEBUG/TRACE) |
| `/soyshttp migrate <from> <to>` | Migrate data between storage backends |
| `/soyshttp sync` | Full overwrite-sync from primary storage to all secondaries |
| `/soyshttp status` / `report` / `eula` / `help` | Status / data-contribution report / EULA viewer / help |

## 1.7 Integrating Your First Third-Party Plugin

### 1.7.1 Prerequisite

Declare a soft dependency in your `plugin.yml`:

```yaml
softdepend: [SOYSHTTPOverMC]
```

### 1.7.2 Getting the Facade

```java
public class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Case A: this plugin loads after SOYSHTTPOverMC — fetch directly
        HttpOverMcPlugin soys = HttpOverMcPlugin.getInstance();
        if (soys == null) { getLogger().warning("SOYSHTTPOverMC not loaded, skipping integration"); return; }
        SoysHttpOverMcApi api = soys.getApi();
        // register an annotation controller (capability group 1)
        api.getApiRegistration().registerController(new MyApi());
        // register a web page (capability group 2)
        api.getWebPage().registerPage(this, "/hello",
                "<h1>Hello from MyPlugin</h1>".getBytes(StandardCharsets.UTF_8), "text/html");

        // Case B: this plugin might load before SOYS — listen for SoysReadyEvent and register lazily
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onReady(SoysReadyEvent e) {
                e.getApi().getApiRegistration().registerController(new MyApi());
            }
        }, this);
    }
}
```

### 1.7.3 Automatic Unregistration

APIs and pages registered under a third-party plugin are **unregistered automatically** when that plugin is disabled (via `ApiLifecycleListener`); you can also call `api.getWebPage().unregisterPluginPages(pluginName)` / `api.getApiRegistration().unregisterPluginControllers(pluginName)` explicitly.

### 1.7.4 Reload Hooks

If your plugin has its own config and wants to refresh on `/soyshttp reload`:

```java
api.registerReloadHook(() -> {
    myConfig.reload();
});
```

Equivalently, listen for `HttpConfigReloadEvent` (see Chapter 7).

## 1.8 Multi-Version Packaging (Build Side)

The repository builds multiple jars from one source via Maven multi-module + version profiles: `core` and `common` stay Java-8 bytecode, and each `adapter` version module picks its spigot-api dependency and compile JDK (see Chapter 6, 6.8). For your third-party plugin, depend on `soyshttpovermc-common` (no Bukkit dependency; compilable on any JDK) to use annotations and ORM; depend on `soyshttpovermc-core` or just integrate at runtime via `softdepend` when you need the facade API.
