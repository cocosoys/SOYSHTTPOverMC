package soys.soyshttpovermc.api.event;

import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** API 访问监听事件：POST 请求类型（监听本类只收到 POST 访问；监听基类收全部）。 */
public class ApiPostEvent extends ApiAccessEvent {

    public ApiPostEvent(String path, String apiName, String permission, String ownerPlugin,
                  boolean authenticated, String playerName, Player player, CredentialPresentation credential) {
        super("POST", path, apiName, permission, ownerPlugin, authenticated, playerName, player, credential);
    }

    public static HandlerList getHandlerList() {
        return ApiAccessEvent.getHandlerList();
    }

    @Override
    public HandlerList getHandlers() {
        return ApiAccessEvent.getHandlerList();
    }
}
