package com.dlz.db.inf;

import com.dlz.db.modal.dto.Order;
import com.dlz.db.modal.dto.Page;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 分页与排序构造器。<br>
 * 同一个 {@link Page} 对象承载页号、页大小、排序列表，多次调用会合并到同一 Page 上。
 *
 * <pre>
 * .page(1, 20)                         // 第1页，每页20条
 * .orderByDesc("create_time")          // 排序
 * .page(1, 20, Order.desc("id"))       // 一次性指定分页 + 排序
 * </pre>
 *
 * @param <T> 链式返回类型
 */
public interface ISqlPage<T extends ISqlPage>{
    /** 当前绑定的分页对象（可能为 null）。 */
    Page getPage();

    /** 直接用一个已有 {@link Page} 对象覆盖当前分页设置。 */
    T page(Page page);

    /** 追加升序排序列。 */
    default T orderByAsc(String... column) {
        return sort(Order.ascs(column));
    }

    /** 追加降序排序列。 */
    default T orderByDesc(String... column) {
        return sort(Order.descs(column));
    }

    /** 等同于 {@link #page(long, long, List)}，参数透传。 */
    default T page(int pageIndex, int size, Order... orders) {
        return page(pageIndex, size, Arrays.asList(orders));
    }
    /**
     * 仅设置分页大小，不改排序。
     * @param size 每页大小，最大 10000；{@code <=0} 则保持原值（默认 20）
     */
    default T limit(int size) {
        return page(1, size, Collections.emptyList());
    }

    /**
     * 设置分页与排序。
     *
     * @param current 页号，从 1 开始；{@code <=0} 时不修改当前页号
     * @param size    每页大小，最大 10000；{@code <=0} 则保持原值（默认 20）
     * @param orders  追加到排序列表末尾的排序规则
     */
    default T page(long current, long size, List<Order> orders) {
        Page pmPage = getPage();
        if (pmPage == null) {
            pmPage = Page.build();
        }
        if(orders!=null && !orders.isEmpty()){
            pmPage.addOrder(orders);
        }
        if (size > 0) {
            pmPage.setSize(size);
        }
        if (current > 0) {
            pmPage.setCurrent(current);
        }
        return page(pmPage);
    }
    /** 可变参数版本的 {@link #page(long, long, List)}。 */
    default T page(long current, long size, Order... orders) {
        return page(current,size,Arrays.asList(orders));
    }

    /** 仅追加排序，不改分页。 */
    default T sort(Order... orders) {
        return sort(Arrays.asList(orders));
    }

    /** 仅追加排序，不改分页。 */
    default T sort(List<Order> orders) {
        return page(0, 0, orders);
    }
}
