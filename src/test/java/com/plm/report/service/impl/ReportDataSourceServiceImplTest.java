package com.plm.report.service.impl;

import com.plm.report.domain.entity.ReportDataSourceEntity;
import com.plm.report.mapper.ReportConfigMapper;
import com.plm.report.mapper.ReportDataSourceMapper;
import com.plm.report.util.DataSourcePasswordCipher;
import com.plm.report.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportDataSourceServiceImplTest {

    @Mock
    private ReportDataSourceMapper reportDataSourceMapper;

    @Mock
    private ReportConfigMapper reportConfigMapper;

    @Mock
    private DataSourcePasswordCipher passwordCipher;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private ReportDataSourceServiceImpl reportDataSourceService;

    @Captor
    private ArgumentCaptor<ReportDataSourceEntity> entityCaptor;

    @Test
    void ensureSystemDefaultDataSource_shouldRefreshExistingRecordFromSpringDatasourceConfig() {
        ReflectionTestUtils.setField(reportDataSourceService, "springDatasourceUrl",
                "jdbc:mysql://192.168.1.10:3306/report_db?useSSL=false");
        ReflectionTestUtils.setField(reportDataSourceService, "springDatasourceUsername", "app_user");
        ReflectionTestUtils.setField(reportDataSourceService, "springDatasourcePassword", "new_pass");

        ReportDataSourceEntity existing = new ReportDataSourceEntity();
        existing.setId(123L);
        existing.setName("系统默认MySQL数据源");
        existing.setType("MYSQL");
        existing.setHost("old-host");
        existing.setPort(3307);
        existing.setDatabaseName("old_db");
        existing.setUsername("old_user");
        existing.setPasswordEncrypted("v1:old");

        when(reportDataSourceMapper.findByName("系统默认MySQL数据源")).thenReturn(existing);
        when(passwordCipher.encrypt("new_pass")).thenReturn("enc:new_pass");
        when(reportDataSourceMapper.restoreById(any(ReportDataSourceEntity.class))).thenReturn(1);

        Long defaultDataSourceId = reportDataSourceService.ensureSystemDefaultDataSource();

        assertEquals(Long.valueOf(123L), defaultDataSourceId);
        verify(reportDataSourceMapper).restoreById(entityCaptor.capture());
        verify(reportDataSourceMapper, never()).insert(any(ReportDataSourceEntity.class));

        ReportDataSourceEntity entity = entityCaptor.getValue();
        assertEquals("系统默认MySQL数据源", entity.getName());
        assertEquals("MYSQL", entity.getType());
        assertEquals("192.168.1.10", entity.getHost());
        assertEquals(Integer.valueOf(3306), entity.getPort());
        assertEquals("report_db", entity.getDatabaseName());
        assertEquals("app_user", entity.getUsername());
        assertEquals("enc:new_pass", entity.getPasswordEncrypted());
    }
}
