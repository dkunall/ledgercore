# Shared helpers for the demo and load scripts. Source, don't run.
BASE=${BASE:-http://localhost:7690}
OAUTH=${OAUTH:-http://localhost:7691}

# The app validates the issuer as http://oauth:8080/ledgercore (its in-network name),
# and mock-oauth2-server derives the issuer from the Host header.
token() {
  curl -sf -H "Host: oauth:8080" -d grant_type=client_credentials -d "client_id=$1" -d client_secret=unused \
    "$OAUTH/ledgercore/token" | jq -r .access_token
}

key() { printf 'demo-%s' "$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')"; }

call() {
  local tok=$1 method=$2 path=$3 body=${4:-}
  shift 4 || shift $#
  curl -s -X "$method" "$BASE$path" -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
    "$@" ${body:+-d "$body"}
}
