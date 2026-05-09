package com.report.domain.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

public class ReportQueryRequest {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须 >= 1")
    private Integer pageNo;

    @Min(value = 1, message = "每页行数必须 >= 1")
    @Max(value = 200, message = "每页行数必须 <= 200")
    private Integer pageSize;

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

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
