# Chapter 5 Data Storage & ORM

SOYSHTTPOverMC persistence goes through **ORM** uniformly (entity annotations + condition chain, one API across YAML/SQL backends): system-level cross-server data (blacklist / audit / heartbeat / global secret) is also an ORM entity `SoysRecord` (table `soys_records`), routed through the `DATA` facade.

## 5.1 Storage Architecture (ORM Multi-Backend Routing)

All entity data is routed through the `DATA` facade: `storage.backends.mysql/sqlite/yaml` can be **enabled simultaneously**;
the unique primary storage (priority `MYSQL > SQLITE > YAML`) serves default reads/writes. Other enabled backends remain reachable —
every `DATA` method has a backend-typed overload (e.g. `DATA.get(StorageType.SQLITE, User.class, id)`) for explicit per-backend I/O,
identical to default operations except for the extra backend-type parameter.

### 5.1.1 Routing Rules

- `DATA.sqlEnabled()`: `storage.backends.mysql/sqlite` assembled (mysql preferred) → default reads/writes go to SQL;
- Otherwise default reads/writes fall back to `data/<table>.yml` (YAML backend, zero-dependency);
- If a backend is unavailable (e.g. SQL init failed) it falls back to YAML automatically; when both are unavailable the related API throws `IllegalStateException` (not triggered in normal configuration);
- Tables are created / column-patched automatically by the ORM (SQL `CREATE TABLE IF NOT EXISTS` + tolerant `ALTER`; YAML lazily generated);
- With multiple backends enabled, non-primary backends do not participate in default I/O — access them via the `StorageType` overloads only.

### 5.1.2 config.yml Storage Configuration

```yaml
storage:
  backends:
    yaml:
      enabled: true
      file: data/              # folder holding all yml tables (soys_records.yml + per-ORM-table .yml)
      backup-on-save: false
    sqlite:
      enabled: false
      file: data/records.db
    mysql:
      enabled: false
      url: 'jdbc:mysql://localhost:3306/minecraft?useUnicode=true&characterEncoding=utf8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai'
      username: root
      password: ''
  cross-server: false      # cross-server sync: all instances point at the same MySQL, data visible across servers
```

- Cross-server prerequisites: every instance sets `storage.backends.mysql.enabled: true` pointing at the same database (the SQL backend is the shared data source);
  enabling `storage.cross-server: true` while SQL is off logs a warning at startup (multi-instance data is not shared).

### 5.1.3 Data Plane (SoysRecord Entity)

Cross-server sync data (token blacklist / issuance audit / instance heartbeat / global JWT secret) lives in the ORM entity
`SoysRecord` (table `soys_records`), routed through the `DATA` facade; the semantic layer is `RecordSyncStorage` (the `SyncStorage` interface).
Key conventions: `blacklist:<jti>` / `audit:<jti>:<nonce>` / `instance:<serverId>` / `meta:jwt_secret`.
Audit fields `create_time / update_time` are auto-filled by the ORM write path (business time uses `updated_at`).

### 5.1.4 Backend Migration / Overwrite Sync

```text
/soyshttp migrate <backend> <backend> [confirm]   # migrate all registered tables between two backends (merge: upsert by key, no clearing)
/soyshttp sync [<from> <to> [confirm]]            # overwrite semantics (clear-then-write; SQL side runs in one transaction, rollback on failure)
                                                  #   no-arg = primary storage → all secondary backends (preview, then confirm)
                                                  #   with args = targeted overwrite (requires confirm)
```

> Writes land on a single DATA backend (the default primary). `migrate` / `sync` are explicit ops channels, not part of auto routing.

## 5.2 ORM: Entity Annotations

ORM uses the `com.dlz.db.annotation` annotations to mark entities:

| Annotation | Target | Description |
| --- | --- | --- |
| `@TableName("table")` | class | table name (default: class name lowercased with underscores) |
| `@TableId(value, type)` | field | primary key; `type = IdType.SEQ` (auto-increment) / others (see IdType) |
| `@TableField(value, exist, select)` | field | column mapping; `exist=false` ignores the field; `select=false` excludes it from queries |

```java
@TableName("user")
public class User {
    @TableId(type = IdType.SEQ)
    private Long id;
    @TableField("name")
    private String name;
    private String role;      // column = role
    // getters / setters ...
}
```

> Entity fields must have getters/setters (ORM reads/writes through them); column names are identical on the YAML and SQL sides (lowercase with underscores).

## 5.3 Dual-Backend Facades: YAML.Pojo / SQL.Pojo

Business code should **prefer the unified `DATA` facade** (auto-routes the default primary backend; use the
`StorageType` overloads such as `DATA.select(StorageType.SQLITE, User.class)` for a fixed backend).
`YAML.Pojo` / `SQL.Pojo` are the isomorphic low-level facades for advanced use. Both share **one API surface**:

