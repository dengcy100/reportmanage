-- Force UTF-8 session for script execution
SET NAMES utf8mb4;

USE `plm-report`;

ALTER TABLE `custom_report_config`
  ADD COLUMN `export_wait_message` VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Export waiting message';

CREATE TABLE IF NOT EXISTS `report_search_field` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake ID',
  `report_id` BIGINT NOT NULL COMMENT 'Report config ID',
  `label` VARCHAR(128) NOT NULL COMMENT 'Search label',
  `field_name` VARCHAR(128) NOT NULL COMMENT 'Procedure parameter name',
  `field_type` VARCHAR(16) NOT NULL COMMENT 'string/number/date/datetime/boolean',
  `match_type` VARCHAR(16) NOT NULL COMMENT 'like/eq/range/in',
  `control_type` VARCHAR(16) NOT NULL COMMENT 'input/single_select/multi_select',
  `multiline_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '0-no 1-yes',
  `option_values_json` TEXT COMMENT 'JSON options array',
  `search_sort` INT NOT NULL DEFAULT 0 COMMENT 'search order',
  `default_query_days` INT NOT NULL DEFAULT 0 COMMENT 'default query days for date/datetime range',
  `max_query_days` INT NOT NULL DEFAULT 0 COMMENT 'max query days for date/datetime range',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0-active 1-deleted',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report_search_field_report_deleted_sort` (`report_id`, `deleted`, `search_sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Report search field config';

CREATE TABLE IF NOT EXISTS `report_export_task` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake ID',
  `report_id` BIGINT NOT NULL COMMENT 'Report config ID',
  `request_digest` VARCHAR(64) NOT NULL COMMENT 'Digest for same request dedup',
  `status` VARCHAR(16) NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/EXPIRED',
  `request_json` LONGTEXT COMMENT 'Request filters json',
  `file_name` VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Export file name',
  `file_path` VARCHAR(1024) NOT NULL DEFAULT '' COMMENT 'Export file path',
  `error_message` VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Error message',
  `expires_at` DATETIME NULL COMMENT 'File expire time',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report_export_task_report_digest_status` (`report_id`, `request_digest`, `status`),
  KEY `idx_report_export_task_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Report export async task';

INSERT INTO `report_search_field` (
  `id`,`report_id`,`label`,`field_name`,`field_type`,`match_type`,`control_type`,
  `multiline_enabled`,`option_values_json`,`search_sort`,`default_query_days`,
  `max_query_days`,`deleted`,`created_at`,`updated_at`
)
SELECT
  `id`,
  `report_id`,
  `label`,
  `field_name`,
  `field_type`,
  `match_type`,
  CASE WHEN `match_type`='in' THEN 'multi_select' ELSE 'input' END AS `control_type`,
  0 AS `multiline_enabled`,
  '[]' AS `option_values_json`,
  CASE WHEN IFNULL(`search_sort`,0) > 0 THEN `search_sort` ELSE `sort_order` END AS `search_sort`,
  IFNULL(`default_query_days`,0) AS `default_query_days`,
  IFNULL(`max_query_days`,0) AS `max_query_days`,
  0 AS `deleted`,
  `created_at`,
  `updated_at`
FROM `custom_report_field` f
WHERE f.`deleted`=0
  AND IFNULL(f.`searchable`,0)=1
  AND NOT EXISTS (
    SELECT 1
    FROM `report_search_field` s
    WHERE s.`deleted`=0
      AND s.`report_id`=f.`report_id`
      AND s.`field_name`=f.`field_name`
  );
