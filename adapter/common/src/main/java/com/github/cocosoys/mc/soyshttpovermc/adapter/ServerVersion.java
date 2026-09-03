package com.github.cocosoys.mc.soyshttpovermc.adapter;

import org.bukkit.Bukkit;

import java.util.Objects;

/**
 * MC 服务端版本探测 / 解析 / 区间判定（跨版本通用，编译到最低版本 API）。
 *
 * <p>版本串来自 {@link Bukkit#getBukkitVersion()}（形如 {@code 1.6.4-R0.1} / {@code 1.12.2-R0.1-SNAPSHOT}），
 * 仅取首个 {@code -} 前的版本号解析为 {@code major.minor.patch}。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * ServerVersion v = ServerVersion.current();
 * if (v.below(1, 7)) { /* 1.6.x 分支 *\/ }
 * if (v.within(1, 6, 1, 7)) { /* 1.6.x ~ 1.7.x 段 *\/ }
 * }</pre>
 */
public final class ServerVersion implements Comparable<ServerVersion> {

    private final int major;
    private final int minor;
    private final int patch;
    private final String raw;

    private ServerVersion(int major, int minor, int patch, String raw) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.raw = raw;
    }

    /**
     * 解析当前运行服务端的版本。
     */
    public static ServerVersion current() {
        String bukkitVersion = Bukkit.getBukkitVersion();
        return parse(bukkitVersion);
    }

    /**
     * 从版本串解析（容忍空串 / 非法输入：无法解析时退化为 0.0.0）。
     *
     * @param s 如 {@code 1.7.10-R0.1} / {@code 1.12.2} / {@code 1.6.4-R0.1-SNAPSHOT}
     */
    public static ServerVersion parse(String s) {
        if (s == null || s.isEmpty()) {
            return new ServerVersion(0, 0, 0, s == null ? "" : s);
        }
        String base = s.split("-")[0].trim();
        String[] parts = base.split("\\.");
        int major = num(parts, 0);
        int minor = num(parts, 1);
        int patch = num(parts, 2);
        return new ServerVersion(major, minor, patch, s);
    }

    private static int num(String[] parts, int idx) {
        if (parts == null || idx >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[idx].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 主版本（Minecraft 均为 1）。 */
    public int major() {
        return major;
    }

    /** 次版本（如 1.7.10 的 7）。 */
    public int minor() {
        return minor;
    }

    /** 补丁版本（如 1.7.10 的 10；1.6.4 的 4；无补丁段为 0）。 */
    public int patch() {
        return patch;
    }

    /** 规范化版本串，如 {@code 1.7.10} / {@code 1.6.4} / {@code 1.12.2}。 */
    public String mcVersion() {
        if (patch > 0) {
            return major + "." + minor + "." + patch;
        }
        return major + "." + minor;
    }

    /** 原始 bukkit 版本串（如 {@code 1.7.10-R0.1}）。 */
    public String raw() {
        return raw;
    }

    /**
     * 是否 ≥ 指定 {@code 1.minor.patch} 版本。
     */
    public boolean atLeast(int minor) {
        return atLeast(1, minor);
    }

    /**
     * 是否 ≥ 指定 {@code major.minor} 版本。
     */
    public boolean atLeast(int major, int minor) {
        return compare(major, minor, 0) >= 0;
    }

    /**
     * 是否 &lt; 指定 {@code major.minor} 版本。
     */
    public boolean below(int major, int minor) {
        return compare(major, minor, 0) < 0;
    }

    /**
     * 是否落在 [loMajor.loMinor, hiMajor.hiMinor] 闭区间内（含边界）。
     *
     * <p>上下界均以 minor 段为粒度：{@code within(1,6,1,7)} 覆盖 1.6.x ~ 1.7.x
     * 任意补丁（如 1.7.10）。</p>
     */
    public boolean within(int loMajor, int loMinor, int hiMajor, int hiMinor) {
        return compare(loMajor, loMinor, 0) >= 0 && compareUpTo(hiMajor, hiMinor) <= 0;
    }

    /**
     * 忽略自身 patch 段，仅按 major.minor 与给定 minor 边界比较（用于「到某 minor 段为止」的上界判定）。
     */
    private int compareUpTo(int oMajor, int oMinor) {
        if (major != oMajor) {
            return Integer.compare(major, oMajor);
        }
        return Integer.compare(minor, oMinor);
    }

    private int compare(int oMajor, int oMinor, int oPatch) {
        if (major != oMajor) {
            return Integer.compare(major, oMajor);
        }
        if (minor != oMinor) {
            return Integer.compare(minor, oMinor);
        }
        return Integer.compare(patch, oPatch);
    }

    @Override
    public int compareTo(ServerVersion o) {
        return compare(o.major, o.minor, o.patch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServerVersion)) {
            return false;
        }
        ServerVersion that = (ServerVersion) o;
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return mcVersion();
    }
}
