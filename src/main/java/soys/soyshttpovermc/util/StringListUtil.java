package soys.soyshttpovermc.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 字符串列表工具：前缀匹配、列表分页打印。
 */
public class StringListUtil {

    /** 默认每页行数（help / pages 等命令的分页内容，可经静态常量统一调整）。 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private StringListUtil() {
    }

    /**
     * 根据输入前缀，匹配list中以前缀开头的字符串（大小写忽略）
     * @param inputStr 输入的前缀字符
     * @param sourceList 候选数据源list
     * @return 匹配结果集合
     */
    public static List<String> matchByPrefix(String inputStr, List<String> sourceList) {
        List<String> result = new ArrayList<>();
        // 入参判空
        if (inputStr == null || inputStr.isEmpty() || sourceList == null || sourceList.isEmpty()) {
            return result;
        }
        String lowerInput = inputStr.toLowerCase();

        for (String item : sourceList) {
            if (item == null) {
                continue;
            }
            if (item.toLowerCase().startsWith(lowerInput)) {
                result.add(item);
            }
        }
        return result;
    }

    // ===== 字符串列表翻页 =====

    /**
     * 翻页结果：页眉 + 当前页内容 + 页尾 组成的可打印行，附带分页元信息。
     */
    public static final class PagedResult {
        /** 整页可打印行（页眉 + 当前页内容 + 页尾）；未命中内容时为空 list。 */
        public final List<String> lines;
        /** 1-based 当前页（已夹到合法区间）。 */
        public final int page;
        public final int totalPages;
        public final int totalItems;
        public final int pageSize;

        PagedResult(List<String> lines, int page, int totalPages, int totalItems, int pageSize) {
            this.lines = lines;
            this.page = page;
            this.totalPages = totalPages;
            this.totalItems = totalItems;
            this.pageSize = pageSize;
        }

        public boolean hasPrev() {
            return page > 1;
        }

        public boolean hasNext() {
            return page < totalPages;
        }
    }

    /**
     * 列表翻页：把 {@code content} 按 {@code pageSize} 分页，取第 {@code page} 页内容，
     * 并以「页眉 + 当前页内容 + 页尾」拼装成可打印行。
     * <b>强制翻页</b>：页号自动夹到 {@code [1, totalPages]}（越界不报错），保证总能返回有效页。
     *
     * @param header   页眉（可 null，不输出）
     * @param content  内容行（null/空视为 0 条）
     * @param footer   页尾（可 null，不输出）
     * @param page     1-based 页号（<=0 视为第 1 页，超过末页取末页）
     * @param pageSize 每页行数（<1 按 {@link #DEFAULT_PAGE_SIZE} 处理）
     */
    public static PagedResult page(String header, List<String> content, String footer, int page, int pageSize) {
        int size = Math.max(1, pageSize);
        int totalItems = content == null ? 0 : content.size();
        int totalPages = Math.max(1, (totalItems + size - 1) / size);
        int cur = Math.max(1, Math.min(page, totalPages));

        List<String> lines = new ArrayList<>();
        if (header != null) {
            lines.add(header);
        }
        if (content != null && !content.isEmpty()) {
            int start = (cur - 1) * size;
            int end = Math.min(start + size, totalItems);
            for (int i = start; i < end; i++) {
                String it = content.get(i);
                lines.add(it == null ? "" : it);
            }
        }
        if (footer != null) {
            lines.add(footer);
        }
        return new PagedResult(lines, cur, totalPages, totalItems, size);
    }

    /**
     * 便捷翻页：使用默认每页行数 {@link #DEFAULT_PAGE_SIZE}。
     */
    public static PagedResult page(String header, List<String> content, String footer, int page) {
        return page(header, content, footer, page, DEFAULT_PAGE_SIZE);
    }

    /**
     * 便捷翻页：返回整页行（无需分页元信息时用）。
     */
    public static List<String> pageLines(String header, List<String> content, String footer, int page, int pageSize) {
        return Collections.unmodifiableList(page(header, content, footer, page, pageSize).lines);
    }
}
