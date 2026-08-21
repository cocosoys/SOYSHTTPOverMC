package soys.ihomepages.spring.entity;

import lombok.Data;

/**
 * 实时数据实体（在线人数等），由 {@code GET /api/homepage/live} 返回给前端。
 * 与 home.yml 内容分离，独立端点避免配置 JSON 不可缓存。
 */
@Data
public class LiveData {

    private final int online;
    private final int max;
    private final long timestamp;

    public LiveData(int online, int max) {
        this.online = online;
        this.max = max;
        this.timestamp = System.currentTimeMillis();
    }
}
