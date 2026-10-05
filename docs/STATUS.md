# STATUS — Guardavideo

_Last updated: 2026-10-04 · branch `main` · 0 uncommitted files_

## Next action
Install `android/release/1-0.1.0-debug.apk` on the Pixel (`ANDROID_SERIAL=58281FDCQ005US make android-install`) and run the phone checklist: Share → Guardavideo from Chrome, background download with notification, PiP, "Guardar en la galería".

## State
- Web + API (Mac): HLS + yt-dlp downloads, SQLite library, shelves, mobile UI, `make serve` (production on 127.0.0.1:8080, debug off) + `make tailscale-serve` for private sharing. Repo `github.com/cpinan/guardavideo` (private); CI runs `check` (web) and `android` jobs, both green.
- Android (`android/`, KMP `:shared` + `:androidApp`): downloads **on the phone**, direct links only (.m3u8 incl. AES-128, .mp4/.webm/.mov/.mkv/.ts); site pages (YouTube…) are explained, DRM refused. WorkManager foreground worker, Room library, Media3 player with resume + PiP, .ts→.mp4 remux (Transformer, no re-encode), save to gallery (MediaStore), Share target. minSdk 29. Verified on an API 36 emulator.
- iOS (`ios/`, SwiftUI on the same `:shared`): same downloads; TS played through a local byte-range .m3u8 served by `LocalMediaServer` (127.0.0.1 + token) because AVPlayer won't play HLS from a file path. PiP, resume, ShareLink export, `guardavideo://download?url=…` for Shortcuts. Verified on an iOS 26.5 simulator (download + AES); playback verified with AVPlayer on macOS against the same server file.
- Not built: iOS share extension, iOS background downloads beyond the system's few minutes, Tailscale sharing not finished (needs sign-in), cookies for login-only sites, real-device runs of either app.

## In flight
Nothing in flight; all committed and pushed. Production server (`make serve`) may still be running from this session on :8080.

## Verify
```bash
tools/verify.sh        # make check (141 backend + 294 web tests, lint, types, build) + make android-check (36 shared + 6 app tests, debug APK)
make ios-build         # XcodeGen + xcodebuild for the simulator (builds the Kotlin framework)
make ui-audit          # web layout audit (needs dev-api + dev-web)
```
Device: `android/tools/dev.sh install|share URL|shot|ui|tap|files` (set `ANDROID_SERIAL`).

## Open questions
- Tailscale (2026-10-03): Tailscale.app is open but signed out (VPN "Connecting", CLI error 1). Needs the user to sign in, approve the VPN config, and enable HTTPS certificates; then run `make tailscale-serve`.
- Make the repo public? Pending since 2026-10-02 (commit email exposure; would need a history rewrite).
- iOS on a real iPhone needs a signing team chosen in Xcode (personal team is fine for sideloading, 7-day expiry).

## Do not redo
- ViewModels never receive `Context` (user rule): build context-needing objects in Hilt modules (`PlayerModule` gives `Player`), inject interfaces (`DownloadQueue`, `VideoExporter`).
- AVPlayer does not play a local `.m3u8` from a file URL (stays `.unknown`, no error) and `AVAssetResourceLoader` cannot serve HLS media segments: use the loopback server.
- AGP 9 KMP library plugin: without `withHostTest {}` shared tests silently never run. Use `kotlin-test-junit` (not `kotlin-test`) in Android-only modules.
- Gradle in an Xcode build phase needs `ENABLE_USER_SCRIPT_SANDBOXING: NO`; exclude the x86_64 simulator slice (Kotlin builds arm64 only).
- `emulator-5554` (and other emulators) are used by other sessions (PianoApp): start a dedicated AVD on its own port (e.g. `-port 5570`).
- zsh does not word-split `$VAR` (`$ADB -s …` fails, `set -- $xy` fails): use `tools/dev.sh` or `${=var}`.
- Don't rename internal `hlsweb` keys (`HLSWEB_*`, `hlsweb:*`, `hlsweb.db`).
- Web: grid/flex items holding horizontal scrollers need `min-width: 0`; `.job` uses `animation-fill-mode: backwards` (a filled transform traps the fixed lightbox).
- Don't run full `recover_jobs` against the live DB while the API runs; call `_rewrite_fragmented(repo)` alone.
- Don't auto-retry POST/DELETE mutations; don't `_cleanup` on delete (shared files).
