package com.github.cocosoys.mc.soyshttpovermc.util;

import com.github.cocosoys.mc.soyshttpovermc.orm.query.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * 若依风格内存分页工具：全量 List → {@link TableDataInfo}（{code, msg, rows, total}）。
 *
 * <p>与 {@link TableDataInfo} 同包配套：业务层完成筛选/排序后调用
 * {@link #page(List, Integer, Integer)} 做内存分页并包装为前端表格数据；
 * 参数为 RuoYi 契约的 pageNum/pageSize（缺省 1/10，从 1 起）。</p>
 *
 * <pre>
 *   return PageUtils.page(list, pageNum, pageSize);  // {"code":200,...,"rows":[...],"total":N}
 *   return PageUtils.page(list, page);               // 复用 {@link Page} 参数（current/size）
 * </pre>
 */
public final class PageUtils {

    private PageUtils() {
    }

    /**
     * 内存分页：pageNum/pageSize 缺省 1/10；null/空列表直接返回空页（total=0）。
     */
    public static TableDataInfo page(List<?> rows, Integer pageNum, Integer pageSize) {
        if (rows == null || rows.isEmpty()) {
            return TableDataInfo.success(new ArrayList<>(), 0L);
        }
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 ? 10 : pageSize;
        int total = rows.size();
        int from = (pn - 1) * ps;
        int to = Math.min(from + ps, total);
        List<Object> page = new ArrayList<>();
        if (from < total) {
            page.addAll(rows.subList(from, to));
        }
        return TableDataInfo.success(page, total);
    }

    /**
     * 内存分页（{@link Page} 参数版）：current/size 取 Page 字段（缺省 1/10）。
     */
    public static TableDataInfo page(List<?> rows, Page<?> page) {
        if (page == null) {
            return page(rows, (Integer) null, (Integer) null);
        }
        return page(rows, (int) page.getCurrent(), (int) page.getSize());
    }
}
