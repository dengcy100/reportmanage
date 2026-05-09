package com.report.domain.entity;

import java.time.LocalDateTime;

public class ReportConfigEntity {
    private Long id;
    private Long dataSourceId;
    private String name;
    private String procedureName;
    private Integer pageSize;
    private String exporters;
    private String exportWaitMessage;
    private Integer queryEnabled;
    private Integer downloadEnabled;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public Integer getQueryEnabled() {
        return queryEnabled;
    }

    public void setQueryEnabled(Integer queryEnabled) {
        this.queryEnabled = queryEnabled;
    }

    public Integer getDownloadEnabled() {
        return downloadEnabled;
    }

    public void setDownloadEnabled(Integer downloadEnabled) {
        this.downloadEnabled = downloadEnabled;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
