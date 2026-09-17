-- ==============================================================
--  V2 迁移：soys_perm_user 增加 vip_level 字段（默认 '0'）
-- ==============================================================
--  SQL 通道（YAML 通道见 data/migrations/V2/soys_perm_user.yml）。
--  每次迁移只执行一次（meta 记录），升级到 V2 自动执行本脚本。
--  MySQL 不支持 ADD COLUMN IF NOT EXISTS，手动重复执行会报"列已存在"——
--  属预期（正常流程由 meta 保证只执行一次）。
-- ==============================================================
ALTER TABLE `soys_perm_user`
    ADD COLUMN `vip_level` VARCHAR(64) NOT NULL DEFAULT '0' COMMENT '会员等级（演示迁移字段）';