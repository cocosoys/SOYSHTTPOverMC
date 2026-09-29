# Chapter 7 Event System (Usage & Registration)

SOYSHTTPOverMC broadcasts key lifecycle and gateway events through Bukkit events. Third-party plugins simply `registerEvents` a listener.

## 7.1 Event Summary

Events are organized as **nested classes under abstract bases** (package `com.github.cocosoys.mc.soyshttpovermc.api.event`); import the concrete class you need:

| Event (nested path) | When | Thread | Payload |
| --- | --- | --- | --- |
| `SoysReadyEvent` (top-level) | after the main plugin's `onEnable` completes | synchronous (main thread) | facade API instance |
| `HttpConfigReloadEvent` (top-level) | broadcast after `/soyshttp reload` completes | synchronous | none |
| `GatewayEvent.GatewayRequestEvent` | request entered, before policy evaluation | async (sniffer thread pool) | method/path/IP/headers |
| `GatewayEvent.GatewayRequestServedEvent` | request processing completed | async | status code + latency |
| `GatewayEvent.GatewayAccessDeniedEvent` | rejected by the policy chain (401/403/426/429/500) | async | status code + policy name |
| `GatewayEvent.GatewayCredentialIssuedEvent` | after `/soyshttp key` or a login plugin issues a credential | synchronous | credential object |
| `GatewayEvent.GatewayLoginResultEvent` | player login-bridge verdict finished | synchronous | player / success / reason / IP |
| `ApiEvent.ApiRegisteredEvent` | API endpoint registered | synchronous | `List<ApiInfo>` |
| `ApiEvent.ApiUnregisteredEvent` | API endpoint unregistered | synchronous | `List<ApiInfo>` |
| `ApiEvent.ApiAccessEvent` | route matched and permission passed, before the handler runs | worker → dispatched on the main thread | request context + player name/entity + request params + body |
| `ApiEvent.ApiAccessCompletedEvent` | API request finished (including denied) | worker → dispatched on the main thread | above + response body/status code/reason/headers |
| `WebResourcesEvent.WebResourcesAccessEvent` | client requests a web resource | async | path / is-html / is-js / result (redirectable) |
| `WebResourcesEvent.WebResourcesLoadedEvent` | all web resources loaded | async | `List<WebResourceAccess>` |

Per-request-type API access events (extend `ApiEvent.ApiAccessEvent` / `ApiEvent.ApiAccessCompletedEvent`, **sharing the same HandlerList** as the base class):

- Before handling: `ApiGetEvent` / `ApiPostEvent` / `ApiPutEvent` / `ApiDeleteEvent` / `ApiPatchEvent` / `ApiOtherEvent`
- After handling: `ApiGetCompletedEvent` / `ApiPostCompletedEvent` / `ApiPutCompletedEvent` / `ApiDeleteCompletedEvent` / `ApiPatchCompletedEvent` / `ApiOtherCompletedEvent`

> Thread constraint (forced on 1.12.2): **credential-issuance / API registration / unregistration events fire synchronously on the main thread** — the gateway would throw `IllegalStateException` if it fired async events directly from a worker thread, so these events are designed synchronous; access/completed events are dispatched back to the main thread by the gateway.

## 7.2 SoysReadyEvent (Recommended First)

Solves the problem of third-party plugins loading **before** SOYS and not yet having the facade:

```java
public class MyPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onSoysReady(SoysReadyEvent e) {
        SoysHttpOverMcApi api = e.getApi();
        api.getApiRegistration().registerController(new MyApi());
        api.getWebPage().registerPage(this, "/hello", bytes, "text/html");
    }
}
```

The event fires once after SOYS `onEnable` finishes; if your plugin loads after SOYS is ready (`HttpOverMcPlugin.getInstance() != null`), use the facade directly — no event needed.

## 7.3 Gateway Events (Async)

