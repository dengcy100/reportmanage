package com.plm.report.domain.dto;

import java.util.List;

public class ReportVO {
    private Long id;
    private String name;
    private String procedureName;
    private Integer pageSize;
    private String exporters;
    private List<ReportFieldVO> fields;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public List<ReportFieldVO> getFields() {
        return fields;
    }

    public void setFields(List<ReportFieldVO> fields) {
        this.fields = fields;
    }
}
