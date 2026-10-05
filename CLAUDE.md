# Guardavideo

API FastAPI + frontend React/TS para descargar videos con progreso en vivo (SSE).
`.m3u8` directo → descargador HLS propio; cualquier otro sitio → yt-dlp. Proyecto de aprendizaje, uso local.

## Comandos (todo se hace desde la raíz con `make`)
- `make install` · `make dev-api` (:8000, /docs) · `make dev-web` (:5173, proxy /api → :8000)
- `make dev-api-debug`: escenarios simulados (`http://debug.test/<escenario>`) + panel 🐞 en la UI. Ver `docs/DEBUGGING.md`.
- `make test-fast` (backend sin ffmpeg) · `make test` · `make lint` · `make typecheck` · `make fmt`
- **`make check` debe pasar antes de dar una tarea por terminada** (es lo mismo que corre el CI).
- Cambios de CSS/layout: `make ui-audit` (con dev-api y dev-web corriendo) revisa teléfono/tablet/escritorio en claro y oscuro: desborde horizontal y objetivos táctiles < 44 px. Capturas en `ui-audit-out/`.
- Un solo test: `cd backend && pytest tests/test_jobs_api.py::test_cancel_running_job -q` · `cd frontend && npx vitest run src/features/jobs/JobRow.test.tsx`
- Requiere `ffmpeg` en el PATH. Para probar con streams en localhost: `HLSWEB_ALLOW_PRIVATE_URLS=true` (solo dev).

## App Android (`android/`)
Proyecto Gradle propio (AGP 9.3.1, Kotlin 2.4.10). Descarga **en el teléfono**, sin servidor: solo enlaces
directos (.m3u8 / .mp4); los enlaces de páginas (YouTube…) necesitan yt-dlp y no funcionan ahí.
- `:shared` (KMP Android + iOS): `source/` detección de enlaces, `hls/` parser, `download/MediaDownloader`
  (Ktor + Okio), `library/` Room KMP + `JobRepository` + `DownloadRunner`. Tests en `commonTest` (MockEngine +
  FakeFileSystem): `./gradlew :shared:testAndroidHostTest` (sin `withHostTest {}` no corren).
- `:androidApp`: Compose M3, Hilt, WorkManager (worker foreground dataSync), Media3, Coil (miniatura del video).
- `make android-check` · `make android-install` · `android/tools/dev.sh` (share/tap/shot/ui/files en el emulador).
- `:androidApp` además: remux .ts→.mp4 con Media3 Transformer (sin recodificar), «Guardar en la galería»
  (MediaStore, minSdk 29), Picture-in-Picture.
- **iOS (`ios/`)**: SwiftUI sobre el mismo `:shared` (`GuardavideoIos` en `iosMain`). `make ios-build`.
  AVPlayer no reproduce HLS desde un archivo: los .ts se indexan en un .m3u8 local (byte ranges) y se sirven
  con `LocalMediaServer` (loopback + token). Sin share extension: `guardavideo://download?url=…` (Atajos).
- **ViewModels nunca reciben `Context`**: lo que lo necesita (ExoPlayer, WorkManager) se crea en un módulo Hilt
  y el VM recibe una interfaz (`Player`, `DownloadQueue`). Textos de UI en `strings.xml`.

## Arquitectura (dependencias hacia adentro)
```
backend/app/
  main.py           create_app(settings, downloaders) + lifespan + handlers de errores
  api/jobs.py       rutas /api/v1/jobs (delgadas: validan, delegan, formatean)
  services/jobs.py  JobService: cola, cancelación, SSE (no conoce FastAPI)
  domain.py         Job, Status          schemas.py   contrato público (sin file_path)
  repositories.py   Protocol JobRepository + InMemory (tests) + Sqlite (historial, migraciones)
  hashing.py        SHA-256: url_key (dedupe por URL) y file_sha256 (dedupe por contenido)
  services/recovery.py  reconcilia BD vs. disco al arrancar
  downloaders/      Protocol Downloader: hls.py, ytdlp.py, registry.py (el orden importa)
  security.py       protección SSRF       config.py    Settings (HLSWEB_*)
  thumbnails.py     fotograma JPEG por job (ffmpeg); nunca debe romper una descarga
frontend/src/
  lib/              api.ts (fetch + zod), schemas.ts, retry.ts (backoff), persist.ts (caché local), debug.ts
  features/jobs/    JobForm, JobList, JobRow, JobPlayer, useJobs, useJobEvents, useAutoDownload (+ tests al lado)
  lib/ (más)        autodownload.ts y playback.ts: estado en localStorage con useSyncExternalStore
docs/               ARCHITECTURE.md, INTERVIEW_CASES.md
```

