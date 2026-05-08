package com.dayz.aeroverse.common.response;

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
    public PageResponse {
        records = records == null ? List.of() : List.copyOf(records);
    }

    public static <T> PageResponse<T> of(List<T> records, long total, long page, long size) {
        return new PageResponse<>(records, total, page, size);
    }

    public static <T> PageResponse<T> empty(long page, long size) {
        return new PageResponse<>(List.of(), 0, page, size);
    }
}
