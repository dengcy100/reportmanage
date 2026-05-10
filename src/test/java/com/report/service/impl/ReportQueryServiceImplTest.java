package com.report.service.impl;

import com.report.domain.dto.ReportFieldVO;
import com.report.domain.dto.ReportQueryRequest;
import com.report.domain.dto.ReportQueryResultVO;
import com.report.domain.dto.ReportSearchFieldVO;
import com.report.domain.dto.ReportVO;
import com.report.domain.entity.ReportDataSourceEntity;
import com.report.exception.AccessDeniedException;
import com.report.exception.BusinessException;
import com.report.mapper.CustomLogMapper;
import com.report.mapper.ReportExportTaskMapper;
import com.report.service.ReportDataSourceService;
import com.report.service.ReportService;
import com.report.service.UserContextService;
import com.report.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceImplTest {

    @Mock
    private ReportService reportService;

    @Mock
    private ReportDataSourceService reportDataSourceService;

    @Mock
    private ReportExportTaskMapper reportExportTaskMapper;

    @Mock
    private CustomLogMapper customLogMapper;

    @Mock
    private UserContextService userContextService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private ReportQueryServiceImpl reportQueryService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(reportQueryService, "queryTimeoutSeconds", 45);
    }

    @Test
    void query_sql_shouldExecuteCountThenPagedData() throws Exception {
        ReportVO report = buildSqlReport();
        report.setQuerySql("SELECT order_no, amount FROM demo_order_data WHERE order_no = #{order_no}");
        report.setCountSql("SELECT COUNT(1) FROM demo_order_data WHERE order_no = #{order_no}");
        when(reportService.getDetail(1L)).thenReturn(report);

        ReportDataSourceEntity ds = new ReportDataSourceEntity();
        ds.setId(9L);
        when(reportDataSourceService.getActiveMysqlDataSource(9L)).thenReturn(ds);

        Connection conn = mock(Connection.class);
        PreparedStatement countStmt = mock(PreparedStatement.class);
        PreparedStatement dataStmt = mock(PreparedStatement.class);
        ResultSet countRs = mock(ResultSet.class);
        ResultSet dataRs = mock(ResultSet.class);

        when(reportDataSourceService.openConnection(9L)).thenReturn(conn);
        when(conn.prepareStatement("SELECT COUNT(1) FROM demo_order_data WHERE order_no = ?")).thenReturn(countStmt);
        when(conn.prepareStatement("SELECT order_no, amount FROM demo_order_data WHERE order_no = ? LIMIT ?,?")).thenReturn(dataStmt);

        when(countStmt.executeQuery()).thenReturn(countRs);
        when(countRs.getMetaData()).thenReturn(null);
        when(countRs.next()).thenReturn(true, false);
        when(countRs.getLong(1)).thenReturn(3L);

        when(dataStmt.executeQuery()).thenReturn(dataRs);
        when(dataRs.next()).thenReturn(true, false);
        when(dataRs.getObject("order_no")).thenReturn("ORD-001");
        when(dataRs.getObject("amount")).thenReturn(88.5D);
        when(snowflakeIdGenerator.nextId()).thenReturn(1001L);

        ReportQueryRequest req = new ReportQueryRequest();
        req.setPageNo(2);
        req.setPageSize(20);
        Map<String, Object> filters = new HashMap<String, Object>();
        filters.put("order_no", "ORD-001");
        req.setFilters(filters);

        ReportQueryResultVO result = reportQueryService.query(1L, req);

        verify(countStmt).setObject(1, "ORD-001");
        verify(dataStmt).setObject(1, "ORD-001");
        verify(dataStmt).setInt(2, 20);
        verify(dataStmt).setInt(3, 20);
        assertEquals(Long.valueOf(3L), result.getTotal());
        assertEquals(1, result.getRows().size());
        assertEquals("ORD-001", result.getRows().get(0).get("order_no"));
        verify(customLogMapper).insert(any());
        verify(reportDataSourceService).openConnection(9L);
    }

    @Test
    void query_sql_exportModeShouldNotAppendLimit() throws Exception {
        ReportVO report = buildSqlReport();
        report.setQuerySql("SELECT order_no FROM demo_order_data WHERE order_no = #{order_no}");
        report.setCountSql("SELECT COUNT(1) FROM demo_order_data WHERE order_no = #{order_no}");
        when(reportService.getDetail(1L)).thenReturn(report);

        ReportDataSourceEntity ds = new ReportDataSourceEntity();
        ds.setId(9L);
        when(reportDataSourceService.getActiveMysqlDataSource(9L)).thenReturn(ds);

        Connection conn = mock(Connection.class);
        PreparedStatement countStmt = mock(PreparedStatement.class);
        PreparedStatement dataStmt = mock(PreparedStatement.class);
        ResultSet countRs = mock(ResultSet.class);
        ResultSet dataRs = mock(ResultSet.class);

        when(reportDataSourceService.openConnection(9L)).thenReturn(conn);
        when(conn.prepareStatement("SELECT COUNT(1) FROM demo_order_data WHERE order_no = ?")).thenReturn(countStmt);
        when(conn.prepareStatement("SELECT order_no FROM demo_order_data WHERE order_no = ?")).thenReturn(dataStmt);

        when(countStmt.executeQuery()).thenReturn(countRs);
        when(countRs.getMetaData()).thenReturn(null);
        when(countRs.next()).thenReturn(true, false);
        when(countRs.getLong(1)).thenReturn(1L);

        when(dataStmt.executeQuery()).thenReturn(dataRs);
        when(dataRs.next()).thenReturn(true, false);
        when(dataRs.getObject("order_no")).thenReturn("ORD-001");

        ReportQueryRequest req = new ReportQueryRequest();
        req.setPageNo(1);
        req.setPageSize(20);
        req.setFilters(Collections.singletonMap("order_no", "ORD-001"));

        reportQueryService.export(1L, req, new org.springframework.mock.web.MockHttpServletResponse());

        verify(conn).prepareStatement("SELECT order_no FROM demo_order_data WHERE order_no = ?");
        verify(dataStmt, never()).setInt(anyInt(), anyInt());
    }

    @Test
    void query_sql_shouldBindNullWhenParamMissing() throws Exception {
        ReportVO report = buildSqlReport();
        report.setQuerySql("SELECT order_no FROM demo_order_data WHERE order_no = #{order_no}");
        report.setCountSql("SELECT COUNT(1) FROM demo_order_data WHERE order_no = #{order_no}");
        when(reportService.getDetail(1L)).thenReturn(report);

        ReportDataSourceEntity ds = new ReportDataSourceEntity();
        ds.setId(9L);
        when(reportDataSourceService.getActiveMysqlDataSource(9L)).thenReturn(ds);

        Connection conn = mock(Connection.class);
        PreparedStatement countStmt = mock(PreparedStatement.class);
        PreparedStatement dataStmt = mock(PreparedStatement.class);
        ResultSet countRs = mock(ResultSet.class);
        ResultSet dataRs = mock(ResultSet.class);
        when(reportDataSourceService.openConnection(9L)).thenReturn(conn);
        when(conn.prepareStatement("SELECT COUNT(1) FROM demo_order_data WHERE order_no = ?")).thenReturn(countStmt);
        when(conn.prepareStatement("SELECT order_no FROM demo_order_data WHERE order_no = ? LIMIT ?,?")).thenReturn(dataStmt);

        ReportQueryRequest req = new ReportQueryRequest();
        req.setPageNo(1);
        req.setPageSize(20);
        req.setFilters(new LinkedHashMap<String, Object>());

        when(countStmt.executeQuery()).thenReturn(countRs);
        when(countRs.getMetaData()).thenReturn(null);
        when(countRs.next()).thenReturn(true, false);
        when(countRs.getLong(1)).thenReturn(0L);
        when(dataStmt.executeQuery()).thenReturn(dataRs);
        when(dataRs.next()).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(1002L);

        ReportQueryResultVO result = reportQueryService.query(1L, req);
        assertEquals(Long.valueOf(0L), result.getTotal());
        assertEquals(0, result.getRows().size());
        verify(countStmt).setObject(1, null);
        verify(dataStmt).setObject(1, null);
    }

    @Test
    void query_sql_shouldRejectWhenCountSqlNotSingleColumn() throws Exception {
        ReportVO report = buildSqlReport();
        report.setQuerySql("SELECT order_no FROM demo_order_data WHERE order_no = #{order_no}");
        report.setCountSql("SELECT COUNT(1),SUM(amount) FROM demo_order_data WHERE order_no = #{order_no}");
        when(reportService.getDetail(1L)).thenReturn(report);

        ReportDataSourceEntity ds = new ReportDataSourceEntity();
        ds.setId(9L);
        when(reportDataSourceService.getActiveMysqlDataSource(9L)).thenReturn(ds);

        Connection conn = mock(Connection.class);
        PreparedStatement countStmt = mock(PreparedStatement.class);
        ResultSet countRs = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);

        when(reportDataSourceService.openConnection(9L)).thenReturn(conn);
        when(conn.prepareStatement("SELECT COUNT(1),SUM(amount) FROM demo_order_data WHERE order_no = ?")).thenReturn(countStmt);
        when(countStmt.executeQuery()).thenReturn(countRs);
        when(countRs.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(2);
        when(snowflakeIdGenerator.nextId()).thenReturn(1003L);

        ReportQueryRequest req = new ReportQueryRequest();
        req.setPageNo(1);
        req.setPageSize(20);
        req.setFilters(Collections.singletonMap("order_no", "ORD-001"));

        BusinessException ex = assertThrows(BusinessException.class, () -> reportQueryService.query(1L, req));
        assertEquals("统计SQL必须只返回一列", ex.getMessage());
    }

    @Test
    void query_shouldRejectWhenNoQueryPermission() {
        ReportVO report = buildSqlReport();
        report.setQueryPermitted(Boolean.FALSE);
        when(reportService.getDetail(1L)).thenReturn(report);

        ReportQueryRequest req = new ReportQueryRequest();
        req.setPageNo(1);
        req.setPageSize(20);
        req.setFilters(Collections.singletonMap("order_no", "ORD-001"));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> reportQueryService.query(1L, req));
        assertEquals("当前用户无查询权限", ex.getMessage());
        verify(reportDataSourceService, never()).openConnection(9L);
    }

    @Test
    void createExportTask_shouldRejectWhenNoDownloadPermission() {
        ReportVO report = buildSqlReport();
        report.setDownloadPermitted(Boolean.FALSE);
        when(reportService.getDetail(1L)).thenReturn(report);

        ReportQueryRequest req = new ReportQueryRequest();
        req.setFilters(Collections.singletonMap("order_no", "ORD-001"));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> reportQueryService.createExportTask(1L, req));
        assertEquals("当前用户无导出权限", ex.getMessage());
    }

    @Test
    void getExportTaskStatus_shouldRejectWhenNoDownloadPermission() {
        ReportVO report = buildSqlReport();
        report.setDownloadPermitted(Boolean.FALSE);
        when(reportService.getDetail(1L)).thenReturn(report);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> reportQueryService.getExportTaskStatus(1L, 1001L));
        assertEquals("当前用户无导出权限", ex.getMessage());
    }

    @Test
    void queryLogs_shouldRejectWhenNotAdmin() {
        when(userContextService.isCurrentUserAdmin()).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> reportQueryService.queryLogs(1, 20, null, null));
        assertEquals("仅管理员可查看日志", ex.getMessage());
    }

    @Test
    void queryExportTasks_shouldRejectWhenNotAdmin() {
        when(userContextService.isCurrentUserAdmin()).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> reportQueryService.queryExportTasks(1, 20, null, null));
        assertEquals("仅管理员可查看日志", ex.getMessage());
    }

    @Test
    void clearLogs_shouldRejectWhenNotAdmin() {
        when(userContextService.isCurrentUserAdmin()).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> reportQueryService.clearLogs());
        assertEquals("仅管理员可清理日志", ex.getMessage());
    }

    @Test
    void queryLogs_shouldAllowWhenAdmin() {
        when(userContextService.isCurrentUserAdmin()).thenReturn(true);
        when(customLogMapper.pageList(null, "", 0, 20)).thenReturn(Collections.emptyList());
        when(customLogMapper.count(null, "")).thenReturn(0L);

        assertEquals(0L, reportQueryService.queryLogs(1, 20, null, null).getTotal());
        verify(customLogMapper).pageList(null, "", 0, 20);
    }

    private ReportVO buildSqlReport() {
        ReportVO report = new ReportVO();
        report.setId(1L);
        report.setName("SQL报表");
        report.setDataSourceId(9L);
        report.setQueryType("SQL");
        report.setQueryEnabled(Boolean.TRUE);
        report.setDownloadEnabled(Boolean.TRUE);
        report.setQueryPermitted(Boolean.TRUE);
        report.setDownloadPermitted(Boolean.TRUE);
        report.setLogPermitted(Boolean.FALSE);
        report.setPageSize(20);

        ReportFieldVO f1 = new ReportFieldVO();
        f1.setSort(1);
        f1.setLabel("订单号");
        f1.setField("order_no");
        f1.setType("string");
        f1.setMatch("eq");

        ReportFieldVO f2 = new ReportFieldVO();
        f2.setSort(2);
        f2.setLabel("金额");
        f2.setField("amount");
        f2.setType("number");
        f2.setMatch("eq");

        report.setFields(Arrays.asList(f1, f2));
        report.setSearchFields(Collections.singletonList(buildSearchField("order_no", "eq")));
        return report;
    }

    private ReportSearchFieldVO buildSearchField(String field, String match) {
        ReportSearchFieldVO item = new ReportSearchFieldVO();
        item.setLabel(field);
        item.setField(field);
        item.setType("string");
        item.setMatch(match);
        item.setControlType("input");
        item.setMultilineEnabled(Boolean.FALSE);
        item.setSearchSort(1);
        item.setDefaultQueryDays(0);
        item.setMaxQueryDays(0);
        return item;
    }
}
