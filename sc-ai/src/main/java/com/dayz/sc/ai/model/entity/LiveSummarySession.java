package com.dayz.sc.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * LiveSummarySession.
 *
 * @author DaYZ
 */
@Getter
@Setter
@TableName("ai_live_summary_session")
public class LiveSummarySession {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("class_session_id")
    private UUID classSessionId;

    @TableField("course_id")
    private UUID courseId;

    @TableField("teacher_id")
    private UUID teacherId;

    @TableField("started_by")
    private UUID startedBy;

    @TableField("status")
    private String status;

    @TableField("compressed_state")
    private String compressedState;

    @TableField("last_summarized_sequence_no")
    private Integer lastSummarizedSequenceNo;

    @TableField("started_at")
    private Instant startedAt;

    @TableField("stopped_at")
    private Instant stoppedAt;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
