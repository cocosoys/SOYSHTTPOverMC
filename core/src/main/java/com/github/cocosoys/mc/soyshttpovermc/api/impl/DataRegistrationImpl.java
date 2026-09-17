package com.github.cocosoys.mc.soyshttpovermc.api.impl;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.web.DataHandle;
import com.github.cocosoys.mc.soyshttpovermc.api.DataRegistrationApi;
import com.github.cocosoys.mc.soyshttpovermc.orm.AutoOps;
import com.github.cocosoys.mc.soyshttpovermc.orm.DataSpec;
import com.github.cocosoys.mc.soyshttpovermc.orm.SchemaRegistry;
import lombok.CustomLog;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 能力组 7 实现：数据层自动化运维（委托 {@link AutoOps} 事务 + {@link SchemaRegistry} meta）。
 */
@CustomLog
public final class DataRegistrationImpl implements DataRegistrationApi {

    private final HttpOverMcPlugin hostPlugin;
    private final Map<String, DataHandle> handles = new ConcurrentHashMap<>();

    public DataRegistrationImpl(HttpOverMcPlugin hostPlugin) {
        this.hostPlugin = hostPlugin;
    }

    @Override
    public DataHandle register(Plugin owner, DataSpec spec) {
        if (owner == null) {
            log.warnT("log.datareg.fail", "[数据注册] owner 为空，拒绝登记");
            return null;
        }
        if (spec == null || spec.getPluginName() == null || spec.getPluginName().trim().isEmpty()) {
            log.warnT("log.datareg.fail", "[数据注册] DataSpec/pluginName 为空，拒绝登记");
            return null;
        }
        String name = spec.getPluginName().trim();
        String err = AutoOps.install(hostPlugin.getPlatform(), owner.getClass().getClassLoader(), spec);
        if (err != null) {
            // 失败策略（auto.ops.fail）：disable → 仅禁用失败的对应插件（本附属插件），
            // 主插件及其它插件继续运行；block / warn → 返回 null，由调用方自行处理。
            String failAct = AutoOps.failAction(hostPlugin.getPlatform());
            if ("disable".equalsIgnoreCase(failAct)) {
                try {
                    owner.getServer().getPluginManager().disablePlugin(owner);
                    log.warnT("log.datareg.disable",
                            "[数据注册] {0} 安装/更新失败，auto.ops.fail=disable，已禁用该插件: {1}",
                            name, err);
                } catch (Exception ex) {
                    log.warnT("log.datareg.disable-failed",
                            "[数据注册] {0} 安装/更新失败（禁用该插件失败）: {1}", name, err);
                }
            } else {
                log.warnT("log.datareg.fail", "[数据注册] {0} 安装/更新失败: {1}", name, err);
            }
            return null;
        }
        DataHandle h = new DataHandle(owner, spec);
        handles.put(name, h);
        return h;
    }

    @Override
    public boolean unregister(DataHandle handle) {
        if (handle == null || !handle.isRegistered()) {
            return false;
        }
        String name = handle.getSpec().getPluginName();
        handle.markUnregistered();
        handles.remove(name);
        log.infoT("log.datareg.unregistered",
                "[数据注册] {0} 已摘登记（数据保留，meta 未动；重装自动走保留数据更新）", name);
        return true;
    }

    @Override
    public boolean purge(DataHandle handle) {
        if (handle == null || !handle.isRegistered()) {
            log.warnT("log.datareg.fail", "[数据注册] purge 失败：句柄未登记");
            return false;
        }
        String err = AutoOps.purge(hostPlugin.getPlatform(), handle.getSpec());
        if (err != null) {
            log.warnT("log.datareg.fail", "[数据注册] {0} 清理失败: {1}", handle.getSpec().getPluginName(), err);
            return false;
        }
        return true;
    }

    @Override
    public boolean reinstall(DataHandle handle, boolean purgeFirst) {
        if (handle == null) {
            return false;
        }
        if (purgeFirst && !purge(handle)) {
            return false;
        }
        return register(handle.getOwner(), handle.getSpec()) != null;
    }

    @Override
    public boolean isInstalled(String pluginName) {
        return SchemaRegistry.isInstalled(pluginName);
    }

    @Override
    public Set<String> tablesOf(String pluginName) {
        return SchemaRegistry.tablesOf(pluginName);
    }

    @Override
    public DataHandle handleOf(String pluginName) {
        return pluginName == null ? null : handles.get(pluginName);
    }

    @Override
    public List<String> registeredNames() {
        return new java.util.ArrayList<>(handles.keySet());
    }

    @Override
    public boolean update(String pluginName) {
        DataHandle h = handleOf(pluginName);
        if (h == null) {
            log.warnT("log.datareg.fail", "[数据注册] {0} 未登记数据句柄，无法显式更新（启动时已自动更新）", pluginName);
            return false;
        }
        return reinstall(pluginName); // 更新 = 保留数据重装（补复制/补列/迁移/种子补缺/meta 刷新）
    }

    @Override
    public boolean update(String pluginName, int targetVersion) {
        DataHandle h = handleOf(pluginName);
        if (h == null) {
            log.warnT("log.datareg.fail", "[数据注册] {0} 未登记数据句柄，无法显式更新", pluginName);
            return false;
        }
        if (targetVersion < 0) {
            return false;
        }
        h.getSpec().setSchemaVersion(targetVersion); // 临时覆盖目标版本（meta 幂等不回退）
        String err = AutoOps.install(hostPlugin.getPlatform(), h.getOwner().getClass().getClassLoader(), h.getSpec());
        if (err != null) {
            log.warnT("log.datareg.fail", "[数据注册] {0} 迁移到 V{1} 失败: {2}", pluginName, targetVersion, err);
            return false;
        }
        return true;
    }

    @Override
    public boolean unregister(String pluginName) {
        DataHandle h = handleOf(pluginName);
        return h != null && unregister(h);
    }

    @Override
    public boolean reinstall(String pluginName) {
        DataHandle h = handleOf(pluginName);
        if (h == null) {
            log.warnT("log.datareg.fail", "[数据注册] {0} 未登记数据句柄，无法重装", pluginName);
            return false;
        }
        String err = AutoOps.install(hostPlugin.getPlatform(), h.getOwner().getClass().getClassLoader(), h.getSpec());
        if (err != null) {
            log.warnT("log.datareg.fail", "[数据注册] {0} 重装失败: {1}", pluginName, err);
            return false;
        }
        return true;
    }
}
