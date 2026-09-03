#!/usr/bin/env bash
#
# Starts the backend for local development.
#
# Reads .env, checks PostgreSQL is actually up first (the commonest
# reason a start fails), then runs the app. Docker is not required.
#
#   ./dev.sh
#
set -euo pipefail
cd "$(dirname "$0")"

# Homebrew installs these outside the default PATH.
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/postgresql@16/bin:$PATH"

if [ ! -f .env ]; then
  echo "No .env file. Copy .env.example to .env and fill it in." >&2
  exit 1
fi

# Export every assignment in .env without needing them pre-declared.
set -a
# shellcheck disable=SC1091
source .env
set +a

if ! pg_isready -q -h localhost -p 5432 2>/dev/null; then
  echo "PostgreSQL is not accepting connections on localhost:5432."
  echo "Start it with:  brew services start postgresql@16"
  exit 1
fi

echo "PostgreSQL is up. Starting the API on http://localhost:${SERVER_PORT:-8080}"
echo "Swagger: http://localhost:${SERVER_PORT:-8080}/swagger-ui.html"
echo
exec mvn spring-boot:run
