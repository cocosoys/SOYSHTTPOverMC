package soys.soyshttpovermc.bot;

import java.util.Map;

/**
 * 抽象规则控制器：决定一次 API 请求由哪个逻辑队列（tier）处理。
 * 由主 Bot 统一调控各逻辑队列的优先级与排序（不新建物理 Bot 连接，仅模拟“小 Bot”队列）。
 *
 * <p>默认实现 {@link ApiKeyBotRule}：携带 {@code X-API-Key} 的请求走 ADMIN 队列，其余走 COMMON。
 * 可继承扩展（按 path 前缀 / 速率 / 插件归属等分流规则）。</p>
 */
public abstract class BotRuleController {

    /** 根据请求头计算该请求应进入的逻辑队列层级。 */
    public abstract BotTier selectTier(Map<String, String> headers);
}
