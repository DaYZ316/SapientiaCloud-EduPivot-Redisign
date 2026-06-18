package com.dayz.sc.course.model.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 存储在 edu_chapter.attachment_urls 中的章节附件元数据。
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public record ChapterAttachment(
        @JsonProperty("fileId") @Nullable UUID fileId,
        @JsonProperty("displayName") @Nullable String displayName,
        @JsonProperty("fileName") @Nullable String fileName,
        @JsonProperty("contentType") @Nullable String contentType,
        @JsonProperty("sizeBytes") @Nullable Long sizeBytes,
        @JsonProperty("legacyUrl") @Nullable String legacyUrl
) {
}
