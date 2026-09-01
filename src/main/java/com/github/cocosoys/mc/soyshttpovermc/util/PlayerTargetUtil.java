package com.github.cocosoys.mc.soyshttpovermc.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 解析“目标玩家”参数：支持原生指令玩家选择器与玩家名。
 *
 * <p>Spigot 1.12.2 未提供 {@code Bukkit.selectEntities}，故此处手动解析最常用的基础选择器
 * （{@code @a} / {@code @p} / {@code @r} / {@code @e} / {@code @s}）；
 * 选择器后带的 {@code [条件]} 在本版本忽略（best-effort），仅取对应范围内的在线玩家。</p>
 */
public final class PlayerTargetUtil {

    private static final Random RND = new Random();

    private PlayerTargetUtil() {
    }

    /**
     * 把参数解析为玩家列表；非 @ 开头按玩家名精确/模糊匹配。
     */
    public static List<Player> resolve(CommandSender sender, String arg) {
        if (arg == null || arg.isEmpty()) return Collections.emptyList();
        if (arg.charAt(0) == '@') {
            String base = arg;
            int br = base.indexOf('[');
            if (br >= 0) base = base.substring(0, br); // 1.12.2 忽略 [..] 条件
            switch (base) {
                case "@a":
                case "@e":
                    return new ArrayList<>(Bukkit.getOnlinePlayers());
                case "@p": {
                    if (sender instanceof Player) return Collections.singletonList((Player) sender);
                    List<Player> ps = new ArrayList<>(Bukkit.getOnlinePlayers());
                    return ps.isEmpty() ? Collections.emptyList() : Collections.singletonList(ps.get(0));
                }
                case "@r": {
                    List<Player> ps = new ArrayList<>(Bukkit.getOnlinePlayers());
                    return ps.isEmpty() ? Collections.emptyList() : Collections.singletonList(ps.get(RND.nextInt(ps.size())));
                }
                case "@s":
                    return sender instanceof Player ? Collections.singletonList((Player) sender) : Collections.emptyList();
                default:
                    return Collections.emptyList();
            }
        }
        Player p = Bukkit.getPlayer(arg);
        if (p != null) return Collections.singletonList(p);
        return Collections.emptyList();
    }
}
