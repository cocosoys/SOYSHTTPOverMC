package com.github.cocosoys.mc.soyshttpovermc.api;

import com.github.cocosoys.mc.soyshttpovermc.orm.DataSpec;
import com.github.cocosoys.mc.soyshttpovermc.web.DataHandle;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Set;

/**
 * 能力组 7：数据层自动化运维注册（委托 {@code AutoOps} + meta 表 {@code soys_schema_meta}）。
 * 由 {@link SoysHttpOverMcApi#getDataRegistration()} 跳转获取。
 *
 * <p><b>定位</b>：第三方插件（含 {@code SoysExpansion} 附属插件）只需声明数据包
 * {@link DataSpec}（默认文件 / init.sql / 迁移 / 种子 / schema 版本），
 * 经本接口完成<b>安装 / 更新 / 重装 / 清理</b>全事务，无需了解 SQL/YAML 双端细节。</p>
 *
 * <p><b>生命周期语义</b>：</p>
 * <ul>
 *   <li>{@link #register} —— 自动识别：meta 无记录 → 全新安装；有记录（如卸载后重装）→
 *       保留数据更新（补默认文件 / 补列 / 版本迁移 / 种子补缺）；</li>
 *   <li>{@link #unregister} —— 摘登记（数据保留，meta 不动）；</li>
 *   <li>{@link #purge} —— 显式清理（DROP / 删 YAML 文件 + 删 meta；他属校验，须二次确认）；</li>
 *   <li>{@link #reinstall} —— {@code purgeFirst=false} 保留数据重装 / {@code true} 清空重装。</li>
 * </ul>
 */
public interface DataRegistrationApi {

    /**
     * 登记数据包并执行安装 / 更新（自动按 meta 识别；受 config {@code auto.ops.*} 控制）。
     *
     * @param owner 所属插件（取其 ClassLoader 定位 jar 内 data/sql 资源）
     * @param spec  数据包描述（pluginName 必填）
     * @return 成功返回 {@link DataHandle}；失败返回 null（原因经日志输出）
     */
    DataHandle register(Plugin owner, DataSpec spec);

    /**
     * 摘登记（卸载运行时数据句柄）：数据保留、meta 不动——重装同标识自动走"保留数据更新"。
     *
     * @return true=已摘除；false=句柄未登记 / 不存在
     */
    boolean unregister(DataHandle handle);

    /**
     * 显式清理数据包（purge）：按 meta 识别归属表 → DROP / 删 YAML 文件 → 删 meta 行。
     * <b>不可逆</b>，调用方必须二次确认；表仍被其它插件占用时拒绝。
     *
     * @return true=清理完成；false=失败（他属拒绝 / 异常，原因经日志输出）
     */
    boolean purge(DataHandle handle);

    /**
     * 重装数据包。
     *
     * @param purgeFirst true=清空重装（先 {@link #purge} 再安装）；false=保留数据重装（仅补缺/迁移）
     * @return true=成功
     */
    boolean reinstall(DataHandle handle, boolean purgeFirst);

    /**
     * 是否已安装（存在插件级 meta 记录）。
     */
    boolean isInstalled(String pluginName);

    /**
     * 按插件名取已登记句柄（未登记返回 null）。
     */
    DataHandle handleOf(String pluginName);

    /**
     * 已登记数据句柄的插件名清单。
     */
    List<String> registeredNames();

    /**
     * 显式执行数据更新（按已登记 spec：补复制 / 补列 / 迁移到 spec.schemaVersion / 种子补缺）。
     * 未登记句柄返回 false（此类插件仅随启动自动更新）。
     */
    boolean update(String pluginName);

    /**
     * 显式迁移到指定目标版本（临时覆盖 spec.schemaVersion；须已登记句柄）。
     */
    boolean update(String pluginName, int targetVersion);

    /**
     * 按名摘登记（数据保留、meta 不动）。
     */
    boolean unregister(String pluginName);

    /**
     * 按名重装（保留数据：补缺 + 迁移 + 种子补缺 + meta 刷新）。
     */
    boolean reinstall(String pluginName);

    /**
     * 某插件归属的表名集合（不含插件级行）；供运维查看 / 二次确认。
     */
    Set<String> tablesOf(String pluginName);
}
