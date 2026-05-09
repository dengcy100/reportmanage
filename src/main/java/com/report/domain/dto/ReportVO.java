package com.report.domain.dto;

import java.util.List;

public class ReportVO {
    private Long id;
    private Long dataSourceId;
    private String name;
    private String routerPath;
    private String dataSourceName;
    private String dataSourceType;
    private String procedureName;
    private Integer pageSize;
    private String exporters;
    private String exportWaitMessage;
    private Boolean queryEnabled;
    private Boolean downloadEnabled;
    private List<ReportFieldVO> fields;
    private List<ReportSearchFieldVO> searchFields;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRouterPath() {
        return routerPath;
    }

    public void setRouterPath(String routerPath) {
        this.routerPath = routerPath;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    public String getDataSourceType() {
        return dataSourceType;
    }

    public void setDataSourceType(String dataSourceType) {
        this.dataSourceType = dataSourceType;
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

    public String getExportWaitMessage() {
        return exportWaitMessage;
    }

    public void setExportWaitMessage(String exportWaitMessage) {
        this.exportWaitMessage = exportWaitMessage;
    }

    public Boolean getQueryEnabled() {
        return queryEnabled;
    }

    public void setQueryEnabled(Boolean queryEnabled) {
        this.queryEnabled = queryEnabled;
    }

    public Boolean getDownloadEnabled() {
        return downloadEnabled;
    }

    public void setDownloadEnabled(Boolean downloadEnabled) {
        this.downloadEnabled = downloadEnabled;
    }

    public List<ReportFieldVO> getFields() {
        return fields;
    }

    public void setFields(List<ReportFieldVO> fields) {
        this.fields = fields;
    }

    public List<ReportSearchFieldVO> getSearchFields() {
        return searchFields;
    }

    public void setSearchFields(List<ReportSearchFieldVO> searchFields) {
        this.searchFields = searchFields;
    }
}
