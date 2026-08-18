package com.dlz.db.core;

import com.dlz.db.core.anno.SqlAction;
import com.dlz.db.exception.DbException;
import com.dlz.db.modal.DB;
import com.dlz.db.util.DbLogUtil;
import com.dlz.kit.exception.SystemException;
import com.dlz.kit.fn.DlzFn2;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class SegmentIdGenerator {

    private final ConcurrentHashMap<String, SegmentBuffer> bufferMap = new ConcurrentHashMap<>();
    private final int defaultStep;

    public SegmentIdGenerator(int defaultStep) {
        this.defaultStep = defaultStep;
    }

    public long nextId(String tableName, long count) {
        if (count <= 0) count = 1;
        String key = DB.Dynamic.getCurrentConfig().getName() + ":" + tableName;
        SegmentBuffer buffer = bufferMap.computeIfAbsent(key, k -> new SegmentBuffer(tableName, defaultStep));
        return buffer.nextIds(count);
    }

    private class SegmentBuffer {
        private final String tableName;
        private final int step;
        private volatile long max;        // 当前号段在 DB 中的 max_id 值（号段上限）
        private final AtomicLong current; // 下一个将要分配的 ID

        SegmentBuffer(String tableName, int step) {
            this.tableName = tableName;
            this.step = step;
            // 初始化时获取第一个号段
            long base = fetchFromDb(step);
            this.max = base + step;
            this.current = new AtomicLong(base + 1);
        }

        // ==================== 数据库操作 ====================

        private int update(String sql, Object... args) {
            return doDb(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    NativeSqlUtil.bindArgs(ps, args);
                    return ps.executeUpdate();
                }
            }, null);
        }

        private <T> T doDb(SqlAction action, DlzFn2<Long, T, String> msg) {
            long t = System.currentTimeMillis();
            T re = null;
            Exception err = null;
            try (Connection conn = DB.Dynamic.getCurrentConfig().getDataSource().getConnection()) {
                re = (T) action.run(conn);
                return re;
            } catch (Exception e) {
                err = e;
                if (e instanceof DbException) throw (DbException) e;
                throw new DbException("sql执行错误:" + e.getMessage(), 1001, e);
            } finally {
                if (msg != null) DbLogUtil.logInfo(msg, t, re, err);
            }
        }

        private Long getId(String sql, Object... args) {
            return doDb(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    NativeSqlUtil.bindArgs(ps, args);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getLong(1);
                    }
                    return null;
                }
            }, null);
        }

        private Long getTableMaxId() {
            try {
                final Long id = getId("SELECT MAX(id) FROM `" + tableName + "`");
                if (id != null) log.info("Real MAX(id) of table {} is {}", tableName, id);
                else log.info("Table {} has no records", tableName);
                return id;
            } catch (Exception e) {
                if (isNoSuchTableError(e)) return 0L;
                log.error("Failed to getTableMaxId for table {}", tableName);
                return 0L;
            }
        }

        /**
         * 纯粹从 DB 获取号段，返回分配前的 oldMax。
         * 【注意】不修改 this.max，由调用方统一修改，避免状态混乱。
         */
        private long fetchFromDb(int step) {
            int retries = 0;
            int maxRetries = 5;
            while (true) {
                try {
                    int rows = 0;
                    // 乐观路径：如果内存中有 max，尝试 CAS
                    if (this.max > 0) {
                        String sql = "UPDATE sys_seq SET id = id + ? WHERE k = ? AND id = ?";
                        rows = update(sql, step, tableName, this.max);
                        if (rows == 1) return this.max; // 成功，返回旧 max 作为新号段起点
                    }

                    // 悲观路径：SELECT 当前值
                    Long currentMax = getId("SELECT id FROM sys_seq WHERE k = ?", tableName);

                    if (currentMax == null) {
                        // 记录不存在，尝试初始化
                        try {
                            Long maxId = getTableMaxId();
                            long realMax = maxId == null ? 0 : maxId;
                            update("INSERT INTO sys_seq (k, id) VALUES (?, ?)", tableName, realMax + step);
                            return realMax;
                        } catch (Exception e) {
                            log.debug("Insert conflict for table {}, retrying...", tableName);
                        }
                    } else {
                        // 记录存在，用查到的真实值 CAS
                        rows = update("UPDATE sys_seq SET id = id + ? WHERE k = ? AND id = ?", step, tableName, currentMax);
                        if (rows == 1) return currentMax;
                        log.debug("CAS conflict for table {} (currentMax={}), retrying...", tableName, currentMax);
                    }

                    if (++retries < maxRetries) Thread.sleep(10 * retries);
                    else throw new DbException("Failed to fetch segment after " + maxRetries + " retries", 1004);

                } catch (Exception e) {
                    if (isNoSuchTableError(e)) initTableSegment();
                    else {
                        log.error("Failed to fetch segment for table {}", tableName, e);
                        if (e instanceof DbException) throw (DbException) e;
                    }
                    if (++retries < maxRetries) {
                        try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    } else throw new DbException("Failed to fetch segment", 1004);
                }
            }
        }

        private boolean isNoSuchTableError(Exception e) {
            final String message = e.getMessage();
            return message != null && (message.contains("no such table") || message.contains("doesn't exist"));
        }

        private void initTableSegment() {
            log.info("Creating sys_seq and initializing for table {}...", tableName);
            update("CREATE TABLE IF NOT EXISTS sys_seq (k VARCHAR(64) PRIMARY KEY, id BIGINT NOT NULL DEFAULT 0, step BIGINT NOT NULL DEFAULT 1000)");
            long realMax = 0;
            Long maxId = getTableMaxId();
            if (maxId != null) realMax = maxId;
            try {
                update("INSERT INTO sys_seq (k, id) VALUES (?, ?)", tableName, realMax);
            } catch (Exception e) {
                log.debug("Initial record for table {} already exists, skipped", tableName);
            }
        }

        // ==================== ID 分发（核心重构） ====================

        /**
         * 批量获取 N 个连续 ID 的终止值。
         */
        long nextIds(long count) {
            while (true) {
                long cur = current.get();
                long remaining = max - cur + 1;

                // 1. 本地充足：纯内存 CAS 无锁分配 (99% 路径)
                if (remaining >= count) {
                    // 尝试原子更新，成功则返回，失败则自旋重试
                    if (current.compareAndSet(cur, cur + count)) {
                        return cur + count - 1;
                    }
                    continue;
                }

                // 2. 本地不足：加锁触发 DB 扩容 (1% 路径)
                synchronized (this) {
                    // 【双重检查 DCL】可能排队期间其他线程已经扩容完毕
                    if (max - current.get() + 1 >= count) {
                        continue;
                    }

                    int loadStep = (int) Math.max(this.step, count);
                    long oldMaxFromDb = fetchFromDb(loadStep);
                    long newMax = oldMaxFromDb + loadStep;

                    long resultEndId;

                    // 【核心亮点：零浪费拼接】
                    if (oldMaxFromDb == this.max) {
                        // DB 连续性成立：旧号段剩余 [cur, max] + 新号段首部 拼接
                        long startId = current.get();
                        resultEndId = startId + count - 1;
                    } else {
                        // DB 连续性被破坏（如外部重置）：降级，放弃旧残余
                        long startId = oldMaxFromDb + 1;
                        resultEndId = startId + count - 1;
                    }

                    // 统一更新内存状态
                    this.current.set(resultEndId + 1);
                    this.max = newMax;

                    return resultEndId;
                }
            }
        }
    }
}