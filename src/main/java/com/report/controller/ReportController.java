package com.report.controller;

import com.report.common.Result;
import com.report.domain.dto.PageResult;
import com.report.domain.dto.ReportExportTaskCreateResponse;
import com.report.domain.dto.ReportExportTaskStatusVO;
import com.report.domain.dto.CustomLogVO;
import com.report.domain.dto.ReportQueryRequest;
import com.report.domain.dto.ReportQueryResultVO;
import com.report.domain.dto.ReportUpsertRequest;
import com.report.domain.dto.ReportVO;
import com.report.service.ReportQueryService;
import com.report.service.ReportService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportQueryService reportQueryService;

    public ReportController(ReportService reportService, ReportQueryService reportQueryService) {
        this.reportService = reportService;
        this.reportQueryService = reportQueryService;
    }

    @GetMapping
    public Result<PageResult<ReportVO>> pageList(@RequestParam(defaultValue = "1") @Min(1) Integer pageNo,
                                                 @RequestParam(defaultValue = "10") @Min(1) Integer pageSize,
                                                 @RequestParam(required = false) String keyword) {
        return Result.ok(reportService.pageList(pageNo, pageSize, keyword));
    }

    @GetMapping("/{id}")
    public Result<ReportVO> detail(@PathVariable("id") Long id) {
        return Result.ok(reportService.getDetail(id));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody ReportUpsertRequest request) {
        return Result.ok(reportService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Object> update(@PathVariable("id") Long id, @Valid @RequestBody ReportUpsertRequest request) {
        reportService.update(id, request);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable("id") Long id) {
        reportService.delete(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/query")
    public Result<ReportQueryResultVO> query(@PathVariable("id") Long id, @Valid @RequestBody ReportQueryRequest request) {
        return Result.ok(reportQueryService.query(id, request));
    }

    @PostMapping("/{id}/export")
    public void export(@PathVariable("id") Long id,
                       @Valid @RequestBody ReportQueryRequest request,
                       HttpServletResponse response) {
        reportQueryService.export(id, request, response);
    }

    @PostMapping("/{id}/exports")
    public Result<ReportExportTaskCreateResponse> createExportTask(@PathVariable("id") Long id,
                                                                   @Valid @RequestBody ReportQueryRequest request) {
        return Result.ok(reportQueryService.createExportTask(id, request));
    }

    @GetMapping("/logs")
    public Result<PageResult<CustomLogVO>> queryLogs(@RequestParam(defaultValue = "1") @Min(1) Integer pageNo,
                                                     @RequestParam(defaultValue = "20") @Min(1) Integer pageSize,
                                                     @RequestParam(required = false) Long reportId,
                                                     @RequestParam(required = false) String status) {
        return Result.ok(reportQueryService.queryLogs(pageNo, pageSize, reportId, status));
    }

    @DeleteMapping("/logs")
    public Result<Object> clearLogs() {
        reportQueryService.clearLogs();
        return Result.ok(null);
    }

    @GetMapping("/{id}/exports/{taskId}")
    public Result<ReportExportTaskStatusVO> getExportTaskStatus(@PathVariable("id") Long id,
                                                                @PathVariable("taskId") Long taskId) {
        return Result.ok(reportQueryService.getExportTaskStatus(id, taskId));
    }

    @GetMapping("/{id}/exports/{taskId}/download")
    public void downloadExportTask(@PathVariable("id") Long id,
                                   @PathVariable("taskId") Long taskId,
                                   HttpServletResponse response) {
        reportQueryService.downloadExportTask(id, taskId, response);
    }
}
