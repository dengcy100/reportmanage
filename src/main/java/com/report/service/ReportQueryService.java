package com.report.service;

import com.report.domain.dto.ReportExportTaskCreateResponse;
import com.report.domain.dto.ReportExportTaskStatusVO;
import com.report.domain.dto.ReportExportTaskVO;
import com.report.domain.dto.CustomLogVO;
import com.report.domain.dto.PageResult;
import com.report.domain.dto.ReportQueryRequest;
import com.report.domain.dto.ReportQueryResultVO;

import javax.servlet.http.HttpServletResponse;

public interface ReportQueryService {
    ReportQueryResultVO query(Long reportId, ReportQueryRequest request);

    void export(Long reportId, ReportQueryRequest request, HttpServletResponse response);

    ReportExportTaskCreateResponse createExportTask(Long reportId, ReportQueryRequest request);

    PageResult<CustomLogVO> queryLogs(int pageNo, int pageSize, Long reportId, String status);

    PageResult<ReportExportTaskVO> queryExportTasks(int pageNo, int pageSize, Long reportId, String status);

    void clearLogs();

    ReportExportTaskStatusVO getExportTaskStatus(Long reportId, Long taskId);

    void downloadExportTask(Long reportId, Long taskId, HttpServletResponse response);
}
