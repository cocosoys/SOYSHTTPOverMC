-- ============================================================================
-- V4 迁移：新增 soys_sso_ticket（SSO 一次性登录票据表）
-- 适用: MySQL 5.x / 8.x / MariaDB（SQLite 亦兼容；删除各列 COMMENT 即可手工执行，
--      插件运行时 SqlBackendExecutor 也会自动建表，本脚本供运维参考/手工初始化）
-- 背景: 游戏内免登链接票据从内存 Map 实体化（对齐 YAML 双后端 ORM），
--       群组服接同一 MySQL 时票据全局可消费，支撑跨域名 SSO 回跳。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `soys_sso_ticket` (
  `id`             BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  `ticket`         VARCHAR(64)  NOT NULL COMMENT '票据串（tk_ 前缀随机，业务查询键）',
  `subject`        VARCHAR(255)          COMMENT '绑定玩家名',
  `redirect_url`    VARCHAR(1024)         COMMENT '目标回跳地址（跨域 SSO 用）',
  `issued_server`  VARCHAR(255)          COMMENT '签发服标识（审计）',
  `client_ip`      VARCHAR(64)           COMMENT '客户端 IP（可空 = 不绑定）',
  `consumed_at`    BIGINT                COMMENT '消费毫秒时间戳（空 = 未消费）',
  `expires_at`     BIGINT                COMMENT '过期毫秒时间戳',
  `create_time`    VARCHAR(255)          COMMENT '创建时间（yyyy-MM-dd HH:mm:ss）',
  `update_time`    VARCHAR(255)          COMMENT '最后更新时间（yyyy-MM-dd HH:mm:ss）',
  UNIQUE KEY `uk_sso_ticket` (`ticket`)
);
