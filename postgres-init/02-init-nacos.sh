#!/bin/bash
set -e

# 创建 nacos 数据库（如果不存在）
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE nacos' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'nacos')\gexec
EOSQL

# 在 nacos 数据库上执行初始化 SQL（从子目录读取，避免被 Docker 自动执行）
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname nacos < /docker-entrypoint-initdb.d/nacos/schema.sql

echo "Nacos database initialized successfully."
