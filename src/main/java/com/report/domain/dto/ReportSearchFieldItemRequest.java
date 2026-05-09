package com.report.domain.dto;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;

public class ReportSearchFieldItemRequest {

    @NotBlank(message = "搜索字段名不能为空")
    private String label;

    @NotBlank(message = "搜索字段不能为空")
    @Pattern(regexp = "^[A-Za-z_][A-Za-z0-9_]{0,127}$", message = "搜索字段仅允许字母数字下划线，且不能以数字开头")
    private String field;

    @NotBlank(message = "搜索字段类型不能为空")
    @Pattern(regexp = "^(string|number|date|datetime|boolean)$", message = "搜索字段类型不合法")
    private String type;

    @NotBlank(message = "搜索匹配方式不能为空")
    @Pattern(regexp = "^(like|eq|range|in)$", message = "搜索匹配方式不合法")
    private String match;

    @NotBlank(message = "搜索控件类型不能为空")
    @Pattern(regexp = "^(input|single_select|multi_select)$", message = "搜索控件类型不合法")
    private String controlType;

    @NotNull(message = "是否支持批量输入不能为空")
    private Boolean multilineEnabled;

    @NotNull(message = "搜索排序不能为空")
    @Min(value = 1, message = "搜索排序必须 >= 1")
    @Max(value = 99, message = "搜索排序必须 <= 99")
    private Integer searchSort;

    @Min(value = 0, message = "默认查询天数不能小于0")
    @Max(value = 3650, message = "默认查询天数不能大于3650")
    private Integer defaultQueryDays;

    @Min(value = 0, message = "最长查询天数不能小于0")
    @Max(value = 3650, message = "最长查询天数不能大于3650")
    private Integer maxQueryDays;

    @Valid
    private List<ReportSearchOptionItemRequest> options = new ArrayList<ReportSearchOptionItemRequest>();

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

    public String getControlType() {
        return controlType;
    }

    public void setControlType(String controlType) {
        this.controlType = controlType;
    }

    public Boolean getMultilineEnabled() {
        return multilineEnabled;
    }

    public void setMultilineEnabled(Boolean multilineEnabled) {
        this.multilineEnabled = multilineEnabled;
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

    public List<ReportSearchOptionItemRequest> getOptions() {
        return options;
    }

    public void setOptions(List<ReportSearchOptionItemRequest> options) {
        this.options = options;
    }
}
