package com.dayz.sc.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.ai.model.entity.LiveSummarySnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

/**
 * LiveSummarySnapshotMapper.
 *
 * @author DaYZ
 */
@Mapper
public interface LiveSummarySnapshotMapper extends BaseMapper<LiveSummarySnapshot> {

    @Select("""
            SELECT COALESCE(MAX(sequence_no), 0)
            FROM ai_live_summary_snapshot
            WHERE summary_session_id = #{summarySessionId}
            """)
    int maxSequenceNoIncludingDeleted(@Param("summarySessionId") UUID summarySessionId);

    @Select("""
            SELECT snapshot.*
            FROM ai_live_summary_snapshot snapshot
            JOIN ai_live_summary_session summary_session
              ON summary_session.id = snapshot.summary_session_id
            WHERE summary_session.course_id = #{courseId}
              AND snapshot.deleted = CAST(0 AS SMALLINT)
              AND summary_session.deleted = CAST(0 AS SMALLINT)
            ORDER BY snapshot.created_at DESC
            LIMIT #{limit}
            """)
    @Results(id = "liveSummarySnapshotResultMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "summary_session_id", property = "summarySessionId"),
            @Result(column = "class_session_id", property = "classSessionId"),
            @Result(column = "sequence_no", property = "sequenceNo"),
            @Result(column = "transcript_until_sequence_no", property = "transcriptUntilSequenceNo"),
            @Result(column = "overview", property = "overview"),
            @Result(column = "payload", property = "payload",
                    typeHandler = com.dayz.sc.ai.config.PostgresJsonbMapTypeHandler.class),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "deleted", property = "deleted")
    })
    List<LiveSummarySnapshot> findRecentActiveByCourseId(@Param("courseId") UUID courseId,
                                                         @Param("limit") int limit);

    @Update("""
            UPDATE ai_live_summary_snapshot
            SET deleted = CAST(1 AS SMALLINT)
            WHERE id = #{id}
              AND deleted = CAST(0 AS SMALLINT)
            """)
    int softDeleteById(@Param("id") UUID id);
}
