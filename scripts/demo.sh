#!/usr/bin/env bash
# Walks through the main flows against a running `docker compose up` stack.
set -euo pipefail
cd "$(dirname "$0")"
. ./lib.sh

step() { printf '\n== %s\n' "$*"; }
show() { jq -c "$@"; }

ALICE=$(token alice); BOB=$(token bob); ASHA=$(token ops-asha); RAVI=$(token ops-ravi)

step "alice and bob open INR savings accounts"
OPENED=$(call "$ALICE" POST /v1/accounts '{"type":"SAVINGS","currency":"INR"}')
echo "$OPENED" | show
A=$(echo "$OPENED" | jq -r .id)
B=$(call "$BOB" POST /v1/accounts '{"type":"SAVINGS","currency":"INR"}' | jq -r .id)
echo "alice=$A bob=$B"

step "ops deposits 50,000.00 into alice's account"
call "$ASHA" POST "/v1/accounts/$A/deposits" '{"amountMinor":5000000,"currency":"INR","reference":"cash deposit"}' \
  -H "Idempotency-Key: $(key)" | show '{status, amountMinor}'

step "alice sends 1,250.00 to bob, then retries with the same Idempotency-Key"
K=$(key)
BODY="{\"sourceAccountId\":\"$A\",\"destinationAccountId\":\"$B\",\"amountMinor\":125000,\"currency\":\"INR\",\"reference\":\"rent share\"}"
call "$ALICE" POST /v1/transfers "$BODY" -H "Idempotency-Key: $K" -o /dev/null -w 'first:  HTTP %{http_code}\n'
call "$ALICE" POST /v1/transfers "$BODY" -H "Idempotency-Key: $K" -D - -o /dev/null | grep -iE '^HTTP|^idempotent-replayed'
call "$ALICE" POST /v1/transfers "${BODY/125000/999}" -H "Idempotency-Key: $K" | show '{status, code, detail}'

step "balances after one transfer (the retry did not move money twice)"
call "$ALICE" GET "/v1/accounts/$A/balance" | show
call "$BOB" GET "/v1/accounts/$B/balance" | show

step "alice tries to overspend"
call "$ALICE" POST /v1/transfers "{\"sourceAccountId\":\"$A\",\"destinationAccountId\":\"$B\",\"amountMinor\":900000000,\"currency\":\"INR\"}" \
  -H "Idempotency-Key: $(key)" | show '{status, failureReason}'

step "bob tries to spend from alice's account"
call "$BOB" POST /v1/transfers "{\"sourceAccountId\":\"$A\",\"destinationAccountId\":\"$B\",\"amountMinor\":100,\"currency\":\"INR\"}" \
  -H "Idempotency-Key: $(key)" | show '{status, code}'

step "a 15,000.00 transfer is held for review; ops sees why"
R=$(call "$ALICE" POST /v1/transfers "{\"sourceAccountId\":\"$A\",\"destinationAccountId\":\"$B\",\"amountMinor\":1500000,\"currency\":\"INR\"}" \
  -H "Idempotency-Key: $(key)" | jq -r .id)
call "$ASHA" GET "/v1/transfers/$R" | show '{status, fraud}'
call "$ALICE" GET "/v1/accounts/$A/balance" | show '{ledgerMinor, heldMinor, availableMinor}'

step "ops approves it; a second approval finds nothing left to approve"
call "$ASHA" POST "/v1/transfers/$R/approve" | show '{status, approvedBy}'
call "$RAVI" POST "/v1/transfers/$R/approve" | show '{status, code}'

step "payout to an external beneficiary goes through the mock rail over Kafka"
PAYOUT=$(call "$ALICE" POST /v1/transfers "{\"sourceAccountId\":\"$A\",\"destinationRef\":\"IFSC:HDFC0001:12345\",\"amountMinor\":20000,\"currency\":\"INR\"}" \
  -H "Idempotency-Key: $(key)")
echo "$PAYOUT" | show '{status, type, destinationRef}'
P=$(echo "$PAYOUT" | jq -r .id)
for _ in $(seq 1 50); do
  S=$(call "$ALICE" GET "/v1/transfers/$P" | jq -r .status)
  [ "$S" != HELD ] && break
  sleep 0.2
done
echo "payout settled as $S"

step "a payout the rail rejects releases its hold"
F=$(call "$ALICE" POST /v1/transfers "{\"sourceAccountId\":\"$A\",\"destinationRef\":\"IFSC:HDFC0001:99000\",\"amountMinor\":30000,\"currency\":\"INR\"}" \
  -H "Idempotency-Key: $(key)" | jq -r .id)
for _ in $(seq 1 50); do
  S=$(call "$ALICE" GET "/v1/transfers/$F" | jq -r .status)
  [ "$S" != HELD ] && break
  sleep 0.2
done
call "$ALICE" GET "/v1/transfers/$F" | show '{status, failureReason}'

step "ops reverses the first transfer"
FIRST=$(call "$ALICE" POST /v1/transfers "$BODY" -H "Idempotency-Key: $K" | jq -r .id)
call "$ASHA" POST "/v1/transfers/$FIRST/reverse" '{"reason":"customer dispute upheld"}' | show '{status}'

step "alice's history, two entries per page"
PAGE=$(call "$ALICE" GET "/v1/accounts/$A/transactions?limit=2")
echo "$PAGE" | show '[.items[] | {entryType, amountMinor, balanceAfterMinor}]'
CUR=$(echo "$PAGE" | jq -r .nextCursor)
call "$ALICE" GET "/v1/accounts/$A/transactions?limit=2&cursor=$CUR" | show '[.items[] | {entryType, amountMinor, balanceAfterMinor}]'

step "hash chain check and audit trail"
call "$ASHA" GET "/v1/accounts/$A/integrity" | show
call "$ASHA" GET "/v1/audit?resourceId=$R" | show '[.[] | {actor, action}]'

step "notifications written by the Kafka consumer"
sleep 1
docker compose -f ../docker-compose.yml exec -T postgres psql -U ledgercore -At -c \
  "SELECT kind, count(*) FROM notification GROUP BY kind ORDER BY kind" 2>/dev/null || echo "(psql not reachable from here)"

step "trial balance across every account (must be zero)"
docker compose -f ../docker-compose.yml exec -T postgres psql -U ledgercore -At -c \
  "SELECT currency, sum(amount_minor) FROM posting GROUP BY currency" 2>/dev/null || true
