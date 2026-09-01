package com.dlz.db.modal.dto;

import com.dlz.db.inf.IChained;
import com.dlz.db.util.DbConvertUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Getter
@Setter
@ApiModel(value = "排序对象")
public class Sort<T extends Sort> implements Serializable, IChained<T> {
    private static final long serialVersionUID = 1L;
    @ApiModelProperty(value = "排序")
    private List<Order> orders = new ArrayList<>();

    public Sort(Order... order) {
        this.orders.addAll(Arrays.asList(order));
    }

    public Sort(List<Order> orders) {
        this.orders.addAll(orders);
    }

    public String getSortSql() {
        if (orders == null || orders.isEmpty()) {
            return null;
        }
        return " order by " + orders.stream()
                .map(o -> DbConvertUtil.toDbColumnNames(o.getColumn()) + (o.isAsc() ? " asc" : " desc"))
                .collect(Collectors.joining(","));
    }

    public T removeOrder(Predicate<Order> filter) {
        for (int i = this.orders.size() - 1; i >= 0; --i) {
            if (filter.test(this.orders.get(i))) {
                this.orders.remove(i);
            }
        }
        return me();
    }

    public T addOrder(Order... items) {
        return addOrder(Arrays.asList(items));
    }

    public T addOrder(List<Order> items) {
        List<Order> collect = items.stream().filter(o -> o.getColumn() != null).collect(Collectors.toList());
        if (!collect.isEmpty()) {
            this.orders.addAll(collect);
        }
        return me();
    }

    @Override
    public T me() {
        return (T) this;
    }
}