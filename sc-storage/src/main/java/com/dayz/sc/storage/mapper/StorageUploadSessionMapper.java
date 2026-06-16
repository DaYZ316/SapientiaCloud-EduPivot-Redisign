package com.dayz.sc.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.storage.model.entity.StorageUploadSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

/**
 * MyBatis-Plus Mapper 接口。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Mapper
public interface StorageUploadSessionMapper extends BaseMapper<StorageUploadSession> {

    @Update("<script>" +
            "UPDATE storage_upload_session SET status = #{status} " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int batchUpdateStatus(@Param("ids") List<UUID> ids,
                          @Param("status") String status);
}
