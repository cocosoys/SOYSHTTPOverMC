package com.dlz.db.inf;

import com.dlz.db.convertor.columnname.ColumnNameNative;
import com.dlz.db.convertor.columnname.ColumnNameToLower;
import com.dlz.db.convertor.columnname.ColumnNameToUper;
import com.dlz.db.convertor.columnname.IConvertorToFieldName;
import com.dlz.db.modal.dto.Page;
import com.dlz.db.modal.dto.ResultMap;
import com.dlz.db.support.DBHolder;
import com.dlz.db.support.SqlRunThreadHolder;

import java.util.List;

/**
 * 查询执行器：把构造器渲染出的 SQL 真正交给数据库执行，并按期望类型返回结果。
 *
 * <p><b>命名规则（重要，易错）</b>：
 * <ul>
 *   <li>{@code queryOne / queryList / queryPage}（不带参或带 {@link IConvertorToFieldName}）→ 返回 {@link ResultMap}；</li>
 *   <li>{@code queryXxx(Class)} → 返回指定 Bean 类型；</li>
 *   <li>{@code queryStr / queryLong / queryInt / queryDouble} 及其 List 版本 → 返回单列标量；</li>
 *   <li>{@code count()} → 返回匹配记录数。</li>
 * </ul>
 *
 * <p>{@code IConvertorToFieldName} 参数用于本次查询临时覆盖默认"列名→属性名"转换策略
 * （例如禁用下划线转驼峰），仅作用于当前线程本次调用。
 */
public interface IExecutorQuery<ME extends IExecutorQuery> extends ISqlPara ,IChained<ME>{
    /** 当前绑定的分页对象（由分页构造器设置）。 */
    Page<?> getPage();

    /** 由分页构造器调用，注入分页对象。业务代码通常不直接调用。 */
    void setPage(Page<?> page);

    default ME convert(IConvertorToFieldName convertor) {
        if(convertor != null){
            SqlRunThreadHolder.setConvertorToFieldName(convertor);
        }
        return me();
    }

    default ME convertNative() {
        SqlRunThreadHolder.setConvertorToFieldName(new ColumnNameNative());
        return me();
    }

    default ME convertUpper() {
        SqlRunThreadHolder.setConvertorToFieldName(new ColumnNameToUper());
        return me();
    }

    default ME convertLower() {
        SqlRunThreadHolder.setConvertorToFieldName(new ColumnNameToLower());
        return me();
    }

    /**
     * 查询单条，返回 {@link ResultMap}（支持 {@code getStr("a.b.c")} 深度取值）。
     * <pre>ResultMap row = ...queryOne();</pre>
     */
    default ResultMap queryOne() {
        return DBHolder.doDb(s->s.getMap(this));
    }

    /** 查询列表，返回 {@link ResultMap} 列表。 */
    default List<ResultMap> queryList() {
        return DBHolder.doDb(s->s.getMapList(this));
    }

    /** 分页查询，返回 {@link ResultMap} 分页（附带总数、页号等）。 */
    default Page<ResultMap> queryPage() {
        return DBHolder.doDb(s->s.getPage(this));
    }

    /**
     * 查询单条并映射为指定 Bean。
     * <pre>User u = ...queryOne(User.class);</pre>
     */
    default <T> T queryOne(Class<T> tClass) {
        return DBHolder.doDb(s->s.getBean(this,tClass));
    }

    /** 查询列表并映射为指定 Bean 列表。 */
    default <T> List<T> queryList(Class<T> tClass) {
        return DBHolder.doDb(s->s.getBeanList(this,tClass));
    }

    /** 分页查询并映射为指定 Bean 分页。 */
    default <T> Page<T> queryPage(Class<T> tClass) {
        return DBHolder.doDb(s->s.getPage(this,tClass));
    }


    /** 查询单值字符串（通常 SELECT 单列单行）。 */
    default String queryStr() {
        return DBHolder.doDb(s->s.getStr(this));
    }

    /** 查询字符串列表（单列多行）。 */
    default List<String> queryStrList() {
        return DBHolder.doDb(s->s.getStrList(this));
    }

    /** 查询单值 Long。 */
    default Long queryLong() {
        return DBHolder.doDb(s->s.getLong(this));
    }

    /** 查询 Long 列表（单列多行）。 */
    default List<Long> queryLongList() {
        return DBHolder.doDb(s->s.getLongList(this));
    }


    /** 查询单值 Double。 */
    default Double queryDouble() {
        return DBHolder.doDb(s->s.getDouble(this));
    }

    /** 查询 Double 列表（单列多行）。 */
    default List<Double> queryDoubleList() {
        return DBHolder.doDb(s->s.getDoubleList(this));
    }


    /** 查询单值 Integer。 */
    default Integer queryInt() {
        return DBHolder.doDb(s->s.getInt(this));
    }

    /** 查询 Integer 列表（单列多行）。 */
    default List<Integer> queryIntList() {
        return DBHolder.doDb(s->s.getIntList(this));
    }

    /**
     * 执行 {@code COUNT(*)} 查询，返回匹配记录数。
     * <pre>int n = ...count();</pre>
     */
    default long count() {
        return DBHolder.doDb(s->s.getCnt(this));
    }
}
