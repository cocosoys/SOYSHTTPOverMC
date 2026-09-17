package com.github.cocosoys.mc.soyshttpovermc.web;

/**
 * 单个被请求 web 资源的<b>只读快照</b>（copy 语义）：全部字段为值拷贝，
 * 不暴露注册表内部对象——开发者对它的任何修改都不会影响已登记内容。
 */
public final class ResourceAccess {

    private final String path;
    private final String ownerPlugin;
    private final String contentType;
    private final boolean navigable;
    private final String redirectTo;
    private final int redirectCode;

    private ResourceAccess(String path, String ownerPlugin, String contentType,
                           boolean navigable, String redirectTo, int redirectCode) {
        this.path = path;
        this.ownerPlugin = ownerPlugin;
        this.contentType = contentType;
        this.navigable = navigable;
        this.redirectTo = redirectTo;
        this.redirectCode = redirectCode;
    }

    /**
     * 由插件登记项构造快照（{@link WebRegistry.Entry} 值拷贝）。
     */
    public static ResourceAccess of(WebRegistry.Entry entry) {
        if (entry == null) return null;
        return new ResourceAccess(entry.path, entry.ownerPlugin, entry.effectiveContentType(),
                entry.isNavigable(), entry.redirectTo, entry.redirectCode);
    }

    /**
     * 由静态资源 / 网络页构造快照（owner 未知时传 null）。
     */
    public static ResourceAccess of(String path, String contentType, String ownerPlugin) {
        return new ResourceAccess(path, ownerPlugin, contentType, false, null, 0);
    }

    /**
     * 资源路径（去 query 的 cleanPath）。
     */
    public String path() {
        return path;
    }

    /**
     * 归属插件名（静态资源 / 未知 = null）。
     */
    public String ownerPlugin() {
        return ownerPlugin;
    }

    /**
     * Content-Type（如 text/html; charset=utf-8）。
     */
    public String contentType() {
        return contentType;
    }

    /**
     * 是否可导航页面（HTML 页或跳转入口）。
     */
    public boolean navigable() {
        return navigable;
    }

    /**
     * 跳转目标（非空 = 该资源为跳转项）。
     */
    public String redirectTo() {
        return redirectTo;
    }

    /**
     * 跳转状态码（302 / 301 ...；非跳转项为 0）。
     */
    public int redirectCode() {
        return redirectCode;
    }

    @Override
    public String toString() {
        return "ResourceAccess{" + path + (ownerPlugin == null ? "" : " owner=" + ownerPlugin) + "}";
    }
}
