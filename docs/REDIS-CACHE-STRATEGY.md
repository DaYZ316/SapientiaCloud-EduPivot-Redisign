# Redis 缓存策略设计文档

## 一、现状分析

### 1.1 当前实现

`MybatisUserAccountRepository` 采用 **Cache-Aside（旁路缓存）** 模式：

```
读: 先查 Redis → 命中返回 → 未命中查 DB → 写入 Redis → 返回
写: 先写 DB → 再写 Redis
```

**缓存 Key 设计：**

| Key 格式 | 数据 | TTL |
|----------|------|-----|
| `auth:user:{userId}` | User 对象 | 30min |
| `auth:identity:{provider}:{providerUserId}` | UserIdentity 对象 | 30min |
| `auth:user:{userId}:providers` | List\<OauthProvider\> | 30min |
| `auth:user:email:{email}` | User 对象 | 30min |

**安全措施：**
- Redis 异常静默吞没，降级到数据库
- 写操作后主动更新缓存

### 1.2 存在问题

| 问题 | 说明 | 风险等级 |
|------|------|----------|
| **缓存穿透** | 查询不存在的数据（如错误 email），每次请求都打到 DB | 🔴 高 |
| **缓存击穿** | 热点 key（如管理员账号）过期瞬间，并发请求全部打到 DB | 🔴 高 |
| **缓存雪崩** | 所有 key TTL 相同（30min），同时过期时 DB 压力骤增 | 🟡 中 |
| **缓存不一致** | `saveUser()` 后更新了 `auth:user:{id}`，但 `auth:user:email:{email}` 仍是旧数据 | 🟡 中 |
| **无缓存预热** | 系统启动后首次请求全部打到 DB | 🟡 中 |
| **无分布式锁** | 并发读同一 key 时，多个请求同时查 DB | 🟡 中 |
| **序列化开销** | `GenericJacksonJsonRedisSerializer` 带类型信息，存储体积大 | 🟢 低 |

---

## 二、缓存策略设计

### 2.1 核心原则

```
1. 缓存是 DB 的加速层，不是数据源 — DB 是唯一可信数据源
2. 宁可少缓存，不可多缓存 — 缓存越多，一致性越难维护
3. 读多写少的数据才值得缓存 — 高频写入的数据缓存收益低
4. 缓存失效比缓存更新更安全 — 删除缓存让下次读取重建
```

### 2.2 Cache-Aside 模式（保持）

这是最适合当前场景的模式，保持不变，但需要改进实现细节。

```
读操作:
  1. 读 Redis
  2. 命中 → 返回
  3. 未命中 → 查 DB
  4. DB 有数据 → 写入 Redis（带 TTL）→ 返回
  5. DB 无数据 → 写入空值（防穿透，短 TTL）→ 返回空

写操作:
  1. 先写 DB
  2. 删除缓存（不是更新缓存）
  3. 下次读取时自动重建
```

**关键变更：写操作从"更新缓存"改为"删除缓存"。**

原因：更新缓存在并发场景下可能导致数据不一致。删除缓存更安全，下次读取时自动重建。

---

## 三、三大问题解决方案

### 3.1 缓存穿透（Cache Penetration）

**场景：** 查询不存在的数据（如 `findByEmail("not-exist@email.com")`），缓存永远未命中，每次请求都打到 DB。

**方案：缓存空值 + 布隆过滤器**

```java
// 方案 A：缓存空值（简单有效）
@Override
public Optional<User> findByEmail(String email) {
    String cacheKey = emailKey(email);

    // 1. 查缓存
    Object cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        if (cached instanceof User user) {
            return Optional.of(user);
        }
        // cached 是空值标记 → 直接返回空
        return Optional.empty();
    }

    // 2. 查 DB
    Optional<User> user = Optional.ofNullable(
        userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getEmail, email)));

    // 3. 写缓存（存在 → 正常 TTL；不存在 → 短 TTL 空值标记）
    if (user.isPresent()) {
        redisTemplate.opsForValue().set(cacheKey, user.get(), CACHE_TTL);
    } else {
        redisTemplate.opsForValue().set(cacheKey, NULL_MARKER, NULL_CACHE_TTL);
    }

    return user;
}
```

**参数设计：**

| 参数 | 值 | 说明 |
|------|-----|------|
| `CACHE_TTL` | 30 min | 正常数据 TTL |
| `NULL_CACHE_TTL` | 2 min | 空值标记 TTL（不宜太长，防止长期占用内存） |
| `NULL_MARKER` | `new Object()` 或特定字符串 | 空值标记，区别于正常 User 对象 |

**方案 B：布隆过滤器（适合数据量大的场景）**

在 Redis 中维护一个布隆过滤器，预加载所有存在的 email 或 userId。查询前先过滤，拦截不存在的 key。

