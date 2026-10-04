-- ============================================================================
-- SOYSHTTPOverMC · 初始化建表脚本
-- 适用: MySQL 5.x / 8.x / MariaDB（SQLite 亦兼容，见下方说明）
-- ----------------------------------------------------------------------------
-- 【重要说明】
-- 1) 【初始化 = 最新版本完整结构】本脚本始终维护为当前 schemaVersion 的最终形态
--    （含各版本迁移新增的列 / 索引）；全新安装直接使用本脚本，不再执行
--    sql/migrations/ 下的迁移脚本（迁移仅用于老用户版本升级，见 7）。
--    插件运行时若表不存在也会自动 CREATE TABLE IF NOT EXISTS
--    （SqlBackendExecutor.ensureTable + 容忍式补列 ALTER），二者不冲突，可重复执行。
-- 2) 列名规则: 实体字段驼峰转小写下划线（createTime → create_time），
--    与 YAML 端 data/<表名>.yml 的键名完全一致（双端同构）。
-- 3) 类型映射: String → VARCHAR(255)；主键 String → VARCHAR(64)
--    （utf8mb4 下 VARCHAR(255) 主键索引超长 1020B > 1000B）；
--    int → INT；boolean → TINYINT；Date/long → BIGINT；嵌套 → TEXT(JSON)。
-- 5) SQLite 兼容: SQLite 不支持 COMMENT 子句与 TINYINT（但类型宽松，TINYINT 可接受），
--    如需在 SQLite 手工执行，请删除各列的 COMMENT 子句；插件运行时会自动建表，
--    不依赖本脚本。
-- 7) 版本迁移（sql/migrations/V<n>/<表名>.sql，仅 MySQL）：仅当存在旧数据
--    且 meta.schema_version < 声明版本时增量执行 V(cur+1)..V(schemaVersion)；
--    全新安装不执行（初始化已含最新结构）。
-- 6) MySQL 建议库字符集 utf8mb4:
--    CREATE DATABASE IF NOT EXISTS minecraft DEFAULT CHARACTER SET utf8mb4;
--    执行时请使用: mysql --default-character-set=utf8mb4 -u<用户> -p < 库名 < init.sql
-- ============================================================================

-- ---------- 用户级权限表（主键 = 玩家 UUID） ----------
CREATE TABLE IF NOT EXISTS `soys_perm_user` (
  `uuid`       VARCHAR(64)  PRIMARY KEY COMMENT '玩家 UUID（主键，区分大小写）',
  `player`     VARCHAR(255)          COMMENT '玩家名（冗余；改名后以 uuid 为准）',
  `expiry`     VARCHAR(255)          COMMENT '用户级权限过期时间（yyyy-MM-dd HH:mm:ss；空 = 不过期）',
  `vip_level`  VARCHAR(64)  NOT NULL DEFAULT '0' COMMENT '会员等级（V2 迁移字段，最新结构内置）',
  `create_time` VARCHAR(255)          COMMENT '创建时间（yyyy-MM-dd HH:mm:ss）',
  `update_time` VARCHAR(255)          COMMENT '最后更新时间（yyyy-MM-dd HH:mm:ss）',
  KEY `idx_perm_user_player` (`player`)
);

-- ---------- 权限组定义表 ----------
CREATE TABLE IF NOT EXISTS `soys_perm_group` (
  `id`          VARCHAR(64)  PRIMARY KEY COMMENT '权限组 ID（小写规范化）',
  `display`     VARCHAR(255)          COMMENT '显示名',
  `prefix`      VARCHAR(255)          COMMENT '前缀（如聊天前缀）',
  `weight`      INT                   COMMENT '权重（数字越大优先级越高）',
  `description` VARCHAR(255)          COMMENT '描述',
  `create_time`  VARCHAR(255)          COMMENT '创建时间（yyyy-MM-dd HH:mm:ss）',
  `update_time`  VARCHAR(255)          COMMENT '最后更新时间（yyyy-MM-dd HH:mm:ss）'
);

