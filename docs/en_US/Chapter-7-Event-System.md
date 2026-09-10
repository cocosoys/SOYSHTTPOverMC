# Chapter 7 Event System (Usage & Registration)

SOYSHTTPOverMC broadcasts key lifecycle and gateway events through Bukkit events. Third-party plugins simply `registerEvents` a listener.

## 7.1 Event Summary

| Event | When | Thread | Payload |
| --- | --- | --- | --- |
| `SoysReadyEvent` | after the main plugin's `onEnable` completes | synchronous (main thread) | facade API instance |
| `HttpConfigReloadEvent` | broadcast after `/soyshttp reload` completes (equivalent to a ReloadHttpConfigHandler) | synchronous | none |
| `GatewayRequestEvent` | request entered, before policy evaluation | async (sniffer thread pool) | method/path/IP/headers |
| `GatewayRequestServedEvent` | request processing completed | async | status code + duration |
| `GatewayAccessDeniedEvent` | rejected by the policy chain (401/403/426/429/500) | async | status code + policy name |
| `GatewayCredentialIssuedEvent` | after `/soyshttp key` or a login plugin issues a credential | synchronous | credential object |
| `ApiRegisteredEvent` | API endpoint registered | synchronous | ApiInfo |
| `ApiUnregisteredEvent` | API endpoint unregistered | synchronous | ApiInfo |
| `ApiAccessEvent` | route matched and permission passed, before the handler runs | worker → dispatched on the main thread | request context + player name/entity |
| `ApiAccessDeniedEvent` | insufficient permission (403) | synchronous | player name/permission/path |

Per-request-type API access events (extend `ApiAccessEvent`, **sharing the same HandlerList** as the base class):

- `ApiGetEvent` / `ApiPostEvent` / `ApiPutEvent` / `ApiDeleteEvent` / `ApiPatchEvent` / `ApiOtherEvent`

> Thread constraint (forced on 1.12.2): **credential-issuance / API registration / unregistration events fire synchronously on the main thread** — the gateway would throw `IllegalStateException` if it fired async events directly from a worker thread, so these events are designed synchronous.

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

`GatewayEvent` is the abstract base of all gateway events (request/served/denied/credential/API reg/unreg); listen to it for unified handling:

```java
@EventHandler
public void onGateway(GatewayEvent e) { /* dispatch by instanceof */ }
```

## 7.4 API Access Events (Synchronous)

```java
@EventHandler
public void onApiAccess(ApiAccessEvent e) {
    // e.getPlayerName() player name (String, never dangling)
    // e.getPlayer()    player entity (null offline)
    // e.getPath() / e.getMethod()
    long start = System.currentTimeMillis();
    // Note: fires before the handler runs; use GatewayRequestServedEvent for post-handling
}

@EventHandler
public void onGet(ApiGetEvent e) { /* GET only */ }

@EventHandler
public void onApiDenied(ApiAccessDeniedEvent e) {
    plugin.getLogger().info("Permission denied: " + e.getPlayerName() + " tried " + e.getPath());
}
```

`ApiAccessEvent` and its subclasses share one `HandlerList`: listening on the base class receives all types; listening on a subclass receives only that type (a parent listener does not stop subclass listeners from firing).

## 7.5 Registration / Unregistration Events (Synchronous)

```java
@EventHandler
public void onApiRegistered(ApiRegisteredEvent e) {
    ApiInfo info = e.getApiInfo();
    // info: method / path / apiName / permission / ownerClass / ownerPlugin
    registerToMyDiscovery(info);
}

@EventHandler
public void onApiUnregistered(ApiUnregisteredEvent e) {
    removeFromMyDiscovery(e.getApiInfo());
}
```

## 7.6 Events & Reload

`/soyshttp reload` broadcasts `HttpConfigReloadEvent` when done (equivalent to registering a `ReloadHttpConfigHandler` hook); third-party plugins can refresh their own config here:

```java
@EventHandler
public void onReload(HttpConfigReloadEvent e) {
    myConfig.reload();
}
```

## 7.7 Version Notes

- All events behave identically on 1.12.2 and the other supported versions; the 1.6.4 / 1.7.10 sniffer thread pools also provide async gateway events;
- Do not do blocking work inside synchronous handlers (they would stall the main thread tick).
