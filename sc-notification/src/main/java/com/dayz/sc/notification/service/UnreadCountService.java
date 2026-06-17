package com.dayz.sc.notification.service;

import com.dayz.sc.notification.model.vo.UnreadCountVO;
import com.dayz.sc.notification.repository.NotificationReadStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 未读通知计数管理（Redis Hash 原子计数）。
 * <p>
 * Key: notification:unread:{userId}
 * Fields: total, system, teaching
 * TTL: 5-10 分钟（随机抖动），过期后由 getOrInitFromDb 从 DB 重建。
 * 缓存击穿防护：单飞锁（Redis SET NX），同一 userId 同时只有一个请求回源 DB
 *
 * @author DaYZ
 * @since 2026-06-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnreadCountService {

    private static final String KEY_PREFIX = "notification:unread:";
    private static final String LOCK_PREFIX = "lock:notification:unread:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration TTL_JITTER = Duration.ofMinutes(5);
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);
    private static final long POLL_INTERVAL_MS = 100;
    private static final RedisScript<@NonNull Long> INCREMENT_IF_PRESENT_SCRIPT = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return 0
            end
            redis.call('HINCRBY', KEYS[1], 'total', 1)
            redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);
    private static final RedisScript<@NonNull Long> DECREMENT_IF_PRESENT_SCRIPT = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return 0
            end
            local total = redis.call('HINCRBY', KEYS[1], 'total', -1)
            if total < 0 then
              redis.call('HSET', KEYS[1], 'total', 0)
            end
            local typed = redis.call('HINCRBY', KEYS[1], ARGV[1], -1)
            if typed < 0 then
              redis.call('HSET', KEYS[1], ARGV[1], 0)
            end
            return 1
            """, Long.class);
    private static final RedisScript<@NonNull Long> RELEASE_LOCK_SCRIPT = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);
    /**
     * 最多等 3s
     */
    private static final int MAX_POLL_ROUNDS = 30;
    private final StringRedisTemplate redisTemplate;

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }

    private String lockKey(UUID userId) {
        return LOCK_PREFIX + userId;
    }

    /**
     * 带随机抖动的 TTL，防止雪崩。
     */
    private Duration ttlWithJitter() {
        long jitterSeconds = ThreadLocalRandom.current().nextLong(TTL_JITTER.getSeconds());
        return TTL.plusSeconds(jitterSeconds);
    }

    /**
     * 新通知到达：total +1，对应 type +1。
     */
    public UnreadCountVO increment(UUID userId, int type) {
        String key = key(userId);
        Long updated = redisTemplate.execute(
                INCREMENT_IF_PRESENT_SCRIPT,
                List.of(key),
                typeField(type),
                String.valueOf(ttlWithJitter().toSeconds()));
        if (!Long.valueOf(1L).equals(updated)) {
            return null;
        }
        return readFromHash(key);
    }

    /**
     * 标记单条已读：total -1，对应 type -1（不低于 0）。
     */
    public void decrement(UUID userId, int type) {
        String key = key(userId);
        redisTemplate.execute(
                DECREMENT_IF_PRESENT_SCRIPT,
                List.of(key),
                typeField(type));
    }

    /**
     * 全部已读 / 删除全部：直接删除 key。
     */
    public void reset(UUID userId) {
        redisTemplate.delete(key(userId));
    }

    /**
     * 读取当前未读计数（Redis 有数据时直接读，无数据返回 null）。
     */
    public UnreadCountVO getUnreadCount(UUID userId) {
        String key = key(userId);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return null;
        }
        return readFromHash(key);
    }

    /**
     * 读取未读计数，Redis 无数据时从 DB 初始化（带单飞锁防缓存击穿）。
     */
    public UnreadCountVO getOrInitFromDb(UUID userId, NotificationReadStatusRepository repo) {
        String key = key(userId);
        long start = System.currentTimeMillis();

        // Redis 命中 → 直接返回
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            UnreadCountVO cached = readFromHash(key);
            log.debug("unread-count redis hit: userId={}, cost={}ms", userId, System.currentTimeMillis() - start);
            return cached;
        }

        // Redis miss → 尝试拿单飞锁
        String lock = lockKey(userId);
        String lockValue = UUID.randomUUID().toString();
        boolean locked = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lock, lockValue, LOCK_TTL));

        if (locked) {
            try {
                // 双重检查：拿锁后再读一次 Redis
                if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                    return readFromHash(key);
                }
                // 回源 DB
                return loadFromDb(userId, repo, start);
            } finally {
                releaseLock(lock, lockValue);
            }
        }

        // 未拿到锁 → 轮询等待其他线程回源结果
        for (int i = 0; i < MAX_POLL_ROUNDS; i++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                UnreadCountVO result = readFromHash(key);
                log.debug("unread-count lock-wait hit: userId={}, polled={}ms", userId, (i + 1) * POLL_INTERVAL_MS);
                return result;
            }
        }

        // 等待超时 → 最后兜底直接回源（不再抢锁）
        log.warn("unread-count lock-wait timeout, fallback to DB: userId={}", userId);
        return loadFromDb(userId, repo, start);
    }

    private UnreadCountVO loadFromDb(UUID userId, NotificationReadStatusRepository repo, long start) {
        Map<String, Long> counts = repo.countUnreadAll(userId);
        long total = counts.getOrDefault("totalCount", 0L);
        long system = counts.getOrDefault("systemCount", 0L);
        long teaching = counts.getOrDefault("teachingCount", 0L);

        var hashOps = redisTemplate.opsForHash();
        hashOps.put(key(userId), "total", String.valueOf(total));
        hashOps.put(key(userId), "system", String.valueOf(system));
        hashOps.put(key(userId), "teaching", String.valueOf(teaching));
        redisTemplate.expire(key(userId), ttlWithJitter());

        log.info("unread-count db load: userId={}, total={}, system={}, teaching={}, cost={}ms",
                userId, total, system, teaching, System.currentTimeMillis() - start);
        return new UnreadCountVO(total, system, teaching);
    }

    private void releaseLock(String lock, String lockValue) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lock), lockValue);
    }

    private UnreadCountVO readFromHash(String key) {
        var hashOps = redisTemplate.opsForHash();
        long total = parseLong(hashOps.get(key, "total"));
        long system = parseLong(hashOps.get(key, "system"));
        long teaching = parseLong(hashOps.get(key, "teaching"));
        return new UnreadCountVO(total, system, teaching);
    }

    /**
     * 原子递增/递减，结果不低于 0。
     */
    private String typeField(int type) {
        return type == 1 ? "system" : "teaching";
    }

    private long parseLong(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
