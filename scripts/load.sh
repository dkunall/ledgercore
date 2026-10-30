#!/usr/bin/env bash
# Crude closed-loop load: N internal transfers between a pool of accounts, C at a time.
# The default pool keeps each account under the velocity threshold (20 per minute),
# so this measures the posting path rather than fraud rejections.
# Prints wall time, throughput and latency percentiles as seen by curl.
set -euo pipefail
cd "$(dirname "$0")"
. ./lib.sh
N=${N:-2000}; C=${C:-16}; ACCOUNTS=${ACCOUNTS:-200}

OPS=$(token ops-asha); TOKEN=$(token alice)
mapfile -t IDS < <(for _ in $(seq "$ACCOUNTS"); do
  id=$(call "$TOKEN" POST /v1/accounts '{"type":"WALLET","currency":"INR"}' | jq -r .id)
  call "$OPS" POST "/v1/accounts/$id/deposits" '{"amountMinor":100000000,"currency":"INR"}' \
    -H "Idempotency-Key: $(key)" -o /dev/null
  echo "$id"
done)

export BASE TOKEN
export IDS_FLAT="${IDS[*]}"
one() {
  read -ra ids <<< "$IDS_FLAT"
  local from=${ids[RANDOM % ${#ids[@]}]} to
  to=${ids[RANDOM % ${#ids[@]}]}
  while [ "$to" = "$from" ]; do to=${ids[RANDOM % ${#ids[@]}]}; done
  curl -s -o /dev/null -w '%{http_code} %{time_total}\n' -X POST "$BASE/v1/transfers" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -H "Idempotency-Key: load-$1-$RANDOM$RANDOM" \
    -d "{\"sourceAccountId\":\"$from\",\"destinationAccountId\":\"$to\",\"amountMinor\":$((RANDOM % 5000 + 1)),\"currency\":\"INR\"}"
}
export -f one

START=$(date +%s.%N)
seq "$N" | xargs -P "$C" -I{} bash -c 'one {}' > /tmp/ledgercore-load.txt
END=$(date +%s.%N)

sort -k2 -n /tmp/ledgercore-load.txt | awk -v start="$START" -v end="$END" -v c="$C" '
  { codes[$1]++; t[NR] = $2 * 1000 }
  END {
    n = NR; wall = end - start
    printf "requests=%d concurrency=%d wall=%.1fs throughput=%.0f req/s\n", n, c, wall, n / wall
    printf "latency ms: p50=%.1f p95=%.1f p99=%.1f max=%.1f\n", t[int(n*0.50)], t[int(n*0.95)], t[int(n*0.99)], t[n]
    for (k in codes) printf "HTTP %s: %d\n", k, codes[k]
  }'
