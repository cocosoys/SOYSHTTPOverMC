# SOYSHTTPOverMC

**Run HTTP/HTTPS on top of your Minecraft inbound port** — the same socket serves MC players, plaintext HTTP, and HTTPS simultaneously. No separate web port, no port-forwarding; just point your browser at `https://<server-address>:<MC-port>` and the in-game web UI opens.

> Spigot/Paper 1.6.4 ~ 26.x multi-version plugin (pick the matching jar for your version, see [Requirements](#requirements)). Optional BungeeCord-side proxy module (reverse proxy / cross-server egress).

**Language / 语言**: English · [中文](README.md)

## Table of Contents

- [Features](#features)
- [How It Works (brief)](#how-it-works-brief)
- [Requirements](#requirements)
- [Installation & Usage](#installation--usage)
- [Storage & ORM (optional)](#storage--orm-optional)
- [Command Reference](#command-reference)
- [Developer Extension (third-party plugins)](#developer-extension-third-party-plugins)
- [Documentation](#documentation)
- [Build](#build)
- [Security Notes](#security-notes)
- [Open-Source Notes](#open-source-notes)
- [Disclaimer](#disclaimer)

---

## Features

- **Same-port triple-protocol coexistence**: sniff the first packet on the Spigot listening socket and demultiplex — MC handshakes pass through to players, HTTP/TLS handled in place, neither side disturbed;
- **In-place HTTPS upgrade**: TLS attached dynamically inside the sniffer (no separate 443), supports PKCS12 / PEM / auto self-signed cert;
- **Annotation-driven REST API**: Spring-like `@GetMapping` / `@PostMapping` / `@ApiName` / `@ApiPermission` + `AjaxResult`; register an endpoint in one line;
- **Security gateway**: TLS enforcement (426 Upgrade), IP allowlist (CIDR), auth (X-API-Key / Bearer / Cookie session token), token-bucket rate limiting; pluggable policy chain;
- **Session tokens**: stateless JWT (HS256) + logout blacklist; offline cookies auto-upgrade to online tokens after login; `/soyshttp key` issues a server-owner top-privilege key;
- **BungeeCord / proxy support**: BungeeCord/Waterfall/Velocity backends auto-detected, cross-server requests via `/server/<subserver>/...`, plus an optional proxy module (reverse HTTP/HTTPS on the proxy listening port);
- **Login-plugin integration**: `LoginProvider` SPI — AuthMe implementation shipped (web login, password verify, remember-me); extendable to other login plugins;
- **Developer-friendly surface**: `SoysExpansion` minimal registration (one `register()` call wires up endpoints / page hosting / CORS / data init — see [Developer Extension](#developer-extension-third-party-plugins)); or use the unified facade `SoysHttpOverMcApi` (annotation controllers / page registration / bulk directory hosting / custom MIME / credentials / cross-server HTTP / data ops), ready in onEnable;
- **ORM multi-backend storage**: unified `DATA` facade routes (SQL when available, else YAML fallback), `YAML.Pojo` / `SQL.Pojo` homogeneous API; multiple backends can be enabled simultaneously — a single primary backend by priority handles default R/W, others remain reachable via typed overloads; `/soyshttp migrate|sync|data` provides migration / overwrite sync / automated data ops;
- **Performance**: gzip, ETag/304 caching, HTTP/1.1 keep-alive, real visitor IP passthrough (`X-Forwarded-For`).

---

## How It Works (brief)

```
Browser ──TLS/HTTP──> Spigot listening port (server-port)
                       │  SocketSniffer first-byte classifier
                       ├─ MC handshake ────────► Spigot MC decoder (players join normally)
                       ├─ plaintext HTTP ──────► gateway policy chain → 426 Upgrade to TLS
                       └─ TLS (0x16 0x03) ────► in-place SslHandler decrypt → gateway policy chain
                                                → WebFrontendHandler routes:
                                                   login / annotated API / plugin pages / static
                                                   response written back over the same connection
```

BungeeCord mode: install the plugin on every backend subserver; optionally drop `SOYSHTTPOverMC-Proxy.jar`
on the BungeeCord proxy to do first-byte classification and reverse proxy on the proxy listening port
(home-server=self hosts static pages on the proxy itself, or routes `/` and unprefixed `/api/...` to a chosen subserver).

---

## Requirements

| Item       | Requirement                                                                                       |
| ---------- | ------------------------------------------------------------------------------------------------- |
| Server     | Spigot / Paper **1.6.4 ~ 26.x** (pick the matching jar); BungeeCord / Waterfall (optional proxy module) |
| Java runtime | Match the chosen artifact: 1.6 ~ 1.16.5 → **Java 8/11**; 1.17 ~ 1.20.4 → **Java 17**; 1.20.5 ~ 1.21+ → **Java 21**; 26.x → **Java 25** |
| Java build | JDK 8 by default; newer modules auto-switch via Maven toolchains (JDK 8/11/17/21/25, see [Build](#build)) |
| Optional   | AuthMe (web login); BungeeCord proxy module (cross-server egress)                                |

Version-to-artifact map (pick from Release, no config changes needed):

| Server version     | Artifact (`<ver>` = plugin version)          | Java |
| ------------------ | --------------------------------------------- | ---- |
| 1.6.x (min 1.6.4)  | `SOYSHTTPOverMC-1_6-<ver>.jar`               | 8    |
| 1.7.x              | `SOYSHTTPOverMC-1_7-<ver>.jar`              | 8    |
| 1.8 ~ 1.12.2       | `SOYSHTTPOverMC-1_8-<ver>.jar` / `SOYSHTTPOverMC-1_12-<ver>.jar` (default) | 8 |
| 1.13 ~ 1.16.5      | `SOYSHTTPOverMC-1_16-<ver>.jar`             | 8/11 |
| 1.17 ~ 1.20.4      | `SOYSHTTPOverMC-1_20-<ver>.jar`             | 17   |
| 1.20.5 ~ 1.21.x    | `SOYSHTTPOverMC-1_21-<ver>.jar`             | 21   |
| 26.x               | `SOYSHTTPOverMC-1_26-<ver>.jar`             | 25   |

> ⚠️ Port convention: **access port == `server.properties` `server-port`** — no extra port, no firewall change (just browse that port).
> If behind a reverse proxy, set `mc.public-host` / `mc.public-port` (display-only).

---

## Installation & Usage

### Standalone mode

1. Download `SOYSHTTPOverMC-1_12-<version>.jar` from Releases into `plugins/`, restart the server;
2. In `server.properties`: `server-port=<your port>`;
3. Confirm the log shows `HTTP-Over-MC started (same-port sniffing...)`;
4. Browse `https://<address>:<port>/` (trust the self-signed cert manually or use `-k`).

### BungeeCord (network) mode

1. Install `SOYSHTTPOverMC.jar` on every backend subserver (same as standalone), and in `config.yml`:
   ```yaml
   mc:
     public-host: "proxy public IP or domain"   # display only (optional)
     public-port: 25577                          # client-reachable port
   server-name: subserver-name                  # must match BungeeCord config.yml servers.<name>
   proxy-address: "127.0.0.1:25577"             # cross-server forward target (BungeeCord listen)
   ```
2. Set `spigot.yml` `bungeecord: true` on each subserver (align with proxy `ip_forward: true`); firewall must allow proxy↔subserver loopback;
3. (Optional) install `SOYSHTTPOverMC-Proxy.jar` on BungeeCord, set `plugins/SOYSHTTPOverMC-Proxy/proxy.yml` `enabled: true`,
   `home-server: self` (proxy hosts static pages itself) or a subserver name (`/` and unprefixed `/api/...` route there);
   the proxy listen port is decided by BungeeCord `config.yml` (e.g. 25577) — you don't need to care about the exact port;
4. Cross-server access: `/server/<subserver>/...`.

### Config layout

Auto-generated on first start:

```
plugins/SOYSHTTPOverMC/
├── config.yml                 # core: mc.host/port / public-host / trust-proxy / server-name / proxy-address / storage / auto.ops
├── gateway/
│   ├── config.yml             # gateway master switch + api-prefix
│   ├── https.yml              # HTTPS: enabled / keystore(PKCS12) > cert+key(PEM) > self-signed
│   ├── policies/              # one file per security policy: tls.yml / ip-allowlist.yml / auth.yml / rate-limit.yml
│   │                         #   auth.yml also holds remember-me (auto.login.ttl / ip / fp / ticket) and X-API-Key fail-open switch
│   └── issuers/               # credential issuers: session-token.yml (JWT session token)
└── data/                      # extracted web frontend (hot-swap on disk); ORM data files (<table>.yml); token-secret.key (JWT secret — keep secret)
```

After editing config, run `/soyshttp reload` (command-style and annotated controllers require a restart).

---

## Storage & ORM (optional)

The plugin ships a built-in **ORM multi-backend storage**: entities are annotated with `@TableName` / `@TableId` / `@TableField`,
and routed through the unified facade `DATA` (SQL when available, else YAML fallback); a typed overload lets you pin a backend explicitly (e.g. `DATA.get(StorageType.SQLITE, ...)`).

- **Unified facade `DATA`**: business code writes one `DATA.select/get/insert/updateById/deleteById`, backend-agnostic;
  `YAML.Pojo` / `SQL.Pojo` are homogeneous lower-level facades for cases that need a fixed backend;
- **Multi-backend primary/secondary**: `storage.backends` can enable YAML / SQLite / MySQL simultaneously; by priority
  `MYSQL > SQLITE > YAML` a single primary backend handles default R/W; other enabled backends stay reachable via typed overloads;
- **Data ops**: `/soyshttp migrate <from> <to>` merge-semantics migration; `/soyshttp sync [<from> <to>]` overwrite-semantics sync
  (no args = primary → all secondaries); `/soyshttp data <plugin> status|update|reinstall|uninstall` automated data-layer ops;
- **Cross-server sharing**: point all instances at the same MySQL (`storage.backends.mysql.enabled: true`) and set `storage.cross-server: true`
  for shared data (token blacklist / audit / heartbeat / global key, entity table `soys_records`).

Enable a backend (`config.yml`):

```yaml
storage:
  backends:
    yaml:
      enabled: true
      file: data/              # folder for .yml tables (soys_records.yml + per-ORM tables)
    sqlite:
      enabled: false
      file: data/records.db
    mysql:
      enabled: false
      url: 'jdbc:mysql://localhost:3306/minecraft?useUnicode=true&characterEncoding=utf8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai'
      username: root
      password: ''
  cross-server: false          # cross-server sharing requires primary = MySQL and all subservers point to the same DB
```

> If old `config.yml` has no `storage` section, the plugin runs in in-memory mode; add the section to enable backends.

---

## Command Reference (`/soyshttp` or `/shttp`, op by default)

```
/soyshttp help [subcommand|page]          show help (or detailed usage of a subcommand)
/soyshttp eula                            show the EULA
/soyshttp status                          show HTTP service status
/soyshttp report                          manually report plugin usage
/soyshttp reload                          hot-reload config & gateway
/soyshttp key <subject>                   issue a top-privilege key for a subject (ak_ prefix, bypasses all permissions — use with care)
/soyshttp send <url|/page> [text] [player]   send a clickable link to a player
/soyshttp pages [all] [page]              list registered pages (UI pages by default; all includes resources/scripts)
/soyshttp api [plugin]                    list registered annotated API endpoints
/soyshttp tokens                          list all issued tokens
/soyshttp lang [code]                     view/switch language
/soyshttp log [level]                     view/change log level (OFF/ERROR/WARN/INFO/DEBUG/TRACE)
/soyshttp migrate <yaml|sqlite|mysql> <yaml|sqlite|mysql> [confirm]   merge-semantics migration between ORM backends
/soyshttp sync [<from> <to> [confirm]]   overwrite-semantics sync (no args = primary → all secondaries; both require confirm)
/soyshttp perm                            local built-in permission table (group/user CRUD + query), pair with permission.offline-fallback: local
/soyshttp apikey                          X-API-Key local table management (create/enable/expire/bind/permissions)
/soyshttp data <plugin> <status|update [ver]|reinstall|uninstall>   automated data-layer ops
```

---

## Developer Extension (third-party plugins)

Recommended: **extend `SoysExpansion`** (PlaceholderAPI-Expansion style). Add
`softdepend: [SOYSHTTPOverMC]` to `plugin.yml`, and in onEnable call **`register()` once** —
endpoint registration, owner auto-detection, page hosting, CORS declaration, data init, and unload cleanup
are all handled by the framework. You don't touch internal APIs like `ApiRegistrationApi` / `WebPageApi`:

```java
import com.github.cocosoys.mc.soyshttpovermc.api.SoysExpansion;
import java.util.List;
import java.util.Arrays;

public class ShopExpansion extends SoysExpansion {

    @Override
    public String getIdentifier() { return "shop"; }        // the only required metadata (conflict detection / page tag / unload key)

    // —— endpoints on the expansion class: auto-registered as normal (→ /api/plugins/<plugin>/...) ——
    @GetMapping("/items")
    public AjaxResult items(@RequestParam("page") int page) { ... }

    // —— optional: auto-host a frontend dist (disk-first lazy registration, hot-swappable; falls back to jar resource if no on-disk folder) ——
    @Override
    protected String resourceRoot() { return "dist"; }

    // —— optional: endpoints in standalone Controller classes —— bulk register (return all instances; shared by register/unregister) ——
    @Override
    protected List<Object> buildControllers() { return Arrays.asList(new ShopAdminController()); }

    // —— optional: proxy registration (no /plugins/<plugin> prefix, e.g. /api/prod-api/*; default none) ——
    @Override
    protected List<Object> buildProxyControllers() { return Arrays.asList(new ShopApiController()); }

    // —— optional: CORS declaration (empty pathPrefix or "/" = global) ——
    @Override
    protected CorsSpec[] cors() { return new CorsSpec[]{ new CorsSpec("/api", "*") }; }

    // —— optional: automated data init (init.sql / seed data / versioned migrations, gated by config auto.ops.*) ——
    @Override
    protected String[] dataRoots() { return new String[]{"data"}; }
    @Override
    protected int schemaVersion() { return 1; }
}
```

```java
// one line in onEnable:
if (!new ShopExpansion().register()) {
    plugin.getLogger().warning("ShopExpansion registration failed (identifier conflict? host not ready?)");
}
// call unregister() in onDisable for precise teardown (the framework also cleans up by owner plugin name as a fallback):
new ShopExpansion().unregister();
```

`SoysExpansion` template-method notes (`register()` / `unregister()` are final):

- Order: data init → endpoint registration (`registerControllers`: normal + proxy) → page hosting (`registerPages`) → CORS (`registerCors`) → `onRegister()` callback; **any failure rolls back what succeeded** and returns `false`;
- **Owner auto-detection**: `JavaPlugin.getProvidingPlugin(getClass())`, no manual plugin instance;
- **Duplicate `identifier` rejected**; pages are tagged `expansion:<identifier>`, unload cleans up by tag precisely (including directory index / SPA fallback rules);
- To customize a single registration type, override the matching hook (`registerCommonController` / `registerProxyController` / `registerPages` / `registerCors` / `unregister*`) without affecting others;
- Data init runs before endpoint registration (so endpoints see ready data); `unregister()` only removes registrations and **never deletes data**.

**The facade API remains available** (advanced scenarios / non-Expansion): `SoysHttpOverMcApi` has 7 capability groups —
`ApiRegistration` (annotation controllers), `WebPage` (pages/directories/resources), `AuthCredential` (credentials),
`Toolkit` (JSON / Content-Type / prefix family `apiPrefix` `pluginsPrefix` `apiFullPrefix`
`pageFullPrefix` `webResourcePrefix` `serverPrefix` `fullPathPrefix` `scheme` `host` `port` `spaFallback`
`pageBase` `fpEnabled` `fpStrict`), `HttpClient` (outbound HTTP / loopback / cross-server), `Extension`
(`LoginProvider` login-plugin SPI + custom `/soyshttp` subcommands + request interceptors + custom policies),
`DataRegistration` (automated data-layer ops).

Listenable events (all nested under abstract base classes; register with Bukkit's standard `registerEvents`):
`ApiEvent` (`ApiRegisteredEvent` / `ApiUnregisteredEvent` / `ApiAccessEvent` and its GET/POST/... subtypes /
`ApiAccessCompletedEvent` and its subtypes), `GatewayEvent` (`GatewayRequestEvent` / `GatewayRequestServedEvent` /
`GatewayAccessDeniedEvent` / `GatewayCredentialIssuedEvent` / `GatewayLoginResultEvent`),
`WebResourcesEvent` (`WebResourcesAccessEvent` / `WebResourcesLoadedEvent`), `SoysReadyEvent`, `HttpConfigReloadEvent`.

When a plugin is disabled, its APIs / pages / CORS are unloaded automatically.

---

## Build

Multi-module Maven project: `common` (Bukkit-free shared lib) + `core` (main plugin, default target 1.12.2, Java 8 bytecode) +
`adapter` (version-compat aggregator: `common` / `v1_6x` / `v1_7x` / `v1_16x` / `v1_20x` / `v1_21x` / `v1_26x`).
The project root ships `.mvn/maven.config` (`--toolchains toolchains.xml`) and `toolchains.xml`
(declaring JDK 8 / 11 / 14 / 17 / 21 / 25 `jdkHome`). **The same core source switches spigot-api and Java version via profiles** —
no manual JAVA_HOME juggling:

```powershell
# full build (one command from root; core uses JDK8, v1_20x/v1_21x/v1_26x auto-switch to JDK17/21/25 via toolchains)
mvn clean package "-Drevision=1.4.0"

# build only the main plugin with a target profile (profiles: 1_12 default / 1_8 / 1_16 / 1_17 / 1_20_5)
mvn -f core/pom.xml clean package -P1_17 "-Drevision=1.4.0"

# build only version-compat modules (requires common/core installed to local repo)
mvn -f adapter/pom.xml clean package "-Drevision=1.4.0"
```

Artifacts (in each module's `target/`, auto-copied to root `output/`):

| Artifact | Source | Target server |
| --- | --- | --- |
| `SOYSHTTPOverMC-1_12-<ver>.jar` | `core/target` (default; `-P1_8` → `-1_8`, `-P1_16` → `-1_16`, `-P1_17` → `-1_17`, `-P1_20_5` → `-1_20_5`) | 1.8 ~ 1.12.2 (default); variants: 1.8.8 / 1.16.5 / 1.17 / 1.20.5+ |
| `SOYSHTTPOverMC-1_6-<ver>.jar` | `adapter/v1_6x/target` | 1.6.x |
| `SOYSHTTPOverMC-1_7-<ver>.jar` | `adapter/v1_7x/target` | 1.7.x |
| `SOYSHTTPOverMC-1_16-<ver>.jar` | `adapter/v1_16x/target` | 1.16.x |
| `SOYSHTTPOverMC-1_20-<ver>.jar` | `adapter/v1_20x/target` | 1.20.x |
| `SOYSHTTPOverMC-1_21-<ver>.jar` | `adapter/v1_21x/target` | 1.21.x |
| `SOYSHTTPOverMC-1_26-<ver>.jar` | `adapter/v1_26x/target` | 26.x (Java 25) |
| `SOYSHTTPOverMC-Proxy-<ver>.jar` | **standalone Maven project** (`SOYSHTTPOverMC-Proxy/`, outside this reactor) | BungeeCord proxy |

> Pushing a tag (e.g. `v1.4.0`) automatically builds and uploads the Release; you can also trigger a build-only run manually from the Actions page.
> All third-party deps are pulled from Maven Central (netty-all / HikariCP / protobuf-java / jackson-annotations /
> sqlite-jdbc / mysql-connector-java etc.), no local install needed; the high-version module (v1_26x) requires JDK 25 and
> Lombok 1.18.38 (declared in-module).

---

## Security Notes

- The gateway defaults to TLS enforcement (`tls.yml`, plaintext → 426); to allow plaintext, disable it in `gateway/policies/tls.yml`;
- With `mc.trust-proxy: true` the backend trusts `X-Forwarded-For` from a front proxy; if the backend is directly reachable by clients, set `false` to prevent IP spoofing;
- Self-signed certs are not trusted by browsers (configure a real PKCS12/PEM cert in `gateway/https.yml`);
- `data/token-secret.key` is the JWT signing key — **keep it secret**; copy it over when migrating servers, otherwise old tokens invalidate;
- **X-API-Keys are stored hashed in the local `soys_api_key` table (platform-generated, shown only once)** — no plaintext static `keys`;
  `auth.yml` `api-key.local-fallback-all: false` by default — when the local table is unavailable the gateway **denies** rather than fail-open to "all permissions";
- The `/soyshttp key <subject>` top-privilege key (ak_ prefix, bypasses all permissions) is for server owners only — keep it secret;
- Device remember-me uses **device-fingerprint two-factor auth** (`auto.login.fp.*`, binding table `soys_device_binding`) instead of IP matching (IP cannot identify a personal device and would misfire under shared NAT);
- Local permission tables (`soys_perm_*` / `/soyshttp perm`) and the API key table (`soys_api_key`) are sensitive; they live under `data/` or a SQL backend — don't distribute your data folder.

---

## Open-Source Notes

- This project ships a `LICENSE` file — choose a license before public release (e.g. MIT / GPL-3.0, subject to dependency compatibility);
- **Third-party licenses**: `netty-all` (Apache-2.0), `HikariCP` (Apache-2.0), `protobuf-java` (BSD-3),
  `jackson-annotations` (Apache-2.0), `sqlite-jdbc` (Apache-2.0), `mysql-connector-java` (GPL-2.0 with FOSS exception),
  `AuthMe` (GPL-3.0, compile-time optional), `BungeeCord API` / `Velocity API` (compile-time optional) — review each before distribution;
- Issues / PRs welcome.

---

## Documentation

Detailed developer-oriented guides (tutorial style, with examples, parameter notes, and best practices), **maintained in sync in both languages**:

| Chapter | 中文 | English |
| --- | --- | --- |
| Introduction | [docs/zh_CN/导言.md](docs/zh_CN/导言.md) | [docs/en_US/Introduction.md](docs/en_US/Introduction.md) |
| 1 Overview & Quick Start | [docs/zh_CN/第1章-概述与快速开始.md](docs/zh_CN/第1章-概述与快速开始.md) | [docs/en_US/Chapter-1-Overview-and-Quick-Start.md](docs/en_US/Chapter-1-Overview-and-Quick-Start.md) |
| 2 Annotated Web API | [docs/zh_CN/第2章-注解式WebAPI开发.md](docs/zh_CN/第2章-注解式WebAPI开发.md) | [docs/en_US/Chapter-2-Annotated-WebAPI-Development.md](docs/en_US/Chapter-2-Annotated-WebAPI-Development.md) |
| 3 Web Pages & Static Resources | [docs/zh_CN/第3章-网页与静态资源托管.md](docs/zh_CN/第3章-网页与静态资源托管.md) | [docs/en_US/Chapter-3-Web-Pages-and-Static-Resources.md](docs/en_US/Chapter-3-Web-Pages-and-Static-Resources.md) |
| 4 Auth & Security | [docs/zh_CN/第4章-鉴权与安全管理.md](docs/zh_CN/第4章-鉴权与安全管理.md) | [docs/en_US/Chapter-4-Authentication-and-Security.md](docs/en_US/Chapter-4-Authentication-and-Security.md) |
| 5 Data Storage & ORM | [docs/zh_CN/第5章-数据存储与ORM.md](docs/zh_CN/第5章-数据存储与ORM.md) | [docs/en_US/Chapter-5-Data-Storage-and-ORM.md](docs/en_US/Chapter-5-Data-Storage-and-ORM.md) |
| 6 Advanced & Best Practices | [docs/zh_CN/第6章-进阶能力与最佳实践.md](docs/zh_CN/第6章-进阶能力与最佳实践.md) | [docs/en_US/Chapter-6-Advanced-Capabilities-and-Best-Practices.md](docs/en_US/Chapter-6-Advanced-Capabilities-and-Best-Practices.md) |
| 7 Event System | [docs/zh_CN/第7章-事件系统（使用与注册）.md](docs/zh_CN/第7章-事件系统（使用与注册）.md) | [docs/en_US/Chapter-7-Event-System.md](docs/en_US/Chapter-7-Event-System.md) |
| 8 SoysExpansion | [docs/zh_CN/第8章-SoysExpansion插件扩展.md](docs/zh_CN/第8章-SoysExpansion插件扩展.md) | [docs/en_US/Chapter-8-SoysExpansion-Extension.md](docs/en_US/Chapter-8-SoysExpansion-Extension.md) |
| Appendix API Reference | [docs/zh_CN/附录-API参考手册.md](docs/zh_CN/附录-API参考手册.md) | [docs/en_US/Appendix-API-Reference.md](docs/en_US/Appendix-API-Reference.md) |

---

## Disclaimer

This project is provided "as-is"; the authors are not liable for any direct or indirect damage from its use.
HTTP-Over-MC is experimental — please test thoroughly before production use.
