# SOYSHTTPOverMC · 版本兼容模块统一规范

> 本文档定义所有版本兼容模块（`adapter/common`、`adapter/v1_6x`、`adapter/v1_7x`…）的
> **统一样式**：目录 / 包结构 / 命名 / 编译基线 / 构建 / 接入约定。
> 任何新版本模块必须严格遵循本文，否则视为不合规。

---

## 1. 目录与模块约定

```
adapter/
├── pom.xml             # 聚合工程（packaging=pom）：modules = common / v1_6x / v1_7x
├── common/             # 公共底座（本模块）：统一规范 + 版本中立兼容工具 + SPI 契约
│   ├── pom.xml         # artifactId = soyshttpovermc-adapter-common；编译基线 = 最低版本 craftbukkit
│   └── lib/            # vendored craftbukkit-1.6.4.jar（本模块自包含，编译依赖）
├── v1_6x/              # 1.6.x 适配模块（最低 1.6.4）：依赖 adapter/common + 自包含 lib/craftbukkit-1.6.4.jar
└── v1_7x/              # 1.7.x 适配模块（1.7.10）：依赖 adapter/common + 自包含 lib/craftbukkit-1.7.10.jar
```

- **公共底座** `adapter/common`：不得包含任何具体版本的特有实现；只放「所有版本通用」的东西。
- **版本模块** `adapter/v1_6x` / `adapter/v1_7x`：各自实现 `spi` 契约，只写本版本差异。
- **lib/（各模块自包含）**：低版本 API jar 一律 vendor 到**各模块自己的** `lib/` 目录
  （官方 Maven 仓库已无 1.6/1.7 构件，见 §4），pom 用 `${project.basedir}/lib` 定位。

## 2. 包结构约定

所有兼容模块统一使用包根：

```
com.github.cocosoys.mc.soyshttpovermc.adapter
├── AdapterConstants        # 统一常量（插件名 / 产物命名 / api-prefix）
├── ServerVersion           # 版本探测 / 解析 / 区间判定（1.7.10、1.6.4…）
├── compat/                 # 版本中立兼容工具（编译到最低版本，差异全反射）
│   ├── BukkitCompat        # getOnlinePlayers 等跨版本 API 反射双通道
│   ├── ChatCompat          # 可点击消息：有 Spigot API 用之，否则纯文本降级
│   └── PlayerIdentity      # 玩家身份键：1.6=name，1.7.10+=UUID 优先（反射）
└── spi/                    # 版本模块必须/可实现的 SPI 契约
    ├── AdapterActivator    # 统一激活入口（id / supports / activate / deactivate）
    ├── SocketSnifferAdapter# 嗅探器版本桥（supported / locateListenerChannels）
    └── AdapterActivators   # ServiceLoader 发现 / 匹配助手
```

> 禁止在 `adapter/*` 中新建其它包根；新增能力放 `compat` 或 `spi`。

## 3. 命名约定

| 场景 | 规范 | 示例 |
|---|---|---|
| 版本模块目录 | `v<major>_<minor>x`（x=含补丁段） | `v1_6x`、`v1_7x` |
| 模块 artifactId | `soyshttpovermc-adapter-<module>` | `soyshttpovermc-adapter-v1_6x` |
| 版本实现类前缀 | `V<major>_<minor>` | `V1_7SocketSnifferAdapter` |
| SPI 实现命名 | `<前缀> + <契约名>` | `V1_7AdapterActivator` |
| 产物 jar | `SOYSHTTPOverMC-<版本段>-<revision>.jar` | `SOYSHTTPOverMC-1_6-1.2.0.jar` |
| 版本探测 | 统一用 `ServerVersion` | `ServerVersion.current()` |

## 4. 编译基线（硬约束）

1. **`adapter/common` 以最低版本（1.6.4）craftbukkit API 编译**；版本模块以各自目标版本编译。
   → 保证公共底座被所有低版本模块复用，且不触碰高版本独有 API。
2. **所有跨版本差异一律反射**：禁止直接 `import` / 调用仅在部分版本存在的方法或类。
   反例（会编译期绑定，运行时 NoSuchMethodError）：`Bukkit.getOnlinePlayers()`（1.8+ 返回 Collection）、
   `Player.getUniqueId()`（1.7.2+ 才有）、`Player.spigot()`（仅 Spigot，CraftBukkit 无）。
   正例：`BukkitCompat.onlinePlayers()`、`PlayerIdentity.key(p)`、`ChatCompat.sendClickable(...)`。
