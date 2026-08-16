# lib/ —— 本地魔改依赖（CI 构建专用）

这些 jar 是**本地修改过的第三方库**（公共 Maven 仓库无法解析，或同名构件内容不同），
GitHub Actions 在 CI 中通过 `mvn install:install-file` 装入 Maven 本地仓库后再构建主插件。
**请勿删除**；若你重新构建/修改这些库，请同步替换本目录文件。

| 文件 | 坐标 | 说明 | 改动 |
|---|---|---|---|
| `MCProtocolLib-1.12.2-2.jar` | `com.github.steveice10:MCProtocolLib:1.12.2-2` | MC 协议库 | 见 `mcpl-patch/`（AttributeType 增加 GENERIC_REACH_DISTANCE + MagicValues 注册 generic.reachDistance，防 Bot 断连） |
| `packetlib-1.2.jar` | `com.github.steveice10:packetlib:1.2` | 网络库 | 见 `packetlib-patch/`（TcpClientSession 支持 host 含 `\0` 的转发握手，群组服 Bot 用） |
| `opennbt-1.0.jar` | `com.github.steveice10:opennbt:1.0` | NBT 库 | 使用 GeyserMC/OpenNBT 旧版（含 `com.github.steveice10.opennbt.tag.builtin.*` + `NBTIO`），与 MCProtocolLib 1.12.2-2 兼容；Central/JitPack 同名重构版不兼容 |
| `bungeecord-api-1.12.2-build1867-slim.jar` | `net.md_5:bungeecord-api:1.12.2-build1867` | BungeeCord API（proxy 模块用） | 从本地 fat jar 提取 `net/md_5/bungee/api/**` 与 `net/md_5/bungee/config/**`（即官方 bungeecord-api / bungeecord-config 构件内容，BSD 许可）。公共仓库已无 bungeecord-api 1.12.x，故 vendored；netty 由 Central 的 netty-all（provided）提供 |

> `mcpl-patch/` 与 `packetlib-patch/` 含补丁 jar/sources，位于 .gitignore（未提交）；
> 如需在 CI 从源码重建，需先将其纳入仓库或改为从私有仓库发布。

> ⚠️ 开源注意：上述库均有各自许可证（如 MCProtocolLib 为 LGPL-3.0，opennbt 为 MIT 等），
> 分发本插件 jar 前请核对各库许可条款；LGPL 类库的修改版如对外分发需遵循其源码可获取性要求。
