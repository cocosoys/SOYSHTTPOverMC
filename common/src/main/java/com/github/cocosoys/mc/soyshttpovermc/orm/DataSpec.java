package com.github.cocosoys.mc.soyshttpovermc.orm;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据包描述（DataSpec）——自动运维对"一个插件的数据层"的完整声明。
 *
 * <p>由主插件（自身数据包）与 {@code SoysExpansion}（{@code registerData()} 内部构造）
 * 提供；经 {@code AutoOps} 完成<b>安装 / 更新 / 重装 / 清理</b>事务，受 config
 * {@code auto.ops.*} 控制。</p>
 *
 * <ul>
 *   <li>{@code pluginName} —— 归属标识（主插件名 / Expansion identifier），写 meta 归属；</li>
 *   <li>{@code schemaVersion} —— 当前 schema 版本（>=0；0 = 仅 init.sql + 种子，无迁移；
 *       迁移脚本按 {@code sql/migrations/V&lt;n&gt;__*.sql} 约定执行）；</li>
 *   <li>{@code dataRoots} —— jar 内默认数据文件根（如 {@code "data"}），复制（不覆盖）；</li>
 *   <li>{@code sqlRoots} —— jar 内 SQL 资源根（如 {@code "sql"}），执行 init.sql + migrations；</li>
 *   <li>{@code seedData} —— 种子实体实例列表（按类分组，表空才插入）；</li>
 *   <li>{@code tableClasses} —— 归属表实体类（用于确保建表 / purge 识别；缺省由
 *       {@code seedData} 推断）。</li>
 * </ul>
 */
@Data
public final class DataSpec {

    private String pluginName;
    private int schemaVersion;
    private String[] dataRoots;
    private String[] sqlRoots;
    private List<Object> seedData;
    private Class<?>[] tableClasses;
    /**
     * 合并后的归属表实体类集合：显式 {@code tableClasses} + {@code seedData} 推断（去重，保持声明顺序）。
     */
    public List<Class<?>> mergedTableClasses() {
        Set<Class<?>> out = new LinkedHashSet<>();
        if (tableClasses != null) {
            for (Class<?> c : tableClasses) {
                if (c != null) {
                    out.add(c);
                }
            }
        }
        if (seedData != null) {
            for (Object bean : seedData) {
                if (bean != null) {
                    out.add(bean.getClass());
                }
            }
        }
        return new ArrayList<>(out);
    }
}
