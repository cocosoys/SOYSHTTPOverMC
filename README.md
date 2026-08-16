# SOYSHTTPOverMC

**把 HTTP/HTTPS 架在 Minecraft 的入服端口上** —— 同一 socket 同时服务 MC 玩家、明文 HTTP 与 HTTPS，
无需独立 web 端口、无需端口映射，浏览器直接访问 `https://<服务器地址>:<MC端口>` 即可打开游戏内 Web 服务。

> 1.12.2 Spigot/Paper 插件（Java 8）。含 BungeeCord 端可选代理模块（反向代理 / 群组服出网）。

---

## 特性

- **同端口三协议共存**：在 Spigot 监听 socket 上嗅探首包分流 —— MC 握手原样放行给玩家，HTTP/TLS 就地处理，互不干扰；
- **无头 Bot 隧道**：内置无头 Bot 回环登录本服，经 PluginMessage 通道搬运 HTTP 请求/响应，玩家进服与 Web 服务互不影响；
- **HTTPS 就地升级**：TLS 在嗅探器内动态挂载（无需独立 443），支持 PKCS12 / PEM / 自动自签证书；
- **注解式 REST API**：仿 Spring 的 `@GetMapping` / `@PostMapping` / `@ApiName` / `@ApiPermission` + `AjaxResult`，一行注册端点；
- **安全网关**：TLS 强制（426 升级）、IP 白名单（CIDR）、鉴权（X-API-Key / Bearer / Cookie 会话令牌）、令牌桶限流，策略可插拔；
- **会话令牌**：无状态 JWT（HS256）+ 退出黑名单；离线 cookie 进游戏后自动升级为在线令牌；`/soyshttp key` 可签发服主最高权限 key；
- **群组服支持**：BungeeCord/Waterfall/Velocity 后端自动探测，Bot 携带转发握手数据，跨服请求 `/server/<子服名>/...`，并带可选代理模块（在代理监听端口上反向代理 HTTP/HTTPS）；
- **登录插件接入**：`LoginProvider` SPI —— 已有 AuthMe 实现（网页登录、密码校验、免登录 Bot），可扩展其他登录插件；
- **开发者开放面**：统一门面 `SoysHttpOverMcApi`（注解控制器 / 网页登记 / 目录批量托管 / 门户导航项 / 自定义 MIME / 凭证 / 跨服 HTTP / 日志 / Bot 管理），插件 onEnable 即用；
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
                                                → 无头 Bot 回环 + PluginMessage 隧道
                                                   把响应经 MC 协议发回浏览器
```

群组服模式：每个子服各装一份本插件（各持一个 Bot）；可选 `SOYSHTTPOverMC-Proxy.jar` 装在 BungeeCord，
在代理监听端口上做首包分类与反向代理（home-server=self 由代理自身托管静态页，或路由到指定子服）。

---

## 环境要求

| 项 | 要求 |
|---|---|
| 服务端 | Spigot / Paper **1.12.2**（后端）；BungeeCord / Waterfall（群组服代理） |
| Java | **8**（编译与运行目标） |
| 正版验证 | **`online-mode=false`（离线服必需）** —— Bot 以离线身份回环登录 |
| 可选 | AuthMe（网页登录接入）；BungeeCord 代理模块（群组服出网） |

> ⚠️ 端口约定：**访问端口 == `server.properties` 的 `server-port`**，无需新增端口、无需改防火墙（浏览器访问该端口即可）。
> 若经代理对外，请按需填写 `mc.public-host` / `mc.public-port`（仅影响对外展示的地址）。

---

## 安装与使用

### 单服模式

1. 下载 `SOYSHTTPOverMC.jar`（本仓库 Release）放入 `plugins/`，重启服务器；
2. `server.properties`：`online-mode=false`，`server-port=<你想要的端口>`；
3. 确认日志出现 `HTTP-Over-MC 已启动（同端口嗅探...）`，且 Bot `__http_proxy__` 成功登录；
4. 浏览器访问 `https://<地址>:<端口>/` 打开门户首页（自签证书请手动信任或加 `-k`）。

