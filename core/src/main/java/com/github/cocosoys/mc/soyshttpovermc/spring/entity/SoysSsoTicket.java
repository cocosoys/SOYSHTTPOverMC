package com.github.cocosoys.mc.soyshttpovermc.spring.entity;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import lombok.Data;

/**
 * SSO 一次性登录票据实体（ORM，落 {@code data/soys_sso_ticket.yml} 或 SQL 表 {@code soys_sso_ticket}）。
 *
 * <p>用途：
 * <ul>
 *   <li><b>游戏内免登链接</b>（原有 ticket link）：游戏内签发票据 → 浏览器带票访问登录页换 cookie；</li>
 *   <li><b>跨域名 SSO 回跳</b>：主域名登录成功签发票据 → 302 回目标域名带 {@code ?ticket=}
 *       → 目标服消费票据种本域 cookie。</li>
 * </ul>
 *
 * <p>语义：一次性、短 TTL（默认 60s）。消费时不删行，而是置 {@link #consumedAt}（保留审计）；
 * 重复消费 / 已过期 / 已消费一律视为无效。过期行由读取方惰性清理（短命票，无需定时任务）。</p>
 *
 * <p>群组服共享：多子服接同一 MySQL 时票据全局可消费（跨域名 SSO）；YAML 后端 = 独立服，
 * 票据仅本服有效（跨域 SSO 需 MySQL，符合降级预期）。</p>
 */
@TableName("soys_sso_ticket")
@Data
public class SoysSsoTicket extends BaseEntity {

    /**
     * 自增主键（Long；SQL 端 AUTO_INCREMENT / SQLite AUTOINCREMENT，YAML 端由 ORM 分配）。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 票据串（{@code tk_} 前缀随机串；业务查询键，全局唯一）。
     */
    private String ticket;

    /**
     * 绑定玩家名（签发时确定；消费后据此换发会话）。
     */
    private String subject;

    /**
     * 签发时记录的目标回跳地址（跨域 SSO 用；游戏内链接场景为 null）。
     */
    private String redirectUrl;

    /**
     * 签发服标识（审计；群组服 server-name / 独立服 standalone-&lt;host&gt;:&lt;port&gt;）。
     */
    private String issuedServer;

    /**
     * 客户端 IP（可选绑定；null=不校验）。
     */
    private String clientIp;

    /**
     * 消费时间戳（毫秒；null=未消费，非 null=已消费，重复消费拒绝）。
     */
    private Long consumedAt;

    /**
     * 过期时间戳（毫秒；超过即视为无效）。
     */
    private Long expiresAt;

    public SoysSsoTicket() {
    }
}
