# 第 8 章 插件扩展：SoysExpansion 极简注册

SoysExpansion 是本插件面向**第三方附属插件**推荐的一体化注册入口（类似 PlaceholderAPI 的 Expansion 语义）：继承一个抽象类、覆写声明式钩子、在 `onEnable` 中调用一次 `register()`，即可一次性完成**数据层初始化、端点登记、前端页面托管、CORS 声明与卸载登记**，框架自动识别所属插件（owner）、自动拼接前缀、自动打标签，开发者无需感知 `ApiRegistrationApi` / `WebPageApi` 等内部 API。

## 8.1 核心思想：模板方法 + 声明式钩子

`SoysExpansion` 采用**模板方法模式**：



* `register()` / `unregister()` 为 `final` 骨架，**不可覆写**，内部按固定顺序调用各个可覆写的钩子；

* 所有声明式钩子都有**安全默认值**（默认不注册 / 空操作 / 返回 true），开发者只需要覆写自己需要的部分；

* 注册过程**任一环节失败自动回滚**已成功部分，并返回 `false`。



```
public abstract class SoysExpansion {

    public abstract String getIdentifier();   // 唯一必填

    public final boolean register()   { ... } // final 骨架，自动按序执行

    public final boolean unregister() { ... }

}
```

## 8.2 最小接入

### 8.2.1 plugin.yml



```
name: MyShop

main: com.example.myshop.MyShop

version: 1.0.0

api-version: 1.13

softdepend: [SOYSHTTPOverMC]   # 弱依赖：没有主插件也能加载，只是不注册
```

### 8.2.2 扩展类



```
package com.example.myshop;

import com.github.cocosoys.mc.soyshttpovermc.api.SoysExpansion;

public class ShopExpansion extends SoysExpansion {

    @Override

    public String getIdentifier() { return "shop"; }   // 唯一必填元信息

}
```

### 8.2.3 onEnable 一行注册



```
@Override

public void onEnable() {

    if (!new ShopExpansion().register()) {

        getLogger().warning("ShopExpansion 注册失败（identifier 冲突？主插件未就绪？）");

    }

}

@Override

public void onDisable() {

    new ShopExpansion().unregister();   // 精确反注册（框架亦按 owner 插件名兜底清理）

}
```

此时该扩展已注册（注册表内可查，`SoysExpansion.registered()`），但未声明任何端点 / 页面 / 数据，因此**除了占用一个 identifier 外不产生任何路由**—— 所有能力都来自下面的可选钩子。

## 8.3 元信息钩子



| 钩子                | 默认         | 说明                                                                    |
| ----------------- | ---------- | --------------------------------------------------------------------- |
| `getIdentifier()` | **必填（抽象）** | 扩展唯一标识：冲突检测（重复 identifier 拒绝注册）、页面 tag（`expansion:<identifier>`）、卸载依据 |
| `getAuthor()`     | `""`       | 作者信息（文档 / 调试展示，可选）                                                    |
| `getVersion()`    | `""`       | 版本信息（文档 / 调试展示，可选）                                                    |

## 8.4 端点注册（buildControllers /buildProxyControllers）

端点来源是**两个可覆写的 "实例列表" 钩子**，注册与注销共用同一来源：



| 钩子                        | 默认              | 登记方式                                 | 路由形态                     |
| ------------------------- | --------------- | ------------------------------------ | ------------------------ |
| `buildControllers()`      | `[this]`（扩展类自身） | **正常登记**（`registerCommonController`） | `/api/plugins/<插件名>/...` |
| `buildProxyControllers()` | `[]`（无）         | **代理登记**（`registerProxyController`）  | `/api/...`（无插件段，以主插件名义）  |

### 8.4.1 端点在扩展类上书写（默认即生效）



```
public class ShopExpansion extends SoysExpansion {

    @Override

    public String getIdentifier() { return "shop"; }

    @GetMapping("/items")

    public AjaxResult items(@RequestParam(name = "page", defaultValue = "1") int page) {

        return AjaxResult.success("...");

    }

}
```

默认 `buildControllers()` 返回 `[this]`，因此扩展类自身被正常登记 → `GET /api/plugins/你的插件名/items`。

### 8.4.2 端点在独立 Controller 类时批量登记



```
@Override

protected List\<Object> buildControllers() {

    return Arrays.asList(new ShopAdminController(), new ShopQueryController());

}
```

### 8.4.3 需要无前缀路径（如若依风格 `/api/prod-api/*`）



```
@Override

protected List\<Object> buildProxyControllers() {

    return Arrays.asList(new ShopApiController());

}
```