当前场景数据量不大，方案 A 已足够。

---

### 3.2 缓存击穿（Cache Breakdown）

**场景：** 热点 key（如管理员账号、高频查询的课程信息）过期瞬间，大量并发请求同时打到 DB。

**方案：分布式锁（Mutex Lock）**

```java
@Override
public Optional<User> findUser(UUID userId) {
    String cacheKey = userKey(userId);

    // 1. 查缓存
    Optional<User> cached = getCached(cacheKey, User.class);
    if (cached.isPresent()) {
        return cached;
    }

    // 2. 缓存未命中 → 尝试获取分布式锁
    String lockKey = "lock:" + cacheKey;
    boolean locked = tryLock(lockKey, LOCK_TIMEOUT);

    if (locked) {
        try {
            // 双重检查：获取锁后再查一次缓存
            cached = getCached(cacheKey, User.class);
            if (cached.isPresent()) {
                return cached;
            }

            // 3. 查 DB → 写缓存
            User user = userMapper.selectById(userId);
            putCached(cacheKey, user);
            return Optional.ofNullable(user);
        } finally {
            releaseLock(lockKey);
        }
    } else {
        // 4. 未获取到锁 → 短暂等待后重试
        Thread.sleep(50);
        return findUser(userId); // 递归重试
    }
}
```

**分布式锁实现（Redis SETNX）：**

```java
private boolean tryLock(String key, long timeoutMs) {
    Boolean result = redisTemplate.opsForValue()
        .setIfAbsent(key, "1", Duration.ofMillis(timeoutMs));
    return Boolean.TRUE.equals(result);
}

private void releaseLock(String key) {
    redisTemplate.delete(key);
}
```

**参数设计：**

| 参数 | 值 | 说明 |
|------|-----|------|
| `LOCK_TIMEOUT` | 3 s | 锁超时时间，防止死锁 |
| `RETRY_SLEEP` | 50 ms | 重试等待时间 |
| `MAX_RETRIES` | 3 | 最大重试次数 |

**优化：本地缓存（Caffeine）作为 L1 缓存**

对于极高频读取的数据（如当前登录用户信息），可以增加本地缓存作为 L1 层：

```
请求 → L1 本地缓存 (Caffeine, 1min TTL) → L2 Redis (30min TTL) → DB
```

本地缓存命中率高、零网络开销，但需要注意多实例间的一致性问题。适合读多写少、对一致性要求不高的数据。

---

### 3.3 缓存雪崩（Cache Avalanche）

**场景：** 大量 key 同时过期，瞬间请求全部打到 DB。

**方案：TTL 随机化**

```java
private void putCached(String key, Object value) {
    if (value == null) {
        return;
    }
    // TTL 加随机偏移，防止同时过期
    Duration ttl = CACHE_TTL.plus(Duration.ofSeconds(
        ThreadLocalRandom.current().nextLong(0, CACHE_JITTER.getSeconds())));
    redisTemplate.opsForValue().set(key, value, ttl);
}
```

**参数设计：**

| 参数 | 值 | 说明 |
|------|-----|------|
| `CACHE_TTL` | 30 min | 基础 TTL |
| `CACHE_JITTER` | 5 min | 随机偏移范围 |
| 实际 TTL | 30~35 min | 均匀分散过期时间 |

---

## 四、缓存一致性策略

### 4.1 问题场景

```
时间线:
  T1: 线程 A 读取 user → 缓存未命中 → 查 DB 得到 v1
  T2: 线程 B 更新 user → 写 DB 得到 v2 → 更新缓存 v2
  T3: 线程 A 将 v1 写入缓存（覆盖了 v2）
  结果: 缓存中是 v1，DB 中是 v2 → 数据不一致
```

### 4.2 解决方案：延迟双删

```
写操作:
  1. 先写 DB
  2. 删除缓存
  3. 延迟 500ms
  4. 再次删除缓存（清除并发读可能写入的旧数据）
```

```java
@Override
public User saveUser(User user) {
    int updated = userMapper.updateById(user);
    if (updated == 0) {
        userMapper.insert(user);
    }

    // 延迟双删
    deleteCached(userKey(user.getId()));
    deleteCached(emailKey(user.getEmail()));

    // 异步延迟再删一次
    scheduler.schedule(() -> {
        deleteCached(userKey(user.getId()));
        deleteCached(emailKey(user.getEmail()));
    }, Duration.ofMillis(500));

    return user;
}
```

### 4.3 更简单的方案：只删不更新

对于当前场景，最简单且最安全的方式：

```java
@Override
public User saveUser(User user) {
    int updated = userMapper.updateById(user);
    if (updated == 0) {
        userMapper.insert(user);
    }
    // 只删除缓存，不主动更新
    // 下次读取时自动从 DB 加载最新数据
    evictUserCache(user.getId(), user.getEmail());
    return user;
}

private void evictUserCache(UUID userId, String email) {
    deleteCached(userKey(userId));
    deleteCached(emailKey(email));
    // email 为空时不删（避免误删）
}
```

