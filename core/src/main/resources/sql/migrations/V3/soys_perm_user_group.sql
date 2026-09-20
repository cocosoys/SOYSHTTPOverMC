-- ==============================================================
--  V3 迁移：soys_perm_user_group 主键自增改造（合成键 → Long AUTO_INCREMENT）
-- ==============================================================
--  原主键 id 为 VARCHAR(64) 合成键（uuid|group），
--  本次改为 BIGINT 自增主键；业务键唯一性改由逻辑层查重保证
--  （LocalPermissionStore.addUserGroup 按 uuid+group 条件查重）。
--  运行时 SqlBackendExecutor.ensureAutoIdTable 也会自动自愈（幂等，
--  检测主键已是整数后跳过）；本脚本供 MySQL 运维显式迁移，二者效果一致。
--  重复执行会因表已重建报错——属预期（meta 记录保证每脚本只执行一次）。
-- ==============================================================
CREATE TABLE `soys_perm_user_group__new` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `uuid` VARCHAR(255) NOT NULL DEFAULT '',
  `group` VARCHAR(255) NOT NULL DEFAULT '',
  `create_time` VARCHAR(255) NULL,
  `update_time` VARCHAR(255) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `soys_perm_user_group__new`
    (`uuid`, `group`, `create_time`, `update_time`)
SELECT `uuid`, `group`, `create_time`, `update_time`
FROM `soys_perm_user_group`;

DROP TABLE `soys_perm_user_group`;
RENAME TABLE `soys_perm_user_group__new` TO `soys_perm_user_group`;
