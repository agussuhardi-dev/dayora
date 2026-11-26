#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
remote="${DEPLOY_USER:-$USER}@${DEPLOY_HOST:-10.1.10.18}"
remote_dir="${DEPLOY_DIR:-/opt/dayora}"
tag="${DEPLOY_TAG:-$(git -C "$root" rev-parse --short HEAD)}"
platform="${DEPLOY_PLATFORM:-linux/amd64}"
archive="${TMPDIR:-/tmp}/dayora-${tag}.tar.gz"
cloudflared_archive="${TMPDIR:-/tmp}/dayora-cloudflared-${tag}.tar"

command -v docker >/dev/null || { echo 'Docker tidak ditemukan.' >&2; exit 1; }
docker buildx version >/dev/null 2>&1 || { echo 'Docker Buildx tidak ditemukan.' >&2; exit 1; }
command -v ssh >/dev/null || { echo 'SSH tidak ditemukan.' >&2; exit 1; }
command -v scp >/dev/null || { echo 'SCP tidak ditemukan.' >&2; exit 1; }
[[ -f "$root/.env" ]] || { echo '.env tidak ditemukan.' >&2; exit 1; }
ssh_opts=(-o BatchMode=yes -o ConnectTimeout=15 -o ConnectionAttempts=1 -o ServerAliveInterval=10 -o ServerAliveCountMax=2)

backend_image="dayora-backend:${tag}"
frontend_image="dayora-frontend:${tag}"

echo "Cek koneksi SSH ke $remote..."
ssh "${ssh_opts[@]}" "$remote" true || { echo "VM tidak dapat dijangkau via SSH: $remote" >&2; exit 1; }

cleanup() { rm -f "$archive" "$cloudflared_archive"; }
trap cleanup EXIT

echo "Build image untuk $platform..."
docker buildx build --platform "$platform" --load -t "$backend_image" "$root/backend"
docker buildx build --platform "$platform" --load -t "$frontend_image" "$root/frontend"

echo "Download Cloudflare Tunnel untuk $platform..."
docker pull --platform "$platform" cloudflare/cloudflared:latest
docker image save --platform "$platform" -o "$cloudflared_archive" cloudflare/cloudflared:latest

echo 'Export image...'
docker save "$backend_image" "$frontend_image" | gzip > "$archive"

remote_archive="$remote_dir/dayora-${tag}.tar.gz"
remote_cloudflared_archive="$remote_dir/dayora-cloudflared-${tag}.tar"
echo "Deploy ke $remote..."
ssh "${ssh_opts[@]}" "$remote" "mkdir -p '$remote_dir/secrets' && if [[ -d '$remote_dir/secrets/dayora-firebase.json' ]]; then rm -rf '$remote_dir/secrets/dayora-firebase.json'; fi"
scp "${ssh_opts[@]}" "$archive" "$remote:$remote_archive"
scp "${ssh_opts[@]}" "$cloudflared_archive" "$remote:$remote_cloudflared_archive"
scp "${ssh_opts[@]}" "$root/compose.yaml" "$root/.env" "$remote:$remote_dir/"

firebase_file=""
if grep -q '^FIREBASE_CREDENTIALS_FILE=' "$root/.env"; then
  firebase_file="$(sed -n 's/^FIREBASE_CREDENTIALS_FILE=//p' "$root/.env")"
fi
if [[ -n "$firebase_file" ]]; then
  if [[ ! -f "$firebase_file" && -f "$root/$firebase_file" ]]; then
    firebase_file="$root/$firebase_file"
  fi
  if [[ -f "$firebase_file" ]]; then
    scp "$firebase_file" "$remote:$remote_dir/secrets/dayora-firebase.json"
  fi
fi

ssh "${ssh_opts[@]}" "$remote" bash -s -- "$remote_dir" "$tag" "$remote_archive" "$remote_cloudflared_archive" <<'REMOTE'
set -euo pipefail
remote_dir="$1"
tag="$2"
archive="$3"
cloudflared_archive="$4"
cd "$remote_dir"
chmod 600 .env
if [[ -f secrets/dayora-firebase.json ]]; then chmod 600 secrets/dayora-firebase.json; fi
docker load < "$archive"
cloudflared_image="$(docker load -i "$cloudflared_archive" | sed -n 's/^Loaded image ID: //p; s/^Loaded image: //p' | tail -1)"
[[ -n "$cloudflared_image" ]] || { echo 'Cloudflared image gagal dimuat.' >&2; exit 1; }
docker tag "$cloudflared_image" cloudflare/cloudflared:latest
# Simpan tag agar `docker compose up -d` manual memakai image yang sudah dikirim.
awk '!/^DEPLOY_TAG=/' .env > .env.next
printf 'DEPLOY_TAG=%s\n' "$tag" >> .env.next
chmod 600 .env.next
mv .env.next .env
docker compose down --remove-orphans
docker compose up -d --no-build --pull never
rm -f "$archive" "$cloudflared_archive"
docker image prune -f
echo "Deploy selesai: $tag"
REMOTE

echo "Deploy selesai ke $remote"
