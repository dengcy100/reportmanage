-- Force UTF-8 session for script execution
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `report`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `report`;

CREATE TABLE IF NOT EXISTS `report_data_source` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake ID',
  `name` VARCHAR(128) NOT NULL COMMENT 'Data source name',
  `type` VARCHAR(32) NOT NULL COMMENT 'MYSQL/ORACLE/PGSQL/SQLSERVER',
  `host` VARCHAR(128) NOT NULL COMMENT 'Database host',
  `port` INT NOT NULL COMMENT 'Database port',
  `database_name` VARCHAR(128) NOT NULL COMMENT 'Database name',
  `username` VARCHAR(128) NOT NULL COMMENT 'Database username',
  `password_encrypted` VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Encrypted password',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0-active 1-deleted',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report_data_source_deleted_updated` (`deleted`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Report data source';

CREATE TABLE IF NOT EXISTS `report_config` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake ID',
  `data_source_id` BIGINT NULL COMMENT 'Bound data source ID',
  `name` VARCHAR(128) NOT NULL COMMENT 'Report name',
  `router_path` VARCHAR(128) NULL COMMENT 'Third-party router path',
  `query_type` VARCHAR(16) NOT NULL DEFAULT 'PROCEDURE' COMMENT 'PROCEDURE/SQL',
  `procedure_name` VARCHAR(128) NULL COMMENT 'Stored procedure name',
  `query_sql` LONGTEXT NULL COMMENT 'SQL query template',
  `count_sql` LONGTEXT NULL COMMENT 'SQL count template',
  `page_size` INT NOT NULL COMMENT 'Query page size',
  `exporters` VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Comma separated exporters',
  `export_wait_message` VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Export waiting message',
  `query_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '1-query enabled 0-disabled',
  `download_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '1-download enabled 0-disabled',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0-active 1-deleted',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report_config_deleted_updated` (`deleted`, `updated_at`),
  KEY `idx_report_config_data_source_id` (`data_source_id`),
  UNIQUE KEY `uk_report_config_router_path` (`router_path`)
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
SELECT t.order_no, t.customer_name, t.amount, t.create_date
FROM (
  SELECT 'SO-2026001' AS order_no, 'ACME' AS customer_name, 2000.00 AS amount, '2026-01-05' AS create_date
  UNION ALL SELECT 'SO-2026002', 'Globex', 3200.50, '2026-01-20'
  UNION ALL SELECT 'SO-2026003', 'Initech', 1800.00, '2026-02-12'
  UNION ALL SELECT 'SO-2026004', 'Umbrella', 4500.30, '2026-03-01'
  UNION ALL SELECT 'SO-2026005', 'Wonka', 980.00, '2026-03-22'
) t
WHERE NOT EXISTS (
  SELECT 1
  FROM `demo_order_data` d
  WHERE d.`order_no` = t.order_no
);

SET @default_data_source_name := '系统默认MySQL数据源';
SET @default_data_source_id := 900000000000001001;
SET @default_data_source_host := IFNULL(@init_app_db_host, '127.0.0.1');
SET @default_data_source_host := IF(TRIM(@default_data_source_host) = '', '127.0.0.1', @default_data_source_host);
SET @default_data_source_port := IFNULL(@init_app_db_port, 3306);
SET @default_data_source_db := IFNULL(@init_app_db_name, DATABASE());
SET @default_data_source_db := IF(TRIM(@default_data_source_db) = '', DATABASE(), @default_data_source_db);
SET @default_data_source_user := IFNULL(@init_app_db_user, '');
SET @default_data_source_password_encrypted := IFNULL(@init_app_db_password_encrypted, '');

INSERT INTO `report_data_source` (
  `id`,`name`,`type`,`host`,`port`,`database_name`,`username`,`password_encrypted`,`deleted`,`created_at`,`updated_at`
)
SELECT
  @default_data_source_id,
  @default_data_source_name,
  'MYSQL',
  @default_data_source_host,
  @default_data_source_port,
  @default_data_source_db,
  @default_data_source_user,
  @default_data_source_password_encrypted,
  0,
  NOW(),
  NOW()
WHERE NOT EXISTS (
  SELECT 1
  FROM `report_data_source`
  WHERE `name` = @default_data_source_name
);

