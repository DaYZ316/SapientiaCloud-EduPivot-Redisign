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

        // 12 bits random
        long randA = RANDOM.nextInt() & 0x0FFFL;
        // 62 bits random
        long randB = RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL;

        // 48 bits timestamp + version 7 + 12 bits random
        long msb = (millis << 16)
                | (0x7L << 12)
                | randA;

        // variant 10 + 62 bits random
        long lsb = (0x2L << 62)
                | randB;

        return new UUID(msb, lsb);
    }
}
