package com.report.domain.dto;

import java.util.ArrayList;
import java.util.List;

public class ReportSearchFieldVO {
    private Long id;
    private String label;
    private String field;
    private String type;
    private String match;
    private String controlType;
    private Boolean multilineEnabled;
    private Integer searchSort;
    private Integer defaultQueryDays;
    private Integer maxQueryDays;
    private List<ReportSearchOptionVO> options = new ArrayList<ReportSearchOptionVO>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public List<ReportSearchOptionVO> getOptions() {
        return options;
    }

    public void setOptions(List<ReportSearchOptionVO> options) {
        this.options = options;
    }
}