## Convenciones
**Backend**
- Python 3.11+, tipos completos (mypy `disallow_untyped_defs`), ruff (line-length 100).
- Lógica en `services/`, nunca en las rutas. Errores de dominio (`exceptions.py`) → handlers en `main.py`; no uses `HTTPException` en servicios.
- Un archivo puede ser compartido por varios jobs (contenido idéntico): bórralo solo si ningún otro job lo usa.
- Estado compartido entre hilos: siempre vía el repositorio (lock + copias). Transiciones de estado bajo `JobService._lock`.
- Los descargadores deben llamar `ctx.check_cancelled()` entre pasos y `ctx.report(%)` para el progreso. Esperas con `ctx.sleep()` (cancelable); fallos de red recuperables con `ctx.retrying(n)`; miniatura con `ctx.thumbnail(path)`.
- No expongas rutas del disco (`file_path`) en `schemas.py`.

**Frontend**
- TypeScript estricto, hooks y componentes funcionales, código por feature.
- Estado de UI que debe sobrevivir a recargas (descarga automática, posición del video) vive en `lib/*` con localStorage validado con zod; el estado del servidor, en React Query.
- Mutaciones (POST/DELETE) nunca se reintentan solas. La caché local (`persist.ts`) siempre se valida con zod; el servidor manda.
- Server state solo en React Query; el SSE parchea la caché con `replaceJob` (conserva referencias; `JobRow` usa `React.memo`). No dupliques jobs en `useState`.
- Todo dato de red pasa por zod. Accesibilidad obligatoria: label visible, `aria-invalid`/`aria-describedby`, `role="alert"` en errores, foco visible.
- Tests con Testing Library: consulta por rol/label, no por clases ni estado interno.

## Pruebas
- Backend: unitarias + API con `FakeDownloader` (`tests/conftest.py`) + integración con ffmpeg (`@pytest.mark.integration`). Usa `wait_for`, **nunca `sleep` fijos**.
- Frontend: Vitest + Testing Library; `FakeEventSource` y `makeClient()` en `src/test/utils.tsx`.
- Todo cambio de comportamiento lleva su test. Un bug se corrige primero con un test que falle.

## Recetas
- **Nueva fuente de descarga:** clase en `downloaders/` que cumpla `Downloader` → registrarla en `registry.py` antes de `YtDlpDownloader` → tests con servidor local, sin red externa.
- **Nuevo endpoint:** schema en `schemas.py` → método en `JobService` → ruta delgada en `api/jobs.py` → test de API → actualizar el cliente en `lib/api.ts`.
- **Persistencia:** ya hay `SqliteJobRepository`. Campo nuevo en `Job` → columna + migración (`SCHEMA_VERSION`, `ALTER TABLE`) + test de migración. Otro motor: implementar `JobRepository` y cambiarlo en `create_app`.
- **Yt-dlp roto con un sitio:** `pip install -U yt-dlp`, volver a probar; si persiste, es un cambio del sitio, no del código.

## Límites (importante)
- **No evadir DRM** (Widevine/FairPlay) ni añadir código para saltarlo. `allow_unplayable_formats` debe seguir en `False`.
- **No debilitar la protección SSRF** (`security.py`) ni dejar `ALLOW_PRIVATE_URLS` activo fuera de desarrollo/tests.
- Solo uso personal con contenido propio o autorizado; no preparar el proyecto para hospedarlo públicamente sin revisión legal.
- No commitear `downloads/`, `.env`, `node_modules/` ni claves.

## Flujo de trabajo con Claude Code
1. Lee los archivos relevantes y propone un plan corto antes de editar (cambios grandes: usa modo plan).
2. Cambios pequeños y enfocados; no refactorices lo que no se pidió.
3. Corre `make test-fast` mientras iteras y `make check` al final.
4. Resume qué cambió, qué probaste y qué NO pudiste probar (p. ej. sitios reales sin red).
5. Commits atómicos con mensajes en imperativo (`feat:`, `fix:`, `test:`, `docs:`).
