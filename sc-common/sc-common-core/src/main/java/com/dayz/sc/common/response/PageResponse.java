package com.dayz.sc.common.response;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 轻量分页响应数据。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public record PageResponse<T>(
        List<T> records,
        long total,
        long page,
        long size
) {
    public PageResponse(@Nullable List<T> records, long total, long page, long size) {
        this.records = records == null ? List.of() : List.copyOf(records);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResponse<T> of(@Nullable List<T> records, long total, long page, long size) {
        return new PageResponse<>(records, total, page, size);
    }

    public static <T> PageResponse<T> empty(long page, long size) {
        return new PageResponse<>(List.of(), 0, page, size);
    }
}
