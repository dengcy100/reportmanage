package com.report.mapper;

import com.report.domain.dto.ReportDataSourceOptionVO;
import com.report.domain.entity.ReportDataSourceEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReportDataSourceMapper {

    @Select("<script>" +
            "SELECT id,name,type,host,port,database_name,username,password_encrypted,deleted,created_at,updated_at " +
            "FROM report_data_source " +
            "WHERE deleted=0 " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (name LIKE CONCAT('%',#{keyword},'%') " +
            "OR host LIKE CONCAT('%',#{keyword},'%') " +
            "OR database_name LIKE CONCAT('%',#{keyword},'%') " +
            "OR username LIKE CONCAT('%',#{keyword},'%') " +
            "OR type LIKE CONCAT('%',#{keyword},'%')) " +
            "</if> " +
            "ORDER BY updated_at DESC " +
            "LIMIT #{offset},#{limit}" +
            "</script>")
    List<ReportDataSourceEntity> pageList(@Param("keyword") String keyword,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(1) FROM report_data_source WHERE deleted=0 " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (name LIKE CONCAT('%',#{keyword},'%') " +
            "OR host LIKE CONCAT('%',#{keyword},'%') " +
            "OR database_name LIKE CONCAT('%',#{keyword},'%') " +
            "OR username LIKE CONCAT('%',#{keyword},'%') " +
            "OR type LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "</script>")
    long count(@Param("keyword") String keyword);

    @Select("SELECT id,name,type,host,port,database_name,username,password_encrypted,deleted,created_at,updated_at " +
            "FROM report_data_source WHERE id=#{id} AND deleted=0")
    ReportDataSourceEntity findById(@Param("id") Long id);

    @Select("SELECT id,name,type,host,port,database_name,username,password_encrypted,deleted,created_at,updated_at " +
            "FROM report_data_source WHERE name=#{name} AND deleted=0 LIMIT 1")
    ReportDataSourceEntity findByName(@Param("name") String name);

    @Select("SELECT id,name,type FROM report_data_source WHERE deleted=0 AND type='MYSQL' ORDER BY updated_at DESC")
    List<ReportDataSourceOptionVO> listMysqlOptions();

    @Select("<script>" +
            "SELECT id,name,type,host,port,database_name,username,password_encrypted,deleted,created_at,updated_at " +
            "FROM report_data_source WHERE deleted=0 AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<ReportDataSourceEntity> findActiveByIds(@Param("ids") List<Long> ids);

    @Select("SELECT COUNT(1) FROM report_config WHERE deleted=0 AND data_source_id=#{id}")
    long countReferencedByReports(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM report_data_source WHERE deleted=0 AND name=#{name} AND id<>#{excludeId}")
    long countByNameExcludeId(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Insert("INSERT INTO report_data_source(id,name,type,host,port,database_name,username,password_encrypted,deleted,created_at,updated_at) " +
            "VALUES(#{id},#{name},#{type},#{host},#{port},#{databaseName},#{username},#{passwordEncrypted},0,NOW(),NOW())")
    int insert(ReportDataSourceEntity entity);

    @Update("UPDATE report_data_source SET name=#{name},type=#{type},host=#{host},port=#{port}," +
            "database_name=#{databaseName},username=#{username},password_encrypted=#{passwordEncrypted}," +
            "deleted=0,updated_at=NOW() WHERE id=#{id} AND deleted=0")
    int updateById(ReportDataSourceEntity entity);

    @Update("UPDATE report_data_source SET deleted=1,updated_at=NOW() WHERE id=#{id} AND deleted=0")
    int logicDelete(@Param("id") Long id);
}
