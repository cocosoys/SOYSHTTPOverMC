package com.github.cocosoys.mc.soyshttpovermc.bot;
import com.github.cocosoys.mc.soyshttpovermc.enums.BotTier;

import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.util.AuthUtils;

import java.util.Map;

/**
 * 默认规则控制器：请求头携带 {@code X-API-Key}（非空）→ ADMIN 队列，否则 COMMON 队列。
 * 头名可通过构造参数覆盖（默认 X-API-Key，大小写不敏感匹配）。
 */
public class ApiKeyBotRule extends BotRuleController {

    private final String apiKeyHeader;

    public ApiKeyBotRule() {
        this("X-API-Key");
    }

    public ApiKeyBotRule(String apiKeyHeader) {
        this.apiKeyHeader = apiKeyHeader == null ? "X-API-Key" : apiKeyHeader;
    }

    @Override
    public BotTier selectTier(Map<String, String> headers) {
        String v = AuthUtils.getHeader(headers, apiKeyHeader);
        return (v != null && !v.isEmpty()) ? BotTier.ADMIN : BotTier.COMMON;
    }
}
