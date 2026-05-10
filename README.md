# reportmanage

## 项目简介

`reportmanage` 是一个基于 `Spring Boot 2.7`、`MyBatis`、`MySQL` 和 `EasyExcel` 的轻量级报表管理系统。项目通过“报表配置 + 查询定义（存储过程或自定义 SQL）”的方式，把报表查询、字段展示、搜索条件、导出流程和操作日志统一收口，适合内部运营报表、订单统计报表、台账类报表等场景快速搭建。

系统同时提供后端接口和内置静态管理页面，支持在页面上维护数据源、配置报表、执行查询、发起导出以及查看查询日志和导出任务状态。

## 核心功能

- 报表配置管理：支持维护报表名称、路由标识、绑定数据源、查询类型（存储过程/SQL）、展示字段、搜索字段、分页大小、导出人及导出等待提示。
- 数据源管理：支持维护 MySQL 数据源，提供连接测试，并对密码进行加密存储。
- 动态查询：按报表配置动态组装存储过程参数或执行只读 SQL，支持分页、批量输入、多选/单选、日期区间、默认查询天数和最大查询范围限制。
- Excel 导出：支持同步导出和异步导出任务，异步任务可轮询状态并在生成完成后下载文件。
- 日志与任务跟踪：记录查询日志，保留导出任务状态、错误信息、文件路径和过期时间，便于排查和审计。
- 初始化与演示数据：应用启动时自动执行 [sql/init.sql](D:\JavaProject\reportmanage\sql\init.sql)，统一初始化库表、存储过程、演示数据、默认数据源和示例报表配置。

## 技术栈

- Java 8
- Spring Boot 2.7.18
- MyBatis Spring Boot Starter 2.2.2
- MySQL 8+
- EasyExcel 3.3.4
- 原生静态 HTML 页面

## 适用场景

- 需要快速搭建内部报表后台，而不想为每张报表单独开发查询页面。
- 查询逻辑已经沉淀在数据库存储过程中，希望由系统统一管理报表入口和导出能力。
- 需要让业务人员维护报表字段、筛选项和导出流程，而不是频繁改代码发版。

## 快速启动

1. 准备 MySQL 服务（默认库名 `report`，启动时会自动创建并初始化）。
2. 按需修改 [src/main/resources/application.properties](D:\JavaProject\reportmanage\src\main\resources\application.properties) 中的数据库连接、导出目录和加密密钥配置。
3. 在项目根目录执行：

```powershell
.\mvnw.cmd spring-boot:run
```

4. 默认启动地址为 `http://localhost:8080`。应用每次启动都会自动执行 [sql/init.sql](D:\JavaProject\reportmanage\sql\init.sql)（幂等），确保结构和基础演示数据完整。

## 页面入口

- 报表配置页：`/reportConfig`
- 数据源配置页：`/dataSourceConfig`
- 报表查询页：`/reportView`
- 查询日志页：`/reportLog`
- 导出任务页：`/reportExportTask`

## 默认示例

项目启动后会确保存在以下演示内容：

- 默认数据源：`系统默认MySQL数据源`（自动绑定到示例报表）。
- 示例报表（存储过程）：`月度订单汇总报表`，基于 `usp_GetMonthlyOrders`。
- 示例报表（自定义 SQL）：`月度订单汇总报表_自定义SQL数据`。
- 演示数据表：`demo_order_data`。

说明：

- `sql/init.sql` 为初始化唯一来源，包含库表结构、索引、演示数据、存储过程和示例报表配置。
- 脚本按幂等方式执行，已存在的数据不会重复插入。
- 默认数据源密码会在应用启动时按当前 `spring.datasource.password` 自动加密后写入，便于示例报表直接连通当前数据库。

## 自定义 SQL 配置规范

- 查询类型选择 `SQL` 时，必须同时配置 `查询SQL` 和 `统计SQL`。
- SQL 仅支持单条只读 `SELECT`，禁止 `INSERT/UPDATE/DELETE/DDL`、注释、多语句、`${}` 占位符。
- 参数占位符必须使用 `#{field}`，其中 `field` 需与搜索字段一致；区间字段可使用 `#{field_start}`、`#{field_end}`。
- 查询SQL禁止显式 `LIMIT`，系统在列表查询时自动追加分页参数；导出时不追加分页限制。
- 报表字段中的 `field` 必须与 SQL 返回列别名一致，例如：

```sql
SELECT
  d.order_no AS order_no,
  d.customer_name AS customer_name,
  d.amount AS amount,
  DATE_FORMAT(d.create_date, '%Y-%m-%d') AS create_date
FROM demo_order_data d
WHERE (#{order_no} IS NULL OR #{order_no} = '' OR d.order_no = #{order_no})
  AND (#{create_date_start} IS NULL OR #{create_date_start} = '' OR d.create_date >= STR_TO_DATE(#{create_date_start}, '%Y-%m-%d'))
  AND (#{create_date_end} IS NULL OR #{create_date_end} = '' OR d.create_date <= STR_TO_DATE(#{create_date_end}, '%Y-%m-%d'))
ORDER BY d.create_date DESC
```

```sql
SELECT COUNT(1)
FROM demo_order_data d
WHERE (#{order_no} IS NULL OR #{order_no} = '' OR d.order_no = #{order_no})
  AND (#{create_date_start} IS NULL OR #{create_date_start} = '' OR d.create_date >= STR_TO_DATE(#{create_date_start}, '%Y-%m-%d'))
  AND (#{create_date_end} IS NULL OR #{create_date_end} = '' OR d.create_date <= STR_TO_DATE(#{create_date_end}, '%Y-%m-%d'))
```
