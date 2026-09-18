package com.github.cocosoys.mc.soyshttpovermc.orm;

import com.github.cocosoys.mc.soyshttpovermc.orm.executor.SqlBackendExecutor;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.YamlBackendExecutor;
import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platform;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platforms;
import lombok.CustomLog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动运维（Auto Ops）——数据层<b>完全自动化运维</b>（二期：meta 版本表 + 事务）。
 *
 * <p>把"运维人工初始化"提升为全自动，主插件与 {@code SoysExpansion} 附属插件统一走本入口，
 * 行为受 config.yml {@code auto.ops.*} 控制：</p>
 *
 * <pre>
 * auto:
 *   ops:
 *     enabled: true     # 总开关（false = 全部跳过）
 *     init:    true     # 自动初始化（默认文件复制 + init.sql + 种子 + 首次安装的迁移）
 *     update:  true     # 自动更新（已有安装的版本化迁移）
 *     fail:    block    # block=阻止启动（SOYS disable）/ warn=跳过并告警
 * </pre>
 *
 * <p><b>生命周期语义</b>（meta 表 {@code soys_schema_meta} 识别，见 {@link SchemaRegistry}）：</p>
 * <ul>
 *   <li><b>install</b>（全新）：meta 无记录 → 复制默认文件（不覆盖）→ 确保建表 →
 *       init.sql（仅 MySQL，幂等）→ 迁移 V1..V(schemaVersion)（仅 MySQL）→ 种子（表空才插）
 *       → 写插件级 + 表级 meta；</li>
 *   <li><b>更新 / 重装（数据保留）</b>：meta 有记录 → 补复制 + 补列 +
 *       （update 开启时）迁移 V(meta+1)..V(schemaVersion) → 种子补缺 → meta 刷新；
 *       <b>数据永不删除</b>——卸载插件（jar 移除）后重装，meta 仍在 → 自动识别为"保留数据更新"；</li>
 *   <li><b>purge</b>（显式清理，二次确认由调用方负责）：按 meta 识别归属表 → DROP /
 *       删 YAML 文件（他属校验：表仍被其它插件占用时拒绝）→ 删 meta 行。</li>
 * </ul>
 *
 * <p><b>迁移脚本约定</b>：{@code sql/migrations/V&lt;n&gt;__&lt;描述&gt;.sql}，{@code n} 从 1 起；
 * 按版本升序增量执行，<b>每个脚本执行成功后立即落 meta</b>（中途失败重跑不重复执行已成功的脚本）。
 * SQLite 方言跳过 init.sql / 迁移（语法不兼容且运行时 ensureTable 已自动建表）。</p>
 */
@CustomLog
public final class AutoOps {

    /** 迁移脚本文件名模式：{@code V<n>__描述.sql}。 */
    /** 迁移版本目录：{@code V<n>}（n 为数字，大小写不敏感）。 */
    private static final Pattern VERSION_DIR_PATTERN = Pattern.compile("^V(\\d+)$", Pattern.CASE_INSENSITIVE);

    private AutoOps() {
    }

    // ===== config 读取（auto.ops.*） =====

    private static ConfigSection section(Platform p) {
        if (p == null || p.getConfig() == null) {
            return null;
        }
        return p.getConfig().getSection("auto.ops");
    }

    /** 自动运维总开关（默认 true）。 */
    public static boolean enabled(Platform p) {
        ConfigSection s = section(p);
        return s == null || s.getBoolean("enabled", true);
    }

    /** 自动初始化开关（默认 true）。 */
    public static boolean initEnabled(Platform p) {
        ConfigSection s = section(p);
        return s == null || s.getBoolean("init", true);
    }

    /** 自动更新开关（默认 true）：已有安装的版本化迁移。 */
    public static boolean updateEnabled(Platform p) {
        ConfigSection s = section(p);
        return s == null || s.getBoolean("update", true);
    }