-- ---------- 权限节点表 ----------
-- owner_type: GROUP / USER；owner_id: 组 ID 或玩家 UUID
-- permission: 权限节点（":" 与 "." 等价，如 "test:ping" ≡ "test.ping"）
-- negative:   1 = 负权限（拒绝）
CREATE TABLE IF NOT EXISTS `soys_perm_permission` (
  `id`          VARCHAR(64)  PRIMARY KEY COMMENT '记录 ID',
  `owner_type`  VARCHAR(255)          COMMENT '归属类型: GROUP / USER / APIKEY（X-API-Key）',
  `owner_id`    VARCHAR(255)          COMMENT '归属 ID（组 ID / 玩家 UUID / X-API-Key 主键 id）',
  `permission`  VARCHAR(255)          COMMENT '权限节点',
  `negative`    TINYINT               COMMENT '负权限标记: 1 = 拒绝',
  `create_time`  VARCHAR(255)          COMMENT '创建时间（yyyy-MM-dd HH:mm:ss）'
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
  `issued_at`  VARCHAR(255)          COMMENT '签发时间（yyyy-MM-dd HH:mm:ss）',
  `expires_at` VARCHAR(255)          COMMENT '过期时间（yyyy-MM-dd HH:mm:ss；过期自动清理/拉黑）'
);

-- ---------- 自动运维元数据表（soys_schema_meta） ----------
-- 记录每个表的归属插件 / schema 版本 / 已执行脚本，供卸载 / 重装 / 更新自动识别。
-- 逻辑双主键（plugin + table_name）以组合键 id = "<plugin>:<table_name>" 实现
-- （dlz ORM 单主键约束）；table_name = '*' 行为插件级迁移记录。
-- schema_version: 已应用最高迁移版本（如 0/1/2…）；executed_scripts: 已执行脚本 JSON 数组。
CREATE TABLE IF NOT EXISTS `soys_schema_meta` (
  `id`               VARCHAR(64)  PRIMARY KEY COMMENT '组合主键（<plugin>:<table_name>）',
  `plugin`           VARCHAR(255)          COMMENT '归属插件（主插件名 / Expansion identifier）',
  `table_name`       VARCHAR(255)          COMMENT '表名（* = 插件级迁移记录；否则为 ORM 表名）',
  `schema_version`   INT                   COMMENT '当前 schema 版本（已应用最高迁移版本）',
  `state`            VARCHAR(255)          COMMENT '状态（INSTALLED / UNINSTALLED，预留）',
  `executed_scripts` TEXT                  COMMENT '已执行脚本 JSON 数组（插件级行使用）',
  `create_time`       VARCHAR(255)          COMMENT '创建时间（yyyy-MM-dd HH:mm:ss）',
  `update_time`       VARCHAR(255)          COMMENT '最后更新时间（yyyy-MM-dd HH:mm:ss）'
);

-- ---------- X-API-Key 本地表（认证与权限门共用；密钥不存明文） ----------
-- api_key: 密钥 SHA-256 全量哈希（唯一；明文仅生成时展示一次）
-- fingerprint: SHA-256 前 8 位短指纹（唯一；日志/展示脱敏，不参与校验）
-- uuid: 绑定玩家 UUID（可空 = 未绑定；未来接入后 key 等效该玩家凭证，一期仅存储）
-- expiry: 过期时刻（yyyy-MM-dd HH:mm:ss；空 = 永久有效）
-- last_used_at / used_count: 使用统计（校验命中时更新）
-- 权限节点复用 soys_perm_permission（owner_type=APIKEY，owner_id=本表 id，支持否定/通配/过期）
CREATE TABLE IF NOT EXISTS `soys_api_key` (
  `id`           VARCHAR(64)  PRIMARY KEY COMMENT '主键（平台生成 UUID）',
  `api_key`      VARCHAR(255) NOT NULL COMMENT '密钥 SHA-256 全量哈希（唯一；不存明文）',
  `fingerprint`  VARCHAR(255) NOT NULL COMMENT '短指纹（SHA-256 前 8 位，唯一）',
  `uuid`         VARCHAR(64)           COMMENT '绑定玩家 UUID（可空 = 未绑定）',
  `player`       VARCHAR(255)          COMMENT '绑定玩家名（冗余；改名后以 uuid 为准）',
  `enabled`      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用（0=停用）',
  `expiry`       VARCHAR(255)          COMMENT '过期时刻（yyyy-MM-dd HH:mm:ss；空 = 永久）',
  `last_used_at` VARCHAR(255)          COMMENT '最后使用时刻（yyyy-MM-dd HH:mm:ss）',
  `used_count`   BIGINT       NOT NULL DEFAULT 0 COMMENT '累计使用次数',
  `remark`       VARCHAR(255)          COMMENT '备注',
  `create_time`  VARCHAR(255)          COMMENT '创建时间（yyyy-MM-dd HH:mm:ss）',
  `update_time`  VARCHAR(255)          COMMENT '最后更新时间（yyyy-MM-dd HH:mm:ss）',
  UNIQUE KEY `uk_api_key_hash` (`api_key`),
  UNIQUE KEY `uk_api_key_fp` (`fingerprint`)
);

