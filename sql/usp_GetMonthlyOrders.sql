-- Force UTF-8 session for script execution
SET NAMES utf8mb4;

USE `report`;

CREATE TABLE IF NOT EXISTS `demo_order_data` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(64) NOT NULL,
  `customer_name` VARCHAR(128) NOT NULL,
  `amount` DECIMAL(12,2) NOT NULL,
  `create_date` DATE NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_demo_order_data_create_date` (`create_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Demo report source data';

INSERT INTO `demo_order_data` (`order_no`,`customer_name`,`amount`,`create_date`)
SELECT s.order_no, s.customer_name, s.amount, s.create_date
FROM (
  SELECT 'SO-2025001' AS order_no, 'ACME' AS customer_name, 1680.00 AS amount, '2025-01-08' AS create_date UNION ALL
  SELECT 'SO-2025002', 'Globex', 2960.50, '2025-01-21' UNION ALL
  SELECT 'SO-2025003', 'Initech', 2300.00, '2025-02-06' UNION ALL
  SELECT 'SO-2025004', 'Umbrella', 4150.80, '2025-02-25' UNION ALL
  SELECT 'SO-2025005', 'Wonka', 990.00, '2025-03-03' UNION ALL
  SELECT 'SO-2025006', 'Soylent', 3720.00, '2025-03-19' UNION ALL
  SELECT 'SO-2025007', 'Stark', 5520.00, '2025-04-10' UNION ALL
  SELECT 'SO-2025008', 'Wayne', 4388.00, '2025-04-28' UNION ALL
  SELECT 'SO-2025009', 'Cyberdyne', 1870.00, '2025-05-09' UNION ALL
  SELECT 'SO-2025010', 'Tyrell', 2640.00, '2025-05-23' UNION ALL
  SELECT 'SO-2025011', 'Oscorp', 3490.00, '2025-06-11' UNION ALL
  SELECT 'SO-2025012', 'Hooli', 2780.00, '2025-06-26' UNION ALL
  SELECT 'SO-2026001', 'ACME', 2000.00, '2026-01-05' UNION ALL
  SELECT 'SO-2026002', 'Globex', 3200.50, '2026-01-20' UNION ALL
  SELECT 'SO-2026003', 'Initech', 1800.00, '2026-02-12' UNION ALL
  SELECT 'SO-2026004', 'Umbrella', 4500.30, '2026-03-01' UNION ALL
  SELECT 'SO-2026005', 'Wonka', 980.00, '2026-03-22' UNION ALL
  SELECT 'SO-2026006', 'Soylent', 5100.00, '2026-04-07' UNION ALL
  SELECT 'SO-2026007', 'Stark', 6250.00, '2026-04-18' UNION ALL
  SELECT 'SO-2026008', 'Wayne', 4720.00, '2026-05-02' UNION ALL
  SELECT 'SO-2026009', 'Cyberdyne', 2100.00, '2026-05-16' UNION ALL
  SELECT 'SO-2026010', 'Tyrell', 3010.00, '2026-06-09' UNION ALL
  SELECT 'SO-2026011', 'Oscorp', 2880.00, '2026-06-21' UNION ALL
  SELECT 'SO-2026012', 'Hooli', 3330.00, '2026-07-03'
) s
WHERE NOT EXISTS (SELECT 1 FROM `demo_order_data` LIMIT 1);

