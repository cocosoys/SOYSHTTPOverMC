# Chapter 5 Data Storage & ORM

SOYSHTTPOverMC persistence goes through **ORM** uniformly (entity annotations + condition chain, one API across YAML/SQL backends): system-level cross-server data (blacklist / audit / heartbeat / global secret) is also an ORM entity `SoysRecord` (table `soys_records`), routed through the `DATA` facade.

## 5.1 Storage Architecture (ORM Dual-Backend Routing)

All entity data is routed through the `DATA` facade: if `storage.backends.mysql/sqlite` is enabled → SQL (mysql preferred),
otherwise it falls back to YAML (`data/<table>.yml`). **Only one backend is active at a time** (no primary-secondary, no mirroring);
cross-server sharing = every instance connects to the same MySQL.

### 5.1.1 Routing Rules

- `DATA.sqlEnabled()`: `storage.backends.mysql/sqlite` assembled (mysql preferred) → all entities go to SQL;
- Otherwise fall back to `data/<table>.yml` (YAML backend, zero-dependency);
- If a backend is unavailable (e.g. SQL init failed) it falls back to YAML automatically; when both are unavailable the related API throws `IllegalStateException` (not triggered in normal configuration);
- Tables are created / column-patched automatically by the ORM (SQL `CREATE TABLE IF NOT EXISTS` + tolerant `ALTER`; YAML lazily generated).

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
      table-prefix: ''
    mysql:
      enabled: false
      url: 'jdbc:mysql://localhost:3306/minecraft?useUnicode=true&characterEncoding=utf8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai'
      username: root
      password: ''
      table-prefix: ''
  cross-server: false      # cross-server sync: all instances point at the same MySQL, data visible across servers
```

- Do not change `table-prefix` unless you must (developers must read the prefixed table name accordingly);
- Cross-server prerequisites: every instance sets `storage.backends.mysql.enabled: true` pointing at the same database (the SQL backend is the shared data source);
  enabling `storage.cross-server: true` while SQL is off logs a warning at startup (multi-instance data is not shared).

### 5.1.3 Data Plane (SoysRecord Entity)

Cross-server sync data (token blacklist / issuance audit / instance heartbeat / global JWT secret) lives in the ORM entity
`SoysRecord` (table `soys_records`), routed through the `DATA` facade; the semantic layer is `RecordSyncStorage` (the `SyncStorage` interface).
Key conventions: `blacklist:<jti>` / `audit:<jti>:<nonce>` / `instance:<serverId>` / `meta:jwt_secret`.
Audit fields `create_time / update_time` are auto-filled by the ORM write path (business time uses `updated_at`).

Legacy data is auto-migrated at startup (idempotent):
- YAML: old `records.yml` (root `records`) → `soys_records.yml` (root `soys_records`);
- SQL: old tables `mc_shttp_records` / `mc_shttp_soys_records` → `soys_records` (REPLACE SELECT + DROP).

### 5.1.4 Explicit Migration

```text
/soyshttp migrate <yaml|sql> <yaml|sql>   # explicitly migrate all soys_records data between the two backends (bypasses auto routing)
```

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

Both facades share **one API surface**, routed per backend:

```java
import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.soyshttpovermc.orm.SQL;

// —— queries ——
List<User> all = YAML.Pojo.select(User.class);                    // all rows
List<User> admins = YAML.Pojo.select(User.class,
        q -> q.eq(User::getRole, "admin"));                       // with condition
User u = YAML.Pojo.get(User.class, 1L);                           // by primary key
Page<User> page = YAML.Pojo.selectPage(User.class, 1, 10);        // paged

// —— writes ——
YAML.Pojo.insert(user);                                           // insert (primary key must be set)
YAML.Pojo.updateById(user);                                       // update by PK (upsert on the YAML side)
YAML.Pojo.deleteById(User.class, 1L);

// —— SQL side (when the backend is available) ——
if (SQL.Pojo.isAvailable()) {
    List<User> sqlAdmins = SQL.Pojo.select(User.class, q -> q.eq(User::getRole, "admin"));
}
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

## 5.8 Version Notes

- **Old-driver compatibility on 1.6.4 / 1.7.10**: the server bundles legacy JDBC3 drivers (`org.sqlite.Conn` / `com.mysql.jdbc.ConnectionImpl` without `isValid(int)`); each adapter module's `JdbcCompat` auto-compensates (isValid→isClosed wrapper + connectionTestQuery + detectMysqlDriver); no patch scripts needed;
- SQLite primary + ORM verified on 1.6.4 / 1.7.10;
- The cross-backend search **interface default** throws `UnsupportedOperationException` ("current backend does not support cross-backend search"); the YAML and SQL backends both implement it; third-party backends must override `search` / `searchPage`.