-- ---------- 设备绑定表（记住我 Cookie + 设备指纹双因子） ----------
-- 自增主键 id（SQL 端 AUTO_INCREMENT / SQLite AUTOINCREMENT；YAML 端由 ORM 分配 max+1）
-- fingerprint_hash: SHA-256(玩家名|原始指纹|服务端盐)，仅存哈希（指纹非秘密，仅一致性弱校验）
-- revoked: 1 = 设备已弃用（不参与判定）
-- 联合唯一键 (player, fingerprint_hash)：一个玩家可绑定多台设备、一台设备一个绑定
CREATE TABLE IF NOT EXISTS `soys_device_binding` (
  `id`               BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  `player`           VARCHAR(255)          COMMENT '玩家名（联合唯一索引之一）',
  `uuid`             VARCHAR(64)           COMMENT '玩家 UUID（可空，绑定/登录时回填）',
  `fingerprint_hash` VARCHAR(64)           COMMENT '设备指纹哈希（SHA-256+盐，仅存哈希）',
  `device_label`     VARCHAR(255)          COMMENT '设备描述（UA 摘要，仅展示）',
  `last_ip`          VARCHAR(64)           COMMENT '最后成功绑定/登录 IP（弱校验参考）',
  `last_bind_at`     VARCHAR(255)          COMMENT '最后绑定时刻（yyyy-MM-dd HH:mm:ss）',
  `revoked`          TINYINT               COMMENT '弃用标记: 1 = 已弃用',
  `create_time`      VARCHAR(255)          COMMENT '创建时间（yyyy-MM-dd HH:mm:ss）',
  `update_time`      VARCHAR(255)          COMMENT '最后更新时间（yyyy-MM-dd HH:mm:ss）',
  UNIQUE KEY `uk_device_player_fp` (`player`, `fingerprint_hash`)
);

-- ---------- 通用记录表（soys_records：令牌黑名单 / 签发审计 / 实例心跳 / 全局密钥） ----------
-- 四类 KV 记录统一存储（RecordSyncStorage），key 约定：
--   blacklist:<jti>      type=BLACKLIST  data=JSON{server_id, revoked_at}
--   audit:<jti>:<nonce>  type=AUDIT      data=JSON（append-only，nonce 保证唯一）
--   instance:<serverId>  type=INSTANCE   data=JSON（跨服心跳）
--   meta:jwt_secret      type=META       data=base64（全局 JWT 密钥）
-- 业务时间以 updated_at 为准；create_time/update_time 为审计字段（ORM 自动填充）。
CREATE TABLE IF NOT EXISTS `soys_records` (
  `key`         VARCHAR(64)  PRIMARY KEY COMMENT '记录键（blacklist:<jti> / audit:<jti>:<nonce> / instance:<serverId> / meta:jwt_secret）',
  `type`        VARCHAR(255)          COMMENT '记录类型（BLACKLIST / AUDIT / INSTANCE / META）',
  `data`        TEXT                  COMMENT '负载（JSON 字符串，结构见 RecordSyncStorage）',
  `updated_at`  VARCHAR(255)          COMMENT '业务时间（yyyy-MM-dd HH:mm:ss；黑名单注销/审计签发/心跳/密钥更新）',
  `create_time` VARCHAR(255)          COMMENT '创建时间（yyyy-MM-dd HH:mm:ss）',
  `update_time` VARCHAR(255)          COMMENT '最后更新时间（yyyy-MM-dd HH:mm:ss）'
);
