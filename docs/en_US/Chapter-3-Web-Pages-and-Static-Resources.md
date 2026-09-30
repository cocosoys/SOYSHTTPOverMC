# Chapter 3 Web Pages & Static Resource Hosting

SOYSHTTPOverMC offers two channels for hosting web pages: **programmatic registration** (`WebPageApi` → `WebRegistry`, for third-party plugins) and **config-file registration** (`pages.yml`, for admins). Both end up in the same routing table, served by `WebFrontendHandler`.

## 3.1 Namespace & Routing Conventions

- **Default prefix**: third-party plugin registrations automatically get `/web/plugins/<pluginName>`; plugin Foo registering `/dashboard` → `/web/plugins/Foo/dashboard`;
- **Forced proxy (no prefix)**: `registerProxyPage` / `registerProxyResource` register under the main plugin's name without the prefix (e.g. `/dashboard`); `owner` still records the real plugin and unregistration cleans them up together;
- **Nickname routes**: registrations may carry `nicknames`; visiting a nickname path hits the same page (e.g. `/主页`);
- **.html suffix smart matching**: after registering `/login`, both `/login` and `/login.html` work;
- **Parameterized routes**: paths containing `{name}` segments (e.g. `/users/{id}`) are auto-registered in the parameterized route table; matched requests expose path variables;
- **Directory-index fallback (index)**: visiting `/web/plugins/<pluginName>` (with or without a trailing slash) 302-redirects to `…/index` when normal resolution misses (which then hits `index.html` via smart matching). `setIndexRule(ownerName, enabled, indexFile)` toggles / retargets per plugin (default globally enabled);
- **SPA fallback declaration**: after `setSpaFallback(ownerName, enabled)`, **extension-less** misses under that plugin's namespace fall back to the root `index.html` (200, left to the front-end router); **extension-bearing** paths (`.js/.css/…`, i.e. likely static assets) still return HTTP 404 and never fall back (so stale chunks are never swallowed by HTML). Plugins without a declaration keep the original 404 semantics;
- **Redirect registration**: `registerRedirect` / `registerProxyRedirect` (WebRegistry) register internal 302 redirects; backtick-wrapped values in `pages.yml` also register 302 redirects;
- **Duplicate paths are blocked by default**; `force=true` overwrites and logs the plugin that forced the registration.

## 3.2 Facade Group 2: WebPageApi

`api.getWebPage()` provides (all delegating to `WebRegistry`). **Every registration method returns the registered `WebRegistry.Entry`** (with the resolved path / owner; `null` on failure); bulk directory registrations return `Set<Entry>` — no more silent void failures.

### 3.2.1 Minimal Convenience Registration (one/two args, owner auto-detected)

The top of the interface is a group of minimal methods — pass only the content, everything else is filled in automatically:

| Method | Auto-filled |
| --- | --- |
| `registerPage(byte[] content)` | path auto = `web/plugins/<plugin>/page/page-<seq>` (per-plugin counter); Content-Type `text/html; charset=utf-8` |
| `registerPage(String resourcePath)` | jar resource; path = `web/plugins/<plugin>/page/<resource file name without extension>` |
| `registerPage(String path, byte[] content)` | explicit path + content (auto-prepends `/web/plugins/<pluginName>`) |
| `registerResource(String resourcePath)` / `(String path, String resourcePath)` | jar resource (lazy read) |
| `registerProxyPage(String path, byte[] content)` / `registerProxyResource(String path, String resourcePath)` | without the `/web/plugins/<pluginName>` prefix |
| `registerErrorPage(byte[] content)` / `(String html)` | default 404 error page |
| `registerDirectory(File dir)` | basePath auto = `web/plugins/<plugin>/<dir name>`; bulk disk directory |

`owner` is auto-detected along the call stack via `JavaPlugin.getProvidingPlugin` (fallback: the main plugin).

### 3.2.2 Full Registration (explicit owner / all parameters)

