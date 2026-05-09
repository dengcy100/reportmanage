package com.report.service.impl;

import com.report.domain.dto.ReportFieldItemRequest;
import com.report.domain.dto.ReportUpsertRequest;
import com.report.domain.entity.ReportConfigEntity;
import com.report.domain.entity.ReportDataSourceEntity;
import com.report.exception.BusinessException;
import com.report.mapper.ReportConfigMapper;
import com.report.mapper.ReportFieldMapper;
import com.report.mapper.ReportSearchFieldMapper;
import com.report.service.ReportDataSourceService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    }

    private ReportUpsertRequest buildBaseRequest() {
        ReportUpsertRequest request = new ReportUpsertRequest();
        request.setDataSourceId(9L);
        request.setName("test report");
        request.setProcedureName("usp_TestReport");
        request.setPageSize(20);
        request.setExporters("");
        request.setExportWaitMessage("");
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

    private ReportDataSourceEntity buildDataSource() {
        ReportDataSourceEntity entity = new ReportDataSourceEntity();
        entity.setId(9L);
        entity.setType("MYSQL");
        return entity;
    }
}
