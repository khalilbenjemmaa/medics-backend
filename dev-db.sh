#!/usr/bin/env bash
#
# Opens the database browser at http://localhost:8081
#
#   ./dev-db.sh
#
set -euo pipefail
cd "$(dirname "$0")"
export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"

# Bound to loopback deliberately: pgweb has full read/write access to
# the database and no login of its own, so it must not be reachable
# from the network.
exec pgweb \
  --url "postgres://practice:practice@localhost:5432/practice?sslmode=disable" \
  --listen 8081 --bind 127.0.0.1 --skip-open
