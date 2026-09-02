package com.dlz.db.ds;

import com.dlz.db.core.ITxExecutor;
import com.dlz.db.modal.DB;
import com.dlz.db.support.DBHolder;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 事务执行器。
 * <p>基于 {@link ITxExecutor} 接口抽象事务执行逻辑，支持不同框架的事务实现（Spring、Solon 等）。</p>
 *
 * <p>典型用法：</p>
 * <pre>
 * DB.Tx.run(() -&gt; { ... });           // 默认数据源 + 事务
 * DB.Tx.run("slave", () -&gt; { ... });  // 切换到 slave + 事务
 * </pre>
 *
 * <p>本类不处理 Spring 的 {@code @Transactional} 传播行为；如需嵌套事务、传播控制，请使用 Spring 自身机制。</p>
 */
@Slf4j
public class DBTx {

    /**
     * 在当前线程的数据源上开启事务执行。
     * <p>当前线程数据源由 {@link DBDynamic#use} 决定，未切换时使用默认数据源。</p>
     */
    public <T> T run(Supplier<T> c) {
        DataSourceConfig config = DB.Dynamic.getCurrentConfig();
        return doRun(config, c);
    }

    /**
     * 在当前线程的数据源上开启事务执行（无返回值版本）。
     */
    public void run(Runnable r) {
        run(() -> {
            r.run();
            return null;
        });
    }

    /**
     * 切换到指定数据源并在其上开启事务执行。
     * <p>等价于 {@code DB.Dynamic.use(name, () -> DB.Tx.run(c))}。</p>
     */
    public <T> T run(String name, Supplier<T> c) {
        return DB.Dynamic.use(name, () -> {
            DataSourceConfig config = DB.Dynamic.getCurrentConfig();
            return doRun(config, c);
        });
    }

    /**
     * 切换到指定数据源并在其上开启事务执行（无返回值版本）。
     */
    public void run(String name, Runnable r) {
        run(name, () -> {
            r.run();
            return null;
        });
    }

    /**
     * 事务核心执行逻辑。
     */
    private <T> T doRun(DataSourceConfig config, Supplier<T> c) {
        ITxExecutor txExecutor = DBHolder.getTxExecutor(config);
        try {
            return txExecutor.execute(c);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("事务执行失败", e);
        }
    }
}
