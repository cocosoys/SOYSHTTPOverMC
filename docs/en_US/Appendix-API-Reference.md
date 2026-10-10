# Appendix API Reference

This appendix summarizes the full API surface open to third-party developers, organized by "capability group". Signatures and behaviors are verified against the current source.

## A.1 The Facade: SoysHttpOverMcApi

Obtaining it:

```java
SoysHttpOverMcApi api = HttpOverMcPlugin.getInstance().getApi();
// event scenario: SoysReadyEvent e -> e.getApi()
```

| Capability group | Getter | Responsibility |
| --- | --- | --- |
| 1 API registration | `getApiRegistration()` | register/unregister annotation controllers, permission service |
| 2 Web hosting | `getWebPage()` | register pages/resources/directories/network pages/error pages/CORS |
| 3 Auth credentials | `getAuthCredential()` | register issuers, issue credentials |
| 4 Toolkit | `getToolkit()` | JSON, Content-Type, link messages, prefix family, contract primitives |
| 5 HTTP client | `getHttpClient()` | outbound requests, local loopback, environment adaptation |
| 6 Extensions | `getExtension()` | login providers, subcommands, interceptors, custom policies |
| 7 Data ops | `getDataRegistration()` | data-layer automation (install/update/reinstall/cleanup transactions, see Chapter 5) |

Also: `registerReloadHook(ReloadHttpConfigHandler)` registers a reload hook (refreshes your config on `/soyshttp reload`; equivalent to implementing `ReloadHttpConfigHandler`). Prefix/contract primitives (`serverPrefix()` / `apiPrefix()` / `scheme()` etc.) are **not** on the facade — they live in capability group 4 `ApiToolkitApi`.

## A.2 Group 1: ApiRegistrationApi

| Method | Description |
| --- | --- |
| `void registerController(Object controller)` | register a controller (non-main plugins automatically get `/plugins/<pluginName>`; overloads: `(Object, Plugin)` / `(Object, boolean force)` / `(Object, Plugin, boolean)`) |
| `void registerProxyController(Object controller)` | register as proxy under the main plugin's name (no plugin prefix; same overloads) |
| `List<ApiInfo> unregisterController(Object controller)` | unregister all endpoints of that controller; returns the unregistered snapshot |
| `List<ApiInfo> unregisterPluginControllers(String pluginName)` | unregister all endpoints of a plugin; returns the snapshot |
| `void setPermissionService(PermissionService service)` | plug in a custom permission-checking service |
| `PermissionService getPermissionService()` | the current permission service |
| `List<ApiInfo> getRegisteredApis()` | snapshot of all endpoints |
| `String getApiPrefix()` | the global prefix (/api) |

`ApiInfo`: `method` / `path` / `apiName` / `permission` / `ownerClass` / `ownerPlugin`.

## A.3 Group 2: WebPageApi

> Return types: **every registration method returns `WebRegistry.Entry`** (`null` on failure); bulk directory registrations return `Set<Entry>`. A minimal-convenience section sits at the top of the interface (one/two args, owner auto-detected).

**Minimal convenience section**:

| Method | Description |
| --- | --- |
| `Entry registerPage(byte[] content)` | path auto = `web/plugins/<plugin>/page/page-<seq>`; Content-Type `text/html; charset=utf-8` |
| `Entry registerPage(String resourcePath)` | jar resource; path = `web/plugins/<plugin>/page/<file name without extension>` |
| `Entry registerPage(String path, byte[] content)` | explicit path + content (auto-prepends `/web/plugins/<pluginName>`) |
| `Entry registerResource(String resourcePath)` / `(String path, String resourcePath)` | jar resource (lazy read) |
| `Entry registerProxyPage(String path, byte[] content)` / `registerProxyResource(String path, String resourcePath)` | without the plugin-name prefix |
| `Entry registerErrorPage(byte[] content)` / `(String html)` | default 404 error page |
| `Set<Entry> registerDirectory(File dir)` | basePath auto = `web/plugins/<plugin>/<dir name>`; bulk disk directory |

**Full registration** (explicit owner):

