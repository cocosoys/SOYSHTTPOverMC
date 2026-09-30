# Chapter 8 Plugin Extension: SoysExpansion Minimal Registration

SoysExpansion is the **recommended one-stop registration entry** for third-party plugins (PlaceholderAPI-Expansion style): extend one abstract class, override declarative hooks, and call `register()` once in `onEnable` to complete **data initialization, endpoint registration, front-end page hosting, CORS declaration and unregister bookkeeping** in a single pass. The framework auto-detects the owning plugin, auto-appends prefixes and auto-tags registrations, so developers never need to touch internal APIs such as `ApiRegistrationApi` / `WebPageApi`.

## 8.1 Core Idea: Template Method + Declarative Hooks

`SoysExpansion` uses the **template method pattern**:

- `register()` / `unregister()` are `final` skeletons — **not overridable** — and invoke the overridable hooks in a fixed order;
- Every declarative hook has a **safe default** (no-op / empty / true); override only what you need;
- **Any failing step rolls back** the already-succeeded parts and returns `false`.

```java
public abstract class SoysExpansion {
    public abstract String getIdentifier();   // the only required hook
    public final boolean register()   { ... } // final skeleton, runs hooks in order
    public final boolean unregister() { ... }
}
```

## 8.2 Minimal Integration

### 8.2.1 plugin.yml

```yaml
name: MyShop
main: com.example.myshop.MyShop
version: 1.0.0
api-version: 1.13
softdepend: [SOYSHTTPOverMC]   # soft dependency: loads without the main plugin, just skips registration
```

### 8.2.2 The extension class

```java
package com.example.myshop;

import com.github.cocosoys.mc.soyshttpovermc.api.SoysExpansion;

public class ShopExpansion extends SoysExpansion {

    @Override
    public String getIdentifier() { return "shop"; }   // the only required metadata
}
```

### 8.2.3 One-line registration in onEnable

```java
@Override
public void onEnable() {
    if (!new ShopExpansion().register()) {
        getLogger().warning("ShopExpansion registration failed (identifier conflict? main plugin not ready?)");
    }
}

@Override
public void onDisable() {
    new ShopExpansion().unregister();   // precise unregister (the framework also cleans up by owner plugin name)
}
```

At this point the extension is registered (visible in `SoysExpansion.registered()`) but declares no endpoints / pages / data — so it creates **no routes besides occupying an identifier**. All capabilities come from the optional hooks below.

## 8.3 Metadata Hooks

| Hook | Default | Description |
| --- | --- | --- |
| `getIdentifier()` | **required (abstract)** | Unique extension id: conflict detection (duplicate identifier is rejected), page tag (`expansion:<identifier>`), unregister bookkeeping |
| `getAuthor()` | `""` | author info (docs / debug display, optional) |
| `getVersion()` | `""` | version info (docs / debug display, optional) |

## 8.4 Endpoint Registration (buildControllers / buildProxyControllers)

Endpoints come from **two overridable "instance list" hooks**, shared by registration and unregistration:

