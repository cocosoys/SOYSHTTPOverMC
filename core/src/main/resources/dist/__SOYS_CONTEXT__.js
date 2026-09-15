/**
 * SOYS 前端契约文件（约定文件名：__SOYS_CONTEXT__.js，存放路径不限，SOYS 按文件名识别）。
 * SOYS 网关在响应本文件时，会把下方占位符 __SOYS_CONTEXT__ 替换为当前服务器的环境原语：
 *   { scheme, host, port, apiPrefix, pluginsPrefix, fullPrefix, pagePrefix, webResourcePrefix }
 * 因此本文件切勿手工填写具体 host/port/前缀——换服务器、改 api-prefix 等均由 SOYS 自动注入。
 *
 * 页面引用：<script src="/__SOYS_CONTEXT__.js"></script>，必须置于其他使用 SOYS_CONTEXT 的脚本之前。
 * 排除声明：window.SOYS_CONTEXT_EXCLUDES = [...] 用于声明 HTML 资源引用改写时排除的前缀
 *           （未声明默认排除 /api；应用自定义 API 前缀如 /prod-api 必须显式声明）。
 * <pre>
 * window.SOYS_CONTEXT = {
 *   "scheme": "http",                          // TLS 启用 → https
 *   "host": "play.example.com",                // public-host → mc.host → server-ip 回退
 *   "port": 25574,                             // public-port → mc.port → server-port 回退
 *   "apiPrefix": "/api",                       // 网关 api-prefix
 *   "pluginsPrefix": "/plugins/SOYSHTTPOverMC",             // 按登记插件 owner 计算；主插件 ""
 *   "apiFullPrefix": "/api/plugins/SOYSHTTPOverMC",          // apiPrefix + pluginsPrefix 归一
 *   "pageFullPrefix": "/web/plugins/SOYSHTTPOverMC",         // 页面 URL 前缀（主插件 ""）
 *   "webResourcePrefix": "/web/plugins/SOYSHTTPOverMC/page"  // 快捷注册资源默认前缀
 * };
 * </pre>
 */
window.SOYS_CONTEXT = __SOYS_CONTEXT__;
window.SOYS_CONTEXT_EXCLUDES = ["/api"];
