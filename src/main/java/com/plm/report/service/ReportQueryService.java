package com.plm.report.service;

import com.plm.report.domain.dto.ReportQueryRequest;
import com.plm.report.domain.dto.ReportQueryResultVO;

import javax.servlet.http.HttpServletResponse;

public interface ReportQueryService {
    ReportQueryResultVO query(Long reportId, ReportQueryRequest request);

    void export(Long reportId, ReportQueryRequest request, HttpServletResponse response);
}