| Method | Description |
| --- | --- |
| `Entry registerPage(Plugin, String path, byte[] content [, String contentType] [, boolean force])` | web page (Content-Type inferred from extension or explicit) |
| `Entry registerResource(Plugin, String path, ClassLoader, String resourcePath [, String contentType] [, boolean force])` | jar resource (read on demand) |
| `Entry registerProxyPage(Plugin, String path, byte[] content [, String contentType])` / `registerProxyResource(Plugin, String path, ClassLoader, String resourcePath [, String contentType])` | without the plugin-name prefix |
| `Set<Entry> registerDirectory(Plugin, String basePath, File dir [, boolean proxy])` | bulk disk directory (recursive, lazy read, hot-replaceable) |
| `Set<Entry> registerResourceDirectory(Plugin, String basePath, ClassLoader, String resourceRoot [, boolean proxy])` | bulk jar resource directory |
| `Entry registerErrorPage(Plugin, int status, byte[] content)` / `(Plugin, int status, String html)` | custom error page |
| `Entry registerNetworkPage(Plugin, NetworkPage page)` | network page (custom loading/encrypted transport) |
| `NetworkTransport registerNetworkTransport(NetworkTransport)` | register a network transport provider (stored + warned only; not yet wired into the load chain) |
| `LargeFileLoader registerLargeFileLoader(LargeFileLoader)` | register a custom large-file loader (returns the instance) |
| `LargeFileLoader setDefaultLargeFileLoader(String name)` / `setLargeFileLoader(String pathPrefix, String name)` | switch the default / per-path-prefix loader |
| `void unregisterPluginPages(String pluginName)` / `int unregisterByTag(String tag)` / `void unregisterCors(String pluginName)` | unregister by plugin / source tag / CORS |
| `void setIndexRule(String ownerName, boolean enabled, String indexFile)` / `removeIndexRule(String ownerName)` | directory-index fallback rule (globally enabled by default, target `index`) |
| `void setSpaFallback(String ownerName, boolean enabled)` / `removeSpaFallback(String ownerName)` | SPA fallback declaration (extension-less → index.html; extension-bearing → still 404) |

> Full overloads carrying non-GET methods, nickname + description + **permissions**, CORS and redirects live in **`WebRegistry`** (e.g. `registerPage(owner, path, httpMethod, content, contentType, force, desc, nicknames, permissions)` / `registerCors(...)` / `registerRedirect(...)` / `registerProxyRedirect(...)`); the facade `WebPageApi` does not forward every overload — reach into `WebRegistry` for those (see Chapter 3, 3.2 / 3.5).

## A.4 Group 3: AuthCredentialApi

| Method | Description |
| --- | --- |
| `void registerCredentialIssuer(String name, Supplier<CredentialIssuer> factory)` | register an issuer factory (maps to gateway/issuers/<name>.yml) |
| `boolean isAuthEnabled()` | whether the auth policy is enabled |
| `List<String> getIssuerNames()` | names of enabled issuers |
| `IssuedCredential issueCredential(String subject)` | issue via the first enabled issuer |
| `IssuedCredential issueCredential(String issuerName, String subject)` | issue via a named issuer |
| `IssuedCredential issueCredential(String subject, Map<String,String> claims)` | issue with custom claims (reserved keys sub/mode/exp/iat/jti/adm unavailable; keys `[a-zA-Z0-9_-]{1,32}`, values ≤256 chars) |
| `IssuedCredential issueCredential(String issuerName, String subject, Map<String,String> claims)` | named issuer + claims |

## A.5 Group 4: ApiToolkitApi

