-- Force UTF-8 session for script execution
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `plm-report`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `plm-report`;

CREATE TABLE IF NOT EXISTS `custom_report_config` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake ID',
  `name` VARCHAR(128) NOT NULL COMMENT 'Report name',
  `procedure_name` VARCHAR(128) NOT NULL COMMENT 'Stored procedure name',
  `page_size` INT NOT NULL COMMENT 'Query page size',
  `exporters` VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Comma separated exporters',
  `export_wait_message` VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Export waiting message',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0-active 1-deleted',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_custom_report_config_deleted_updated` (`deleted`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Report config';

CREATE TABLE IF NOT EXISTS `custom_report_field` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake ID',
  `report_id` BIGINT NOT NULL COMMENT 'Report config ID',
  `label` VARCHAR(128) NOT NULL COMMENT 'Column label',
  `field_name` VARCHAR(128) NOT NULL COMMENT 'Procedure parameter/result field',
  `field_type` VARCHAR(16) NOT NULL COMMENT 'string/number/date/datetime/boolean',
  `match_type` VARCHAR(16) NOT NULL COMMENT 'like/eq/range/in',
  `searchable` TINYINT NOT NULL DEFAULT 0 COMMENT '0-no 1-yes',
  `search_sort` INT NOT NULL DEFAULT 0 COMMENT 'search order',
  `default_query_days` INT NOT NULL DEFAULT 0 COMMENT 'default query days for date/datetime range',
  `max_query_days` INT NOT NULL DEFAULT 0 COMMENT 'max query days for date/datetime range',
  `sort_order` INT NOT NULL COMMENT 'column display order',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0-active 1-deleted',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_custom_report_field_report_deleted_sort` (`report_id`, `deleted`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Report field config';

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

CREATE TABLE IF NOT EXISTS `custom_log` (
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
  KEY `idx_custom_log_report_created` (`report_id`, `created_at`),
  KEY `idx_custom_log_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Custom report query logs';

CREATE TABLE IF NOT EXISTS `demo_order_data` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(64) NOT NULL,
  `customer_name` VARCHAR(128) NOT NULL,
  `amount` DECIMAL(12,2) NOT NULL,
  `create_date` DATE NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_demo_order_data_create_date` (`create_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Demo report source data';

INSERT INTO `demo_order_data` (`order_no`,`customer_name`,`amount`,`create_date`) VALUES
('SO-2026001','ACME',2000.00,'2026-01-05'),
('SO-2026002','Globex',3200.50,'2026-01-20'),
('SO-2026003','Initech',1800.00,'2026-02-12'),
('SO-2026004','Umbrella',4500.30,'2026-03-01'),
('SO-2026005','Wonka',980.00,'2026-03-22');

DROP PROCEDURE IF EXISTS `usp_GetMonthlyOrders`;
DELIMITER $$
CREATE PROCEDURE `usp_GetMonthlyOrders`(
    IN `order_no` VARCHAR(64),
    IN `customer_name` VARCHAR(128),
    IN `create_date_start` VARCHAR(32),
    IN `create_date_end` VARCHAR(32),
    IN `in_page_no` INT,
    IN `in_page_size` INT,
    OUT `out_total` BIGINT
)
BEGIN
    DECLARE `v_offset` INT DEFAULT 0;
    SET `v_offset` = (IFNULL(in_page_no, 1) - 1) * IFNULL(in_page_size, 20);

    SELECT COUNT(1) INTO out_total
    FROM demo_order_data d
    WHERE (order_no IS NULL OR order_no = '' OR
           (LOCATE(';', order_no) > 0 AND FIND_IN_SET(d.order_no, REPLACE(CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, ';', ',')) > 0) OR
           (LOCATE(';', order_no) = 0 AND d.order_no LIKE CONCAT('%', CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, '%')))
      AND (customer_name IS NULL OR customer_name = '' OR
           (LOCATE(';', customer_name) > 0 AND FIND_IN_SET(d.customer_name, REPLACE(CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, ';', ',')) > 0) OR
           (LOCATE(';', customer_name) = 0 AND d.customer_name LIKE CONCAT('%', CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, '%')))
      AND (create_date_start IS NULL OR create_date_start = '' OR d.create_date >= STR_TO_DATE(create_date_start, '%Y-%m-%d'))
      AND (create_date_end IS NULL OR create_date_end = '' OR d.create_date <= STR_TO_DATE(create_date_end, '%Y-%m-%d'));

    IF IFNULL(in_page_size, 0) = 0 THEN
        SELECT d.order_no, d.customer_name, d.amount, DATE_FORMAT(d.create_date, '%Y-%m-%d') AS create_date
        FROM demo_order_data d
        WHERE (order_no IS NULL OR order_no = '' OR
               (LOCATE(';', order_no) > 0 AND FIND_IN_SET(d.order_no, REPLACE(CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, ';', ',')) > 0) OR
               (LOCATE(';', order_no) = 0 AND d.order_no LIKE CONCAT('%', CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, '%')))
          AND (customer_name IS NULL OR customer_name = '' OR
               (LOCATE(';', customer_name) > 0 AND FIND_IN_SET(d.customer_name, REPLACE(CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, ';', ',')) > 0) OR
               (LOCATE(';', customer_name) = 0 AND d.customer_name LIKE CONCAT('%', CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, '%')))
          AND (create_date_start IS NULL OR create_date_start = '' OR d.create_date >= STR_TO_DATE(create_date_start, '%Y-%m-%d'))
          AND (create_date_end IS NULL OR create_date_end = '' OR d.create_date <= STR_TO_DATE(create_date_end, '%Y-%m-%d'))
        ORDER BY d.create_date DESC, d.id DESC;
    ELSE
        SELECT d.order_no, d.customer_name, d.amount, DATE_FORMAT(d.create_date, '%Y-%m-%d') AS create_date
        FROM demo_order_data d
        WHERE (order_no IS NULL OR order_no = '' OR
               (LOCATE(';', order_no) > 0 AND FIND_IN_SET(d.order_no, REPLACE(CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, ';', ',')) > 0) OR
               (LOCATE(';', order_no) = 0 AND d.order_no LIKE CONCAT('%', CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, '%')))
          AND (customer_name IS NULL OR customer_name = '' OR
               (LOCATE(';', customer_name) > 0 AND FIND_IN_SET(d.customer_name, REPLACE(CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, ';', ',')) > 0) OR
               (LOCATE(';', customer_name) = 0 AND d.customer_name LIKE CONCAT('%', CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, '%')))
          AND (create_date_start IS NULL OR create_date_start = '' OR d.create_date >= STR_TO_DATE(create_date_start, '%Y-%m-%d'))
          AND (create_date_end IS NULL OR create_date_end = '' OR d.create_date <= STR_TO_DATE(create_date_end, '%Y-%m-%d'))
        ORDER BY d.create_date DESC, d.id DESC
        LIMIT v_offset, in_page_size;
    END IF;
END$$
DELIMITER ;