### 群组服模式（BungeeCord）

1. 每个后端子服都安装 `SOYSHTTPOverMC.jar`（步骤同单服），并在 `config.yml` 填写：
   ```yaml
   mc:
     public-host: "代理公网IP或域名"   # 对外展示用（可选）
     public-port: 25577                # 客户端实际可连的端口
   server-name: 子服名                  # 必须与 BungeeCord config.yml 的 servers.<name> 一致
   proxy-address: "127.0.0.1:25577"    # Bot 经代理连接的地址
   ```
2. 各子服 `spigot.yml` 设 `bungeecord: true`（与代理 `ip_forward: true` 对齐）；防火墙放行代理与回环访问子服端口；
3. （可选）代理装 `SOYSHTTPOverMC-Proxy.jar`，`plugins/SOYSHTTPOverMC-Proxy/proxy.yml` 设 `enabled: true`、
   `home-server: self`（代理自身托管静态页）或某子服名（`/` 与无前缀 `/api/...` 路由到它）；
   代理监听端口由 BungeeCord 的 `config.yml` 决定（如 25577），**无需关心具体端口号**；
4. 跨服访问：`/server/<子服名>/...`（兼容旧写法 `/srv/<子服名>/...`）。

### 配置速览

首次启动自动生成：

```
plugins/SOYSHTTPOverMC/
├── config.yml                 # 核心：bot.username / channel / mc.host/port / public-host / trust-proxy / server-name / proxy-address
├── gateway/
│   ├── config.yml             # 网关总开关 + api-prefix
│   ├── https.yml              # HTTPS：enabled / keystore(PKCS12) > cert+key(PEM) > 自签
│   ├── policies/              # 每个安全策略一个文件：tls.yml / ip-allowlist.yml / auth.yml / rate-limit.yml
│   └── issuers/               # 凭证颁发器：session-token.yml（JWT 会话令牌）
└── data/                      # web 前端解压目录（磁盘热替换）；token-secret.key（JWT 密钥，勿外泄）
```

修改配置后 `/soyshttp reload` 热重载（命令类与注解控制器除外，需重启生效）。

---

## 命令参考（`/soyshttp` 或简写 `/shttp`，默认 op）

```
/soyshttp help [子指令]    查看总览或某子指令的详细用法（参数/示例/注意）
/soyshttp reload           热重载日志级别 + 网关策略与 TLS 配置
/soyshttp key <subject>    为指定主体签发最高权限 key（ak_ 前缀，免权限访问全部 API，请谨慎）
/soyshttp reconnect        主 Bot 重新连接（恢复隧道）
/soyshttp send <url|/page> [显示文字] [玩家]   向玩家发送可点击链接（%url%、&→§、@a/@p/@r/@e/@s）
/soyshttp pages [all]      查看已登记界面（默认仅 .html 页 + 跳转；all 含全部资源/脚本）
/soyshttp api [插件名]     查看已注册的注解式 API 端点
/soyshttp tokens           查询所有已颁发的会话令牌
```

---

## 开发者扩展（第三方插件接入）

在 `plugin.yml` 写 `softdepend: [SOYSHTTPOverMC]`，onEnable 中取门面：

```java
SoysHttpOverMcApi api = HttpOverMcPlugin.getInstance().getApi();

// 1) 注解式 REST API
api.getApiRegistration().registerController(new MyApi());   // @GetMapping("/demo") ...

// 2) 网页 / 静态资源 / 目录 / 门户导航
api.getWebPage().registerPage(this, "/hello", "<h1>Hi</h1>".getBytes());
api.getWebPage().registerDirectory(this, "/", new File(plugin.getDataFolder(), "web/dist"));
api.getWebPage().registerNavItem(this, "我的面板", "/plugins/MyPlugin/demo", "🚀", null, 10);

// 3) 自定义扩展名 Content-Type（.vue / .ts 等）
api.getToolkit().registerMimeType("vue", "text/html; charset=utf-8");

// 4) 凭证 / 工具 / 日志 / 跨服 HTTP / Bot
api.getAuthCredential().issueCredential("someone");
api.getHttpClient().sendGet("https://example.com/api");
```

