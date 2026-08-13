package soys.soyshttpovermc.api.event;

import org.bukkit.event.Event;

/**
 * 网关事件抽象基类。
 * <ul>
 *   <li>请求类事件（request/denied/served）为<b>异步事件</b>：在 HTTP 处理线程（嗅探器线程池）触发；</li>
 *   <li>凭证下发事件（credential issued）为<b>同步事件</b>：在主线程（命令/登录流程）触发；</li>
 *   <li>API 注册 / 卸载事件（api registered / unregistered）为<b>同步事件</b>：
 *       在插件 onEnable / onDisable（主线程）触发，1.12.2 主线程触发异步事件会抛 IllegalStateException，
 *       故强制同步。可在该事件中获取某插件注册的端点清单（方法 / 路径 / 端点名 / 权限 / 处理器类）。</li>
 * </ul>
 * 其他插件用 Bukkit 标准方式监听：
 * <pre>
 *   getServer().getPluginManager().registerEvents(new Listener() {
 *       &#64;EventHandler
 *       public void onDenied(GatewayAccessDeniedEvent e) {
 *           // ...
 *       }
 *   }, yourPlugin);
 * </pre>
 */
public abstract class GatewayEvent extends Event {

    /** 默认异步（HTTP 处理线程触发；兼容旧字节码/无参构造） */
    protected GatewayEvent() {
        super(true);
    }

    protected GatewayEvent(boolean async) {
        super(async);
    }
}
