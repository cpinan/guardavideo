# STATUS — Guardavideo

_Last updated: 2026-10-03 · branch `main` · 0 uncommitted files_

## Next action
Do one real download from each of YouTube, TikTok, X and Vimeo, because the "Sitios compatibles" list (`frontend/src/lib/providers.ts`) has only been tested with Dailymotion.

## State
- Repo `github.com/cpinan/guardavideo` (private, renamed from `hls-web` on 2026-10-03). CI runs `make check` on every push.
- Backend: HLS + yt-dlp downloads, network retries, thumbnails, ffprobe duration, SQLite history (schema v4), SHA-256 dedupe (by URL and by content), startup recovery, `/stream` with byte ranges, stats, favorites and view counts. Fragmented MP4s from yt-dlp are rewritten as regular MP4s (`-c copy +faststart`) after download and at startup.
- Frontend: library shelves with ‹ › arrows when used with a mouse; on phones, finished videos in each day group scroll sideways as a carousel and the form collapses to just the URL field until tapped; light/dark theme; PWA manifest. A curated list of 61 supported sites sits in a collapsible card, and the URL field names the site (it blocks DRM services and warns when login is needed).
- Checked by hand on a real phone on 2026-10-03: full-screen player, Picture-in-Picture, resume after reload, quick start of the rewritten MP4s, carousel, compact form. Checked in headless Chrome with `make ui-audit` at 320/390/820/1280px.
- Not built: login/auth, public deployment, HTTPS for LAN sharing, passing cookies to yt-dlp (sites that need an account), cleanup of the oldest videos, i18n (UI is Spanish only).

## In flight
Nothing in flight; working tree clean and pushed (last code commit `ddf1206`).

## Verify
```bash
tools/verify.sh        # = make check: 137 backend + 294 frontend tests, lint, types, build
make ui-audit          # with dev-api + dev-web running: overflow / tap targets / labels, screenshots in ui-audit-out/
```
Phone: `make dev-api-debug` + `make share`, open `http://192.168.68.104:5173` (type `http://`; the phone browser's "Always use secure connections" setting must be off).

## Open questions
- HTTPS for phone sharing (deferred 2026-10-02): Tailscale `serve` (real certificate; needs Tailscale signed in on the Mac and phone, plus HTTPS certificates enabled) or `@vitejs/plugin-basic-ssl` (one certificate warning). Tailscale is preferred.
- Make the repo public? Commit metadata exposes name + `carlos.pinan@gmail.com`; the fix is a noreply email plus a history rewrite (force-push). Pending since 2026-10-02.
- Sites that need an account (Instagram, Patreon, Nebula…): pass browser cookies to yt-dlp? Not started.

## Do not redo
- Don't rename internal `hlsweb` keys (`HLSWEB_*` env, `hlsweb:*` localStorage, `hlsweb.db`): doing so wipes history, playback positions and `.env` settings. Only user-facing names changed.
- Don't use `animation-fill-mode: both` with a transform animation on an element that contains the lightbox: Chrome keeps the transform, which turns the card into a containing block and traps `position: fixed`. `.job` uses `backwards`.
- Grid/flex items that hold horizontal scrollers need `min-width: 0` (`.shelf`, lightbox `.player-frame`), or they widen the whole page.
- Visually hidden text inside the carousel (`.btn-label`) needs a positioned parent, or it widens the page.
- Don't spy on `localStorage.setItem` in tests: breaks on jsdom's real Storage (CI Node 20). Spy on `lib/storage` or `vi.stubGlobal("localStorage", …)`.
- Don't use `-ss` when extracting a frame from a still image (ffmpeg writes nothing); `extract_frame` skips it for offset 0.
- Don't run `_cleanup` when deleting a job record: it globs `{job_id}.*` and would delete a file shared with another job.
- Don't remount the `<video>` when switching inline/lightbox/hidden: one instance keeps playback and PiP alive (CSS-only mode switch).
- Headless screenshots: `--screenshot` / `--dump-dom` hang on this app (SSE/video never finish loading); drive Chrome over CDP like `tools/ui-audit.mjs`. Full-page captures show lazy images and fixed buttons wrongly; trust viewport shots.
- Don't run the full `recover_jobs` against the live DB while the API runs: it marks queued/running jobs as interrupted. To fix files by hand, call `_rewrite_fragmented(repo)` alone.
- Don't auto-retry POST/DELETE mutations (would duplicate downloads).
