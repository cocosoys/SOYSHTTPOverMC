# SOYSHTTPOverMC

**把 HTTP/HTTPS 架在 Minecraft 的入服端口上** —— 同一 socket 同时服务 MC 玩家、明文 HTTP 与 HTTPS，
无需独立 web 端口、无需端口映射，浏览器直接访问 `https://<服务器地址>:<MC端口>` 即可打开游戏内 Web 服务。

> Spigot/Paper 1.6.4 ~ 26.x 多版本插件（按版本选择产物，见[环境要求](#环境要求)）。含 BungeeCord 端可选代理模块（反向代理 / 群组服出网）。

## 目录

- [特性](#特性)
- [工作原理（简述）](#工作原理简述)
- [环境要求](#环境要求)
- [安装与使用](#安装与使用)
- [存储与 ORM（可选）](#存储与-orm可选)
- [命令参考](#命令参考)
- [开发者扩展（第三方插件接入）](#开发者扩展第三方插件接入)
- [开发文档](#开发文档)
- [构建](#构建)
- [安全说明](#安全说明)
- [开源注意事项](#开源注意事项)
- [免责声明](#免责声明)

---

## 特性

- **同端口三协议共存**：在 Spigot 监听 socket 上嗅探首包分流 —— MC 握手原样放行给玩家，HTTP/TLS 就地处理，互不干扰；
- **HTTPS 就地升级**：TLS 在嗅探器内动态挂载（无需独立 443），支持 PKCS12 / PEM / 自动自签证书；
- **注解式 REST API**：仿 Spring 的 `@GetMapping` / `@PostMapping` / `@ApiName` / `@ApiPermission` + `AjaxResult`，一行注册端点；
- **安全网关**：TLS 强制（426 升级）、IP 白名单（CIDR）、鉴权（X-API-Key / Bearer / Cookie 会话令牌）、令牌桶限流，策略可插拔；
- **会话令牌**：无状态 JWT（HS256）+ 退出黑名单；离线 cookie 进游戏后自动升级为在线令牌；`/soyshttp key` 可签发服主最高权限 key；
- **群组服支持**：BungeeCord/Waterfall/Velocity 后端自动探测，跨服请求 `/server/<子服名>/...`，并带可选代理模块（在代理监听端口上反向代理 HTTP/HTTPS）；
- **登录插件接入**：`LoginProvider` SPI —— 已有 AuthMe 实现（网页登录、密码校验、免登录），可扩展其他登录插件；
- **开发者开放面**：`SoysExpansion` 极简接入（一行 `register()` 自动完成端点 / 页面托管 / CORS / 数据初始化，见[开发者扩展](#开发者扩展第三方插件接入)）；亦可用统一门面 `SoysHttpOverMcApi`（注解控制器 / 网页登记 / 目录批量托管 / 自定义 MIME / 凭证 / 跨服 HTTP / 数据运维），插件 onEnable 即用；
- **ORM 多后端存储**：统一 `DATA` 门面路由（SQL 可用走 SQL，否则回退 YAML），`YAML.Pojo` / `SQL.Pojo` 同构 API；多后端可同时启用，按优先级唯一主存储承担默认读写，其余后端可经带后端类型参数的重载显式读写；`/soyshttp migrate|sync|data` 提供迁移 / 覆盖同步 / 数据自动化运维（详见 [存储与 ORM](#存储与-orm可选) 与 [开发文档](#开发文档)）；
- **性能**：gzip 压缩、ETag/304 缓存、HTTP/1.1 keep-alive、真实访客 IP 透传（`X-Forwarded-For`）。

---

## 工作原理（简述）

```
浏览器 ──TLS/HTTP──> Spigot 监听端口(server-port)
                       │  SocketSniffer 首包分类
                       ├─ MC 握手 ───────────► Spigot MC 解码器（玩家正常进服）
                       ├─ 明文 HTTP ─────────► 网关策略链 → 426 升级 TLS
                       └─ TLS(0x16 0x03) ────► 就地 SslHandler 解密 → 网关策略链
                                                → WebFrontendHandler 路由：
                                                   登录 / 注解式 API / 插件登记网页 / 静态资源
                                                   响应直接经同一连接写回浏览器
```

群组服模式：每个子服各装一份本插件；可选 `SOYSHTTPOverMC-Proxy.jar` 装在 BungeeCord，
在代理监听端口上做首包分类与反向代理（home-server=self 由代理自身托管静态页，或路由到指定子服）。

---

## 环境要求

| 项         | 要求                                                                                           |
| ---------- | ---------------------------------------------------------------------------------------------- |
| 服务端     | Spigot / Paper **1.6.4 ~ 26.x**（按版本选择对应产物 jar）；BungeeCord / Waterfall（群组服代理，可选代理模块） |
| Java 运行  | 与所选产物匹配：1.6 ~ 1.16.5 → **Java 8/11**；1.17 ~ 1.20.4 → **Java 17**；1.20.5 ~ 1.21+ → **Java 21**；26.x（年度版本号）→ **Java 25** |
| Java 构建  | 默认 JDK 8；高版本模块经 Maven toolchains 自动切换（JDK 8/11/17/21/25，见[构建](#构建)）        |
| 可选       | AuthMe（网页登录接入）；BungeeCord 代理模块（群组服出网）                                        |

版本与产物对照（Release 按需取用，无需改动任何配置）：

| 服务器版本       | 使用产物（`<ver>` 为版本号）                  | 运行 Java |
| ---------------- | --------------------------------------------- | --------- |
| 1.6.x（最低 1.6.4） | `SOYSHTTPOverMC-1_6-<ver>.jar`               | Java 8    |
| 1.7.x            | `SOYSHTTPOverMC-1_7-<ver>.jar`               | Java 8    |
| 1.8 ~ 1.12.2     | `SOYSHTTPOverMC-1_8-<ver>.jar` / `SOYSHTTPOverMC-1_12-<ver>.jar`（默认） | Java 8 |
| 1.13 ~ 1.16.5    | `SOYSHTTPOverMC-1_16-<ver>.jar`              | Java 8/11 |
| 1.17 ~ 1.20.4    | `SOYSHTTPOverMC-1_20-<ver>.jar`              | Java 17   |
| 1.20.5 ~ 1.21.x  | `SOYSHTTPOverMC-1_21-<ver>.jar`              | Java 21   |
| 26.x（年度版本号） | `SOYSHTTPOverMC-1_26-<ver>.jar`             | Java 25   |

> ⚠️ 端口约定：**访问端口 == `server.properties` 的 `server-port`**，无需新增端口、无需改防火墙（浏览器访问该端口即可）。
> 若经代理对外，请按需填写 `mc.public-host` / `mc.public-port`（仅影响对外展示的地址）。

---

## 安装与使用

### 单服模式

1. 下载 `SOYSHTTPOverMC-1_12-<版本>.jar`（本仓库 Release）放入 `plugins/`，重启服务器；
2. `server.properties`：`server-port=<你想要的端口>`；
3. 确认日志出现 `HTTP-Over-MC 已启动（同端口嗅探...）`；
4. 浏览器访问 `https://<地址>:<端口>/` 打开门户首页（自签证书请手动信任或加 `-k`）。

### 群组服模式（BungeeCord）

1. 每个后端子服都安装 `SOYSHTTPOverMC.jar`（步骤同单服），并在 `config.yml` 填写：
   ```yaml
   mc:
     public-host: "代理公网IP或域名"   # 对外展示用（可选）
     public-port: 25577                # 客户端实际可连的端口
   server-name: 子服名                  # 必须与 BungeeCord config.yml 的 servers.<name> 一致
   proxy-address: "127.0.0.1:25577"    # 跨服转发目的地址（BungeeCord 监听地址）
   ```
2. 各子服 `spigot.yml` 设 `bungeecord: true`（与代理 `ip_forward: true` 对齐）；防火墙放行代理与回环访问子服端口；
3. （可选）代理装 `SOYSHTTPOverMC-Proxy.jar`，`plugins/SOYSHTTPOverMC-Proxy/proxy.yml` 设 `enabled: true`、
   `home-server: self`（代理自身托管静态页）或某子服名（`/` 与无前缀 `/api/...` 路由到它）；
   代理监听端口由 BungeeCord 的 `config.yml` 决定（如 25577），**无需关心具体端口号**；
4. 跨服访问：`/server/<子服名>/...`。

### 配置速览

首次启动自动生成：

```
plugins/SOYSHTTPOverMC/
├── config.yml                 # 核心：mc.host/port / public-host / trust-proxy / server-name / proxy-address / storage / auto.ops
├── gateway/
│   ├── config.yml             # 网关总开关 + api-prefix
│   ├── https.yml              # HTTPS：enabled / keystore(PKCS12) > cert+key(PEM) > 自签
│   ├── policies/              # 每个安全策略一个文件：tls.yml / ip-allowlist.yml / auth.yml / rate-limit.yml
│   │                         #   auth.yml 另含记住我（auto.login.ttl / ip / fp / ticket）与 X-API-Key 权限降级开关
│   └── issuers/               # 凭证颁发器：session-token.yml（JWT 会话令牌）
└── data/                      # web 前端解压目录（磁盘热替换）；ORM 数据文件（<表名>.yml）；token-secret.key（JWT 密钥，勿外泄）
```

修改配置后 `/soyshttp reload` 热重载（命令类与注解控制器除外，需重启生效）。

---

## 存储与 ORM（可选）

插件内置一套**ORM 多后端存储**：实体用 `@TableName` / `@TableId` / `@TableField` 标注，
经统一门面 `DATA` 路由读写（SQL 可用走 SQL，否则回退 YAML），也可显式指定后端类型（如 `DATA.get(StorageType.SQLITE, ...)`）。

- **统一门面 `DATA`**：业务层只需写一套 `DATA.select/get/insert/updateById/deleteById`，无需关心后端；
  `YAML.Pojo` / `SQL.Pojo` 为同构底层门面，供需要固定后端的场景直接使用；
- **多后端主辅**：`storage.backends` 可同时启用 YAML / SQLite / MySQL，按 `MYSQL > SQLITE > YAML` 优先级
  唯一主存储承担默认读写；其余启用后端仍可经带 `StorageType` 参数的重载显式读写（操作与默认读写无差异）；
- **数据运维**：`/soyshttp migrate <from> <to>` 合并语义迁移；`/soyshttp sync [<from> <to>]` 覆盖语义同步
  （无参 = 主存储 → 全部辅助后端）；`/soyshttp data <插件> status|update|reinstall|uninstall` 数据层自动化运维；
- **跨服共享**：所有实例的 `storage.backends.mysql.enabled: true` 指向同一数据库，`storage.cross-server: true`
  开启跨服数据共享（令牌黑名单 / 审计 / 心跳 / 全局密钥，实体表 `soys_records`）。

开启存储后端示例（`config.yml`）：

```yaml
storage:
  backends:
    yaml:
      enabled: true
      file: data/              # 存放各类 yml 表的文件夹（soys_records.yml + 各 ORM 表 .yml）
    sqlite:
      enabled: false
      file: data/records.db
    mysql:
      enabled: false
      url: 'jdbc:mysql://localhost:3306/minecraft?useUnicode=true&characterEncoding=utf8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai'
      username: root
      password: ''
  cross-server: false          # 跨服共享需主存储为 MySQL 且各子服指向同一库
```

> 旧 `config.yml` 若没有 `storage` 段，插件按内存模式运行（不启用任何后端）；各服加上该段即启用对应后端。

---

## 命令参考（`/soyshttp` 或简写 `/shttp`，默认 op）

```
/soyshttp help [子指令|页码]            显示帮助页面（或某子指令详细用法）
/soyshttp eula                       显示 EULA 协议内容
/soyshttp status                     查看 HTTP 服务运行状态
/soyshttp report                     手动上报插件使用记录
/soyshttp reload                     热重载配置与网关
/soyshttp key <subject>              为指定主体签发最高权限 key（ak_ 前缀，免权限访问全部 API，请谨慎）
/soyshttp send <url|/page> [显示文字] [玩家]   向玩家发送可点击链接
/soyshttp pages [all] [页码]          查看已登记界面（默认仅 UI 页；all 含全部资源/脚本）
/soyshttp api [插件名]               查看已注册的注解式 API 端点
/soyshttp tokens                    查询所有已颁发的令牌
/soyshttp lang [语言代码]            查看/切换语言
/soyshttp log [级别]                 查看/修改日志打印等级（OFF/ERROR/WARN/INFO/DEBUG/TRACE）
/soyshttp migrate <yaml|sqlite|mysql> <yaml|sqlite|mysql> [confirm]   在 ORM 后端间迁移全部已登记表数据（合并语义）
/soyshttp sync [<from> <to> [confirm]]       后端覆盖迁移（无参=主→全部辅助；带参=定向，均需 confirm）
/soyshttp perm                       本地内置权限表（组/用户 CRUD + 查询），配套 permission.offline-fallback: local
/soyshttp apikey                     X-API-Key 本地表管理（生成/启停/过期/绑定/权限）
/soyshttp data <插件> <status|update [版本]|reinstall|uninstall>   数据层自动化运维
```

---

## 开发者扩展（第三方插件接入）

推荐方式：**继承 `SoysExpansion`**（类似 PlaceholderAPI 的 Expansion），在 `plugin.yml` 写
`softdepend: [SOYSHTTPOverMC]`，onEnable 中 **一行 `register()`** 即完成全部注册——
端点登记、owner 自动识别、页面资源托管、CORS 声明、数据层初始化、卸载登记均由框架自动处理，
开发者无需感知 `ApiRegistrationApi` / `WebPageApi` 等内部 API：

```java
import com.github.cocosoys.mc.soyshttpovermc.api.SoysExpansion;
import java.util.List;
import java.util.Arrays;

public class ShopExpansion extends SoysExpansion {

    @Override
    public String getIdentifier() { return "shop"; }        // 唯一必填元信息（冲突检测 / 页面 tag / 卸载依据）

    // —— 端点在扩展类上书写：默认自动登记本类（正常登记 → /api/plugins/<插件名>/...）——
    @GetMapping("/items")
    public AjaxResult items(@RequestParam("page") int page) { ... }

    // —— 可选：自动托管前端 dist（磁盘优先惰性登记，支持热替换；无 /plugins/<插件名>/dist 时回退 jar 内同名目录）——
    @Override
    protected String resourceRoot() { return "dist"; }

    // —— 可选：端点在独立 Controller 类时，批量登记（覆盖返回全部实例即可，注册/注销共用）——
    @Override
    protected List<Object> buildControllers() { return Arrays.asList(new ShopAdminController()); }

    // —— 可选：代理登记（无 /plugins/<插件名> 前缀，如 /api/prod-api/*；默认无）——
    @Override
    protected List<Object> buildProxyControllers() { return Arrays.asList(new ShopApiController()); }

    // —— 可选：CORS 声明（pathPrefix 为空或 "/" = 全局）——
    @Override
    protected CorsSpec[] cors() { return new CorsSpec[]{ new CorsSpec("/api", "*") }; }

    // —— 可选：数据层自动初始化（默认文件 / init.sql / 种子数据 / 版本迁移，受 config auto.ops.* 控制）——
    @Override
    protected String[] dataRoots() { return new String[]{"data"}; }
    @Override
    protected int schemaVersion() { return 1; }
}
```

```java
// onEnable 中一行注册：
if (!new ShopExpansion().register()) {
    plugin.getLogger().warning("ShopExpansion 注册失败（identifier 冲突？主插件未就绪？）");
}
// 插件禁用时建议在 onDisable 中调用 unregister() 精确反注册（框架亦按 owner 插件名兜底清理已登记内容）：
new ShopExpansion().unregister();
```

`SoysExpansion` 模板方法说明（`register()` / `unregister()` 为 final 骨架）：

- 注册顺序：数据层初始化 → 端点登记（`registerControllers`：正常登记 + 代理登记）→ 页面托管（`registerPages`）→ CORS（`registerCors`）→ 回调 `onRegister()`；**任一失败自动回滚已成功部分**，返回 `false`；
- **owner 自动识别**：`JavaPlugin.getProvidingPlugin(getClass())`，无需手动传插件实例；
- **重复 `identifier` 拒绝注册**；页面统一打 `expansion:<identifier>` tag，卸载按 tag 精确清理（含目录索引 / SPA 回退规则）；
- 需要单独定制某一种注册/注销时，覆写对应钩子（`registerCommonController` / `registerProxyController` / `registerPages` / `registerCors` / `unregister*`）即可，不影响其它类型；
- 数据层初始化先于端点注册（保证端点上线时数据已就绪）；`unregister()` 只摘登记，**永不删除数据**。

**仍可直接使用门面 API**（高级场景 / 非 Expansion 形态）：`SoysHttpOverMcApi` 7 个能力组——
`ApiRegistration`（注解控制器）、`WebPage`（网页/目录/资源）、`AuthCredential`（凭证）、
`Toolkit`（JSON / Content-Type / 前缀家族 `apiPrefix` `pluginsPrefix` `apiFullPrefix`
`pageFullPrefix` `webResourcePrefix` `serverPrefix` `fullPathPrefix` `scheme` `host` `port` `spaFallback`
`pageBase` `fpEnabled` `fpStrict`）、`HttpClient`（对外 HTTP / 回环 / 跨服）、`Extension`
（`LoginProvider` 登录插件 SPI + 自定义 `/soyshttp` 子指令 + 请求拦截器 + 自定义策略）、
`DataRegistration`（数据层自动化运维）。

监听事件（均嵌套于抽象基类下，Bukkit 标准 `registerEvents` 监听即可）：
`ApiEvent`（`ApiRegisteredEvent` / `ApiUnregisteredEvent` / `ApiAccessEvent` 及其 GET/POST/... 子类 /
`ApiAccessCompletedEvent` 及其子类）、`GatewayEvent`（`GatewayRequestEvent` / `GatewayRequestServedEvent` /
`GatewayAccessDeniedEvent` / `GatewayCredentialIssuedEvent` / `GatewayLoginResultEvent`）、
`WebResourcesEvent`（`WebResourcesAccessEvent` / `WebResourcesLoadedEvent`）、`SoysReadyEvent`、`HttpConfigReloadEvent`。

插件禁用时，其名下 API / 网页 / CORS 自动卸载。

---

## 构建

多模块工程：`common`（无 Bukkit 依赖的公共库）+ `core`（主插件，默认目标 1.12.2，Java 8 字节码）+
`adapter`（版本兼容模块聚合：`common` / `v1_6x` / `v1_7x` / `v1_16x` / `v1_20x` / `v1_21x` / `v1_26x`）。
项目根已内置 `.mvn/maven.config`（`--toolchains toolchains.xml`）与 `toolchains.xml`
（声明 JDK 8 / 11 / 14 / 17 / 21 / 25 的 `jdkHome`），**同一份 core 源码经 profile 切换 spigot-api 与 Java 版本**，
无需手动切换 JAVA_HOME：

```powershell
# 全量构建（根目录一条命令；core 走 JDK8，v1_20x/v1_21x/v1_26x 经 toolchains 自动切 JDK17/21/25）
mvn clean package "-Drevision=1.4.0"

# 仅构建主插件并切换目标版本（profile：1_12 默认 / 1_8 / 1_16 / 1_17 / 1_20_5）
mvn -f core/pom.xml clean package -P1_17 "-Drevision=1.4.0"

# 仅构建版本兼容模块（依赖 common/core 已 install 到本地仓库）
mvn -f adapter/pom.xml clean package "-Drevision=1.4.0"
```

产物（各模块 `target/`，并自动复制一份到根 `output/` 便于直接取用）：

| 产物 | 来源 | 目标服务器 |
| --- | --- | --- |
| `SOYSHTTPOverMC-1_12-<ver>.jar` | `core/target`（默认；`-P1_8` → `-1_8`、`-P1_16` → `-1_16`、`-P1_17` → `-1_17`、`-P1_20_5` → `-1_20_5`） | 1.8 ~ 1.12.2（默认）；变体：1.8.8 / 1.16.5 / 1.17 / 1.20.5+ |
| `SOYSHTTPOverMC-1_6-<ver>.jar` | `adapter/v1_6x/target` | 1.6.x |
| `SOYSHTTPOverMC-1_7-<ver>.jar` | `adapter/v1_7x/target` | 1.7.x |
| `SOYSHTTPOverMC-1_16-<ver>.jar` | `adapter/v1_16x/target` | 1.16.x |
| `SOYSHTTPOverMC-1_20-<ver>.jar` | `adapter/v1_20x/target` | 1.20.x |
| `SOYSHTTPOverMC-1_21-<ver>.jar` | `adapter/v1_21x/target` | 1.21.x |
| `SOYSHTTPOverMC-1_26-<ver>.jar` | `adapter/v1_26x/target` | 26.x（年度版本号，Java 25） |
| `SOYSHTTPOverMC-Proxy-<ver>.jar` | **独立 Maven 工程**（`SOYSHTTPOverMC-Proxy/`，不参与本 reactor） | BungeeCord 代理 |

> 打 tag（如 `v1.4.0`）即自动打包并上传到 Release；也可在 Actions 页手动触发（仅出构建产物）。
> 所有第三方依赖均从 Maven 中央仓库/官方仓库拉取（netty-all / HikariCP / protobuf-java / jackson-annotations /
> sqlite-jdbc / mysql-connector-java 等），无需本地手工 install；高版本模块（v1_26x）需 JDK 25 与
> Lombok 1.18.38（已在模块内声明）。

---

## 安全说明

- 网关策略默认 `tls.yml` 强制 HTTPS（明文 426）；如需开放明文，在 `gateway/policies/tls.yml` 关闭；
- `mc.trust-proxy: true` 时后端信任前置代理注入的 `X-Forwarded-For`；若后端可被客户端直连，建议设 `false` 防伪造 IP；
- 自签证书不获浏览器信任（可自行配置 PKCS12/PEM 正式证书，见 `gateway/https.yml`）；
- `data/token-secret.key` 为 JWT 签名密钥，**请勿外泄**；换服迁移需一并复制（否则旧令牌失效）；
- **X-API-Key 凭据存于本地表 `soys_api_key`（哈希存储，平台随机生成且仅展示一次）**，不再使用明文静态 `keys`；
  `auth.yml` 的 `api-key.local-fallback-all: false` 默认关闭——当本地表不可用时**拒绝**而非降级为"全权限放行"（避免 fail-open）；
- `/soyshttp key <subject>` 签发的最高权限 key（`ak_` 前缀，免权限访问全部 API）仅限服主使用，请勿外泄；
- 设备免登录采用**设备指纹双因子**（`auto.login.fp.*`，绑定表 `soys_device_binding`）而非 IP 匹配（IP 无法精准到个人设备，同 NAT 下会误伤他人）；
- 本地权限表（`soys_perm_*` / `/soyshttp perm`）与 API 密钥表（`soys_api_key`）属敏感数据，存储于 `data/` 或 SQL 后端，请勿将数据文件夹外传。

---

## 开源注意事项

- 本项目**尚未附带 LICENSE 文件**，开源发布前请先选定许可证（如 MIT / GPL-3.0 需视依赖兼容性）；
- **第三方依赖许可**：`netty-all`（Apache-2.0）、`HikariCP`（Apache-2.0）、`protobuf-java`（BSD-3）、
  `jackson-annotations`（Apache-2.0）、`sqlite-jdbc`（Apache-2.0）、`mysql-connector-java`（GPL-2.0 with FOSS exception）、
  `AuthMe`（GPL-3.0，仅编译期可选）、`BungeeCord API` / `Velocity API`（编译期可选）等，分发前请核对各自许可条款；
- 欢迎 Issue / PR。

---

## 开发文档

面向二次开发者的详细规范（标准教程体例，含示例、参数说明与最佳实践），**中英文档同步维护**：

| 章节 | 中文 | English |
| --- | --- | --- |
| 导言 / Introduction | [docs/zh_CN/导言.md](docs/zh_CN/导言.md) | [docs/en_US/Introduction.md](docs/en_US/Introduction.md) |
| 第1章 概述与快速开始 | [docs/zh_CN/第1章-概述与快速开始.md](docs/zh_CN/第1章-概述与快速开始.md) | [docs/en_US/Chapter-1-Overview-and-Quick-Start.md](docs/en_US/Chapter-1-Overview-and-Quick-Start.md) |
| 第2章 注解式WebAPI开发 | [docs/zh_CN/第2章-注解式WebAPI开发.md](docs/zh_CN/第2章-注解式WebAPI开发.md) | [docs/en_US/Chapter-2-Annotated-WebAPI-Development.md](docs/en_US/Chapter-2-Annotated-WebAPI-Development.md) |
| 第3章 网页与静态资源托管 | [docs/zh_CN/第3章-网页与静态资源托管.md](docs/zh_CN/第3章-网页与静态资源托管.md) | [docs/en_US/Chapter-3-Web-Pages-and-Static-Resources.md](docs/en_US/Chapter-3-Web-Pages-and-Static-Resources.md) |
| 第4章 鉴权与安全管理 | [docs/zh_CN/第4章-鉴权与安全管理.md](docs/zh_CN/第4章-鉴权与安全管理.md) | [docs/en_US/Chapter-4-Authentication-and-Security.md](docs/en_US/Chapter-4-Authentication-and-Security.md) |
| 第5章 数据存储与ORM | [docs/zh_CN/第5章-数据存储与ORM.md](docs/zh_CN/第5章-数据存储与ORM.md) | [docs/en_US/Chapter-5-Data-Storage-and-ORM.md](docs/en_US/Chapter-5-Data-Storage-and-ORM.md) |
| 第6章 进阶能力与最佳实践 | [docs/zh_CN/第6章-进阶能力与最佳实践.md](docs/zh_CN/第6章-进阶能力与最佳实践.md) | [docs/en_US/Chapter-6-Advanced-Capabilities-and-Best-Practices.md](docs/en_US/Chapter-6-Advanced-Capabilities-and-Best-Practices.md) |
| 第7章 事件系统 | [docs/zh_CN/第7章-事件系统（使用与注册）.md](docs/zh_CN/第7章-事件系统（使用与注册）.md) | [docs/en_US/Chapter-7-Event-System.md](docs/en_US/Chapter-7-Event-System.md) |
| 第8章 插件扩展：SoysExpansion 极简注册 | [docs/zh_CN/第8章-SoysExpansion插件扩展.md](docs/zh_CN/第8章-SoysExpansion插件扩展.md) | [docs/en_US/Chapter-8-SoysExpansion-Extension.md](docs/en_US/Chapter-8-SoysExpansion-Extension.md) |
| 附录 API参考手册 | [docs/zh_CN/附录-API参考手册.md](docs/zh_CN/附录-API参考手册.md) | [docs/en_US/Appendix-API-Reference.md](docs/en_US/Appendix-API-Reference.md) |

---

## 免责声明

本项目按“现状”提供，作者不对因使用本插件造成的任何直接/间接损失负责。
HTTP-Over-MC 属实验性技术方案，请在生产环境充分测试后再使用。
