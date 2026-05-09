package com.report.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureReportDataSourceTable();
        ensureReportConfigDataSourceColumn();
        ensureReportConfigDataSourceIndex();
        ensureReportConfigQueryEnabledColumn();
        ensureReportConfigDownloadEnabledColumn();
        backfillReportConfigCapabilityFlags();
    }

    private void ensureReportDataSourceTable() {
        if (tableExists("report_data_source")) {
            return;
        }
        jdbcTemplate.execute(
                "CREATE TABLE `report_data_source` (" +
                        "  `id` BIGINT NOT NULL COMMENT 'Snowflake ID'," +
                        "  `name` VARCHAR(128) NOT NULL COMMENT 'Data source name'," +
                        "  `type` VARCHAR(32) NOT NULL COMMENT 'MYSQL/ORACLE/PGSQL/SQLSERVER'," +
                        "  `host` VARCHAR(128) NOT NULL COMMENT 'Database host'," +
                        "  `port` INT NOT NULL COMMENT 'Database port'," +
                        "  `database_name` VARCHAR(128) NOT NULL COMMENT 'Database name'," +
                        "  `username` VARCHAR(128) NOT NULL COMMENT 'Database username'," +
                        "  `password_encrypted` VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Encrypted password'," +
                        "  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0-active 1-deleted'," +
                        "  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "  PRIMARY KEY (`id`)," +
                        "  KEY `idx_report_data_source_deleted_updated` (`deleted`, `updated_at`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Report data source'");
    }

    private void ensureReportConfigDataSourceColumn() {
        if (columnExists("report_config", "data_source_id")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `report_config` ADD COLUMN `data_source_id` BIGINT NULL COMMENT 'Bound data source ID'");
    }

    private void ensureReportConfigDataSourceIndex() {
        if (indexExists("report_config", "idx_report_config_data_source_id")) {
            return;
        }
        jdbcTemplate.execute("CREATE INDEX `idx_report_config_data_source_id` ON `report_config` (`data_source_id`)");
    }

    private void ensureReportConfigQueryEnabledColumn() {
        if (columnExists("report_config", "query_enabled")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `report_config` ADD COLUMN `query_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '1-query enabled 0-disabled'");
    }

    private void ensureReportConfigDownloadEnabledColumn() {
        if (columnExists("report_config", "download_enabled")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `report_config` ADD COLUMN `download_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '1-download enabled 0-disabled'");
    }

    private void backfillReportConfigCapabilityFlags() {
        if (!columnExists("report_config", "query_enabled") || !columnExists("report_config", "download_enabled")) {
            return;
        }
        jdbcTemplate.execute("UPDATE `report_config` SET `query_enabled` = 1 WHERE `query_enabled` IS NULL");
        jdbcTemplate.execute("UPDATE `report_config` SET `download_enabled` = 1 WHERE `download_enabled` IS NULL");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName);
        return count != null && count > 0;
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class,
                tableName,
                indexName);
        return count != null && count > 0;
    }
}
