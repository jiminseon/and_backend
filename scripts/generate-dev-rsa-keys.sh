#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
KEY_DIR="${ROOT_DIR}/.secrets/jwt"

mkdir -p "${KEY_DIR}"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "${KEY_DIR}/private-key.pem"
openssl pkey -in "${KEY_DIR}/private-key.pem" -pubout -out "${KEY_DIR}/public-key.pem"
chmod 600 "${KEY_DIR}/private-key.pem"

echo "Generated local JWT keys in ${KEY_DIR}"
echo "Set JWT_PRIVATE_KEY=file:${KEY_DIR}/private-key.pem for user-service only."
echo "Set JWT_PUBLIC_KEY=file:${KEY_DIR}/public-key.pem for every service."