UPDATE `report_data_source`
SET
  `name` = @default_data_source_name,
  `type` = 'MYSQL',
  `host` = @default_data_source_host,
  `port` = @default_data_source_port,
  `database_name` = @default_data_source_db,
  `username` = @default_data_source_user,
  `password_encrypted` = @default_data_source_password_encrypted,
  `deleted` = 0,
  `updated_at` = NOW()
WHERE `name` = @default_data_source_name;

SET @default_data_source_final_id := (
  SELECT `id`
  FROM `report_data_source`
  WHERE `name` = @default_data_source_name
  ORDER BY `updated_at` DESC, `id` DESC
  LIMIT 1
);

UPDATE `report_config`
SET `query_type` = 'PROCEDURE'
WHERE `query_type` IS NULL OR TRIM(`query_type`) = '';

UPDATE `report_config`
SET `query_enabled` = 1
WHERE `query_enabled` IS NULL;

UPDATE `report_config`
SET `download_enabled` = 1
WHERE `download_enabled` IS NULL;

UPDATE `report_config`
SET `data_source_id` = @default_data_source_final_id,
    `updated_at` = NOW()
WHERE `deleted` = 0
  AND `data_source_id` IS NULL;

SET @report_proc_name := '月度订单汇总报表';
SET @report_proc_id := 900000000000002001;
SET @report_proc_router := 'monthlyOrdersProcedure';
SET @report_sql_name := '月度订单汇总报表_自定义SQL数据';
SET @report_sql_id := 900000000000002002;
SET @report_sql_router := 'monthlyOrdersSql';

SET @sql_monthly_orders_query := CONCAT(
  'SELECT d.order_no AS order_no, d.customer_name AS customer_name, d.amount AS amount, ',
  'DATE_FORMAT(d.create_date, ''%Y-%m-%d'') AS create_date ',
  'FROM demo_order_data d ',
  'WHERE (#{order_no} IS NULL OR #{order_no} = '''' OR ',
  '(LOCATE(CHAR(59), #{order_no}) > 0 AND FIND_IN_SET(d.order_no, REPLACE(#{order_no}, CHAR(59), '','')) > 0) OR ',
  '(LOCATE(CHAR(59), #{order_no}) = 0 AND d.order_no LIKE CONCAT(''%'', #{order_no}, ''%''))) ',
  'AND (#{customer_name} IS NULL OR #{customer_name} = '''' OR ',
  '(LOCATE(CHAR(59), #{customer_name}) > 0 AND FIND_IN_SET(d.customer_name, REPLACE(#{customer_name}, CHAR(59), '','')) > 0) OR ',
  '(LOCATE(CHAR(59), #{customer_name}) = 0 AND d.customer_name LIKE CONCAT(''%'', #{customer_name}, ''%''))) ',
  'AND (#{create_date_start} IS NULL OR #{create_date_start} = '''' OR d.create_date >= STR_TO_DATE(#{create_date_start}, ''%Y-%m-%d'')) ',
  'AND (#{create_date_end} IS NULL OR #{create_date_end} = '''' OR d.create_date <= STR_TO_DATE(#{create_date_end}, ''%Y-%m-%d'')) ',
  'ORDER BY d.create_date DESC, d.id DESC'
);

SET @sql_monthly_orders_count := CONCAT(
  'SELECT COUNT(1) ',
  'FROM demo_order_data d ',
  'WHERE (#{order_no} IS NULL OR #{order_no} = '''' OR ',
  '(LOCATE(CHAR(59), #{order_no}) > 0 AND FIND_IN_SET(d.order_no, REPLACE(#{order_no}, CHAR(59), '','')) > 0) OR ',
  '(LOCATE(CHAR(59), #{order_no}) = 0 AND d.order_no LIKE CONCAT(''%'', #{order_no}, ''%''))) ',
  'AND (#{customer_name} IS NULL OR #{customer_name} = '''' OR ',
  '(LOCATE(CHAR(59), #{customer_name}) > 0 AND FIND_IN_SET(d.customer_name, REPLACE(#{customer_name}, CHAR(59), '','')) > 0) OR ',
  '(LOCATE(CHAR(59), #{customer_name}) = 0 AND d.customer_name LIKE CONCAT(''%'', #{customer_name}, ''%''))) ',
  'AND (#{create_date_start} IS NULL OR #{create_date_start} = '''' OR d.create_date >= STR_TO_DATE(#{create_date_start}, ''%Y-%m-%d'')) ',
  'AND (#{create_date_end} IS NULL OR #{create_date_end} = '''' OR d.create_date <= STR_TO_DATE(#{create_date_end}, ''%Y-%m-%d''))'
);

