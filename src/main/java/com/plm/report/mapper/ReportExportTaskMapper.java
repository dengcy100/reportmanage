package com.plm.report.mapper;

import com.plm.report.domain.entity.ReportExportTaskEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReportExportTaskMapper {

    @Insert("INSERT INTO report_export_task(id,report_id,request_digest,status,request_json,file_name,file_path,error_message,expires_at,created_at,updated_at) " +
            "VALUES(#{id},#{reportId},#{requestDigest},#{status},#{requestJson},#{fileName},#{filePath},#{errorMessage},#{expiresAt},NOW(),NOW())")
    int insert(ReportExportTaskEntity entity);

    @Select("SELECT id,report_id,request_digest,status,request_json,file_name,file_path,error_message,expires_at,created_at,updated_at " +
            "FROM report_export_task WHERE report_id=#{reportId} AND request_digest=#{requestDigest} AND status IN ('PENDING','RUNNING') ORDER BY created_at DESC LIMIT 1")
    ReportExportTaskEntity findRunningByDigest(@Param("reportId") Long reportId, @Param("requestDigest") String requestDigest);

    @Select("SELECT id,report_id,request_digest,status,request_json,file_name,file_path,error_message,expires_at,created_at,updated_at " +
            "FROM report_export_task WHERE id=#{id} AND report_id=#{reportId}")
    ReportExportTaskEntity findById(@Param("id") Long id, @Param("reportId") Long reportId);

    @Update("UPDATE report_export_task SET status=#{status},error_message=#{errorMessage},updated_at=NOW() WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("errorMessage") String errorMessage);

    @Update("UPDATE report_export_task SET status='SUCCESS',file_name=#{fileName},file_path=#{filePath},expires_at=#{expiresAt},error_message='',updated_at=NOW() WHERE id=#{id}")
    int markSuccess(@Param("id") Long id,
                    @Param("fileName") String fileName,
                    @Param("filePath") String filePath,
                    @Param("expiresAt") java.time.LocalDateTime expiresAt);

    @Update("UPDATE report_export_task SET status='FAILED',error_message=#{errorMessage},updated_at=NOW() WHERE id=#{id}")
    int markFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);

    @Update("UPDATE report_export_task SET status='EXPIRED',updated_at=NOW() WHERE id=#{id} AND status='SUCCESS'")
    int markExpired(@Param("id") Long id);
}
