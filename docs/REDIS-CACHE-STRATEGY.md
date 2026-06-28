# Redis 缓存策略设计文档

## 一、当前状态

`MybatisUserAccountRepository` 采用 Cache-Aside 模式：

```text
读：先查 Redis -> 命中返回 -> 未命中查 DB -> 写入 Redis -> 返回
写：先写 DB -> 删除相关 Redis key -> 下次读取自动重建
```

### 1.1 用户缓存 Key

| Key 格式 | 数据 | TTL |
| --- | --- | --- |
| `auth:user:{userId}` | User 对象 | 30~35min |
| `auth:identity:{provider}:{providerUserId}` | UserIdentity 对象 | 30~35min |
| `auth:user:{userId}:providers` | OAuth provider 列表 | 30~35min |
| `auth:user:email:{email}` | User 对象 | 30~35min |
| `lock:{cacheKey}` | 缓存重建互斥锁 | 3s |

### 1.2 已完成能力

| 问题 | 状态 | 说明 |
| --- | --- | --- |
| 缓存穿透 | 已完成 | DB 无数据时写入 `__NULL__`，TTL 为 2 分钟。 |
| 缓存击穿 | 已完成 | 缓存未命中时使用 Redis 互斥锁重建热点 key。 |
| 缓存雪崩 | 已完成 | 正常数据 TTL 为 30 分钟基础值 + 0~5 分钟随机偏移。 |
| 写后缓存不一致 | 已完成 | 写 DB 后只删除缓存，不主动更新缓存。 |
| email 缓存不一致 | 已完成 | `saveUser()` 同时删除旧 email 与新 email 缓存。 |
| 手写缓存指标 | 已完成 | 使用 Micrometer counter 暴露 read/rebuild/lock/evict 指标。 |

### 1.3 暂未实施项

| 项目 | 优先级 | 暂缓原因 |
| --- | --- | --- |
| 缓存预热 | P2 | 需要先确认真实热点用户与启动流量模式。 |
| L1 Caffeine 本地缓存 | P2 | 多实例一致性复杂度更高，需性能数据证明收益。 |
| Pipeline 批量操作 | P2 | 当前用户缓存路径不是批量 Redis 操作瓶颈。 |
| 序列化优化 | P3 | 仍使用 `GenericJacksonJsonRedisSerializer`，待存储体积成为明确问题后再优化。 |

## 二、核心策略

### 2.1 Cache-Aside

读路径：

1. 读取 Redis。
2. 命中正常值时返回数据。
3. 命中空值标记时返回空结果。
4. 未命中时尝试获取 `lock:{cacheKey}`。
5. 获取锁成功后双查 Redis，仍未命中才查 DB 并写回 Redis。
6. 获取锁失败时等待 50ms 后重试，最多 3 次。
7. 重试耗尽后降级查 DB，保证业务可用。

写路径：

1. 写 PostgreSQL。
2. 删除用户 id、providers、旧 email、新 email、identity 等相关缓存。
3. 不主动写新缓存，避免并发读写覆盖新数据。

### 2.2 空值缓存

空值缓存用于降低错误 email、错误 OAuth identity、未绑定 providers 等请求反复打到 DB 的风险。

| 参数 | 值 | 说明 |
| --- | --- | --- |
| `NULL_MARKER` | `__NULL__` | 空值标记。 |
| `NULL_CACHE_TTL` | 2min | 避免空值长期占用内存，也降低短时间穿透。 |

### 2.3 互斥锁

互斥锁用于降低热点 key 过期瞬间的缓存击穿风险。

| 参数 | 值 | 说明 |
| --- | --- | --- |
| 锁 key | `lock:{cacheKey}` | 与被重建的缓存 key 一一对应。 |
| 锁 value | UUID 字符串 | 用于安全释放锁。 |
| 锁 TTL | 3s | 防止进程异常导致死锁。 |
| 等待间隔 | 50ms | 未拿到锁时短暂等待。 |
| 最大重试 | 3 次 | 总等待约 150ms，避免请求长时间阻塞。 |
| 释放方式 | Lua 比较 value 后删除 | 防止误删其他请求新获取的锁。 |

### 2.4 TTL 随机化

正常缓存 TTL 为 `30min + random(0~5min)`，用于分散 key 过期时间，降低同一时间大量请求回源 DB 的风险。

## 三、监控与告警

### 3.1 指标清单

Java 代码中的 Micrometer counter 会在 Prometheus 中转换为下划线格式并带 `_total` 后缀。

| Micrometer 指标 | Prometheus 指标 | 标签 | 说明 |
| --- | --- | --- | --- |
| `redis.auth.cache.read` | `redis_auth_cache_read_total` | `cache`, `result` | 缓存读取结果。 |
| `redis.auth.cache.rebuild` | `redis_auth_cache_rebuild_total` | `cache`, `result` | DB 回源与缓存重建结果。 |
| `redis.auth.cache.lock` | `redis_auth_cache_lock_total` | `cache`, `result` | 互斥锁获取、等待、释放、超时结果。 |
| `redis.auth.cache.evict` | `redis_auth_cache_evict_total` | `cache`, `result` | 写操作后的缓存删除结果。 |

标签约束：

