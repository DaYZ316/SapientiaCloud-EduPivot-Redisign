#!/bin/bash
# ============================================================
# 生成 RSA 密钥对用于 JWT RS256 签名
# 用法: ./scripts/generate-rsa-keys.sh
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEY_DIR="${SCRIPT_DIR}/../keys"

mkdir -p "${KEY_DIR}"

echo "正在生成 RSA 2048 位密钥对..."

# 生成私钥（PKCS#8 格式）
openssl genrsa -out "${KEY_DIR}/private.pem" 2048

# 从私钥提取公钥
openssl rsa -in "${KEY_DIR}/private.pem" -pubout -out "${KEY_DIR}/public.pem"

echo ""
echo "=== 密钥已生成到 ${KEY_DIR} ==="
echo ""

# 输出 .env 格式
echo "# 添加到 .env 文件"
echo "JWT_PRIVATE_KEY=$(cat "${KEY_DIR}/private.pem" | tr '\n' '\\' | sed 's/\\$/\\n/g' | sed 's/\\$//')"
echo ""
echo "JWT_PUBLIC_KEY=$(cat "${KEY_DIR}/public.pem" | tr '\n' '\\' | sed 's/\\$/\\n/g' | sed 's/\\$//')"
echo ""
echo "=== 安全提示 ==="
echo "1. 将 keys/ 目录加入 .gitignore"
echo "2. 私钥仅用于 auth 服务，不要暴露给其他服务"
echo "3. 公钥可通过 JWKS 端点动态获取，无需手动分发"
