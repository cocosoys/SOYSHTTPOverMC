# 第五章 数据存储与 ORM

本章讲解如何把数据持久化。本插件提供两套互补的存储能力：

- **ORM（对象关系映射）**：用一套注解同时读写 **YAML 文件**与 **SQL 数据库**，API 表面完全一致；
- **StorageManager（主辅镜像协调器）**：面向“原生记录（`SyncRecord`）”的多后端主辅镜像。

两者解决不同层次的问题：ORM 让你像操作 Java 对象一样读写业务实体；StorageManager 负责跨后端一致性与热备。

## 5.1 ORM 与 StorageManager 的区别

| 维度 | ORM（YAML.Pojo / SQL.Pojo） | StorageManager |
| --- | --- | --- |
| 操作对象 | 带注解的 Java Bean（实体类） | `SyncRecord`（key-value 原始记录） |
| API 风格 | `select` / `get` / `insert` / `updateById` / `Query` 条件链 | `load` / `saveAsync` / `migrate` / `syncToSecondaries` |
| 多后端 | 每次显式选 `YAML` 或 `SQL` 门面 | 自动选主存储 + 镜像到辅助 |
| 典型用途 | 业务实体（用户、配置、排行榜） | 系统级记录（隧道状态、会话等）跨后端同步 |
| 自动建表 | SQL 端按实体自动建表 | 各后端自行管理结构 |

> 作为插件开发者，做业务功能通常直接用 ORM 即可，简单直接。StorageManager 更多由本插件内部与需要跨后端热备的场景使用。

## 5.2 实体注解契约

ORM 复用 `com.dlz.db.annotation` 下的一套注解，并且**一套注解同时解决 SQL 与 YAML 两端**——这是本插件的设计核心。

### 5.2.1 @TableName（表名 = YAML 文件名）

```java
@TableName("player_profile")
public class PlayerProfile { ... }
```

- `value` **原样使用**：既作为 YAML 文件名 `data/player_profile.yml`，也作为 SQL 表名 `player_profile`；
- 不写 `@TableName` 时，默认取类名小写（如 `PlayerProfile` → `playerprofile`）；
- 注意：`@TableName` 的 value **不经过列名转换器**，原样落到文件名/表名。

### 5.2.2 @TableId（主键）

```java
@TableId(value = "id", type = IdType.INPUT)
private String id;
```

- 标记主键字段；
- `value` 为列名（不写则取字段名）；
- `type` 为 `IdType`：本项目推荐 `INPUT`（由你自行赋值主键），或 `ASSIGN_UUID` / `ASSIGN_ID` 自动生成。`AUTO`（数据库自增）需数据库侧显式设置自增列才生效。

### 5.2.3 @TableField（字段映射）

```java
@TableField("display_name")
private String displayName;

@TableField(exist = false)
private transient String tempCache;   // 不持久化
```

- `value`：自定义列名（不写则按字段名转小写下划线，见下）；
- `exist = false`：该字段不是存储列，**不参与读写**（适合临时缓存、计算字段）。

### 5.2.4 列名小写下划线统一（ColumnNameLower）

字段名到列名的默认转换规则为 **驼峰转小写下划线（snake_case）**：

```
userName      → user_name
displayName   → display_name
createdAt     → created_at
```

**关键点：YAML 端与 SQL 端使用完全相同的列名规则**，因此同一实体在两端的数据结构天然对齐，迁移/镜像不会错位。若想覆盖默认列名，用 `@TableField("自定义列名")`。

### 5.2.5 一个完整实体

```java
@TableName("player_profile")
public class PlayerProfile {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("player_name")
    private String playerName;

    @TableField("level")
    private int level;

    @TableField("vip")
    private boolean vip;

    @TableField(exist = false)
    private transient long runtimeOnly;   // 不参与存储

    // 必须提供 getter / setter（Lambda 字段引用依赖）
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public boolean isVip() { return vip; }
    public void setVip(boolean vip) { this.vip = vip; }
}
```

> 提示：条件链使用 Lambda 引用字段（如 `q.eq(PlayerProfile::getPlayerName, "Steve")`），重构安全；内部通过 `SerializedLambda` 解析为列名，因此**实体必须有标准 getter/setter**。

## 5.3 YAML 后端（YAML.Pojo）

