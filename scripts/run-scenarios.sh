#!/usr/bin/env bash
# Runs the three classic saga scenarios against the locally running services.
# Requires: curl. (jq is optional, only for pretty printing.)
set -u
ORDER=http://localhost:8081
PAYMENT=http://localhost:8082
INVENTORY=http://localhost:8083

pp() { if command -v jq >/dev/null 2>&1; then jq .; else cat; echo; fi; }

create_order() {  # userId productId quantity amount
  curl -s -X POST "$ORDER/orders" -H 'Content-Type: application/json' \
       -d "{\"userId\":$1,\"productId\":$2,\"quantity\":$3,\"amount\":$4}" \
  | sed -n 's/.*"id":"\([^"]*\)".*/\1/p'
}

show() {          # orderId userId productId
  echo "--- order";       curl -s "$ORDER/orders/$1" | pp
  echo "--- payment";      curl -s "$PAYMENT/payments/$1" | pp
  echo "--- wallet $2";    curl -s "$PAYMENT/wallets/$2" | pp
  echo "--- reservation";  curl -s "$INVENTORY/reservations/$1" | pp
  echo "--- stock $3";     curl -s "$INVENTORY/stock/$3" | pp
}

echo "=== 1. Happy path (user 1, 2 x product 100, amount 200) -> COMPLETED ==="
ID=$(create_order 1 100 2 200); echo "order id: $ID"; sleep 5; show "$ID" 1 100

echo; echo "=== 2. Payment fails (user 2 has only 50) -> CANCELLED: Insufficient balance ==="
ID=$(create_order 2 100 1 200); echo "order id: $ID"; sleep 5; show "$ID" 2 100

echo; echo "=== 3. Out of stock (50 x product 100) -> payment taken, then REFUNDED, order CANCELLED ==="
ID=$(create_order 1 100 50 100); echo "order id: $ID"; sleep 15; show "$ID" 1 100
