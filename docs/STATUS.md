# STATUS

## Shipped (uncommitted — no commits yet)
- Infra: `.gitignore`, `.env.example`, CI workflow, `git init`.
- Retry: backend network retries w/ backoff (`HLSWEB_NETWORK_RETRIES`), `POST /jobs/{id}/retry`,
  `Job.retries`; frontend query backoff, SSE reconnect, offline banner, auto-refetch.
- Cache: job list + form draft in localStorage (zod-validated), "Sincronizando…" state.
- Thumbnails: ffmpeg frame (early + final) → `GET /jobs/{id}/thumbnail`, `thumbnail_version`.
- Debug: `HLSWEB_DEBUG` scenarios at `http://debug.test/<name>`, 🐞 panel, simulated offline/latency, log.
  See `docs/DEBUGGING.md`.
- SQLite history (`hlsweb.db`) + startup recovery + delete; SHA-256 dedupe (URL key + content hash), `force` flag.
- Mobile: compact cards at <=640px, safe-area insets, 44-48px touch targets, iOS no-zoom inputs (checked at 390 and 320 px).
- UI redesign: cards, ring + bar progress, ETA, animations, dark/light, reduced-motion.

## Not verified
- Real sites via yt-dlp (no external network); real browser interaction (Chrome extension not connected).

## Next action
- Commit in atomic chunks (`chore:` infra, `feat:` retry, `feat:` cache, `feat:` thumbnails, `feat:` debug, `feat:` ui, `test:`, `docs:`).

## Verify
- `PATH=$PWD/backend/.venv/bin:$PATH make check` (111 backend, 139 frontend tests)
- Manual: `make dev-api-debug` + `make dev-web`, follow checklist in `docs/DEBUGGING.md`.
