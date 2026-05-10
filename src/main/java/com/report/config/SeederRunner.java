package com.report.config;

import com.report.domain.dto.ReportFieldItemRequest;
import com.report.domain.dto.ReportSearchFieldItemRequest;
import com.report.domain.dto.ReportUpsertRequest;
import com.report.mapper.ReportConfigMapper;
import com.report.service.ReportDataSourceService;
import com.report.service.ReportService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(2)
public class SeederRunner implements CommandLineRunner {

    private static final String REPORT_NAME_PROCEDURE = "月度订单汇总报表";
    private static final String REPORT_NAME_SQL = "月度订单汇总报表_自定义SQL数据";
    private static final String QUERY_TYPE_PROCEDURE = "PROCEDURE";
    private static final String QUERY_TYPE_SQL = "SQL";
    private static final String PROCEDURE_NAME_MONTHLY_ORDERS = "usp_GetMonthlyOrders";
    private static final String SQL_MONTHLY_ORDERS_QUERY =
            "SELECT d.order_no AS order_no, d.customer_name AS customer_name, d.amount AS amount, " +
                    "DATE_FORMAT(d.create_date, '%Y-%m-%d') AS create_date " +
                    "FROM demo_order_data d " +
                    "WHERE (#{order_no} IS NULL OR #{order_no} = '' OR " +
                    "(LOCATE(CHAR(59), #{order_no}) > 0 AND FIND_IN_SET(d.order_no, REPLACE(#{order_no}, CHAR(59), ',')) > 0) OR " +
                    "(LOCATE(CHAR(59), #{order_no}) = 0 AND d.order_no LIKE CONCAT('%', #{order_no}, '%'))) " +
                    "AND (#{customer_name} IS NULL OR #{customer_name} = '' OR " +
                    "(LOCATE(CHAR(59), #{customer_name}) > 0 AND FIND_IN_SET(d.customer_name, REPLACE(#{customer_name}, CHAR(59), ',')) > 0) OR " +
                    "(LOCATE(CHAR(59), #{customer_name}) = 0 AND d.customer_name LIKE CONCAT('%', #{customer_name}, '%'))) " +
                    "AND (#{create_date_start} IS NULL OR #{create_date_start} = '' OR d.create_date >= STR_TO_DATE(#{create_date_start}, '%Y-%m-%d')) " +
                    "AND (#{create_date_end} IS NULL OR #{create_date_end} = '' OR d.create_date <= STR_TO_DATE(#{create_date_end}, '%Y-%m-%d')) " +
                    "ORDER BY d.create_date DESC, d.id DESC";
    private static final String SQL_MONTHLY_ORDERS_COUNT =
            "SELECT COUNT(1) " +
                    "FROM demo_order_data d " +
                    "WHERE (#{order_no} IS NULL OR #{order_no} = '' OR " +
                    "(LOCATE(CHAR(59), #{order_no}) > 0 AND FIND_IN_SET(d.order_no, REPLACE(#{order_no}, CHAR(59), ',')) > 0) OR " +
                    "(LOCATE(CHAR(59), #{order_no}) = 0 AND d.order_no LIKE CONCAT('%', #{order_no}, '%'))) " +
                    "AND (#{customer_name} IS NULL OR #{customer_name} = '' OR " +
                    "(LOCATE(CHAR(59), #{customer_name}) > 0 AND FIND_IN_SET(d.customer_name, REPLACE(#{customer_name}, CHAR(59), ',')) > 0) OR " +
                    "(LOCATE(CHAR(59), #{customer_name}) = 0 AND d.customer_name LIKE CONCAT('%', #{customer_name}, '%'))) " +
                    "AND (#{create_date_start} IS NULL OR #{create_date_start} = '' OR d.create_date >= STR_TO_DATE(#{create_date_start}, '%Y-%m-%d')) " +
                    "AND (#{create_date_end} IS NULL OR #{create_date_end} = '' OR d.create_date <= STR_TO_DATE(#{create_date_end}, '%Y-%m-%d'))";

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
        Long defaultDataSourceId = reportDataSourceService.ensureSystemDefaultDataSource();
        reportDataSourceService.backfillMissingReportDataSourceIds(defaultDataSourceId);
        createProcedureReportIfMissing(defaultDataSourceId);
        createCustomSqlReportIfMissing(defaultDataSourceId);
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

    private void createProcedureReportIfMissing(Long dataSourceId) {
        if (reportConfigMapper.countByName(REPORT_NAME_PROCEDURE) > 0) {
            return;
        }
        ReportUpsertRequest request = buildCommonRequest(dataSourceId, REPORT_NAME_PROCEDURE);
        request.setQueryType(QUERY_TYPE_PROCEDURE);
        request.setProcedureName(PROCEDURE_NAME_MONTHLY_ORDERS);
        reportService.create(request);
    }

    private void createCustomSqlReportIfMissing(Long dataSourceId) {
        if (reportConfigMapper.countByName(REPORT_NAME_SQL) > 0) {
            return;
        }
        ReportUpsertRequest request = buildCommonRequest(dataSourceId, REPORT_NAME_SQL);
        request.setQueryType(QUERY_TYPE_SQL);
        request.setQuerySql(SQL_MONTHLY_ORDERS_QUERY);
        request.setCountSql(SQL_MONTHLY_ORDERS_COUNT);
        reportService.create(request);
    }

    private ReportUpsertRequest buildCommonRequest(Long dataSourceId, String reportName) {
        ReportUpsertRequest request = new ReportUpsertRequest();
        request.setDataSourceId(dataSourceId);
        request.setName(reportName);
        request.setPageSize(20);
        request.setExporters("张三,李四,王五");
        request.setExportWaitMessage("导出数据量较大，请耐心等待，系统正在准备下载文件。");
        request.setFields(defaultFields());
        request.setSearchFields(defaultSearchFields());
        return request;
    }
}