```java
import com.github.cocosoys.mc.soyshttpovermc.orm.DATA;
import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.soyshttpovermc.orm.SQL;

// —— unified facade (auto-routes the default primary) ——
List<User> all = DATA.select(User.class);
List<User> admins = DATA.select(User.class, q -> q.eq(User::getRole, "admin"));
User u = DATA.get(User.class, 1L);
DATA.insert(user);
DATA.updateById(user);                        // update by PK (upsert on the YAML side)
DATA.deleteById(User.class, 1L);
// per-backend: DATA.get(StorageType.SQLITE, User.class, 1L) / DATA.select(StorageType.MYSQL, ...)

// —— low-level facades (fixed backend) ——
List<User> yamlAll = YAML.Pojo.select(User.class);
List<User> sqlAdmins = SQL.Pojo.select(User.class, q -> q.eq(User::getRole, "admin"));
```

- `YAML.Pojo.init(dataDir)` is assembled by core (`config.yml storage.backends.yaml.file`);
- `SQL.Pojo` draws its datasource from `storage.backends.{mysql,sqlite}` (mysql preferred); tables are created automatically from entities; when not wired up, methods return empty / false;
- YAML data files: `data/<tableName>.yml`, root node = table name, key = primary key value.

## 5.4 Condition Chain (Query)

`Query<T>` is shaped like MyBatis-Plus's LambdaQueryWrapper: conditions reference fields via **Lambda references** (refactor-safe) and accumulate into a `ConditionTree` (resolved uniformly by both backends):

```java
YAML.Pojo.selectW(User.class)
        .eq(User::getRole, "admin")
        .like(User::getName, "a")
        .orderByDesc(User::getCreateTime)
        .queryBeanList();                    // execute → List

// grouped conditions
YAML.Pojo.select(User.class, q -> q
        .eq(User::getOnline, true)
        .ands(g -> g.eq(User::getRole, "vip").eq(User::getVipLevel, 3))
        .ors(g -> g.eq(User::getRole, "admin").eq(User::getRole, "mod")));
```

| Condition method | SQL semantics |
| --- | --- |
| `eq / ne` | = / <> |
| `gt / ge / lt / le` | > / >= / < / <= |
| `like` | LIKE %val% |
| `in(column, values...)` | IN |
| `isNull / isNotNull` | IS NULL / IS NOT NULL |
| `or(column, op, value)` | OR with the previous condition |
| `ands(g)` / `ors(g)` | AND group / OR group |
| `orderByAsc / orderByDesc` | sorting |
| `page(Page)` | pagination |

Executors: `queryBeanList()` (List), `queryBeanPage()` (Page), `tree()` (condition tree).

## 5.5 Cross-Backend Search (Fuzzy Search)

Both backends implement keyword fuzzy search (the interface default throws `UnsupportedOperationException`, but the YAML/SQL executors both override it):

```java
// YAML side: full scan + LIKE (case-insensitive)
List<User> hits = YAML.Pojo.search(User.class, "steve", "name");          // search the name field only
List<User> hits2 = YAML.Pojo.search(User.class, "vip", "name", "role");   // any field matches

// SQL side: LIKE queries
if (SQL.Pojo.isAvailable()) {
    List<User> sqlHits = SQL.Pojo.search(User.class, "steve", "name");
}

// paged variants
Page<User> page = YAML.Pojo.searchPage(User.class, 1, 10, "steve", "name");
```

## 5.6 Raw File View (YAML only)

```java
ConfigSection raw = YAML.Pojo.get(User.class);   // raw view of the entity's file
raw.set("extra", "value");                       // free-form read/write
YAML.Pojo.save(User.class);                      // explicit flush to disk (atomic write)
```

## 5.7 Cross-Server Sync

- `storage.cross-server: true` + MySQL (all instances point at the same database): token blacklist / audit / heartbeat / global secret (entity table `soys_records`) are visible across servers;
- The plugin reports an async heartbeat every 30 seconds (serverId + server name + host + port); serverId rule: proxy network = `proxy.server-name`, standalone = `standalone-<host>:<port>`;
- The global JWT secret is distributed from shared storage, keeping cross-server verification consistent;
- Note: enabling cross-server while SQL is off logs a warning at startup (YAML is a single-machine file and cannot be shared across instances).

## 5.8 Data-Layer Auto Ops (Auto Ops / DataRegistrationApi)

> Lifts "manual ops initialization" (copying default data files, running init.sql, seeding data, schema upgrades)
> to **fully automatic**. The main plugin and `SoysExpansion` add-ons share one mechanism, governed by `auto.ops.*` in config.yml.

### 5.8.1 Config Switches

```yaml
auto:
  ops:
    enabled: true    # master switch (false = everything skipped)
    init: true       # auto init (default file copy + init.sql + seed + first-install migration)
    update: true     # auto update (versioned migration for existing installs)
    fail: disable    # disable (default) = disable only the failing plugin (main plugin fails → disable SOYS;
                     #   add-on fails → disable that add-on only, no collateral)
                     # warn = skip and warn, keep running (some features may be missing)
```

