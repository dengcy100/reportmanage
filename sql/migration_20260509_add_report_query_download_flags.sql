-- Force UTF-8 session for script execution
SET NAMES utf8mb4;

USE `report`;

ALTER TABLE `report_config`
  ADD COLUMN `query_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '1-query enabled 0-disabled',
  ADD COLUMN `download_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '1-download enabled 0-disabled';
