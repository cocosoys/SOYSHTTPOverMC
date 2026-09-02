package com.dlz.db.inf;

import com.dlz.db.enums.DbOperateEnum;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 按 Map 自动批量追加条件 —— 适合把前端传入的查询参数一键转为 WHERE 子句。
 *
 * <p><b>Key 约定</b>：
 * <ul>
 *   <li>普通 key（如 {@code "status"}）→ {@code status = value}，默认 eq；</li>
 *   <li>带前缀 key：{@code "_<op>_<column>"} → 使用指定操作符，例如
 *       {@code "_gt_age"} → {@code age > value}；{@code "_lk_name"} → {@code name LIKE ?}。
 *       可用操作符见 {@link DbOperateEnum}。</li>
 * </ul>
 *
 * <pre>
 * Map&lt;String, Object&gt; req = new HashMap&lt;&gt;();
 * req.put("status", 1);
 * req.put("_gt_age", 18);
 * req.put("_lk_name", "张");
 * .auto(req);   // status = 1 AND age > 18 AND name LIKE '%张%'
 * </pre>
 *
 * @param <ME> 链式返回类型
 */
public interface ICondAuto<ME extends ICondAuto> extends ICondBase<ME> {
    /**
     * 按 Map 批量追加条件，接受所有 key。详见接口文档。
     *
     * @param req key=列名（可带 {@code _op_} 前缀），value=值
     */
    default ME auto(Map<String, Object> req) {
        return auto(req, (Function<String, Boolean>) null);
    }


    /**
     * 按 Map 批量追加条件，并用自定义过滤器决定是否接受某个 key（去前缀后的真实列名）。
     * <pre>.auto(req, col -> !"password".equals(col));   // 排除 password</pre>
     *
     * @param req     key=列名（可带 {@code _op_} 前缀），value=值
     * @param fillter 接收"去前缀后的列名"，返回 true 表示接受该条件；null 表示全部接受
     */
    default ME auto(Map<String, Object> req, Function<String, Boolean> fillter) {
        if (req != null) {
            for (String key : req.keySet()) {
                Object o = req.get(key);
                DbOperateEnum oprate = DbOperateEnum.eq;
                if (key.startsWith("_")) {
                    int keyIndex = key.substring(1).indexOf("_");
                    if (keyIndex == -1) {
                        continue;
                    }
                    String op = key.substring(1, keyIndex + 1);
                    key = key.substring(op.length() + 2);
                    if (key.isEmpty()) {
                        continue;
                    }
                    oprate = DbOperateEnum.getDbOperateEnum(op);
                }
                if (fillter != null && !fillter.apply(key)) {
                    continue;
                }
                addChildren(oprate.mk(key, o, getTableName()));
            }
        }
        return me();
    }

    /**
     * 按 Map 批量追加条件，并用黑名单排除指定列。
     * <pre>.auto(req, new HashSet&lt;&gt;(Arrays.asList("password", "secret")));</pre>
     *
     * @param req     key=列名（可带 {@code _op_} 前缀），value=值
     * @param exclude 需排除的列名集合（匹配去前缀后的列名）
     */
    default ME auto(Map<String, Object> req, Set<String> exclude) {
        return auto(req, (key) -> exclude != null && !exclude.contains(key));
    }
}
