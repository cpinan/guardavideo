# Guardavideo para Android

Descarga videos **en el teléfono**, sin la computadora: enlaces directos a un stream HLS (`.m3u8`,
también cifrado AES-128) o a un archivo (`.mp4`, `.webm`, `.mov`, `.mkv`, `.ts`). Los enlaces a páginas
(YouTube, TikTok…) necesitan yt-dlp, que solo corre en el servidor; la app lo explica en vez de fallar.
Los servicios con DRM (Netflix, Disney+…) nunca se descargan.

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
