#!/usr/bin/env bash
set -euo pipefail
base_url="${1:-http://localhost:8080}"
curl --fail --silent --show-error "$base_url/actuator/health" | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "UP"'
curl --fail --silent --show-error "$base_url/api/products" | python3 -c 'import json,sys; data=json.load(sys.stdin); assert len(data)==8; assert all(p["price"] > 0 for p in data)'
curl --fail --silent --show-error "$base_url/" | python3 -c 'import sys; assert "Tecnologia que" in sys.stdin.read()'
echo 'Smoke test OK: aplicação, banco, catálogo e página inicial.'
