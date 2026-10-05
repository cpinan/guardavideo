# Guardavideo para Android

Descarga videos **en el teléfono**, sin la computadora:
- **Páginas de sitios** (Dailymotion, YouTube, TikTok… los ~1.750 de yt-dlp): con yt-dlp embebido
  (`youtubedl-android`, la librería de Seal: Python + yt-dlp + ffmpeg). yt-dlp se actualiza solo una vez al día.
- **Enlaces directos** a un stream HLS (`.m3u8`, también cifrado AES-128) o a un archivo (`.mp4`, `.webm`,
  `.mov`, `.mkv`, `.ts`): con el descargador propio del módulo compartido.
- Los servicios con DRM (Netflix, Disney+…) nunca se descargan.
- APK solo `arm64-v8a` (teléfonos actuales y emuladores en Apple silicon); pesa más por el runtime de Python.

- Compartir → Guardavideo desde el navegador rellena el enlace.
- La descarga sigue en segundo plano (WorkManager, notificación con progreso) y se reanuda si el sistema la detiene.
- Biblioteca con miniaturas, favoritos y reproducción con Media3 que continúa donde la dejaste.

## Dos versiones (product flavors, IDs distintos: se instalan a la vez)

| Flavor | applicationId | Nombre | Qué descarga | Para |
|---|---|---|---|---|
| `full` | `com.carlospinan.guardavideo` | Guardavideo+ | sitios (yt-dlp) + enlaces directos | tú y amigos (APK directo, firmado con la clave debug local) |
| `play` | `com.carlospinan.guardavideo.play` | Guardavideo | solo enlaces directos; sin yt-dlp/Python/ffmpeg (~12 MB) | Google Play (AAB, clave de subida en `android/keystore.properties`) |

El código de yt-dlp vive solo en `androidApp/src/full`; `src/main` usa las interfaces `SiteSupport` y
`FragmentedMp4Fixer`, que cada `FlavorModule` (Hilt) implementa.

```bash
make android-check      # tests (KMP + app) + APK debug
make android-install    # full debug en el emulador o dispositivo ($ANDROID_SERIAL)
make android-release    # full release (R8) encima de la debug, conserva datos
make android-play-bundle  # AAB de Play → android/release/play/<versionCode>-<versionName>-release.aab
android/tools/dev.sh play # instala la play debug
android/tools/dev.sh share "https://…/video.m3u8"   # abre la app como si compartieras el enlace
```

Estructura: `shared/` (KMP Android + iOS: detección de enlaces, parser HLS, descargador, Room) y
`androidApp/` (Compose, Hilt, WorkManager, Media3). Los archivos quedan en
`Android/data/<applicationId>/files/Movies` (se borran al desinstalar).
