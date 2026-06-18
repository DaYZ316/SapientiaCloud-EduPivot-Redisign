package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 章节附件视图对象
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public record ChapterAttachmentVO(
        @JsonProperty("fileId") @Nullable UUID fileId,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("fileName") String fileName,
        @JsonProperty("contentType") @Nullable String contentType,
        @JsonProperty("sizeBytes") @Nullable Long sizeBytes,
        @JsonProperty("url") @Nullable String url
) {
}
