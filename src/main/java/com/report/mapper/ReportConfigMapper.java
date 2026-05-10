package com.report.mapper;

import com.report.domain.entity.ReportConfigEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReportConfigMapper {

    @Select("<script>" +
            "SELECT id,data_source_id,name,router_path,query_type,procedure_name,query_sql,count_sql,page_size,exporters,export_wait_message,query_enabled,download_enabled,deleted,created_at,updated_at " +
            "FROM report_config " +
            "WHERE deleted=0 " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (name LIKE CONCAT('%',#{keyword},'%') OR procedure_name LIKE CONCAT('%',#{keyword},'%') OR router_path LIKE CONCAT('%',#{keyword},'%')) " +
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
            "AND (name LIKE CONCAT('%',#{keyword},'%') OR procedure_name LIKE CONCAT('%',#{keyword},'%') OR router_path LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "</script>")
    long count(@Param("keyword") String keyword);

    @Select("SELECT id,data_source_id,name,router_path,query_type,procedure_name,query_sql,count_sql,page_size,exporters,export_wait_message,query_enabled,download_enabled,deleted,created_at,updated_at " +
            "FROM report_config WHERE id=#{id} AND deleted=0")
    ReportConfigEntity findById(@Param("id") Long id);

    @Select("SELECT id,data_source_id,name,router_path,query_type,procedure_name,query_sql,count_sql,page_size,exporters,export_wait_message,query_enabled,download_enabled,deleted,created_at,updated_at " +
            "FROM report_config WHERE router_path=#{routerPath} AND deleted=0")
    ReportConfigEntity findByRouterPath(@Param("routerPath") String routerPath);

    @Insert("INSERT INTO report_config(id,data_source_id,name,router_path,query_type,procedure_name,query_sql,count_sql,page_size,exporters,export_wait_message,query_enabled,download_enabled,deleted,created_at,updated_at) " +
            "VALUES(#{id},#{dataSourceId},#{name},#{routerPath},#{queryType},#{procedureName},#{querySql},#{countSql},#{pageSize},#{exporters},#{exportWaitMessage},#{queryEnabled},#{downloadEnabled},0,NOW(),NOW())")
    int insert(ReportConfigEntity entity);

    @Update("UPDATE report_config SET data_source_id=#{dataSourceId},name=#{name},router_path=#{routerPath},query_type=#{queryType},procedure_name=#{procedureName},query_sql=#{querySql},count_sql=#{countSql},page_size=#{pageSize}," +
            "exporters=#{exporters},export_wait_message=#{exportWaitMessage},query_enabled=#{queryEnabled},download_enabled=#{downloadEnabled},updated_at=NOW() WHERE id=#{id} AND deleted=0")
    int updateById(ReportConfigEntity entity);

    @Select("<script>" +
            "SELECT COUNT(1) FROM report_config WHERE deleted=0 AND router_path=#{routerPath} " +
            "<if test='excludeId != null'>AND id != #{excludeId}</if>" +
            "</script>")
    long countByRouterPath(@Param("routerPath") String routerPath, @Param("excludeId") Long excludeId);

    @Update("UPDATE report_config SET data_source_id=#{dataSourceId},updated_at=NOW() WHERE deleted=0 AND data_source_id IS NULL")
    int backfillDataSourceId(@Param("dataSourceId") Long dataSourceId);

    @Update("UPDATE report_config SET deleted=1,updated_at=NOW() WHERE id=#{id} AND deleted=0")
    int logicDelete(@Param("id") Long id);
}
