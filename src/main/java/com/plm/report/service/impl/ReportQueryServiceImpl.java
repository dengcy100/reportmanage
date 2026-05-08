package com.plm.report.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.plm.report.domain.dto.ReportFieldVO;
import com.plm.report.domain.dto.ReportQueryRequest;
import com.plm.report.domain.dto.ReportQueryResultVO;
import com.plm.report.domain.dto.ReportVO;
import com.plm.report.exception.BusinessException;
import com.plm.report.service.ReportQueryService;
import com.plm.report.service.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReportQueryServiceImpl implements ReportQueryService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATETIME_MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DATETIME_SECOND_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String SEQUENCE_FIELD = "__serial_no";
    private static final String SEQUENCE_LABEL = "序号";
    private static final int DEFAULT_QUERY_PAGE_SIZE = 20;
    private static final Set<Integer> ALLOWED_QUERY_PAGE_SIZE =
            new HashSet<Integer>(Arrays.asList(10, 20, 50, 100, 200));

    private final DataSource dataSource;
    private final ReportService reportService;

    public ReportQueryServiceImpl(DataSource dataSource, ReportService reportService) {
        this.dataSource = dataSource;
        this.reportService = reportService;
    }

    @Override
    public ReportQueryResultVO query(Long reportId, ReportQueryRequest request) {
        ReportVO report = reportService.getDetail(reportId);
        int queryPageSize = resolveQueryPageSize(request.getPageSize(), report.getPageSize());
        List<ReportFieldVO> dataColumns = sortedColumns(report);
        ProcedureCallResult callResult = callProcedure(report, dataColumns, request.getFilters(), request.getPageNo(), queryPageSize);
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
        return result;
    }

    @Override
    public void export(Long reportId, ReportQueryRequest request, HttpServletResponse response) {
        ReportVO report = reportService.getDetail(reportId);
        List<ReportFieldVO> dataColumns = sortedColumns(report);
        ProcedureCallResult callResult = callProcedure(report, dataColumns, request.getFilters(), 1, 0);
        List<ReportFieldVO> columns = prependSequenceColumn(dataColumns);
        List<Map<String, Object>> rows = prependSequenceValue(callResult.getRows(), 1L);

        String fileName = buildExportFileName(report.getName());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName);
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
        try {
            EasyExcel.write(response.getOutputStream())
                    .useDefaultStyle(false)
                    .head(heads)
                    .registerWriteHandler(new HorizontalCellStyleStrategy(commonStyle, commonStyle))
                    .sheet("report")
                    .doWrite(data);
        } catch (IOException e) {
            throw new BusinessException("导出失败: " + e.getMessage());
        }
    }

    private String buildExportFileName(String reportName) {
        String raw = reportName + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        try {
            return URLEncoder.encode(raw, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new BusinessException("导出文件名编码失败: " + e.getMessage());
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

    private ProcedureCallResult callProcedure(ReportVO report,
                                              List<ReportFieldVO> fields,
                                              Map<String, Object> filters,
                                              Integer pageNo,
                                              Integer pageSize) {
        if (!StringUtils.hasText(report.getProcedureName())) {
            throw new BusinessException("报表未配置存储过程");
        }
        Map<String, Object> preparedFilters = prepareFiltersForQuery(fields, filters);
        List<ProcedureParam> params = buildInputParams(fields, preparedFilters);
        params.add(new ProcedureParam(pageNo));
        params.add(new ProcedureParam(pageSize));
        String callSql = buildCallSql(report.getProcedureName(), params.size() + 1);

        try (Connection conn = dataSource.getConnection();
             CallableStatement statement = conn.prepareCall(callSql)) {
            int index = 1;
            for (ProcedureParam param : params) {
                statement.setObject(index++, param.getValue());
            }
            statement.registerOutParameter(index, Types.BIGINT);
            boolean hasResultSet = statement.execute();
            List<Map<String, Object>> rows = extractRows(statement, hasResultSet, fields);
            long total = statement.getLong(index);
            return new ProcedureCallResult(rows, total);
        } catch (SQLException ex) {
            throw new BusinessException("执行存储过程失败: " + ex.getMessage());
        }
    }

    private Map<String, Object> prepareFiltersForQuery(List<ReportFieldVO> fields, Map<String, Object> filters) {
        Map<String, Object> preparedFilters = new HashMap<String, Object>();
        if (filters != null) {
            preparedFilters.putAll(filters);
        }
        for (ReportFieldVO field : fields) {
            applyDateRangeRule(field, preparedFilters);
        }
        return preparedFilters;
    }

    private void applyDateRangeRule(ReportFieldVO field, Map<String, Object> filters) {
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

    private boolean isDateRangeSearchField(ReportFieldVO field) {
        return field != null
                && Boolean.TRUE.equals(field.getSearchable())
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

    private List<ProcedureParam> buildInputParams(List<ReportFieldVO> fields, Map<String, Object> filters) {
        List<ReportFieldVO> searchableFields = new ArrayList<ReportFieldVO>();
        for (ReportFieldVO field : fields) {
            if (Boolean.TRUE.equals(field.getSearchable())) {
                searchableFields.add(field);
            }
        }
        searchableFields.sort((a, b) -> Integer.compare(a.getSearchSort(), b.getSearchSort()));
        Map<String, Object> safeFilters = filters == null ? new HashMap<String, Object>() : filters;
        List<ProcedureParam> params = new ArrayList<ProcedureParam>();
        for (ReportFieldVO field : searchableFields) {
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
}
