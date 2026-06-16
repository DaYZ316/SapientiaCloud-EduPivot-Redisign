# 数据库迁移规范

## 概述

本项目使用 Flyway 管理数据库迁移。每个微服务有独立的迁移历史表，共享同一个 PostgreSQL 数据库。

## 服务与迁移表映射

| 服务              | 迁移表                                | 数据库      |
|-----------------|------------------------------------|----------|
| sc-auth         | flyway_schema_history_auth         | edupivot |
| sc-course       | flyway_schema_history_course       | edupivot |
| sc-notification | flyway_schema_history_notification | edupivot |
| sc-storage      | flyway_schema_history_storage      | edupivot |

## 迁移文件命名规范

`
V{yyyyMMdd}{序号}__{描述}.sql
`

示例：

- V2026061301__add_user_avatar.sql
- V2026061302__create_notification_table.sql

### 版本号规则

- yyyyMMdd：创建日期（如 20260613）
- 序号：同一天内的序号（01, 02, 03...）
- 描述：使用 snake_case 描述迁移内容

## 迁移文件编写规范

### 1. 必须幂等

所有迁移文件必须是幂等的，可以重复执行而不会报错。

`sql
-- 正确
CREATE TABLE IF NOT EXISTS users (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
name VARCHAR(100) NOT NULL
);

-- 错误
CREATE TABLE users (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
name VARCHAR(100) NOT NULL
);
`

### 2. 使用事务

Flyway 默认在事务中执行迁移（PostgreSQL 支持）。确保迁移可以在事务中回滚。

### 3. 避免数据迁移

数据迁移（如 UPDATE、INSERT）应该：

- 单独创建迁移文件
- 考虑大数据量的性能影响
- 添加必要的索引

### 4. 注释规范

`sql
-- V2026061301: 添加用户头像字段
-- @author DaYZ
-- @since 2026-06-13
`

## 操作流程

### 新增迁移

1. 创建迁移文件
   在对应服务的 db/migration 目录下创建文件

2. 测试迁移
   本地环境验证

3. 提交到 Git
   git add db/migration/V2026061301__add_user_avatar.sql
   git commit -m "feat(auth): add user avatar field"

4. 部署后验证
   flyway info
   flyway validate

### 修复迁移问题

#### Checksum 不匹配

使用 flyway repair 修复

#### 迁移失败

1. 检查 SQL 语法错误
2. 修复问题后重新部署
3. Flyway 会自动重试失败的迁移

#### 需要回滚

1. 不要修改旧的迁移文件
2. 创建新的反向迁移文件

## 禁止事项

### 绝对不要

1. 修改已部署的迁移文件
    - 一旦迁移应用到任何环境，文件不可变
    - 需要修改时，创建新版本迁移

2. 直接修改 flyway_schema_history 表
    - 使用 flyway repair 命令
    - 不要手动 SQL 操作

3. 在生产环境使用 flyway clean
    - clean 会删除所有数据
    - 仅在开发/测试环境使用

4. 跳过迁移版本
    - Flyway 按版本号顺序执行
    - 跳过版本会导致验证失败

5. 在迁移中使用 SELECT
    - 迁移应该是纯 DDL（CREATE、ALTER、DROP）
    - 数据查询应在应用代码中

## 故障排查

### 常见问题

#### 1. Checksum mismatch

原因：迁移文件在部署后被修改
解决：flyway repair

#### 2. Applied migration not resolved locally

原因：数据库中有迁移记录，但本地文件缺失
解决：恢复迁移文件，或使用 flyway repair 标记为 DELETED

#### 3. Migration already applied

原因：迁移已执行，尝试重新应用
解决：检查迁移是否幂等，或创建新版本

### 调试命令

flyway info - 查看迁移状态
flyway validate - 验证迁移
flyway repair - 修复迁移
flyway migrate - 执行迁移

## 最佳实践

1. 小步迭代：每个迁移只做一件事
2. 向后兼容：新迁移不能破坏旧版本应用
3. 测试先行：在测试环境验证迁移
4. 文档记录：复杂迁移添加注释
5. 监控告警：迁移失败时及时通知

---

最后更新：2026-06-13
维护者：DaYZ
