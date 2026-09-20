-- ==============================================================
--  V3 迁移：soys_perm_permission 主键自增改造（合成键 → Long AUTO_INCREMENT）
-- ==============================================================
--  原主键 id 为 VARCHAR(64) 合成键（ownerType|ownerId|permission），
--  本次改为 BIGINT 自增主键；业务键唯一性改由逻辑层查重保证
--  （LocalPermissionStore / ApiKeyStore 按 owner_type+owner_id+permission 条件查重）。
--  运行时 SqlBackendExecutor.ensureAutoIdTable 也会自动自愈（幂等，
--  检测主键已是整数后跳过）；本脚本供 MySQL 运维显式迁移，二者效果一致。
--  重复执行会因表已重建报错——属预期（meta 记录保证每脚本只执行一次）。
-- ==============================================================
CREATE TABLE `soys_perm_permission__new` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `owner_type` VARCHAR(255) NOT NULL DEFAULT '',
  `owner_id` VARCHAR(255) NOT NULL DEFAULT '',
  `permission` VARCHAR(255) NOT NULL DEFAULT '',
  `negative` TINYINT NOT NULL DEFAULT 0,
  `create_time` VARCHAR(255) NULL,
  `update_time` VARCHAR(255) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `soys_perm_permission__new`
    (`owner_type`, `owner_id`, `permission`, `negative`, `create_time`, `update_time`)
SELECT `owner_type`, `owner_id`, `permission`, `negative`, `create_time`, `update_time`
FROM `soys_perm_permission`;

DROP TABLE `soys_perm_permission`;
RENAME TABLE `soys_perm_permission__new` TO `soys_perm_permission`;
