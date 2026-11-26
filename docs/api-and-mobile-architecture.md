# Dayora API & Mobile Implementation Guide

Dokumen panduan arsitektur, flow data, endpoint REST, realtime contract, serta spesifikasi klien Android native (Kotlin / Jetpack Compose).

---

## 1. Perubahan Arsitektur (Changelog)

- **Offline Sync Android (backend contract)**: Device-scoped `/api/sync/bootstrap?deviceId={uuid}`, `/api/sync/pull?deviceId={uuid}&cursor={n}&limit=500`, `POST /api/sync/push`. Device didaftarkan oleh bootstrap. Push body `{deviceId,operations:[{operationId,entityType,entityId,baseRevision,action,payload}]}`; action `UPSERT|DELETE`. Batch atomic, idempotent per operation ID, stale revision `409`. Pull/bootstrap response `{cursor,changes:[{cursor,entityType,entityId,revision,deleted,payload}],hasMore}`. Account comes from session, never request body. Supported push: `TASK`, `CALENDAR_EVENT`, `WORKSPACE` (create/rename), `SETTINGS`, `FOCUS_SESSION` (create/update; no delete). Pull also includes `TIME_ENTRY` read-only. This contract does not yet provide full write sync for time entries or workspace deletion; do not describe offline sync as complete.
- **REST CRUD**: Web memakai endpoint REST `/api/*` tanpa offline task queue. Android native direncanakan memakai API yang sama; belum ada implementasi Android dalam repo.
- **Database Bersih (Liquibase `014-remove-offline-sync`)**:
  - Menghapus tabel: `device`, `sync_operation_receipt`, `change_log`, `sync_cursor_counter`.
  - Membuat tabel: `account_event_counter` (hanya pencatat sequence event per akun).
  - Menyimpan tabel domain inti: `app_user`, `workspace`, `task`, `calendar_event`, `focus_session`, `time_entry`, `account_settings`.
- **Realtime Invalidation-Only**:
  - WebSocket `/ws/events` mengirim sinyal invalidasi: `{"type":"INVALIDATE","cursor":<number>}`.
  - Sinyal ini **hanya pemicu refetch** via REST; tidak lagi menyediakan replay payload offline.

---

## 2. Autentikasi & Session

### Endpoints
- `GET /api/auth/csrf`: Mendapatkan token CSRF (`{headerName: string, token: string}`).
- `POST /api/auth/login`: Form-encoded body `username` dan `password`.
  - Header: `[csrf.headerName]: [csrf.token]`
  - Response sukses: `204 No Content`. Session memakai cookie; atribut cookie bergantung konfigurasi deployment.
- `GET /api/auth/me`: Mengembalikan detail user yang sedang login (`{id: UUID}`).
- `POST /api/auth/logout`: Menghapus session server.

> **Catatan Android:** Android native menggunakan HTTP Client (seperti Retrofit/Ktor) dengan persistence Cookie jar (`CookieManager` / OkHttp `CookieJar`) untuk menyimpan `JSESSIONID`. Sertakan header CSRF pada setiap request mutasi (`POST`, `PUT`, `DELETE`).

---

## 3. Workspaces

Setiap resource (Task, Event, Focus) terisolasi per Workspace milik user. User baru otomatis memiliki workspace default `Personal`.

- `GET /api/workspaces`: Mengambil daftar workspace user `[{id, name}]`.
- `POST /api/workspaces`: Membuat workspace baru `{"name": "Work"}` (maks 120 karakter).

---

## 4. REST Endpoints & Data Models

### A. Tasks (`/api/tasks`)
Model:
```json
{
  "id": "UUID",
  "workspaceId": "UUID",
  "title": "string (maks 240 char)",
  "notes": "string (opsional, maks 10000 char)",
  "status": "TODO | IN_PROGRESS | DONE | CANCELED",
  "priority": "NONE | LOW | MEDIUM | HIGH",
  "dueDate": "YYYY-MM-DD (opsional)",
  "completedAt": "ISO-8601 Instant (opsional)",
  "revision": 1
}
```

- `GET /api/tasks?page=0&size=100&workspaceId={uuid}`: Pageable list task.
- `POST /api/tasks`: Create task baru (status default: `TODO`, priority: `NONE`).
- `PUT /api/tasks/{id}?baseRevision={revision}`: Update task.
  - Jika `baseRevision` tidak cocok dengan server, mengembalikan HTTP `409 Conflict`. Klien harus me-reload data terbaru.
- `DELETE /api/tasks/{id}?baseRevision={revision}`: Hapus task.

### B. Planner & Calendar (`/api/calendar`)
Model:
```json
{
  "id": "UUID",
  "workspaceId": "UUID",
  "taskId": "UUID (opsional)",
  "title": "string (maks 240 char)",
  "startsAt": "ISO-8601 Instant",
  "endsAt": "ISO-8601 Instant (harus > startsAt)",
  "timezone": "string (e.g. Asia/Jakarta)",
  "category": "string (opsional)",
  "color": "string (opsional)",
  "revision": 1
}
```

- `GET /api/calendar?from={iso}&to={iso}&workspaceId={uuid}`: Mengambil agenda dalam rentang waktu.
- `POST /api/calendar`: Buat jadwal/time block baru.
- `PUT /api/calendar/{id}?baseRevision={revision}`: Update jadwal (menolak `409 Conflict` jika revisi beda).
- `DELETE /api/calendar/{id}?baseRevision={revision}`: Hapus event.

