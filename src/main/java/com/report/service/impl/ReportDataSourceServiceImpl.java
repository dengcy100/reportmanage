package com.report.service.impl;

import com.report.domain.dto.PageResult;
import com.report.domain.dto.ReportDataSourceOptionVO;
import com.report.domain.dto.ReportDataSourceUpsertRequest;
import com.report.domain.dto.ReportDataSourceVO;
import com.report.domain.entity.ReportDataSourceEntity;
import com.report.exception.AccessDeniedException;
import com.report.exception.BusinessException;
import com.report.mapper.ReportDataSourceMapper;
import com.report.service.ReportDataSourceService;
import com.report.service.UserContextService;
import com.report.util.DataSourcePasswordCipher;
import com.report.util.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportDataSourceServiceImpl implements ReportDataSourceService {

    private static final String MYSQL_TYPE = "MYSQL";
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;

    private final ReportDataSourceMapper reportDataSourceMapper;
    private final DataSourcePasswordCipher passwordCipher;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final UserContextService userContextService;

    public ReportDataSourceServiceImpl(ReportDataSourceMapper reportDataSourceMapper,
                                       DataSourcePasswordCipher passwordCipher,
                                       SnowflakeIdGenerator snowflakeIdGenerator,
                                       UserContextService userContextService) {
        this.reportDataSourceMapper = reportDataSourceMapper;
        this.passwordCipher = passwordCipher;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.userContextService = userContextService;
    }

    @Override
    public PageResult<ReportDataSourceVO> pageList(int pageNo, int pageSize, String keyword) {
        ensureAdminAccess("仅管理员可查看数据源");
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePageNo - 1) * safePageSize;
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
        List<ReportDataSourceEntity> entities = reportDataSourceMapper.pageList(safeKeyword, offset, safePageSize);
        long total = reportDataSourceMapper.count(safeKeyword);
        List<ReportDataSourceVO> list = new ArrayList<ReportDataSourceVO>();
        for (ReportDataSourceEntity entity : entities) {
            list.add(toVO(entity));
        }
        PageResult<ReportDataSourceVO> page = new PageResult<ReportDataSourceVO>();
        page.setPageNo(safePageNo);
        page.setPageSize(safePageSize);
        page.setTotal(total);
        page.setList(list);
        return page;
    }

    @Override
    public List<ReportDataSourceOptionVO> listMysqlOptions() {
        return reportDataSourceMapper.listMysqlOptions();
    }

    @Override
    public Map<Long, ReportDataSourceEntity> getActiveMysqlDataSourceMap(List<Long> ids) {
        Map<Long, ReportDataSourceEntity> result = new HashMap<Long, ReportDataSourceEntity>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<ReportDataSourceEntity> entities = reportDataSourceMapper.findActiveByIds(ids);
        for (ReportDataSourceEntity entity : entities) {
            if (entity != null && entity.getId() != null) {
                result.put(entity.getId(), entity);
            }
        }
        return result;
    }

    @Override
    public ReportDataSourceVO getDetail(Long id) {
        ensureAdminAccess("仅管理员可查看数据源");
        return toVO(getActiveMysqlDataSource(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReportDataSourceUpsertRequest request) {
        ensureAdminAccess("仅管理员可管理数据源");
        String name = normalizeRequired(request.getName(), "数据源名称不能为空");
        String type = normalizeType(request.getType());
        ensureMysqlOnly(type);
        ensureNameUnique(name, null);
        testConnection(request);
        ReportDataSourceEntity entity = new ReportDataSourceEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setName(name);
        entity.setType(type);
        entity.setHost(normalizeRequired(request.getHost(), "数据库IP不能为空"));
        entity.setPort(normalizePort(request.getPort()));
        entity.setDatabaseName(normalizeRequired(request.getDatabaseName(), "数据库名不能为空"));
        entity.setUsername(normalizeRequired(request.getUsername(), "用户名不能为空"));
        String password = normalizeRequired(request.getPassword(), "密码不能为空");
        entity.setPasswordEncrypted(passwordCipher.encrypt(password));
        reportDataSourceMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ReportDataSourceUpsertRequest request) {
        ensureAdminAccess("仅管理员可管理数据源");
        ReportDataSourceEntity old = getActiveMysqlDataSource(id);
        String name = normalizeRequired(request.getName(), "数据源名称不能为空");
        String type = normalizeType(request.getType());
        ensureMysqlOnly(type);
        ensureNameUnique(name, id);
        request.setId(id);
        testConnection(request);
        ReportDataSourceEntity entity = new ReportDataSourceEntity();
        entity.setId(old.getId());
        entity.setName(name);
        entity.setType(type);
        entity.setHost(normalizeRequired(request.getHost(), "数据库IP不能为空"));
        entity.setPort(normalizePort(request.getPort()));
        entity.setDatabaseName(normalizeRequired(request.getDatabaseName(), "数据库名不能为空"));
        entity.setUsername(normalizeRequired(request.getUsername(), "用户名不能为空"));
        String password = normalizeOptional(request.getPassword());
        entity.setPasswordEncrypted(StringUtils.hasText(password)
                ? passwordCipher.encrypt(password)
                : old.getPasswordEncrypted());
        if (!StringUtils.hasText(entity.getPasswordEncrypted())) {
            entity.setPasswordEncrypted(passwordCipher.encrypt(""));
        }
        int rows = reportDataSourceMapper.updateById(entity);
        if (rows == 0) {
            throw new BusinessException("数据源不存在或已删除");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ensureAdminAccess("仅管理员可管理数据源");
        ReportDataSourceEntity entity = getActiveMysqlDataSource(id);
        long referenced = reportDataSourceMapper.countReferencedByReports(id);
        if (referenced > 0) {
            throw new BusinessException("该数据源已被报表引用，无法删除");
        }
        int rows = reportDataSourceMapper.logicDelete(entity.getId());
        if (rows == 0) {
            throw new BusinessException("删除失败，数据源不存在");
        }
    }

    @Override
    public void testConnection(ReportDataSourceUpsertRequest request) {
        ensureAdminAccess("仅管理员可管理数据源");
        RuntimeConnectionSpec spec = resolveConnectionSpec(request);
        try (Connection conn = openMysqlConnection(spec)) {
            try (Statement statement = conn.createStatement()) {
                statement.setQueryTimeout(5);
                statement.execute("SELECT 1");
            }
        } catch (Exception ex) {
            throw new BusinessException("测试连接失败: " + ex.getMessage());
        }
    }

    @Override
    public Connection openConnection(Long id) {
        ReportDataSourceEntity entity = getActiveMysqlDataSource(id);
        return openMysqlConnection(new RuntimeConnectionSpec(
                entity.getHost(),
                entity.getPort(),
                entity.getDatabaseName(),
                entity.getUsername(),
                passwordCipher.decrypt(entity.getPasswordEncrypted())));
    }

    @Override
    public ReportDataSourceEntity getActiveMysqlDataSource(Long id) {
        if (id == null) {
            throw new BusinessException("请选择数据源");
        }
        ReportDataSourceEntity entity = reportDataSourceMapper.findById(id);
        if (entity == null) {
            throw new BusinessException("数据源不存在或已删除");
        }
        ensureMysqlOnly(normalizeType(entity.getType()));
        return entity;
    }

    private void ensureAdminAccess(String message) {
        if (!userContextService.isCurrentUserAdmin()) {
            throw new AccessDeniedException(message);
        }
    }

    private void ensureNameUnique(String name, Long excludeId) {
        long count = reportDataSourceMapper.countByNameExcludeId(name, excludeId == null ? -1L : excludeId);
        if (count > 0) {
            throw new BusinessException("数据源名称已存在");
        }
    }

    private String normalizeRequired(String value, String errorMessage) {
        String text = value == null ? "" : value.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(errorMessage);
        }
        return text;
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private Integer normalizePort(Integer port) {
        if (port == null || port < 1 || port > 65535) {
            throw new BusinessException("端口必须在1-65535之间");
        }
        return port;
    }

    private String normalizeType(String type) {
        String normalized = normalizeRequired(type, "数据源类型不能为空").toUpperCase();
        return normalized;
    }

    private void ensureMysqlOnly(String type) {
        if (!MYSQL_TYPE.equals(type)) {
            throw new BusinessException("当前版本仅支持 MYSQL 数据源");
        }
    }

    private ReportDataSourceVO toVO(ReportDataSourceEntity entity) {
        ReportDataSourceVO vo = new ReportDataSourceVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setType(entity.getType());
        vo.setHost(entity.getHost());
        vo.setPort(entity.getPort());
        vo.setDatabaseName(entity.getDatabaseName());
        vo.setUsername(entity.getUsername());
        vo.setPasswordConfigured(StringUtils.hasText(entity.getPasswordEncrypted()));
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private RuntimeConnectionSpec resolveConnectionSpec(ReportDataSourceUpsertRequest request) {
        String type = normalizeType(request.getType());
        ensureMysqlOnly(type);
        String host = normalizeRequired(request.getHost(), "数据库IP不能为空");
        int port = normalizePort(request.getPort());
        String databaseName = normalizeRequired(request.getDatabaseName(), "数据库名不能为空");
        String username = normalizeRequired(request.getUsername(), "用户名不能为空");
        String password = normalizeOptional(request.getPassword());
        if (!StringUtils.hasText(password) && request.getId() != null) {
            ReportDataSourceEntity existing = reportDataSourceMapper.findById(request.getId());
            if (existing == null) {
                throw new BusinessException("数据源不存在或已删除");
            }
            ensureMysqlOnly(normalizeType(existing.getType()));
            password = passwordCipher.decrypt(existing.getPasswordEncrypted());
        } else if (!StringUtils.hasText(password)) {
            throw new BusinessException("密码不能为空");
        }
        return new RuntimeConnectionSpec(host, port, databaseName, username, password);
    }

    private Connection openMysqlConnection(RuntimeConnectionSpec spec) {
        String jdbcUrl = buildMysqlJdbcUrl(spec.host, spec.port, spec.databaseName);
        try {
            return DriverManager.getConnection(jdbcUrl, spec.username, spec.password);
        } catch (Exception ex) {
            throw new BusinessException("连接数据库失败: " + ex.getMessage());
        }
    }

    private String buildMysqlJdbcUrl(String host, int port, String databaseName) {
        return "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=" + DEFAULT_CONNECT_TIMEOUT_MS;
    }

    private static class RuntimeConnectionSpec {
        private final String host;
        private final int port;
        private final String databaseName;
        private final String username;
        private final String password;

        private RuntimeConnectionSpec(String host, int port, String databaseName, String username, String password) {
            this.host = host;
            this.port = port;
            this.databaseName = databaseName;
            this.username = username;
            this.password = password;
        }
    }

}
