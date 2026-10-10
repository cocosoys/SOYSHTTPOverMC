# 第5章 数据存储与ORM

SOYSHTTPOverMC 的持久化统一走 **ORM**（实体注解 + 条件链，YAML/SQL 双后端同一 API）：系统级跨服同步数据（黑名单 / 审计 / 心跳 / 全局密钥）亦为 ORM 实体 `SoysRecord`（表 `soys_records`），经 `DATA` 门面路由读写。

## 5.1 存储架构（ORM 多后端路由）

实体数据统一经 `DATA` 门面路由：`storage.backends.mysql/sqlite/yaml` **可同时启用**，
按 `MYSQL > SQLITE > YAML` 优先级唯一主存储承担默认读写；其余启用后端仍可被访问——
`DATA` 门面所有方法提供带后端类型参数的重载（如 `DATA.get(StorageType.SQLITE, User.class, id)`），
显式选择后端执行读写，操作与默认读写无差异、仅多一个后端类型参数。

### 5.1.1 路由规则

- `DATA.sqlEnabled()`：`storage.backends.mysql/sqlite` 已装配（mysql 优先）→ 默认读写走 SQL；
- 否则默认读写回退 `data/<表名>.yml`（YAML 后端，零依赖）；
- 后端不可用（如 SQL 初始化失败）自动回退 YAML；双不可用时相关 API 抛 `IllegalStateException`（正常配置不触发）；
- 实体表由 ORM 自动建表 / 补列（SQL `CREATE TABLE IF NOT EXISTS` + 容忍式 `ALTER`；YAML 懒生成）；
- 多后端同时启用时，非主后端不参与默认读写，仅经带 `StorageType` 参数的重载显式访问。

### 5.1.2 config.yml 存储配置

```yaml
storage:
  backends:
    yaml:
      enabled: true
      file: data/              # 存放各类 yml 表的文件夹（soys_records.yml + 各 ORM 表 .yml）
      backup-on-save: false
    sqlite:
      enabled: false
      file: data/records.db
    mysql:
      enabled: false
      url: 'jdbc:mysql://localhost:3306/minecraft?useUnicode=true&characterEncoding=utf8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai'
      username: root
      password: ''
  cross-server: false      # 跨服同步：所有实例指向同一 MySQL，数据跨服可见
```

- 跨服同步前提：所有实例 `storage.backends.mysql.enabled: true` 指向同一数据库（SQL 后端即共享数据源）；
  开启 `storage.cross-server: true` 但 SQL 未启用时启动会告警（多实例数据不共享）。

### 5.1.3 数据面（SoysRecord 实体）

跨服同步数据（令牌黑名单 / 签发审计 / 实例心跳 / 全局 JWT 密钥）统一落在 ORM 实体
`SoysRecord`（表 `soys_records`），经 `DATA` 门面路由读写，语义层为 `RecordSyncStorage`（`SyncStorage` 接口）。
key 约定：`blacklist:<jti>` / `audit:<jti>:<nonce>` / `instance:<serverId>` / `meta:jwt_secret`。
审计字段 `create_time / update_time` 由 ORM 写路径自动填充（业务时间以 `updated_at` 为准）。

### 5.1.4 后端迁移 / 覆盖同步

```text
/soyshttp migrate <后端> <后端> [confirm]     # 在两个后端之间迁移全部已登记表数据（合并语义：按 key upsert，不清空）
/soyshttp sync [<from> <to> [confirm]]        # 覆盖语义（先清后写，SQL 端同事务回滚）
                                              #   无参 = 主存储 → 全部辅助后端全量覆盖（预览后追加 confirm 执行）
                                              #   带参 = 定向覆盖（需 confirm 二次确认）
```

> 写仅落于一个 DATA 后端（默认主存储）；`migrate`/`sync` 为显式运维通道，不参与自动路由。

## 5.2 ORM：实体注解

ORM 使用 `com.dlz.db.annotation` 下的注解标记实体：

| 注解 | 目标 | 说明 |
| --- | --- | --- |
| `@TableName("表名")` | 类 | 指定表名（缺省用类名小写下划线） |
| `@TableId(value, type)` | 字段 | 主键；`type = IdType.SEQ`（自增）/ 其它（见 IdType） |
| `@TableField(value, exist, select)` | 字段 | 列名映射；`exist=false` 忽略字段；`select=false` 查询不选中 |

