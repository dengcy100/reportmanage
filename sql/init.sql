-- Force UTF-8 session for script execution
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `plm-report`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `plm-report`;

CREATE TABLE IF NOT EXISTS `report_config` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake ID',
  `name` VARCHAR(128) NOT NULL COMMENT 'Report name',
  `procedure_name` VARCHAR(128) NOT NULL COMMENT 'Stored procedure name',
  `page_size` INT NOT NULL COMMENT 'Query page size',
  `exporters` VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Comma separated exporters',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0-active 1-deleted',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report_config_deleted_updated` (`deleted`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Report config';

CREATE TABLE IF NOT EXISTS `report_field` (
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
  KEY `idx_report_field_report_deleted_sort` (`report_id`, `deleted`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Report field config';

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
    WHERE (order_no IS NULL OR order_no = '' OR d.order_no LIKE CONCAT('%', CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, '%'))
      AND (customer_name IS NULL OR customer_name = '' OR d.customer_name LIKE CONCAT('%', CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, '%'))
      AND (create_date_start IS NULL OR create_date_start = '' OR d.create_date >= STR_TO_DATE(create_date_start, '%Y-%m-%d'))
      AND (create_date_end IS NULL OR create_date_end = '' OR d.create_date <= STR_TO_DATE(create_date_end, '%Y-%m-%d'));

    IF IFNULL(in_page_size, 0) = 0 THEN
        SELECT d.order_no, d.customer_name, d.amount, DATE_FORMAT(d.create_date, '%Y-%m-%d') AS create_date
        FROM demo_order_data d
        WHERE (order_no IS NULL OR order_no = '' OR d.order_no LIKE CONCAT('%', CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, '%'))
          AND (customer_name IS NULL OR customer_name = '' OR d.customer_name LIKE CONCAT('%', CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, '%'))
          AND (create_date_start IS NULL OR create_date_start = '' OR d.create_date >= STR_TO_DATE(create_date_start, '%Y-%m-%d'))
          AND (create_date_end IS NULL OR create_date_end = '' OR d.create_date <= STR_TO_DATE(create_date_end, '%Y-%m-%d'))
        ORDER BY d.create_date DESC, d.id DESC;
    ELSE
        SELECT d.order_no, d.customer_name, d.amount, DATE_FORMAT(d.create_date, '%Y-%m-%d') AS create_date
        FROM demo_order_data d
        WHERE (order_no IS NULL OR order_no = '' OR d.order_no LIKE CONCAT('%', CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, '%'))
          AND (customer_name IS NULL OR customer_name = '' OR d.customer_name LIKE CONCAT('%', CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, '%'))
          AND (create_date_start IS NULL OR create_date_start = '' OR d.create_date >= STR_TO_DATE(create_date_start, '%Y-%m-%d'))
          AND (create_date_end IS NULL OR create_date_end = '' OR d.create_date <= STR_TO_DATE(create_date_end, '%Y-%m-%d'))
        ORDER BY d.create_date DESC, d.id DESC
        LIMIT v_offset, in_page_size;
    END IF;
END$$
DELIMITER ;
