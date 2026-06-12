#!/bin/bash
set -e

echo "Initializing edupivot database..."

# edupivot 库由 Docker 通过 POSTGRES_DB 自动创建
# 表结构由 Flyway 在应用启动时自动管理
# Flyway 脚本位置: 各服务 src/main/resources/db/migration/

echo "edupivot database initialized successfully. Tables will be created by Flyway."
