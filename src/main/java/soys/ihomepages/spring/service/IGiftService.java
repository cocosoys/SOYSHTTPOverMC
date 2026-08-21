package soys.ihomepages.spring.service;

import org.bukkit.entity.Player;

import soys.soyshttpovermc.util.AjaxResult;

/**
 * 礼包领取服务接口：处理「在线玩家领取礼包」的业务逻辑（去重 + 发放 + 记录）。
 */
public interface IGiftService {

    /** 为指定在线玩家领取礼包；player 为空表示未登录。 */
    AjaxResult claim(Player player);

    /** 查询指定玩家当前的礼包领取状态；player 为空返回未登录。 */
    AjaxResult status(Player player);
}