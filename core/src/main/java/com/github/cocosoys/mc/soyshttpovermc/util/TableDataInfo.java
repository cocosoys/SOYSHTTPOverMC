package com.github.cocosoys.mc.soyshttpovermc.util;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * 若依分页返回体：{code, msg, rows, total}。前端 axios 拦截器按 rows/total 渲染表格。
 *
 * <p>继承 {@link AjaxResult}：SOYS 注解式 API 对 AjaxResult 子类直接平铺序列化
 * （非 AjaxResult 返回值会被包进 data 键，导致前端读不到 rows/total）。</p>
 *
 * <p>由 MCERP 的 TableDataInfo 移植而来（包名归一为主插件 util，与 AjaxResult 同包）；
 * 保留原语义并补充 i18n 变体（*T，与 {@link AjaxResult} 家族一致）与 Java 侧访问器。</p>
 *
 * <pre>
 *   return TableDataInfo.success(rows, total);   // {"code":200,"msg":"查询成功","rows":[...],"total":N}
 *   return TableDataInfo.error("查询失败");        // {"code":500,"msg":"查询失败","rows":[],"total":0}
 * </pre>
 */
public class TableDataInfo extends AjaxResult {

    private static final long serialVersionUID = 1L;

    /**
     * 分页成功：{code:200, msg:"查询成功", rows, total}。
     */
    public static TableDataInfo success(List<?> rows, long total) {
        TableDataInfo t = new TableDataInfo();
        t.put("code", SUCCESS);
        t.put("msg", "查询成功");
        t.put("rows", rows == null ? new ArrayList<>() : rows);
        t.put("total", total);
        return t;
    }

    /**
     * 分页成功（i18n 消息版）：{@code successT(rows, total, "ajax.table.key", "查询成功")}。
     */
    public static TableDataInfo successT(List<?> rows, long total, String i18nKey, String fallback, Object... args) {
        TableDataInfo t = success(rows, total);
        t.put("msg", I18n.resolve(i18nKey, fallback, args));
        return t;
    }

    /**
     * 分页失败：{code:500, msg, rows:[], total:0}。
     */
    public static TableDataInfo error(String msg) {
        TableDataInfo t = new TableDataInfo();
        t.put("code", ERROR);
        t.put("msg", msg);
        t.put("rows", new ArrayList<>());
        t.put("total", 0L);
        return t;
    }

    /**
     * 分页失败（i18n 消息版）：{@code errorT("ajax.table.key", "查询失败")}。
     */
    public static TableDataInfo errorT(String i18nKey, String fallback, Object... args) {
        return error(I18n.resolve(i18nKey, fallback, args));
    }

    // ===== 访问器 =====
    @SuppressWarnings("unchecked")
    public List<Object> getRows() {
        Object v = get("rows");
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    public long getTotal() {
        Object v = get("total");
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }
}
