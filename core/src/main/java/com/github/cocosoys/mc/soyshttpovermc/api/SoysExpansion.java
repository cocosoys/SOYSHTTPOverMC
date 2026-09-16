package com.github.cocosoys.mc.soyshttpovermc.api;

import com.github.cocosoys.mc.soyshttpovermc.web.CorsRegistry;
import com.github.cocosoys.mc.soyshttpovermc.web.WebRegistry;
import lombok.CustomLog;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SOYS 模块扩展基类：
 * 附属插件开发者只需 <b>继承本类 + 覆写元信息/业务方法</b>，再调用一次零参数
 * {@link #register()}，框架自动完成端点注册、owner 识别、页面资源托管、CORS 声明、
 * 卸载登记与冲突检测——无需感知 {@link ApiRegistrationApi} / {@link WebPageApi} 等细节。
 *
 * <pre>
 * public class ShopController extends SoysExpansion {
 *     &#64;Override public String getIdentifier() { return "shop"; }   // 唯一必填元信息
 *
 *     &#64;GetMapping("/items")
 *     public AjaxResult items(...) { ... }
 *
 *     &#64;Override protected String resourceRoot() { return "dist"; }  // 可选：自动托管 jar 内 web 目录
 * }
 *
 * // onEnable 中一行注册：
 * if (!new ShopController().register()) {
 *     plugin.getLogger().warning("ShopController 注册失败（identifier 冲突？bootstrap 未初始化？）");
 * }
 * </pre>
 *
 * <p><b>模板方法结构</b>：{@link #register()} / {@link #unregister()} 为 final 骨架，依次调用
 * 可覆写的单类注册钩子——{@link #registerControllers()}（端点）、{@link #registerPages()}（页面目录）、
 * {@link #registerCors()}（CORS）与对应的注销钩子。需要单独定制某一种注册/注销时，
 * 覆写对应钩子即可（骨架会自动按序调用并做失败回滚），不影响其它类型。</p>
 *
 * <p><b>注册自动处理项</b>：① 端点注解扫描（方法级映射/权限/限流等照常生效）；
 * ② owner 自动识别（{@link JavaPlugin#getProvidingPlugin(Class)}）；
 * ③ 非 proxy 模式自动补 /plugins/&lt;插件名&gt; 前缀；④ {@code resourceRoot()} 非空时自动
 * 托管 jar 内资源目录（页面打 {@code expansion:&lt;identifier&gt;} tag，可精确卸载）；
 * ⑤ {@link #cors()} 非空时自动注册 CORS；⑥ 登记到模块注册表，{@link #unregister()} 时
 * 精确反注册端点 / 页面 / CORS。重复 identifier 拒绝注册。</p>
 */
@CustomLog
public abstract class SoysExpansion {

    // ===== 静态门面（静态注册管理器）=====
    private static volatile SoysHttpOverMcApi api;

    /**
     * 由主插件（SOYSHTTPOverMC）在 API 门面就绪后调用一次；reload 重建门面时幂等覆盖。
     */
    public static void bootstrap(SoysHttpOverMcApi api) {
        SoysExpansion.api = api;
    }

    /**
     * 已注册模块表：identifier → 扩展实例（冲突检测 + 全量清单）。
     */
    private static final ConcurrentHashMap<String, SoysExpansion> REGISTERED = new ConcurrentHashMap<>();

    // ===== 实例状态 =====
    private volatile boolean registered;
    private volatile Plugin owner;
    private volatile String tag;                 // "expansion:" + identifier，页面精确卸载用
    private volatile boolean hasCors;            // 本次是否注册过 CORS（卸载时按插件兜底清）
    private volatile Set<WebRegistry.Entry> pages = Collections.emptySet();

    // ===== 元信息（仅 getIdentifier 必填）=====

    /**
     * 模块唯一标识：冲突检测、页面 tag、卸载与文档展示均使用它。不能为空，不能重复。
     */
    public abstract String getIdentifier();

    /**
     * 作者信息（可选，文档/调试展示）。
     */
    public String getAuthor() {
        return "";
    }

    /**
     * 版本信息（可选，文档/调试展示）。
     */
    public String getVersion() {
        return "";
    }

    // ===== 生命周期钩子 =====

    /**
     * 注册完成回调：返回 false 将回滚本次注册并视为注册失败；抛异常同样回滚。
     */
    public boolean onRegister() {
        return true;
    }

    /**
     * 反注册回调（unregister 时调用，此时已从注册表移除）。
     */
    public void onUnregister() {
    }

    // ===== 声明式钩子 =====

    /**
     * 页面资源目录（jar 内根路径，如 "dist"）：非空时 {@link #registerPages()} 自动
     * {@code registerResourceDirectory} 托管整目录，页面统一打
     * {@code expansion:&lt;identifier&gt;} tag。返回 null = 无页面资源。
     */
    protected String resourceRoot() {
        return null;
    }

    /**
     * CORS 声明列表：非空时 {@link #registerCors()} 逐条自动注册。返回 null = 无 CORS。
     */
    protected CorsSpec[] cors() {
        return null;
    }

    /**
     * 是否强制代理登记（无 /plugins/&lt;插件名&gt; 前缀）。默认 false = 自动加插件命名空间前缀。
     */
    protected boolean proxy() {
        return false;
    }

    /**
     * CORS 声明值对象（{@link #cors()} 返回数组元素）。
     */
    public static final class CorsSpec {

        public final String pathPrefix;
        public final String origin;
        public final String methods;
        public final String headers;
        public final boolean credentials;

        /**
         * 完整声明。
         *
         * @param pathPrefix 路径前缀（空或 "/" = 全局）
         * @param origin     允许来源（如 * 或 https://example.com，逗号分隔多源）
         * @param methods    允许方法（如 GET,POST，null = 默认全方法）
         * @param headers    允许头（null = *）
         * @param credentials 是否允许携带凭据（origin 为 * 时建议 false，否则浏览器拒绝）
         */
        public CorsSpec(String pathPrefix, String origin, String methods, String headers, boolean credentials) {
            this.pathPrefix = pathPrefix;
            this.origin = origin;
            this.methods = methods;
            this.headers = headers;
            this.credentials = credentials;
        }

        /**
         * 常用简写：路径前缀 + 允许来源（其余默认）。
         */
        public CorsSpec(String pathPrefix, String origin) {
            this(pathPrefix, origin, null, null, false);
        }
    }

    // ===== 唯一注册入口（零参数，final 骨架）=====

    /**
     * 注册本扩展：依次执行 {@link #registerControllers()} / {@link #registerPages()} /
     * {@link #registerCors()}，任一失败自动回滚已成功部分；随后登记注册表并回调
     * {@link #onRegister()}（返回 false 同样整体回滚）。
     *
     * @return true=注册成功；false=失败（已注册 / bootstrap 未初始化 / identifier 为空或冲突 /
     *         任一注册钩子失败 / onRegister 拒绝）
     */
    public final boolean register() {
        if (registered) {
            log.warn("SoysExpansion 已注册，忽略重复注册: {0}", getClass().getName());
            return false;
        }
        SoysHttpOverMcApi a = api;
        if (a == null) {
            log.warn("SoysExpansion 未初始化（主插件未调用 bootstrap），无法注册: {0}", getClass().getName());
            return false;
        }
        String id = getIdentifier();
        if (id == null || id.trim().isEmpty()) {
            log.warn("SoysExpansion 的 getIdentifier() 不能为空: {0}", getClass().getName());
            return false;
        }
        id = id.trim();
        if (REGISTERED.containsKey(id)) {
            log.warn("SoysExpansion identifier 冲突，已存在同名扩展，拒绝注册: {0}", id);
            return false;
        }

        // 实例状态就绪（钩子可经 getOwner()/api() 访问）
        this.owner = JavaPlugin.getProvidingPlugin(getClass());
        this.tag = tagOf(id);

        // 依次执行单类注册钩子；任一失败回滚已成功部分（不触发 onUnregister）
        boolean ok = registerControllers();
        if (ok) {
            ok = registerPages();
        }
        if (ok) {
            ok = registerCors();
        }
        if (!ok) {
            rollbackPartial();
            log.warn("SoysExpansion 注册失败，已回滚已注册部分: {0}", id);
            return false;
        }

        // 登记注册表 + 生命周期回调（失败整体回滚）
        REGISTERED.put(id, this);
        registered = true;
        try {
            if (!onRegister()) {
                unregister();
                log.warn("SoysExpansion onRegister() 返回 false，已整体回滚: {0}", id);
                return false;
            }
        } catch (Exception ex) {
            unregister();
            log.warn("SoysExpansion onRegister() 异常，已整体回滚: {0}: {1}", id, ex.getMessage());
            return false;
        }
        log.info("SoysExpansion 已注册: {0} (owner={1}, pages={2}, cors={3})",
                id, owner == null ? "?" : owner.getName(), pages.size(), hasCors);
        return true;
    }

    /**
     * 反注册本扩展：依次执行 {@link #unregisterControllers()} / {@link #unregisterPages()} /
     * {@link #unregisterCors()}，并从注册表摘除、回调 {@link #onUnregister()}。
     *
     * @return true=已反注册；false=未处于注册状态
     */
    public final boolean unregister() {
        if (!registered) {
            return false;
        }
        String id = getIdentifier() == null ? "" : getIdentifier().trim();
        try {
            unregisterControllers();
            unregisterPages();
            unregisterCors();
        } finally {
            REGISTERED.remove(id);
            resetState();
            try {
                onUnregister();
            } catch (Exception ex) {
                log.warn("SoysExpansion onUnregister() 异常: {0}: {1}", id, ex.getMessage());
            }
        }
        log.info("SoysExpansion 已反注册: {0}", id);
        return true;
    }

    /**
     * 是否已注册。
     */
    public final boolean isRegistered() {
        return registered;
    }

    /**
     * 已注册模块全量清单（identifier → 扩展实例），供调试/文档使用。
     */
    public static ConcurrentHashMap<String, SoysExpansion> registered() {
        return REGISTERED;
    }

    /**
     * 本扩展归属的插件（register() 时经 getProvidingPlugin 自动识别；注册前为 null）。
     */
    protected final Plugin getOwner() {
        return owner;
    }

    // ===== 可覆写单类注册钩子（模板方法模式；骨架按序调用）=====

    /**
     * 端点注册骨架：依次执行 {@link #registerController()}（正常登记）与
     * {@link #registerProxyController()}（代理登记）。
     *
     * <p>两个子钩子均可独立覆写：例如既想正常登记又想代理登记时，分别覆写
     * {@link #registerController()} / {@link #registerProxyController()} 并各自执行注册即可，
     * 无需重写本聚合方法。</p>
     *
     * @return true=两类端点均无异常完成；false=任一失败（骨架将整体回滚）
     */
    protected boolean registerControllers() {
        boolean ok = registerController();
        if (ok) {
            ok = registerProxyController();
        }
        return ok;
    }

    /**
     * 正常登记端点（自动补 /plugins/&lt;插件名&gt; 前缀）。
     *
     * <p>默认：{@link #proxy()} 为 false 时登记本实例；{@link #proxy()} 为 true 时跳过
     * （空操作视为成功）。</p>
     *
     * <p><b>覆写场景</b>：controller 的注册代码书写在其它类中（如 MCERP 的独立 Controller 类）
     * 时，在覆写中调用 {@code api().getApiRegistration().registerController(instance, getOwner())}
     * 登记该实例即可；希望保留默认行为后再追加注册其它实例时，先调用
     * {@code super.registerController()}。</p>
     *
     * @return true=无异常完成（端点已登记或按模式跳过）；false=失败（骨架将整体回滚）
     */
    protected boolean registerController() {
        if (proxy()) {
            return true; // 代理模式不执行正常登记
        }
        Plugin o = owner;
        SoysHttpOverMcApi a = api;
        if (a == null || o == null) {
            return false;
        }
        try {
            a.getApiRegistration().registerController(this, o, false);
            return true;
        } catch (Exception ex) {
            log.warn("SoysExpansion 端点注册失败（正常登记）: {0}: {1}", getIdentifier(), ex.getMessage());
            return false;
        }
    }

    /**
     * 代理登记端点（无 /plugins/&lt;插件名&gt; 前缀）。
     *
     * <p>默认：{@link #proxy()} 为 true 时登记本实例；{@link #proxy()} 为 false 时跳过
     * （空操作视为成功）。</p>
     *
     * <p>覆写场景同 {@link #registerController()}，走 {@code registerProxyController} 通道。</p>
     *
     * @return true=无异常完成（端点已登记或按模式跳过）；false=失败（骨架将整体回滚）
     */
    protected boolean registerProxyController() {
        if (!proxy()) {
            return true; // 正常模式不执行代理登记
        }
        Plugin o = owner;
        SoysHttpOverMcApi a = api;
        if (a == null || o == null) {
            return false;
        }
        try {
            a.getApiRegistration().registerProxyController(this, o, false);
            return true;
        } catch (Exception ex) {
            log.warn("SoysExpansion 端点注册失败（代理登记）: {0}: {1}", getIdentifier(), ex.getMessage());
            return false;
        }
    }

    /**
     * 页面资源托管：{@code resourceRoot()} 非空时批量登记 jar 内资源目录，页面打
     * {@code expansion:&lt;identifier&gt;} tag（供 {@link #unregisterPages()} 精确卸载）。
     * 覆写示例：改为逐页 {@code registerPage(...)} 手动登记（此时应同步维护本类页面状态或一并覆写注销钩子）。
     *
     * @return true=无异常完成（含未声明资源目录的空操作）；false=失败（骨架将回滚已注册部分）
     */
    protected boolean registerPages() {
        Plugin o = owner;
        SoysHttpOverMcApi a = api;
        if (a == null || o == null) {
            return false;
        }
        String root = resourceRoot();
        if (root == null || root.trim().isEmpty()) {
            return true; // 未声明页面资源 → 空操作视为成功
        }
        try {
            Set<WebRegistry.Entry> reg = new HashSet<>();
            String t = tag;
            Set<WebRegistry.Entry> es = a.getWebPage().registerResourceDirectory(o, "/",
                    o.getClass().getClassLoader(), root.trim());
            if (es != null) {
                for (WebRegistry.Entry e : es) {
                    e.tags.add(t);
                    reg.add(e);
                }
            }
            this.pages = reg;
            return true;
        } catch (Exception ex) {
            log.warn("SoysExpansion 页面托管失败: {0}: {1}", getIdentifier(), ex.getMessage());
            return false;
        }
    }

    /**
     * CORS 注册：{@code cors()} 非空时逐条 {@code registerCors}。任一条成功即置位 hasCors
     * （骨架失败回滚时按插件兜底清理已注册的 CORS）。
     *
     * @return true=无异常完成（含未声明 CORS 的空操作）；false=失败（骨架将回滚已注册部分）
     */
    protected boolean registerCors() {
        Plugin o = owner;
        SoysHttpOverMcApi a = api;
        if (a == null || o == null) {
            return false;
        }
        CorsSpec[] specs = cors();
        if (specs == null) {
            return true; // 未声明 CORS → 空操作视为成功
        }
        try {
            for (CorsSpec s : specs) {
                if (s == null) {
                    continue;
                }
                a.getWebPage().registerCors(o, s.pathPrefix, s.origin, s.methods, s.headers, s.credentials);
                this.hasCors = true;
            }
            return true;
        } catch (Exception ex) {
            log.warn("SoysExpansion CORS 注册失败: {0}: {1}", getIdentifier(), ex.getMessage());
            return false;
        }
    }

    // ===== 可覆写单类注销钩子（模板方法模式；骨架按序调用）=====

    /**
     * 端点反注册（正常登记侧）：默认 {@code unregisterController(this)} 精确移除本扩展全部端点。
     *
     * <p>注意：若覆写 {@link #registerController()} 注册了额外实例，请一并覆写本方法卸载它们
     * （默认只卸载本实例）。</p>
     */
    protected void unregisterControllers() {
        SoysHttpOverMcApi a = api;
        if (a == null) {
            return;
        }
        try {
            a.getApiRegistration().unregisterController(this);
        } catch (Exception ex) {
            log.warn("SoysExpansion 端点反注册失败（正常登记）: {0}: {1}", getIdentifier(), ex.getMessage());
        }
    }

    /**
     * 页面反注册：按 {@code expansion:&lt;identifier&gt;} tag 精确移除本扩展托管的全部页面。
     */
    protected void unregisterPages() {
        SoysHttpOverMcApi a = api;
        if (a == null || tag == null) {
            return;
        }
        try {
            a.getWebPage().unregisterByTag(tag);
        } catch (Exception ex) {
            log.warn("SoysExpansion 页面反注册失败: {0}: {1}", getIdentifier(), ex.getMessage());
        }
    }

    /**
     * CORS 反注册：按 owner 插件名清理（仅当本次注册过 CORS）。
     */
    protected void unregisterCors() {
        SoysHttpOverMcApi a = api;
        Plugin o = owner;
        if (a == null || o == null || !hasCors) {
            return;
        }
        try {
            a.getWebPage().unregisterCors(o.getName());
        } catch (Exception ex) {
            log.warn("SoysExpansion CORS 反注册失败: {0}: {1}", getIdentifier(), ex.getMessage());
        }
    }

    // ===== 内部工具 =====

    /**
     * 骨架失败回滚：对"已执行过注册钩子"的类型逐一反注册（不触发 onUnregister，不经过完整 unregister 流程）。
     */
    private void rollbackPartial() {
        try {
            unregisterControllers();
        } catch (Exception ignored) {
        }
        try {
            unregisterPages();
        } catch (Exception ignored) {
        }
        try {
            unregisterCors();
        } catch (Exception ignored) {
        }
        resetState();
    }

    /**
     * 清空实例状态（register 失败回滚 / unregister 完成后调用）。
     */
    private void resetState() {
        registered = false;
        owner = null;
        tag = null;
        pages = Collections.emptySet();
        hasCors = false;
    }

    private static String tagOf(String id) {
        return "expansion:" + id;
    }
}