适合轻量、单服务器、人类可读的配置型数据。数据落在 `data/<@TableName>.yml`，结构为：

```yaml
player_profile:            # 根节点 = 表名
  "id-1":                  # 主键值为键
    player_name: Steve
    level: 10
    vip: true
  "id-2":
    player_name: Alex
    level: 5
    vip: false
```

### 5.3.1 装配

宿主插件（SOYSHTTPOverMC）在 `onEnable` 中调用：

```java
YAML.Pojo.init(getDataFolder());   // 设置数据目录
```

你作为第三方插件**无需手动 init**——主插件已装配好；直接调用 `YAML.Pojo.xxx(...)` 即可（未装配时按默认 `data/` 懒初始化）。

### 5.3.2 查询

```java
// 查询全部 → 直接返回 List
List<PlayerProfile> all = YAML.Pojo.select(PlayerProfile.class);

// 按条件查询（Consumer 构建条件链）
List<PlayerProfile> vips = YAML.Pojo.select(PlayerProfile.class,
    q -> q.eq(PlayerProfile::isVip, true).orderByDesc(PlayerProfile::getLevel));

// 链式构建器
List<PlayerProfile> top = YAML.Pojo.selectW(PlayerProfile.class)
    .ge(PlayerProfile::getLevel, 8)
    .orderByDesc(PlayerProfile::getLevel)
    .queryBeanList();

// 按主键取单条（无返回 null）
PlayerProfile p = YAML.Pojo.get(PlayerProfile.class, "id-1");

// 分页
Page<PlayerProfile> page = YAML.Pojo.selectPage(PlayerProfile.class, 1, 10);
```

### 5.3.3 原始文件视图

`YAML.Pojo.get(Class)` 返回实体对应文件的 `YamlConfiguration`（FileConfiguration）原始视图，可自由读取；外部写入后需 `save(Class)` 落盘（原子写）：

```java
YamlConfiguration raw = YAML.Pojo.get(PlayerProfile.class);
int count = raw.getConfigurationSection("player_profile").getKeys(false).size();
// ... 直接改 raw ...
YAML.Pojo.save(PlayerProfile.class);   // 显式落盘
```

### 5.3.4 写入 / 更新 / 删除

```java
PlayerProfile p = new PlayerProfile();
p.setId("id-3");
p.setPlayerName("Bob");
p.setLevel(1);
p.setVip(false);

YAML.Pojo.insert(p);          // 插入（主键必须已赋值）
YAML.Pojo.updateById(p);      // 按主键更新（YAML 端整体覆盖 = upsert）
YAML.Pojo.deleteById(PlayerProfile.class, "id-3");  // 按主键删除
```

### 5.3.5 跨端搜索

```java
// 在 player_name 字段上模糊搜索 "ste"
List<PlayerProfile> hits = YAML.Pojo.search(PlayerProfile.class, "ste", "player_name");
// 不传 fields 时在全部 String / 枚举字段上模糊匹配
List<PlayerProfile> all = YAML.Pojo.search(PlayerProfile.class, "ste");
```

YAML 端搜索对目标字段做**包含（contains，忽略大小写）**匹配，适合站内搜索、自动补全等。

## 5.4 SQL 后端（SQL.Pojo）

当数据量大、或需要跨实例共享（群组服统一数据库）时，用 SQL 后端（dlz-db-core + HikariCP）。数据源来自 `storage.backends.{mysql,sqlite}`（mysql 优先）。

### 5.4.1 可用性判断

```java
if (SQL.Pojo.isAvailable()) {
    // SQL 后端已装配（mysql/sqlite 启用）
}
```

未启用 SQL 后端时，`SQL.Pojo` 的方法安全返回空 / false，**不会抛异常**。

### 5.4.2 同 API 表面

`SQL.Pojo` 与 `YAML.Pojo` **方法签名完全一致**，因此同一段业务代码只需切换门面即可换后端：

```java
List<PlayerProfile> all = SQL.Pojo.select(PlayerProfile.class);
PlayerProfile p = SQL.Pojo.get(PlayerProfile.class, "id-1");
SQL.Pojo.insert(p);
SQL.Pojo.updateById(p);
SQL.Pojo.deleteById(PlayerProfile.class, "id-1");
Page<PlayerProfile> page = SQL.Pojo.selectPage(PlayerProfile.class, 1, 10);
```

