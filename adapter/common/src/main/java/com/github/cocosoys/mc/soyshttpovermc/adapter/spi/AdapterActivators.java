package com.github.cocosoys.mc.soyshttpovermc.adapter.spi;

import lombok.CustomLog;
import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 版本适配模块发现 / 匹配助手。
 *
 * <p>通过 {@link ServiceLoader} 扫描宿主插件 classpath 上的全部 {@link AdapterActivator}
 * 实现（各版本模块 jar 注册在各自的 {@code META-INF/services} 中），按当前服务器版本
 * 匹配唯一激活者。未匹配时返回 {@code null}（主插件继续走默认 1.12.2 行为，无需适配）。</p>
 */
@CustomLog
public final class AdapterActivators {

    private AdapterActivators() {
    }

    /**
     * 发现全部已注册的版本激活器。
     *
     * @return 非 null 列表；classpath 上无实现时为空列表
     */
    public static List<AdapterActivator> discover() {
        try {
            ServiceLoader<AdapterActivator> loader = ServiceLoader.load(AdapterActivator.class);
            List<AdapterActivator> list = new ArrayList<>();
            for (AdapterActivator a : loader) {
                list.add(a);
            }
            return list;
        } catch (Throwable t) {
            log.warn("[adapter] 发现 AdapterActivator 失败", t);
            return Collections.emptyList();
        }
    }

    /**
     * 为当前服务器版本挑选匹配的激活器。
     *
     * <p>同时命中多个实现时，选择 {@code supports()} 为 true 且支持版本段最窄（minor 最大）者，
     * 保证 1.7.10 优先命中 v1_7x 而非 v1_6x。</p>
     *
     * @param version 当前服务器版本
     * @return 匹配的激活器；无匹配返回 {@code null}
     */
    public static AdapterActivator findFor(ServerVersion version) {
        AdapterActivator best = null;
        int bestMinor = -1;
        for (AdapterActivator a : discover()) {
            try {
                if (a.supports(version)) {
                    int minor = a.id() == null ? 0 : minorOf(a.id());
                    if (minor > bestMinor) {
                        best = a;
                        bestMinor = minor;
                    }
                }
            } catch (Throwable t) {
                log.warn("[adapter] 激活器 " + a + " 的 supports() 抛异常，跳过", t);
            }
        }
        return best;
    }

    private static int minorOf(String id) {
        // id 形如 v1_6x / v1_7x → 取次版本号
        try {
            String s = id.replaceFirst("^v\\d+_", "");
            s = s.replaceFirst("[xX].*$", "");
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
