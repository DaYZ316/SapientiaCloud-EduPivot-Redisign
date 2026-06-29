# 数据库迁移规范

适用版本：`2.0.0` 正式版

## 概述

本项目使用 Flyway 管理数据库迁移。所有后端服务共享同一个 PostgreSQL 数据库和 schema，但每个服务使用独立的 Flyway
历史表。Flyway 的 versioned migration 只执行一次，执行记录由对应的 `flyway_schema_history_*` 表保存。

## 服务与迁移表映射

| 服务              | 表前缀        | 迁移表                                  | 数据库      |
|-----------------|------------|--------------------------------------|----------|
| sc-auth         | `auth_`    | `flyway_schema_history_auth`         | edupivot |
| sc-course       | `edu_`     | `flyway_schema_history_course`       | edupivot |
| sc-notification | `ntf_`     | `flyway_schema_history_notification` | edupivot |
| sc-storage      | `storage_` | `flyway_schema_history_storage`      | edupivot |
| sc-ai           | `ai_`      | `flyway_schema_history_ai`           | edupivot |

历史例外：早期 `sc-auth` 迁移曾创建 `edu_*` 与 `ntf_*` 表。不要移动或重写这些已存在迁移；从现在开始禁止新增跨服务表前缀迁移。

## 命名与归属

迁移文件格式：

```text
V{yyyyMMdd}{NN}__{snake_case_description}.sql
```

示例：

- `V2026061301__add_user_avatar.sql`
- `V2026061302__create_notification_target_table.sql`

规则：

- `yyyyMMdd` 使用创建迁移的日期，`NN` 为当天两位序号。
- 同一服务内版本号必须唯一，新增迁移版本必须大于该服务已发布最高版本。
- 文件放在对应服务的 `src/main/resources/db/migration/`。
- 迁移只能维护本服务表前缀对应的对象；跨服务数据修复必须先拆清服务归属。

## 编写规范

- 已应用迁移不可修改、重命名、移动或删除；任何修复都新增前向迁移。
- 新迁移尽量使用 `IF NOT EXISTS` / `IF EXISTS`，但不能依赖 Flyway 重跑已成功迁移。
- 复杂表、列、约束建议补充 `COMMENT ON TABLE/COLUMN`。
- 数据修复迁移允许使用 DML，但必须独立成文件，说明影响范围，并保证可审计、可回滚。
- 大表索引需要评估锁表影响；`CREATE INDEX CONCURRENTLY` 不能在普通事务迁移中随意使用。
- 避免在迁移中写业务查询逻辑；如必须使用 `SELECT`，需要在迁移注释中说明原因。

## 配置规则

- 禁止启用 `spring.flyway.out-of-order`。Flyway 默认按版本顺序执行，乱序迁移会让环境状态更难复现。
- 本项目保留 `baseline-on-migrate: true` 作为共享 schema + 各服务独立 history 表的首次接入例外。不要用它掩盖连错库、连错
  schema 或误删 history 表的问题。
- 各服务必须显式配置自己的 `spring.flyway.table`，避免多个服务写入同一个 history 表。

## 操作流程

### 新增迁移

1. 在对应服务目录新增迁移文件。
2. 运行只读校验：

   ```bash
   python scripts/validate_flyway_migrations.py
   ```

3. 本地或测试环境启动服务，确认 Flyway 成功执行。
4. 发布前查看目标环境迁移状态：

   ```bash
   flyway info
   flyway validate
   ```

### 修复迁移问题

- `Checksum mismatch`：说明已应用迁移文件被改过。优先恢复原文件；只有确认 history 元数据需要修复时才使用 `repair`。
- `Applied migration not resolved locally`：说明数据库记录存在但本地文件缺失。优先恢复文件；如确认为废弃历史，再评估
  `repair`。
- 迁移 SQL 失败：修复 SQL 后重新部署，Flyway 会重试失败迁移；如果迁移已成功执行，不要改旧文件。
- 需要回滚业务结构：新增反向迁移，不直接编辑旧迁移。

使用 `repair` 前必须备份对应 history 表，例如：

```sql
CREATE TABLE flyway_schema_history_auth_backup_yyyymmdd AS
SELECT * FROM flyway_schema_history_auth;
```

## 禁止事项

- 禁止修改、重命名、移动或删除已应用迁移。
- 禁止直接手写 SQL 修改 `flyway_schema_history_*`；需要修复元数据时使用 Flyway `repair`。
- 禁止生产环境使用 `flyway clean`。
- 禁止新增低于当前最高版本的迁移。
- 禁止新增跨服务表前缀迁移。
- 禁止用 `baseline-on-migrate` 或 `repair` 掩盖错误数据库连接。

## 校验脚本

`scripts/validate_flyway_migrations.py` 是只读校验工具：

- 文件名格式错误、同服务版本重复、非 legacy 跨服务表前缀会失败。
- 现存 legacy 跨服务迁移、DML、顶层 `SELECT`、`CREATE INDEX CONCURRENTLY`、`DROP` 操作会输出 warning。
- 脚本不会修改迁移 SQL、数据库或 history 表。

## 最佳实践

1. 小步迭代：每个迁移只做一件事。
2. 前向兼容：新迁移不能破坏仍可能运行的旧版本应用。
3. 先校验再发布：提交前跑脚本，发布前跑 `validate/info`。
4. 复杂迁移写清楚原因、影响范围和回滚方式。
5. 迁移失败优先恢复事实，不急着 `repair`。

---

最后更新：2026-06-18
维护者：DaYZ
