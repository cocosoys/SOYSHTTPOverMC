# Chapter 4 Authentication & Security Management

The security model of SOYSHTTPOverMC is carried by the **gateway (GatewayFilter)**: every incoming HTTP(S) request passes through a **policy chain** (TLS enforcement → IP allowlist → rate limit → unified auth → access limiter); any policy returning DENY short-circuits the request. All policies are configured via YAML under `gateway/` and hot-reloaded with `/soyshttp reload`.

## 4.1 Gateway Directory Layout

```
gateway/
├── config.yml            # enabled master switch / api-prefix / debug-events
├── https.yml             # TLS certificate sources & protocols
├── policies/
│   ├── tls.yml           # order 5: plain HTTP → 426 Upgrade Required
│   ├── ip-allowlist.yml  # order 10: IP allow/deny list (exact IP + CIDR)
│   ├── auth.yml          # order 20: unified credential auth
│   ├── rate-limit.yml    # order 30: token-bucket rate limiting
│   └── access-limiter.yml# order 31: window access limiter
└── issuers/
    └── session-token.yml # session token issuer (JWT)
```

## 4.2 Policy Details

### 4.2.1 tls.yml (HTTPS enforcement)

```yaml
enabled: true
host: ""        # hostname used in the 426 Location (falls back to https.yml host)
```

Plain HTTP → `426 Upgrade Required`, Location pointing to the same-port `https://`.

### 4.2.2 ip-allowlist.yml

```yaml
enabled: false
default: allow        # allow = the list acts as a blacklist (hits are denied); deny = whitelist (only listed IPs pass)
list: []              # e.g. ["127.0.0.1", "192.168.1.0/24"]
trust-proxy: false    # true = prefer the first IP in X-Forwarded-For (behind a trusted proxy)
```

### 4.2.3 rate-limit.yml (token bucket)

```yaml
enabled: false
scope: ip             # ip | key (per IP or per API key)
rpm: 60               # tokens replenished per minute
burst: 10             # burst cap
```

Over limit → `429 + Retry-After`.

### 4.2.4 access-limiter.yml (window limiter)

```yaml
enabled: false
path-patterns:
  - name: "ip access limit"
    description: "Each IP may visit web pages 50 times and APIs 200 times per window; reset hourly"
    scope: ip         # ip | key | path
    limit: 50         # max visits per window
    window-seconds: 3600
```

Passive refresh: no background task; the window is checked only when a request arrives.

### 4.2.5 auth.yml (unified credential auth, order 20)

```yaml
enabled: true
header: X-API-Key            # API key request header name
login-provider: ""           # login plugin provider name (e.g. authme; empty = auto-pick the first available)
api-key:
  local-fallback-all: false  # when the local soys_api_key table is unavailable:
                             #   true = downgrade to "all permissions" mode (warn); false (default) = deny
paths: [/api/*]              # empty = protect all paths; * = everything; /api/* = prefix match
exempt: [/ping, /whoami, /auth/login, /auth/issue, /auth/mode, /auth/status, /homepage/config, /homepage/live]
accept:
  header: true               # accept X-API-Key header
  bearer: true               # accept Authorization: Bearer <key>
  basic: true                # accept Authorization: Basic (username = key)
  cookie: true               # accept Cookie (values validated by enabled issuers)
```

> The legacy `keys: []` static source is **removed**: credentials now live in the local table `soys_api_key` (hashed, managed via `/soyshttp apikey`, see 4.6.3). Credential matching: any record in the local table or any enabled issuer passes; none match → 401. When both a valid X-API-Key and a player Bearer are present, a failed key check continues down the player chain (OR logic).

### 4.2.6 https.yml (TLS)

```yaml
enabled: true
host: ""
cert: ""          # PEM certificate path (optional)
key: ""           # PEM private key path (optional, PKCS8)
keystore: ""      # PKCS12 keystore path (optional; takes priority over cert/key)
keystore-pass: ""
key-pass: ""
enabled-protocols: []   # empty = enable TLSv1.0~1.3 (unsupported ones are skipped by the JVM automatically)
min-tls: TLSv1.1
```

Certificate source priority: `keystore(PKCS12) > cert+key(PEM, private key must be PKCS8) > empty = automatic keytool self-signed (fully offline)`. Public browsers do not trust self-signed certs — a known trade-off; API clients can use `-k` or pin the certificate.

## 4.3 Credential System

### 4.3.1 Credential Sources (three)

1. **X-API-Key header** (default name `X-API-Key`): matches the local API key table `soys_api_key` (hashed; `/soyshttp apikey` manages it, see 4.6.3);
2. **Authorization**: `Bearer <key>` (matches the local table) or `Basic` (username = key);
3. **Cookie**: request cookies are validated by enabled issuers under `gateway/issuers/` (e.g. `soys_session`).

