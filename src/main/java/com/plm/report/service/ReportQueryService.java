package com.plm.report.service;

import com.plm.report.domain.dto.ReportExportTaskCreateResponse;
import com.plm.report.domain.dto.ReportExportTaskStatusVO;
import com.plm.report.domain.dto.CustomLogVO;
import com.plm.report.domain.dto.PageResult;
import com.plm.report.domain.dto.ReportQueryRequest;
import com.plm.report.domain.dto.ReportQueryResultVO;

import javax.servlet.http.HttpServletResponse;

public interface ReportQueryService {
    ReportQueryResultVO query(Long reportId, ReportQueryRequest request);

    void export(Long reportId, ReportQueryRequest request, HttpServletResponse response);

    ReportExportTaskCreateResponse createExportTask(Long reportId, ReportQueryRequest request);

    PageResult<CustomLogVO> queryLogs(int pageNo, int pageSize, Long reportId, String status);

    ReportExportTaskStatusVO getExportTaskStatus(Long reportId, Long taskId);

    void downloadExportTask(Long reportId, Long taskId, HttpServletResponse response);
}
