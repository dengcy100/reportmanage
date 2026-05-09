-- Force UTF-8 session for script execution
SET NAMES utf8mb4;

USE `report`;

RENAME TABLE `custom_report_config` TO `report_config`;
RENAME TABLE `custom_report_field` TO `report_field`;
RENAME TABLE `custom_log` TO `report_log`;

CREATE TABLE IF NOT EXISTS `report_log` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake ID',
  `report_id` BIGINT NOT NULL COMMENT 'Report config ID',
  `user_id` BIGINT NULL COMMENT 'Operator user ID',
  `action_type` VARCHAR(32) NOT NULL COMMENT 'QUERY/EXPORT/...',
  `filters_json` LONGTEXT COMMENT 'Request filters json',
  `page_no` INT NOT NULL DEFAULT 1 COMMENT 'Query page no',
  `page_size` INT NOT NULL DEFAULT 20 COMMENT 'Query page size',
  `result_total` BIGINT NOT NULL DEFAULT 0 COMMENT 'Total result count',
  `status` VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
  `error_message` VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Error message',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report_log_report_created` (`report_id`, `created_at`),
  KEY `idx_report_log_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Custom report query logs';
