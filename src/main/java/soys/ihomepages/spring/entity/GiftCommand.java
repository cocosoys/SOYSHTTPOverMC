package soys.ihomepages.spring.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 礼包执行指令（展示 {@code name} 给前端，执行参数 {@code cmd} 仅留服务端，支持 {player} 占位符）。
 * <p>继承 {@link BaseDisplayEntity}，使用其 name / show / enable 通用字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class GiftCommand extends BaseDisplayEntity {

    /** 服务端执行：控制台指令（{player} 运行时替换为玩家名，安全导出时置空） */
    private String cmd;
}