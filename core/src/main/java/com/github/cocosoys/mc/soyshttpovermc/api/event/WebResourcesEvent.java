package com.github.cocosoys.mc.soyshttpovermc.api.event;

import lombok.Getter;
import com.github.cocosoys.mc.soyshttpovermc.web.MimeTypes;
import com.github.cocosoys.mc.soyshttpovermc.web.ResourceAccess;
import com.github.cocosoys.mc.soyshttpovermc.web.ResourceAccessResult;
import com.github.cocosoys.mc.soyshttpovermc.web.WebResourceAccess;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.List;
import java.util.Map;

/**
 * web 资源事件抽象基类：SOYS web 资源访问全生命周期事件的总入口。
 *
 * <ul>
 *   <li>{@link WebResourcesEvent.WebResourcesAccessEvent} —— 资源加载<b>前</b>（每个被请求资源触发一次；
 *       可通过 getResult() 跳转拦截 / 拒绝 / 替换）</li>
 *   <li>{@link WebResourcesEvent.WebResourcesLoadedEvent} —— 全部资源<b>加载完毕</b>（每请求最多一次；纯通知）</li>
 * </ul>
 */
public abstract class WebResourcesEvent extends Event {

    /**
     * 默认同步构造（web 资源事件在 HTTP worker 线程触发）。
     */
    protected WebResourcesEvent() {
        super(false);
    }

    protected WebResourcesEvent(boolean async) {
        super(async);
    }

    // ================================================================
    // 嵌套事件：web 资源访问（前）/ 全部加载完毕（后）
    // ================================================================






    /**
     * web 资源访问事件（加载<b>前</b>）：客户端请求命中已登记 web 资源（插件登记网页 / 网络页 / 静态资源）、
     * 权限守卫与响应构造<b>之前</b>，在 HTTP worker 线程通过 Bukkit 事件总线发射，
     * <b>每个被请求的资源触发一次</b>。
     *
     * <p><b>唯一 web 资源访问事件</b>：SOYS 只发射本事件（不拆分 Html / Js 子事件），
     * 以减少每次请求的 Bukkit 事件分发开销。细分判断由监听器通过 {@link #isHtml()} / {@link #isJs()}
     * 或 {@link #getPath()} / {@link #getCurrent()} 自行过滤。</p>
     *
     * <p><b>Bukkit 注册方式</b>（附属插件标准接入）：</p>
     * <pre>{@code
     * Bukkit.getPluginManager().registerEvents(new Listener() {
     *     @EventHandler
     *     public void onWebResource(WebResourcesAccessEvent e) {
     *         if (e.isHtml() && e.getPath().startsWith("/web/plugins/Foo/admin")) {
     *             e.getResult().redirect("/login.html");          // 跳转拦截
     *         }
     *         if (e.isJs()) {
     *             e.getResult().deny(403, "forbidden".getBytes(), "text/plain"); // 拒绝
     *         }
     *     }
     * }, plugin);
     * }</pre>
     *
     * <p><b>copy 快照</b>：{@link #getCurrent()} / {@link #getAccessed()} 为值拷贝（不暴露注册表内部），
     * 监听器修改不会影响已登记内容；{@link #getResult()} 为独立可写对象，仅影响本次响应，
     * 调用 redirect / deny / replace 后短路请求（不再触发后续监听器、直接返回）。</p>
     *
     * <p><b>线程模型</b>：在 HTTP worker 线程触发（非主线程）；监听器如需主线程操作请自行调度
     * （如 {@code Bukkit.getScheduler().runTask(plugin, ...)}）。</p>
     */
    @Getter
        public static class WebResourcesAccessEvent extends WebResourcesEvent {

        /**
         * 独立 HandlerList。
         */
        private static final HandlerList HANDLERS = new HandlerList();

        /**
         * HTTP 方法（大写，如 GET / POST）。
         */
        private final String method;
        /**
         * 请求头（原始 Map 快照引用；只读约定，勿修改）。
         */
        private final Map<String, String> headers;
        /**
         * 请求路径（去 query 的 cleanPath，如 web/plugins/Foo/index.html）。
         */
        private final String path;
        /**
         * 原始 query 串（不含 ?；无 query 为 null）。
         */
        private final String query;
        /**
         * 本次请求已访问的所有资源快照列表（请求级累积；正常 1 项）。
         */
        private final List<ResourceAccess> accessed;
        /**
         * 当前被请求的单个资源（= accessed 末尾项；无资源时 null）。
         */
        private final ResourceAccess current;
        /**
         * 可写结果：redirect（跳转拦截）/ deny（拒绝）/ replace（替换内容）；调用后短路本次请求。
         */
        private final ResourceAccessResult result;