    /**
     * 初始化/更新失败策略（{@code auto.ops.fail}，默认 {@code disable}）：
     * <ul>
     *   <li>{@code disable}（默认）= 仅禁用<b>失败的对应插件</b>：主插件失败 → 禁用 SOYS；
     *       附属插件（Expansion）数据失败 → 仅禁用该附属插件，主插件及其它插件继续运行；</li>
     *   <li>{@code warn} = 跳过并告警：继续运行，相关功能可能缺失。</li>
     * </ul>
     * <p>历史 {@code block} 配置自动归入 {@code disable}（等价：主插件自身失败均禁用本体）。</p>
     */
    public static String failAction(Platform p) {
        ConfigSection s = section(p);
        if (s == null) {
            return "disable";
        }
        String fail = s.getString("fail", "disable");
        if (fail != null && "warn".equalsIgnoreCase(fail.trim())) {
            return "warn";
        }
        return "disable";
    }

    // ===== 主事务入口 =====

    /**
     * 安装 / 更新数据包（自动识别：meta 无记录 → 全新安装；有记录 → 保留数据更新）。
     *
     * @param platform 平台抽象（数据文件夹 / config）
     * @param resCl    调用方 ClassLoader（定位其 jar 内 data/sql 资源）
     * @param spec     数据包描述（见 {@link DataSpec}）
     * @return null=成功（或按开关跳过）；非 null=失败原因（调用方按 {@link #failAction} 处理）
     */
    public static String install(Platform platform, ClassLoader resCl, DataSpec spec) {
        String name = spec == null ? "?" : spec.getPluginName();
        try {
            if (!enabled(platform)) {
                log.infoT("log.autoops.skip", "[自动运维] 总开关关闭，跳过 {0} 数据事务", name);
                return null;
            }
            if (!initEnabled(platform)) {
                log.infoT("log.autoops.skip", "[自动运维] auto.ops.init=false，跳过 {0} 数据事务", name);
                return null;
            }
            if (spec == null) {
                return "DataSpec 不能为空";
            }
            boolean existed = SchemaRegistry.isInstalled(name);
            if (!existed && hasLegacyData(spec)) {
                // 老版本升级且 meta 缺失（1.4.0 之前无 meta 表）：
                // 已有业务数据 → 按升级路径处理（cur=0 → 迁移 V1..V(schemaVersion)），
                // 避免把老数据当全新安装而跳过迁移。
                log.infoT("log.autoops.legacy-found",
                        "[自动运维] 检测到 {0} 存在旧数据（meta 缺失），按升级路径执行迁移", name);
                return updateInstall(platform, resCl, spec);
            }
            if (!existed) {
                return freshInstall(platform, resCl, spec);
            }
            return updateInstall(platform, resCl, spec);
        } catch (Exception e) {
            log.warnT("log.autoops.fail", "[自动运维] {0} 数据事务失败: {1}", name, String.valueOf(e.getMessage()));
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    /**
     * 显式清理数据包（purge）：按 meta 识别归属表 → DROP / 删 YAML 文件 →
     * 删 meta 行。他属校验：表仍被其它插件占用时拒绝清理。
     *
     * @return null=成功；非 null=失败原因（含他属拒绝）
     */
    public static String purge(Platform platform, DataSpec spec) {
        String name = spec == null ? "?" : spec.getPluginName();
        try {
            if (!enabled(platform)) {
                return "auto.ops.enabled=false，拒绝 purge";
            }
            if (spec == null || spec.getPluginName() == null) {
                return "DataSpec 或 pluginName 为空";
            }
            Set<String> tables = SchemaRegistry.tablesOf(name);
            for (String t : tables) {
                for (String owner : SchemaRegistry.ownersOf(t)) {
                    if (!name.equals(owner)) {
                        return "表 " + t + " 仍被插件 " + owner + " 使用，拒绝清理（防止误删共享数据）";
                    }
                }
            }
            if (DATA.sqlEnabled()) {
                for (String t : tables) {
                    SchemaRegistry.sql().execSql("DROP TABLE IF EXISTS `" + t + "`");
                }
                log.infoT("log.autoops.purged", "[自动运维] 已清理 SQL 表: {0}（{1} 张）", name, tables.size());
            } else {
                File dataDir = YAML.Pojo.getDataDir();
                int removed = 0;
                for (String t : tables) {
                    File f = new File(dataDir, t + ".yml");
                    if (f.isFile() && f.delete()) {
                        removed++;
                    }
                }
                log.infoT("log.autoops.purged", "[自动运维] 已清理 YAML 文件: {0}（{1} 个）", name, removed);
            }
            SchemaRegistry.removePlugin(name);
            return null;
        } catch (Exception e) {
            log.warnT("log.autoops.fail", "[自动运维] {0} 清理失败: {1}", name, String.valueOf(e.getMessage()));
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    /**
     * 探测是否存在"老版本遗留数据"（meta 缺失时区分全新安装 vs 老用户升级）。
     * YAML：数据文件夹已存在业务表文件；SQL：业务表已存在。
     * 调用时机在复制默认数据 / 建表之前，避免模板与 ensureTable 污染判定。
     */
    private static boolean hasLegacyData(DataSpec spec) {
        if (spec == null) {
            return false;
        }
        for (Class<?> c : spec.mergedTableClasses()) {
            String t = com.github.cocosoys.mc.soyshttpovermc.orm.meta.PojoMeta.of(c).getTableName();
            if (t == null || t.isEmpty()) {
                continue;
            }
            if (DATA.sqlEnabled()) {
                SqlBackendExecutor sql = SqlBackendExecutor.get();
                if (sql != null && sql.tableExists(t)) {
                    return true;
                }
            } else if (new File(YAML.Pojo.getDataDir(), t + ".yml").isFile()) {
                return true;
            }
        }
        return false;
    }

    // ===== 内部：全新安装 / 保留数据更新 =====

    /** 全新安装：默认文件 → 建表 → init.sql（最新结构）→ 种子 → meta=最高版本（不执行迁移，迁移仅老用户升级）。 */
    private static String freshInstall(Platform p, ClassLoader cl, DataSpec spec) throws Exception {
        String name = spec.getPluginName();
        copyDataDefaults(p, cl, spec.getDataRoots());
        ensureTables(spec);
        executeInitSql(cl, spec.getSqlRoots());
        int target = spec.getSchemaVersion();
        // 全新安装：data / init.sql 即为最新版本完整结构，直接标记最高版本；
        // migrations 目录仅用于老用户版本升级（updateInstall 增量执行）。
        List<String> scripts = new ArrayList<>();
        seed(spec.getSeedData());
        recordAllMeta(spec, target, scripts);
        log.infoT("log.autoops.installed",
                "[自动运维] {0} 全新安装完成（schemaVersion={1}，脚本 {2}）", name, target, scripts.size());
        return null;
    }

    /** 保留数据更新：补复制 + 补列 +（update 开启）迁移 + 种子补缺 + meta 刷新。 */
    private static String updateInstall(Platform p, ClassLoader cl, DataSpec spec) throws Exception {
        String name = spec.getPluginName();
        SoysSchemaMeta pluginRow = SchemaRegistry.getPlugin(name);
        int cur = pluginRow == null ? 0 : pluginRow.getSchemaVersion();
        int target = spec.getSchemaVersion();
        copyDataDefaults(p, cl, spec.getDataRoots());
        ensureTables(spec);
        List<String> scripts = SchemaRegistry.parseScripts(pluginRow);
        if (updateEnabled(p) && target > cur) {
            executeMigrations(cl, spec, name, cur, target, scripts);
        } else if (target > cur) {
            log.infoT("log.autoops.update-skip",
                    "[自动运维] auto.ops.update=false，跳过 {0} 迁移（{1}→{2}，数据保留）", name, cur, target);
        }
        seed(spec.getSeedData());
        recordAllMeta(spec, Math.max(cur, target), scripts);
        log.infoT("log.autoops.updated",
                "[自动运维] {0} 数据已就绪（schemaVersion {1}→{2}，保留数据）", name, cur, target);
        return null;
    }

    /** 写插件级 + 表级 meta 记录。 */
    private static void recordAllMeta(DataSpec spec, int version, List<String> scripts) {
        String name = spec.getPluginName();
        SchemaRegistry.recordPlugin(name, version, scripts);
        for (Class<?> c : spec.mergedTableClasses()) {
            String table = com.github.cocosoys.mc.soyshttpovermc.orm.meta.PojoMeta.of(c).getTableName();
            if (table != null && !table.isEmpty()) {
                SchemaRegistry.recordTable(name, table);
            }
        }
    }

    /** 确保归属表存在（SQL ensureTable / YAML 懒生成）：经 DATA.select 触发。 */
    private static void ensureTables(DataSpec spec) {
        for (Class<?> c : spec.mergedTableClasses()) {
            DATA.select(c); // SQL 端 ensureTable；YAML 端空文件即空表
        }
    }

    // ===== ① 默认 data 文件复制（已存在不覆盖） =====

    private static void copyDataDefaults(Platform p, ClassLoader cl, String[] roots) throws IOException {
        if (roots == null) {
            return;
        }
        for (String root : roots) {
            if (root == null || root.trim().isEmpty()) {
                continue;
            }
            copyDataRoot(p, cl, root.trim());
        }
    }

    private static void copyDataRoot(Platform p, ClassLoader cl, String root) throws IOException {
        File jar = jarOf(cl);
        if (jar == null) {
            log.warnT("log.autoops.jar-not-found", "[自动运维] 无法定位调用方 jar（IDE 运行？跳过默认文件复制）");
            return;
        }
        String prefix = root.startsWith("/") ? root.substring(1) : root;
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix += "/";
        }
        File targetDir = new File(p.getDataFolder(), prefix.replace('/', File.separatorChar));
        int copied = 0;
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                if (je.isDirectory()) {
                    continue;
                }
                String name = je.getName();
                if (!name.startsWith(prefix)) {
                    continue;
                }
                String rel = name.substring(prefix.length());
                if (rel.isEmpty()) {
                if (rel.startsWith("migrations/")) {
                    continue; // 迁移脚本不随默认数据复制
                }
                    continue;
                }
                File target = new File(targetDir, rel.replace('/', File.separatorChar));
                if (target.isFile()) {
                    continue; // 已存在不覆盖
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    log.warnT("log.autoops.mkdir-failed", "[自动运维] 无法创建目录: {0}", parent);
                    continue;
                }
                try (InputStream in = jf.getInputStream(je)) {
                    Files.copy(in, target.toPath());
                }
                copied++;
            }
        }
        if (copied > 0) {
            log.infoT("log.autoops.data-copied", "[自动运维] 默认数据文件已复制: {0}（{1} 个，已存在不覆盖）", targetDir, copied);
        }
    }

    /** 经 ClassLoader 定位调用方 jar 文件。 */
    private static File jarOf(ClassLoader cl) {
        try {
            java.net.URL u = cl.getResource("plugin.yml");
            if (u != null && "jar".equals(u.getProtocol())) {
                String p = u.getPath();
                int bang = p.indexOf("!/");
                if (bang > 0) {
                    return new File(new java.net.URI(p.substring(0, bang)));
                }
            }
        } catch (Exception ignored) {
            // 非 jar 运行 → null，调用方跳过
        }
        return null;
    }

    // ===== ② init.sql（仅 MySQL；幂等） =====

    private static void executeInitSql(ClassLoader cl, String[] roots) throws IOException {
        if (roots == null) {
            return;
        }
        for (String root : roots) {
            if (root == null || root.trim().isEmpty()) {
                continue;
            }
            String path = (root.startsWith("/") ? root : "/" + root) + "/init.sql";
            executeSqlResource(cl, path);
        }
    }

    private static void executeSqlResource(ClassLoader cl, String path) throws IOException {
        SqlBackendExecutor sql = SqlBackendExecutor.get();
        if (sql == null || !sql.isAvailable()) {
            log.infoT("log.autoops.sql-skip", "[自动运维] SQL 后端未装配，跳过 {0}（YAML 模式无需 SQL 脚本）", path);
            return;
        }
        if (!"mysql".equalsIgnoreCase(sql.name())) {
            log.infoT("log.autoops.sql-dialect-skip",
                    "[自动运维] 当前 SQL 方言 {0}，跳过 {1}（运行时自动建表已覆盖）", sql.name(), path);
            return;
        }
        InputStream in = cl.getResourceAsStream(path);
        if (in == null) {
            return;
        }
        String script;
        try (InputStream is = in) {
            script = readAll(is);
        }
        int ok = 0;
        for (String stmt : splitStatements(script)) {
            sql.execSql(stmt);
            ok++;
        }
        log.infoT("log.autoops.sql-executed", "[自动运维] SQL 脚本已执行: {0}（{1} 条语句）", path, ok);
    }

    // ===== ③ 版本化迁移（sql/migrations/V{n}__*.sql；仅 MySQL；增量 + 每步落 meta） =====

    /**
     * 执行迁移脚本：枚举 jar 内 {@code sql/migrations/} 下 {@code V<n>__*.sql}，
     * 执行版本 {@code (from, to]} 的脚本（升序），<b>每个脚本执行成功后立即写入 meta</b>。
     *
     * @param scripts 已执行脚本清单（传入可变 List，逐级追加）
     */
    /**
     * 执行迁移脚本：枚举 jar 内 {@code <sqlRoot>/migrations/} 下 {@code V<n>__*.sql / V<n>__*.yml}，
     * 执行版本 {@code (from, to]} 的脚本（升序），<b>每个脚本执行成功后立即写入 meta</b>。
     *
     * <p><b>双通道</b>：MySQL 方言执行 {@code .sql}（ALTER 等 SQL 变更）；YAML 后端执行
     * {@code .yml}（声明式补字段，见 {@link #executeYamlMigration(ClassLoader, String, DataSpec)}）；
     * SQLite 方言跳过迁移（运行时自动建表 + 容忍式补列已覆盖）。</p>
     *
     * @param spec     数据包（其 tableClasses 供 YAML 迁移按表名定位实体类）
     * @param scripts 已执行脚本清单（传入可变 List，逐级追加）
     */
    private static void executeMigrations(ClassLoader cl, DataSpec spec, String pluginName,
                                          int from, int to, List<String> scripts) throws IOException {
        String[] sqlRoots = spec == null ? null : spec.getSqlRoots();
        String[] dataRoots = spec == null ? null : spec.getDataRoots();
        if (to <= 0 || (sqlRoots == null && dataRoots == null)) {
            return;
        }
        boolean sqlMode = DATA.sqlEnabled();
        if (sqlMode) {
            SqlBackendExecutor sql = SqlBackendExecutor.get();
            if (sql == null || !"mysql".equalsIgnoreCase(sql.name())) {
                String dialect = sql == null ? "?" : sql.name();
                log.infoT("log.autoops.sql-dialect-skip",
                        "[自动运维] 当前 SQL 方言 {0}，跳过迁移（运行时自动建表/补列已覆盖）", dialect);
                return;
            }
        }
        List<Migration> migrations = new ArrayList<>();
        if (sqlRoots != null) {
            for (String root : sqlRoots) {
                if (root == null || root.trim().isEmpty()) {
                    continue;
                }
                collectMigrations(cl, root.trim(), migrations);
            }
        }
        if (dataRoots != null) {
            for (String root : dataRoots) {
                if (root == null || root.trim().isEmpty()) {
                    continue;
                }
                collectMigrations(cl, root.trim(), migrations);
            }
        }
        if (migrations.isEmpty()) {
            return;
        }
        migrations.sort((a, b) -> Integer.compare(a.version, b.version));
        for (Migration m : migrations) {
            if (m.version <= from || m.version > to) {
                continue;
            }
            // 通道分流：SQL 模式只跑 .sql；YAML 模式只跑 .yml
            if (sqlMode && m.yaml) {
                continue;
            }
            if (!sqlMode && !m.yaml) {
                continue;
            }
            String path = m.resourcePath;
            log.infoT("log.autoops.migrate-start",
                    "[自动运维] 执行迁移 V{0}: {1}（{2}→{3}）", m.version, path, from, to);
            if (m.yaml) {
                executeYamlMigration(cl, path, spec);
            } else {
                executeSqlResource(cl, path);
            }
            if (!scripts.contains(path)) {
                scripts.add(path);
            }
            SchemaRegistry.recordPlugin(pluginName, m.version, scripts); // 每步落 meta（失败不重跑已成功脚本）
            from = m.version;
        }
    }

    /**
     * 枚举 jar 内 {@code <root>/migrations/V<n>/<表名>.sql|.yml}（版本号 = 子目录名）。
     */
    private static void collectMigrations(ClassLoader cl, String root, List<Migration> out) throws IOException {
        File jar = jarOf(cl);
        if (jar == null) {
            return;
        }
        String base = (root.startsWith("/") ? root.substring(1) : root);
        if (!base.isEmpty() && !base.endsWith("/")) {
            base += "/";
        }
        String prefix = base + "migrations/";
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                if (je.isDirectory()) {
                    continue;
                }
                String name = je.getName();
                if (!name.startsWith(prefix)) {
                    continue;
                }
                String rel = name.substring(prefix.length());
                int slash = rel.indexOf('/');
                if (slash <= 0) {
                    continue; // 必须是 V<n>/<表名>.<ext> 两层
                }
                String verDir = rel.substring(0, slash);
                String file = rel.substring(slash + 1);
                if (file.indexOf('/') >= 0 || file.indexOf("__") >= 0) {
                    continue; // 仅一层文件；拒绝旧 V{n}__ 扁平命名
                }
                Matcher vm = VERSION_DIR_PATTERN.matcher(verDir);
                if (!vm.matches()) {
                    continue;
                }
                int dot = file.lastIndexOf('.');
                if (dot <= 0) {
                    continue;
                }
                String ext = file.substring(dot + 1).toLowerCase();
                if (!"sql".equals(ext) && !"yml".equals(ext)) {
                    continue;
                }
                out.add(new Migration(Integer.parseInt(vm.group(1)), name, "yml".equals(ext)));
            }
        }
    }

    private static final class Migration {
        final int version;
        final String resourcePath;
        final boolean yaml;

        Migration(int version, String resourcePath, boolean yaml) {
            this.version = version;
            this.resourcePath = resourcePath;
            this.yaml = yaml;
        }
    }

    /**
     * 执行 YAML 声明式迁移：{@code add-fields} 补列（给既有记录补充缺失字段默认值）。
     * 目标表从<b>文件名</b>推导（{@code data/migrations/V<n>/<表名>.yml} → 表名）；
     * 脚本内容若含 {@code table} 字段则必须与文件名一致（防误放）。
     *
     * <pre>{@code
     * # data/migrations/V2/soys_perm_user.yml
     * # （目标表 = 文件名，脚本无需 table；若声明则必须一致）
     * add-fields:
     *   vip_level: "0"
     * }</pre>
     *
     * 经 ORM 共享缓存视图读写（{@link YamlBackendExecutor#getConfig(Class)} /
     * {@link YamlBackendExecutor#save(Class)}），与运行时写路径共用锁与实例，落盘后一致。
     */
    private static void executeYamlMigration(ClassLoader cl, String path, DataSpec spec) throws IOException {
        Platform p = Platforms.getOrNull();
        if (p == null) {
            log.warnT("log.autoops.migrate-yaml-skip", "[自动运维] 平台未就绪，跳过 YAML 迁移: {0}", path);
            return;
        }
        InputStream in = cl.getResourceAsStream(path);
        if (in == null) {
            return;
        }
        String content;
        try (InputStream is = in) {
            content = readAll(is);
        }
        // 表名 = 文件名（去 .yml 后缀）
        String file = path.substring(path.lastIndexOf('/') + 1);
        int dot = file.lastIndexOf('.');
        if (dot <= 0) {
            log.warnT("log.autoops.migrate-yaml-bad-name",
                    "[自动运维] YAML 迁移文件名不合法（应为 <表名>.yml）: {0}", path);
            return;
        }
        String table = file.substring(0, dot);
        ConfigSection doc = p.loadYaml(content);
        ConfigSection fields = doc.getSection("add-fields");
        String declared = doc.getString("table");
        if (fields == null) {
            log.warnT("log.autoops.migrate-yaml-bad-add-fields",
                    "[自动运维] YAML 迁移缺少 add-fields: {0}", path);
            return;
        }
        if (declared != null && !declared.trim().isEmpty() && !table.equalsIgnoreCase(declared.trim())) {
            log.warnT("log.autoops.migrate-yaml-bad-table",
                    "[自动运维] YAML 迁移 table 与文件名不一致: {0}（文件 {1} vs 声明 {2}）", path, table, declared);
            return;
        }
        Class<?> cls = tableClassOf(spec, table);
        if (cls == null) {
            log.warnT("log.autoops.migrate-yaml-skip",
                    "[自动运维] YAML 迁移表 {0} 不在数据包表清单中，跳过: {1}", table, path);
            return;
        }
        YamlBackendExecutor yaml = YamlBackendExecutor.get(YAML.Pojo.getDataDir());
        ConfigSection config = yaml.getConfig(cls);
        ConfigSection rows = config.getSection(table);
        if (rows == null) {
            return;
        }
        int patched = 0;
        for (String key : rows.getKeys(false)) {
            ConfigSection row = rows.getSection(key);
            if (row == null) {
                continue;
            }
            for (String f : fields.getKeys(false)) {
                if (row.get(f) == null) {
                    row.set(f, fields.get(f));
                    patched++;
                }
            }
        }
        yaml.save(cls);
        log.infoT("log.autoops.migrate-yaml-done",
                "[自动运维] YAML 迁移已执行: {0}（表 {1}，补 {2} 个字段值）", path, table, patched);
    }

    /** 按表名在数据包表清单中定位实体类（用于 YAML 迁移）。 */
    private static Class<?> tableClassOf(DataSpec spec, String table) {
        if (spec == null) {
            return null;
        }
        for (Class<?> c : spec.mergedTableClasses()) {
            String t = com.github.cocosoys.mc.soyshttpovermc.orm.meta.PojoMeta.of(c).getTableName();
            if (t != null && table.equalsIgnoreCase(t)) {
                return c;
            }
        }
        return null;
    }

    // ===== ④ 种子数据（按类分组，表空才插入） =====

    private static void seed(List<Object> seedData) {
        if (seedData == null || seedData.isEmpty()) {
            return;
        }
        Map<Class<?>, List<Object>> byClass = new LinkedHashMap<>();
        for (Object bean : seedData) {
            if (bean == null) {
                continue;
            }
            byClass.computeIfAbsent(bean.getClass(), k -> new ArrayList<>()).add(bean);
        }
        for (Map.Entry<Class<?>, List<Object>> e : byClass.entrySet()) {
            if (!DATA.select(e.getKey()).isEmpty()) {
                continue; // 表非空 → 跳过（幂等）
            }
            for (Object bean : e.getValue()) {
                DATA.insert(bean);
            }
        }
    }

    // ===== 工具 =====

    /**
     * 简单可靠的 SQL 脚本拆分：按 {@code ;} 分句，正确处理单/双引号、行注释
     * （{@code --}、{@code #}）与块注释（{@code /* ... *}{@code /}）。
     */
    static List<String> splitStatements(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLine = false;
        boolean inBlock = false;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char n = i + 1 < script.length() ? script.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (inSingle) {
                if (c == '\'') {
                    inSingle = false;
                }
                cur.append(c);
                continue;
            }
            if (inDouble) {
                if (c == '"') {
                    inDouble = false;
                }
                cur.append(c);
                continue;
            }
            if (c == '-' && n == '-') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '#') {
                inLine = true;
                continue;
            }
            if (c == '/' && n == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '\'') {
                inSingle = true;
                cur.append(c);
                continue;
            }
            if (c == '"') {
                inDouble = true;
                cur.append(c);
                continue;
            }
            if (c == ';') {
                String s = cur.toString().trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) {
            out.add(tail);
        }
        return out;
    }

    private static String readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 便捷：当前平台（供 Expansion / 主插件装配点使用）。 */
    public static Platform platform() {
        return Platforms.getOrNull();
    }
}
