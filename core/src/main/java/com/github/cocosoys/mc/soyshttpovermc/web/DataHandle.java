package com.github.cocosoys.mc.soyshttpovermc.web;

import com.github.cocosoys.mc.soyshttpovermc.api.DataRegistrationApi;
import com.github.cocosoys.mc.soyshttpovermc.orm.DataSpec;
import org.bukkit.plugin.Plugin;

/**
 * 数据包句柄（DataHandle）——{@link DataRegistrationApi#register(Plugin, DataSpec)} 的返回值，
 * 供后续卸载 / 清理 / 重装等数据事务使用。
 *
 * <p>持有注册时的 owner 与 {@link DataSpec}（含 meta 自动识别所需的全部信息）。
 * 卸载时数据层不操作、meta 保留（数据为服务器资产），仅摘登记；重装（register 同标识）
 * 自动识别为"保留数据更新"。</p>
 */
public final class DataHandle {

    private final Plugin owner;
    private final DataSpec spec;
    private volatile boolean registered;

    public DataHandle(Plugin owner, DataSpec spec) {
        this.owner = owner;
        this.spec = spec;
        this.registered = true;
    }

    /**
     * 登记时所属插件。
     */
    public Plugin getOwner() {
        return owner;
    }

    /**
     * 数据包描述。
     */
    public DataSpec getSpec() {
        return spec;
    }

    /**
     * 是否仍处于登记状态（{@link DataRegistrationApi#unregister(DataHandle)} 后为 false）。
     */
    public boolean isRegistered() {
        return registered;
    }

    public void markUnregistered() {
        this.registered = false;
    }
}