| Method | Description |
| --- | --- |
| `String toJson(Object obj)` | object → JSON (reuses JsonWriter) |
| `String guessContentType(String path)` | extension → Content-Type (reuses MimeTypes) |
| `void registerMimeType(String ext, String contentType)` | register/override an extension's Content-Type (global, thread-safe; custom extensions like vue/ts/json5 must be registered first for browsers to render correctly) |
| `void sendLink(Player, String url, String display)` | send a clickable link message (display supports `%url%` / `%url_label%`; `&` stands for the `§` color code) |
| `void sendLink(Collection<? extends Player>, String url, String display)` | bulk send |
| `String apiPrefix()` | global API prefix (gateway api-prefix, default `/api`) |
| `String pluginsPrefix(String pluginName)` | plugin namespace prefix (e.g. `/plugins/MCER`); empty for the main plugin |
| `String pluginsPrefix(Plugin plugin)` | same, with the plugin main class instance |
| `String apiFullPrefix(String pluginName)` | `apiPrefix() + pluginsPrefix()` full path prefix (normal API registration, e.g. `/api/plugins/MCER`) |
| `String apiFullPrefix(Plugin plugin)` | same, with the plugin main class instance |
| `String pageFullPrefix(String pluginName)` | page full prefix = "/web" + pluginsPrefix() (e.g. `/web/plugins/MCER`); empty for the main plugin |
| `String pageFullPrefix(Plugin plugin)` | same, with the plugin main class instance |
| `String webResourcePrefix(String pluginName)` | Web minimalist page URL prefix (`/web/plugins/<plugin>/page`) |
| `String webResourcePrefix(Plugin plugin)` | same, with the plugin main class instance |
| `String serverPrefix()` | proxy-server prefix (e.g. `/server/lobby`); empty on standalone |
| `String fullPathPrefix(String pluginName)` | full 3-segment = `serverPrefix() + apiFullPrefix()` (e.g. `/server/lobby/api/plugins/Foo`) |
| `String scheme()` | transport scheme (`https` when TLS enabled, else `http`; same source as the `__SOYS_CONTEXT__.js` contract's scheme) |
| `String host()` | server public address (`mc.public-host` → `mc.host` → server-ip → `localhost` fallback) |
| `int port()` | server public port (`mc.public-port` → `mc.port` → server-port → `25565` fallback) |
| `boolean spaFallback(String pluginName)` | whether the plugin declared SPA fallback (drives history/hash mode; false when unset/registry unavailable) |
| `String pageBase(String pluginName)` | vue-router base: `/` for the main plugin; `pageFullPrefix + "/"` for add-ons (same source as contract pageBase) |
| `boolean fpEnabled()` | device-fingerprint two-factor switch (auth.yml `auto.login.fp.enabled`; false without a login bridge) |
| `boolean fpStrict()` | device-fingerprint strict mode (auth.yml `auto.login.fp.strict`; default true without a login bridge) |

## A.6 Group 5: HttpClientApi

| Method | Description |
| --- | --- |
| `HttpResponse sendHttp(String method, String url, Map<String,String> headers, byte[] body)` | real outbound request (throws `HttpClientException` on failure) |
| `HttpResponse sendGet(String url)` / `sendGet(String url, Map headers)` | GET |
| `HttpResponse sendPost(String url, byte[] body)` / `sendPost(String url, Map headers, byte[] body)` | POST |
| `Object callLocalApi(String method, String path, Map headers, byte[] body)` | local loopback (bypasses the network, equivalent to local dispatch) |
| `Object sendApi(String method, String logicalPath, Map headers, byte[] body)` | resolveUrl-prefixed local dispatch; annotation-level permissions still apply; null on no route |
| `String resolveUrl(String logicalPath)` | adds /api + /server/<thisServer> (proxy) + /plugins/<plugin> (third-party) |
| `String getApiPrefix()` | the annotation API global prefix |
| `boolean isAuthEnabled()` | whether gateway auth is enabled |

## A.7 Group 6: ExtensionApi

| Method | Description |
| --- | --- |
| `void registerLoginProvider(LoginProvider provider)` | register a login plugin provider (call after detecting the corresponding plugin loaded) |
| `void registerSubCommand(SubCommand subCommand)` | register a `/soyshttp` subcommand (after the host onEnable finishes) |
| `void registerWebInterceptor(WebInterceptor interceptor)` | request-level interceptor (after gateway policies, before business routing; may rewrite/short-circuit) |
| `void registerPolicy(SecurityPolicy policy)` | inject a custom security policy (ordered; DENY short-circuits; survives reload) |

## A.8 Event API (see Chapter 7)

All live in `com.github.cocosoys.mc.soyshttpovermc.api.event`; events are **nested classes under abstract bases**:

- `SoysReadyEvent` (`getApi()`), `HttpConfigReloadEvent` — top-level classes;
- Under `ApiEvent`: `ApiRegisteredEvent` / `ApiUnregisteredEvent` (`getApis()` → `List<ApiInfo>`),
  `ApiAccessEvent` (`getPlayerName()`/`getPlayer()`/`getAsyncPlayer()`/`getRequestParams()`/`getBody()`; subclasses
  `ApiGetEvent`/`ApiPostEvent`/`ApiPutEvent`/`ApiDeleteEvent`/`ApiPatchEvent`/`ApiOtherEvent`),
  `ApiAccessCompletedEvent` (adds `getResponseBody()`/`getStatusCode()`/`getReason()`/`getHeaders()`; subclasses
  `ApiGetCompletedEvent` etc.) — registration/unregistration are synchronous; access/completed are dispatched back to the main thread by the gateway;
- Under `GatewayEvent`: `GatewayRequestEvent` / `GatewayRequestServedEvent` (`getStatusCode()`/`getLatencyMs()`),
  `GatewayAccessDeniedEvent` (`getStatusCode()`/`getPolicyName()`), `GatewayCredentialIssuedEvent`,
  `GatewayLoginResultEvent` (`getPlayer()`/`isSuccess()`/`getReason()`/`getIp()`);
- Under `WebResourcesEvent`: `WebResourcesAccessEvent` (`getPath()`/`isHtml()`/`isJs()`/`getResult()`, can redirect/block/replace),
  `WebResourcesLoadedEvent` (`getResources()` → `List<WebResourceAccess>`).

## A.9 Configuration Index (real paths)

| File | Key nodes |
| --- | --- |
| `config.yml` | `upload` / `mc.public-host` / `mc.public-port` / `mc.trust-proxy` / `proxy.server-name` / `proxy.proxy-address` / `sniffer` / `http-backend.mode` / `log.level` / `permission.providers` / `permission.offline-fallback` / `storage.*` / `auto.ops.*` |
| `pages.yml` | `web.root` / `web.home` / `web.cache.*` / `web.large-file-*` / `pages.page` / `pages.auto` / `pages.alias` / `permissions` |
| `language.yml` | `current` / `rule` / `sources` |
| `EULA.yml` | `eula` (true to accept) |
| `gateway/config.yml` | `enabled` / `api-prefix` / `debug-events` |
| `gateway/https.yml` | `enabled` / `keystore` / `cert` / `key` / `enabled-protocols` / `min-tls` |
| `gateway/policies/tls.yml` | `enabled` / `host` |
| `gateway/policies/ip-allowlist.yml` | `default` / `list` / `trust-proxy` |
| `gateway/policies/auth.yml` | `enabled` / `header` / `login-provider` / `api-key.local-fallback-all` / `paths` / `exempt` / `accept.*` / `auto.login.ttl.*` / `auto.login.ip.enabled` / `auto.login.fp.*` / `auto.login.ticket.*` (legacy `keys` removed; use the local `soys_api_key` table) |
| `gateway/policies/rate-limit.yml` | `scope` / `rpm` / `burst` |
| `gateway/policies/access-limiter.yml` | `path-patterns` (name/scope/limit/window-seconds) |
| `gateway/issuers/session-token.yml` | `enabled` / `cookie-name` / `ttl-seconds` / `clock-skew-seconds` |

## A.10 Command Quick Reference

```bash
/soyshttp eula | status | report | reload | help [subcommand|page]
/soyshttp key <subject>
/soyshttp send <url|/page> [display text] [player]
/soyshttp pages [all] [page]
/soyshttp api [pluginName]
/soyshttp tokens
/soyshttp lang [code]            # lang sources on|off|download|update|remove|info <index>
/soyshttp log [level]            # OFF|ERROR|WARN|INFO|DEBUG|TRACE
/soyshttp perm group|user|check|reload ...
/soyshttp apikey                 # X-API-Key local table management (generate/enable-disable/expire/bind/permissions)
/soyshttp migrate <backend> <backend> [confirm]   # ORM backend migration (merge semantics)
/soyshttp sync [<from> <to> [confirm]]            # backend overwrite sync (no-arg = primary → all secondary)
/soyshttp data <plugin> status|update [version]|reinstall|uninstall   # data-layer auto ops
```

Short alias `/shttp`; master permission `soyshttp.admin` (OP by default).
