package com.github.cocosoys.mc.soyshttpovermc.api;

import com.github.cocosoys.mc.soyshttpovermc.web.DataHandle;
import com.github.cocosoys.mc.soyshttpovermc.web.WebRegistry;
import lombok.CustomLog;
import lombok.Data;
import lombok.Getter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
 *     &#64;Override protected String resourceRoot() { return "dist"; }  // 可选：自动托管 dist 目录。惰性登记（请求时读盘，支持热替换），优先级：/plugins/插件名称/dist → jar中的dist
 *
 *     // 可选：需要把部分端点挂到有 /plugins 前缀的代理路径（如 /api/plugins/插件名/items）时，
 *     &#64;Override protected java.util.List&lt;Object&gt; buildControllers() {
 *          List&lt;Object&gt; list = new ArrayList<>();
 *          list.add(this);
 *          return list;
 *     }
 *
 *     // 可选：需要把部分端点挂到无 /plugins 前缀的代理路径（如 /api/*）时，
 *     &#64;Override protected java.util.List&lt;Object&gt; buildProxyControllers() {
 *          List&lt;Object&gt; list = new ArrayList<>();
 *          list.add(new SysLogController(new SysLogServiceImpl()));
 *          return list;
 *     }
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
 * ③ 正常登记自动补 /plugins/&lt;插件名&gt; 前缀，{@link #buildProxyControllers()} 代理登记无前缀；
 * 托管 jar 内资源目录（页面打 {@code expansion:identifier} tag，可精确卸载）；
 * ④ {@link #dataRoots()} / {@link #sqlRoots()} / {@link #seedData()} 非空时自动执行数据层初始化
 *    （默认文件复制 + init.sql + 种子数据，受 config auto.ops.* 控制；先于端点注册）；
 * ⑤ {@link #cors()} 非空时自动注册 CORS；⑥ 登记到模块注册表，{@link #unregister()} 时
 * 精确反注册端点 / 页面 / CORS。重复 identifier 拒绝注册。</p>
 */
@CustomLog
@Getter
public abstract class SoysExpansion {

    // ===== 静态门面（静态注册管理器）=====
    private @Getter static volatile SoysHttpOverMcApi api;

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
    private volatile DataHandle dataHandle;            // 数据层登记句柄（unregister 摘登记，数据保留）

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
     * 数据默认文件资源根（jar 内，如 {@code "data"}）：非空时 {@link #registerData()} 自动
     * 复制其下所有文件到数据文件夹同名相对目录——<b>已存在不覆盖</b>（保留运维/运行期修改），
     * 受 config {@code auto.ops.*} 控制。返回 null = 无默认数据文件。
     */
    protected String[] dataRoots() {
        return null;
    }

    /**
     * SQL 初始化脚本资源根（jar 内，如 {@code "sql"}）：非空时 {@link #registerData()} 自动
     * 执行其下 {@code init.sql}——<b>仅 MySQL 方言后端</b>（SQLite 语法不兼容且运行时已自动建表），
     * 脚本须幂等（IF NOT EXISTS / IGNORE，一期无"已执行"标记）。返回 null = 无初始化脚本。
     */
    protected String[] sqlRoots() {
        return null;
    }

    /**
     * 种子数据（实体实例列表）：非空时 {@link #registerData()} 按类分组，
     * <b>对应表为空</b>才批量插入（SQL 自动建表 / YAML 懒生成由 ORM 保证，幂等）。
     * 返回 null / 空 = 无种子数据。
     */
    protected List<Object> seedData() {
        return null;

    }

    /**
     * 当前 schema 版本（>=0）：0 = 仅 init.sql + 种子，无版本迁移。
     * 迁移脚本约定 {@code sql/migrations/V&lt;n&gt;__&lt;描述&gt;.sql}（n 从 1 起）：
     * 全新安装执行 V1..V(schemaVersion)；已安装则按 meta 记录增量执行 V(meta+1)..V(schemaVersion)。
     * 返回当前声明版本即可，升级插件 jar 时提高该值并附带对应迁移脚本，
     * 服务器启动自动完成 schema 升级（受 config auto.ops.update 控制）。
     */
    protected int schemaVersion() {
        return 0;
    }


    /**
     * 页面资源根（如 "dist"）：非空时 {@link #registerPages()} 自动托管——
     * 优先<b>惰性登记</b>插件数据文件夹 {@code plugins/&lt;插件名&gt;/&lt;resourceRoot&gt;}（请求时才读盘，
     * 支持磁盘热替换），该磁盘目录不存在时回退 jar 内同名资源目录。页面统一打
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
    /**
     * CORS 声明值对象（{@link #cors()} 返回数组元素）。
     */
    @Data
    public static final class CorsSpec {

        /** 路径前缀（空或 "/" = 全局）。 */
        public final String pathPrefix;

        /** 允许来源（* 或 https://example.com，逗号分隔多源）。 */
        public final String origin;

        /** 允许方法（GET,POST；null = 默认全方法）。 */
        public final String methods;

        /** 允许头（null = *）。 */
        public final String headers;

        /** 是否允许携带凭据（origin 为 * 时建议 false，否则浏览器拒绝）。 */
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
            log.warnT("log.expansion.duplicate-register", "SoysExpansion 已注册，忽略重复注册: {0}", getClass().getName());
            return false;
        }
        SoysHttpOverMcApi a = api;
        if (a == null) {
            log.warnT("log.expansion.bootstrap-missing", "SoysExpansion 未初始化（主插件未调用 bootstrap），无法注册: {0}", getClass().getName());
            return false;
        }
        String id = getIdentifier();
        if (id == null || id.trim().isEmpty()) {
            log.warnT("log.expansion.identifier-empty", "SoysExpansion 的 getIdentifier() 不能为空: {0}", getClass().getName());
            return false;
        }
        id = id.trim();
        if (REGISTERED.containsKey(id)) {
            log.warnT("log.expansion.identifier-conflict", "SoysExpansion identifier 冲突，已存在同名扩展，拒绝注册: {0}", id);
            return false;
        }

        // 实例状态就绪（钩子可经 getOwner()/api() 访问）
        this.owner = JavaPlugin.getProvidingPlugin(getClass());
        this.tag = tagOf(id);

        // 依次执行单类注册钩子；任一失败回滚已成功部分（不触发 onUnregister）
        // 数据层自动初始化（默认文件/init.sql/种子；受 auto.ops.* 控制）——先于端点注册，
        // 保证业务端点上线时数据已就绪。失败 = 初始化未完成 → 整体注册失败（数据不完整比不可用更危险）
        boolean ok = registerData();
        if (ok) {
            ok = registerControllers();
        }
        if (ok) {
            ok = registerPages();
        }
        if (ok) {
            ok = registerCors();
        }
        if (!ok) {
            rollbackPartial();
            log.warnT("log.expansion.register-failed-rollback", "SoysExpansion 注册失败，已回滚已注册部分: {0}", id);
            return false;
        }

        // 登记注册表 + 生命周期回调（失败整体回滚）
        REGISTERED.put(id, this);
        registered = true;
        try {
            if (!onRegister()) {
                unregister();
                log.warnT("log.expansion.on-register-false", "SoysExpansion onRegister() 返回 false，已整体回滚: {0}", id);
                return false;
            }
        } catch (Exception ex) {
            unregister();
            log.warnT("log.expansion.on-register-error", "SoysExpansion onRegister() 异常，已整体回滚: {0}: {1}", id, ex.getMessage());
            return false;
        }
        log.infoT("log.expansion.registered", "SoysExpansion 已注册: {0} (owner={1}, pages={2}, cors={3})",
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
            unregisterData();
            unregisterControllers();
            unregisterPages();
            unregisterCors();
        } finally {
            REGISTERED.remove(id);
            resetState();
            try {
                onUnregister();
            } catch (Exception ex) {
                log.warnT("log.expansion.on-unregister-error", "SoysExpansion onUnregister() 异常: {0}: {1}", id, ex.getMessage());
            }
        }
        log.infoT("log.expansion.unregistered", "SoysExpansion 已反注册: {0}", id);
        return true;
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
    public final Plugin getOwner() {
        return owner;
    }

    /**
     * 当前 SOYS API 门面（bootstrap 注入；未初始化时为 null）。
     */
    public final SoysHttpOverMcApi getApi() {
        return api;
    }

    /**
     * 待登记端点实例列表（注册与注销共用同一来源）。
     *
     * <p>默认返回本扩展自身（{@code singletonList(this)}）——即"端点在扩展类上书写"的极简形态。
     * 当端点书写在独立 Controller 类中（如 MCERP 的 AuthController / SysUserController / …）
     * 时，覆写本方法返回全部实例列表即可批量注册，{@link #registerCommonController()} 与
     *  会自动遍历本来源，无需重写注册逻辑。</p>
     *
     * @return 待登记实例列表（null / 空列表视为无端点，空操作成功）
     */
    protected List<Object> buildControllers() {
        return Collections.singletonList(this);
    }

    /**
     * 代理登记端点实例列表（无 /plugins/&lt;插件名&gt; 前缀；注册与注销共用同一来源）。
     *
     * <p>默认返回空列表——即默认无代理端点。需要把端点挂到无插件命名空间的路径
     * （如 {@code /api/prod-api/*}）时，覆写本方法返回对应实例列表；与
     * {@link #buildControllers()} 可同时使用（同一实例亦可同时出现在两个来源，
     * 注销按实例幂等，先清后空操作无害）。</p>
     *
     * @return 待代理登记实例列表（null / 空列表视为无代理端点，空操作成功）
     */
    protected List<Object> buildProxyControllers() {
        return Collections.emptyList();
    }

    // ===== 可覆写单类注册钩子（模板方法模式；骨架按序调用）=====

    /**
     * 端点注册骨架：依次执行 {@link #registerCommonController()}（正常登记）与
     * {@link #registerProxyController()}（代理登记）。
     *
     * <p>两个子钩子均可独立覆写：例如既想正常登记又想代理登记时，分别覆写
     * {@link #registerCommonController()} / {@link #registerProxyController()} 并各自执行注册即可，
     * 无需重写本聚合方法。</p>
     *
     * @return true=两类端点均无异常完成；false=任一失败（骨架将整体回滚）
     */
    protected boolean registerControllers() {
        boolean ok = registerCommonController();
        if (ok) {
            ok = registerProxyController();
        }
        return ok;
    }

    /**
     * 正常登记端点（自动补 /plugins/&lt;插件名&gt; 前缀）。
     *
     * <p>默认：遍历 {@link #buildControllers()} 返回的全部实例逐例登记；
     * 空列表视为无端点（空操作成功）。</p>
     *
     * <p><b>覆写场景</b>：需在批量登记之外追加实例时，先调用
     * {@code super.registerController()} 再自行登记额外实例。</p>
     *
     * @return true=无异常完成（端点已登记或空列表跳过）；false=失败（骨架将整体回滚）
     */
    protected boolean registerCommonController() {
        Plugin o = owner;
        SoysHttpOverMcApi a = api;
        if (a == null || o == null) {
            return false;
        }
        try {
            List<Object> controllers = buildControllers();
            if (controllers == null || controllers.isEmpty()) {
                return true; // 未提供端点实例 → 空操作成功
            }
            for (Object c : controllers) {
                if (c == null) {
                    continue;
                }
                a.getApiRegistration().registerController(c, o, false);
            }
            return true;
        } catch (Exception ex) {
            log.warnT("log.expansion.controller-register-fail", "SoysExpansion 端点注册失败（正常登记）: {0}: {1}", getIdentifier(), ex.getMessage());
            return false;
        }
    }

    /**
     * 代理登记端点（无 /plugins/&lt;插件名&gt; 前缀）。
     *
     * <p>默认：遍历 {@link #buildProxyControllers()} 返回的全部实例逐例代理登记；
     * 空列表视为无代理端点（空操作成功）。</p>
     *
     * <p>与 {@link #registerCommonController()} 可同时生效——同一扩展可同时拥有正常命名空间端点
     * 与代理端点。</p>
     *
     * @return true=无异常完成（端点已登记或空列表跳过）；false=失败（骨架将整体回滚）
     */
    protected boolean registerProxyController() {
        Plugin o = owner;
        SoysHttpOverMcApi a = api;
        if (a == null || o == null) {
            return false;
        }
        try {
            List<Object> controllers = buildProxyControllers();
            if (controllers == null || controllers.isEmpty()) {
                return true; // 未提供代理端点实例 → 空操作成功
            }
            for (Object c : controllers) {
                if (c == null) {
                    continue;
                }
                a.getApiRegistration().registerProxyController(c, o, false);
            }
            return true;
        } catch (Exception ex) {
            log.warnT("log.expansion.proxy-register-fail", "SoysExpansion 端点注册失败（代理登记）: {0}: {1}", getIdentifier(), ex.getMessage());
            return false;
        }
    }

    /**
     * 页面资源托管：{@code resourceRoot()} 非空时自动登记页面资源，页面打
     * {@code expansion:&lt;identifier&gt;} tag（供 {@link #unregisterPages()} 精确卸载）。
     *
     * <p><b>惰性登记（磁盘优先）</b>：优先登记插件数据文件夹下的
     * {@code plugins/&lt;插件名&gt;/&lt;resourceRoot&gt;} 磁盘目录——请求时才读盘，支持磁盘热替换；
     * 磁盘目录不存在时回退登记 jar 内同名资源目录（classpath）。两种来源均自动补
     * /web/plugins/&lt;插件名&gt; 前缀。</p>
     *
     * <p>覆写示例：改为逐页 {@code registerPage(...)} 手动登记（此时应同步维护本类页面状态或一并覆写注销钩子）。</p>
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
            Set<WebRegistry.Entry> es;
            // 1) 磁盘优先：数据文件夹/<resourceRoot> 存在 → 惰性登记（请求时读盘，支持热替换）
            java.io.File disk = new java.io.File(o.getDataFolder(), root.trim());
            if (disk.isDirectory()) {
                es = a.getWebPage().registerDirectory(o, "/", disk);
            } else {
                // 2) 回退：jar 内同名资源目录（classpath）
                es = a.getWebPage().registerResourceDirectory(o, "/",
                        o.getClass().getClassLoader(), root.trim());
            }
            if (es != null) {
                for (WebRegistry.Entry e : es) {
                    e.tags.add(t);
                    reg.add(e);
                }
            }
            this.pages = reg;
            return true;
        } catch (Exception ex) {
            log.warnT("log.expansion.pages-register-fail", "SoysExpansion 页面托管失败: {0}: {1}", getIdentifier(), ex.getMessage());
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
            log.warnT("log.expansion.cors-register-fail", "SoysExpansion CORS 注册失败: {0}: {1}", getIdentifier(), ex.getMessage());
            return false;
        }
    }

    // ===== 可覆写单类注销钩子（模板方法模式；骨架按序调用）=====

    /**
     * 端点反注册骨架：依次执行 {@link #unregisterController()}（正常登记侧）与
     * {@link #unregisterProxyController()}（代理登记侧）。
     */
    protected void unregisterControllers() {
        unregisterController();
        unregisterProxyController();
    }

    /**
     * 端点反注册（正常登记侧）：遍历 {@link #buildControllers()} 逐实例卸载。
     *
     * <p>若覆写 {@link #registerCommonController()} 追加注册了列表之外的实例，请一并覆写本方法。</p>
     */
    protected void unregisterController() {
        SoysHttpOverMcApi a = api;
        if (a == null) {
            return;
        }
        try {
            List<Object> controllers = buildControllers();
            if (controllers == null || controllers.isEmpty()) {
                return;
            }
            for (Object c : controllers) {
                if (c == null) {
                    continue;
                }
                a.getApiRegistration().unregisterController(c);
            }
        } catch (Exception ex) {
            log.warnT("log.expansion.controller-unregister-fail", "SoysExpansion 端点反注册失败（正常登记）: {0}: {1}", getIdentifier(), ex.getMessage());
        }
    }

    /**
     * 端点反注册（代理登记侧）：遍历 {@link #buildProxyControllers()} 逐实例卸载
     * （与 {@link #unregisterController()} 幂等——ApiRegistry 按实例整体卸载，不分通道）。
     */
    protected void unregisterProxyController() {
        SoysHttpOverMcApi a = api;
        if (a == null) {
            return;
        }
        try {
            List<Object> controllers = buildProxyControllers();
            if (controllers == null || controllers.isEmpty()) {
                return;
            }
            for (Object c : controllers) {
                if (c == null) {
                    continue;
                }
                a.getApiRegistration().unregisterController(c);
            }
        } catch (Exception ex) {
            log.warnT("log.expansion.proxy-unregister-fail", "SoysExpansion 端点反注册失败（代理登记）: {0}: {1}", getIdentifier(), ex.getMessage());
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
            log.warnT("log.expansion.pages-unregister-fail", "SoysExpansion 页面反注册失败: {0}: {1}", getIdentifier(), ex.getMessage());
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
            log.warnT("log.expansion.cors-unregister-fail", "SoysExpansion CORS 反注册失败: {0}: {1}", getIdentifier(), ex.getMessage());
        }
    }

    // ===== 内部工具 =====

    /**
     * 骨架失败回滚：对"已执行过注册钩子"的类型逐一反注册（不触发 onUnregister，不经过完整 unregister 流程）。
     */
    /**
     * 数据层自动化运维登记：构造 {@link DataSpec}（声明式钩子自动装配）→
     * 经 {@link DataRegistrationApi#register(Plugin, DataSpec)} 完成安装 / 更新事务
     * （meta 表自动识别：首次安装 vs 保留数据更新）。
     *
     * <p><b>生命周期边界</b>：{@link #unregister()} 只摘数据登记（数据保留、meta 不动），
     * <b>永不删除数据</b>；显式清理 / 清空重装经 {@code DataRegistrationApi#purge/reinstall}
     * 由插件自行调用（二次确认由调用方负责）。</p>
     *
     * @return true=成功（或未声明任何数据源 / 开关跳过）；false=失败（骨架将整体回滚）
     */
    private boolean registerData() {
        String[] roots = dataRoots();
        String[] sqls = sqlRoots();
        List<Object> seed = seedData();
        if ((roots == null || roots.length == 0)
                && (sqls == null || sqls.length == 0)
                && (seed == null || seed.isEmpty())
                && schemaVersion() <= 0) {
            return true; // 未声明任何数据源 → 空操作成功
        }
        Plugin o = owner;
        SoysHttpOverMcApi a = api;
        if (o == null || a == null) {
            log.warnT("log.expansion.data-register-no-ready", "SoysExpansion 数据登记失败: owner/api 未就绪: {0}", getIdentifier());
            return false;
        }
        try {
            com.github.cocosoys.mc.soyshttpovermc.orm.DataSpec spec = new com.github.cocosoys.mc.soyshttpovermc.orm.DataSpec();
            spec.setPluginName(getIdentifier());
            spec.setSchemaVersion(schemaVersion());
            spec.setDataRoots(roots);
            spec.setSqlRoots(sqls);
            spec.setSeedData(seed);
            DataHandle h = a.getDataRegistration().register(o, spec);
            if (h == null) {
                // 失败策略已在 DataRegistrationApi.register 内处理（fail=disable → 禁用本插件）
                log.warnT("log.expansion.data-register-fail", "SoysExpansion 数据登记失败: {0}", getIdentifier());
                return false;
            }
            this.dataHandle = h;
            return true;
        } catch (Exception ex) {
            log.warnT("log.expansion.data-register-error", "SoysExpansion 数据登记异常: {0}: {1}", getIdentifier(), ex.getMessage());
            return false;
        }
    }


    /**
     * 摘除数据登记（unregister 时调用）：经 {@link DataRegistrationApi#unregister(DataHandle)}
     * 摘句柄——数据保留、meta 不动（重装同 identifier 自动走保留数据更新）。
     */
    private void unregisterData() {
        DataHandle h = dataHandle;
        if (h == null) {
            return;
        }
        dataHandle = null;
        SoysHttpOverMcApi a = api;
        if (a == null) {
            return;
        }
        try {
            a.getDataRegistration().unregister(h);
        } catch (Exception ex) {
            log.warnT("log.expansion.data-unregister-error", "SoysExpansion 数据登记摘除异常: {0}: {1}", getIdentifier(), ex.getMessage());
        }
    }

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
