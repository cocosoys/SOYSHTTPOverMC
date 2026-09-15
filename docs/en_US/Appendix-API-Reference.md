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
| 4 Toolkit | `getToolkit()` | JSON, Content-Type, link messages |
| 5 HTTP client | `getHttpClient()` | outbound requests, local loopback, environment adaptation |
| 6 Extensions | `getExtension()` | login providers, subcommands, interceptors, custom policies |

Other facade capabilities: `registerReloadHook(Runnable)`, `serverPrefix()`, `apiPrefix()`, `getConfigSection(...)` etc.

## A.2 Group 1: ApiRegistrationApi

| Method | Description |
| --- | --- |
| `void registerController(Object controller)` | register a controller (non-main plugins automatically get `/plugins/<pluginName>`) |
| `void registerController(Object controller, Plugin owner)` | explicitly specify the owning plugin |
| `void registerController(Object controller, boolean force)` | force=true overwrites duplicate routes |
| `void registerProxyController(Object controller)` | register as proxy under the main plugin's name (no plugin prefix) |
| `void unregisterController(Object controller)` | unregister all endpoints of that controller |
| `void unregisterPluginControllers(String pluginName)` | unregister all endpoints of a plugin |
| `void setPermissionService(PermissionService service)` | plug in a custom permission-checking service |
| `List<ApiInfo> getRegisteredApis()` | snapshot of all endpoints |
| `String getApiPrefix()` | the global prefix (/api) |

`ApiInfo`: `method` / `path` / `apiName` / `permission` / `ownerClass` / `ownerPlugin`.

## A.3 Group 2: WebPageApi

| Method | Description |
| --- | --- |
| `registerPage(Plugin, String path, byte[] content)` | web page (Content-Type inferred from extension) |
| `registerPage(Plugin, String path, byte[] content, String contentType)` | web page (explicit Content-Type) |
| `registerPage(Plugin, String path, byte[] content, String contentType, boolean force)` | force overwrite |
| `registerPage(Plugin, String path, HttpMethod method, byte[] content, String contentType, boolean force, String desc, List<String> nicknames)` | non-GET static response + nicknames |
| `registerResource(Plugin, String path, ClassLoader, String resourcePath)` | jar resource (read on demand) |
| `registerResource(..., String contentType)` / `(..., boolean force)` | resource overloads |
| `registerProxyPage(...)` / `registerProxyResource(...)` | without the plugin-name prefix |
| `registerDirectory(Plugin, String basePath, File dir)` / `(..., boolean proxy)` | bulk disk directory |
| `registerResourceDirectory(Plugin, String basePath, ClassLoader, String resourceRoot)` / `(..., boolean proxy)` | bulk jar resource directory |
| `registerNetworkPage(Plugin, NetworkPage page)` | network page (custom loading/encrypted transport) |
| `registerLargeFileLoader(LargeFileLoader)` | custom large-file loader |
| `setDefaultLargeFileLoader(String name)` | switch the default large-file loader |
| `setLargeFileLoader(String pathPrefix, String name)` | per-path-prefix loader |
| `registerErrorPage(Plugin, int status, byte[] content)` | custom error page |
| `registerCors(Plugin, String pathPrefix, String origin, String methods, String headers, boolean credentials)` | CORS declaration |
| `unregisterPluginPages(String pluginName)` | unregister all pages of a plugin |

Also: `registerPage(..., List<String> permissions)` / `registerProxyPage(..., permissions)` accept AND-semantic permissions (see Chapter 3, 3.5).

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

`SoysReadyEvent` (`getApi()`), `HttpConfigReloadEvent`, `GatewayRequestEvent`, `GatewayRequestServedEvent` (`getStatusCode()`/`getDurationMs()`), `GatewayAccessDeniedEvent` (`getStatusCode()`/`getPolicyName()`), `GatewayCredentialIssuedEvent`, `ApiRegisteredEvent`/`ApiUnregisteredEvent` (`getApiInfo()`), `ApiAccessEvent` and its subclasses (`ApiGetEvent` etc., `getPlayerName()`/`getPlayer()`), `ApiAccessDeniedEvent`. All live in `com.github.cocosoys.mc.soyshttpovermc.api.event`; `GatewayEvent` is the abstract base.

## A.9 Configuration Index (real paths)

| File | Key nodes |
| --- | --- |
| `config.yml` | `upload` / `mc.public-host` / `mc.public-port` / `mc.trust-proxy` / `proxy.server-name` / `proxy.proxy-address` / `sniffer` / `http-backend.mode` / `log.level` / `permission.providers` / `permission.offline-fallback` / `storage.*` |
| `pages.yml` | `web.root` / `web.home` / `web.cache.*` / `web.large-file-*` / `pages.page` / `pages.auto` / `permissions` |
| `language.yml` | `current` / `rule` / `sources` |
| `EULA.yml` | `eula` (true to accept) |
| `gateway/config.yml` | `enabled` / `api-prefix` / `debug-events` |
| `gateway/https.yml` | `enabled` / `keystore` / `cert` / `key` / `enabled-protocols` / `min-tls` |
| `gateway/policies/tls.yml` | `enabled` / `host` |
| `gateway/policies/ip-allowlist.yml` | `default` / `list` / `trust-proxy` |
| `gateway/policies/auth.yml` | `enabled` / `header` / `login-provider` / `keys` / `paths` / `exempt` / `accept.*` / `auto.login.*` |
| `gateway/policies/rate-limit.yml` | `scope` / `rpm` / `burst` |
| `gateway/policies/access-limiter.yml` | `path-patterns` (name/scope/limit/window-seconds) |
| `gateway/issuers/session-token.yml` | `enabled` / `cookie-name` / `ttl-seconds` / `clock-skew-seconds` |

## A.10 Command Quick Reference

```
/soyshttp reload
/soyshttp key <subject>
/soyshttp send <url|/page> [display text] [player]
/soyshttp pages [all]
/soyshttp api
/soyshttp tokens
/soyshttp lang [code]            # lang sources on|off|download|update|remove|info <index>
/soyshttp perm group|user|check|reload ...
/soyshttp log-level <OFF|ERROR|WARN|INFO|DEBUG|TRACE>
/soyshttp migrate <from> <to>
/soyshttp sync
/soyshttp status | report | eula | help
```

Short alias `/shttp`; master permission `soyshttp.admin` (OP by default).
