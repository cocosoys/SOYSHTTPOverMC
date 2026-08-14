package soys.soyshttpovermc.api.event;

import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** API 访问监听事件：PATCH 请求类型（监听本类只收到 PATCH 访问；监听基类收全部）。 */
public class ApiPatchEvent extends ApiAccessEvent {

    public ApiPatchEvent(String path, String apiName, String permission, String ownerPlugin,
                  boolean authenticated, String playerName, Player player, CredentialPresentation credential) {
        super("PATCH", path, apiName, permission, ownerPlugin, authenticated, playerName, player, credential);
    }

    public static HandlerList getHandlerList() {
        return ApiAccessEvent.getHandlerList();
    }

    @Override
    public HandlerList getHandlers() {
        return ApiAccessEvent.getHandlerList();
    }
}