### 4.3.2 session-token.yml (session token issuer)

```yaml
enabled: true
cookie-name: soys_session
ttl-seconds: 86400
clock-skew-seconds: 30    # cross-server clock tolerance (validates exp/iat)
```

- When enabled, `/soyshttp key <subject>` issues session tokens; the same token works as X-API-Key / Bearer / Cookie;
- Tokens are **in-memory**: all invalidated on server restart;
- Session tokens carrying the `adm` marker (manually issued by the admin) are treated as highest privilege, bypassing permission checks for all APIs;
- JWT secret: read from shared storage (MySQL, unified across servers) when available, otherwise falls back to the local `token-secret.key` file in the plugin data folder (auto-generated; cross-server verification may differ — see the config.yml log).

## 4.4 Web Login & Login Plugin Integration

### 4.4.1 LoginProvider SPI

The core defines the login plugin provider SPI (`web/gateway/policy/auth/bridge/spi/LoginProvider`); login plugins register through `LoginProviderFactory`. The built-in `AuthMeLoginProvider` registers automatically when AuthMe is detected. `login-provider` in `gateway/policies/auth.yml` can pin a provider; empty auto-selects the first available.

- **With a login plugin**: the web login window `/api/auth/login` validates passwords (AuthMe) and issues a session token for the player;
- **Without a login plugin**: web login falls into **password-free mode** — entering a username alone issues a token (token permissions are bound by `PlayerPermissionService`: whatever the player has in-game, the token has).

### 4.4.2 Auto-Login (auth.yml auto.login.*)

```yaml
auto:
  login:
    ttl:
      enable: true      # remember-me (device auto-login) master switch, default true
      activetime: 7     # remember-me credential validity (days), default 7
    ip:
      enabled: false    # legacy "IP-match auto-login" switch, default off
    fp:
      enable: true      # device-fingerprint two-factor (default on): records the browser fingerprint into soys_device_binding on login
      strict: true      # strict=true: a fingerprint mismatch (different browser/environment) also denies an already-logged-in session
    ticket:
      ttl: 180          # in-game → web binding ticket validity (seconds)
      in-game-link: true  # when on, /soyshttp link in-game issues a one-time ticket; entering it on the web completes the device binding
```

- **Remember me**: on login, when the front-end "remember me" is checked, a long-lived device credential cookie (`soys_remember`, HttpOnly) is issued; subsequent `/api/auth/status` calls auto-login;
- **Device-fingerprint two-factor (default on)**: after a successful login the browser fingerprint (UA + Canvas + WebGL digest) is written to the `soys_device_binding` table (bound to the uuid); later access from a **different-fingerprint** device is denied when `fp.strict=true`, or only warned when `fp.strict=false`. This solves the "IP cannot be pinned to a single device" problem — fingerprints distinguish devices behind the same NAT;
- **Ticket binding (in-game-link)**: `/soyshttp link` in-game issues a one-time ticket (a `soys_remember` Ticket-type credential); entering it on the web binds that browser to the player's device without a password;
- **IP-match auto-login** (default off): legacy behavior auto-logs in when "player online + web IP == game IP"; IPs cannot be pinned to a single device and cause collateral damage behind NAT, so it defaults to off (only recommended for LAN / single-machine setups).

### 4.4.3 Login Window Endpoints (/api/auth/*)

| Endpoint | Description |
| --- | --- |
| `/api/auth/login` | login window login endpoint (no credential before login; exempt from auth by default) |
| `/api/auth/issue` | ticket / password-free login submission |
| `/api/auth/mode` | login-page mode probe (password mode / password-free mode) |
| `/api/auth/status` | login status check (called when not logged in; supports remember-me auto-login) |

`/auth/logout` and `/auth/me` still require a credential.

## 4.5 Permission Checks (Combined Permission Service)

### 4.5.1 Check Order

`CombinedPermissionService` (extends `PlayerPermissionService`):

1. Bukkit native permission (online) + offline OP check (true → pass immediately, a performance optimization);
2. Iterate all configured permission-plugin providers in `ProviderRegistry`: online players get `hasPermission(player, perm)`, offline players get `hasOfflinePermission(playerName, perm)`; any true → pass;
3. All false → deny (403).

### 4.5.2 config.yml Permission Config

```yaml
permission:
  providers: []            # empty = all installed permission plugins join automatically; explicit list = use only the list
  offline-fallback: op-only   # offline player fallback: op-only / local / false
```

Supported plugins: `luckperms` (recommended, offline queries), `permsex` (offline queries), `essentials`, `essentialx`, `local` (built-in local table; online & offline).

### 4.5.3 Offline Fallback Policies

| Value | Behavior |
| --- | --- |
| `op-only` (default) | only OP players pass (reads ops.json) |
| `local` | checks the plugin's built-in local permission table (data/soys_perm_*.yml or SQL tables) |
| `false` | all offline players denied (strictest) |

