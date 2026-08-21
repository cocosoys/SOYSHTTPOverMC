package soys.soyshttpovermc.storage;

import java.util.Collection;

/**
 * 数据存储后端抽象（数据面为通用 {@link SyncRecord}）。
 * <ul>
 *   <li>方法可能在异步线程被调用，实现需保证线程安全；</li>
 *   <li>任何失败以异常上报，由 {@link StorageManager} 统一降级/镜像处理；</li>
 *   <li>{@link #save} 语义为 upsert（存在覆盖）。</li>
 * </ul>
 * 新增后端只需实现本接口并在 {@link StorageManager#buildStorage} 注册一行。
 */
public interface DataStorage {

    /** 后端类型（含优先级）。 */
    StorageType getType();

    /** 初始化连接 / 建表 / 创建数据文件；失败抛异常（该后端被标记不可用跳过）。 */
    void initialize() throws Exception;

    /** 释放资源（关服/重载时调用）。 */
    void shutdown();

    /** 当前是否可用（不可用的后端被跳过而非崩溃）。 */
    boolean isAvailable();

    /** 简要描述（文件路径或数据库地址）。 */
    String describe();

    // ===== 读（仅主存储被调用） =====

    /** 按 key 读取单条记录；不存在返回 null。 */
    SyncRecord load(String key) throws Exception;

    /** 读取全部记录（迁移/同步/管理用）。 */
    Collection<SyncRecord> loadAll() throws Exception;

    /** 记录总数。 */
    int count() throws Exception;

    // ===== 写（主存储 + 镜像） =====

    /** 保存（upsert）单条记录。 */
    void save(SyncRecord record) throws Exception;

    /** 批量保存（尽量事务/单次落盘）。 */
    void saveAll(Collection<SyncRecord> records) throws Exception;

    /** 按 key 删除单条记录。 */
    void delete(String key) throws Exception;

    /** 清空全部记录（迁移覆盖 / 同步覆盖用）。 */
    void clear() throws Exception;
}
