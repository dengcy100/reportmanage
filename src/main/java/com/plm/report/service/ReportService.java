package com.plm.report.service;

import com.plm.report.domain.dto.PageResult;
import com.plm.report.domain.dto.ReportVO;
import com.plm.report.domain.dto.ReportUpsertRequest;

public interface ReportService {
    PageResult<ReportVO> pageList(int pageNo, int pageSize, String keyword);

    ReportVO getDetail(Long id);

    Long create(ReportUpsertRequest request);

    void update(Long id, ReportUpsertRequest request);

    void delete(Long id);
}
