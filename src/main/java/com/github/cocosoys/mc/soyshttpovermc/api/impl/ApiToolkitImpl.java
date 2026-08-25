package com.github.cocosoys.mc.soyshttpovermc.api.impl;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.github.cocosoys.mc.soyshttpovermc.api.ApiToolkitApi;
import com.github.cocosoys.mc.soyshttpovermc.exception.ExceptionBus;
import com.github.cocosoys.mc.soyshttpovermc.exception.ToolkitException;
import com.github.cocosoys.mc.soyshttpovermc.util.JsonWriter;
import com.github.cocosoys.mc.soyshttpovermc.util.LinkMessageUtil;
import com.github.cocosoys.mc.soyshttpovermc.web.MimeTypes;

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
            throw ExceptionBus.fire(new ToolkitException("E_TO_JSON", "exception.toolkit.to-json-fail", "对象序列化 JSON 失败: {0}", ex, ex.getMessage()));
        }
    }

    @Override
    public String guessContentType(String path) {
        try {
            return MimeTypes.forPath(path);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ToolkitException("E_MIME", "exception.toolkit.mime-fail", "推断 Content-Type 失败(path={0}): {1}", ex, path, ex.getMessage()));
        }
    }

    @Override
    public void registerMimeType(String ext, String contentType) {
        try {
            MimeTypes.register(ext, contentType);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ToolkitException("E_MIME_REGISTER", "exception.toolkit.mime-register-fail", "注册 Content-Type 失败(ext={0}): {1}", ex, ext, ex.getMessage()));
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
