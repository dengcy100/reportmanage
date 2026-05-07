package com.plm.report.config;

import com.plm.report.domain.dto.ReportFieldItemRequest;
import com.plm.report.domain.dto.ReportUpsertRequest;
import com.plm.report.mapper.ReportConfigMapper;
import com.plm.report.service.ReportService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SeederRunner implements CommandLineRunner {

    private final ReportConfigMapper reportConfigMapper;
    private final ReportService reportService;

    public SeederRunner(ReportConfigMapper reportConfigMapper, ReportService reportService) {
        this.reportConfigMapper = reportConfigMapper;
        this.reportService = reportService;
    }

    @Override
    public void run(String... args) {
        if (reportConfigMapper.count(null) > 0) {
            return;
        }
        ReportUpsertRequest request = new ReportUpsertRequest();
        request.setName("月度订单汇总报表");
        request.setProcedureName("usp_GetMonthlyOrders");
        request.setPageSize(20);
        request.setExporters("张三,李四,王五");
        request.setFields(defaultFields());
        reportService.create(request);
    }

    private List<ReportFieldItemRequest> defaultFields() {
        List<ReportFieldItemRequest> fields = new ArrayList<ReportFieldItemRequest>();
        fields.add(buildField(1, "订单编号", "order_no", "string", "like", true, 1));
        fields.add(buildField(2, "客户名称", "customer_name", "string", "like", true, 2));
        fields.add(buildField(3, "订单金额", "amount", "number", "eq", false, 0));
        fields.add(buildField(4, "创建日期", "create_date", "date", "range", true, 3));
        return fields;
    }

    private ReportFieldItemRequest buildField(int sort,
                                              String label,
                                              String field,
                                              String type,
                                              String match,
                                              boolean searchable,
                                              int searchSort) {
        ReportFieldItemRequest item = new ReportFieldItemRequest();
        item.setSort(sort);
        item.setLabel(label);
        item.setField(field);
        item.setType(type);
        item.setMatch(match);
        item.setSearchable(searchable);
        item.setSearchSort(searchSort);
        return item;
    }
}
