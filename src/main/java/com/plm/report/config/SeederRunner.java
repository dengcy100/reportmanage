package com.plm.report.config;

import com.plm.report.domain.dto.ReportFieldItemRequest;
import com.plm.report.domain.dto.ReportSearchFieldItemRequest;
import com.plm.report.domain.dto.ReportUpsertRequest;
import com.plm.report.mapper.ReportConfigMapper;
import com.plm.report.service.ReportDataSourceService;
import com.plm.report.service.ReportService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(2)
public class SeederRunner implements CommandLineRunner {

    private final ReportConfigMapper reportConfigMapper;
    private final ReportService reportService;
    private final ReportDataSourceService reportDataSourceService;

    public SeederRunner(ReportConfigMapper reportConfigMapper,
                        ReportService reportService,
                        ReportDataSourceService reportDataSourceService) {
        this.reportConfigMapper = reportConfigMapper;
        this.reportService = reportService;
        this.reportDataSourceService = reportDataSourceService;
    }

    @Override
    public void run(String... args) {
        if (reportConfigMapper.count(null) > 0) {
            Long defaultDataSourceId = reportDataSourceService.ensureSystemDefaultDataSource();
            reportDataSourceService.backfillMissingReportDataSourceIds(defaultDataSourceId);
            return;
        }
        Long defaultDataSourceId = reportDataSourceService.ensureSystemDefaultDataSource();
        reportDataSourceService.backfillMissingReportDataSourceIds(defaultDataSourceId);
        ReportUpsertRequest request = new ReportUpsertRequest();
        request.setDataSourceId(defaultDataSourceId);
        request.setName("月度订单汇总报表");
        request.setProcedureName("usp_GetMonthlyOrders");
        request.setPageSize(20);
        request.setExporters("张三,李四,王五");
        request.setExportWaitMessage("导出数据量较大，请耐心等待，系统正在准备下载文件。");
        request.setFields(defaultFields());
        request.setSearchFields(defaultSearchFields());
        reportService.create(request);
    }

    private List<ReportFieldItemRequest> defaultFields() {
        List<ReportFieldItemRequest> fields = new ArrayList<ReportFieldItemRequest>();
        fields.add(buildField(1, "订单编号", "order_no", "string", "like"));
        fields.add(buildField(2, "客户名称", "customer_name", "string", "like"));
        fields.add(buildField(3, "订单金额", "amount", "number", "eq"));
        fields.add(buildField(4, "创建日期", "create_date", "date", "range"));
        return fields;
    }

    private List<ReportSearchFieldItemRequest> defaultSearchFields() {
        List<ReportSearchFieldItemRequest> fields = new ArrayList<ReportSearchFieldItemRequest>();
        fields.add(buildSearchField("订单编号", "order_no", "string", "like", "input", true, 1, 0, 0));
        fields.add(buildSearchField("客户名称", "customer_name", "string", "like", "input", false, 2, 0, 0));
        fields.add(buildSearchField("创建日期", "create_date", "date", "range", "input", false, 3, 30, 90));
        return fields;
    }

    private ReportFieldItemRequest buildField(int sort,
                                              String label,
                                              String field,
                                              String type,
                                              String match) {
        ReportFieldItemRequest item = new ReportFieldItemRequest();
        item.setSort(sort);
        item.setLabel(label);
        item.setField(field);
        item.setType(type);
        item.setMatch(match);
        return item;
    }

    private ReportSearchFieldItemRequest buildSearchField(String label,
                                                          String field,
                                                          String type,
                                                          String match,
                                                          String controlType,
                                                          boolean multilineEnabled,
                                                          int searchSort,
                                                          int defaultQueryDays,
                                                          int maxQueryDays) {
        ReportSearchFieldItemRequest item = new ReportSearchFieldItemRequest();
        item.setLabel(label);
        item.setField(field);
        item.setType(type);
        item.setMatch(match);
        item.setControlType(controlType);
        item.setMultilineEnabled(multilineEnabled);
        item.setSearchSort(searchSort);
        item.setDefaultQueryDays(defaultQueryDays);
        item.setMaxQueryDays(maxQueryDays);
        return item;
    }
}
