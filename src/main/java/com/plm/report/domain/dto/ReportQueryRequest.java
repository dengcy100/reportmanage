package com.plm.report.domain.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

public class ReportQueryRequest {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须 >= 1")
    private Integer pageNo;

    private Map<String, Object> filters = new HashMap<String, Object>();

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }
}