| Method | Description |
| --- | --- |
| `Entry registerPage(Plugin, String path, byte[] content [, String contentType] [, boolean force])` | register a page (Content-Type inferred from extension or explicit) |
| `Entry registerResource(Plugin, String path, ClassLoader, String resourcePath [, String contentType] [, boolean force])` | register a jar resource (read on demand, memory-friendly) |
| `Entry registerProxyPage(Plugin, String path, byte[] content [, String contentType])` / `registerProxyResource(Plugin, String path, ClassLoader, String resourcePath [, String contentType])` | same without the `/web/plugins/<pluginName>` prefix |
| `Set<Entry> registerDirectory(Plugin, String basePath, File dir [, boolean proxy])` | bulk-register a disk directory (recursive, lazy read, hot-replaceable) |
| `Set<Entry> registerResourceDirectory(Plugin, String basePath, ClassLoader, String resourceRoot [, boolean proxy])` | bulk-register a jar resource directory |
| `Entry registerErrorPage(Plugin, int status, byte[] content)` / `(Plugin, int status, String html)` | register a custom error page (replaces 404/500 etc.) |
| `Entry registerNetworkPage(Plugin, NetworkPage page)` | network page (content fetched via `page.load()` on access; custom encrypted transport possible) |
| `NetworkTransport registerNetworkTransport(NetworkTransport)` | register a network transport provider (currently stored + warned only; not wired into the load chain) |
| `LargeFileLoader registerLargeFileLoader(LargeFileLoader)` | register a custom large-file loader (returns the instance) |
| `LargeFileLoader setDefaultLargeFileLoader(String name)` / `setLargeFileLoader(String pathPrefix, String name)` | switch the default / path-prefix-specific large-file loader |
| `void unregisterPluginPages(String pluginName)` / `int unregisterByTag(String tag)` / `void unregisterCors(String pluginName)` | unregister by plugin / source tag / CORS |
| `void setIndexRule(String ownerName, boolean enabled, String indexFile)` / `removeIndexRule(String ownerName)` | directory-index fallback rule (globally enabled by default, target `index`) |
| `void setSpaFallback(String ownerName, boolean enabled)` / `removeSpaFallback(String ownerName)` | SPA fallback declaration (extension-less → index.html; extension-bearing → still 404) |

> Full overloads carrying non-GET methods, nickname + description + **permissions**, CORS and redirects live in **`WebRegistry`** (e.g. `registerPage(owner, path, httpMethod, content, contentType, force, desc, nicknames, permissions)` / `registerCors(...)` / `registerRedirect(...)` / `registerProxyRedirect(...)`); the facade `WebPageApi` does not forward every overload — reach into `WebRegistry` for those (see 3.2 / 3.5).

Example:

```java
// minimal (content only; path & type auto-filled)
api.getWebPage().registerPage("<h1>Hello</h1>".getBytes(StandardCharsets.UTF_8));

// raw content
api.getWebPage().registerPage(this, "/hello",
        "<h1>Hello</h1>".getBytes(StandardCharsets.UTF_8), "text/html");

// jar resource (resources/dist/notice.html)
api.getWebPage().registerResource(this, "/notice",
        getClass().getClassLoader(), "dist/notice.html");

// bulk disk directory (returns Set<Entry>)
api.getWebPage().registerDirectory(this, "/app", new File(getDataFolder(), "webapp"));

// proxied page with nickname + description (no prefix; permission goes through the WebRegistry overload)
api.getWebPage().registerProxyPage(this, "/admin", null, bytes, "text/html", true,
        "Admin Panel", List.of("后台"));
webRegistry.registerProxyPage(this, "/admin", bytes, "text/html", true,
        "Admin Panel", List.of("后台"), List.of("soyshttp.page.admin"));
```

## 3.3 pages.yml (Admin Configuration)

`pages.yml` is assembled by `PagesConfig` and has three sections:

### 3.3.1 web.* — Front-End Root Configuration

```yaml
web:
  root: "/dist"            # front-end root: jar /dist/* extracted to plugins/SOYSHTTPOverMC/web/ and served as a disk directory root
  home: "/index.html"      # homepage (served when visiting /)
  cache:
    max-bytes: 16777216    # LRU cache byte cap (default 16MB)
    max-entries: 1024      # cache entry cap
    ttl-seconds: 60        # entry TTL (seconds)
    pinned: ["/", "/assets/"]   # pinned entries (exact path or directory prefix; not evicted, not counted against max-bytes)
  large-file-threshold: 16777216   # files larger than this are treated as large files (no cache; streamed loaders)
  large-file-max-bytes: 134217728  # hard cap for large files (rejected beyond this; default 128MB)
```

`web.home` supports three sources: relative/logical path (e.g. `dist/index.html`), absolute disk path (`C:/sites/home.html`), or a network URL (fetched on demand and cached ~5 minutes; falls back to the default homepage on failure).

> First run extracts the jar `/dist/*` into `web/`; existing files are not overwritten (your edits survive), missing disk files fall back to the jar-bundled versions.

### 3.3.2 pages.page / pages.auto — Manual Registration

```yaml
pages:
  page:
    "/":
      nicknames: ["主页"]
      description: "Homepage, also reachable via nickname '/主页'"
      resource: "dist/index.html"
      permissions: ["soyshttp.page.admin"]   # optional: single-page inline permissions (see 3.5)
  auto:
    "/": "dist/"                            # directory: recursively register all .html files under the folder
    "/link/mcmod": "`https://www.mcmod.cn/`"  # backtick-wrapped → 302 network redirect
