-- ============================================================================
-- SOYSHTTPOverMC · 初始化建表脚本
-- 适用: MySQL 5.x / 8.x / MariaDB（SQLite 亦兼容，见下方说明）
-- ----------------------------------------------------------------------------
-- 【重要说明】
-- 1) 插件运行时若表不存在会自动执行 CREATE TABLE IF NOT EXISTS
--    （SqlBackendExecutor.ensureTable + 容忍式补列 ALTER），本脚本用于
--    运维提前建表 / DBA 核对 schema / 批量授权，二者不冲突，可重复执行。
-- 2) 列名规则: 实体字段驼峰转小写下划线（createdAt → created_at），
--    与 YAML 端 data/<表名>.yml 的键名完全一致（双端同构）。
-- 3) 类型映射: String → VARCHAR(255)；主键 String → VARCHAR(64)
--    （utf8mb4 下 VARCHAR(255) 主键索引超长 1020B > 1000B）；
--    int → INT；boolean → TINYINT；Date/long → BIGINT；嵌套 → TEXT(JSON)。
-- 4) 表名前缀: 若配置了 storage.backends.mysql.table-prefix（默认空），
--    请将下方所有表名统一加上该前缀（如 soys_perm_user → <前缀>soys_perm_user）。
-- 5) SQLite 兼容: SQLite 不支持 COMMENT 子句与 TINYINT（但类型宽松，TINYINT 可接受），
--    如需在 SQLite 手工执行，请删除各列的 COMMENT 子句；插件运行时会自动建表，
--    不依赖本脚本。
-- 6) MySQL 建议库字符集 utf8mb4:
--    CREATE DATABASE IF NOT EXISTS minecraft DEFAULT CHARACTER SET utf8mb4;
--    执行时请使用: mysql --default-character-set=utf8mb4 -u<用户> -p < 库名 < init.sql
-- ============================================================================

-- ---------- 用户级权限表（主键 = 玩家 UUID） ----------
CREATE TABLE IF NOT EXISTS `soys_perm_user` (
  `uuid`       VARCHAR(64)  PRIMARY KEY COMMENT '玩家 UUID（主键，区分大小写）',
  `player`     VARCHAR(255)          COMMENT '玩家名（冗余；改名后以 uuid 为准）',
  `expiry`     VARCHAR(255)          COMMENT '用户级权限过期时间（毫秒时间戳字符串；空 = 不过期）',
  `created_at` VARCHAR(255)          COMMENT '创建时间（毫秒时间戳字符串）',
  `updated_at` VARCHAR(255)          COMMENT '最后更新时间（毫秒时间戳字符串）'
);

-- ---------- 权限组定义表 ----------
CREATE TABLE IF NOT EXISTS `soys_perm_group` (
  `id`          VARCHAR(64)  PRIMARY KEY COMMENT '权限组 ID（小写规范化）',
  `display`     VARCHAR(255)          COMMENT '显示名',
  `prefix`      VARCHAR(255)          COMMENT '前缀（如聊天前缀）',
  `weight`      INT                   COMMENT '权重（数字越大优先级越高）',
  `description` VARCHAR(255)          COMMENT '描述',
  `created_at`  VARCHAR(255)          COMMENT '创建时间（毫秒时间戳字符串）',
  `updated_at`  VARCHAR(255)          COMMENT '最后更新时间（毫秒时间戳字符串）'
);

-- ---------- 权限节点表 ----------
-- owner_type: GROUP / USER；owner_id: 组 ID 或玩家 UUID
-- permission: 权限节点（":" 与 "." 等价，如 "test:ping" ≡ "test.ping"）
-- negative:   1 = 负权限（拒绝）
CREATE TABLE IF NOT EXISTS `soys_perm_permission` (
  `id`          VARCHAR(64)  PRIMARY KEY COMMENT '记录 ID',
  `owner_type`  VARCHAR(255)          COMMENT '归属类型: GROUP / USER',
  `owner_id`    VARCHAR(255)          COMMENT '归属 ID（组 ID 或玩家 UUID）',
  `permission`  VARCHAR(255)          COMMENT '权限节点',
  `negative`    TINYINT               COMMENT '负权限标记: 1 = 拒绝',
  `created_at`  VARCHAR(255)          COMMENT '创建时间（毫秒时间戳字符串）'
);

-- ---------- 用户 - 权限组关联表 ----------
-- 注意: group 为 SQL 保留字（GROUP BY），必须用反引号包裹
CREATE TABLE IF NOT EXISTS `soys_perm_user_group` (
  `id`    VARCHAR(64)  PRIMARY KEY COMMENT '记录 ID',
  `uuid`  VARCHAR(255)          COMMENT '玩家 UUID',
  `group` VARCHAR(255)          COMMENT '权限组 ID（保留字，已反引号包裹）'
);

-- ---------- 记住我凭证表（自动登录 / 设备免登录） ----------
CREATE TABLE IF NOT EXISTS `soys_remember` (
  `jti`        VARCHAR(64)  PRIMARY KEY COMMENT '凭证 ID（JWT jti）',
  `player`     VARCHAR(255)          COMMENT '玩家名',
  `issued_at`  VARCHAR(255)          COMMENT '签发时间（毫秒时间戳字符串）',
  `expires_at` VARCHAR(255)          COMMENT '过期时间（毫秒时间戳字符串；过期自动清理/拉黑）'
);
