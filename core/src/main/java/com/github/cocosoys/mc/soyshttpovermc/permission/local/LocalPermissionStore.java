package com.github.cocosoys.mc.soyshttpovermc.permission.local;

import com.github.cocosoys.mc.soyshttpovermc.orm.SQL;
import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.Query;
import com.github.cocosoys.mc.soyshttpovermc.util.UuidUtil;
import lombok.CustomLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 本地内置权限表存取门面（配套 {@code permission.offline-fallback: local}）。
 *
 * <p>承载 4 张全关联 ORM 实体（{@link SoysPermGroup} / {@link SoysPermUser} /
 * {@link SoysPermUserGroup} / {@link SoysPermPermission}）的全部 CRUD 与本地判定。
 * 读写复用现有 ORM 门面（SQL.Pojo 优先、YAML.Pojo 兜底，双后端写镜像，见 {@code storage.backends.*}）。</p>
 *
 * <p>用户侧身份键一律为 UUID 主键（离线服为确定性离线 UUID，见
 * {@link com.github.cocosoys.mc.soyshttpovermc.util.UuidUtil}）：玩家名仅是属性。
 * 所有用户操作方法入参「玩家名或 UUID」均可，统一经 {@link #userKey(String)} 归一到 uuid 键。</p>
 *
 * <p>节点规范化：</p>
 * <ul>
 *   <li>{@code ':' ≡ '.'}：写入与匹配时统一归一，如 {@code test:ping} 等价 {@code test.ping}；</li>
 *   <li>{@code -} 前缀=否定：写入剥离前缀、{@link SoysPermPermission#isNegative()}=true；</li>
 *   <li>通配：全量 {@code *}、段级尾通配 {@code a.*}（匹配 {@code a.x} / {@code a.x.y}）。</li>
 * </ul>
 *
 * <p>判定顺序：用户整体过期 → 聚合（用户直接权限 ∪ 所属组权限）→ 否定优先 → 肯定（精确/通配）。
 * 判定直接走 ORM（无内存缓存，数据量小）。</p>
 */
@CustomLog
public class LocalPermissionStore {

    // ==================== 规范化 ====================

    /**
     * 节点归一：去空白 + {@code ':' → '.'}（{@code test:ping} ≡ {@code test.ping}）。
     */
    public static String normalize(String node) {
        return node == null ? "" : node.trim().replace(':', '.');
    }

    /**
     * 用户身份主键：统一归一到 UUID（标准小写带横线）。
     * 已识别为 UUID（带/不带横线）→ 原样归一；否则视为玩家名 → 推导离线 UUID。
     * 离线服下离线 UUID 由玩家名确定性推导（见 {@link com.github.cocosoys.mc.soyshttpovermc.util.UuidUtil}）。
     */
    public static String userKey(String identity) {
        return UuidUtil.keyOf(identity);
    }

    /**
     * 解析用户输入节点：剥离 {@code -} 前缀为否定标记，返回归一化后的纯节点。
     *
     * @param input 原始输入（可为 {@code -node} 或 {@code node}）
     * @return {@link ParsedNode}（node 已归一，negative 标记）
     */
    public static ParsedNode parseNode(String input) {
        String s = input == null ? "" : input.trim();
        boolean neg = s.startsWith("-");
        if (neg) {
            s = s.substring(1).trim();
        }
        return new ParsedNode(normalize(s), neg);
    }

    /**
     * 解析后的权限节点（已归一 + 否定标记）。
     */
    public static final class ParsedNode {
        public final String node;
        public final boolean negative;

        ParsedNode(String node, boolean negative) {
            this.node = node;
            this.negative = negative;
        }
    }

    /**
     * 通配匹配：{@code *} 全量；{@code a.*} 匹配 {@code a.x} / {@code a.x.y}；否则要求精确相等。
     */
    public static boolean match(String pattern, String node) {
        if (pattern == null || node == null) return false;
        if (pattern.isEmpty()) return false;
        if ("*".equals(pattern)) return true;
        if (pattern.equals(node)) return true;
        if (pattern.endsWith(".*")) {
            String prefix = pattern.substring(0, pattern.length() - 2); // 去掉尾段 ".*"
            return node.startsWith(prefix + ".");
        }
        return false;
    }

    // ==================== 双后端 IO ====================

    private <T> T getOrNull(Class<T> c, Object id) {
        if (SQL.Pojo.isAvailable()) {
            T r = SQL.Pojo.get(c, id);
            if (r != null) return r;
        }
        if (YAML.Pojo.isAvailable()) {
            return YAML.Pojo.get(c, id);
        }
        return null;
    }

    private boolean save(Object bean) {
        boolean ok = false;
        try {
            if (SQL.Pojo.isAvailable()) {
                if (SQL.Pojo.insert(bean)) ok = true;
            }
            if (YAML.Pojo.isAvailable()) {
                if (YAML.Pojo.insert(bean)) ok = true;
            }
        } catch (Throwable t) {
            log.warn("[permission/local] 写入本地权限表失败: " + t, t);
        }
        return ok;
    }

    private boolean delete(Class<?> c, Object id) {
        boolean ok = false;
        try {
            if (SQL.Pojo.isAvailable()) {
                if (SQL.Pojo.deleteById(c, id)) ok = true;
            }
            if (YAML.Pojo.isAvailable()) {
                if (YAML.Pojo.deleteById(c, id)) ok = true;
            }
        } catch (Throwable t) {
            log.warn("[permission/local] 删除本地权限表记录失败: " + t, t);
        }
        return ok;
    }

    private <T> List<T> list(Class<T> c, Consumer<Query<T>> cond) {
        if (SQL.Pojo.isAvailable()) {
            List<T> r = SQL.Pojo.select(c, cond);
            if (r != null && !r.isEmpty()) return r;
        }
        if (YAML.Pojo.isAvailable()) {
            List<T> r = YAML.Pojo.select(c, cond);
            if (r != null) return r;
        }
        return Collections.emptyList();
    }

    // ==================== 组 ====================

    /**
     * 创建/更新权限组（upsert 语义：已存在则更新元数据）。
     */
    public boolean createGroup(String id, int weight, String display, String description) {
        String gid = normalize(id).toLowerCase();
        if (gid.isEmpty()) return false;
        long now = System.currentTimeMillis();
        SoysPermGroup g = getGroup(gid);
        if (g == null) {
            g = new SoysPermGroup(gid);
            g.setCreatedAt(String.valueOf(now));
        }
        g.setWeight(weight);
        g.setDisplay(display == null || display.isEmpty() ? gid : display);
        g.setDescription(description == null ? "" : description);
        g.setUpdatedAt(String.valueOf(now));
        return save(g);
    }

    /**
     * 删除权限组（连带删除该组全部权限记录与所有用户对该组的引用）。
     */
    public boolean deleteGroup(String id) {
        String gid = normalize(id).toLowerCase();
        if (gid.isEmpty()) return false;
        // 删组权限
        for (SoysPermPermission p : listPermissions(SoysPermPermission.TYPE_GROUP, gid)) {
            delete(SoysPermPermission.class, p.getId());
        }
        // 删用户-组引用
        for (SoysPermUserGroup ug : list(SoysPermUserGroup.class, c -> c.eq(SoysPermUserGroup::getGroup, gid))) {
            delete(SoysPermUserGroup.class, ug.getId());
        }
        return delete(SoysPermGroup.class, gid);
    }

    public SoysPermGroup getGroup(String id) {
        String gid = normalize(id).toLowerCase();
        return gid.isEmpty() ? null : getOrNull(SoysPermGroup.class, gid);
    }

    public List<SoysPermGroup> listGroups() {
        List<SoysPermGroup> out = list(SoysPermGroup.class, q -> {
        });
        out.sort((a, b) -> Integer.compare(b.getWeight(), a.getWeight()));
        return out;
    }

    /**
     * 组加权限（{@code -} 前缀=否定；{@code :≡.} 归一）。
     */
    public boolean addGroupPermission(String id, String nodeInput) {
        String gid = normalize(id).toLowerCase();
        if (gid.isEmpty()) return false;
        ParsedNode p = parseNode(nodeInput);
        if (p.node.isEmpty()) return false;
        return save(new SoysPermPermission(SoysPermPermission.TYPE_GROUP, gid, p.node, p.negative));
    }

    public boolean removeGroupPermission(String id, String nodeInput) {
        String gid = normalize(id).toLowerCase();
        if (gid.isEmpty()) return false;
        ParsedNode p = parseNode(nodeInput);
        if (p.node.isEmpty()) return false;
        return delete(SoysPermPermission.class, SoysPermPermission.TYPE_GROUP + "|" + gid + "|" + p.node);
    }

    public List<SoysPermPermission> listGroupPermissions(String id) {
        String gid = normalize(id).toLowerCase();
        return gid.isEmpty() ? Collections.emptyList()
                : listPermissions(SoysPermPermission.TYPE_GROUP, gid);
    }

    // ==================== 用户 ====================

    /**
     * 取用户记录（不存在返回 null）。
     */
    public SoysPermUser getUser(String player) {
        String pk = userKey(player);
        return pk.isEmpty() ? null : getOrNull(SoysPermUser.class, pk);
    }

    /**
     * 用户是否存在（含已过期）。
     */
    public boolean hasUser(String player) {
        return getUser(player) != null;
    }

    /**
     * 创建/更新用户（upsert；uuid 主键，名字仅作属性同步）。
     * 传入玩家名时自动补录离线 UUID 与玩家名属性；传入 UUID 时按原样归一主键。
     */
    public boolean createUser(String player) {
        String pk = userKey(player);
        if (pk.isEmpty()) return false;
        String name = UuidUtil.isUuid(player) ? null : player.trim(); // 名字输入才记录玩家名属性
        long now = System.currentTimeMillis();
        SoysPermUser u = getUser(pk);
        if (u == null) {
            u = new SoysPermUser(pk);
            u.setCreatedAt(String.valueOf(now));
        }
        if (name != null && !name.equals(u.getPlayer())) {
            u.setPlayer(name); // 同步最新玩家名属性
        }
        u.setUpdatedAt(String.valueOf(now));
        return save(u);
    }

    /**
     * 删除用户（连带删除用户直接权限与用户-组引用）。
     */
    public boolean deleteUser(String player) {
        String pk = userKey(player);
        if (pk.isEmpty()) return false;
        for (SoysPermPermission p : listPermissions(SoysPermPermission.TYPE_USER, pk)) {
            delete(SoysPermPermission.class, p.getId());
        }
        for (SoysPermUserGroup ug : list(SoysPermUserGroup.class, c -> c.eq(SoysPermUserGroup::getUuid, pk))) {
            delete(SoysPermUserGroup.class, ug.getId());
        }
        return delete(SoysPermUser.class, pk);
    }

    /**
     * 设置用户整体过期时刻。
     *
     * @param expiryEpoch epoch 毫秒字符串；空串或 "0" = 清除（永久）。
     */
    public boolean setUserExpiry(String player, String expiryEpoch) {
        String pk = userKey(player);
        if (pk.isEmpty()) return false;
        SoysPermUser u = getUser(pk);
        if (u == null) u = new SoysPermUser(pk);
        String exp = expiryEpoch == null ? "" : expiryEpoch.trim();
        if (exp.isEmpty() || "0".equals(exp) || "clear".equalsIgnoreCase(exp)) {
            u.setExpiry("");
        } else {
            try {
                long v = Long.parseLong(exp);
                u.setExpiry(String.valueOf(v));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        u.setUpdatedAt(String.valueOf(System.currentTimeMillis()));
        return save(u);
    }

    /**
     * 用户是否已过期（无记录视为不过期——调用方需先判存在）。
     */
    public boolean isExpired(String player) {
        SoysPermUser u = getUser(player);
        if (u == null) return false;
        String e = u.getExpiry();
        if (e == null || e.isEmpty()) return false;
        try {
            return Long.parseLong(e) < System.currentTimeMillis();
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    // ==================== 用户-组 ====================

    /**
     * 用户加组（幂等：已存在则 no-op 返回 true）。
     */
    public boolean addUserGroup(String player, String group) {
        String pk = userKey(player);
        String gid = normalize(group).toLowerCase();
        if (pk.isEmpty() || gid.isEmpty()) return false;
        if (getUser(pk) == null) createUser(pk);
        if (getGroup(gid) == null) createGroup(gid, 0, gid, "");
        SoysPermUserGroup exist = getOrNull(SoysPermUserGroup.class, pk + "|" + gid);
        if (exist != null) return true; // 已存在
        return save(new SoysPermUserGroup(pk, gid));
    }

    public boolean removeUserGroup(String player, String group) {
        String pk = userKey(player);
        String gid = normalize(group).toLowerCase();
        if (pk.isEmpty() || gid.isEmpty()) return false;
        return delete(SoysPermUserGroup.class, pk + "|" + gid);
    }

    /**
     * 用户所属组名列表。
     */
    public List<String> listUserGroups(String player) {
        String pk = userKey(player);
        if (pk.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (SoysPermUserGroup ug : list(SoysPermUserGroup.class, c -> c.eq(SoysPermUserGroup::getUuid, pk))) {
            out.add(ug.getGroup());
        }
        return out;
    }

    /**
     * 某组下的全部用户（供组管理展示）。
     */
    public List<SoysPermUserGroup> listGroupMembers(String group) {
        String gid = normalize(group).toLowerCase();
        return gid.isEmpty() ? Collections.emptyList()
                : list(SoysPermUserGroup.class, c -> c.eq(SoysPermUserGroup::getGroup, gid));
    }

    // ==================== 权限 ====================

    private List<SoysPermPermission> listPermissions(String ownerType, String ownerId) {
        return list(SoysPermPermission.class,
                c -> c.eq(SoysPermPermission::getOwnerType, ownerType)
                        .eq(SoysPermPermission::getOwnerId, ownerId));
    }

    /**
     * 用户直接权限（不含组继承）。
     */
    public List<SoysPermPermission> listUserPermissions(String player) {
        String pk = userKey(player);
        return pk.isEmpty() ? Collections.emptyList()
                : listPermissions(SoysPermPermission.TYPE_USER, pk);
    }

    /**
     * 用户加直接权限（{@code -} 前缀=否定；{@code :≡.} 归一）。
     */
    public boolean addUserPermission(String player, String nodeInput) {
        String pk = userKey(player);
        if (pk.isEmpty()) return false;
        if (getUser(pk) == null) createUser(pk);
        ParsedNode p = parseNode(nodeInput);
        if (p.node.isEmpty()) return false;
        return save(new SoysPermPermission(SoysPermPermission.TYPE_USER, pk, p.node, p.negative));
    }

    public boolean removeUserPermission(String player, String nodeInput) {
        String pk = userKey(player);
        if (pk.isEmpty()) return false;
        ParsedNode p = parseNode(nodeInput);
        if (p.node.isEmpty()) return false;
        return delete(SoysPermPermission.class, SoysPermPermission.TYPE_USER + "|" + pk + "|" + p.node);
    }

    /**
     * 用户生效权限（用户直接权限 ∪ 所属组权限；组内权限并集，不含优先级折叠，供管理展示与判定）。
     */
    public List<SoysPermPermission> listEffectivePermissions(String player) {
        String pk = userKey(player);
        if (pk.isEmpty()) return Collections.emptyList();
        List<SoysPermPermission> out = new ArrayList<>();
        out.addAll(listPermissions(SoysPermPermission.TYPE_USER, pk));
        for (String g : listUserGroups(pk)) {
            out.addAll(listPermissions(SoysPermPermission.TYPE_GROUP, g));
        }
        return out;
    }

    // ==================== 判定 ====================

    /**
     * 本地权限判定：{@code check(player, node)}。
     *
     * <p>规则：用户不存在/整体过期 → false；聚合（用户直接 ∪ 组）；否定（精确/通配）优先拒绝；
     * 肯定（精确/通配）命中 → true；否则 false。</p>
     */
    public boolean check(String player, String permission) {
        String pk = userKey(player);
        if (pk.isEmpty() || permission == null || permission.trim().isEmpty()) return false;
        SoysPermUser user = getOrNull(SoysPermUser.class, pk);
        if (user == null) return false;
        if (isExpired(pk)) return false;
        String node = normalize(permission);
        if (node.isEmpty()) return false;

        // 否定优先（两遍扫描）：先查否定，任一命中即拒绝；再查肯定，任一命中即通过
        for (SoysPermPermission p : listEffectivePermissions(pk)) {
            if (p.isNegative() && match(p.getPermission(), node)) return false;
        }
        for (SoysPermPermission p : listEffectivePermissions(pk)) {
            if (!p.isNegative() && match(p.getPermission(), node)) return true;
        }
        return false;
    }
}
