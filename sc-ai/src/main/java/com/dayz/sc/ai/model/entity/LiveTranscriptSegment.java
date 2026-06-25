package com.dayz.sc.ai.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@TableName("ai_live_transcript_segment")
public class LiveTranscriptSegment {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("summary_session_id")
    private UUID summarySessionId;

    @TableField("class_session_id")
    private UUID classSessionId;

    @TableField("sequence_no")
    private Integer sequenceNo;

    @TableField("speaker_id")
    private UUID speakerId;

    @TableField("text")
    private String text;

    @TableField("begin_time_ms")
    private Integer beginTimeMs;

    @TableField("end_time_ms")
    private Integer endTimeMs;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
