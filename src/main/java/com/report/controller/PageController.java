package com.report.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {

    @GetMapping({"/", "/reportConfig"})
    public String reportListPage() {
        return "forward:/reportConfig.html";
    }

    @GetMapping({"/dataSourceConfig"})
    public String dataSourceListPage() {
        return "forward:/dataSourceConfig.html";
    }

    @GetMapping("/dataSourceConfig/{id}")
    public String dataSourceById(@PathVariable("id") String id) {
        return "redirect:/dataSourceConfig.html?id=" + id;
    }

    @GetMapping("/reportView")
    public String reportViewPage() {
        return "forward:/reportView.html";
    }

    @GetMapping("/reportView/{id}")
    public String reportViewById(@PathVariable("id") String id) {
        return "redirect:/reportView.html?id=" + id;
    }

    @GetMapping("/reportLog")
    public String reportLogPage() {
        return "forward:/reportLog.html";
    }

    @GetMapping("/reportLog/{reportId}")
    public String reportLogByReportId(@PathVariable("reportId") String reportId) {
        return "redirect:/reportLog.html?reportId=" + reportId;
    }
}
