package com.dayz.sc.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.storage.model.entity.StorageObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MyBatis-Plus Mapper 接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Mapper
public interface StorageObjectMapper extends BaseMapper<StorageObject> {

    /**
     * 批量更新存储对象的删除状态。
     *
     * @param ids       存储对象ID列表
     * @param deleted   删除状态（0=正常, 1=已删除）
     * @param deletedAt 删除时间
     * @return 受影响的行数
     */
    @Update("<script>" +
            "UPDATE storage_object SET deleted = #{deleted}, deleted_at = #{deletedAt} " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int batchUpdateDeleted(@Param("ids") List<UUID> ids,
                           @Param("deleted") int deleted,
                           @Param("deletedAt") Instant deletedAt);
}