```java
@TableName("user")
public class User {
    @TableId(type = IdType.SEQ)
    private Long id;
    @TableField("name")
    private String name;
    private String role;      // 列名 = role
    // getter / setter ...
}
```

> 实体字段须有 getter/setter（ORM 通过 getter/setter 读写）；YAML 端与 SQL 端列名一致（小写下划线）。

## 5.3 双后端门面：YAML.Pojo / SQL.Pojo

业务层**优先使用统一门面 `DATA`**（自动路由默认主存储，无需关心后端）；需要固定后端时用带
`StorageType` 参数的重载（如 `DATA.select(StorageType.SQLITE, User.class)`）；`YAML.Pojo` / `SQL.Pojo`
为同构底层门面，仅高级场景直接使用。两套底层门面**同一 API 表面**：

```java
import com.github.cocosoys.mc.soyshttpovermc.orm.DATA;
import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.soyshttpovermc.orm.SQL;

// —— 统一门面（自动路由默认主存储）——
List<User> all = DATA.select(User.class);
List<User> admins = DATA.select(User.class, q -> q.eq(User::getRole, "admin"));
User u = DATA.get(User.class, 1L);
DATA.insert(user);
DATA.updateById(user);                       // 按主键更新（YAML 端=upsert）
DATA.deleteById(User.class, 1L);
// 指定后端类型读写：DATA.get(StorageType.SQLITE, User.class, 1L) / DATA.select(StorageType.MYSQL, ...)

// —— 底层门面（固定后端）——
List<User> yamlAll = YAML.Pojo.select(User.class);
List<User> sqlAdmins = SQL.Pojo.select(User.class, q -> q.eq(User::getRole, "admin"));
```

- `YAML.Pojo.init(dataDir)` 由 core 装配（`config.yml storage.backends.yaml.file`）；
- `SQL.Pojo` 数据源来自 `storage.backends.{mysql,sqlite}`（mysql 优先），表按实体自动创建；未装配时方法返回空 / false；
- YAML 数据文件：`data/<表名>.yml`，根节点为表名，主键值为键。

## 5.4 条件链（Query）

`Query<T>` 借鉴 MyBatis-Plus LambdaQueryWrapper 形态，条件以 **Lambda 引用字段**（重构安全），内部累积为 `ConditionTree`（双后端通解）：

```java
YAML.Pojo.selectW(User.class)
        .eq(User::getRole, "admin")
        .like(User::getName, "a")
        .orderByDesc(User::getCreateTime)
        .queryBeanList();                    // 执行并返回 List

// 分组条件
YAML.Pojo.select(User.class, q -> q
        .eq(User::getOnline, true)
        .ands(g -> g.eq(User::getRole, "vip").eq(User::getVipLevel, 3))
        .ors(g -> g.eq(User::getRole, "admin").eq(User::getRole, "mod")));
```

| 条件方法 | SQL 语义 |
| --- | --- |
| `eq / ne` | = / <> |
| `gt / ge / lt / le` | > / >= / < / <= |
| `like` | LIKE %val% |
| `in(column, values...)` | IN |
| `isNull / isNotNull` | IS NULL / IS NOT NULL |
| `or(column, op, value)` | OR 连接上一条件 |
| `ands(g)` / `ors(g)` | AND 组 / OR 组 |
| `orderByAsc / orderByDesc` | 排序 |
| `page(Page)` | 分页 |

执行方法：`queryBeanList()`（List）、`queryBeanPage()`（Page）、`tree()`（条件树）。

## 5.5 跨端搜索（模糊搜索）

两后端均已实现关键字模糊搜索（接口默认实现抛 `UnsupportedOperationException`，但 YAML/SQL 执行器均已 override）：