能力组一览：`ApiRegistration`（注解控制器）、`WebPage`（网页/目录/导航）、`AuthCredential`、
`Toolkit`（JSON / Content-Type）、`Logger`、`BotManagement`、`HttpClient`、`Extension`
（`LoginProvider` 登录插件 SPI + 自定义 `/soyshttp` 子指令）、`CrossServer`（跨服 HTTP 调用）。

监听事件：`ApiAccessEvent` 系列（GET/POST/...）、`GatewayRequestEvent` / `GatewayRequestServedEvent` /
`GatewayAccessDeniedEvent`、`ApiRegisteredEvent` / `ApiUnregisteredEvent` 等（Bukkit 事件，直接监听即可）。

插件禁用时，其名下 API / 网页 / 导航项自动卸载。

---

## 构建

```powershell
# 本地（PowerShell，需 JDK 8 与 Maven 3.9+）
$env:JAVA_HOME = "D:\WorkTools\JDK\8"
& "D:\WorkTools\Maven\apache-maven-3.9.9\bin\mvn.cmd" clean package
# 产物：target/SOYSHTTPOverMC.jar（后端）、proxy/target/SOYSHTTPOverMC-Proxy.jar（代理，单独构建）
```

> ⚠️ 本项目依赖若干**本地魔改的第三方库**（MCProtocolLib / packetlib / opennbt / bungeecord-api），
> 已 vendored 到 `lib/` 并在 GitHub Actions 中 `mvn install:install-file` 后构建（见 `.github/workflows/release.yml`）。
> 打 tag（如 `v1.0.1`）即自动打包两个 jar 并上传到 Release；也可在 Actions 页手动触发（仅出构建产物）。

---

## 安全说明

- 必须在**离线服**（`online-mode=false`）运行；正版服请自行评估 Bot 离线登录的风险；
- 网关策略默认 `tls.yml` 强制 HTTPS（明文 426）；如需开放明文，在 `gateway/policies/tls.yml` 关闭；
- `mc.trust-proxy: true` 时后端信任前置代理注入的 `X-Forwarded-For`；若后端可被客户端直连，建议设 `false` 防伪造 IP；
- 自签证书不获浏览器信任（可自行配置 PKCS12/PEM 正式证书，见 `gateway/https.yml`）；
- `data/token-secret.key` 为 JWT 签名密钥，**请勿外泄**；换服迁移需一并复制（否则旧令牌失效）。

---

## 开源注意事项

- 本项目**尚未附带 LICENSE 文件**，开源发布前请先选定许可证（如 MIT / GPL-3.0 / LGPL-3.0 需视依赖兼容性）；
- **第三方依赖许可**：`MCProtocolLib`（LGPL-3.0）、`packetlib`、`opennbt`（GeyserMC，MIT）、`BungeeCord API`（BSD-3）、
  `AuthMe`（GPL-3.0，仅编译期可选）等，分发前请核对各自许可条款；LGPL 类库的修改版如对外分发需遵循其
  源码可获取性要求（本仓库的修改记录与材料见 `mcpl-patch/`、`packetlib-patch/`，当前未纳入 git）；
- `lib/` 内的魔改 jar 是 CI 构建所必需，**请勿删除**；其来源与改动说明见 `lib/README.md`；
- 仓库 `.gitignore` 已排除 `server*/`（测试环境）、`target/`、日志、脚本与补丁目录——**请勿提交任何
  `token-secret.key`、`*.pem`/`*.p12` 私钥、rcon 密码、服务器内网信息**等敏感内容；
- 欢迎 Issue / PR；涉及协议库修改的 PR 请同时说明补丁来源。

---

## 免责声明

本项目按“现状”提供，作者不对因使用本插件造成的任何直接/间接损失负责。
HTTP-Over-MC 属实验性技术方案，请在生产环境充分测试后再使用。
