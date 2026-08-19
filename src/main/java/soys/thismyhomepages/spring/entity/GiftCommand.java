package soys.thismyhomepages.spring.entity;

import lombok.Data;

/**
 * 礼包执行指令（展示 {@code name} 给前端，执行参数 {@code cmd} 仅留服务端，支持 {player} 占位符）。
 */
@Data
public class GiftCommand {

    private String name;  // 前端展示名（如「欢迎广播」）
    private String cmd;   // 服务端执行：控制台指令（{player} 运行时替换为玩家名）
}
