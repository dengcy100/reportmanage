package com.plm.report.domain.dto;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.List;

public class ReportUpsertRequest {

    @NotBlank(message = "报表名称不能为空")
    private String name;

    @NotBlank(message = "存储过程不能为空")
    @Pattern(regexp = "^[A-Za-z_][A-Za-z0-9_]{0,127}$", message = "存储过程仅允许字母数字下划线，且不能以数字开头")
    private String procedureName;

    @NotNull(message = "每页行数不能为空")
    @Min(value = 10, message = "每页行数至少10")
    @Max(value = 200, message = "每页行数最多200")
    private Integer pageSize;

    private String exporters;

    @NotEmpty(message = "至少配置一个字段")
    @Valid
    private List<ReportFieldItemRequest> fields;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProcedureName() {
        return procedureName;
    }

    public void setProcedureName(String procedureName) {
        this.procedureName = procedureName;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getExporters() {
        return exporters;
    }

    public void setExporters(String exporters) {
        this.exporters = exporters;
    }

    public List<ReportFieldItemRequest> getFields() {
        return fields;
    }

    public void setFields(List<ReportFieldItemRequest> fields) {
        this.fields = fields;
    }
}