INSERT INTO `report_config` (
  `id`,`data_source_id`,`name`,`router_path`,`query_type`,`procedure_name`,`query_sql`,`count_sql`,
  `page_size`,`exporters`,`export_wait_message`,`query_enabled`,`download_enabled`,`deleted`,`created_at`,`updated_at`
)
SELECT
  @report_proc_id,
  @default_data_source_final_id,
  @report_proc_name,
  @report_proc_router,
  'PROCEDURE',
  'usp_GetMonthlyOrders',
  NULL,
  NULL,
  20,
  '张三,李四,王五',
  '导出数据量较大，请耐心等待，系统正在准备下载文件。',
  1,
  1,
  0,
  NOW(),
  NOW()
WHERE NOT EXISTS (
  SELECT 1
  FROM `report_config`
  WHERE `name` = @report_proc_name
    AND `deleted` = 0
);

UPDATE `report_config`
SET
  `data_source_id` = @default_data_source_final_id,
  `router_path` = IFNULL(`router_path`, @report_proc_router),
  `query_type` = 'PROCEDURE',
  `procedure_name` = 'usp_GetMonthlyOrders',
  `query_sql` = NULL,
  `count_sql` = NULL,
  `page_size` = IFNULL(`page_size`, 20),
  `exporters` = IFNULL(`exporters`, ''),
  `export_wait_message` = IFNULL(`export_wait_message`, ''),
  `query_enabled` = IFNULL(`query_enabled`, 1),
  `download_enabled` = IFNULL(`download_enabled`, 1),
  `updated_at` = NOW()
WHERE `name` = @report_proc_name
  AND `deleted` = 0;

INSERT INTO `report_config` (
  `id`,`data_source_id`,`name`,`router_path`,`query_type`,`procedure_name`,`query_sql`,`count_sql`,
  `page_size`,`exporters`,`export_wait_message`,`query_enabled`,`download_enabled`,`deleted`,`created_at`,`updated_at`
)
SELECT
  @report_sql_id,
  @default_data_source_final_id,
  @report_sql_name,
  @report_sql_router,
  'SQL',
  NULL,
  @sql_monthly_orders_query,
  @sql_monthly_orders_count,
  20,
  '张三,李四,王五',
  '导出数据量较大，请耐心等待，系统正在准备下载文件。',
  1,
  1,
  0,
  NOW(),
  NOW()
WHERE NOT EXISTS (
  SELECT 1
  FROM `report_config`
  WHERE `name` = @report_sql_name
    AND `deleted` = 0
);

UPDATE `report_config`
SET
  `data_source_id` = @default_data_source_final_id,
  `router_path` = IFNULL(`router_path`, @report_sql_router),
  `query_type` = 'SQL',
  `procedure_name` = NULL,
  `query_sql` = IFNULL(`query_sql`, @sql_monthly_orders_query),
  `count_sql` = IFNULL(`count_sql`, @sql_monthly_orders_count),
  `page_size` = IFNULL(`page_size`, 20),
  `exporters` = IFNULL(`exporters`, ''),
  `export_wait_message` = IFNULL(`export_wait_message`, ''),
  `query_enabled` = IFNULL(`query_enabled`, 1),
  `download_enabled` = IFNULL(`download_enabled`, 1),
  `updated_at` = NOW()
WHERE `name` = @report_sql_name
  AND `deleted` = 0;

SET @report_proc_real_id := (
  SELECT `id`
  FROM `report_config`
  WHERE `name` = @report_proc_name
    AND `deleted` = 0
  ORDER BY `updated_at` DESC, `id` DESC
  LIMIT 1
);

SET @report_sql_real_id := (
  SELECT `id`
  FROM `report_config`
  WHERE `name` = @report_sql_name
    AND `deleted` = 0
  ORDER BY `updated_at` DESC, `id` DESC
  LIMIT 1
);