**选择建议：当前场景用"只删不更新"足够简单可靠。**

---

## 五、缓存 Key 规范

### 5.1 命名规范

```
{业务}:{模块}:{维度}:{标识}

示例:
  auth:user:{userId}                    — 用户信息
  auth:user:email:{email}               — 邮箱查询
  auth:identity:{provider}:{providerId} — OAuth 身份
  course:{courseId}                      — 课程信息
  course:{courseId}:chapters             — 课程章节列表
  notification:unread:{userId}          — 未读通知数
  ratelimit:{endpoint}:{ip}             — 限流计数
  auth:blacklist:{jti}                  — Token 黑名单
  auth:refresh:{token}                  — Refresh Token
  lock:{key}                            — 分布式锁
```

### 5.2 Key 长度控制

Redis key 过长会浪费内存。建议：
- 总长度不超过 128 字节
- 使用缩写：`auth:` 而不是 `authentication:`
- UUID 去掉短横线：`uuid.replace("-", "")` 可节省 4 字节

---

## 六、缓存预热策略

### 6.1 启动预热

系统启动后，将热点数据提前加载到 Redis：

```java
@Component
public class CacheWarmer implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        // 预热管理员账号
        warmAdminUsers();
        // 预热门诊课程（如果有的话）
        warmPopularCourses();
    }

    private void warmAdminUsers() {
        List<User> admins = userMapper.selectList(
            new LambdaQueryWrapper<User>().eq(User::getRole, 0));
        for (User admin : admins) {
            redisTemplate.opsForValue().set(
                userKey(admin.getId()), admin, CACHE_TTL);
        }
    }
}
```

### 6.2 定时预热

对于有规律的访问模式（如每天上课前），可以定时预热：

```java
@Scheduled(cron = "0 0 7 * * ?") // 每天早上 7 点
public void warmCourseCache() {
    // 预热当天有课的课程数据
}
```

---

## 七、监控与告警

### 7.1 关键指标

| 指标 | 说明 | 告警阈值 |
|------|------|----------|
| 缓存命中率 | `hits / (hits + misses)` | < 80% |
| Redis 内存使用率 | `used_memory / maxmemory` | > 80% |
| Redis 连接数 | `connected_clients` | > 100 |
| 慢查询 | `slowlog get` | > 10ms |
| Key 过期数 | `expired_keys` 监控雪崩 | 突增 |

### 7.2 日志记录

```java
private <T> Optional<T> getCached(String key, Class<T> type) {
    try {
        Object value = redisTemplate.opsForValue().get(key);
        if (type.isInstance(value)) {
            cacheMetrics.recordHit(key);
            return Optional.of(type.cast(value));
        }
        cacheMetrics.recordMiss(key);
    } catch (RuntimeException e) {
        cacheMetrics.recordError(key);
        log.warn("Redis 读取失败，降级到 DB: key={}", key, e);
    }
    return Optional.empty();
}
```

---

## 八、实施计划

### Phase 1: 基础加固（当前迭代）

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| 缓存空值防穿透 | P0 | 0.5d |
| TTL 随机化防雪崩 | P0 | 0.5d |
| 写操作改为"只删不更新" | P0 | 0.5d |
| 修复 email 缓存不一致 | P0 | 0.5d |

### Phase 2: 高可用（下一迭代）

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| 分布式锁防击穿 | P1 | 1d |
| 缓存命中率监控 | P1 | 1d |
| 缓存预热 | P2 | 0.5d |

### Phase 3: 性能优化（后续）

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| 本地缓存 L1（Caffeine） | P2 | 1d |
| Pipeline 批量操作 | P2 | 0.5d |
| 热点 key 探测 | P3 | 1d |

---

## 九、缓存数据分级

不同数据采用不同的缓存策略：

| 数据类型 | TTL | 策略 | 示例 |
|----------|-----|------|------|
| 用户基础信息 | 30min | Cache-Aside + 空值缓存 | User, UserProfile |
| OAuth 身份 | 30min | Cache-Aside | UserIdentity |
| 课程信息 | 15min | Cache-Aside | Course, Chapter |
| 通知计数 | 5min | Cache-Aside | UnreadCount |
| 会话状态 | 7d | 直接存储 | RefreshToken |
| 限流计数 | 滑动窗口 | 直接存储 | RateLimit |
| Token 黑名单 | Token 剩余 TTL | 直接存储 | Blacklist |
| 搜索结果 | 2min | Cache-Aside（短 TTL） | CourseList |

**原则：变化越频繁的数据，TTL 越短。**
