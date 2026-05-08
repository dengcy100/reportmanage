package com.plm.report.service.impl;

import com.plm.report.domain.dto.PageResult;
import com.plm.report.domain.dto.ReportFieldItemRequest;
import com.plm.report.domain.dto.ReportFieldVO;
import com.plm.report.domain.dto.ReportUpsertRequest;
import com.plm.report.domain.dto.ReportVO;
import com.plm.report.domain.entity.ReportConfigEntity;
import com.plm.report.domain.entity.ReportFieldEntity;
import com.plm.report.exception.BusinessException;
import com.plm.report.mapper.ReportConfigMapper;
import com.plm.report.mapper.ReportFieldMapper;
import com.plm.report.service.ReportService;
import com.plm.report.util.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReportServiceImpl implements ReportService {

    private static final Set<Integer> ALLOWED_PAGE_SIZE = new HashSet<Integer>();
    private static final int MAX_QUERY_DAYS_LIMIT = 3650;
    private static final String[] SQL_DANGEROUS = new String[]{
            "select", "insert", "update", "delete", "drop", "create", "alter",
            "exec", "execute", "xp_", "union", "where", "from", "join",
            "--", "/*", "*/", "'", "\"", ";"
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
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public ReportServiceImpl(ReportConfigMapper reportConfigMapper,
                             ReportFieldMapper reportFieldMapper,
                             SnowflakeIdGenerator snowflakeIdGenerator) {
        this.reportConfigMapper = reportConfigMapper;
        this.reportFieldMapper = reportFieldMapper;
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
        for (ReportConfigEntity entity : entities) {
            list.add(toReportVO(entity, null));
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
        return toReportVO(config, fields);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReportUpsertRequest request) {
        validateUpsertRequest(request);
        ReportConfigEntity entity = new ReportConfigEntity();
        long reportId = snowflakeIdGenerator.nextId();
        entity.setId(reportId);
        entity.setName(request.getName().trim());
        entity.setProcedureName(request.getProcedureName().trim());
        entity.setPageSize(request.getPageSize());
        entity.setExporters(normalizeExporters(request.getExporters()));
        reportConfigMapper.insert(entity);
        insertFieldSnapshot(reportId, request.getFields());
        return reportId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ReportUpsertRequest request) {
        validateUpsertRequest(request);
        ReportConfigEntity old = getConfigOrThrow(id);
        ReportConfigEntity entity = new ReportConfigEntity();
        entity.setId(old.getId());
        entity.setName(request.getName().trim());
        entity.setProcedureName(request.getProcedureName().trim());
        entity.setPageSize(request.getPageSize());
        entity.setExporters(normalizeExporters(request.getExporters()));
        int rows = reportConfigMapper.updateById(entity);
        if (rows == 0) {
            throw new BusinessException("报表不存在或已删除");
        }
        reportFieldMapper.logicDeleteByReportId(id);
        insertFieldSnapshot(id, request.getFields());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getConfigOrThrow(id);
        reportFieldMapper.logicDeleteByReportId(id);
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
        validateFields(request.getFields());
    }

    private void validateFields(List<ReportFieldItemRequest> fields) {
        Set<String> fieldNameSet = new HashSet<String>();
        Set<Integer> searchSortSet = new HashSet<Integer>();
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
            if (Boolean.TRUE.equals(field.getSearchable())) {
                if (field.getSearchSort() == null || field.getSearchSort() <= 0) {
                    throw new BusinessException("搜索字段的搜索排序必须大于0");
                }
                if (!searchSortSet.add(field.getSearchSort())) {
                    throw new BusinessException("搜索排序重复: " + field.getSearchSort());
                }
            } else if (field.getSearchSort() != null && field.getSearchSort() != 0) {
                throw new BusinessException("非搜索字段的搜索排序必须为0");
            }
            validateDateQueryDays(field);
        }
    }

    private void validateDateQueryDays(ReportFieldItemRequest field) {
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

    private boolean isDateRangeSearchField(ReportFieldItemRequest field) {
        return Boolean.TRUE.equals(field.getSearchable())
                && "range".equals(field.getMatch())
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
            field.setSearchable(Boolean.TRUE.equals(req.getSearchable()) ? 1 : 0);
            field.setSearchSort(Boolean.TRUE.equals(req.getSearchable()) ? req.getSearchSort() : 0);
            boolean dateRangeSearchField = isDateRangeSearchField(req);
            field.setDefaultQueryDays(dateRangeSearchField ? normalizeQueryDays(req.getDefaultQueryDays()) : 0);
            field.setMaxQueryDays(dateRangeSearchField ? normalizeQueryDays(req.getMaxQueryDays()) : 0);
            field.setSortOrder(req.getSort());
            reportFieldMapper.insert(field);
        }
    }

    public static ReportVO toReportVO(ReportConfigEntity config, List<ReportFieldEntity> fieldEntities) {
        ReportVO vo = new ReportVO();
        vo.setId(config.getId());
        vo.setName(config.getName());
        vo.setProcedureName(config.getProcedureName());
        vo.setPageSize(config.getPageSize());
        vo.setExporters(config.getExporters());
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
        return vo;
    }
}
