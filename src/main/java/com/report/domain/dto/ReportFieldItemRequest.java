package com.report.domain.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class ReportFieldItemRequest {

    @NotNull(message = "字段排序不能为空")
    @Min(value = 1, message = "字段排序必须 >= 1")
    private Integer sort;

    @NotBlank(message = "字段名不能为空")
    private String label;

    @NotBlank(message = "字段不能为空")
    @Pattern(regexp = "^[A-Za-z_][A-Za-z0-9_]{0,127}$", message = "字段仅允许字母数字下划线，且不能以数字开头")
    private String field;

    @NotBlank(message = "字段类型不能为空")
    @Pattern(regexp = "^(string|number|date|datetime|boolean)$", message = "字段类型不合法")
    private String type;

    @NotBlank(message = "匹配方式不能为空")
    @Pattern(regexp = "^(like|eq|range|in)$", message = "匹配方式不合法")
    private String match;

    // 兼容旧请求，展示字段已不再使用 searchable/searchSort/queryDays 配置
    private Boolean searchable;

    private Integer searchSort;

    private Integer defaultQueryDays;

    private Integer maxQueryDays;

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMatch() {
        return match;
    }

    public void setMatch(String match) {
        this.match = match;
    }

    public Boolean getSearchable() {
        return searchable;
    }

    public void setSearchable(Boolean searchable) {
        this.searchable = searchable;
    }

    public Integer getSearchSort() {
        return searchSort;
    }

    public void setSearchSort(Integer searchSort) {
        this.searchSort = searchSort;
    }

    public Integer getDefaultQueryDays() {
        return defaultQueryDays;
    }

    public void setDefaultQueryDays(Integer defaultQueryDays) {
        this.defaultQueryDays = defaultQueryDays;
    }

    public Integer getMaxQueryDays() {
        return maxQueryDays;
    }

    public void setMaxQueryDays(Integer maxQueryDays) {
        this.maxQueryDays = maxQueryDays;
    }
}