## 4.6 Local Permission Table (/soyshttp perm)

Pairs with `offline-fallback: local` (or `providers: ["local"]`); the plugin maintains built-in user/group/permission tables:

```bash
/soyshttp perm group create <id> [weight] [display]      # create group (higher weight = higher priority)
/soyshttp perm group delete <id>                         # delete group (cascades permissions & member refs)
/soyshttp perm group weight <id> <weight>                # adjust weight
/soyshttp perm group add|remove <id> <permission>        # add/remove group permission
/soyshttp perm group list                                # list groups
/soyshttp perm user <player> group add|remove <group>    # join/leave group
/soyshttp perm user <player> add|remove <permission>     # direct user permission
/soyshttp perm user <player> list                        # view effective permissions (incl. group inheritance)
/soyshttp perm user <player> expiry <yyyy-MM-dd HH:mm:ss|clear>  # user-level expiry
/soyshttp perm check <player> <permission>               # debug: check result
/soyshttp perm reload                                    # reload the provider chain
```

Rules (real implementation):

- **Node matching**: `:` equals `.` (`test:ping` ≡ `test.ping`); `-` prefix is negative (stripped on write, stored as `permission + negative=true`); `*` matches everything; `a.*` matches the segment;
- **No group inheritance** (phase 1 is flat: groups only hold permissions);
- **Expiry granularity**: user-level expiry only (yyyy-MM-dd HH:mm:ss; empty = forever); expired users are denied;
- **Storage**: `data/soys_perm_group.yml` + `data/soys_perm_user.yml` (or SQL tables when an SQL backend is enabled);
- **User primary key**: `uuid` (auto-recorded on first permission check while online; after renames, uuid matching takes priority, name matching only when uuid is absent);
- **Caching**: each check queries ORM directly (no extra cache layer).

### 4.6.3 API Key Table (/soyshttp apikey)

Local API keys live in the `soys_api_key` table (YAML / SQL dual backend, hashed — never plaintext), letting programmatic callers reach the gateway via the `X-API-Key` header (replacing the legacy static `keys` in auth.yml):

```bash
/soyshttp apikey generate <note>              # generate a key (platform-random; shown only once)
/soyshttp apikey list                         # list (masked; full keys never displayed)
/soyshttp apikey enable|disable <id>          # enable/disable
/soyshttp apikey expire <id> <yyyy-MM-dd HH:mm:ss|clear>
/soyshttp apikey bind <id> <uuid|player>      # bind a player (future checks run against the bound subject)
/soyshttp apikey remove <id>
```

- Permission checks on a matched key use the `soys_api_key` permission nodes (unified string matching, `:` ≡ `.`, `-` negation and `*` wildcards supported — reusing `soys_perm_permission` semantics);
- When the local table is unavailable, auth.yml `api-key.local-fallback-all` decides between downgrading to "all permissions" (warn) and denying (default); the same rule applies when the `local` backend itself is unavailable.

## 4.7 Custom Security Policies (ExtensionApi)

Third-party plugins can implement `SecurityPolicy` to inject into the gateway policy chain (outside gateway/policies/), participate by order, short-circuit on DENY, fail closed on exceptions, and survive `/soyshttp reload`:

```java
api.getExtension().registerPolicy(new SecurityPolicy() {
    public int order() { return 15; }                       // between ip-allowlist and auth
    public PolicyResult check(GatewayContext ctx) { ... }   // ALLOW / DENY
});
```

## 4.8 Facade Group 3: AuthCredentialApi

`api.getAuthCredential()`:

| Method | Description |
| --- | --- |
| `registerCredentialIssuer(String name, Supplier<CredentialIssuer> factory)` | register a credential issuer factory (login plugin integration point) |
| `isAuthEnabled()` | whether the auth policy is enabled |
| `getIssuerNames()` | names of enabled issuers |
| `issueCredential(subject)` / `issueCredential(issuerName, subject)` | issue a credential for an authenticated subject |
| `issueCredential(subject, claims)` / `issueCredential(issuerName, subject, claims)` | issue a credential with custom claims (keys `[a-zA-Z0-9_-]{1,32}`, values ≤256 chars; reserved keys `sub/mode/exp/iat/jti/adm` unavailable) |

```java
IssuedCredential cred = api.getAuthCredential().issueCredential("Steve");
// cred.token() / cred.expiresAt() ...
```

## 4.9 Version Notes

- TLS depends on the runtime JDK: on 1.6.4 running under JDK7, TLS is capped at TLSv1.1/1.2, and old clients supporting only TLSv1.0 may fail the handshake;
- On proxy networks, enable MySQL shared storage (`storage.cross-server: true`) so all backend servers verify JWTs with the same secret.
