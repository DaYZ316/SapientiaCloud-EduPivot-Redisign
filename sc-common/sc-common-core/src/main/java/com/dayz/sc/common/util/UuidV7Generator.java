package com.dayz.sc.common.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID v7 生成器（RFC 9562）。
 * <p>
 * UUID v7 是时间有序的 UUID，前 48 位为毫秒级 Unix 时间戳，
 * 后续位为随机数。时间有序性对数据库索引友好。
 * <p>
 * 格式：
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                         unix_ts_ms                            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          unix_ts_ms           |  ver  |         rand_a        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |var|                       rand_b                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          rand_b                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * @author DaYZ
 * @since 2026-06-10
 */
public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7Generator() {
    }

    /**
     * 生成一个新的 UUID v7。
     *
     * @return 时间有序的 UUID v7
     */
    public static UUID generate() {
        long millis = System.currentTimeMillis();

        long randA = RANDOM.nextInt() & 0x0FFFL;  // 12 bits
        long randB = RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL;  // 62 bits

        long msb = (millis << 16)  // 48 bits timestamp
                | (0x7L << 12)     // version 7
                | randA;           // 12 bits random

        long lsb = (0x2L << 62)    // variant 10
                | randB;           // 62 bits random

        return new UUID(msb, lsb);
    }
}
