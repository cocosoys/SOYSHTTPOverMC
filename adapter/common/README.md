# SOYSHTTPOverMC · 版本兼容模块统一规范

> 本文档定义所有版本兼容模块（`adapter/common`、`adapter/v1_6x`、`adapter/v1_7x`、`adapter/v1_16x`、
> `adapter/v1_20x`、`adapter/v1_21x`、`adapter/v1_26x`…）的
> **统一样式**：目录 / 包结构 / 命名 / 编译基线 / 构建 / 接入约定。
> 任何新版本模块必须严格遵循本文，否则视为不合规。

---

## 1. 目录与模块约定

```
adapter/
├── pom.xml             # 聚合工程（packaging=pom）：modules = common / v1_6x / v1_6xJava7 / v1_7x / v1_16x / v1_20x / v1_21x / v1_26x
├── common/             # 公共底座（本模块）：统一规范 + 版本中立兼容工具 + SPI 契约
│   ├── pom.xml         # artifactId = soyshttpovermc-adapter-common
│   └── src/            # 零 Bukkit 依赖：所有版本差异均以反射字符串调用，不 import 任何 Bukkit 类
├── v1_6x/              # 1.6.x 适配模块（最低 1.6.4）：依赖 adapter/common + 自包含 lib/craftbukkit-1.6.4.jar
├── v1_6xJava7/         # 1.6.x Java 7 兼容产物（retrolambda/backport 处理，独立构建链）
├── v1_7x/              # 1.7.x 适配模块（1.7.10）：依赖 adapter/common + 自包含 lib/craftbukkit-1.7.10.jar
├── v1_16x/             # 1.13 – 1.16.5：Java 8 编译 + api-version: 1.13（spigot-api 来自 Maven 仓库）
├── v1_20x/             # 1.17 – 1.20.4：Java 17 编译（spigot-api 来自 Maven 仓库）
├── v1_21x/             # 1.20.5 – 1.21.x：Java 21 编译（spigot-api 来自 Maven 仓库）
└── v1_26x/             # 1.26.x：JDK 25 编译（自声明 Lombok 1.18.38；toolchains 绑定 JDK25）
```

- **公共底座** `adapter/common`：不得包含任何具体版本的特有实现，**不得出现任何对 Bukkit 的引用**——
  跨版本差异全部以反射字符串调用（见 §4）；只放「所有版本通用」的东西。
- **版本模块** `adapter/v1_6x` / `v1_7x` / `v1_16x` / …：各自实现 `spi` 契约，只写本版本差异。
- **lib/（仅低版本模块自包含）**：官方 Maven 仓库已无 1.6/1.7 构件（见 §4），
  `v1_6x` / `v1_7x` / `v1_6xJava7` 各自 vendor `craftbukkit` 到**自己的** `lib/`，pom 用 `${project.basedir}/lib` 定位；
  `v1_16x` 起 spigot-api 可从 Maven 仓库直接获取，无需 lib/。

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
| 产物 jar | `SOYSHTTPOverMC-<版本段>-<revision>.jar` | `SOYSHTTPOverMC-1_6-1.4.0.jar` |
| 版本探测 | 统一用 `ServerVersion` | `ServerVersion.current()` |

## 4. 编译基线（硬约束）

1. **`adapter/common` 零 Bukkit 依赖**：不 import 任何 Bukkit / craftbukkit 类，跨版本调用一律反射
   （`Class.forName(...)` + `getMethod(...)` 字符串），编译只依赖 JDK 与主工程 common/core 构件。
2. **版本模块以各自目标版本编译**：`v1_6x`/`v1_7x` 用各自 vendored craftbukkit，`v1_16x` 起用 Maven 仓库 spigot-api。
   → 保证公共底座被所有版本模块复用，且不触碰高版本独有 API。
3. **跨版本差异一律反射**：禁止直接 `import` / 调用仅在部分版本存在的方法或类。
   反例（会编译期绑定，运行时 NoSuchMethodError）：`Bukkit.getOnlinePlayers()`（1.8+ 返回 Collection）、
   `Player.getUniqueId()`（1.7.2+ 才有）、`Player.spigot()`（仅 Spigot，CraftBukkit 无）。
   正例：`BukkitCompat.onlinePlayers()`、`PlayerIdentity.key(p)`、`ChatCompat.sendClickable(...)`。
4. **禁止引用 NMS / craftbukkit 内部**（`net.minecraft.server.*`、`org.bukkit.craftbukkit.*`）。
   嗅探器对服务端内部的反射，一律收敛到 `SocketSnifferAdapter`，由各版本模块实现。
5. **Java 目标**：默认统一 1.8（低版本服务端运行于 Java 8）；`v1_26x` 例外——JDK 25 编译
   （toolchains.xml 绑定 JDK25，模块自声明 Lombok 1.18.38），其余模块不受影响。

## 5. 构建约定

```powershell
# ① 全量构建（根 reactor 已注册 adapter 聚合模块）：core/common 先编译，随后 adapter 各档位
& "D:\WorkTools\Maven\apache-maven-3.9.9\bin\mvn.cmd" -B clean package -DskipTests -Drevision=1.4.0

# ② 仅 adapter 聚合（含各版本模块；toolchains 自动选 JDK，无需手动切 JAVA_HOME）
& "D:\WorkTools\Maven\apache-maven-3.9.9\bin\mvn.cmd" -f adapter\pom.xml clean package

# ③ 单个版本模块（示例：v1_7x，依赖 common/core 已 install 到本地仓库）
& "D:\WorkTools\Maven\apache-maven-3.9.9\bin\mvn.cmd" -f adapter\v1_7x\pom.xml clean package
```

- **低版本 API 无官方 Maven 构件**（hub.spigotmc.org 仅 1.12.2+ 存活）：
  1.6.4 / 1.7.10 的 `craftbukkit.jar` 已 vendor 到 `v1_6x` / `v1_7x` / `v1_6xJava7` 各自的 `lib/`，
  pom 用 `system` scope + `${project.basedir}/lib` 引用；`v1_16x` 起 spigot-api 直接走 Maven 仓库。
- **多 JDK**：根 `.mvn/maven.config` 与项目内 `toolchains.xml` 已声明 JDK8 / JDK17 / JDK21 / JDK25，
  各版本模块的 maven-compiler-plugin 绑定对应 toolchain，一条命令全量过（无需手动切 JAVA_HOME）；
  CI（GitHub Actions release.yml）按 job matrix 用 `setup-java` 提供对应 JDK。
- **产物命名**：`SOYSHTTPOverMC-<版本段>-<revision>.jar`（如 `SOYSHTTPOverMC-1_6-1.4.0.jar`、`-1_7-`、`-1_16-`、`-1_20-`、`-1_21-`、`-1_26-`）；v1_6xJava7 产物为 `SOYSHTTPOverMC-1_6Java7-<revision>-java7.jar`。

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

> 更高档位（v1_16x / v1_20x / v1_21x / v1_26x）差异集中在：`api-version` 字段、Java 编译目标
> （17 / 21 / 25）、spigot-api 依赖来源（Maven 仓库）、以及各版本 NMS 内部结构变化
> （仍由 `SocketSnifferAdapter` + 反射收敛）；其余能力与 1.7.10+ 一致。

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