```java
// YAML 端：全量扫描 + LIKE（不区分大小写）
List<User> hits = YAML.Pojo.search(User.class, "steve", "name");          // 只搜 name 字段
List<User> hits2 = YAML.Pojo.search(User.class, "vip", "name", "role");   // 多字段任一命中

// SQL 端：LIKE 查询
if (SQL.Pojo.isAvailable()) {
    List<User> sqlHits = SQL.Pojo.search(User.class, "steve", "name");
}

// 分页版本
Page<User> page = YAML.Pojo.searchPage(User.class, 1, 10, "steve", "name");
```

## 5.6 原始文件视图（仅 YAML）

```java
ConfigSection raw = YAML.Pojo.get(User.class);   // 实体对应文件的原始视图
raw.set("extra", "value");  // 写入
YAML.Pojo.save(User.class);  // 保存回数据库
```

## 5.7 跨服同步

- `storage.cross-server: true` + MySQL（所有实例指向同一数据库）：令牌黑名单 / 审计 / 心跳 / 全局密钥（实体表 `soys_records`）跨服可见；
- 插件每 30 秒异步上报心跳（serverId + 服名 + host + port），serverId 规则：群组服 = `proxy.server-name`，独立服 = `standalone-<host>:<port>`；
- JWT 全局密钥从共享存储下发，保证跨服验签一致；
- 注意：跨服开启但 SQL 未启用时，启动会告警（YAML 为单机文件，无法跨实例共享）。

## 5.8 数据层自动化运维（Auto Ops / DataRegistrationApi）

> 把"运维人工初始化"（复制默认 data 文件、执行 init.sql、写入种子数据、schema 升级）提升为
> **完全自动化**。主插件与 `SoysExpansion` 附属插件统一走同一机制，行为受 config.yml
> `auto.ops.*` 控制。

### 5.8.1 config 开关

```yaml
auto:
  ops:
    enabled: true    # 总开关（false = 全部跳过）
    init: true       # 自动初始化（默认文件复制 + init.sql + 种子 + 首次安装的迁移）
    update: true     # 自动更新（已有安装的版本化迁移）
    fail: disable    # disable（默认）= 仅禁用失败的对应插件（主插件失败→禁 SOYS；附属失败→只禁该附属）
                     # warn = 跳过并告警，继续运行（相关功能可能缺失）
```

### 5.8.2 meta 版本表（soys_schema_meta）

- 存储：SQL 表 `soys_schema_meta` 或 YAML 文件 `data/soys_schema_meta.yml`（文件名 = 表名）；
- 记录粒度：**每表一行**（`plugin` + `tableName` 逻辑双主键，dlz 单主键约束下以 `plugin:tableName`
  组合键实现），`tableName = "*"` 行为插件级迁移记录（schemaVersion + 已执行脚本 JSON）；
- 作用：卸载 / 重装 / 更新时**自动识别**——卸载插件（删 jar）后重装，meta 仍在 → 自动走
  "保留数据更新"，数据不丢；purge（显式清理）后才回到全新安装。

### 5.8.3 迁移脚本约定（schemaVersion + V{n}）

- 插件声明 `schemaVersion()`（Expansion 钩子，默认 0 = 仅 init.sql + 种子，无迁移）；
- 脚本位置（按后端分目录，禁止混放）：jar 内 `sql/migrations/V<n>/<表名>.sql`（MySQL 方言）与
  `data/migrations/V<n>/<表名>.yml`（YAML 后端，声明式补字段）；`V<n>` 从 1 起递增；
  注意 YAML 迁移与 SQL 迁移**共享同一版本号**（同一 `V<n>` 可同时存在两个通道的文件）；
