package com.plm.report.domain.dto;

import javax.validation.constraints.NotBlank;

public class ReportSearchOptionItemRequest {

    @NotBlank(message = "选项值不能为空")
    private String value;

    @NotBlank(message = "选项名称不能为空")
    private String label;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
