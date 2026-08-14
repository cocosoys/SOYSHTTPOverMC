package soys.soyshttpovermc.api.impl;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import soys.soyshttpovermc.api.ApiToolkitApi;
import soys.soyshttpovermc.exception.ExceptionBus;
import soys.soyshttpovermc.exception.ToolkitException;
import soys.soyshttpovermc.util.JsonWriter;
import soys.soyshttpovermc.util.LinkMessageUtil;
import soys.soyshttpovermc.web.MimeTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 能力组 4：工具（对象 → JSON、扩展名 → Content-Type、发送链接消息）。
 * 由 {@link SoysHttpOverMcApiImpl} 组合并对外暴露；实现 {@link ApiToolkitApi}。
 */
public class ApiToolkitImpl implements ApiToolkitApi {

    private final Plugin plugin;

    public ApiToolkitImpl(Plugin hostPlugin) {
        this.plugin = hostPlugin;
    }

    @Override
    public String toJson(Object obj) {
        if (obj == null) return "null";
        try {
            return JsonWriter.write(obj);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ToolkitException("E_TO_JSON", "对象序列化 JSON 失败: " + ex.getMessage(), ex));
        }
    }

    @Override
    public String guessContentType(String path) {
        try {
            return MimeTypes.forPath(path);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ToolkitException("E_MIME", "推断 Content-Type 失败(path=" + path + "): " + ex.getMessage(), ex));
        }
    }

    @Override
    public void sendLink(Player player, String url, String display) {
        if (player == null || url == null) return;
        sendOnMain(java.util.Collections.singletonList(player), url, display);
    }

    @Override
    public void sendLink(Collection<? extends Player> players, String url, String display) {
        if (players == null || url == null) return;
        sendOnMain(new ArrayList<>(players), url, display);
    }

    /** 确保 sendMessage 在主线程执行（Bukkit 发包要求）。 */
    private void sendOnMain(List<Player> players, String url, String display) {
        if (players.isEmpty()) return;
        Runnable r = () -> {
            for (Player p : players) LinkMessageUtil.send(p, url, display);
        };
        if (Bukkit.isPrimaryThread()) r.run();
        else plugin.getServer().getScheduler().runTask(plugin, r);
    }
}