```java
@EventHandler(ignoreCancelled = true)
public void onRequest(GatewayRequestEvent e) {
    if (e.getPath().startsWith("/api/console")) {
        // log sensitive API access
    }
}

@EventHandler
public void onServed(GatewayRequestServedEvent e) {
    if (e.getStatusCode() >= 500) {
        plugin.getLogger().warning("Request error: " + e.getPath() + " -> " + e.getStatusCode());
    }
}

@EventHandler
public void onDenied(GatewayAccessDeniedEvent e) {
    plugin.getLogger().info("Access denied: " + e.getIp() + " " + e.getPath()
            + " -> " + e.getStatusCode() + " (policy: " + e.getPolicyName() + ")");
}
```

`GatewayEvent` is the abstract base of all gateway events (request/served/denied/credential/login result); listen to it for unified handling:

```java
@EventHandler
public void onGateway(GatewayEvent e) { /* dispatch by instanceof */ }
```

Listen to `GatewayEvent.GatewayLoginResultEvent` for login-bridge verdicts (including failures):

```java
@EventHandler
public void onLoginResult(GatewayEvent.GatewayLoginResultEvent e) {
    if (!e.isSuccess()) {
        plugin.getLogger().info("Login failed: " + e.getPlayer() + " reason: " + e.getReason());
    }
}
```

## 7.4 API Access Events (Synchronous)

```java
@EventHandler
public void onApiAccess(ApiEvent.ApiAccessEvent e) {
    // e.getPlayerName()   player name (String, never dangling)
    // e.getPlayer()       player entity (null offline)
    // e.getPath() / e.getMethod()
    // e.getRequestParams()   request params (JSON / x-www-form-urlencoded parsed map)
    // e.getBody()            raw request body
    long start = System.currentTimeMillis();
    // Note: fires before the handler runs; use ApiAccessCompletedEvent for post-handling
}

@EventHandler
public void onGet(ApiEvent.ApiGetEvent e) { /* GET only */ }

@EventHandler
public void onApiCompleted(ApiEvent.ApiAccessCompletedEvent e) {
    // request finished (incl. denied): e.getResponseBody() / e.getStatusCode() / e.getReason() / e.getHeaders()
    if (e.getStatusCode() >= 500) {
        plugin.getLogger().warning("API error: " + e.getPath() + " -> " + e.getStatusCode());
    }
}
```

`ApiEvent.ApiAccessEvent` and its subclasses share one `HandlerList`: listening on the base class receives all types; listening on a subclass receives only that type (a parent listener does not stop subclass listeners from firing). The same holds for the completed events.

## 7.5 Registration / Unregistration Events (Synchronous)

```java
@EventHandler
public void onApiRegistered(ApiEvent.ApiRegisteredEvent e) {
    for (ApiInfo info : e.getApis()) {
        // info: method / path / apiName / permission / ownerClass / ownerPlugin
        registerToMyDiscovery(info);
    }
}

@EventHandler
public void onApiUnregistered(ApiEvent.ApiUnregisteredEvent e) {
    for (ApiInfo info : e.getApis()) {
        removeFromMyDiscovery(info);
    }
}
```

## 7.6 Web Resource Events (Async)

```java
@EventHandler
public void onWebAccess(WebResourcesEvent.WebResourcesAccessEvent e) {
    // e.getPath()   requested resource path (prefix included)
    // e.isHtml() / e.isJs()   whether it is an .html / .js resource
    // e.getResult() can redirect / replace the response
    if (e.getPath().startsWith("/web/plugins/MCER") && !e.getResult().isHandled()) {
        e.getResult().redirect("/web/plugins/MCER/index");
    }
}

@EventHandler
public void onWebLoaded(WebResourcesEvent.WebResourcesLoadedEvent e) {
    for (WebResourceAccess r : e.getResources()) {
        // all resources loaded (incl. pages.yml / language.yml registrations)
    }
}
```

## 7.7 Events & Reload

`/soyshttp reload` broadcasts `HttpConfigReloadEvent` when done (equivalent to registering a `ReloadHttpConfigHandler` hook); third-party plugins can refresh their own config here:

```java
@EventHandler
public void onReload(HttpConfigReloadEvent e) {
    myConfig.reload();
}
```

## 7.8 Version Notes

- All events behave identically on 1.12.2 and the other supported versions; the 1.6.4 / 1.7.10 sniffer thread pools also provide async gateway events;
- Do not do blocking work inside synchronous handlers (they would stall the main thread tick).
