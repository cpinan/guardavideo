# STATUS — Guardavideo

_Last updated: 2026-10-02 · branch `main` · 0 uncommitted files_

## Next action
Open `http://192.168.68.104:5173/?debug` on a real phone and check lightbox, Picture-in-Picture and resume-after-reload (the only parts never run in a real mobile browser).

## State
- Repo `github.com/cpinan/hls-web` (private), CI green on every push (`make check`).
- Backend: HLS + yt-dlp downloads, network retries, thumbnails, ffprobe duration, SQLite history (schema v4), SHA-256 dedupe (URL + content), startup recovery, Range `/stream`, stats, favorites + view counts.
- Frontend: two download modes (process+download / process only), inline player + lightbox + PiP that resume by second, library shelves/filters/sort/search/day groups, toasts, friendly errors, light/dark/system theme, PWA manifest, mobile layout, debug panel.
- Verified by hand: real Dailymotion download via yt-dlp; local HLS streams; desktop + 390/320px layouts in headless Chrome.
- Not built: login/auth, public deployment, "delete oldest videos" cleanup, i18n (Spanish only), app rename.

## In flight
Nothing in flight; working tree clean and pushed (last commit `bc4ee60`).

## Verify
```bash
tools/verify.sh        # = make check: 131 backend + 276 frontend tests, lint, types, build
```
Manual: `make dev-api-debug` + `make share`, then follow `docs/DEBUGGING.md`.

## Open questions
- Make the repo public? Audit found no secrets, but commit metadata exposes name + `carlos.pinan@gmail.com`; fix = noreply email + history rewrite (force-push). Decision pending (asked 2026-10-02); README still says "(privado)".
- App name: shortlist Aljibe, Cauce, Remanso, Keep (not chosen). Rename touches repo, README, title, manifest, package.json, pyproject.
- Hosting: no free host fits as built (needs ffmpeg + disk + auth). Preferred path = own machine + Tailscale. A one-container build with login is offered, not started.
- Chrome extension was never connected this session, so no click-through UI test has been done.

## Do not redo
- Don't spy on `localStorage.setItem` in tests: breaks on jsdom's real Storage (CI Node 20). Spy on `lib/storage` or `vi.stubGlobal("localStorage", …)`.
- Don't use `-ss` when extracting a frame from a still image (ffmpeg writes nothing); `extract_frame` skips it for offset 0.
- Don't run `_cleanup` when deleting a job record: it globs `{job_id}.*` and would delete a file shared with another job.
- Don't remount the `<video>` when switching inline/lightbox/hidden: one instance keeps playback and PiP alive (CSS-only mode switch).
- Headless Chrome enforces a ~500px min window: check mobile layouts via a 390px iframe on an http page (not file://), with a delayed resource to wait for render.
- Don't auto-retry POST/DELETE mutations (would duplicate downloads).
