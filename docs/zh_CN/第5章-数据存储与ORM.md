# 第5章 数据存储与ORM

SOYSHTTPOverMC 提供两层数据能力：**KV 存储**（`SyncRecord` 主辅镜像）与 **ORM**（实体注解 + 条件链，YAML/SQL 双后端同一 API）。

## 5.1 存储架构（StorageManager）

### 5.1.1 主辅模型

- 所有**已启用**后端中优先级最高者（**MYSQL > SQLITE > YAML**）成为**主存储**，承担全部读操作；
- 其余启用后端作为**辅助存储**，写入时被镜像同步（热备份 / 降级方案）；
- 写入收敛到**单线程执行器**，保证写操作严格有序；
- 任何后端初始化失败被标记不可用跳过，不崩溃；全部不可用时降级为内存模式（日志警告）。

### 5.1.2 config.yml 存储配置

```yaml
storage:
  backends:
    yaml:
      enabled: true
      file: data/              # 存放各类 yml 表的文件夹（records.yml + 各 ORM 表 .yml）
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
      keepalive-interval: 1800
  cross-server: false      # 跨服同步：所有实例指向同一 MySQL，数据跨服可见
  mirror:
    enabled: true          # 辅助存储镜像写入
    async: true
    sync-on-startup: false
```

- `table-prefix` 若无特殊要求请勿修改（开发者需按前缀获取正确表名）；
- 跨服同步前提：所有实例 `mysql.enabled: true` 指向同一数据库，MySQL 自动成为主存储；实例各自仍可保留本地 yaml/sqlite 热备份，但**勿在多实例间互相同步**（sync 命令 / sync-on-startup）以免覆盖共享数据。

### 5.1.3 数据面（SyncRecord）

存储的数据面是通用 `SyncRecord`（key + value + 时间戳）。`DataStorage` 是后端抽象接口，实现 `getType()/initialize()/shutdown()/isAvailable()/describe()` 与读写方法即可新增后端（在 `StorageManager.buildStorage` 注册一行）。

### 5.1.4 互转与覆盖

```text
/soyshttp migrate <来源> <目标>   # 任意两个后端之间转换数据
/soyshttp sync                   # 主存储全量覆盖写入所有辅助存储
```

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

两套门面**同一 API 表面**，按后端自动路由：

```java
import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.soyshttpovermc.orm.SQL;

// —— 查询 ——
List<User> all = YAML.Pojo.select(User.class);                    // 全部
List<User> admins = YAML.Pojo.select(User.class,
        q -> q.eq(User::getRole, "admin"));                       // 条件
User u = YAML.Pojo.get(User.class, 1L);                           // 按主键
Page<User> page = YAML.Pojo.selectPage(User.class, 1, 10);        // 分页

// —— 写 ——
YAML.Pojo.insert(user);                                           // 插入（主键必须已赋值）
YAML.Pojo.updateById(user);                                       // 按主键更新（YAML 端=upsert）
YAML.Pojo.deleteById(User.class, 1L);

// —— SQL 端（后端可用时）——
if (SQL.Pojo.isAvailable()) {
    List<User> sqlAdmins = SQL.Pojo.select(User.class, q -> q.eq(User::getRole, "admin"));
}
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
raw.set("extra", "value");                       // 自由读写
YAML.Pojo.save(User.class);                      // 显式落盘（原子写）
```

## 5.7 跨服同步

- `storage.cross-server: true` + MySQL：令牌黑名单 / 审计 / 心跳 / 全局密钥跨服可见；
- 插件每 30 秒异步上报心跳（serverId + 服名 + host + port），serverId 规则：群组服 = `proxy.server-name`，独立服 = `standalone-<host>:<port>`；
- JWT 全局密钥从共享存储下发，保证跨服验签一致。

## 5.8 版本注意

- **1.6.4 / 1.7.10 旧驱动兼容**：服务端自带旧版 JDBC3 驱动（`org.sqlite.Conn` / `com.mysql.jdbc.ConnectionImpl` 无 `isValid(int)`），adapter 各版本模块的 `JdbcCompat` 已自动兼容（isValid→isClosed 包装 + connectionTestQuery + detectMysqlDriver），无需补丁脚本；
- sqlite 主存储 + ORM 已在 1.6.4 / 1.7.10 实测通过；
- 跨端搜索接口的**默认实现**抛 `UnsupportedOperationException`（"当前后端不支持跨端搜索"），YAML 与 SQL 后端均已实现；新增第三方后端时需 override `search` / `searchPage`。
