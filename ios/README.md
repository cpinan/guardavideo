# Guardavideo para iOS

SwiftUI sobre el módulo compartido de Kotlin (`../android/shared`): misma detección de enlaces,
parser HLS, descargador (con AES-128 vía CommonCrypto) y biblioteca (Room). Solo enlaces directos
(.m3u8 / .mp4), igual que en Android.

```bash
make ios-build        # xcodegen + xcodebuild para el simulador (la fase de build compila el framework Kotlin)
open ios/Guardavideo.xcodeproj   # para ejecutarlo en un iPhone: elige tu equipo de firma en Signing
```

- **Reproducción:** AVPlayer no abre HLS desde un archivo. Las descargas .ts traen un `.m3u8` local que las
  indexa por rangos de bytes, y `LocalMediaServer` lo sirve en `127.0.0.1` (con token aleatorio): AVPlayer
  lo reproduce como cualquier HLS, con saltos precisos y PiP, sin red.
- **Compartir → Guardavideo:** no hay extensión de compartir; crea un Atajo que abra
  `guardavideo://download?url=` + el enlace (o `guardavideo://add?url=…` para solo rellenarlo).
- **Exportar:** mantén pulsado un video → «Compartir o guardar», o abre la app Archivos → En mi iPhone → Guardavideo.
- **Segundo plano:** iOS da solo unos minutos fuera de la app; las descargas largas se reanudan al volver.
- `xcodegen` genera el proyecto desde `project.yml`; el `.xcodeproj` no se versiona.
