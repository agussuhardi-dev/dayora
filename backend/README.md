# Dayora Backend

Java 25, Spring Boot 4.2, Maven, PostgreSQL, Liquibase.

## Jalankan lokal

```bash
cp .env.example .env
# Isi DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD.
# Untuk DB remote, tidak perlu menjalankan PostgreSQL lokal.
set -a; source .env; set +a
cd backend
mvn spring-boot:run
```

DB lokal opsional:

```bash
cd backend
docker compose up -d
```

`DAYORA_BOOTSTRAP_ADMIN_*` hanya membuat admin pertama bila username belum ada. Hapus environment variable sesudahnya. Jangan gunakan password contoh di deployment.

## Docker deployment

```bash
cp .env.example .env
# Isi DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD.
# Set SESSION_COOKIE_SECURE=true di HTTPS production.
docker compose up -d --build
```

`compose.yaml` root membangun backend dan frontend. Frontend Nginx meneruskan `/api`, `/ws`, dan `/actuator` ke backend. Cloudflare Tunnel meneruskan traffic ke frontend internal pada `http://frontend:80`; atur Public Hostname di Cloudflare Tunnel ke service tersebut. Backend/frontend tidak dipublikasikan langsung ke host. Database eksternal diatur melalui `DATABASE_*`; tidak ada PostgreSQL container pada stack deploy ini.

FCM opsional: letakkan service-account JSON di luar repo, misalnya `/opt/dayora/secrets/firebase.json` dengan mode `600`, lalu set `FIREBASE_CREDENTIALS_FILE=/opt/dayora/secrets/firebase.json` dan `GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/dayora-firebase.json` di `.env`. File tersebut dipasang read-only ke container.

- `GET /actuator/health`
- `GET /api/auth/csrf`
- `POST /api/auth/login` — form fields `username`, `password`, dengan header CSRF.
- `GET /api/auth/me`
- `GET /api/tasks`, `POST /api/tasks`, `GET|PUT|DELETE /api/tasks/{id}`
- `GET /api/calendar`, `POST /api/calendar`, `PUT|DELETE /api/calendar/{id}`
- `POST /api/focus`, `GET /api/focus/current`, `GET /api/focus/history`, `POST /api/focus/{id}/{pause|resume|complete|cancel|next}`
- `GET|PUT /api/settings`
- `PUT|DELETE /api/push/token` — JSON body `{"token":"..."}`; authenticated + CSRF protected.

## Curl smoke test

Login memakai session cookie + CSRF token:

```bash
BASE=http://localhost:8080
curl -c cookies.txt "$BASE/api/auth/csrf"
curl -c cookies.txt -b cookies.txt -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -H 'X-XSRF-TOKEN: <csrf-token>' \
  --data-urlencode username=admin --data-urlencode password='change-this-password'
curl -b cookies.txt "$BASE/api/auth/me"
curl -b cookies.txt "$BASE/api/tasks"
```

Liquibase is the sole schema creator. JPA uses `ddl-auto=validate`.

## Realtime contract
Authenticated raw WebSocket: `GET /ws/events`. Session cookie supported. Native `Authorization: Bearer <JWT>` only when optional issuer is configured; URL tokens unsupported. Origins default `http://localhost:4200`; set `DAYORA_WS_ORIGINS`.

Messages only invalidate: `{"type":"INVALIDATE","cursor":42}`. Clients refetch authoritative REST resources; no replay payload or offline outbox. Event published after commit.

## FCM invalidations

Optional FCM uses Google Application Default Credentials. Set `GOOGLE_APPLICATION_CREDENTIALS` to a protected service-account JSON path and `FIREBASE_PROJECT_ID` to the Firebase project ID (see root `.env.example`). Never put the service-account JSON in `.env`, Git, or the Android app. For deployment, mount it as a read-only secret file and set the path in the environment. Without project ID or valid ADC, startup continues with a warning; push stays disabled.

After login and each Firebase token refresh, Android registers with authenticated `PUT /api/push/token`; logout unregisters with `DELETE /api/push/token`. Both accept `{"token":"..."}` and require session cookie plus CSRF header (`X-XSRF-TOKEN`). Token must be nonblank and at most 4096 characters. Registration is idempotent and transfers a token when another account registers it. Stored token is durable; unregister only removes the caller's token.

Data-only payloads include `cursor`: `focus_invalidated` uses Android HIGH priority; other changes use `sync_invalidated` at NORMAL priority. High priority is reserved for focus invalidations. Android must fetch authoritative focus state, update local block state regardless of FGS start eligibility, and show/update the visible notification for an already-running focus FGS. FCM cannot guarantee delivery or bypass Android background-start policy; priority is subject to platform policy.

Dispatch uses a bounded asynchronous queue after transaction commit. Saturation drops the invalidation and logs owner ID; clients must reconcile through sync. Firebase `UNREGISTERED` tokens are removed. Other send failures log error category only; token values and credentials are never logged.

## Optional OIDC
OIDC disabled without provider. Required env: `SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_DAYORA_ISSUER_URI`, `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_DAYORA_CLIENT_ID`, `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_DAYORA_CLIENT_SECRET`, scopes; JWT resource server requires `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`. Provider choice, explicit `(issuer, subject) -> Account UUID` provisioning/link UX remain blockers. Email/username auto-link prohibited; existing session login retained.
