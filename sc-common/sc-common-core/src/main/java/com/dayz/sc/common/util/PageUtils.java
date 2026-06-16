package com.dayz.sc.common.util;

/**
 * 分页参数规范化工具类
 *
 * @author DaYZ
 * @since 2026-06-10
 */
public final class PageUtils {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private PageUtils() {
    }

    /**
     * 规范化页码，确保 >= 1。
     */
    public static int normalizePage(Long page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        return Math.max(page.intValue(), DEFAULT_PAGE);
    }

    /**
     * 规范化页码，确保 >= 1。
     */
    public static int normalizePage(int page) {
        return Math.max(page, DEFAULT_PAGE);
    }

    /**
     * 规范化每页大小，确保在 [1, MAX_SIZE] 范围内。
     */
    public static int normalizeSize(Long size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return (int) Math.min(size, MAX_SIZE);
    }

    /**
     * 规范化每页大小，确保在 [1, MAX_SIZE] 范围内。
     */
    public static int normalizeSize(int size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