DROP PROCEDURE IF EXISTS `usp_GetMonthlyOrders`;
DELIMITER $$
CREATE PROCEDURE `usp_GetMonthlyOrders`(
    IN `p_order_no` VARCHAR(64),
    IN `p_customer_name` VARCHAR(128),
    IN `p_create_date_start` VARCHAR(32),
    IN `p_create_date_end` VARCHAR(32),
    IN `p_in_page_no` INT,
    IN `p_in_page_size` INT,
    OUT `out_total` BIGINT
)
BEGIN
    DECLARE `v_order_no` VARCHAR(64) DEFAULT NULL;
    DECLARE `v_customer_name` VARCHAR(128) DEFAULT NULL;
    DECLARE `v_start_date` DATE DEFAULT NULL;
    DECLARE `v_end_date` DATE DEFAULT NULL;
    DECLARE `v_page_no` INT DEFAULT 1;
    DECLARE `v_page_size` INT DEFAULT 20;
    DECLARE `v_offset` INT DEFAULT 0;

    SET `v_order_no` = NULLIF(TRIM(p_order_no), '');
    SET `v_customer_name` = NULLIF(TRIM(p_customer_name), '');
    SET `v_start_date` = CASE
      WHEN p_create_date_start IS NULL OR TRIM(p_create_date_start) = '' THEN NULL
      ELSE STR_TO_DATE(p_create_date_start, '%Y-%m-%d')
    END;
    SET `v_end_date` = CASE
      WHEN p_create_date_end IS NULL OR TRIM(p_create_date_end) = '' THEN NULL
      ELSE STR_TO_DATE(p_create_date_end, '%Y-%m-%d')
    END;
    SET `v_page_no` = IFNULL(p_in_page_no, 1);
    IF `v_page_no` < 1 THEN
      SET `v_page_no` = 1;
    END IF;
    SET `v_page_size` = IFNULL(p_in_page_size, 20);
    IF `v_page_size` < 0 THEN
      SET `v_page_size` = 20;
    END IF;
    SET `v_offset` = (`v_page_no` - 1) * `v_page_size`;

    SELECT COUNT(1) INTO `out_total`
    FROM `demo_order_data` d
    WHERE (`v_order_no` IS NULL OR
           (LOCATE(';', `v_order_no`) > 0 AND FIND_IN_SET(d.`order_no`, REPLACE(CONVERT(`v_order_no` USING utf8mb4) COLLATE utf8mb4_general_ci, ';', ',')) > 0) OR
           (LOCATE(';', `v_order_no`) = 0 AND d.`order_no` LIKE CONCAT('%', CONVERT(`v_order_no` USING utf8mb4) COLLATE utf8mb4_general_ci, '%')))
      AND (`v_customer_name` IS NULL OR
           (LOCATE(';', `v_customer_name`) > 0 AND FIND_IN_SET(d.`customer_name`, REPLACE(CONVERT(`v_customer_name` USING utf8mb4) COLLATE utf8mb4_general_ci, ';', ',')) > 0) OR
           (LOCATE(';', `v_customer_name`) = 0 AND d.`customer_name` LIKE CONCAT('%', CONVERT(`v_customer_name` USING utf8mb4) COLLATE utf8mb4_general_ci, '%')))
      AND (`v_start_date` IS NULL OR d.`create_date` >= `v_start_date`)
      AND (`v_end_date` IS NULL OR d.`create_date` <= `v_end_date`);

    IF IFNULL(`v_page_size`, 0) = 0 THEN
      SELECT
        d.`order_no`,
        d.`customer_name`,
        d.`amount`,
        DATE_FORMAT(d.`create_date`, '%Y-%m-%d') AS `create_date`
      FROM `demo_order_data` d
      WHERE (`v_order_no` IS NULL OR
             (LOCATE(';', `v_order_no`) > 0 AND FIND_IN_SET(d.`order_no`, REPLACE(CONVERT(`v_order_no` USING utf8mb4) COLLATE utf8mb4_general_ci, ';', ',')) > 0) OR
             (LOCATE(';', `v_order_no`) = 0 AND d.`order_no` LIKE CONCAT('%', CONVERT(`v_order_no` USING utf8mb4) COLLATE utf8mb4_general_ci, '%')))
        AND (`v_customer_name` IS NULL OR
             (LOCATE(';', `v_customer_name`) > 0 AND FIND_IN_SET(d.`customer_name`, REPLACE(CONVERT(`v_customer_name` USING utf8mb4) COLLATE utf8mb4_general_ci, ';', ',')) > 0) OR
             (LOCATE(';', `v_customer_name`) = 0 AND d.`customer_name` LIKE CONCAT('%', CONVERT(`v_customer_name` USING utf8mb4) COLLATE utf8mb4_general_ci, '%')))
        AND (`v_start_date` IS NULL OR d.`create_date` >= `v_start_date`)
        AND (`v_end_date` IS NULL OR d.`create_date` <= `v_end_date`)
      ORDER BY d.`create_date` DESC, d.`id` DESC;
    ELSE
      SELECT
        d.`order_no`,
        d.`customer_name`,
        d.`amount`,
        DATE_FORMAT(d.`create_date`, '%Y-%m-%d') AS `create_date`
      FROM `demo_order_data` d
      WHERE (`v_order_no` IS NULL OR
             (LOCATE(';', `v_order_no`) > 0 AND FIND_IN_SET(d.`order_no`, REPLACE(CONVERT(`v_order_no` USING utf8mb4) COLLATE utf8mb4_general_ci, ';', ',')) > 0) OR
             (LOCATE(';', `v_order_no`) = 0 AND d.`order_no` LIKE CONCAT('%', CONVERT(`v_order_no` USING utf8mb4) COLLATE utf8mb4_general_ci, '%')))
        AND (`v_customer_name` IS NULL OR
             (LOCATE(';', `v_customer_name`) > 0 AND FIND_IN_SET(d.`customer_name`, REPLACE(CONVERT(`v_customer_name` USING utf8mb4) COLLATE utf8mb4_general_ci, ';', ',')) > 0) OR
             (LOCATE(';', `v_customer_name`) = 0 AND d.`customer_name` LIKE CONCAT('%', CONVERT(`v_customer_name` USING utf8mb4) COLLATE utf8mb4_general_ci, '%')))
        AND (`v_start_date` IS NULL OR d.`create_date` >= `v_start_date`)
        AND (`v_end_date` IS NULL OR d.`create_date` <= `v_end_date`)
      ORDER BY d.`create_date` DESC, d.`id` DESC
      LIMIT `v_offset`, `v_page_size`;
    END IF;
END$$
DELIMITER ;

