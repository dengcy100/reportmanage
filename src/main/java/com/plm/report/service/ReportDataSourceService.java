package com.plm.report.service;

import com.plm.report.domain.dto.PageResult;
import com.plm.report.domain.dto.ReportDataSourceOptionVO;
import com.plm.report.domain.dto.ReportDataSourceUpsertRequest;
import com.plm.report.domain.dto.ReportDataSourceVO;
import com.plm.report.domain.entity.ReportDataSourceEntity;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

public interface ReportDataSourceService {

    PageResult<ReportDataSourceVO> pageList(int pageNo, int pageSize, String keyword);

    List<ReportDataSourceOptionVO> listMysqlOptions();

    Map<Long, ReportDataSourceEntity> getActiveMysqlDataSourceMap(List<Long> ids);

    ReportDataSourceVO getDetail(Long id);

    Long create(ReportDataSourceUpsertRequest request);

    void update(Long id, ReportDataSourceUpsertRequest request);

    void delete(Long id);

    void testConnection(ReportDataSourceUpsertRequest request);

    Connection openConnection(Long id);

    ReportDataSourceEntity getActiveMysqlDataSource(Long id);

    Long ensureSystemDefaultDataSource();

    void backfillMissingReportDataSourceIds(Long defaultDataSourceId);
}
