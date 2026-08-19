package soys.thismyhomepages.spring.entity;

import lombok.Data;

/**
 * 礼包物品（展示 {@code name} 给前端，执行参数 {@code material}/{@code amount} 仅留服务端）。
 */
@Data
public class GiftItem {

    // 前端展示名（如「钻石」）
    private String name;
    // 前端展示图标
    private String icon;
    // 服务端执行：物品材质（如 minecraft:diamond）
    private String material;
    // 服务端执行：数量
    private int amount = 1;
}
