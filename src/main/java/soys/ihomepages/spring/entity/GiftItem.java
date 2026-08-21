package soys.ihomepages.spring.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 礼包物品（展示 {@code name} 给前端，执行参数 {@code material}/{@code amount} 仅留服务端）。
 * <p>继承 {@link BaseDisplayEntity}，使用其 name / show / enable 通用字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class GiftItem extends BaseDisplayEntity {

    /** 前端展示图标（路径/URL/Base64数据，由 JsonHomeConfigExporter 转换） */
    private String icon;

    /** 服务端执行：物品材质（如 minecraft:diamond，安全导出时置空） */
    private String material;

    /** 服务端执行：数量（安全导出时使用默认值 0） */
    private int amount = 1;
}