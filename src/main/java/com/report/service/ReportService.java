package com.report.service;

import com.report.domain.dto.PageResult;
import com.report.domain.dto.ReportVO;
import com.report.domain.dto.ReportUpsertRequest;

public interface ReportService {
    PageResult<ReportVO> pageList(int pageNo, int pageSize, String keyword);

    ReportVO getDetail(Long id);

    ReportVO getDetailByRouterPath(String routerPath);

    Long create(ReportUpsertRequest request);

    void update(Long id, ReportUpsertRequest request);

    void delete(Long id);
}
