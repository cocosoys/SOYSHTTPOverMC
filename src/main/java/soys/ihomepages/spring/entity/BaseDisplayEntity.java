package soys.ihomepages.spring.entity;

import lombok.Data;

/**
 * 通用展示字段实体。
 * 所有需要在前端展示的实体（GiftItem / GiftCommand 等）继承此类，
 * 提供统一的前端展示控制字段，YAML 中可写可不写，均有默认值。
 */
@Data
public class BaseDisplayEntity {

    /** 前端显示的名称（默认空字符串，由前端fallback到其他字段） */
    private String name = "";

    /** 是否在前端展示该值（默认 true） */
    private boolean show = true;

    /** 是否启用（默认 true，设为 false 则后端逻辑跳过该条目） */
    private boolean enable = true;
}