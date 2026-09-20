package com.github.cocosoys.mc.soyshttpovermc.orm.convertor;

import com.github.cocosoys.mc.soyshttpovermc.orm.meta.FieldMeta;
import com.github.cocosoys.mc.soyshttpovermc.orm.meta.PojoMeta;

import java.lang.reflect.Field;
import java.util.Date;

/**
 * ORM 审计字段自动填充（RuoYi MetaObjectHandler 语义，双后端共用）：
 * <ul>
 *   <li>{@code createTime}（create_time）：插入 / upsert 覆盖写时为 null 才填当前时间（保留历史值）；</li>
 *   <li>{@code updateTime}（update_time）：每次插入 / 覆盖写都刷新为当前时间。</li>
 * </ul>
 * 两个后端（{@code YamlBackendExecutor} / {@code SqlBackendExecutor}）的写路径统一调用，
 * 凡实体含同名 {@code Date} 字段即生效（不依赖继承 {@code BaseEntity}）。
 */
public final class AuditFields {

    private AuditFields() {
    }

    /**
     * 写入前填充审计字段（insert 与 upsert 均调用；null 值字段跳过）。
     *
     * @param bean 待写入实体
     */
    public static void fill(Object bean) {
        if (bean == null) {
            return;
        }
        Date now = new Date();
        PojoMeta meta = PojoMeta.of(bean.getClass());
        fillField(meta, bean, "createTime", now, false);
        fillField(meta, bean, "updateTime", now, true);
    }

    private static void fillField(PojoMeta meta, Object bean, String fieldName, Date now, boolean always) {
        FieldMeta fm = meta.byField(fieldName);
        if (fm == null || fm.type != Date.class) {
            return;
        }
        try {
            Field f = fm.field;
            f.setAccessible(true);
            Object v = f.get(bean);
            if (always || v == null) {
                f.set(bean, now);
            }
        } catch (IllegalAccessException ignored) {
            // 无法写入的字段保持原值
        }
    }
}