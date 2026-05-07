package com.plm.report.service.impl;

import com.alibaba.excel.EasyExcel;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportQueryServiceImpl implements ReportQueryService {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DataSource dataSource;
    private final ReportService reportService;

    public ReportQueryServiceImpl(DataSource dataSource, ReportService reportService) {
        this.dataSource = dataSource;
        this.reportService = reportService;
    }

    @Override
    public ReportQueryResultVO query(Long reportId, ReportQueryRequest request) {
        ReportVO report = reportService.getDetail(reportId);
        List<ReportFieldVO> columns = sortedColumns(report);
        ProcedureCallResult callResult = callProcedure(report, columns, request.getFilters(), request.getPageNo(), report.getPageSize());

        ReportQueryResultVO result = new ReportQueryResultVO();
        result.setReportId(report.getId());
        result.setReportName(report.getName());
        result.setColumns(columns);
        result.setRows(callResult.getRows());
        result.setPageNo(request.getPageNo());
        result.setPageSize(report.getPageSize());
        result.setTotal(callResult.getTotal());
        return result;
    }

    @Override
    public void export(Long reportId, ReportQueryRequest request, HttpServletResponse response) {
        ReportVO report = reportService.getDetail(reportId);
        List<ReportFieldVO> columns = sortedColumns(report);
        ProcedureCallResult callResult = callProcedure(report, columns, request.getFilters(), 1, 0);

        String fileName = buildExportFileName(report.getName());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName);
        List<List<String>> heads = new ArrayList<List<String>>();
        for (ReportFieldVO col : columns) {
            heads.add(Collections.singletonList(col.getLabel()));
        }
        List<List<Object>> data = new ArrayList<List<Object>>();
        for (Map<String, Object> row : callResult.getRows()) {
            List<Object> line = new ArrayList<Object>();
            for (ReportFieldVO col : columns) {
                line.add(row.get(col.getField()));
            }
            data.add(line);
        }
        try {
            EasyExcel.write(response.getOutputStream())
                    .head(heads)
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

    private ProcedureCallResult callProcedure(ReportVO report,
                                              List<ReportFieldVO> fields,
                                              Map<String, Object> filters,
                                              Integer pageNo,
                                              Integer pageSize) {
        if (!StringUtils.hasText(report.getProcedureName())) {
            throw new BusinessException("报表未配置存储过程");
        }
        List<ProcedureParam> params = buildInputParams(fields, filters);
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