> 两个来源
>
> **可同时使用**
>
> ：同一实例也可以同时出现在 
>
> `buildControllers()`
>
>  与 
>
> `buildProxyControllers()`
>
> （登记两次、路径不同）；注销按实例幂等，先清后空操作无害。
> 若需完全自定义 "先正常后代理 / 只代理" 等顺序，可分别覆写 
>
> `registerCommonController()`
>
>  / 
>
> `registerProxyController()`
>
> （见 8.10），
>
> **无需重写 **
>
> `registerControllers()`
>
> ** 聚合方法**
>
> 。

## 8.5 前端页面托管（resourceRoot）

`resourceRoot()` 返回插件 jar 内前端资源根（如 `"dist"`），非空时 `registerPages()` 自动托管：



```
plugins/<插件名>/\<resourceRoot>   磁盘优先（惰性登记：请求时才读盘，支持热替换）

   ↓ 目录不存在时回退

jar 内 /\<resourceRoot>            打包默认目录
```

托管效果：



* 页面 URL：`/web/plugins/<插件名>/**`（与 `pageFullPrefix` 一致）；

* 登记的所有页面统一打 `expansion:<identifier>` tag，卸载时按 tag 精确清理（含目录索引 / SPA 回退规则）；

* 页面中的 `__SOYS_CONTEXT__.js` 契约文件自动注入当前服务器环境原语（scheme /host/port / 各类前缀，详见第 3 章），前端无需关心部署环境。



```
@Override

protected String resourceRoot() { return "dist"; }   // 自动托管 dist
```

配套的索引 / SPA 钩子：



| 钩子                       | 默认        | 说明                                                                                                                                        |
| ------------------------ | --------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `indexFallbackEnabled()` | `true`    | 访问 `/web/plugins/<插件名>`（或带尾部斜杠）且未命中时，302 自动导航到 `…/<indexFile>`；`false` 则 404                                                              |
| `indexFile()`            | `"index"` | 目录索引兜底目标（不含扩展名；经 .html 智能匹配命中 `index.html`）                                                                                               |
| `spaFallback()`          | `true`    | history 模式 SPA 回退：`<无扩展名路径>` 未命中 → 回退该插件根下 `index.html`（HTTP 200，交由前端 vue-router 判定）；**带扩展名路径**（.js/.css/…）未命中保持 404、绝不回退（两层 404，详见第 3 章） |

> 三个钩子仅在 
>
> `resourceRoot()`
>
>  非空时生效。

## 8.6 CORS 声明（cors）

`cors()` 返回 `CorsSpec[]`，非空时 `registerCors()` 逐条自动注册；默认 `null` = 无 CORS。



```
@Override

protected CorsSpec[] cors() {

    return new CorsSpec[]{

        new CorsSpec("/api", "\*"),                          // 2 参简写：路径前缀 + 允许来源

        new CorsSpec("/admin", "https://example.com",

                     "GET,POST", "Authorization,Content-Type", true) // 5 参全量

    };

}
```

`CorsSpec` 字段：`pathPrefix`（路径前缀，空或 `"/"` = 全局）、`origin`、`methods`、`headers`、`credentials`。

## 8.7 数据层自动初始化（dataRoots /sqlRoots/seedData /schemaVersion）

四个声明式钩子共同完成 "数据就绪"（在端点注册**之前**执行，保证端点上线时数据已可用），受主插件 `config.yml` 的 `auto.ops.*` 开关控制：



| 钩子                | 默认     | 语义                                                                                                                                          |
| ----------------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `dataRoots()`     | `null` | jar 内默认数据文件根（如 `"data"`）：自动复制其下所有文件到数据文件夹同名相对目录，**已存在不覆盖**（保留运维 / 运行期修改）                                                                    |
| `sqlRoots()`      | `null` | jar 内 SQL 初始化脚本根（如 `"sql"`）：自动执行其下 `init.sql`——**仅 MySQL 方言后端**（SQLite 语法不兼容且运行时已自动建表）；脚本须幂等（IF NOT EXISTS / IGNORE）                        |
| `seedData()`      | `null` | 种子数据（实体实例列表）：按类分组，**对应表为空**才批量插入（幂等）                                                                                                        |
| `schemaVersion()` | `0`    | 当前 schema 版本：`0` = 仅 init.sql + 种子，无版本迁移；`>=1` 时按约定执行迁移脚本 `sql/migrations/V<n>__<描述>.sql`（n 从 1 起）：全新安装执行 `V1..V(schemaVersion)`，已安装按记录增量执行 |



```
@Override

protected String[] dataRoots()      { return new String[]{"data"}; }

@Override

protected String[] sqlRoots()       { return new String[]{"sql"}; }

@Override

protected int schemaVersion()       { return 2; }   // 会执行 V1\_\_...sql 与 V2\_\_...sql
```

## 8.8 注册顺序与失败回滚

`register()`（final）按以下顺序执行，**任一环节失败 / 抛异常 → 回滚已成功部分并返回 **`false`：