### 5.4.3 自动建表

首次访问实体时，SQL 后端按实体元数据**自动建表**：列类型按 Java 类型映射，主键为 `VARCHAR(64)`。你无需手写 DDL。列名与 YAML 端一致（小写下划线），保证结构对齐。

> 注意：MySQL 中部分词是保留字（如 `key`）。SQL 端建表 / 查询会对列名加反引号（`` `key` ``）规避；若你的实体字段恰为保留字，直接用字段名即可，底层已处理。

### 5.4.4 跨端搜索（LIKE）

```java
List<PlayerProfile> hits = SQL.Pojo.search(PlayerProfile.class, "ste", "player_name");
```

SQL 端搜索使用 **`LIKE '%关键字%'`**（不区分大小写由数据库排序规则决定），字段选择逻辑与 YAML 端相同（默认全部 String/枚举字段）。

## 5.5 StorageManager（主辅镜像）

### 5.5.1 主辅模型

所有已启用后端中优先级最高者（MYSQL > SQLITE > YAML）成为**主存储**，承担全部读；其余作为**辅助存储**，在写入时被**镜像同步**（热备 / 降级方案）。多后端可同时开启。

### 5.5.2 配置（config.yml 的 storage 段）

```yaml
storage:
  backends:
    yaml:
      enabled: true
    sqlite:
      enabled: false
    mysql:
      enabled: true
      host: localhost
      port: 3306
      database: mcdb
      username: mc
      password: "***"
      pool-size: 10
  cross-server: false          # 群组服多实例共享（主存储须为 MySQL）
  mirror:
    enabled: true              # 是否镜像写
    async: true                # 镜像是否异步
    sync-on-startup: false     # 启动时把主存储全量同步到辅助
```

### 5.5.3 读主写辅

```java
SyncRecord rec = storageManager.load(key);          // 仅主存储读
Collection<SyncRecord> all = storageManager.loadAll();
storageManager.saveAsync(rec);                      // 主存储写 + 镜像到辅助（异步串行）
storageManager.deleteAsync(key);
```

所有写入收敛到**单线程串行执行器**，保证写顺序，避免并发写错乱；关服阶段会等待写队列排空（最多 15 秒）。

### 5.5.4 迁移与同步

```java
int n = storageManager.migrate(StorageType.YAML, StorageType.MYSQL, true);  // 端到端迁移（overwrite 先清空）
int m = storageManager.syncToSecondaries();                                 // 主 → 全部辅助 全量覆盖
```

对应命令：`soyshttp migrate` 与 `soyshttp sync`（或简写 `shttp migrate` / `shttp sync`）。

### 5.5.5 跨服一致性提醒

开启 `storage.cross-server: true` 时，**主存储必须是 MySQL**，否则多实例无法共享数据（启动会告警）。此外，各实例的 `token-secret.key`、jti 黑名单、限流桶、统计、AuthMe 数据均每服独立，跨服一致需额外处理（见第四章）。

## 5.6 最佳实践

1. **优先用 ORM 写业务**：`YAML.Pojo` / `SQL.Pojo` 表面一致，业务代码与后端解耦；需要换后端只改门面引用与 `storage.backends` 配置。
2. **主键务必显式赋值**（本项目推荐 `IdType.INPUT`）：YAML 端主键即文件中的键，SQL 端主键为 `VARCHAR(64)`，自增需数据库侧显式配置。
3. **列名统一小写下划线**：不要混用大小写，避免 YAML↔SQL 迁移时字段错位。
4. **保留字加反引号已处理**：字段名若为 SQL 保留字，直接写即可，SQL 端会自动加反引号。
5. **不要在主线程跑 SQL**：`SQL.Pojo.select` 等是同步阻塞的，放到 Bukkit 异步调度或自建线程池，避免卡服。
6. **用好 `@TableField(exist = false)`**：临时字段、计算字段、缓存一律标记 `exist=false`，防止被误持久化。
7. **search 用于轻量模糊查询**：YAML 端是内存 contains、SQL 端是 LIKE；大数据量高频搜索请加索引或走专用搜索引擎。

下一章介绍**进阶能力与最佳实践**：HTTP 客户端、跨服调用、Bot 管理、日志、扩展与运维。