| Hook | Default | Registration | Route shape |
| --- | --- | --- | --- |
| `buildControllers()` | `[this]` (the extension class itself) | **common registration** (`registerCommonController`) | `/api/plugins/<pluginName>/...` |
| `buildProxyControllers()` | `[]` (none) | **proxy registration** (`registerProxyController`) | `/api/...` (no plugin segment; under the main plugin's name) |

### 8.4.1 Endpoints written on the extension class (works by default)

```java
public class ShopExpansion extends SoysExpansion {

    @Override
    public String getIdentifier() { return "shop"; }

    @GetMapping("/items")
    public AjaxResult items(@RequestParam(name = "page", defaultValue = "1") int page) {
        return AjaxResult.success("...");
    }
}
```

The default `buildControllers()` returns `[this]`, so the extension class itself is commonly registered → `GET /api/plugins/<yourPluginName>/items`.

### 8.4.2 Batch registration when endpoints live in separate controller classes

```java
@Override
protected List<Object> buildControllers() {
    return Arrays.asList(new ShopAdminController(), new ShopQueryController());
}
```

### 8.4.3 Prefix-less paths (e.g. RuoYi-style `/api/prod-api/*`)

```java
@Override
protected List<Object> buildProxyControllers() {
    return Arrays.asList(new ShopApiController());
}
```

> The two sources **can be used together**: the same instance may appear in both lists (registered twice under different paths); unregistration is idempotent per instance, so clearing before emptying is harmless.
> For full control of the "common first, proxy second / proxy only" order, override `registerCommonController()` / `registerProxyController()` (see 8.10) — **no need to rewrite the `registerControllers()` aggregate**.

## 8.5 Front-end Page Hosting (resourceRoot)

`resourceRoot()` returns the in-jar front-end resource root (e.g. `"dist"`); when non-null, `registerPages()` auto-hosts it:

```
plugins/<pluginName>/<resourceRoot>   disk first (lazy registration: read on request, hot-replace supported)
   ↓ falls back when the directory does not exist
in-jar /<resourceRoot>                packaged default directory
```

Hosting effects:

- Page URL: `/web/plugins/<pluginName>/**` (matches `pageFullPrefix`);
- All registered pages are tagged `expansion:<identifier>`; unregistration cleans them precisely by tag (including directory index / SPA fallback rules);
- `__SOYS_CONTEXT__.js` contract files in pages are auto-injected with the server's real environment primitives (scheme / host / port / all prefixes, see Chapter 3), so the front end never hard-codes the deployment environment.

```java
@Override
protected String resourceRoot() { return "dist"; }   // auto-host dist
```

Companion index / SPA hooks:

| Hook | Default | Description |
| --- | --- | --- |
| `indexFallbackEnabled()` | `true` | Visiting `/web/plugins/<pluginName>` (with or without trailing slash) that misses regular resolution gets a 302 to `…/<indexFile>`; `false` → 404 |
| `indexFile()` | `"index"` | Directory index target (no extension; the smart `.html` matcher resolves `index.html`) |
| `spaFallback()` | `true` | history-mode SPA fallback: an unmatched `<extension-less path>` falls back to the plugin root's `index.html` (HTTP 200, the front-end vue-router decides route validity); **paths with extensions** (.js/.css/… like stale chunks) stay HTTP 404, never fall back (two-layer 404, see Chapter 3) |

> All three hooks take effect only when `resourceRoot()` is non-null.

## 8.6 CORS Declaration (cors)

`cors()` returns `CorsSpec[]`; when non-null, `registerCors()` registers each entry; default `null` = no CORS.

```java
@Override
protected CorsSpec[] cors() {
    return new CorsSpec[]{
        new CorsSpec("/api", "*"),                            // 2-arg shorthand: path prefix + allowed origin
        new CorsSpec("/admin", "https://example.com",
                     "GET,POST", "Authorization,Content-Type", true) // 5-arg full form
    };
}
```

`CorsSpec` fields: `pathPrefix` (empty or `"/"` = global), `origin`, `methods`, `headers`, `credentials`.

## 8.7 Data-layer Auto Initialization (dataRoots / sqlRoots / seedData / schemaVersion)

These four declarative hooks make data ready **before** endpoint registration (so endpoints never run against missing data), controlled by the main plugin's `auto.ops.*` switches in `config.yml`:

| Hook | Default | Semantics |
| --- | --- | --- |
| `dataRoots()` | `null` | In-jar default data file root (e.g. `"data"`): copies every file under it into the data folder at the same relative path, **never overwriting existing files** (preserves ops / runtime edits) |
| `sqlRoots()` | `null` | In-jar SQL init script root (e.g. `"sql"`): executes `init.sql` under it — **MySQL dialect only** (SQLite syntax is incompatible and tables are auto-created at runtime); scripts must be idempotent (IF NOT EXISTS / IGNORE) |
| `seedData()` | `null` | Seed data (entity instances): grouped by class, inserted only **when the table is empty** (idempotent) |
| `schemaVersion()` | `0` | Current schema version: `0` = init.sql + seeds only, no versioned migrations; `>=1` runs migration scripts `sql/migrations/V<n>__<description>.sql` (n from 1): fresh installs run `V1..V(schemaVersion)`, existing installs run the incremental range recorded in metadata |

```java
@Override
protected String[] dataRoots()      { return new String[]{"data"}; }
@Override
protected String[] sqlRoots()       { return new String[]{"sql"}; }
@Override
protected int schemaVersion()       { return 2; }   // runs V1__...sql and V2__...sql
```

## 8.8 Registration Order and Rollback

`register()` (final) runs in this order; **any failure / exception → roll back the succeeded parts and return `false`**:

```
1. Data init      registerData()        (dataRoots copy + sqlRoots exec + seedData seeds + schemaVersion migrations)
2. Endpoints      registerControllers()  (registerCommonController() over buildControllers()
                                        → registerProxyController() over buildProxyControllers())
3. Pages          registerPages()         (resourceRoot disk-first lazy / in-jar fallback, tagged expansion:<id>,
                                           sets directory index and SPA fallback rules)
4. CORS           registerCors()          (registers each entry of cors())
5. Callback       onRegister()            (default true; returning false or throwing also rolls back)
```

`unregister()` (final) removes in the reverse order: pages (by tag) → proxy endpoints → common endpoints → CORS → `onUnregister()` callback (already removed from the registry at that point). **`unregister()` only removes registrations and never deletes data** (for data removal use `/soyshttp data` data-layer auto ops).

## 8.9 Lifecycle Callbacks

| Hook | Default | Called |
| --- | --- | --- |
| `onRegister()` | `true` | Last step of the registration chain: returning `false` or throwing rolls the registration back and treats it as failed |
| `onUnregister()` | empty | Last step of the unregister chain: already removed from the registry |

## 8.10 Fine-grained Overrides (Layered Template Methods)

If the default "whole-class" granularity is not enough, override the **single-type hooks** without affecting the others:

- Endpoints: `registerControllers()` (aggregate) → `registerCommonController()` / `registerProxyController()` (each iterating its own instance list);
- Pages: `registerPages()` (fully replace the default hosting, e.g. manually offset basePath to host multiple front ends);
- CORS: `registerCors()`;
- Unregister: `unregisterControllers()` / `unregisterController()` / `unregisterProxyController()` / `unregisterPages()`.

```java
// Example: to register both common and proxy endpoints, override the two sub-hooks — no need to rewrite the aggregate
@Override
protected boolean registerCommonController() { /* register buildControllers() only */ }
@Override
protected boolean registerProxyController()  { /* register buildProxyControllers() only */ }
```

## 8.11 Static Helpers

| Method | Description |
| --- | --- |
| `SoysExpansion.bootstrap(SoysHttpOverMcApi api)` | Called by the main plugin after startup to establish the global API reference (done automatically before extension registration; normally not needed by developers) |
| `SoysExpansion.registered()` | The current registration table (`ConcurrentHashMap<String, SoysExpansion>`, key = identifier) |
| `getOwner()` (final) | The owning plugin (`JavaPlugin.getProvidingPlugin(getClass())` auto-detected) |
| `getApi()` (final) | The facade API (null when the main plugin is not ready) |

## 8.12 Relationship with the Facade API

Internally, `SoysExpansion` is exactly a **combination of facade calls (`SoysHttpOverMcApi`, 7 capability groups)**: data via `DataRegistration`, endpoints via `ApiRegistration`, pages via `WebPage`, CORS via `WebRegistry`. Therefore:

- 90% of integrations should use `SoysExpansion` (one-line registration, auto prefixes / tags / unregister);
- Advanced scenarios (dynamic registration / non-Expansion shapes / `force=true` overwrites) can still call the facade directly (see Chapter 2 §2.7, Chapter 3, and the Appendix).

## 8.13 FAQ

**Q1: `register()` returns false?**
Duplicate identifier (an extension with the same id is already registered) or the main plugin is not ready (`getApi()` is null). Print `registered()` to inspect the conflict source.

**Q2: Why is the page prefix `/web/plugins/<pluginName>` instead of the identifier?**
The page URL prefix is derived from the **owner plugin name** (`getProvidingPlugin`), independent of the identifier — even multiple expansion instances of the same plugin share one page prefix, so **do not host two front ends containing `index.html` in the same plugin** (`index` / SPA fallback rules are registered per plugin name; the later registration overwrites the earlier).

**Q3: Do I still need to call `unregister()` manually on disable?**
Recommended in `onDisable` (precise and immediate); the framework also cleans up endpoints / pages / CORS under that plugin name as a fallback.

**Q4: Does `unregister()` delete data?**
No. Data-layer auto ops (init / update / reinstall / uninstall) are handled by `/soyshttp data <plugin> ...` and the `soys_schema_meta` version records, decoupled from the registration table.

**Q5: Which dist wins, disk or in-jar?**
Disk wins: if `plugins/<pluginName>/<resourceRoot>` exists it is lazily registered from disk (hot-replace supported); otherwise the in-jar resource directory of the same name is used.
