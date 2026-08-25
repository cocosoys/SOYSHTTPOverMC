package com.github.cocosoys.mc.soyshttpovermc.orm.query;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页参数（与 dlz-db-core 的 Page 同构；双后端通用）。
 */
public class Page<T> {

    private long current = 1;
    private long size = 10;
    private long total;
    private List<T> records = new ArrayList<>();

    public Page() {
    }

    public Page(long current, long size) {
        if (current > 0) this.current = current;
        if (size > 0) this.size = size;
    }

    public long getCurrent() {
        return current;
    }

    public void setCurrent(long current) {
        this.current = current;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records == null ? new ArrayList<>() : records;
    }

    /** 偏移量（SQL LIMIT 用）。 */
    public long offset() {
        return (current - 1) * size;
    }
}
