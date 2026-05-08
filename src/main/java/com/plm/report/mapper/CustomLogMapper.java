package com.plm.report.mapper;

import com.plm.report.domain.entity.CustomLogEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CustomLogMapper {

    @Insert("INSERT INTO report_log(id,report_id,user_id,action_type,filters_json,page_no,page_size,result_total,status,error_message,created_at) " +
            "VALUES(#{id},#{reportId},#{userId},#{actionType},#{filtersJson},#{pageNo},#{pageSize},#{resultTotal},#{status},#{errorMessage},NOW())")
    int insert(CustomLogEntity entity);

    @Delete("DELETE FROM report_log")
    int deleteAll();

    @Select("<script>" +
            "SELECT l.id,l.report_id,c.name AS report_name,l.user_id,l.action_type,l.filters_json,l.page_no,l.page_size,l.result_total,l.status,l.error_message,l.created_at " +
            "FROM report_log l " +
            "LEFT JOIN report_config c ON c.id=l.report_id " +
            "WHERE 1=1 " +
            "<if test='reportId != null'> AND l.report_id=#{reportId} </if> " +
            "<if test='status != null and status != \"\"'> AND l.status=#{status} </if> " +
            "ORDER BY l.id DESC " +
            "LIMIT #{offset},#{limit}" +
            "</script>")
    List<CustomLogEntity> pageList(@Param("reportId") Long reportId,
                                   @Param("status") String status,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(1) FROM report_log WHERE 1=1 " +
            "<if test='reportId != null'> AND report_id=#{reportId} </if> " +
            "<if test='status != null and status != \"\"'> AND status=#{status} </if> " +
            "</script>")
    long count(@Param("reportId") Long reportId,
               @Param("status") String status);
}
