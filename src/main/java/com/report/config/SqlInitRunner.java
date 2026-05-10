package com.report.config;

import com.report.util.DataSourcePasswordCipher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@Order(1)
public class SqlInitRunner implements CommandLineRunner {

    private final DataSource dataSource;
    private final DataSourcePasswordCipher passwordCipher;

    @Value("${spring.datasource.password:}")
    private String springDatasourcePassword;

    public SqlInitRunner(DataSource dataSource, DataSourcePasswordCipher passwordCipher) {
        this.dataSource = dataSource;
        this.passwordCipher = passwordCipher;
    }

    @Override
    public void run(String... args) throws Exception {
        FileSystemResource script = new FileSystemResource("sql/init.sql");
        if (!script.exists()) {
            throw new IllegalStateException("初始化SQL不存在: " + script.getPath());
        }
        try (Connection connection = dataSource.getConnection()) {
            ConnectionInfo connectionInfo = parseConnectionInfo(connection);
            String encryptedPassword = passwordCipher.encrypt(
                    springDatasourcePassword == null ? "" : springDatasourcePassword.trim()
            );
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET @init_app_db_host = '" + escapeSql(connectionInfo.host) + "'");
                statement.execute("SET @init_app_db_port = " + connectionInfo.port);
                statement.execute("SET @init_app_db_name = '" + escapeSql(connectionInfo.databaseName) + "'");
                statement.execute("SET @init_app_db_user = '" + escapeSql(connectionInfo.username) + "'");
                statement.execute("SET @init_app_db_password_encrypted = '" + escapeSql(encryptedPassword) + "'");
            }
            ScriptUtils.executeSqlScript(
                    connection,
                    new EncodedResource(script, StandardCharsets.UTF_8),
                    true,
                    false,
                    ScriptUtils.DEFAULT_COMMENT_PREFIX,
                    ScriptUtils.DEFAULT_STATEMENT_SEPARATOR,
                    ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER,
                    ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER
            );
        }
    }

    private ConnectionInfo parseConnectionInfo(Connection connection) throws SQLException {
        String url = connection.getMetaData().getURL();
        String username = connection.getMetaData().getUserName();
        if (username == null) {
            username = "";
        }
        int atIndex = username.indexOf('@');
        if (atIndex > -1) {
            username = username.substring(0, atIndex);
        }

        if (url == null || !url.startsWith("jdbc:mysql://")) {
            return new ConnectionInfo("127.0.0.1", 3306, "report", username);
        }
        String trimmed = url.substring("jdbc:mysql://".length());
        int queryIndex = trimmed.indexOf('?');
        if (queryIndex > -1) {
            trimmed = trimmed.substring(0, queryIndex);
        }
        int slashIndex = trimmed.indexOf('/');
        if (slashIndex < 0) {
            return new ConnectionInfo("127.0.0.1", 3306, "report", username);
        }
        String hostPort = trimmed.substring(0, slashIndex).trim();
        String databaseName = trimmed.substring(slashIndex + 1).trim();
        String host = hostPort;
        int port = 3306;
        int colonIndex = hostPort.lastIndexOf(':');
        if (colonIndex > -1 && colonIndex < hostPort.length() - 1) {
            host = hostPort.substring(0, colonIndex).trim();
            try {
                port = Integer.parseInt(hostPort.substring(colonIndex + 1).trim());
            } catch (NumberFormatException ignored) {
                port = 3306;
            }
        }
        if (host.isEmpty()) {
            host = "127.0.0.1";
        }
        if (databaseName.isEmpty()) {
            databaseName = "report";
        }
        return new ConnectionInfo(host, port, databaseName, username);
    }

    private String escapeSql(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    private static final class ConnectionInfo {
        private final String host;
        private final int port;
        private final String databaseName;
        private final String username;

        private ConnectionInfo(String host, int port, String databaseName, String username) {
            this.host = host;
            this.port = port;
            this.databaseName = databaseName;
            this.username = username;
        }
    }
}
