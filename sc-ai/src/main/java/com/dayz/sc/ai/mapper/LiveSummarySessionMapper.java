package com.dayz.sc.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.ai.model.entity.LiveSummarySession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * LiveSummarySessionMapper.
 *
 * @author DaYZ
 */
@Mapper
public interface LiveSummarySessionMapper extends BaseMapper<LiveSummarySession> {

    @Select("""
            <script>
            SELECT summary_session.*
            FROM ai_live_summary_session summary_session
            JOIN (
              SELECT summary_session_id, MAX(created_at) latest_snapshot_at
              FROM ai_live_summary_snapshot
              WHERE deleted = CAST(0 AS SMALLINT)
              GROUP BY summary_session_id
            ) latest ON latest.summary_session_id = summary_session.id
            WHERE summary_session.deleted = CAST(0 AS SMALLINT)
            <if test="courseIds != null and courseIds.size() &gt; 0">
              AND summary_session.course_id IN
              <foreach collection="courseIds" item="courseId" open="(" separator="," close=")">
                #{courseId}
              </foreach>
            </if>
            ORDER BY latest.latest_snapshot_at DESC, summary_session.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<LiveSummarySession> findWithSnapshotsByCourseIds(@Param("courseIds") List<UUID> courseIds,
                                                          @Param("limit") int limit,
                                                          @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ai_live_summary_session summary_session
            WHERE summary_session.deleted = CAST(0 AS SMALLINT)
              AND EXISTS (
                SELECT 1
                FROM ai_live_summary_snapshot snapshot
                WHERE snapshot.summary_session_id = summary_session.id
                  AND snapshot.deleted = CAST(0 AS SMALLINT)
              )
            <if test="courseIds != null and courseIds.size() &gt; 0">
              AND summary_session.course_id IN
              <foreach collection="courseIds" item="courseId" open="(" separator="," close=")">
                #{courseId}
              </foreach>
            </if>
            </script>
            """)
    long countWithSnapshotsByCourseIds(@Param("courseIds") List<UUID> courseIds);
}
