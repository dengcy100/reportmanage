package com.report.service.impl;

import com.report.domain.dto.ReportFieldItemRequest;
import com.report.domain.dto.ReportSearchFieldItemRequest;
import com.report.domain.dto.ReportUpsertRequest;
import com.report.domain.dto.ReportVO;
import com.report.domain.entity.ReportConfigEntity;
import com.report.domain.entity.ReportDataSourceEntity;
import com.report.exception.BusinessException;
import com.report.mapper.ReportConfigMapper;
import com.report.mapper.ReportFieldMapper;
import com.report.mapper.ReportSearchFieldMapper;
import com.report.service.ReportDataSourceService;
import com.report.service.UserContextService;
import com.report.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportConfigMapper reportConfigMapper;

    @Mock
    private ReportFieldMapper reportFieldMapper;

    @Mock
    private ReportSearchFieldMapper reportSearchFieldMapper;

    @Mock
    private ReportDataSourceService reportDataSourceService;

    @Mock
    private UserContextService userContextService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private ReportServiceImpl reportService;

    @Captor
    private ArgumentCaptor<ReportConfigEntity> configCaptor;

    @Test
    void create_shouldRejectWhenQueryAndDownloadAreBothDisabled() {
        ReportUpsertRequest request = buildBaseRequest();
        request.setQueryEnabled(Boolean.FALSE);
        request.setDownloadEnabled(Boolean.FALSE);

        assertThrows(BusinessException.class, () -> reportService.create(request));
    }

    @Test
    void create_shouldDefaultCapabilitiesToEnabledWhenMissing() {
        ReportUpsertRequest request = buildBaseRequest();
        request.setQueryEnabled(null);
        request.setDownloadEnabled(null);
        when(snowflakeIdGenerator.nextId()).thenReturn(1001L, 2001L);
        when(reportDataSourceService.getActiveMysqlDataSource(9L)).thenReturn(buildDataSource());

        reportService.create(request);

        verify(reportConfigMapper).insert(configCaptor.capture());
        ReportConfigEntity entity = configCaptor.getValue();
        assertEquals(Integer.valueOf(1), entity.getQueryEnabled());
        assertEquals(Integer.valueOf(1), entity.getDownloadEnabled());
        assertEquals("PROCEDURE", entity.getQueryType());
    }

    @Test
    void create_shouldRejectSqlWhenContainsLimit() {
        ReportUpsertRequest request = buildBaseRequest();
        request.setQueryType("SQL");
        request.setProcedureName("");
        request.setQuerySql("SELECT order_no FROM demo_order_data WHERE order_no = #{order_no} LIMIT 1");
        request.setCountSql("SELECT COUNT(1) FROM demo_order_data WHERE order_no = #{order_no}");
        request.setSearchFields(Arrays.asList(buildSearchField("order_no", "eq")));

        assertThrows(BusinessException.class, () -> reportService.create(request));
    }

    @Test
    void create_shouldRejectSqlWhenPlaceholderMissing() {
        ReportUpsertRequest request = buildBaseRequest();
        request.setQueryType("SQL");
        request.setProcedureName("");
        request.setQuerySql("SELECT order_no FROM demo_order_data WHERE order_no = #{missing_field}");
        request.setCountSql("SELECT COUNT(1) FROM demo_order_data WHERE order_no = #{missing_field}");
        request.setSearchFields(Arrays.asList(buildSearchField("order_no", "eq")));

        assertThrows(BusinessException.class, () -> reportService.create(request));
    }

    @Test
    void create_shouldAcceptSqlConfig() {
        ReportUpsertRequest request = buildBaseRequest();
        request.setQueryType("SQL");
        request.setProcedureName("");
        request.setQuerySql("SELECT order_no,customer_name FROM demo_order_data WHERE order_no = #{order_no}");
        request.setCountSql("SELECT COUNT(1) FROM demo_order_data WHERE order_no = #{order_no}");
        request.setSearchFields(Arrays.asList(buildSearchField("order_no", "eq")));
        when(snowflakeIdGenerator.nextId()).thenReturn(1001L, 2001L, 3001L);
        when(reportDataSourceService.getActiveMysqlDataSource(9L)).thenReturn(buildDataSource());

        reportService.create(request);

        verify(reportConfigMapper).insert(configCaptor.capture());
        ReportConfigEntity entity = configCaptor.getValue();
        assertEquals("SQL", entity.getQueryType());
        assertNull(entity.getProcedureName());
        assertEquals("SELECT order_no,customer_name FROM demo_order_data WHERE order_no = #{order_no}", entity.getQuerySql());
        assertEquals("SELECT COUNT(1) FROM demo_order_data WHERE order_no = #{order_no}", entity.getCountSql());
    }

    @Test
    void create_shouldNormalizePermissionUsers() {
        ReportUpsertRequest request = buildBaseRequest();
        request.setQueryUsers(" admin, zhangshan ,admin ");
        request.setExporters(" * ");
        when(snowflakeIdGenerator.nextId()).thenReturn(1001L, 2001L);
        when(reportDataSourceService.getActiveMysqlDataSource(9L)).thenReturn(buildDataSource());

        reportService.create(request);

        verify(reportConfigMapper).insert(configCaptor.capture());
        ReportConfigEntity entity = configCaptor.getValue();
        assertEquals("admin,zhangshan", entity.getQueryUsers());
        assertEquals("*", entity.getExporters());
    }

    @Test
    void getDetail_shouldPopulatePermissionFlags() {
        ReportConfigEntity config = new ReportConfigEntity();
        config.setId(1001L);
        config.setDataSourceId(9L);
        config.setName("权限测试报表");
        config.setQueryType("PROCEDURE");
        config.setProcedureName("usp_TestReport");
        config.setPageSize(20);
        config.setQueryUsers("admin,zhangshan");
        config.setExporters("*");
        config.setQueryEnabled(1);
        config.setDownloadEnabled(1);
        config.setExportWaitMessage("");
        when(reportConfigMapper.findById(1001L)).thenReturn(config);
        when(reportFieldMapper.findByReportId(1001L)).thenReturn(Collections.emptyList());
        when(reportSearchFieldMapper.findByReportId(1001L)).thenReturn(Collections.emptyList());
        when(reportDataSourceService.getActiveMysqlDataSource(9L)).thenReturn(buildDataSource());
        when(userContextService.hasCurrentUserPermission(anyString())).thenReturn(true);
        when(userContextService.isCurrentUserAdmin()).thenReturn(true);

        ReportVO detail = reportService.getDetail(1001L);

        assertEquals("admin,zhangshan", detail.getQueryUsers());
        assertEquals("*", detail.getExporters());
        assertTrue(Boolean.TRUE.equals(detail.getQueryPermitted()));
        assertTrue(Boolean.TRUE.equals(detail.getDownloadPermitted()));
        assertTrue(Boolean.TRUE.equals(detail.getLogPermitted()));
    }

    private ReportUpsertRequest buildBaseRequest() {
        ReportUpsertRequest request = new ReportUpsertRequest();
        request.setDataSourceId(9L);
        request.setName("test report");
        request.setQueryType("PROCEDURE");
        request.setProcedureName("usp_TestReport");
        request.setPageSize(20);
        request.setQueryUsers("");
        request.setExporters("");
        request.setExportWaitMessage("");
        request.setQuerySql("");
        request.setCountSql("");
        request.setFields(Arrays.asList(buildField(1, "订单号", "order_no")));
        request.setSearchFields(Collections.emptyList());
        return request;
    }

    private ReportFieldItemRequest buildField(int sort, String label, String field) {
        ReportFieldItemRequest item = new ReportFieldItemRequest();
        item.setSort(sort);
        item.setLabel(label);
        item.setField(field);
        item.setType("string");
        item.setMatch("like");
        return item;
    }

    private ReportSearchFieldItemRequest buildSearchField(String field, String match) {
        ReportSearchFieldItemRequest item = new ReportSearchFieldItemRequest();
        item.setLabel(field);
        item.setField(field);
        item.setType("string");
        item.setMatch(match);
        item.setControlType("input");
        item.setMultilineEnabled(false);
        item.setSearchSort(1);
        item.setDefaultQueryDays(0);
        item.setMaxQueryDays(0);
        return item;
    }

    private ReportDataSourceEntity buildDataSource() {
        ReportDataSourceEntity entity = new ReportDataSourceEntity();
        entity.setId(9L);
        entity.setType("MYSQL");
        return entity;
    }
}
