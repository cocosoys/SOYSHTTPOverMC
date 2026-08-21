package soys.ihomepages.spring.entity;

import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import lombok.Data;

/**
 * 礼包领取记录（ORM 实体，落 {@code data/homepage_gift_claim.yml}）。
 * 主键 = 玩家 UUID；{@code period} 记录领取时的周期模式，用于去重判断。
 */
@TableName("homepage_gift_claim")
@Data
public class GiftClaimRecord {

    @TableId
    private String playerUuid;
    private String playerName;
    private String claimedAt;   // 领取时刻（epoch 毫秒字符串）
    private String period;      // 领取时的周期模式：once / daily / weekly

    public GiftClaimRecord() {
    }

    public GiftClaimRecord(String playerUuid, String playerName, String claimedAt, String period) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.claimedAt = claimedAt;
        this.period = period;
    }
}
