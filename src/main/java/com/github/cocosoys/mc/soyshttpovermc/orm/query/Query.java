package com.github.cocosoys.mc.soyshttpovermc.orm.query;

import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.IBackendExecutor;
import com.github.cocosoys.mc.soyshttpovermc.orm.meta.PojoMeta;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 链式查询构建器（借鉴 dlz-db-core PojoQuery / MyBatis-Plus LambdaQueryWrapper 形态）：
 * {@code YAML.Pojo.selectW(User.class).eq(User::getRole,"admin").like(User::getName,"a").queryBeanList()}
 * <p>条件以 Lambda 引用字段（重构安全），内部累积为 {@link ConditionTree}（双后端通解）。
 * 带 {@code ands/ors} 分组条件（平铺为 AND/OR 逻辑链）。</p>
 */
public class Query<T> {

    private final Class<T> beanClass;
    private final ConditionTree tree = new ConditionTree();
    /** 执行器（由门面注入：YAML.Pojo → YamlBackendExecutor；SQL.Pojo → SqlBackendExecutor）。 */
    private IBackendExecutor executor;

    public Query(Class<T> beanClass) {
        this.beanClass = beanClass;
    }

    /** 门面注入执行器（默认 YAML）。 */
    public Query<T> setExecutor(IBackendExecutor executor) {
        this.executor = executor;
        return this;
    }

    public Class<T> getBeanClass() {
        return beanClass;
    }

    public ConditionTree tree() {
        return tree;
    }

    // ===== 条件 =====

    public Query<T> eq(SFunction<T, ?> column, Object value) {
        return add(column, Op.EQ, value);
    }

    public Query<T> ne(SFunction<T, ?> column, Object value) {
        return add(column, Op.NE, value);
    }

    public Query<T> gt(SFunction<T, ?> column, Object value) {
        return add(column, Op.GT, value);
    }

    public Query<T> ge(SFunction<T, ?> column, Object value) {
        return add(column, Op.GE, value);
    }

    public Query<T> lt(SFunction<T, ?> column, Object value) {
        return add(column, Op.LT, value);
    }

    public Query<T> le(SFunction<T, ?> column, Object value) {
        return add(column, Op.LE, value);
    }

    public Query<T> like(SFunction<T, ?> column, Object value) {
        return add(column, Op.LIKE, value);
    }

    public Query<T> in(SFunction<T, ?> column, Object... values) {
        return add(column, Op.IN, Arrays.asList(values));
    }

    public Query<T> in(SFunction<T, ?> column, List<?> values) {
        return add(column, Op.IN, values);
    }

    public Query<T> isNull(SFunction<T, ?> column) {
        return add(column, Op.IS_NULL, null);
    }

    public Query<T> isNotNull(SFunction<T, ?> column) {
        return add(column, Op.NOT_NULL, null);
    }

    /** OR 连接：与上一条件以 OR 相接。 */
    public Query<T> or(SFunction<T, ?> column, Op op, Object value) {
        tree.add(PojoMeta.columnOf(beanClass, LambdaUtils.resolve(column)), op, value, false);
        return this;
    }

    /** 分组条件（AND 组）：{@code ands(s -> s.eq(...).eq(...))}。 */
    public Query<T> ands(Consumer<Query<T>> group) {
        Query<T> g = new Query<>(beanClass);
        group.accept(g);
        for (ConditionTree.Cond c : g.tree().getConditions()) {
            tree.add(c.column, c.op, c.value, true);
        }
        return this;
    }

    /** 分组条件（OR 组）：组内条件以 OR 连接。 */
    public Query<T> ors(Consumer<Query<T>> group) {
        Query<T> g = new Query<>(beanClass);
        group.accept(g);
        boolean first = true;
        for (ConditionTree.Cond c : g.tree().getConditions()) {
            tree.add(c.column, c.op, c.value, first);
            first = false;
        }
        return this;
    }

    private Query<T> add(SFunction<T, ?> column, Op op, Object value) {
        tree.add(PojoMeta.columnOf(beanClass, LambdaUtils.resolve(column)), op, value, true);
        return this;
    }

    // ===== 排序 / 分页 =====

    public Query<T> orderByAsc(SFunction<T, ?> column) {
        tree.orderBy(PojoMeta.columnOf(beanClass, LambdaUtils.resolve(column)), ConditionTree.Order.ASC);
        return this;
    }

    public Query<T> orderByDesc(SFunction<T, ?> column) {
        tree.orderBy(PojoMeta.columnOf(beanClass, LambdaUtils.resolve(column)), ConditionTree.Order.DESC);
        return this;
    }

    public Query<T> page(Page<?> page) {
        tree.setPage(page);
        return this;
    }

    // ===== 执行（委托后端） =====

    /** 查询单条（取第一条）；无结果返回 null。 */
    public T queryBean() {
        List<T> list = queryBeanList();
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    /** 查询列表。 */
    public List<T> queryBeanList() {
        IBackendExecutor e = executor;
        if (e == null) {
            e = YAML.Pojo.executor();
        }
        return e.selectByTree(beanClass, tree);
    }

    /** 分页查询。 */
    public Page<T> queryBeanPage() {
        IBackendExecutor e = executor;
        if (e == null) {
            e = YAML.Pojo.executor();
        }
        return e.selectPageByTree(beanClass, tree);
    }

    // ===== 内部 =====

    /** 列值提取函数（YAML 端内存过滤用；字段名→列名）。 */
    public Function<String, Object> columnValueAccessor(T bean) {
        return column -> {
            java.lang.reflect.Field f = PojoMeta.fieldOfColumn(beanClass, column);
            if (f == null) return null;
            try {
                f.setAccessible(true);
                return f.get(bean);
            } catch (IllegalAccessException e) {
                return null;
            }
        };
    }
}
