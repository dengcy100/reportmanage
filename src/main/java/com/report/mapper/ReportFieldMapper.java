package com.report.mapper;

import com.report.domain.entity.ReportFieldEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReportFieldMapper {

    @Select("SELECT id,report_id,label,field_name,field_type,match_type,searchable,search_sort,default_query_days,max_query_days,sort_order,deleted,created_at,updated_at " +
            "FROM report_field WHERE report_id=#{reportId} AND deleted=0 ORDER BY sort_order ASC")
    List<ReportFieldEntity> findByReportId(@Param("reportId") Long reportId);

    @Select("SELECT COUNT(1) FROM report_field WHERE report_id=#{reportId} AND deleted=0")
    long countByReportId(@Param("reportId") Long reportId);

    @Insert("INSERT INTO report_field(id,report_id,label,field_name,field_type,match_type,searchable,search_sort,default_query_days,max_query_days,sort_order,deleted,created_at,updated_at) " +
            "VALUES(#{id},#{reportId},#{label},#{fieldName},#{fieldType},#{matchType},#{searchable},#{searchSort},#{defaultQueryDays},#{maxQueryDays},#{sortOrder},0,NOW(),NOW())")
    int insert(ReportFieldEntity entity);

    @Update("UPDATE report_field SET deleted=1,updated_at=NOW() WHERE report_id=#{reportId} AND deleted=0")
    int logicDeleteByReportId(@Param("reportId") Long reportId);
}