INSERT INTO `report_field` (
  `id`,`report_id`,`label`,`field_name`,`field_type`,`match_type`,`searchable`,`search_sort`,
  `default_query_days`,`max_query_days`,`sort_order`,`deleted`,`created_at`,`updated_at`
)
SELECT t.id, t.report_id, t.label, t.field_name, t.field_type, t.match_type, 0, 0, 0, 0, t.sort_order, 0, NOW(), NOW()
FROM (
  SELECT 900000000000003001 AS id, @report_proc_real_id AS report_id, '订单编号' AS label, 'order_no' AS field_name, 'string' AS field_type, 'like' AS match_type, 1 AS sort_order
  UNION ALL SELECT 900000000000003002, @report_proc_real_id, '客户名称', 'customer_name', 'string', 'like', 2
  UNION ALL SELECT 900000000000003003, @report_proc_real_id, '订单金额', 'amount', 'number', 'eq', 3
  UNION ALL SELECT 900000000000003004, @report_proc_real_id, '创建日期', 'create_date', 'date', 'range', 4
  UNION ALL SELECT 900000000000003011, @report_sql_real_id, '订单编号', 'order_no', 'string', 'like', 1
  UNION ALL SELECT 900000000000003012, @report_sql_real_id, '客户名称', 'customer_name', 'string', 'like', 2
  UNION ALL SELECT 900000000000003013, @report_sql_real_id, '订单金额', 'amount', 'number', 'eq', 3
  UNION ALL SELECT 900000000000003014, @report_sql_real_id, '创建日期', 'create_date', 'date', 'range', 4
) t
WHERE t.report_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM `report_field` f
    WHERE f.`report_id` = t.report_id
      AND f.`field_name` = t.field_name
      AND f.`deleted` = 0
  );

INSERT INTO `report_search_field` (
  `id`,`report_id`,`label`,`field_name`,`field_type`,`match_type`,`control_type`,
  `multiline_enabled`,`option_values_json`,`search_sort`,`default_query_days`,
  `max_query_days`,`deleted`,`created_at`,`updated_at`
)
SELECT t.id, t.report_id, t.label, t.field_name, t.field_type, t.match_type, t.control_type, t.multiline_enabled,
       '[]', t.search_sort, t.default_query_days, t.max_query_days, 0, NOW(), NOW()
FROM (
  SELECT 900000000000004001 AS id, @report_proc_real_id AS report_id, '订单编号' AS label, 'order_no' AS field_name, 'string' AS field_type, 'like' AS match_type, 'input' AS control_type, 1 AS multiline_enabled, 1 AS search_sort, 0 AS default_query_days, 0 AS max_query_days
  UNION ALL SELECT 900000000000004002, @report_proc_real_id, '客户名称', 'customer_name', 'string', 'like', 'input', 0, 2, 0, 0
  UNION ALL SELECT 900000000000004003, @report_proc_real_id, '创建日期', 'create_date', 'date', 'range', 'input', 0, 3, 30, 90
  UNION ALL SELECT 900000000000004011, @report_sql_real_id, '订单编号', 'order_no', 'string', 'like', 'input', 1, 1, 0, 0
  UNION ALL SELECT 900000000000004012, @report_sql_real_id, '客户名称', 'customer_name', 'string', 'like', 'input', 0, 2, 0, 0
  UNION ALL SELECT 900000000000004013, @report_sql_real_id, '创建日期', 'create_date', 'date', 'range', 'input', 0, 3, 30, 90
) t
WHERE t.report_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM `report_search_field` s
    WHERE s.`report_id` = t.report_id
      AND s.`field_name` = t.field_name
      AND s.`deleted` = 0
  );

