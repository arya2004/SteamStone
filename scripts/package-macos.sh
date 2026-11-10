#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/java-env.sh
exec python3 scripts/package.py "$@"