```
1\. 数据层初始化  registerData()        （dataRoots 复制 + sqlRoots 执行 + seedData 种子 + schemaVersion 迁移）

2\. 端点登记      registerControllers()  （registerCommonController() 遍历 buildControllers()

                                        → registerProxyController() 遍历 buildProxyControllers()）

3\. 页面托管      registerPages()         （resourceRoot 磁盘优先惰性 / jar 回退，打 expansion:\<id> tag，

                                          设置目录索引与 SPA 回退规则）

4\. CORS          registerCors()          （逐条登记 cors()）

5\. 回调          onRegister()            （默认 true；返回 false 或抛异常同样回滚）
```

`unregister()`（final）按相反方向摘除：页面（按 tag）→ 代理端点 → 正常端点 → CORS → `onUnregister()` 回调（此时已从注册表移除）。`unregister()`** 只摘登记，永不删除数据**（卸载数据请走 `/soyshttp data` 数据层自动化运维）。

## 8.9 生命周期回调



| 钩子               | 默认     | 调用时机                                  |
| ---------------- | ------ | ------------------------------------- |
| `onRegister()`   | `true` | 注册链最后一步：返回 `false` 或抛异常 → 回滚本次注册并视为失败 |
| `onUnregister()` | 空      | 反注册链最后一步：此时已从注册表移除                    |

## 8.10 细粒度覆写（模板方法分层）

如果默认的 "整类注册" 粒度不够，可覆写**单类钩子**，不影响其它类型：



* 端点：`registerControllers()`（聚合）→ `registerCommonController()` / `registerProxyController()`（各自遍历对应实例列表）；

* 页面：`registerPages()`（可完全替换默认托管逻辑，如手动错开 basePath 托管多套前端）；

* CORS：`registerCors()`；

* 反注册：`unregisterControllers()` / `unregisterController()` / `unregisterProxyController()` / `unregisterPages()`。



```
// 示例：既想正常登记又想代理登记时，分别覆写两个子钩子即可，无需重写聚合方法

@Override

protected boolean registerCommonController() { /\* 只注册 buildControllers() \*/ }

@Override

protected boolean registerProxyController()  { /\* 只注册 buildProxyControllers() \*/ }
```

## 8.11 静态辅助



| 方法                                               | 说明                                                                    |
| ------------------------------------------------ | --------------------------------------------------------------------- |
| `SoysExpansion.bootstrap(SoysHttpOverMcApi api)` | 由主插件在启动完成后调用，建立全局 API 引用（附属插件注册前主插件自动完成；一般无需开发者调用）                    |
| `SoysExpansion.registered()`                     | 返回当前已注册扩展表（`ConcurrentHashMap<String, SoysExpansion>`，键 = identifier） |
| `getOwner()`（final）                              | 所属插件实例（`JavaPlugin.getProvidingPlugin(getClass())` 自动识别）              |
| `getApi()`（final）                                | 门面 API（主插件未就绪时为 null）                                                 |

## 8.12 与门面 API 的关系

`SoysExpansion` 内部正是**门面 API（**`SoysHttpOverMcApi`** 7 个能力组）的组合调用**：数据层走 `DataRegistration`、端点走 `ApiRegistration`、页面走 `WebPage`、CORS 走 WebRegistry。因此：



* 90% 的接入场景用 `SoysExpansion`（一行注册、自动前缀 /tag/ 卸载）；

* 高级场景（动态注册 / 非 Expansion 形态 / 需要 `force=true` 覆盖等）仍可直接使用门面 API（见第 2 章 2.7、第 3 章、附录）。

## 8.13 常见问题

**Q1：**`register()`** 返回 false？**

重复 identifier（已注册过同名扩展）或主插件未就绪（`getApi()` 为 null）。可在日志中打印 `registered()` 检查冲突来源。

**Q2：页面前缀为什么是 **`/web/plugins/<插件名>`** 而不是 identifier？**

页面 URL 前缀按 **owner 插件名**（`getProvidingPlugin`）计算，与 identifier 无关 —— 同一个插件即使注册多个 Expansion 实例，它们也共享同一页面前缀，**不要在同一插件内托管两份含 **`index.html`** 的前端**（`index` / SPA 回退规则按插件名注册，后注册覆盖先注册）。

**Q3：插件禁用时还需要手动 **`unregister()`** 吗？**

建议在 `onDisable` 调用（精确、即时）；框架也会按 owner 插件名兜底清理该插件名下的端点 / 页面 / CORS。

**Q4：**`unregister()`** 会删数据吗？**

不会。数据层自动化运维（初始化 / 更新 / 重装 / 卸载）由 `/soyshttp data <插件> ...` 与 `soys_schema_meta` 版本记录负责，与注册表解耦。

**Q5：磁盘 dist 与 jar 内 dist 哪个优先？**

磁盘优先：`plugins/<插件名>/<resourceRoot>` 存在即惰性登记磁盘目录（支持热替换），不存在时回退 jar 内同名资源目录。