SET @proc_monthly_orders := CONCAT(
  'CREATE PROCEDURE `usp_GetMonthlyOrders`(',
  '    IN `order_no` VARCHAR(64),',
  '    IN `customer_name` VARCHAR(128),',
  '    IN `create_date_start` VARCHAR(32),',
  '    IN `create_date_end` VARCHAR(32),',
  '    IN `in_page_no` INT,',
  '    IN `in_page_size` INT,',
  '    OUT `out_total` BIGINT',
  ') ',
  'BEGIN ',
  '    DECLARE `v_offset` INT DEFAULT 0; ',
  '    SET `v_offset` = (IFNULL(in_page_no, 1) - 1) * IFNULL(in_page_size, 20); ',
  '    SELECT COUNT(1) INTO out_total ',
  '    FROM demo_order_data d ',
  '    WHERE (order_no IS NULL OR order_no = '''' OR ',
  '           (LOCATE('';'', order_no) > 0 AND FIND_IN_SET(d.order_no, REPLACE(CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, '';'', '','')) > 0) OR ',
  '           (LOCATE('';'', order_no) = 0 AND d.order_no LIKE CONCAT(''%'', CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, ''%''))) ',
  '      AND (customer_name IS NULL OR customer_name = '''' OR ',
  '           (LOCATE('';'', customer_name) > 0 AND FIND_IN_SET(d.customer_name, REPLACE(CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, '';'', '','')) > 0) OR ',
  '           (LOCATE('';'', customer_name) = 0 AND d.customer_name LIKE CONCAT(''%'', CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, ''%''))) ',
  '      AND (create_date_start IS NULL OR create_date_start = '''' OR d.create_date >= STR_TO_DATE(create_date_start, ''%Y-%m-%d'')) ',
  '      AND (create_date_end IS NULL OR create_date_end = '''' OR d.create_date <= STR_TO_DATE(create_date_end, ''%Y-%m-%d'')); ',
  '    IF IFNULL(in_page_size, 0) = 0 THEN ',
  '        SELECT d.order_no, d.customer_name, d.amount, DATE_FORMAT(d.create_date, ''%Y-%m-%d'') AS create_date ',
  '        FROM demo_order_data d ',
  '        WHERE (order_no IS NULL OR order_no = '''' OR ',
  '               (LOCATE('';'', order_no) > 0 AND FIND_IN_SET(d.order_no, REPLACE(CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, '';'', '','')) > 0) OR ',
  '               (LOCATE('';'', order_no) = 0 AND d.order_no LIKE CONCAT(''%'', CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, ''%''))) ',
  '          AND (customer_name IS NULL OR customer_name = '''' OR ',
  '               (LOCATE('';'', customer_name) > 0 AND FIND_IN_SET(d.customer_name, REPLACE(CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, '';'', '','')) > 0) OR ',
  '               (LOCATE('';'', customer_name) = 0 AND d.customer_name LIKE CONCAT(''%'', CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, ''%''))) ',
  '          AND (create_date_start IS NULL OR create_date_start = '''' OR d.create_date >= STR_TO_DATE(create_date_start, ''%Y-%m-%d'')) ',
  '          AND (create_date_end IS NULL OR create_date_end = '''' OR d.create_date <= STR_TO_DATE(create_date_end, ''%Y-%m-%d'')) ',
  '        ORDER BY d.create_date DESC, d.id DESC; ',
  '    ELSE ',
  '        SELECT d.order_no, d.customer_name, d.amount, DATE_FORMAT(d.create_date, ''%Y-%m-%d'') AS create_date ',
  '        FROM demo_order_data d ',
  '        WHERE (order_no IS NULL OR order_no = '''' OR ',
  '               (LOCATE('';'', order_no) > 0 AND FIND_IN_SET(d.order_no, REPLACE(CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, '';'', '','')) > 0) OR ',
  '               (LOCATE('';'', order_no) = 0 AND d.order_no LIKE CONCAT(''%'', CONVERT(order_no USING utf8mb4) COLLATE utf8mb4_general_ci, ''%''))) ',
  '          AND (customer_name IS NULL OR customer_name = '''' OR ',
  '               (LOCATE('';'', customer_name) > 0 AND FIND_IN_SET(d.customer_name, REPLACE(CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, '';'', '','')) > 0) OR ',
  '               (LOCATE('';'', customer_name) = 0 AND d.customer_name LIKE CONCAT(''%'', CONVERT(customer_name USING utf8mb4) COLLATE utf8mb4_general_ci, ''%''))) ',
  '          AND (create_date_start IS NULL OR create_date_start = '''' OR d.create_date >= STR_TO_DATE(create_date_start, ''%Y-%m-%d'')) ',
  '          AND (create_date_end IS NULL OR create_date_end = '''' OR d.create_date <= STR_TO_DATE(create_date_end, ''%Y-%m-%d'')) ',
  '        ORDER BY d.create_date DESC, d.id DESC ',
  '        LIMIT v_offset, in_page_size; ',
  '    END IF; ',
  'END'
);

DROP PROCEDURE IF EXISTS `usp_GetMonthlyOrders`;
PREPARE stmt_create_monthly_orders FROM @proc_monthly_orders;
EXECUTE stmt_create_monthly_orders;
DEALLOCATE PREPARE stmt_create_monthly_orders;