### C. Focus Timer (`/api/focus`)
Model:
```json
{
  "id": "UUID",
  "workspaceId": "UUID",
  "state": "RUNNING | PAUSED | COMPLETED | CANCELED",
  "mode": "NORMAL | POMODORO",
  "phase": "FOCUS | SHORT_BREAK | LONG_BREAK",
  "durationSeconds": 1500,
  "activeDurationSeconds": 0,
  "runningSince": "ISO-8601 Instant | null",
  "taskId": "UUID | null",
  "completedFocusCount": 0,
  "focusDurationMinutes": 25,
  "shortBreakDurationMinutes": 5,
  "longBreakDurationMinutes": 15
}
```

- `GET /api/focus/current`: Mengembalikan sesi fokus yang sedang aktif (`RUNNING` / `PAUSED`), atau `204 No Content` jika tidak ada.
- `POST /api/focus`: Memulai sesi fokus. Body:
  ```json
  {
    "mode": "POMODORO",
    "workspaceId": "UUID",
    "taskId": "UUID (opsional)",
    "focusDurationMinutes": 25,
    "shortBreakDurationMinutes": 5,
    "longBreakDurationMinutes": 15
  }
  ```
- `POST /api/focus/{id}/pause`: Jeda sesi yang sedang `RUNNING`.
- `POST /api/focus/{id}/resume`: Lanjutkan sesi `PAUSED`.
- `POST /api/focus/{id}/complete`: Selesaikan sesi aktif.
- `POST /api/focus/{id}/cancel`: Batalkan sesi aktif.
- `POST /api/focus/{id}/next`: Pindah ke fase Pomodoro berikutnya (Focus → Short Break → Long Break).
- `GET /api/focus/history?workspaceId={uuid}`: Riwayat sesi selesai.

### D. Settings (`/api/settings`)
- `GET /api/settings`: Mengambil preferensi pengguna (Pomodoro durations, sound, appearance theme & accent).
- `GET` mengembalikan `{revision, settings}`. `PUT` wajib mengirim revision terakhir beserta seluruh settings. Revision usang menghasilkan `409`.
- `PUT /api/settings`:
  ```json
  {
    "revision": 0,
    "settings": {
      "schemaVersion": 1,
      "pomodoro": {"focusMinutes": 25, "shortBreakMinutes": 5, "longBreakMinutes": 15, "mode": "POMODORO"},
      "sound": {"enabled": true, "breakTicking": false},
      "appearance": {"theme": "system", "accent": "#7561d8"}
    }
  }
  ```
- Durasi Pomodoro settings: 1–240 menit. Theme: `system`, `light`, `dark`; accent: hex enam digit.

---

## 5. Kontrak Realtime (WebSocket)

- **URL:** `ws://<host>/ws/events` (atau `wss://`)
- **Autentikasi:** Menggunakan session cookie `JSESSIONID`.
- **Payload Diterima Klien:**
  ```json
  {"type": "INVALIDATE", "cursor": 12}
  ```
- **Alur Klien Saat Menerima INVALIDATE:**
  1. Catat `cursor`.
  2. Panggil ulang REST API terkait (misal: `GET /api/tasks`, `GET /api/focus/current`).
  3. Perbarui state UI. Tidak ada operasi merge kompleks atau parsing data diff.

---

## 6. Panduan Implementasi Android Native

### A. Arsitektur Rekomendasi
- **Bahasa / Framework:** Kotlin, Jetpack Compose, Coroutines + StateFlow, Android Architecture Components (ViewModel, Repository).
- **Networking:** Retrofit2 / Ktor Client dengan OkHttp client persistent `CookieJar`.
- **Dependency Injection:** opsional; gunakan Hilt/Koin hanya jika kompleksitas aplikasi memerlukannya.
- **Background:** opsi foreground service dan AlarmManager perlu ditentukan setelah uji kebutuhan, izin, dan batas versi Android.

### B. Flow & Siklus Aplikasi
1. **Startup / Login:**
   - Cek session dengan memanggil `GET /api/auth/me`.
   - Jika `401 Unauthorized` → arahkan ke Login Screen.
   - Panggil `GET /api/auth/csrf` sebelum login.
   - Setelah `POST /api/auth/login` sukses, ambil ulang CSRF token (token dapat berubah saat autentikasi), simpan session secara aman, lalu buka Dashboard. Jangan mencatat cookie/token dalam log.
2. **Dashboard (Today):**
   - Ambil workspace aktif via `GET /api/workspaces`.
   - Fetch tasks aktif (`GET /api/tasks?workspaceId=...`) dan jadwal hari ini (`GET /api/calendar?from=...&to=...`).
   - Fetch status focus aktif (`GET /api/focus/current`).
3. **Focus Session & App Blocker (Native Only):**
   - Jalankan Foreground Service dengan notifikasi persisten saat Focus aktif.
   - App blocking masih roadmap, belum diimplementasikan. `UsageStatsManager` hanya menyediakan informasi penggunaan, bukan API pemblokiran. Evaluasi AccessibilityService sesuai izin eksplisit pengguna, kebijakan Google Play, batas OS; jangan menjanjikan pemblokiran absolut.
   - Evaluasi `AlarmManager.setExactAndAllowWhileIdle()` sesuai izin exact alarm dan kebijakan versi Android. Tidak menjamin alarm ketika aplikasi force-stop; perlu pengujian perangkat nyata.
   - Sinkronkan progress timer ke server via `POST /api/focus/{id}/complete` atau `next`.
4. **WebSocket Listener:**
   - Buat koneksi OkHttp WebSocket ke `/ws/events`.
   - Pada saat event `INVALIDATE`, picu `viewModel.refresh()`.
   - Tangani auto-reconnect dengan backoff exponential saat koneksi putus.
