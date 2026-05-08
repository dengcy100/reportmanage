package com.plm.report.mapper;

import com.plm.report.domain.entity.ReportConfigEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReportConfigMapper {

    @Select("<script>" +
            "SELECT id,name,procedure_name,page_size,exporters,export_wait_message,deleted,created_at,updated_at " +
            "FROM report_config " +
            "WHERE deleted=0 " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (name LIKE CONCAT('%',#{keyword},'%') OR procedure_name LIKE CONCAT('%',#{keyword},'%')) " +
            "</if> " +
            "ORDER BY updated_at DESC " +
            "LIMIT #{offset},#{limit}" +
            "</script>")
    List<ReportConfigEntity> pageList(@Param("keyword") String keyword,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(1) FROM report_config WHERE deleted=0 " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (name LIKE CONCAT('%',#{keyword},'%') OR procedure_name LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "</script>")
    long count(@Param("keyword") String keyword);

    @Select("SELECT id,name,procedure_name,page_size,exporters,export_wait_message,deleted,created_at,updated_at " +
            "FROM report_config WHERE id=#{id} AND deleted=0")
    ReportConfigEntity findById(@Param("id") Long id);

    @Insert("INSERT INTO report_config(id,name,procedure_name,page_size,exporters,export_wait_message,deleted,created_at,updated_at) " +
            "VALUES(#{id},#{name},#{procedureName},#{pageSize},#{exporters},#{exportWaitMessage},0,NOW(),NOW())")
    int insert(ReportConfigEntity entity);

    @Update("UPDATE report_config SET name=#{name},procedure_name=#{procedureName},page_size=#{pageSize}," +
            "exporters=#{exporters},export_wait_message=#{exportWaitMessage},updated_at=NOW() WHERE id=#{id} AND deleted=0")
    int updateById(ReportConfigEntity entity);

    @Update("UPDATE report_config SET deleted=1,updated_at=NOW() WHERE id=#{id} AND deleted=0")
    int logicDelete(@Param("id") Long id);
}
