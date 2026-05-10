-- Force UTF-8 session for script execution
SET NAMES utf8mb4;

USE `report`;

ALTER TABLE `report_config`
  ADD COLUMN `query_type` VARCHAR(16) NOT NULL DEFAULT 'PROCEDURE' COMMENT 'PROCEDURE/SQL',
  ADD COLUMN `query_sql` LONGTEXT NULL COMMENT 'SQL query template',
  ADD COLUMN `count_sql` LONGTEXT NULL COMMENT 'SQL count template';

ALTER TABLE `report_config`
  MODIFY COLUMN `procedure_name` VARCHAR(128) NULL COMMENT 'Stored procedure name';

UPDATE `report_config`
SET `query_type` = 'PROCEDURE'
WHERE `query_type` IS NULL OR TRIM(`query_type`) = '';
