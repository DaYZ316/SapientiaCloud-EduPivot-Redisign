package com.dayz.sc.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dayz.sc.ai.config.PostgresJsonbMapTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * LiveSummarySnapshot.
 *
 * @author DaYZ
 */
@Getter
@Setter
@TableName(value = "ai_live_summary_snapshot", autoResultMap = true)
public class LiveSummarySnapshot {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("summary_session_id")
    private UUID summarySessionId;

    @TableField("class_session_id")
    private UUID classSessionId;

    @TableField("sequence_no")
    private Integer sequenceNo;

    @TableField("transcript_until_sequence_no")
    private Integer transcriptUntilSequenceNo;

    @TableField("overview")
    private String overview;

    @TableField(value = "payload", typeHandler = PostgresJsonbMapTypeHandler.class)
    private Map<String, Object> payload;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