        /**
         * @param access 请求上下文快照（含可写 result）
         */
        public WebResourcesAccessEvent(WebResourceAccess access) {
            this.method = access.method();
            this.headers = access.headers();
            this.path = access.path();
            this.query = access.query();
            this.accessed = access.accessed();
            this.current = access.current();
            this.result = access.result();
        }

        /**
         * 是否 .html 资源（路径以 .html 结尾或 Content-Type 为 text/html）。
         */
        public boolean isHtml() {
            String p = path == null ? "" : path;
            String ct = current == null || current.contentType() == null ? "" : current.contentType();
            return MimeTypes.isHtmlPath(p) || ct.startsWith("text/html");
        }

        /**
         * 是否 .js 资源（路径以 .js/.mjs 结尾或 Content-Type 含 javascript；html 优先，不计为 js）。
         */
        public boolean isJs() {
            if (isHtml()) return false;
            String p = path == null ? "" : path;
            String ct = current == null || current.contentType() == null ? "" : current.contentType();
            return p.endsWith(".js") || p.endsWith(".mjs") || ct.contains("javascript");
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        /**
         * Bukkit 事件注册入口。
         */
        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }






    /**
     * web 资源<b>全部加载完毕</b>事件：一次请求所涉及的全部 web 资源（插件登记网页 / 网络页 / 静态资源）
     * 均已完成访问处理后触发——在访问事件 {@link WebResourcesAccessEvent}（加载前）<b>之后</b>、
     * 响应返回前发射，<b>每请求最多一次</b>。
     *
     * <p><b>与访问事件的区别</b>：{@link WebResourcesAccessEvent} 在资源<b>被请求时</b>触发（可通过
     * {@code getResult()} 跳转拦截 / 拒绝 / 替换）；本事件是<b>加载完成通知</b>——纯监听、不含可写 result、
     * 不可干预响应，适合埋点统计、日志聚合、缓存后处理等"事后"场景。</p>
     *
     * <p><b>Bukkit 注册方式</b>：</p>
     * <pre>{@code
     * Bukkit.getPluginManager().registerEvents(new Listener() {
     *     @EventHandler
     *     public void onLoaded(WebResourcesLoadedEvent e) {
     *         for (WebResourceAccess a : e.getAccessed()) {
     *             log.info("loaded: " + a.path() + " -> " + a.result().statusCode());
     *         }
     *     }
     * }, plugin);
     * }</pre>
     *
     * <p><b>参数</b>：{@link #getAccessed()} 携带本次请求<b>全部</b>已加载资源的 {@link WebResourceAccess}
     * 上下文（copy 快照列表；单请求正常 1 项，链式资源访问时累积多项；修改不会影响已登记内容）。</p>
     *
     * <p><b>线程模型</b>：HTTP worker 线程触发（非主线程）；如需主线程操作请自行调度
     * （如 {@code Bukkit.getScheduler().runTask(plugin, ...)}）。</p>
     */
    @Getter
        public static class WebResourcesLoadedEvent extends WebResourcesEvent {

        /**
         * 独立 HandlerList。
         */
        private static final HandlerList HANDLERS = new HandlerList();

        /**
         * 本次请求全部已加载资源的上下文快照列表（按访问顺序；copy，不含注册表内部引用）。
         */
        private final List<WebResourceAccess> accessed;

        /**
         * @param accessed 本次请求全部已加载资源的上下文列表
         */
        public WebResourcesLoadedEvent(List<WebResourceAccess> accessed) {
            this.accessed = accessed;
        }

        /**
         * 本次加载资源总数。
         */
        public int total() {
            return accessed == null ? 0 : accessed.size();
        }

        /**
         * 全部已加载资源的路径列表（按访问顺序）。
         */
        public List<String> paths() {
            List<String> ps = new java.util.ArrayList<>();
            if (accessed != null) {
                for (WebResourceAccess a : accessed) {
                    if (a != null) ps.add(a.path());
                }
            }
            return ps;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        /**
         * Bukkit 事件注册入口。
         */
        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

}
