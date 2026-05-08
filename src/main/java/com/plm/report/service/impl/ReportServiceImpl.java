package com.plm.report.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plm.report.domain.dto.PageResult;
import com.plm.report.domain.dto.ReportFieldItemRequest;
import com.plm.report.domain.dto.ReportFieldVO;
import com.plm.report.domain.dto.ReportSearchFieldItemRequest;
import com.plm.report.domain.dto.ReportSearchFieldVO;
import com.plm.report.domain.dto.ReportSearchOptionItemRequest;
import com.plm.report.domain.dto.ReportSearchOptionVO;
import com.plm.report.domain.dto.ReportUpsertRequest;
import com.plm.report.domain.dto.ReportVO;
import com.plm.report.domain.entity.ReportConfigEntity;
import com.plm.report.domain.entity.ReportDataSourceEntity;
import com.plm.report.domain.entity.ReportFieldEntity;
import com.plm.report.domain.entity.ReportSearchFieldEntity;
import com.plm.report.exception.BusinessException;
import com.plm.report.mapper.ReportConfigMapper;
import com.plm.report.mapper.ReportFieldMapper;
import com.plm.report.mapper.ReportSearchFieldMapper;
import com.plm.report.service.ReportDataSourceService;
import com.plm.report.service.ReportService;
import com.plm.report.util.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReportServiceImpl implements ReportService {

    private static final Set<Integer> ALLOWED_PAGE_SIZE = new HashSet<Integer>();
    private static final int MAX_QUERY_DAYS_LIMIT = 3650;
    private static final int MAX_EXPORT_WAIT_MESSAGE_LENGTH = 255;
    private static final String[] SQL_DANGEROUS = new String[]{
            "select", "insert", "update", "delete", "drop", "create", "alter",
            "exec", "execute", "xp_", "union", "where", "from", "join",
            "--", "/*", "*/", "'", "\"", ";"
    };
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<ReportSearchOptionVO>> SEARCH_OPTION_LIST_TYPE =
            new TypeReference<List<ReportSearchOptionVO>>() {
            };

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
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public ReportServiceImpl(ReportConfigMapper reportConfigMapper,
                             ReportFieldMapper reportFieldMapper,
                             ReportSearchFieldMapper reportSearchFieldMapper,
                             ReportDataSourceService reportDataSourceService,
                             SnowflakeIdGenerator snowflakeIdGenerator) {
        this.reportConfigMapper = reportConfigMapper;
        this.reportFieldMapper = reportFieldMapper;
        this.reportSearchFieldMapper = reportSearchFieldMapper;
        this.reportDataSourceService = reportDataSourceService;
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
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReportUpsertRequest request) {
        validateUpsertRequest(request);
        ReportConfigEntity entity = new ReportConfigEntity();
        long reportId = snowflakeIdGenerator.nextId();
        entity.setId(reportId);
        entity.setDataSourceId(request.getDataSourceId());
        entity.setName(request.getName().trim());
        entity.setProcedureName(request.getProcedureName().trim());
        entity.setPageSize(request.getPageSize());
        entity.setExporters(normalizeExporters(request.getExporters()));
        entity.setExportWaitMessage(normalizeExportWaitMessage(request.getExportWaitMessage()));
        reportConfigMapper.insert(entity);
        insertFieldSnapshot(reportId, request.getFields());
        insertSearchFieldSnapshot(reportId, request.getSearchFields());
        return reportId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ReportUpsertRequest request) {
        validateUpsertRequest(request);
        ReportConfigEntity old = getConfigOrThrow(id);
        ReportConfigEntity entity = new ReportConfigEntity();
        entity.setId(old.getId());
        entity.setDataSourceId(request.getDataSourceId());
        entity.setName(request.getName().trim());
        entity.setProcedureName(request.getProcedureName().trim());
        entity.setPageSize(request.getPageSize());
        entity.setExporters(normalizeExporters(request.getExporters()));
        entity.setExportWaitMessage(normalizeExportWaitMessage(request.getExportWaitMessage()));
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

    private String normalizeExporters(String exporters) {
        if (!StringUtils.hasText(exporters)) {
            return "";
        }
        return exporters.trim();
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

    private void validateUpsertRequest(ReportUpsertRequest request) {
        String procedure = request.getProcedureName() == null ? "" : request.getProcedureName().trim().toLowerCase();
        for (String keyword : SQL_DANGEROUS) {
            if (procedure.contains(keyword)) {
                throw new BusinessException("存储过程名称包含危险关键词或字符");
            }
        }
        if (!ALLOWED_PAGE_SIZE.contains(request.getPageSize())) {
            throw new BusinessException("每页行数仅允许 10/20/50/100/200");
        }
        if (request.getDataSourceId() == null) {
            throw new BusinessException("请选择数据源");
        }
        reportDataSourceService.getActiveMysqlDataSource(request.getDataSourceId());
        validateFields(request.getFields());
        validateSearchFields(request.getSearchFields());
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

    public static ReportVO toReportVO(ReportConfigEntity config,
                                      List<ReportFieldEntity> fieldEntities,
                                      List<ReportSearchFieldEntity> searchFieldEntities,
                                      ReportDataSourceEntity dataSourceEntity) {
        ReportVO vo = new ReportVO();
        vo.setId(config.getId());
        vo.setDataSourceId(config.getDataSourceId());
        vo.setName(config.getName());
        vo.setDataSourceName(dataSourceEntity == null ? "" : dataSourceEntity.getName());
        vo.setDataSourceType(dataSourceEntity == null ? "" : dataSourceEntity.getType());
        vo.setProcedureName(config.getProcedureName());
        vo.setPageSize(config.getPageSize());
        vo.setExporters(config.getExporters());
        vo.setExportWaitMessage(config.getExportWaitMessage() == null ? "" : config.getExportWaitMessage());
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
