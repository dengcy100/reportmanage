package com.report.controller;

import com.report.common.Result;
import com.report.domain.dto.PageResult;
import com.report.domain.dto.ReportDataSourceOptionVO;
import com.report.domain.dto.ReportDataSourceUpsertRequest;
import com.report.domain.dto.ReportDataSourceVO;
import com.report.service.ReportDataSourceService;
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

import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/data-sources")
public class ReportDataSourceController {

    private final ReportDataSourceService reportDataSourceService;

    public ReportDataSourceController(ReportDataSourceService reportDataSourceService) {
        this.reportDataSourceService = reportDataSourceService;
    }

    @GetMapping
    public Result<PageResult<ReportDataSourceVO>> pageList(@RequestParam(defaultValue = "1") @Min(1) Integer pageNo,
                                                           @RequestParam(defaultValue = "10") @Min(1) Integer pageSize,
                                                           @RequestParam(required = false) String keyword) {
        return Result.ok(reportDataSourceService.pageList(pageNo, pageSize, keyword));
    }

    @GetMapping("/options")
    public Result<List<ReportDataSourceOptionVO>> options() {
        return Result.ok(reportDataSourceService.listMysqlOptions());
    }

    @GetMapping("/{id}")
    public Result<ReportDataSourceVO> detail(@PathVariable("id") Long id) {
        return Result.ok(reportDataSourceService.getDetail(id));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody ReportDataSourceUpsertRequest request) {
        return Result.ok(reportDataSourceService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Object> update(@PathVariable("id") Long id, @Valid @RequestBody ReportDataSourceUpsertRequest request) {
        reportDataSourceService.update(id, request);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable("id") Long id) {
        reportDataSourceService.delete(id);
        return Result.ok(null);
    }

    @PostMapping("/test")
    public Result<Object> test(@Valid @RequestBody ReportDataSourceUpsertRequest request) {
        reportDataSourceService.testConnection(request);
        return Result.ok(null);
    }
}
