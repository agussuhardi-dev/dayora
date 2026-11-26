#!/usr/bin/env bash
set -euo pipefail
export NG_CLI_ANALYTICS=false

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "$root/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$root/.env"
  set +a
fi
backend_pid=""
frontend_pid=""

cleanup() {
  trap - INT TERM EXIT
  for pid in "$frontend_pid" "$backend_pid"; do
    if [[ -n "$pid" ]]; then
      pkill -TERM -P "$pid" 2>/dev/null || true
      kill "$pid" 2>/dev/null || true
    fi
  done
  wait 2>/dev/null || true
}
trap cleanup INT TERM EXIT

command -v docker >/dev/null || { echo 'Docker tidak ditemukan.' >&2; exit 1; }
command -v mvn >/dev/null || { echo 'Maven tidak ditemukan.' >&2; exit 1; }
command -v npm >/dev/null || { echo 'npm tidak ditemukan.' >&2; exit 1; }
[[ -d "$root/frontend/node_modules" ]] || { echo 'Jalankan npm install di frontend terlebih dahulu.' >&2; exit 1; }
if [[ -z "${FIREBASE_PROJECT_ID:-}" ]]; then
  echo 'Peringatan: FIREBASE_PROJECT_ID tidak diatur; push FCM dinonaktifkan.' >&2
fi
if [[ -n "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]]; then
  if [[ "$GOOGLE_APPLICATION_CREDENTIALS" != /* ]]; then
    GOOGLE_APPLICATION_CREDENTIALS="$root/$GOOGLE_APPLICATION_CREDENTIALS"
  elif [[ ! -f "$GOOGLE_APPLICATION_CREDENTIALS" && -f "$root/${FIREBASE_CREDENTIALS_FILE:-}" ]]; then
    GOOGLE_APPLICATION_CREDENTIALS="$root/$FIREBASE_CREDENTIALS_FILE"
  fi
  if [[ ! -f "$GOOGLE_APPLICATION_CREDENTIALS" ]]; then
    echo 'GOOGLE_APPLICATION_CREDENTIALS menunjuk ke file yang tidak ada.' >&2
    exit 1
  fi
  export GOOGLE_APPLICATION_CREDENTIALS
fi

if [[ -n "${DAYORA_BOOTSTRAP_ADMIN_USERNAME:-}" && -z "${DAYORA_BOOTSTRAP_ADMIN_PASSWORD:-}" ]]; then
  echo 'DAYORA_BOOTSTRAP_ADMIN_PASSWORD wajib jika username bootstrap diatur.' >&2
  exit 1
fi

(cd "$root/backend" && docker compose up -d --wait)
(cd "$root/backend" && mvn -q spring-boot:run) &
backend_pid=$!
(cd "$root/frontend" && npm start -- --host 127.0.0.1) &
frontend_pid=$!

echo 'Backend: http://localhost:8080'
echo 'Frontend: http://localhost:4200'
echo 'Ctrl+C menghentikan backend/frontend. PostgreSQL tetap berjalan.'

while kill -0 "$backend_pid" 2>/dev/null && kill -0 "$frontend_pid" 2>/dev/null; do
  sleep 1
done
echo 'Salah satu proses berhenti; menghentikan proses lainnya.' >&2
exit 1