- 执行规则：**全新安装不执行迁移**——data/*.yml 与 init.sql 即最新版本完整结构，
  初始化后 meta 直接标记为最高版本；**已安装（老用户）按 meta 增量执行 V(meta+1)..V(schemaVersion)**；
  无 meta 但检测到旧数据时同样按升级路径执行；**每脚本执行成功后立即落 meta**（中途失败不重跑已成功项）；
- **双通道 + 目录结构**：MySQL 方言执行 `sql/migrations/V<n>/<表名>.sql`（ALTER 等 SQL 变更）；
  **YAML 后端**执行 `data/migrations/V<n>/<表名>.yml`（声明式补字段，见下方格式）；
  版本号 = 子目录名 `V<n>`，文件名 = 表名（禁止旧 `V{n}__描述` 扁平命名）；SQLite 跳过迁移
  （运行时自动建表 + 容忍式补列已覆盖）；
- 升级路径：提高 schemaVersion（如 1→2）并新增 `V2` 目录脚本（`sql/migrations/V2/...` 与
  `data/migrations/V2/...`），重启或 `/soyshttp data <插件> update` 自动增量执行。

YAML 迁移脚本格式（`data/migrations/V<n>/<表名>.yml`）：

```yaml
# data/migrations/V2/soys_perm_user.yml —— 为既有记录补充缺失字段默认值
# 目标表 = 文件名（<表名>.yml），脚本无需 table 字段；若声明则必须与文件名一致
add-fields:
  vip_level: "0"             # 键 = 实体字段名，值 = 默认值
```

- 执行：经 ORM 共享缓存视图读写（`YamlBackendExecutor.getConfig/save`），与运行时写路径共用锁；
- 表不在数据包表清单（DataSpec.tableClasses / seedData 推断）时跳过并告警。
### 5.8.4 Expansion 声明式数据钩子

```java
@Override
protected String[] dataRoots() { return new String[]{"data"}; }   // jar 内默认文件根（复制不覆盖）

@Override
protected String[] sqlRoots()  { return new String[]{"sql"}; }    // jar 内 SQL 根（init.sql + migrations）

@Override
protected List<Object> seedData() {                               // 种子实体（表空才插）
    return Arrays.asList(new MyConfig("default"));
}

@Override
protected int schemaVersion() { return 1; }                       // 当前 schema 版本（配套 V1 目录脚本）
```

- 注册时自动完成：默认文件复制（不覆盖）→ 建表 → init.sql（仅 MySQL）→ 迁移 → 种子 → meta 记录；
- `unregister()` 只摘数据登记（数据与 meta 保留），**永不删除数据**；
- 显式清理（purge / 清空重装）为破坏性操作，由调用方二次确认。

### 5.8.5 DataRegistrationApi（能力组 7）

```java
DataRegistrationApi data = api.getDataRegistration();

DataHandle h = data.register(owner, spec);   // 自动安装 / 更新（meta 识别）
data.unregister(h);                          // 摘登记（数据保留）
data.purge(h);                               // 显式清理（DROP/删文件 + 删 meta，他属校验）
data.reinstall(h, false);                    // 保留数据重装
data.isInstalled("MCERP");                   // 是否已安装
data.tablesOf("MCERP");                      // 归属表清单
```

`DataSpec` 字段：`pluginName`（必填）/ `schemaVersion` / `dataRoots` / `sqlRoots` /
`seedData` / `tableClasses`（缺省由 seedData 推断）。

### 5.8.6 运维命令 /soyshttp data

```bash
/soyshttp data <插件> status                   # 状态（版本/脚本/归属表/存储后端/句柄）
/soyshttp data <插件> update [版本]             # 显式迁移（默认到声明版本，可指定目标）
/soyshttp data <插件> reinstall                # 保留数据重装
/soyshttp data <插件> uninstall                # 摘登记（数据与 meta 保留）
```

- update/reinstall/uninstall 仅对**已登记数据句柄**的插件有效（第三方插件经
  `DataRegistrationApi.register` 登记后生效）；主插件自身数据随启动自动管理；
- purge 不在命令中暴露（防误删），需要清空数据请经 API 显式调用并二次确认。

## 5.9 版本注意

- **1.6.4 / 1.7.10 旧驱动兼容**：服务端自带旧版 JDBC3 驱动（`org.sqlite.Conn` / `com.mysql.jdbc.ConnectionImpl` 无 `isValid(int)`），adapter 各版本模块的 `JdbcCompat` 已自动兼容（isValid→isClosed 包装 + connectionTestQuery + detectMysqlDriver），无需补丁脚本；
- sqlite 主存储 + ORM 已在 1.6.4 / 1.7.10 实测通过；
- 跨端搜索接口的**默认实现**抛 `UnsupportedOperationException`（"当前后端不支持跨端搜索"），YAML 与 SQL 后端均已实现；新增第三方后端时需 override `search` / `searchPage`。
