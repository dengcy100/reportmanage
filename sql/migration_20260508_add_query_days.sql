-- Force UTF-8 session for script execution
SET NAMES utf8mb4;

USE `report`;

ALTER TABLE `report_field`
  ADD COLUMN `default_query_days` INT NOT NULL DEFAULT 0 COMMENT 'default query days for date/datetime range';

ALTER TABLE `report_field`
  ADD COLUMN `max_query_days` INT NOT NULL DEFAULT 0 COMMENT 'max query days for date/datetime range';
