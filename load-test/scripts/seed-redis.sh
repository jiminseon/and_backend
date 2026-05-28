#!/usr/bin/env bash
set -euo pipefail

REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"

seed_stock() {
  local stock_code="$1"
  local price="$2"
  local rsi="$3"
  local sma20="$4"
  local volume_ratio="$5"
  local high52="$6"
  local low52="$7"

  redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" set "minute:${stock_code}" \
    "{\"price\":${price},\"volumeRatio\":${volume_ratio},\"volume\":150000,\"diffFromOpen\":900,\"diffFromOpenPct\":1.3,\"diffFromHigh52wPct\":-2.1,\"diffFromLow52wPct\":18.0}"

  redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" set "daily:${stock_code}" \
    "{\"price\":${price},\"rsi14\":${rsi},\"bbUpper\":${high52},\"bbLower\":${low52},\"sma5\":${sma20},\"sma10\":${sma20},\"sma20\":${sma20},\"sma30\":${sma20},\"sma50\":${sma20},\"sma100\":${sma20},\"sma200\":${sma20}}"
}

seed_stock "005930" 70500 38 69000 140 73000 65000
seed_stock "000660" 181000 39 172000 135 186000 160000
seed_stock "035420" 191000 41 186000 128 198000 170000
seed_stock "035720" 61000 37 56000 145 64000 52000
seed_stock "005380" 252000 42 242000 125 260000 230000

echo "Seeded Redis metrics on ${REDIS_HOST}:${REDIS_PORT}"