```

- `pages.page.<path>.resource`: a single file/resource source; Content-Type inferred by extension (relative to the data dir / absolute disk path / jar `/dist/...`);
- `pages.auto`: key = URL path, value = source; single file / directory (recursive .html) / backtick network redirect;
- All registrations are **forced** (`force`), so they can overwrite same-path pages registered by core or other plugins;
- Re-assembled at every server start and `/soyshttp reload`.

### 3.3.3 Unload Mechanism (tag)

Pages registered by pages.yml carry `tag = "pages.yml"`; on reload, `webRegistry.unregisterByTag("pages.yml")` unloads the old registrations first, then re-registers — **deleted paths/nicknames never linger in memory**.

### 3.3.4 Alias Routes (pages.alias)

Give any existing link (including third-party plugin pages / APIs) a new "internal mapping" alias, for admins / ops:

```yaml
pages:
  alias:
    "/网址/插件/ERP": "/web/plugins/MCERP"   # key = alias, value = real path (a flat table)
    "/erp-admin":     "/web/plugins/MCERP"   # multiple aliases may point at one real link
```

Semantics:

- On alias hit the browser address bar stays unchanged; resolution continues on the real path (exact / .html smart / parameterized / nickname / index fallback all apply);
- **Exact single path, no prefix mapping**: sub-resources (e.g. `/web/plugins/MCERP/static/**`) still go through their original paths; the alias is an entry point only;
- The request's HTTP method is inherited (aliasing `/api/foo` maps GET/POST to the same-method real path);
- Only one hop (the real path is not itself alias-resolved, preventing loops);
- `/soyshttp pages` always shows an alias section at the end: `[✓]` = resolvable target, `[⚠ target not registered]` = not registered (visiting it returns 404).

## 3.4 Content Caching & Performance

`WebContentCache` (`core/.../web/WebContentCache`):

- **pinned**: matched entries live in pinned memory, not evicted and not counted against `max-bytes` (for portal homepages and other resources that must respond fast);
- **LRU + TTL**: applies to lazy sources only (disk files / jar resources, read on request); bytes given directly via `registerPage` are uncontrolled;
- **Large files**: disk files above `large-file-threshold` are not cached; handled by `LargeFileLoaderRegistry` (built-in streaming chunked loader by default); above `large-file-max-bytes` they are rejected outright (prevents a single file from blowing up memory).

## 3.5 Web Page Access Permissions

Two levels, permission nodes as **string arrays with AND semantics** (all entries must pass, otherwise → 302):

1. `pages.page.<path>.permissions` — single-page inline (highest priority; when the same path also exists globally, it **fully replaces, not merges**);
2. `pages.permissions` — global path rules:

```yaml
permissions:
  "/admin":     ["soyshttp.page.admin"]      # exact (includes /admin/... subpaths)
  "/console/*": ["soyshttp.page.console"]    # directory wildcard
  "*":          ["soyshttp.page.guest"]      # everything
```

Path matching reuses `AuthUtils.matchesPath` semantics (`/admin` exact, `/console/*` directory wildcard, `*` all). Behavior:

- **Not logged in** → 302 to the login page `login.html`;
- **Logged in but missing permission** → 302 to the permission-denied page `/perm-denied.html` (an `i` icon in the top-right corner shows the missing permission; includes a back button returning to the page before the block);
- Permission nodes go through the combined permission service (including the `local` permission table; grant `soyshttp.page.xxx` via `/soyshttp perm`);
- Applies to HTML pages / redirects only (css/js/images are not blocked); the login page and the denied page themselves are never intercepted.

Programmatic registration supports permissions too, via the **WebRegistry full overloads** (the facade `WebPageApi` does not expose a permission parameter):
`registerPage(owner, path, content, contentType, force, desc, nicknames, permissions)` /
`registerProxyPage(...)` / `registerResource(...)` — AND semantics, priority below pages.yml single-page inline and above global.

## 3.6 Error Pages & CORS

```java
// custom 404
api.getWebPage().registerErrorPage(this, 404, "<html>...</html>");

// CORS declaration (empty "/" pathPrefix = global) — lives on WebRegistry, not the facade
webRegistry.registerCors(this, "/api", "*", "GET,POST", "Content-Type,Authorization", true);
```

OPTIONS preflight requests matching a CORS declaration → automatic 204 + CORS headers (short-circuit); normal requests get the CORS headers appended.

## 3.7 Network Pages

Implement the `NetworkPage` interface (`path()` / `name()` / `contentType()` / `cacheTtlSeconds()` / `load()`), and implement custom transport — fetch, decrypt, verify — inside `load()`:

```java
api.getWebPage().registerNetworkPage(this, new NetworkPage() {
    public String path() { return "/secure"; }
    public byte[] load() throws Exception { return fetchAndDecrypt(); }
    public long cacheTtlSeconds() { return 60; }
});
```

## 3.8 Version Notes

- `MimeTypes`, caching and permission semantics are identical across versions; on 1.6.4 / 1.7.10 static file reads go through the adapter's `YamlIo` with explicit UTF-8.
