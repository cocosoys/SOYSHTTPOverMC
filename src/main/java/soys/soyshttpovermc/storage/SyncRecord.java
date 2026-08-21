package soys.soyshttpovermc.storage;

import java.io.Serializable;

/**
 * 通用同步记录（多后端存储的数据单元）：
 * 跨服同步数据（令牌黑名单 / 签发审计 / 实例心跳 / 全局密钥等）统一以
 * {@code key -> {type, data(JSON), updatedAt}} 记录存储，YAML 文件 / SQLite / MySQL 三后端同构。
 *
 * <p>key 规则：{@code blacklist:&lt;jti&gt;} / {@code audit:&lt;jti&gt;:&lt;nonce&gt;} /
 * {@code instance:&lt;serverId&gt;} / {@code meta:jwt_secret} 等，由语义层（{@link RecordSyncStorage}）约定。</p>
 */
public class SyncRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String key;
    private String type;
    private String data;     // JSON 字符串（嵌套字段序列化）
    private long updatedAt;

    public SyncRecord() {
    }

    public SyncRecord(String key, String type, String data) {
        this.key = key;
        this.type = type;
        this.data = data;
        this.updatedAt = System.currentTimeMillis();
    }

    /** 反序列化用（updatedAt 从存储读出）。 */
    public SyncRecord(String key, String type, String data, long updatedAt) {
        this.key = key;
        this.type = type;
        this.data = data;
        this.updatedAt = updatedAt;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
