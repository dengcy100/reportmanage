package com.report.service.impl;

import com.report.domain.entity.ReportDataSourceEntity;
import com.report.exception.AccessDeniedException;
import com.report.mapper.ReportDataSourceMapper;
import com.report.service.UserContextService;
import com.report.util.DataSourcePasswordCipher;
import com.report.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportDataSourceServiceImplTest {

    @Mock
    private ReportDataSourceMapper reportDataSourceMapper;

    @Mock
    private DataSourcePasswordCipher passwordCipher;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private UserContextService userContextService;

    @InjectMocks
    private ReportDataSourceServiceImpl reportDataSourceService;

    @Test
    void serviceBeanShouldBeCreated() {
        assertNotNull(reportDataSourceService);
    }

    @Test
    void pageList_shouldRejectWhenNotAdmin() {
        when(userContextService.isCurrentUserAdmin()).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> reportDataSourceService.pageList(1, 10, ""));
        assertEquals("仅管理员可查看数据源", ex.getMessage());
        verifyNoInteractions(reportDataSourceMapper);
    }

    @Test
    void listMysqlOptions_shouldRemainAccessible() {
        when(reportDataSourceMapper.listMysqlOptions()).thenReturn(Collections.emptyList());

        assertEquals(Collections.emptyList(), reportDataSourceService.listMysqlOptions());
        verifyNoInteractions(userContextService);
    }
}
