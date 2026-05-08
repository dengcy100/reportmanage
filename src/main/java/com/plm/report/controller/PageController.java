package com.plm.report.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {

    @GetMapping({"/", "/report-list"})
    public String reportListPage() {
        return "forward:/report-list.html";
    }

    @GetMapping("/report-view")
    public String reportViewPage() {
        return "forward:/report-view.html";
    }

    @GetMapping("/report-view/{id}")
    public String reportViewById(@PathVariable("id") String id) {
        return "redirect:/report-view.html?id=" + id;
    }

    @GetMapping("/report-log")
    public String reportLogPage() {
        return "forward:/report-log.html";
    }

    @GetMapping("/report-log/{reportId}")
    public String reportLogByReportId(@PathVariable("reportId") String reportId) {
        return "redirect:/report-log.html?reportId=" + reportId;
    }
}
