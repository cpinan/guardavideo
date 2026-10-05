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
- Uso personal / instalación directa (no Google Play).

```bash
make android-check      # tests (KMP + app) + APK debug
make android-install    # instala en el emulador o dispositivo ($ANDROID_SERIAL)
android/tools/dev.sh share "https://…/video.m3u8"   # abre la app como si compartieras el enlace
```

Estructura: `shared/` (KMP Android + iOS: detección de enlaces, parser HLS, descargador, Room) y
`androidApp/` (Compose, Hilt, WorkManager, Media3). Los archivos quedan en
`Android/data/com.carlospinan.guardavideo/files/Movies` (se borran al desinstalar).
