package com.github.cocosoys.mc.soyshttpovermc.web;

/**
 * web 资源访问结果（可写）：监听器通过 {@link WebResourceAccess#result()} 获取，
 * 用于对<b>本次请求</b>做跳转拦截 / 拒绝 / 内容替换。
 *
 * <p><b>copy 语义</b>：本对象在每次请求时独立新建，写入只影响本次响应，
 * <b>绝不触碰注册表</b>（已登记的 Entry / 内容 / 路由均不受影响）。</p>
 *
 * <p>任一方法调用后自动置 {@code handled}，请求处理链短路使用本结果返回。</p>
 */
public final class ResourceAccessResult {

    private boolean handled;
    private int statusCode;
    private String location;
    private byte[] body;
    private String contentType;

    /**
     * 是否已被监听器处理（短路返回）。
     */
    public boolean isHandled() {
        return handled;
    }

    /**
     * 跳转拦截（默认 302）。拦截后请求直接返回跳转响应，不再继续后续处理。
     *
     * @param location 跳转目标（绝对或相对 URL / 路径）
     */
    public ResourceAccessResult redirect(String location) {
        this.location = location;
        this.handled = true;
        return this;
    }

    /**
     * 跳转拦截（自定义状态码，如 301 / 307 / 308）。
     */
    public ResourceAccessResult redirect(int statusCode, String location) {
        this.statusCode = statusCode;
        this.location = location;
        this.handled = true;
        return this;
    }

    /**
     * 拒绝拦截：返回自定义状态码 + 响应体（如 403 提示页 / 404 空白）。
     *
     * @param statusCode  状态码（如 403 / 404 / 500）
     * @param body        响应体（可为空数组）
     * @param contentType 响应 Content-Type（null → text/plain）
     */
    public ResourceAccessResult deny(int statusCode, byte[] body, String contentType) {
        this.statusCode = statusCode;
        this.body = body == null ? new byte[0] : body;
        this.contentType = contentType;
        this.handled = true;
        return this;
    }

    /**
     * 替换本次响应内容（如对 HTML 注入脚本 / 改写 JS）。仅影响本次请求的响应，不改动已登记内容。
     *
     * @param body        新响应体
     * @param contentType 新 Content-Type（null → 保持原类型或 text/plain）
     */
    public ResourceAccessResult replace(byte[] body, String contentType) {
        this.body = body == null ? new byte[0] : body;
        this.contentType = contentType;
        this.handled = true;
        return this;
    }

    /**
     * 跳转状态码（redirect(int, String) / deny 设置；0 = 未指定 → 默认 302/200）。
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * 跳转目标（redirect 设置）。
     */
    public String location() {
        return location;
    }

    /**
     * 替换 / 拒绝响应体。
     */
    public byte[] body() {
        return body;
    }

    /**
     * 替换 / 拒绝 Content-Type。
     */
    public String contentType() {
        return contentType;
    }
}