3. **禁止引用 NMS / craftbukkit 内部**（`net.minecraft.server.*`、`org.bukkit.craftbukkit.*`）。
   嗅探器对服务端内部的反射，一律收敛到 `SocketSnifferAdapter`，由各版本模块实现。
4. **Java 目标统一 1.8**（低版本服务端也运行于 Java 8）。

## 5. 构建约定

```powershell
# ① 先确保主工程 common/core 已构建并安装（adapter 版本模块 shade 时会依赖）
$env:JAVA_HOME = "D:\WorkTools\JDK\8"
& "D:\WorkTools\Maven\apache-maven-3.9.9\bin\mvn.cmd" -f common\pom.xml install

# ② 构建 adapter/common（独立验证，不依赖根 reactor 注册）
& "D:\WorkTools\Maven\apache-maven-3.9.9\bin\mvn.cmd" -f adapter\common\pom.xml clean install

# ③ 构建某个版本模块（示例）
& "D:\WorkTools\Maven\apache-maven-3.9.9\bin\mvn.cmd" -f adapter\v1_7x\pom.xml clean package
```

- **低版本 API 无官方 Maven 构件**（hub.spigotmc.org 仅 1.12.2+ 存活）：
  1.6.4 / 1.7.10 的 `craftbukkit.jar` 已 vendor 到**各模块自己的 `lib/`**（如 `adapter/common/lib/`），
  pom 用 `system` scope + `${project.basedir}/lib` 引用。CI 若改用 `install:install-file`，
  请保持与 README 主文档一致的 vendored 说明。
- 默认根构建（`mvn package`）**不受影响**；adapter 模块在版本模块就绪前不注册进根 reactor modules。

## 6. 接入约定（运行时）

1. **版本激活**：版本模块实现 `AdapterActivator` 并经 `META-INF/services` 注册；
   插件启动时用 `AdapterActivators.findFor(ServerVersion.current())` 找到匹配实现并 `activate`。
2. **Platform 覆盖**：需要差异的平台能力（调度 / YAML / 配置）走主工程 common 既有
   `Platform` SPI 的 ServiceLoader 覆盖机制（版本模块注册 `Platform` 实现，优先于 core 兜底）。
3. **嗅探器**：`SocketSnifferAdapter.supported()==false`（如 1.6.4 无 Netty）时，
   插件**禁止**启用同端口嗅探，强制 `standalone-server` 独立端口模式。
4. **身份**：低版本（1.6.4）无 UUID API，玩家身份统一用 `PlayerIdentity.key()`（name 回退）。

## 7. 各版本能力差异速查（实现依据）

| 能力 | 1.6.4 | 1.7.10 | 说明 |
|---|---|---|---|
| `Bukkit.getOnlinePlayers()` | `Player[]` 仅此 | `Player[]`+`Collection` 并存 | 必须反射取 Collection（1.8+ 风格） |
| `Player.getUniqueId()` / `OfflinePlayer` | ❌ 无 | ✅ | 1.6 用 name 做身份键 |
| `Player.spigot()` + bungee chat | ❌（CraftBukkit 均无） | ❌ | 可点击消息须反射探测 + 纯文本降级 |
| 服务器网络栈 | 旧阻塞 IO（无 Netty） | Netty（1.7.2+） | 1.6 无法同端口嗅探，只走独立端口 |
| `YamlConfiguration` / 调度 / OP | ✅ | ✅ | 低版本均有，无需特化 |

## 8. 冒烟测试约定

低版本测试环境已就位于 `servers/`：

- `servers/server-1.6.4/craftbukkit.jar`（最低目标）
- `servers/server-1.7.10/craftbukkit.jar`
- `servers/server-1.8.8` / `server-1.12.2` / `server-1.16.5`（其余档位）

版本模块完成后，把产物放入对应 `servers/server-<版本>/plugins/` 起服验证：
进服 → 访问 `/`（或独立端口）→ 登录 → 网关限流 → 权限鉴权，逐项冒烟。

## 9. 字节码合规验证（每模块必做）

`verify-bytes.ps1` 对构建产物做反编译检查，确认**没有直接绑定高版本独有 API**（只允许反射字符串）：

- `BukkitCompat`：`getOnlinePlayers` 直接调用数必须为 0；
- `ChatCompat`：`net.md_5.bungee` 直接引用数必须为 0；
- `PlayerIdentity`：`OfflinePlayer.getUniqueId` 直接调用数必须为 0。

```powershell
powershell -ExecutionPolicy Bypass -File adapter\common\verify-bytes.ps1
```

版本模块（v1_6x / v1_7x）构建后同样运行本脚本做合规检查。
