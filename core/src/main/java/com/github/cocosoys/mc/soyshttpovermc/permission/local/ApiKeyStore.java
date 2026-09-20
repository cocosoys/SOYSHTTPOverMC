package com.github.cocosoys.mc.soyshttpovermc.permission.local;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysApiKey;
import com.github.cocosoys.mc.soyshttpovermc.enums.SoysPermOwnerType;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysPermPermission;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.ILocalPermStorage;
import com.github.cocosoys.mc.soyshttpovermc.util.UuidUtil;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.util.AuthUtils;
import lombok.CustomLog;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * X-API-Key 本地表存取门面（配套 {@code soys_api_key} 实体）。
 *
 * <p>承载 {@link SoysApiKey} 的生成 / 校验 / CRUD / 绑定与权限管理：读写复用统一 ORM 门面
 * （{@link com.github.cocosoys.mc.soyshttpovermc.orm.DATA}，SQL 可用走 SQL，否则 YAML），
 * IO 经 {@link ILocalPermStorage} 抽象（同 {@link LocalPermissionStore}）。</p>
 *
 * <p><b>安全模型</b>：库中只存 SHA-256 全量哈希与 8 位短指纹；请求头明文经
 * {@link AuthUtils#sha256Hex(String)} 后查表，不落库、不比较明文。
 * 权限节点复用 {@link SoysPermPermission}（ownerType={@link SoysPermOwnerType#APIKEY}、
 * ownerId=key 主键），支持否定 / 通配 / 过期。</p>
 *
 * <p><b>绑定玩家</b>：{@link SoysApiKey#getUuid()} 可空；一期仅存储，判定仍按 key 自身权限链。</p>
 */
@CustomLog
public class ApiKeyStore {

    private final ILocalPermStorage storage;
    private final LocalPermissionStore localStore;

    public ApiKeyStore(ILocalPermStorage storage) {
        this.storage = storage;
        this.localStore = new LocalPermissionStore(storage);
    }

    // ==================== 生成 ====================

    /**
     * 生成随机密钥（32 字节 → Base64 URL-safe 无填充）。
     *
     * @param remark 备注（用途说明，可空）
     * @return 密钥明文（<b>仅本次展示</b>；库中只存哈希）
     */
    public String generate(String remark) {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        String plain = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        SoysApiKey k = new SoysApiKey(UUID.randomUUID().toString(),
                AuthUtils.sha256Hex(plain), AuthUtils.fingerprint(plain));
        k.setRemark(remark == null ? "" : remark);
        k.setCreateTime(new Date());
        storage.save(k);
        return plain;
    }

    // ==================== 查询 ====================

    /**
     * 按请求明文查 key 记录（SHA-256 哈希匹配）。
     */
    public SoysApiKey findByPresented(String presented) {
        if (presented == null || presented.isEmpty()) return null;
        List<SoysApiKey> list = storage.list(SoysApiKey.class,
                c -> c.eq(SoysApiKey::getApiKey, AuthUtils.sha256Hex(presented)));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 按主键查 key 记录（不存在返回 null）。
     */
    public SoysApiKey findById(String id) {
        return id == null || id.isEmpty() ? null : storage.get(SoysApiKey.class, id);
    }

    /**
     * 全部 key 记录（管理展示）。
     */
    public List<SoysApiKey> list() {
        return storage.list(SoysApiKey.class, q -> {
        });
    }

    /**
     * 按主键或 8 位短指纹定位 key（命令/管理用；指纹冲突概率可忽略，取首个）。
     */
    public SoysApiKey findByIdOrFingerprint(String idOrFingerprint) {
        if (idOrFingerprint == null || idOrFingerprint.isEmpty()) return null;
        SoysApiKey byId = findById(idOrFingerprint);
        if (byId != null) return byId;
        for (SoysApiKey k : list()) {
            if (idOrFingerprint.equalsIgnoreCase(k.getFingerprint())) return k;
        }
        return null;
    }

    // ==================== 校验 ====================

    /**
     * 是否有效凭证：存在 && 启用 && 未过期。
     */
    public boolean isValid(String presented) {
        SoysApiKey k = findByPresented(presented);
        return k != null && isUsable(k);
    }

    /**
     * key 权限判定：key 有效 → 查其权限节点（ownerType=APIKEY），否定优先 → 肯定（精确/通配）。
     */
    public boolean checkPermission(String presented, String permission) {
        SoysApiKey k = findByPresented(presented);
        if (k == null || !isUsable(k)) return false;
        return localStore.checkPermissions(SoysPermOwnerType.APIKEY.code(), k.getId(), permission);
    }

    /**
     * 记录一次使用（lastUsedAt / usedCount 更新；读热路径可容忍一次写）。
     */
    public void touch(String presented) {
        try {
            SoysApiKey k = findByPresented(presented);
            if (k == null) return;
            k.setLastUsedAt(new Date());
            k.setUsedCount(k.getUsedCount() + 1);
            k.setUpdateTime(new Date());
            storage.save(k);
        } catch (Throwable t) {
            log.warnT("log.api-key.touch-failed", "X-API-Key 使用统计更新失败: {0}", t.getMessage());
        }
    }

    // ==================== 生命周期 ====================

    /**
     * 启用 / 停用。
     */
    public boolean setEnabled(String id, boolean enabled) {
        SoysApiKey k = findById(id);
        if (k == null) return false;
        k.setEnabled(enabled);
        k.setUpdateTime(new Date());
        return storage.save(k);
    }

    /**
     * 设置过期时刻（yyyy-MM-dd HH:mm:ss；"clear"/"0"/空 = 永久）。
     */
    public boolean setExpiry(String id, String expiryInput) {
        SoysApiKey k = findById(id);
        if (k == null) return false;
        String exp = expiryInput == null ? "" : expiryInput.trim();
        if (exp.isEmpty() || "0".equals(exp) || "clear".equalsIgnoreCase(exp)) {
            k.setExpiry(null);
        } else {
            try {
                k.setExpiry(com.github.cocosoys.mc.soyshttpovermc.orm.convertor.BeanCodec.parseDate(exp));
            } catch (java.text.ParseException e) {
                return false;
            }
        }
        k.setUpdateTime(new Date());
        return storage.save(k);
    }

    /**
     * 绑定玩家（玩家名或 UUID 均可，统一归一到 UUID 主键；名字仅作属性同步）。
     */
    public boolean bind(String id, String playerOrUuid) {
        SoysApiKey k = findById(id);
        if (k == null) return false;
        if (UuidUtil.isUuid(playerOrUuid)) {
            k.setUuid(UuidUtil.keyOf(playerOrUuid));
            k.setPlayer(null);
        } else {
            k.setUuid(UuidUtil.keyOf(playerOrUuid));
            k.setPlayer(playerOrUuid == null ? null : playerOrUuid.trim());
        }
        k.setUpdateTime(new Date());
        return storage.save(k);
    }

    /**
     * 解除玩家绑定（uuid/player 置空；key 仍独立有效）。
     */
    public boolean unbind(String id) {
        SoysApiKey k = findById(id);
        if (k == null) return false;
        k.setUuid(null);
        k.setPlayer(null);
        k.setUpdateTime(new Date());
        return storage.save(k);
    }

    /**
     * 删除 key（连带删除其全部权限记录）。
     */
    public boolean remove(String id) {
        SoysApiKey k = findById(id);
        if (k == null) return false;
        for (SoysPermPermission p : listPermissions(id)) {
            storage.delete(SoysPermPermission.class, p.getId());
        }
        return storage.delete(SoysApiKey.class, id);
    }

    // ==================== 权限 ====================

    /**
     * key 加权限（{@code -} 前缀=否定；{@code :≡.} 归一）。
     */
    public boolean addPermission(String id, String nodeInput) {
        if (findById(id) == null) return false;
        LocalPermissionStore.ParsedNode p = LocalPermissionStore.parseNode(nodeInput);
        if (p.node.isEmpty()) return false;
        for (SoysPermPermission rec : listPermissions(id)) {
            if (rec.getPermission().equals(p.node)) {
                rec.setNegative(p.negative);
                rec.setUpdateTime(new Date());
                return storage.save(rec);
            }
        }
        return storage.save(new SoysPermPermission(SoysPermOwnerType.APIKEY.code(), id, p.node, p.negative));
    }

    /**
     * key 移除权限（按归一化节点精确删除）。
     */
    public boolean removePermission(String id, String nodeInput) {
        if (findById(id) == null) return false;
        LocalPermissionStore.ParsedNode p = LocalPermissionStore.parseNode(nodeInput);
        if (p.node.isEmpty()) return false;
        for (SoysPermPermission rec : listPermissions(id)) {
            if (rec.getPermission().equals(p.node)) {
                return storage.delete(SoysPermPermission.class, rec.getId());
            }
        }
        return false;
    }

    /**
     * key 的权限记录列表。
     */
    public List<SoysPermPermission> listPermissions(String id) {
        return storage.list(SoysPermPermission.class,
                c -> c.eq(SoysPermPermission::getOwnerType, SoysPermOwnerType.APIKEY.code())
                        .eq(SoysPermPermission::getOwnerId, id));
    }

    // ==================== 内部 ====================

    private static boolean isUsable(SoysApiKey k) {
        if (!k.isEnabled()) return false;
        Date e = k.getExpiry();
        return e == null || !e.before(new Date());
    }
}
