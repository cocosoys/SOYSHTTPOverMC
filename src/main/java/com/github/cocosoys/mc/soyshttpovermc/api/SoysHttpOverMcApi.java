package com.github.cocosoys.mc.soyshttpovermc.api;

import com.github.cocosoys.mc.soyshttpovermc.exception.ExceptionBus;

/**
 * 对外集成门面总入口（Holder）。
 *
 * <p>通过 {@code HttpOverMcPlugin.getInstance().getApi()} 获取本实例，
 * 再经各分组 getter 跳转到对应的<b>子接口</b>实例，按能力组调用其方法。
 * 这样每个能力组都是独立、可单独阅读/测试的接口，而非一个庞大的“上帝接口”。</p>
 *
 * <pre>
 *   SoysHttpOverMcApi api = HttpOverMcPlugin.getInstance().getApi();
 *   api.getApiRegistration().registerController(new MyApi());      // 能力组 1
 *   api.getWebPage().registerPage(owner, "/hello", bytes);        // 能力组 2
 *   api.getAuthCredential().issueCredential(subject);             // 能力组 3
 *   api.getToolkit().toJson(obj);                                 // 能力组 4
 *   api.getLogger().logInfo("...");                               // 能力组 5
 *   api.getHttpClient().sendGet("https://example.com");          // 能力组 6
 * </pre>
 *
 * <p>各分组接口：</p>
 * <ul>
 *   <li>{@link ApiRegistrationApi} —— 注解式 API 注册</li>
 *   <li>{@link WebPageApi} —— 网页登记</li>
 *   <li>{@link AuthCredentialApi} —— 鉴权与凭证</li>
 *   <li>{@link ApiToolkitApi} —— 工具（JSON / Content-Type）</li>
 *   <li>{@link HttpClientApi} —— HTTP 请求 / 本地回环</li>
 * </ul>
 *
 * <p>异常统一经 {@link ExceptionBus} 处理：操作失败时门面会构造对应模块的专用异常并
 * {@code ExceptionBus.fire(...)}（结构化日志 + 广播），随后原样抛出；需要精准捕获时按模块
 * catch {@code soys.soyshttpovermc.exception.*} 下的专用异常（{@code ApiException} 等）。</p>
 *
 * <p><b>接入前置</b>：第三方插件须在 plugin.yml 写 {@code softdepend: [SOYSHTTPOverMC]}，
 * 并在自身 onEnable 中注册；其名下 API/网页会在插件禁用时由 {@code ApiLifecycleListener} 自动卸载。</p>
 */
public interface SoysHttpOverMcApi {

    /**
     * 能力组 1：注解式 API 注册
     */
    ApiRegistrationApi getApiRegistration();

    /**
     * 能力组 2：网页登记
     */
    WebPageApi getWebPage();

    /**
     * 能力组 3：鉴权与凭证
     */
    AuthCredentialApi getAuthCredential();

    /**
     * 能力组 4：工具（JSON / Content-Type）
     */
    ApiToolkitApi getToolkit();

    /**
     * 能力组 7：HTTP 请求 / 本地回环
     */
    HttpClientApi getHttpClient();

    /**
     * 能力组 8：扩展接入（登录插件提供者 / /soyshttp 子指令）
     */
    ExtensionApi getExtension();

    /**
     * 注册热重载钩子：提供 /soyshttp 子指令的其它插件实现 {@link ReloadHttpConfigHandler} 后注册，
     * 执行 {@code /soyshttp reload} 时会随本插件一同刷新自身配置（自动检测机制之一）。
     */
    void registerReloadHook(ReloadHttpConfigHandler handler);
}
