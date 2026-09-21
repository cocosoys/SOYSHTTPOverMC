package com.github.cocosoys.mc.soyshttpovermc.orm;

import com.github.cocosoys.mc.soyshttpovermc.enums.StorageType;
import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platforms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 多后端主辅门面：读取 {@code config.yml storage.backends.*} 启停状态，
 * 按 {@link StorageType#getPriority()} 最高者为主存储，其余已启用后端为辅助存储。
 *
 * <p>语义（与 DATA 门面 / SqlBackendExecutor 装配口径一致）：</p>
 * <ul>
 *   <li>主存储 = 已启用后端中 priority 最高者（MYSQL 30 &gt; SQLITE 20 &gt; YAML 10）；
 *       <b>默认读写只落主存储</b>（单主，不镜像）；</li>
 *   <li>辅助存储 = 其余已启用后端（如 mysql 主 + sqlite 辅、sqlite 主 + yaml 辅），
 *       经 {@code DATA} 带 {@link StorageType} 参数的重载显式读写（"指定类型读写"）；</li>
 *   <li>YAML 未显式启用时按启用处理（data/*.yml 是插件数据兜底落点，config 默认 enabled=true）。</li>
 * </ul>
 */
public final class Backends {

    private Backends() {
    }

    /** 已启用后端（按 priority 降序；空配置时仅 YAML）。 */
    public static List<StorageType> enabled() {
        List<StorageType> out = new ArrayList<>();
        for (StorageType t : StorageType.values()) {
            if (isEnabled(t)) {
                out.add(t);
            }
        }
        out.sort(Comparator.comparingInt(StorageType::getPriority).reversed());
        return out;
    }

    /** 主存储：已启用后端中 priority 最高者；无任何启用时兜底 YAML。 */
    public static StorageType primary() {
        List<StorageType> list = enabled();
        return list.isEmpty() ? StorageType.YAML : list.get(0);
    }

    /** 辅助存储列表（其余已启用后端，priority 降序）。 */
    public static List<StorageType> secondaries() {
        StorageType p = primary();
        List<StorageType> out = new ArrayList<>();
        for (StorageType t : enabled()) {
            if (t != p) {
                out.add(t);
            }
        }
        return out;
    }

    /** 指定后端是否启用（读 storage.backends.&lt;id&gt;.enabled；YAML 缺省视为启用）。 */
    public static boolean isEnabled(StorageType t) {
        ConfigSection sec = Platforms.getOrNull() == null ? null
                : Platforms.getOrNull().getConfig().getSection("storage.backends." + t.getId());
        if (sec == null) {
            return t == StorageType.YAML;
        }
        return sec.getBoolean("enabled", t == StorageType.YAML);
    }
}