### 5.8.2 Meta Version Table (soys_schema_meta)

- Storage: SQL table `soys_schema_meta` or YAML file `data/soys_schema_meta.yml` (file name = table name);
- Granularity: **one row per table** (`plugin` + `tableName` logical composite key; `tableName = "*"` for plugin-level
  migration records with schemaVersion + executed-scripts JSON);
- Purpose: automatic detection on uninstall / reinstall / update — after removing the jar and reinstalling, meta persists
  → "keep-data update" path, data is not lost; only an explicit purge returns to a fresh install.

### 5.8.3 Migration Script Convention (schemaVersion + V{n})

- The plugin declares `schemaVersion()` (Expansion hook; default 0 = init.sql + seed only, no migrations);
- Script locations (per-backend directories, no mixing): `sql/migrations/V<n>/<table>.sql` (MySQL dialect) and
  `data/migrations/V<n>/<table>.yml` (YAML backend, declarative field backfill); `V<n>` increments from 1;
  YAML and SQL migrations **share the same version number** (one `V<n>` may carry both channel files);
- Execution rules: **fresh installs skip migrations** — data/*.yml and init.sql already hold the latest full structure, so meta is marked at the highest version right after init; **existing (old) installs run V(meta+1)..V(schemaVersion) incrementally**; no meta but old data detected also takes the upgrade path; **meta is written after each successful script** (a mid-run failure does not re-run successful items);
- Upgrade path: raise `schemaVersion()` (e.g. 1→2) and add the new `V2` files; restart or
  `/soyshttp data <plugin> update` applies them incrementally.

YAML migration script format (`data/migrations/V<n>/<table>.yml`):

```yaml
# add missing-field defaults to existing records
# target table = file name (<table>.yml); no table field needed (must match the file name if declared)
add-fields:
  vip_level: "0"             # key = entity field name, value = default
```

### 5.8.4 Expansion Declarative Data Hooks

```java
@Override
protected String[] dataRoots() { return new String[]{"data"}; }   // jar default-file root (copy, no overwrite)

@Override
protected String[] sqlRoots()  { return new String[]{"sql"}; }    // jar SQL root (init.sql + migrations)

@Override
protected List<Object> seedData() {                               // seed entities (inserted only when the table is empty)
    return Arrays.asList(new MyConfig("default"));
}

@Override
protected int schemaVersion() { return 1; }                       // current schema version (pairs with V1 files)
```

- Registration automatically performs: default file copy (no overwrite) → create tables → init.sql (MySQL only) → migrations → seed → meta record;
- `unregister()` only removes the data registration (data and meta are kept), **never deletes data**;
- Explicit purge (clean reinstall) is destructive and requires caller-side double confirmation.

### 5.8.5 DataRegistrationApi

```java
DataRegistrationApi data = api.getDataRegistration();

DataHandle h = data.register(owner, spec);   // auto install / update (meta-detected)
data.unregister(h);                          // remove registration (data kept)
data.purge(h);                               // explicit purge (DROP/delete file + meta, ownership-checked)
data.reinstall(h, false);                    // reinstall keeping data
data.isInstalled("MCERP");                   // installed?
data.tablesOf("MCERP");                      // owned tables
```

`DataSpec` fields: `pluginName` (required) / `schemaVersion` / `dataRoots` / `sqlRoots` /
`seedData` / `tableClasses` (defaults inferred from seedData).

### 5.8.6 Ops Command /soyshttp data

```
/soyshttp data <plugin> status                   # status (version/scripts/tables/backend/handle)
/soyshttp data <plugin> update [version]          # explicit migration (default: declared version; target optional)
/soyshttp data <plugin> reinstall                 # reinstall keeping data
/soyshttp data <plugin> uninstall                 # remove registration (data & meta kept)
```

- update/reinstall/uninstall only affect plugins with a **registered data handle** (third-party plugins register via
  `DataRegistrationApi.register`); the main plugin's own data is managed automatically at startup;
- purge is not exposed as a command (prevents accidental deletion); use the API explicitly with double confirmation.

## 5.9 Version Notes

- **Old-driver compatibility on 1.6.4 / 1.7.10**: the server bundles legacy JDBC3 drivers (`org.sqlite.Conn` / `com.mysql.jdbc.ConnectionImpl` without `isValid(int)`); each adapter module's `JdbcCompat` auto-compensates (isValid→isClosed wrapper + connectionTestQuery + detectMysqlDriver); no patch scripts needed;
- SQLite primary + ORM verified on 1.6.4 / 1.7.10;
- The cross-backend search **interface default** throws `UnsupportedOperationException` ("current backend does not support cross-backend search"); the YAML and SQL backends both implement it; third-party backends must override `search` / `searchPage`.
