package com.plm.report.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plm.report.domain.dto.ReportExportTaskCreateResponse;
import com.plm.report.domain.dto.ReportExportTaskStatusVO;
import com.plm.report.domain.dto.CustomLogVO;
import com.plm.report.domain.dto.PageResult;
import com.plm.report.domain.dto.ReportFieldVO;
import com.plm.report.domain.dto.ReportQueryRequest;
import com.plm.report.domain.dto.ReportQueryResultVO;
import com.plm.report.domain.dto.ReportSearchFieldVO;
import com.plm.report.domain.dto.ReportVO;
import com.plm.report.domain.entity.CustomLogEntity;
import com.plm.report.domain.entity.ReportExportTaskEntity;
import com.plm.report.domain.entity.ReportDataSourceEntity;
import com.plm.report.mapper.CustomLogMapper;
import com.plm.report.exception.BusinessException;
import com.plm.report.exception.TooManyRequestException;
import com.plm.report.mapper.ReportExportTaskMapper;
import com.plm.report.service.ReportDataSourceService;
import com.plm.report.service.ReportQueryService;
import com.plm.report.service.ReportService;
import com.plm.report.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ReportQueryServiceImpl implements ReportQueryService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATETIME_MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DATETIME_SECOND_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String SEQUENCE_FIELD = "__serial_no";
    private static final String SEQUENCE_LABEL = "序号";
    private static final int DEFAULT_QUERY_PAGE_SIZE = 20;
    private static final int MULTI_VALUE_LIMIT = 200;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final Set<Integer> ALLOWED_QUERY_PAGE_SIZE =
            new HashSet<Integer>(Arrays.asList(10, 20, 50, 100, 200));

    private final ReportService reportService;
    private final ReportDataSourceService reportDataSourceService;
    private final ReportExportTaskMapper reportExportTaskMapper;
    private final CustomLogMapper customLogMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, Boolean> queryLocks = new ConcurrentHashMap<String, Boolean>();
    private final ExecutorService exportExecutor = Executors.newFixedThreadPool(2);

    @Value("${report.query.timeout-seconds:45}")
    private int queryTimeoutSeconds;

    @Value("${report.export.task.poll-interval-ms:1500}")
    private int exportPollIntervalMs;

    @Value("${report.export.task.ttl-hours:24}")
    private int exportTaskTtlHours;

    @Value("${report.export.task.dir:target/export-tasks}")
    private String exportTaskDir;

    public ReportQueryServiceImpl(ReportService reportService,
                                  ReportDataSourceService reportDataSourceService,
                                  ReportExportTaskMapper reportExportTaskMapper,
                                  CustomLogMapper customLogMapper,
                                  SnowflakeIdGenerator snowflakeIdGenerator) {
        this.reportService = reportService;
        this.reportDataSourceService = reportDataSourceService;
        this.reportExportTaskMapper = reportExportTaskMapper;
        this.customLogMapper = customLogMapper;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Override
    public ReportQueryResultVO query(Long reportId, ReportQueryRequest request) {
        String lockKey = buildQueryLockKey(reportId, request);
        if (queryLocks.putIfAbsent(lockKey, Boolean.TRUE) != null) {
            throw new TooManyRequestException("查询正在执行，请勿重复点击");
        }
        ReportVO report = null;
        int queryPageSize = DEFAULT_QUERY_PAGE_SIZE;
        Map<String, Object> preparedFilters = new HashMap<String, Object>();
        try {
            report = reportService.getDetail(reportId);
            queryPageSize = resolveQueryPageSize(request.getPageSize(), report.getPageSize());
            List<ReportFieldVO> dataColumns = sortedColumns(report);
            List<ReportSearchFieldVO> searchFields = sortedSearchFields(report);
            preparedFilters = prepareFiltersForQuery(searchFields, request.getFilters());
            ProcedureCallResult callResult = callProcedure(report, dataColumns, searchFields, preparedFilters, request.getPageNo(), queryPageSize);
            List<ReportFieldVO> columns = prependSequenceColumn(dataColumns);
            List<Map<String, Object>> rows = prependSequenceValue(callResult.getRows(), calcStartSequence(request.getPageNo(), queryPageSize));

            ReportQueryResultVO result = new ReportQueryResultVO();
            result.setReportId(report.getId());
            result.setReportName(report.getName());
            result.setColumns(columns);
            result.setRows(rows);
            result.setPageNo(request.getPageNo());
            result.setPageSize(queryPageSize);
            result.setTotal(callResult.getTotal());
            writeQueryLog(reportId, null, "QUERY", preparedFilters, request.getPageNo(), queryPageSize, callResult.getTotal(), "SUCCESS", "");
            return result;
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            writeQueryLog(reportId, null, "QUERY", preparedFilters, request.getPageNo(), queryPageSize, 0L, "FAILED", message);
            throw ex;
        } finally {
            queryLocks.remove(lockKey);
        }
    }

    @Override
    public void export(Long reportId, ReportQueryRequest request, HttpServletResponse response) {
        ReportVO report = reportService.getDetail(reportId);
        List<ReportFieldVO> dataColumns = sortedColumns(report);
        List<ReportSearchFieldVO> searchFields = sortedSearchFields(report);
        Map<String, Object> preparedFilters = prepareFiltersForQuery(searchFields, request.getFilters());
        ProcedureCallResult callResult = callProcedure(report, dataColumns, searchFields, preparedFilters, 1, 0);
        List<ReportFieldVO> columns = prependSequenceColumn(dataColumns);
        List<Map<String, Object>> rows = prependSequenceValue(callResult.getRows(), 1L);

        ExcelData excelData = buildExcelData(columns, rows);
        prepareExcelResponse(response, encodeFileName(buildRawExportFileName(report.getName(), null)));
        try {
            EasyExcel.write(response.getOutputStream())
                    .useDefaultStyle(false)
                    .head(excelData.getHeads())
                    .registerWriteHandler(excelData.getStyleStrategy())
                    .sheet("report")
                    .doWrite(excelData.getData());
        } catch (IOException e) {
            throw new BusinessException("导出失败: " + e.getMessage());
        }
    }

    @Override
    public ReportExportTaskCreateResponse createExportTask(Long reportId, ReportQueryRequest request) {
        ReportVO report = reportService.getDetail(reportId);
        List<ReportSearchFieldVO> searchFields = sortedSearchFields(report);
        Map<String, Object> preparedFilters = prepareFiltersForQuery(searchFields, request.getFilters());
        String requestDigest = buildDigest(reportId, preparedFilters);
        ReportExportTaskEntity runningTask = reportExportTaskMapper.findRunningByDigest(reportId, requestDigest);
        if (runningTask != null) {
            return buildExportTaskCreateResponse(runningTask.getId(), runningTask.getStatus(), resolveExportWaitMessage(report));
        }

        long taskId = snowflakeIdGenerator.nextId();
        ReportExportTaskEntity task = new ReportExportTaskEntity();
        task.setId(taskId);
        task.setReportId(reportId);
        task.setRequestDigest(requestDigest);
        task.setStatus(STATUS_PENDING);
        task.setRequestJson(serializeJson(preparedFilters));
        task.setFileName("");
        task.setFilePath("");
        task.setErrorMessage("");
        task.setExpiresAt(LocalDateTime.now().plusHours(Math.max(1, exportTaskTtlHours)));
        reportExportTaskMapper.insert(task);

        Map<String, Object> backgroundFilters = new HashMap<String, Object>(preparedFilters);
        exportExecutor.submit(new Runnable() {
            @Override
            public void run() {
                runExportTask(taskId, reportId, backgroundFilters);
            }
        });

        return buildExportTaskCreateResponse(taskId, STATUS_PENDING, resolveExportWaitMessage(report));
    }

    @Override
    public PageResult<CustomLogVO> queryLogs(int pageNo, int pageSize, Long reportId, String status) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePageNo - 1) * safePageSize;
        String safeStatus = normalizeLogStatus(status);
        List<CustomLogEntity> entities = customLogMapper.pageList(reportId, safeStatus, offset, safePageSize);
        long total = customLogMapper.count(reportId, safeStatus);
        List<CustomLogVO> list = new ArrayList<CustomLogVO>();
        for (CustomLogEntity entity : entities) {
            CustomLogVO vo = new CustomLogVO();
            vo.setId(entity.getId());
            vo.setReportId(entity.getReportId());
            vo.setReportName(entity.getReportName() == null ? "" : entity.getReportName());
            vo.setUserId(entity.getUserId());
            vo.setActionType(entity.getActionType());
            vo.setFiltersJson(entity.getFiltersJson());
            vo.setPageNo(entity.getPageNo());
            vo.setPageSize(entity.getPageSize());
            vo.setResultTotal(entity.getResultTotal());
            vo.setStatus(entity.getStatus());
            vo.setErrorMessage(entity.getErrorMessage());
            vo.setCreatedAt(entity.getCreatedAt());
            list.add(vo);
        }
        PageResult<CustomLogVO> page = new PageResult<CustomLogVO>();
        page.setPageNo(safePageNo);
        page.setPageSize(safePageSize);
        page.setTotal(total);
        page.setList(list);
        return page;
    }

    @Override
    public void clearLogs() {
        customLogMapper.deleteAll();
    }

    private String normalizeLogStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "";
        }
        String normalized = status.trim().toUpperCase();
        if (STATUS_SUCCESS.equals(normalized) || STATUS_FAILED.equals(normalized)) {
            return normalized;
        }
        return "";
    }

    @Override
    public ReportExportTaskStatusVO getExportTaskStatus(Long reportId, Long taskId) {
        ReportExportTaskEntity task = getExportTaskOrThrow(reportId, taskId);
        if (isExpired(task)) {
            reportExportTaskMapper.markExpired(task.getId());
            task.setStatus(STATUS_EXPIRED);
        }
        ReportExportTaskStatusVO vo = new ReportExportTaskStatusVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setReady(STATUS_SUCCESS.equals(task.getStatus()));
        vo.setFileName(task.getFileName());
        if (STATUS_FAILED.equals(task.getStatus())) {
            vo.setMessage(StringUtils.hasText(task.getErrorMessage()) ? task.getErrorMessage() : "导出失败");
        } else if (STATUS_EXPIRED.equals(task.getStatus())) {
            vo.setMessage("导出文件已过期，请重新导出");
        } else if (STATUS_SUCCESS.equals(task.getStatus())) {
            vo.setMessage("导出已完成，可下载文件");
        } else {
            vo.setMessage("导出任务进行中，请耐心等待");
        }
        return vo;
    }

    @Override
    public void downloadExportTask(Long reportId, Long taskId, HttpServletResponse response) {
        ReportExportTaskEntity task = getExportTaskOrThrow(reportId, taskId);
        if (!STATUS_SUCCESS.equals(task.getStatus())) {
            if (STATUS_FAILED.equals(task.getStatus())) {
                throw new BusinessException(StringUtils.hasText(task.getErrorMessage()) ? task.getErrorMessage() : "导出任务执行失败");
            }
            throw new BusinessException("导出任务尚未完成");
        }
        if (isExpired(task)) {
            reportExportTaskMapper.markExpired(task.getId());
            throw new BusinessException("导出文件已过期，请重新导出");
        }
        if (!StringUtils.hasText(task.getFilePath())) {
            throw new BusinessException("导出文件不存在，请重新导出");
        }
        Path filePath = Paths.get(task.getFilePath()).normalize();
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new BusinessException("导出文件不存在，请重新导出");
        }
        prepareExcelResponse(response, encodeFileName(StringUtils.hasText(task.getFileName()) ? task.getFileName() : "report.xlsx"));
        try (InputStream in = Files.newInputStream(filePath)) {
            StreamUtils.copy(in, response.getOutputStream());
        } catch (IOException ex) {
            throw new BusinessException("下载导出文件失败: " + ex.getMessage());
        }
    }

    private List<ReportFieldVO> sortedColumns(ReportVO report) {
        if (report.getFields() == null) {
            return Collections.emptyList();
        }
        List<ReportFieldVO> columns = new ArrayList<ReportFieldVO>(report.getFields());
        columns.sort((a, b) -> Integer.compare(a.getSort(), b.getSort()));
        return columns;
    }

    private List<ReportSearchFieldVO> sortedSearchFields(ReportVO report) {
        if (report.getSearchFields() == null) {
            return Collections.emptyList();
        }
        List<ReportSearchFieldVO> searchFields = new ArrayList<ReportSearchFieldVO>(report.getSearchFields());
        searchFields.sort((a, b) -> Integer.compare(a.getSearchSort(), b.getSearchSort()));
        return searchFields;
    }

    private List<ReportFieldVO> prependSequenceColumn(List<ReportFieldVO> columns) {
        List<ReportFieldVO> result = new ArrayList<ReportFieldVO>();
        result.add(buildSequenceColumn());
        result.addAll(columns);
        return result;
    }

    private ReportFieldVO buildSequenceColumn() {
        ReportFieldVO sequenceColumn = new ReportFieldVO();
        sequenceColumn.setSort(0);
        sequenceColumn.setLabel(SEQUENCE_LABEL);
        sequenceColumn.setField(SEQUENCE_FIELD);
        sequenceColumn.setType("number");
        sequenceColumn.setMatch("eq");
        sequenceColumn.setSearchable(Boolean.FALSE);
        sequenceColumn.setSearchSort(0);
        sequenceColumn.setDefaultQueryDays(0);
        sequenceColumn.setMaxQueryDays(0);
        return sequenceColumn;
    }

    private List<Map<String, Object>> prependSequenceValue(List<Map<String, Object>> rows, long startSequence) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        long sequence = startSequence < 1L ? 1L : startSequence;
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> line = new LinkedHashMap<String, Object>();
            line.put(SEQUENCE_FIELD, sequence++);
            if (row != null) {
                line.putAll(row);
            }
            result.add(line);
        }
        return result;
    }

    private long calcStartSequence(Integer pageNo, Integer pageSize) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 0 : pageSize;
        if (safePageSize == 0) {
            return 1L;
        }
        return ((long) (safePageNo - 1) * safePageSize) + 1L;
    }

    private int resolveQueryPageSize(Integer requestPageSize, Integer reportPageSize) {
        int fallbackPageSize = reportPageSize == null || reportPageSize < 1 ? DEFAULT_QUERY_PAGE_SIZE : reportPageSize;
        if (requestPageSize == null) {
            return fallbackPageSize;
        }
        if (!ALLOWED_QUERY_PAGE_SIZE.contains(requestPageSize)) {
            throw new BusinessException("每页行数仅允许 10/20/50/100/200");
        }
        return requestPageSize;
    }

    private void writeQueryLog(Long reportId,
                               Long userId,
                               String actionType,
                               Map<String, Object> filters,
                               Integer pageNo,
                               Integer pageSize,
                               Long resultTotal,
                               String status,
                               String errorMessage) {
        try {
            CustomLogEntity log = new CustomLogEntity();
            log.setId(snowflakeIdGenerator.nextId());
            log.setReportId(reportId);
            log.setUserId(userId);
            log.setActionType(actionType);
            log.setFiltersJson(serializeJson(canonicalizeForDigest(filters)));
            log.setPageNo(pageNo == null ? 1 : Math.max(pageNo, 1));
            log.setPageSize(pageSize == null ? DEFAULT_QUERY_PAGE_SIZE : Math.max(pageSize, 1));
            log.setResultTotal(resultTotal == null ? 0L : resultTotal);
            log.setStatus(StringUtils.hasText(status) ? status : STATUS_SUCCESS);
            log.setErrorMessage(normalizeLogErrorMessage(errorMessage));
            customLogMapper.insert(log);
        } catch (Exception ignored) {
            // Log write failure should not block query main flow.
        }
    }

    private String normalizeLogErrorMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String text = message.trim();
        if (text.length() > 500) {
            return text.substring(0, 500);
        }
        return text;
    }

    private ProcedureCallResult callProcedure(ReportVO report,
                                              List<ReportFieldVO> fields,
                                              List<ReportSearchFieldVO> searchFields,
                                              Map<String, Object> preparedFilters,
                                              Integer pageNo,
                                              Integer pageSize) {
        if (!StringUtils.hasText(report.getProcedureName())) {
            throw new BusinessException("报表未配置存储过程");
        }
        ReportDataSourceEntity dataSource = reportDataSourceService.getActiveMysqlDataSource(report.getDataSourceId());
        List<ProcedureParam> params = buildInputParams(searchFields, preparedFilters);
        params.add(new ProcedureParam(pageNo));
        params.add(new ProcedureParam(pageSize));
        String callSql = buildCallSql(report.getProcedureName(), params.size() + 1);

        try (Connection conn = reportDataSourceService.openConnection(dataSource.getId());
             CallableStatement statement = conn.prepareCall(callSql)) {
            if (queryTimeoutSeconds > 0) {
                statement.setQueryTimeout(queryTimeoutSeconds);
            }
            int index = 1;
            for (ProcedureParam param : params) {
                statement.setObject(index++, param.getValue());
            }
            statement.registerOutParameter(index, Types.BIGINT);
            boolean hasResultSet = statement.execute();
            List<Map<String, Object>> rows = extractRows(statement, hasResultSet, fields);
            long total = statement.getLong(index);
            return new ProcedureCallResult(rows, total);
        } catch (SQLTimeoutException ex) {
            throw new BusinessException("查询超时，请缩小范围后重试");
        } catch (SQLException ex) {
            throw new BusinessException("执行存储过程失败: " + ex.getMessage());
        }
    }

    private Map<String, Object> prepareFiltersForQuery(List<ReportSearchFieldVO> searchFields, Map<String, Object> filters) {
        Map<String, Object> preparedFilters = new HashMap<String, Object>();
        if (filters != null) {
            preparedFilters.putAll(filters);
        }
        for (ReportSearchFieldVO field : searchFields) {
            normalizeSearchValue(field, preparedFilters);
            applyDateRangeRule(field, preparedFilters);
        }
        return preparedFilters;
    }

    private void normalizeSearchValue(ReportSearchFieldVO field, Map<String, Object> filters) {
        if (field == null || !StringUtils.hasText(field.getField())) {
            return;
        }
        if ("range".equals(field.getMatch())) {
            String startKey = field.getField() + "_start";
            String endKey = field.getField() + "_end";
            normalizeSingleFilter(filters, startKey);
            normalizeSingleFilter(filters, endKey);
            return;
        }
        String key = field.getField();
        Object rawValue = filters.get(key);
        String normalized;
        if ("multi_select".equals(field.getControlType())) {
            normalized = normalizeMultiValue(rawValue);
        } else if ("single_select".equals(field.getControlType())) {
            normalized = normalizeText(rawValue);
        } else if (Boolean.TRUE.equals(field.getMultilineEnabled())) {
            normalized = normalizeMultiValue(rawValue);
        } else {
            normalized = normalizeText(rawValue);
            if (normalized.contains(";") || normalized.contains("\n") || normalized.contains("\r")) {
                normalized = normalizeMultiValue(normalized);
            }
        }
        if (StringUtils.hasText(normalized)) {
            filters.put(key, normalized);
        } else {
            filters.remove(key);
        }
    }

    private void normalizeSingleFilter(Map<String, Object> filters, String key) {
        String normalized = normalizeText(filters.get(key));
        if (StringUtils.hasText(normalized)) {
            filters.put(key, normalized);
        } else {
            filters.remove(key);
        }
    }

    private String normalizeMultiValue(Object raw) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        if (raw instanceof Collection) {
            for (Object item : (Collection<?>) raw) {
                addSplitValues(values, item == null ? "" : String.valueOf(item));
            }
        } else {
            addSplitValues(values, raw == null ? "" : String.valueOf(raw));
        }
        if (values.size() > MULTI_VALUE_LIMIT) {
            throw new BusinessException("批量输入最多支持 " + MULTI_VALUE_LIMIT + " 个值");
        }
        if (values.isEmpty()) {
            return "";
        }
        return String.join(";", values);
    }

    private void addSplitValues(Set<String> values, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        String[] parts = text.split("[\\r\\n;]+");
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (StringUtils.hasText(trimmed)) {
                values.add(trimmed);
            }
        }
    }

    private void applyDateRangeRule(ReportSearchFieldVO field, Map<String, Object> filters) {
        if (!isDateRangeSearchField(field)) {
            return;
        }
        int defaultQueryDays = normalizeQueryDays(field.getDefaultQueryDays());
        int maxQueryDays = normalizeQueryDays(field.getMaxQueryDays());
        String startKey = field.getField() + "_start";
        String endKey = field.getField() + "_end";
        String startValue = normalizeText(filters.get(startKey));
        String endValue = normalizeText(filters.get(endKey));
        if (!StringUtils.hasText(startValue) && !StringUtils.hasText(endValue) && defaultQueryDays > 0) {
            DateRange defaultRange = buildDefaultDateRange(field.getType(), defaultQueryDays);
            startValue = defaultRange.getStart();
            endValue = defaultRange.getEnd();
            filters.put(startKey, startValue);
            filters.put(endKey, endValue);
        }
        if (!StringUtils.hasText(startValue) || !StringUtils.hasText(endValue)) {
            return;
        }
        long rangeDays = calculateRangeDays(field.getLabel(), field.getType(), startValue, endValue);
        if (maxQueryDays > 0 && rangeDays > maxQueryDays) {
            throw new BusinessException("字段[" + field.getLabel() + "]查询范围最多" + maxQueryDays + "天");
        }
    }

    private boolean isDateRangeSearchField(ReportSearchFieldVO field) {
        return field != null
                && "range".equals(field.getMatch())
                && ("date".equals(field.getType()) || "datetime".equals(field.getType()));
    }

    private int normalizeQueryDays(Integer value) {
        if (value == null || value < 0) {
            return 0;
        }
        return value;
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "" : text;
    }

    private DateRange buildDefaultDateRange(String fieldType, int queryDays) {
        if ("date".equals(fieldType)) {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(Math.max(0, queryDays - 1));
            return new DateRange(DATE_FORMATTER.format(start), DATE_FORMATTER.format(end));
        }
        LocalDateTime end = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime start = end.minusDays(queryDays);
        return new DateRange(DATETIME_MINUTE_FORMATTER.format(start), DATETIME_MINUTE_FORMATTER.format(end));
    }

    private long calculateRangeDays(String fieldLabel, String fieldType, String start, String end) {
        String safeLabel = StringUtils.hasText(fieldLabel) ? fieldLabel : "日期字段";
        if ("date".equals(fieldType)) {
            LocalDate startDate = parseLocalDate(start, safeLabel + "开始");
            LocalDate endDate = parseLocalDate(end, safeLabel + "结束");
            if (endDate.isBefore(startDate)) {
                throw new BusinessException("字段[" + safeLabel + "]开始时间不能大于结束时间");
            }
            return ChronoUnit.DAYS.between(startDate, endDate) + 1;
        }
        LocalDateTime startTime = parseLocalDateTime(start, safeLabel + "开始");
        LocalDateTime endTime = parseLocalDateTime(end, safeLabel + "结束");
        if (endTime.isBefore(startTime)) {
            throw new BusinessException("字段[" + safeLabel + "]开始时间不能大于结束时间");
        }
        long seconds = ChronoUnit.SECONDS.between(startTime, endTime);
        long daySeconds = 24L * 60L * 60L;
        long days = seconds / daySeconds;
        if (seconds % daySeconds != 0) {
            days++;
        }
        return Math.max(1L, days);
    }

    private LocalDate parseLocalDate(String raw, String desc) {
        try {
            return LocalDate.parse(raw, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(desc + "格式不正确，应为yyyy-MM-dd");
        }
    }

    private LocalDateTime parseLocalDateTime(String raw, String desc) {
        String text = raw == null ? "" : raw.trim().replace(" ", "T");
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(desc + "不能为空");
        }
        try {
            if (text.length() == 16) {
                return LocalDateTime.parse(text, DATETIME_MINUTE_FORMATTER);
            }
            if (text.length() == 19) {
                return LocalDateTime.parse(text, DATETIME_SECOND_FORMATTER);
            }
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(desc + "格式不正确，应为yyyy-MM-ddTHH:mm");
        }
    }

    private List<ProcedureParam> buildInputParams(List<ReportSearchFieldVO> searchFields, Map<String, Object> filters) {
        Map<String, Object> safeFilters = filters == null ? new HashMap<String, Object>() : filters;
        List<ProcedureParam> params = new ArrayList<ProcedureParam>();
        for (ReportSearchFieldVO field : searchFields) {
            String name = field.getField();
            if ("range".equals(field.getMatch())) {
                params.add(new ProcedureParam(normalizeValue(safeFilters.get(name + "_start"))));
                params.add(new ProcedureParam(normalizeValue(safeFilters.get(name + "_end"))));
            } else {
                params.add(new ProcedureParam(normalizeValue(safeFilters.get(name))));
            }
        }
        return params;
    }

    private Object normalizeValue(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof String) {
            String s = ((String) val).trim();
            return s.isEmpty() ? null : s;
        }
        return val;
    }

    private String buildQueryLockKey(Long reportId, ReportQueryRequest request) {
        Map<String, Object> keyData = new LinkedHashMap<String, Object>();
        keyData.put("reportId", reportId);
        keyData.put("pageNo", request.getPageNo());
        keyData.put("pageSize", request.getPageSize());
        keyData.put("filters", canonicalizeForDigest(request.getFilters()));
        return serializeJson(keyData);
    }

    private String buildDigest(Long reportId, Map<String, Object> filters) {
        String payload = reportId + "|" + serializeJson(canonicalizeForDigest(filters));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new BusinessException("生成导出任务摘要失败: " + ex.getMessage());
        }
    }

    private Object canonicalizeForDigest(Object source) {
        if (source == null) {
            return null;
        }
        if (source instanceof Map) {
            Map<?, ?> sourceMap = (Map<?, ?>) source;
            Map<String, Object> sorted = new TreeMap<String, Object>();
            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), canonicalizeForDigest(entry.getValue()));
            }
            return sorted;
        }
        if (source instanceof Collection) {
            List<Object> list = new ArrayList<Object>();
            for (Object item : (Collection<?>) source) {
                list.add(canonicalizeForDigest(item));
            }
            return list;
        }
        return source;
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyMap() : value);
        } catch (Exception ex) {
            throw new BusinessException("序列化请求参数失败: " + ex.getMessage());
        }
    }

    private void runExportTask(Long taskId, Long reportId, Map<String, Object> filters) {
        reportExportTaskMapper.updateStatus(taskId, STATUS_RUNNING, "");
        try {
            ReportVO report = reportService.getDetail(reportId);
            List<ReportFieldVO> dataColumns = sortedColumns(report);
            List<ReportSearchFieldVO> searchFields = sortedSearchFields(report);
            ProcedureCallResult callResult = callProcedure(report, dataColumns, searchFields, filters, 1, 0);
            List<ReportFieldVO> columns = prependSequenceColumn(dataColumns);
            List<Map<String, Object>> rows = prependSequenceValue(callResult.getRows(), 1L);
            ExcelData excelData = buildExcelData(columns, rows);

            Path exportDirectory = Paths.get(exportTaskDir).toAbsolutePath().normalize();
            Files.createDirectories(exportDirectory);
            String rawFileName = buildRawExportFileName(report.getName(), taskId);
            Path filePath = exportDirectory.resolve(rawFileName);
            EasyExcel.write(filePath.toFile())
                    .useDefaultStyle(false)
                    .head(excelData.getHeads())
                    .registerWriteHandler(excelData.getStyleStrategy())
                    .sheet("report")
                    .doWrite(excelData.getData());

            reportExportTaskMapper.markSuccess(taskId, rawFileName, filePath.toString(),
                    LocalDateTime.now().plusHours(Math.max(1, exportTaskTtlHours)));
        } catch (Exception ex) {
            reportExportTaskMapper.markFailed(taskId, trimErrorMessage(ex.getMessage()));
        }
    }

    private ReportExportTaskEntity getExportTaskOrThrow(Long reportId, Long taskId) {
        ReportExportTaskEntity task = reportExportTaskMapper.findById(taskId, reportId);
        if (task == null) {
            throw new BusinessException("导出任务不存在");
        }
        return task;
    }

    private boolean isExpired(ReportExportTaskEntity task) {
        return task != null && task.getExpiresAt() != null && task.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private ReportExportTaskCreateResponse buildExportTaskCreateResponse(Long taskId, String status, String message) {
        ReportExportTaskCreateResponse response = new ReportExportTaskCreateResponse();
        response.setTaskId(taskId);
        response.setStatus(status);
        response.setMessage(message);
        response.setPollIntervalMs(Math.max(exportPollIntervalMs, 500));
        return response;
    }

    private String resolveExportWaitMessage(ReportVO report) {
        if (report != null && StringUtils.hasText(report.getExportWaitMessage())) {
            return report.getExportWaitMessage();
        }
        return "";
    }

    private String trimErrorMessage(String message) {
        String text = StringUtils.hasText(message) ? message.trim() : "导出失败";
        if (text.length() > 500) {
            return text.substring(0, 500);
        }
        return text;
    }

    private ExcelData buildExcelData(List<ReportFieldVO> columns, List<Map<String, Object>> rows) {
        List<List<String>> heads = new ArrayList<List<String>>();
        for (ReportFieldVO col : columns) {
            heads.add(Collections.singletonList(col.getLabel()));
        }
        List<List<Object>> data = new ArrayList<List<Object>>();
        for (Map<String, Object> row : rows) {
            List<Object> line = new ArrayList<Object>();
            for (ReportFieldVO col : columns) {
                line.add(row.get(col.getField()));
            }
            data.add(line);
        }
        WriteCellStyle commonStyle = new WriteCellStyle();
        commonStyle.setWrapped(Boolean.FALSE);
        WriteFont commonFont = new WriteFont();
        commonFont.setBold(Boolean.FALSE);
        commonStyle.setWriteFont(commonFont);
        HorizontalCellStyleStrategy styleStrategy = new HorizontalCellStyleStrategy(commonStyle, commonStyle);
        return new ExcelData(heads, data, styleStrategy);
    }

    private void prepareExcelResponse(HttpServletResponse response, String encodedFileName) {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encodedFileName);
    }

    private String buildRawExportFileName(String reportName, Long taskId) {
        String safeReportName = StringUtils.hasText(reportName) ? reportName.trim() : "report";
        safeReportName = safeReportName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        if (taskId != null) {
            return safeReportName + "_" + timestamp + "_" + taskId + ".xlsx";
        }
        return safeReportName + "_" + timestamp + ".xlsx";
    }

    private String encodeFileName(String raw) {
        try {
            return URLEncoder.encode(raw, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new BusinessException("导出文件名编码失败: " + e.getMessage());
        }
    }

    private String buildCallSql(String procedureName, int parameterCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("{call ").append(procedureName).append("(");
        for (int i = 0; i < parameterCount; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("?");
        }
        sb.append(")}");
        return sb.toString();
    }

    private List<Map<String, Object>> extractRows(CallableStatement statement,
                                                  boolean hasResultSet,
                                                  List<ReportFieldVO> fields) throws SQLException {
        ResultSet resultSet = null;
        if (hasResultSet) {
            resultSet = statement.getResultSet();
        } else {
            while (statement.getMoreResults() || statement.getUpdateCount() != -1) {
                ResultSet rs = statement.getResultSet();
                if (rs != null) {
                    resultSet = rs;
                    break;
                }
            }
        }
        if (resultSet == null) {
            return Collections.emptyList();
        }
        try (ResultSet rs = resultSet) {
            List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                for (ReportFieldVO field : fields) {
                    Object value = readValue(rs, field.getField());
                    row.put(field.getField(), value);
                }
                list.add(row);
            }
            return list;
        }
    }

    private Object readValue(ResultSet rs, String columnLabel) throws SQLException {
        Object raw = rs.getObject(columnLabel);
        if (raw == null) {
            return "";
        }
        if (raw instanceof java.sql.Timestamp) {
            return DATETIME_FORMATTER.format(((java.sql.Timestamp) raw).toLocalDateTime());
        }
        if (raw instanceof java.sql.Date) {
            return raw.toString();
        }
        return raw;
    }

    private static class ProcedureParam {
        private final Object value;

        private ProcedureParam(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }
    }

    private static class DateRange {
        private final String start;
        private final String end;

        private DateRange(String start, String end) {
            this.start = start;
            this.end = end;
        }

        public String getStart() {
            return start;
        }

        public String getEnd() {
            return end;
        }
    }

    private static class ProcedureCallResult {
        private final List<Map<String, Object>> rows;
        private final long total;

        private ProcedureCallResult(List<Map<String, Object>> rows, long total) {
            this.rows = rows;
            this.total = total;
        }

        public List<Map<String, Object>> getRows() {
            return rows;
        }

        public long getTotal() {
            return total;
        }
    }

    private static class ExcelData {
        private final List<List<String>> heads;
        private final List<List<Object>> data;
        private final HorizontalCellStyleStrategy styleStrategy;

        private ExcelData(List<List<String>> heads, List<List<Object>> data, HorizontalCellStyleStrategy styleStrategy) {
            this.heads = heads;
            this.data = data;
            this.styleStrategy = styleStrategy;
        }

        public List<List<String>> getHeads() {
            return heads;
        }

        public List<List<Object>> getData() {
            return data;
        }

        public HorizontalCellStyleStrategy getStyleStrategy() {
            return styleStrategy;
        }
    }
}
