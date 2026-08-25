package com.github.cocosoys.mc.soyshttpovermc.orm.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 条件树（与存储无关的中间条件表示）：
 * 条件列表（AND/OR 平铺 + 逻辑分组简化为平铺 AND/OR 链）+ 排序 + 分页。
 * <ul>
 *   <li>YAML 端：对全量记录逐条内存求值（{@link #matches(java.util.function.Function)}）；</li>
 *   <li>SQL 端（二期）：翻译为参数化 WHERE / ORDER BY / LIMIT。</li>
 * </ul>
 * 能力面取低端：正确性两端一致，性能不承诺对等（YAML 全量过滤适合小规模）。
 */
public class ConditionTree {

    /** 排序方向。 */
    public enum Order { ASC, DESC }

    /** 排序项（列名 + 方向）。 */
    public static final class OrderBy {
        public final String column;
        public final Order direction;

        OrderBy(String column, Order direction) {
            this.column = column;
            this.direction = direction;
        }
    }

    /** 单条条件：{@code 列 op 值}，逻辑由 {@code and} 决定（true=AND 连接上一条件）。 */
    public static final class Cond {
        public final String column;   // 列名（列名转换器输出）
        public final Op op;
        public final Object value;    // EQ/NE/GT... 单值；IN/NOT_IN 为 List
        public final boolean and;     // true=AND，false=OR

        Cond(String column, Op op, Object value, boolean and) {
            this.column = column;
            this.op = op;
            this.value = value;
            this.and = and;
        }
    }

    private final List<Cond> conditions = new ArrayList<>();
    private final List<OrderBy> orders = new ArrayList<>();
    private Page<?> page;

    public List<Cond> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    public List<OrderBy> getOrders() {
        return Collections.unmodifiableList(orders);
    }

    public Page<?> getPage() {
        return page;
    }

    public void setPage(Page<?> page) {
        this.page = page;
    }

    public void add(String column, Op op, Object value, boolean and) {
        conditions.add(new Cond(column, op, value, and));
    }

    public void orderBy(String column, Order direction) {
        orders.add(new OrderBy(column, direction));
    }

    /**
     * 内存求值（YAML 后端）：按条件链判定一条记录是否命中。
     *
     * @param columnValue 取列值的函数（未命中列返回 null）
     */
    public boolean matches(java.util.function.Function<String, Object> columnValue) {
        boolean result = true;
        boolean hasAny = false;
        for (Cond c : conditions) {
            Object actual = columnValue.apply(c.column);
            boolean hit = eval(c, actual);
            if (!hasAny) {
                result = hit;
                hasAny = true;
            } else if (c.and) {
                result = result && hit;
            } else {
                result = result || hit;
            }
        }
        return !hasAny || result;
    }

    private static boolean eval(Cond c, Object actual) {
        switch (c.op) {
            case EQ:
                return nullSafeEquals(actual, c.value);
            case NE:
                return !nullSafeEquals(actual, c.value);
            case GT:
                return compare(actual, c.value) > 0;
            case GE:
                return compare(actual, c.value) >= 0;
            case LT:
                return compare(actual, c.value) < 0;
            case LE:
                return compare(actual, c.value) <= 0;
            case LIKE: {
                if (actual == null || c.value == null) return false;
                return actual.toString().contains(c.value.toString().replace("%", ""));
            }
            case IN: {
                if (actual == null || !(c.value instanceof java.util.List)) return false;
                for (Object v : (java.util.List<?>) c.value) {
                    if (nullSafeEquals(actual, v)) return true;
                }
                return false;
            }
            case NOT_IN: {
                if (actual == null || !(c.value instanceof java.util.List)) return false;
                for (Object v : (java.util.List<?>) c.value) {
                    if (nullSafeEquals(actual, v)) return true;
                }
                return true;
            }
            case IS_NULL:
                return actual == null;
            case NOT_NULL:
                return actual != null;
            default:
                return false;
        }
    }

    private static boolean nullSafeEquals(Object a, Object b) {
        if (a == null || b == null) return a == b;
        // 数值跨类型比较（Long vs Integer vs Double）
        if (a instanceof Number && b instanceof Number) {
            double d1 = ((Number) a).doubleValue();
            double d2 = ((Number) b).doubleValue();
            return Double.compare(d1, d2) == 0;
        }
        return a.toString().equals(b.toString());
    }

    private static int compare(Object a, Object b) {
        if (a == null || b == null) return 0;
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        return a.toString().compareTo(b.toString());
    }
}