- `cache` 只允许低基数字段：`user`、`email`、`identity`、`providers`。
- `result` 只允许状态枚举：`hit`、`negative_hit`、`miss`、`error`、`db_hit`、`db_miss`、`acquired`、`busy`、`released`、`release_error`、`timeout`、`success`。
- 禁止把真实 email、userId、providerUserId、Redis key 放入指标标签。

### 3.2 PromQL 示例

缓存命中率：

```promql
sum(rate(redis_auth_cache_read_total{job="sc-edupivot-backend",instance=~"sc-auth:.*",result=~"hit|negative_hit"}[5m]))
/
clamp_min(sum(rate(redis_auth_cache_read_total{job="sc-edupivot-backend",instance=~"sc-auth:.*",result=~"hit|negative_hit|miss"}[5m])), 1)
```

锁异常：

```promql
sum by (cache,result) (
  rate(redis_auth_cache_lock_total{job="sc-edupivot-backend",instance=~"sc-auth:.*",result=~"timeout|error|release_error"}[5m])
)
```

DB 回源：

```promql
sum by (cache,result) (
  rate(redis_auth_cache_rebuild_total{job="sc-edupivot-backend",instance=~"sc-auth:.*"}[5m])
)
```

Redis 读写删除异常：

```promql
sum(
  rate(redis_auth_cache_read_total{job="sc-edupivot-backend",instance=~"sc-auth:.*",result="error"}[5m])
  or rate(redis_auth_cache_rebuild_total{job="sc-edupivot-backend",instance=~"sc-auth:.*",result="error"}[5m])
  or rate(redis_auth_cache_evict_total{job="sc-edupivot-backend",instance=~"sc-auth:.*",result="error"}[5m])
  or rate(redis_auth_cache_lock_total{job="sc-edupivot-backend",instance=~"sc-auth:.*",result=~"error|release_error"}[5m])
)
```

### 3.3 Grafana 面板

新增面板文件：

```text
deploy/observability/grafana/dashboards/edupivot-auth-cache.json
```

该面板由现有 `dashboards.yaml` 自动加载到 `EduPivot` 文件夹，使用现有 Prometheus datasource UID：`PBFA97CFB590B2093`。

核心面板：

- Auth Cache Hit Ratio
- Auth Cache Read Rate
- Auth Cache Rebuild Rate
- Auth Cache Lock Rate
- Auth Cache Lock Timeout/Error
- Auth Cache Evict Rate

### 3.4 告警规则

告警配置文件：

```text
deploy/observability/grafana/provisioning/alerting/edupivot-alerts.yaml
```

新增告警：

| 告警 | 级别 | 触发条件 | 处理建议 |
| --- | --- | --- | --- |
| Auth Cache Lock Timeout | warning | 5 分钟内持续出现 `result="timeout"` | 检查热点 key、DB 查询耗时、Redis 延迟。 |
| Auth Cache Redis Error | warning | read/rebuild/lock/evict 持续出现 error | 检查 Redis 连接、序列化异常、网络波动。 |
| Auth Cache Hit Ratio Low | warning | 10 分钟窗口命中率低于 80%，且读流量足够 | 检查 TTL、缓存删除频率、异常 miss。 |

## 四、排障口径

### 4.1 `lock timeout` 增加

优先检查：

1. 是否存在热点用户或管理员账号被高频访问。
2. DB 查询是否变慢，导致持锁时间接近 3 秒。
3. Redis 是否存在网络抖动或命令延迟。

处理方向：

- 确认 DB 索引与慢查询。
- 必要时将热点账号纳入缓存预热候选。
- 不建议直接增大锁等待次数，避免放大请求延迟。

### 4.2 命中率下降

优先检查：

1. 是否有批量用户更新或频繁删除缓存。
2. 是否有大量不存在 email/OAuth identity 查询。
3. 是否有 Redis 异常导致读写失败。

处理方向：

- 对异常请求来源做限流或输入校验。
- 确认 `negative_hit` 是否正常吸收穿透流量。
- 如果确认为启动冷流量，再评估缓存预热。

### 4.3 Redis error 增加

优先检查：

1. Redis 服务可用性与连接数。
2. 应用到 Redis 的网络连通性。
3. 是否存在无法反序列化的历史缓存数据。

处理方向：

- 先确认业务是否已按设计降级到 DB。
- 对非法缓存 key 可删除后自动重建。
- 持续错误需要检查 Redis 配置、密码、连接池和序列化兼容性。

## 五、实施阶段

| 阶段 | 状态 | 内容 |
| --- | --- | --- |
| Phase 1 基础加固 | 已完成 | 空值缓存、TTL 随机化、写后删缓存、email 一致性。 |
| Phase 2 高可用 | 已完成 P1 | 互斥锁防击穿、手写缓存指标、Grafana 面板、告警。 |
| Phase 2 后续 | 未开始 | 缓存预热。 |
| Phase 3 性能优化 | 未开始 | L1 Caffeine、Pipeline、热点 key 探测、序列化优化。 |

## 六、验证命令

```bash
mvn -pl sc-auth -Dtest=MybatisUserAccountRepositoryTest test
mvn -pl sc-auth test
```

配置校验：

```bash
python -m json.tool deploy/observability/grafana/dashboards/edupivot-auth-cache.json
python -c "import yaml; yaml.safe_load(open('deploy/observability/grafana/provisioning/alerting/edupivot-alerts.yaml', encoding='utf-8'))"
```
