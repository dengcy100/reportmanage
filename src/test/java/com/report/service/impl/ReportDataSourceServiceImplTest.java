package com.report.service.impl;

import com.report.domain.entity.ReportDataSourceEntity;
import com.report.mapper.ReportDataSourceMapper;
import com.report.util.DataSourcePasswordCipher;
import com.report.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class ReportDataSourceServiceImplTest {

    @Mock
    private ReportDataSourceMapper reportDataSourceMapper;

    @Mock
    private DataSourcePasswordCipher passwordCipher;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private ReportDataSourceServiceImpl reportDataSourceService;

    @Test
    void serviceBeanShouldBeCreated() {
        assertNotNull(reportDataSourceService);
    }
}
