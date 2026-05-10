package com.report.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.report.domain.dto.PageResult;
import com.report.domain.dto.ReportFieldItemRequest;
import com.report.domain.dto.ReportFieldVO;
import com.report.domain.dto.ReportSearchFieldItemRequest;
import com.report.domain.dto.ReportSearchFieldVO;
import com.report.domain.dto.ReportSearchOptionItemRequest;
import com.report.domain.dto.ReportSearchOptionVO;
import com.report.domain.dto.ReportUpsertRequest;
import com.report.domain.dto.ReportVO;
import com.report.domain.entity.ReportConfigEntity;
import com.report.domain.entity.ReportDataSourceEntity;
import com.report.domain.entity.ReportFieldEntity;
import com.report.domain.entity.ReportSearchFieldEntity;
import com.report.exception.BusinessException;
import com.report.mapper.ReportConfigMapper;
import com.report.mapper.ReportFieldMapper;
import com.report.mapper.ReportSearchFieldMapper;
import com.report.service.ReportDataSourceService;
import com.report.service.ReportService;
import com.report.service.UserContextService;
import com.report.util.PermissionUsers;
import com.report.util.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReportServiceImpl implements ReportService {

    private static final Set<Integer> ALLOWED_PAGE_SIZE = new HashSet<Integer>();
    private static final int MAX_QUERY_DAYS_LIMIT = 3650;
    private static final int MAX_EXPORT_WAIT_MESSAGE_LENGTH = 255;
    private static final String QUERY_TYPE_PROCEDURE = "PROCEDURE";
    private static final String QUERY_TYPE_SQL = "SQL";
    private static final Pattern PROCEDURE_NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");
    private static final Pattern SQL_PLACEHOLDER_PATTERN = Pattern.compile("#\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\}");
    private static final Pattern SQL_READONLY_PREFIX_PATTERN = Pattern.compile("(?is)^\\s*select\\b");
    private static final Pattern SQL_FORBIDDEN_KEYWORD_PATTERN = Pattern.compile("(?is)\\b(insert|update|delete|drop|create|alter|truncate|merge|call|exec|execute|grant|revoke)\\b");
    private static final Pattern SQL_LIMIT_PATTERN = Pattern.compile("(?is)\\blimit\\b");
    private static final Pattern ROUTER_PATH_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,127}$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<ReportSearchOptionVO>> SEARCH_OPTION_LIST_TYPE =
            new TypeReference<List<ReportSearchOptionVO>>() {
            };
    private static final int FLAG_ENABLED = 1;
    private static final int FLAG_DISABLED = 0;

    static {
        ALLOWED_PAGE_SIZE.add(10);
        ALLOWED_PAGE_SIZE.add(20);
        ALLOWED_PAGE_SIZE.add(50);
        ALLOWED_PAGE_SIZE.add(100);
        ALLOWED_PAGE_SIZE.add(200);
    }

    private final ReportConfigMapper reportConfigMapper;
    private final ReportFieldMapper reportFieldMapper;
    private final ReportSearchFieldMapper reportSearchFieldMapper;
    private final ReportDataSourceService reportDataSourceService;
    private final UserContextService userContextService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public ReportServiceImpl(ReportConfigMapper reportConfigMapper,
                             ReportFieldMapper reportFieldMapper,
                             ReportSearchFieldMapper reportSearchFieldMapper,
                             ReportDataSourceService reportDataSourceService,
                             UserContextService userContextService,
                             SnowflakeIdGenerator snowflakeIdGenerator) {
        this.reportConfigMapper = reportConfigMapper;
        this.reportFieldMapper = reportFieldMapper;
        this.reportSearchFieldMapper = reportSearchFieldMapper;
        this.reportDataSourceService = reportDataSourceService;
        this.userContextService = userContextService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Override
    public PageResult<ReportVO> pageList(int pageNo, int pageSize, String keyword) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePageNo - 1) * safePageSize;
        List<ReportConfigEntity> entities = reportConfigMapper.pageList(keyword, offset, safePageSize);
        long total = reportConfigMapper.count(keyword);
        List<ReportVO> list = new ArrayList<ReportVO>();
        Set<Long> dataSourceIdSet = new HashSet<Long>();
        for (ReportConfigEntity entity : entities) {
            if (entity.getDataSourceId() != null) {
                dataSourceIdSet.add(entity.getDataSourceId());
            }
        }
        java.util.Map<Long, ReportDataSourceEntity> dataSourceMap =
                reportDataSourceService.getActiveMysqlDataSourceMap(new ArrayList<Long>(dataSourceIdSet));
        for (ReportConfigEntity entity : entities) {
            ReportDataSourceEntity dataSource = entity.getDataSourceId() == null
                    ? null
                    : dataSourceMap.get(entity.getDataSourceId());
            list.add(toReportVO(entity, null, null, dataSource));
        }
        PageResult<ReportVO> page = new PageResult<ReportVO>();
        page.setPageNo(safePageNo);
        page.setPageSize(safePageSize);
        page.setTotal(total);
        page.setList(list);
        return page;
    }

    @Override
    public ReportVO getDetail(Long id) {
        ReportConfigEntity config = getConfigOrThrow(id);
        List<ReportFieldEntity> fields = reportFieldMapper.findByReportId(id);
        List<ReportSearchFieldEntity> searchFields = reportSearchFieldMapper.findByReportId(id);
        if (searchFields == null || searchFields.isEmpty()) {
            searchFields = fallbackSearchFieldsFromLegacy(fields);
        }
        ReportDataSourceEntity dataSource = reportDataSourceService.getActiveMysqlDataSource(config.getDataSourceId());
        return toReportVO(config, fields, searchFields, dataSource);
    }

    @Override
    public ReportVO getDetailByRouterPath(String routerPath) {
        String safeRouterPath = normalizeRouterPath(routerPath);
        if (!StringUtils.hasText(safeRouterPath)) {
            throw new BusinessException("第三方系统路由路径不能为空");
        }
        ReportConfigEntity config = reportConfigMapper.findByRouterPath(safeRouterPath);
        if (config == null) {
            throw new BusinessException("未找到第三方系统路由路径对应的报表");
        }
        return getDetail(config.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReportUpsertRequest request) {
        validateUpsertRequest(request, null);
        ReportConfigEntity entity = new ReportConfigEntity();
        long reportId = snowflakeIdGenerator.nextId();
        entity.setId(reportId);
        entity.setDataSourceId(request.getDataSourceId());
        entity.setName(request.getName().trim());
        entity.setRouterPath(normalizeRouterPath(request.getRouterPath()));
        String queryType = normalizeQueryType(request.getQueryType());
        entity.setQueryType(queryType);
        entity.setProcedureName(normalizeProcedureNameByType(queryType, request.getProcedureName()));
        entity.setQuerySql(normalizeSqlByType(queryType, request.getQuerySql()));
        entity.setCountSql(normalizeSqlByType(queryType, request.getCountSql()));
        entity.setPageSize(request.getPageSize());
        entity.setQueryUsers(normalizePermissionUsers(request.getQueryUsers()));
        entity.setExporters(normalizePermissionUsers(request.getExporters()));
        entity.setExportWaitMessage(normalizeExportWaitMessage(request.getExportWaitMessage()));
        entity.setQueryEnabled(normalizeEnabledFlag(request.getQueryEnabled()));
        entity.setDownloadEnabled(normalizeEnabledFlag(request.getDownloadEnabled()));
        reportConfigMapper.insert(entity);
        insertFieldSnapshot(reportId, request.getFields());
        insertSearchFieldSnapshot(reportId, request.getSearchFields());
        return reportId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ReportUpsertRequest request) {
        validateUpsertRequest(request, id);
        ReportConfigEntity old = getConfigOrThrow(id);
        ReportConfigEntity entity = new ReportConfigEntity();
        entity.setId(old.getId());
        entity.setDataSourceId(request.getDataSourceId());
        entity.setName(request.getName().trim());
        entity.setRouterPath(normalizeRouterPath(request.getRouterPath()));
        String queryType = normalizeQueryType(request.getQueryType());
        entity.setQueryType(queryType);
        entity.setProcedureName(normalizeProcedureNameByType(queryType, request.getProcedureName()));
        entity.setQuerySql(normalizeSqlByType(queryType, request.getQuerySql()));
        entity.setCountSql(normalizeSqlByType(queryType, request.getCountSql()));
        entity.setPageSize(request.getPageSize());
        entity.setQueryUsers(normalizePermissionUsers(request.getQueryUsers()));
        entity.setExporters(normalizePermissionUsers(request.getExporters()));
        entity.setExportWaitMessage(normalizeExportWaitMessage(request.getExportWaitMessage()));
        entity.setQueryEnabled(normalizeEnabledFlag(request.getQueryEnabled()));
        entity.setDownloadEnabled(normalizeEnabledFlag(request.getDownloadEnabled()));
        int rows = reportConfigMapper.updateById(entity);
        if (rows == 0) {
            throw new BusinessException("报表不存在或已删除");
        }
        reportFieldMapper.logicDeleteByReportId(id);
        insertFieldSnapshot(id, request.getFields());
        reportSearchFieldMapper.logicDeleteByReportId(id);
        insertSearchFieldSnapshot(id, request.getSearchFields());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getConfigOrThrow(id);
        reportFieldMapper.logicDeleteByReportId(id);
        reportSearchFieldMapper.logicDeleteByReportId(id);
        int rows = reportConfigMapper.logicDelete(id);
        if (rows == 0) {
            throw new BusinessException("删除失败，报表不存在");
        }
    }

    private ReportConfigEntity getConfigOrThrow(Long id) {
        ReportConfigEntity config = reportConfigMapper.findById(id);
        if (config == null) {
            throw new BusinessException("报表不存在或已删除");
        }
        return config;
    }

    private String normalizePermissionUsers(String permissionUsers) {
        return PermissionUsers.normalize(permissionUsers);
    }

    private String normalizeRouterPath(String routerPath) {
        if (!StringUtils.hasText(routerPath)) {
            return null;
        }
        return routerPath.trim();
    }

    private String normalizeExportWaitMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String trimmed = message.trim();
        if (trimmed.length() > MAX_EXPORT_WAIT_MESSAGE_LENGTH) {
            throw new BusinessException("导出等待提示最多 " + MAX_EXPORT_WAIT_MESSAGE_LENGTH + " 个字符");
        }
        return trimmed;
    }

    private void validateUpsertRequest(ReportUpsertRequest request, Long excludeId) {
        String routerPath = normalizeRouterPath(request.getRouterPath());
        if (StringUtils.hasText(routerPath)) {
            if (!ROUTER_PATH_PATTERN.matcher(routerPath).matches()) {
                throw new BusinessException("第三方系统路由路径格式不合法");
            }
            if (reportConfigMapper.countByRouterPath(routerPath, excludeId) > 0) {
                throw new BusinessException("第三方系统路由路径已存在: " + routerPath);
            }
        }
        String queryType = normalizeQueryType(request.getQueryType());
        validateQueryDefinitionByType(queryType, request.getProcedureName(), request.getQuerySql(), request.getCountSql(), request.getSearchFields());
        if (!ALLOWED_PAGE_SIZE.contains(request.getPageSize())) {
            throw new BusinessException("每页行数仅允许 10/20/50/100/200");
        }
        if (request.getDataSourceId() == null) {
            throw new BusinessException("请选择数据源");
        }
        boolean queryEnabled = resolveEnabled(request.getQueryEnabled(), true);
        boolean downloadEnabled = resolveEnabled(request.getDownloadEnabled(), true);
        if (!queryEnabled && !downloadEnabled) {
            throw new BusinessException("查询和下载至少启用一个");
        }
        reportDataSourceService.getActiveMysqlDataSource(request.getDataSourceId());
        validateFields(request.getFields());
        validateSearchFields(request.getSearchFields());
    }

    private String normalizeQueryType(String queryType) {
        if (!StringUtils.hasText(queryType)) {
            return QUERY_TYPE_PROCEDURE;
        }
        String normalized = queryType.trim().toUpperCase();
        if (!QUERY_TYPE_PROCEDURE.equals(normalized) && !QUERY_TYPE_SQL.equals(normalized)) {
            throw new BusinessException("查询类型仅支持 PROCEDURE 或 SQL");
        }
        return normalized;
    }

    private String normalizeProcedureNameByType(String queryType, String procedureName) {
        if (!QUERY_TYPE_PROCEDURE.equals(queryType)) {
            return null;
        }
        return procedureName == null ? "" : procedureName.trim();
    }

    private String normalizeSqlByType(String queryType, String sql) {
        if (!QUERY_TYPE_SQL.equals(queryType)) {
            return null;
        }
        return sql == null ? "" : sql.trim();
    }

    private void validateQueryDefinitionByType(String queryType,
                                               String procedureName,
                                               String querySql,
                                               String countSql,
                                               List<ReportSearchFieldItemRequest> searchFields) {
        if (QUERY_TYPE_PROCEDURE.equals(queryType)) {
            validateProcedureName(procedureName);
            return;
        }
        validateSqlTemplate(querySql, "查询SQL", true);
        validateSqlTemplate(countSql, "统计SQL", false);
        validateSqlPlaceholders(querySql, countSql, searchFields);
    }

    private void validateProcedureName(String procedureName) {
        String normalized = procedureName == null ? "" : procedureName.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("存储过程不能为空");
        }
        if (!PROCEDURE_NAME_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException("存储过程仅允许字母数字下划线，且不能以数字开头");
        }
    }

    private void validateSqlTemplate(String sql, String label, boolean rejectLimit) {
        String normalized = sql == null ? "" : sql.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(label + "不能为空");
        }
        if (!SQL_READONLY_PREFIX_PATTERN.matcher(normalized).find()) {
            throw new BusinessException(label + "仅允许 SELECT 语句");
        }
        if (normalized.contains(";") || normalized.contains("--") || normalized.contains("/*") || normalized.contains("*/")) {
            throw new BusinessException(label + "禁止注释和多语句");
        }
        if (normalized.contains("${")) {
            throw new BusinessException(label + "禁止使用 ${} 占位符");
        }
        if (SQL_FORBIDDEN_KEYWORD_PATTERN.matcher(normalized).find()) {
            throw new BusinessException(label + "包含禁止的关键字");
        }
        if (rejectLimit && SQL_LIMIT_PATTERN.matcher(normalized).find()) {
            throw new BusinessException(label + "禁止显式 LIMIT，分页由系统统一处理");
        }
    }

    private void validateSqlPlaceholders(String querySql, String countSql, List<ReportSearchFieldItemRequest> searchFields) {
        Set<String> allowedKeys = buildAllowedSqlPlaceholderKeys(searchFields);
        validateSqlPlaceholderSet("查询SQL", querySql, allowedKeys);
        validateSqlPlaceholderSet("统计SQL", countSql, allowedKeys);
    }

    private Set<String> buildAllowedSqlPlaceholderKeys(List<ReportSearchFieldItemRequest> searchFields) {
        Set<String> keys = new HashSet<String>();
        if (searchFields == null || searchFields.isEmpty()) {
            return keys;
        }
        for (ReportSearchFieldItemRequest field : searchFields) {
            if (field == null || !StringUtils.hasText(field.getField())) {
                continue;
            }
            String key = field.getField().trim();
            keys.add(key);
            if ("range".equals(field.getMatch())) {
                keys.add(key + "_start");
                keys.add(key + "_end");
            }
        }
        return keys;
    }

    private void validateSqlPlaceholderSet(String label, String sql, Set<String> allowedKeys) {
        Matcher matcher = SQL_PLACEHOLDER_PATTERN.matcher(sql == null ? "" : sql);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!allowedKeys.contains(key)) {
                throw new BusinessException(label + "包含未配置的占位符: " + key);
            }
        }
    }

    private int normalizeEnabledFlag(Boolean enabled) {
        return resolveEnabled(enabled, true) ? FLAG_ENABLED : FLAG_DISABLED;
    }

    private static boolean resolveEnabled(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static boolean resolveEnabled(Integer value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value.intValue() == FLAG_ENABLED;
    }

    private void validateFields(List<ReportFieldItemRequest> fields) {
        Set<String> fieldNameSet = new HashSet<String>();
        int expectedSort = 1;
        for (ReportFieldItemRequest field : fields) {
            String fieldName = field.getField().trim().toLowerCase();
            if (!fieldNameSet.add(fieldName)) {
                throw new BusinessException("字段重复: " + field.getField());
            }
            if (!field.getSort().equals(expectedSort)) {
                throw new BusinessException("字段排序必须从1连续递增");
            }
            expectedSort++;
        }
    }

    private void validateSearchFields(List<ReportSearchFieldItemRequest> searchFields) {
        if (searchFields == null || searchFields.isEmpty()) {
            return;
        }
        Set<Integer> searchSortSet = new HashSet<Integer>();
        Set<String> searchFieldNameSet = new HashSet<String>();
        for (ReportSearchFieldItemRequest field : searchFields) {
            String searchFieldName = field.getField().trim().toLowerCase();
            if (!searchFieldNameSet.add(searchFieldName)) {
                throw new BusinessException("搜索字段重复: " + field.getField());
            }
            if (!searchSortSet.add(field.getSearchSort())) {
                throw new BusinessException("搜索排序重复: " + field.getSearchSort());
            }
            validateSearchControlRule(field);
            validateSearchOptions(field);
            validateSearchDateQueryDays(field);
        }
    }

    private void validateSearchControlRule(ReportSearchFieldItemRequest field) {
        if (!"input".equals(field.getControlType()) && Boolean.TRUE.equals(field.getMultilineEnabled())) {
            throw new BusinessException("字段[" + field.getLabel() + "]仅输入框支持批量输入");
        }
        if ("single_select".equals(field.getControlType()) && "range".equals(field.getMatch())) {
            throw new BusinessException("字段[" + field.getLabel() + "]单选不支持区间查询");
        }
        if ("multi_select".equals(field.getControlType()) && !"in".equals(field.getMatch())) {
            throw new BusinessException("字段[" + field.getLabel() + "]多选仅支持包含匹配");
        }
    }

    private void validateSearchOptions(ReportSearchFieldItemRequest field) {
        boolean selectControl = "single_select".equals(field.getControlType()) || "multi_select".equals(field.getControlType());
        List<ReportSearchOptionItemRequest> options = field.getOptions() == null
                ? Collections.<ReportSearchOptionItemRequest>emptyList()
                : field.getOptions();
        if (!selectControl) {
            if (!options.isEmpty()) {
                throw new BusinessException("字段[" + field.getLabel() + "]仅单选/多选支持配置选项");
            }
            return;
        }
        if (options.isEmpty()) {
            throw new BusinessException("字段[" + field.getLabel() + "]请至少配置一个选项");
        }
        Set<String> optionValueSet = new HashSet<String>();
        for (ReportSearchOptionItemRequest option : options) {
            String value = option.getValue() == null ? "" : option.getValue().trim();
            if (!optionValueSet.add(value)) {
                throw new BusinessException("字段[" + field.getLabel() + "]选项值重复: " + value);
            }
            if (value.contains(";") || value.contains("\n") || value.contains("\r")) {
                throw new BusinessException("字段[" + field.getLabel() + "]选项值不能包含分号或换行");
            }
        }
    }

    private void validateSearchDateQueryDays(ReportSearchFieldItemRequest field) {
        int defaultQueryDays = normalizeQueryDays(field.getDefaultQueryDays());
        int maxQueryDays = normalizeQueryDays(field.getMaxQueryDays());
        if (isDateRangeSearchField(field)) {
            if (maxQueryDays > 0 && defaultQueryDays > maxQueryDays) {
                throw new BusinessException("字段[" + field.getLabel() + "]默认查询天数不能大于最长查询天数");
            }
            return;
        }
        if (defaultQueryDays > 0 || maxQueryDays > 0) {
            throw new BusinessException("字段[" + field.getLabel() + "]仅日期/日期时间区间搜索字段支持查询天数配置");
        }
    }

    private boolean isDateRangeSearchField(ReportSearchFieldItemRequest field) {
        return "range".equals(field.getMatch())
                && ("date".equals(field.getType()) || "datetime".equals(field.getType()));
    }

    private int normalizeQueryDays(Integer value) {
        if (value == null) {
            return 0;
        }
        if (value < 0 || value > MAX_QUERY_DAYS_LIMIT) {
            throw new BusinessException("查询天数必须在0-" + MAX_QUERY_DAYS_LIMIT + "之间");
        }
        return value;
    }

    private void insertFieldSnapshot(Long reportId, List<ReportFieldItemRequest> fields) {
        for (ReportFieldItemRequest req : fields) {
            ReportFieldEntity field = new ReportFieldEntity();
            field.setId(snowflakeIdGenerator.nextId());
            field.setReportId(reportId);
            field.setLabel(req.getLabel().trim());
            field.setFieldName(req.getField().trim());
            field.setFieldType(req.getType());
            field.setMatchType(req.getMatch());
            field.setSearchable(0);
            field.setSearchSort(0);
            field.setDefaultQueryDays(0);
            field.setMaxQueryDays(0);
            field.setSortOrder(req.getSort());
            reportFieldMapper.insert(field);
        }
    }

    private void insertSearchFieldSnapshot(Long reportId, List<ReportSearchFieldItemRequest> searchFields) {
        if (searchFields == null || searchFields.isEmpty()) {
            return;
        }
        for (ReportSearchFieldItemRequest req : searchFields) {
            ReportSearchFieldEntity field = new ReportSearchFieldEntity();
            field.setId(snowflakeIdGenerator.nextId());
            field.setReportId(reportId);
            field.setLabel(req.getLabel().trim());
            field.setFieldName(req.getField().trim());
            field.setFieldType(req.getType());
            field.setMatchType(req.getMatch());
            field.setControlType(req.getControlType());
            field.setMultilineEnabled(Boolean.TRUE.equals(req.getMultilineEnabled()) ? 1 : 0);
            field.setOptionValuesJson(serializeSearchOptions(req.getOptions()));
            field.setSearchSort(req.getSearchSort());
            if (isDateRangeSearchField(req)) {
                field.setDefaultQueryDays(normalizeQueryDays(req.getDefaultQueryDays()));
                field.setMaxQueryDays(normalizeQueryDays(req.getMaxQueryDays()));
            } else {
                field.setDefaultQueryDays(0);
                field.setMaxQueryDays(0);
            }
            reportSearchFieldMapper.insert(field);
        }
    }

    private String serializeSearchOptions(List<ReportSearchOptionItemRequest> options) {
        List<ReportSearchOptionItemRequest> safeOptions = options == null
                ? Collections.<ReportSearchOptionItemRequest>emptyList()
                : options;
        try {
            return OBJECT_MAPPER.writeValueAsString(safeOptions);
        } catch (Exception ex) {
            throw new BusinessException("搜索选项序列化失败: " + ex.getMessage());
        }
    }

    private List<ReportSearchFieldEntity> fallbackSearchFieldsFromLegacy(List<ReportFieldEntity> fieldEntities) {
        if (fieldEntities == null || fieldEntities.isEmpty()) {
            return Collections.emptyList();
        }
        List<ReportSearchFieldEntity> list = new ArrayList<ReportSearchFieldEntity>();
        int fallbackSort = 1;
        for (ReportFieldEntity fieldEntity : fieldEntities) {
            if (fieldEntity.getSearchable() == null || fieldEntity.getSearchable() != 1) {
                continue;
            }
            ReportSearchFieldEntity searchField = new ReportSearchFieldEntity();
            searchField.setId(fieldEntity.getId());
            searchField.setReportId(fieldEntity.getReportId());
            searchField.setLabel(fieldEntity.getLabel());
            searchField.setFieldName(fieldEntity.getFieldName());
            searchField.setFieldType(fieldEntity.getFieldType());
            searchField.setMatchType(fieldEntity.getMatchType());
            if ("in".equals(fieldEntity.getMatchType())) {
                searchField.setControlType("multi_select");
            } else {
                searchField.setControlType("input");
            }
            searchField.setMultilineEnabled(0);
            searchField.setOptionValuesJson("[]");
            searchField.setSearchSort(fieldEntity.getSearchSort() == null || fieldEntity.getSearchSort() <= 0
                    ? fallbackSort
                    : fieldEntity.getSearchSort());
            searchField.setDefaultQueryDays(fieldEntity.getDefaultQueryDays() == null ? 0 : fieldEntity.getDefaultQueryDays());
            searchField.setMaxQueryDays(fieldEntity.getMaxQueryDays() == null ? 0 : fieldEntity.getMaxQueryDays());
            list.add(searchField);
            fallbackSort++;
        }
        list.sort(new Comparator<ReportSearchFieldEntity>() {
            @Override
            public int compare(ReportSearchFieldEntity a, ReportSearchFieldEntity b) {
                return Integer.compare(a.getSearchSort(), b.getSearchSort());
            }
        });
        return list;
    }

    private ReportVO toReportVO(ReportConfigEntity config,
                                List<ReportFieldEntity> fieldEntities,
                                List<ReportSearchFieldEntity> searchFieldEntities,
                                ReportDataSourceEntity dataSourceEntity) {
        ReportVO vo = new ReportVO();
        vo.setId(config.getId());
        vo.setDataSourceId(config.getDataSourceId());
        vo.setName(config.getName());
        vo.setRouterPath(config.getRouterPath());
        vo.setDataSourceName(dataSourceEntity == null ? "" : dataSourceEntity.getName());
        vo.setDataSourceType(dataSourceEntity == null ? "" : dataSourceEntity.getType());
        vo.setQueryType(StringUtils.hasText(config.getQueryType()) ? config.getQueryType() : QUERY_TYPE_PROCEDURE);
        vo.setProcedureName(config.getProcedureName());
        vo.setQuerySql(config.getQuerySql() == null ? "" : config.getQuerySql());
        vo.setCountSql(config.getCountSql() == null ? "" : config.getCountSql());
        vo.setPageSize(config.getPageSize());
        String queryUsers = normalizePermissionUsers(config.getQueryUsers());
        String exporters = normalizePermissionUsers(config.getExporters());
        boolean queryEnabled = resolveEnabled(config.getQueryEnabled(), true);
        boolean downloadEnabled = resolveEnabled(config.getDownloadEnabled(), true);
        vo.setQueryUsers(queryUsers);
        vo.setExporters(exporters);
        vo.setExportWaitMessage(config.getExportWaitMessage() == null ? "" : config.getExportWaitMessage());
        vo.setQueryEnabled(queryEnabled);
        vo.setDownloadEnabled(downloadEnabled);
        vo.setQueryPermitted(queryEnabled && userContextService.hasCurrentUserPermission(queryUsers));
        vo.setDownloadPermitted(downloadEnabled && userContextService.hasCurrentUserPermission(exporters));
        vo.setLogPermitted(userContextService.isCurrentUserAdmin());
        if (fieldEntities != null) {
            List<ReportFieldVO> fields = new ArrayList<ReportFieldVO>();
            for (ReportFieldEntity entity : fieldEntities) {
                ReportFieldVO fieldVO = new ReportFieldVO();
                fieldVO.setId(entity.getId());
                fieldVO.setSort(entity.getSortOrder());
                fieldVO.setLabel(entity.getLabel());
                fieldVO.setField(entity.getFieldName());
                fieldVO.setType(entity.getFieldType());
                fieldVO.setMatch(entity.getMatchType());
                fieldVO.setSearchable(entity.getSearchable() != null && entity.getSearchable() == 1);
                fieldVO.setSearchSort(entity.getSearchSort());
                fieldVO.setDefaultQueryDays(entity.getDefaultQueryDays() == null ? 0 : entity.getDefaultQueryDays());
                fieldVO.setMaxQueryDays(entity.getMaxQueryDays() == null ? 0 : entity.getMaxQueryDays());
                fields.add(fieldVO);
            }
            vo.setFields(fields);
        }
        if (searchFieldEntities != null) {
            List<ReportSearchFieldVO> searchFields = new ArrayList<ReportSearchFieldVO>();
            for (ReportSearchFieldEntity entity : searchFieldEntities) {
                ReportSearchFieldVO voField = new ReportSearchFieldVO();
                voField.setId(entity.getId());
                voField.setLabel(entity.getLabel());
                voField.setField(entity.getFieldName());
                voField.setType(entity.getFieldType());
                voField.setMatch(entity.getMatchType());
                voField.setControlType(StringUtils.hasText(entity.getControlType()) ? entity.getControlType() : "input");
                voField.setMultilineEnabled(entity.getMultilineEnabled() != null && entity.getMultilineEnabled() == 1);
                voField.setSearchSort(entity.getSearchSort() == null ? 0 : entity.getSearchSort());
                voField.setDefaultQueryDays(entity.getDefaultQueryDays() == null ? 0 : entity.getDefaultQueryDays());
                voField.setMaxQueryDays(entity.getMaxQueryDays() == null ? 0 : entity.getMaxQueryDays());
                voField.setOptions(parseSearchOptions(entity.getOptionValuesJson()));
                searchFields.add(voField);
            }
            searchFields.sort(new Comparator<ReportSearchFieldVO>() {
                @Override
                public int compare(ReportSearchFieldVO a, ReportSearchFieldVO b) {
                    return Integer.compare(a.getSearchSort(), b.getSearchSort());
                }
            });
            vo.setSearchFields(searchFields);
        }
        return vo;
    }

    private static List<ReportSearchOptionVO> parseSearchOptions(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<ReportSearchOptionVO> options = OBJECT_MAPPER.readValue(json, SEARCH_OPTION_LIST_TYPE);
            return options == null ? Collections.<ReportSearchOptionVO>emptyList() : options;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
