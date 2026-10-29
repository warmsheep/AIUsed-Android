#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
PORT="${1:-8088}"
IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo 127.0.0.1)"
echo "APK:   http://$IP:$PORT/AIUsage-1.0.0.apk"
echo "Local: http://127.0.0.1:$PORT/AIUsage-1.0.0.apk"
echo "Press Ctrl+C to stop."
exec python3 -m http.server "$PORT" --bind 0.0.0.0 --directory dist
