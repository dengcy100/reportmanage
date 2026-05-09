package com.report.mapper;

import com.report.domain.entity.ReportSearchFieldEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReportSearchFieldMapper {

    @Select("SELECT id,report_id,label,field_name,field_type,match_type,control_type,multiline_enabled,option_values_json,search_sort,default_query_days,max_query_days,deleted,created_at,updated_at " +
            "FROM report_search_field WHERE report_id=#{reportId} AND deleted=0 ORDER BY search_sort ASC,id ASC")
    List<ReportSearchFieldEntity> findByReportId(@Param("reportId") Long reportId);

    @Insert("INSERT INTO report_search_field(id,report_id,label,field_name,field_type,match_type,control_type,multiline_enabled,option_values_json,search_sort,default_query_days,max_query_days,deleted,created_at,updated_at) " +
            "VALUES(#{id},#{reportId},#{label},#{fieldName},#{fieldType},#{matchType},#{controlType},#{multilineEnabled},#{optionValuesJson},#{searchSort},#{defaultQueryDays},#{maxQueryDays},0,NOW(),NOW())")
    int insert(ReportSearchFieldEntity entity);

    @Update("UPDATE report_search_field SET deleted=1,updated_at=NOW() WHERE report_id=#{reportId} AND deleted=0")
    int logicDeleteByReportId(@Param("reportId") Long reportId);
}
