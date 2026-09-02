package com.dlz.kit.util.id;

import java.util.UUID;

/**
 * dlz-kit 最小子集：UUID 工具（无横线）。
 */
public final class UuidUtil {

    private UuidUtil() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
