package com.github.cocosoys.mc.soyshttpovermc.web;

import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.util.AuthUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网页访问权限检查器：由 pages.yml 的 {@code pages.permissions}（路径规则）与
 * {@code pages.page.<路径>.permissions}（单页内联，优先）装配，供 {@link WebFrontendHandler}
 * 在伺服 HTML 页/跳转前判定访问者权限。
 *
 * <p>路径匹配复用 {@link AuthUtils#matchesPath} 语义：
 * {@code /admin} 精确（含 /admin/... 子路径）、{@code /console/*} 目录通配、{@code *} 全量。
 * 单页内联权限（page 段）完全替换全局（pages.permissions）同路径配置，不合并。
 * 权限数组为 AND 语义：全部节点通过才放行（判定逻辑在 {@link WebFrontendHandler}）。</p>
 */
public class PagePermissionChecker {

    /** 单页内联权限：精确路径 → 权限列表（pages.page.<路径>.permissions，含 /admin ↔ /admin.html 等价）。 */
    private final Map<String, List<String>> inline;
    /** 全局路径规则：按声明顺序匹配，首个命中生效（pages.permissions）。 */
    private final List<Rule> global;

    public PagePermissionChecker(Map<String, List<String>> inline, Map<String, List<String>> global) {
        Map<String, List<String>> in = new LinkedHashMap<>();
        if (inline != null) {
            for (Map.Entry<String, List<String>> e : inline.entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) continue;
                in.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
            }
        }
        this.inline = Collections.unmodifiableMap(in);
        List<Rule> rules = new ArrayList<>();
        if (global != null) {
            for (Map.Entry<String, List<String>> e : global.entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) continue;
                rules.add(new Rule(e.getKey(), e.getValue()));
            }
        }
        this.global = Collections.unmodifiableList(rules);
    }

    /**
     * 返回指定路径的权限列表；返回 {@code null} 表示未配置权限（放行）。
     * 顺序：内联精确匹配（含 .html 后缀等价）→ 全局规则首个命中。
     */
    public List<String> permissionsFor(String cleanPath) {
        if (cleanPath == null) return null;
        List<String> p = inline.get(cleanPath);
        if (p != null) return p.isEmpty() ? null : p;
        String alt = cleanPath.endsWith(".html")
                ? cleanPath.substring(0, cleanPath.length() - ".html".length())
                : cleanPath + ".html";
        if (!alt.equals(cleanPath)) {
            List<String> p2 = inline.get(alt);
            if (p2 != null) return p2.isEmpty() ? null : p2;
        }
        for (Rule r : global) {
            if (AuthUtils.matchesPath(cleanPath, r.pattern)) {
                return r.permissions;
            }
        }
        return null;
    }

    /**
     * 是否未配置任何权限规则（可跳过校验）。
     */
    public boolean isEmpty() {
        return inline.isEmpty() && global.isEmpty();
    }

    /**
     * 单条全局规则。
     */
    private static final class Rule {
        final String pattern;
        final List<String> permissions;

        Rule(String pattern, List<String> permissions) {
            this.pattern = pattern;
            this.permissions = Collections.unmodifiableList(new ArrayList<>(permissions));
        }
    }
}
