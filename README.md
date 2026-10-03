# Guardavideo

Descarga videos desde el navegador con progreso en vivo. API FastAPI + frontend React/TypeScript.

**Repositorio (privado):** <https://github.com/cpinan/hls-web>
**Uso local:** tras arrancar (ver [Cómo usarlo](#cómo-usarlo)) abre <http://localhost:5173>
· API y documentación interactiva: <http://localhost:8000/docs>

> No hay despliegue público: es una herramienta de uso personal/local y de aprendizaje.
> No evade DRM. Respeta los términos de cada sitio y no redistribuyas contenido.

## Qué hace
- **Enlace `.m3u8` directo** → descargador HLS propio (segmentos en paralelo, AES-128, ffmpeg).
- **Cualquier otro sitio** (YouTube, Vimeo, Dailymotion…) → yt-dlp.
- **Dos botones**: **Procesar y descargar** (al terminar, el navegador baja el archivo solo, incluso si
  recargas la página mientras procesa) y **Solo procesar** (queda en la lista para verlo o bajarlo después).
- **Reproductor en línea** (botón *Reproducir* / *Continuar 1:23*): vista previa con Range (saltas a cualquier
  minuto sin bajar todo). Guarda minuto, segundo, volumen y velocidad; al recargar sigue donde lo dejaste.
  Se puede **ampliar a lightbox** (Esc / clic fuera para cerrar) y sacar a **Picture-in-Picture**; cambiar de
  modo no corta la reproducción.
- **Biblioteca**: filtros (todas / en curso / listas / con error), búsqueda, grupos por día, resumen de espacio,
  "Limpiar con error", errores traducidos a lenguaje claro y avisos (toasts) al iniciar descargas.
- **Estantes de la biblioteca**: *Seguir viendo*, *Favoritos* ★, *Más vistos* y *Recién descargados*; filtro de
  favoritos y orden por recientes / vistas / nombre / tamaño. Favoritos y vistas se guardan en la base de datos
  (valen desde el teléfono y el computador); *Seguir viendo* usa la posición guardada en cada navegador.
- **Interfaz**: tema claro/oscuro/sistema, diseño de dos columnas en pantallas anchas, instalable como app (PWA).
- **Progreso en vivo** (SSE) con anillo/barra animada, tiempo restante, miniatura y duración del video.
- **Historial local en SQLite**: las descargas sobreviven a reinicios; se pueden reintentar o eliminar.
- **Sin descargas repetidas**: huella SHA-256 por URL+calidad y por contenido del archivo.
- **Resiliencia**: reintentos con backoff ante cortes de red (backend y frontend), reconexión del SSE,
  caché local para recargar la página sin perder la lista, aviso de "sin conexión".
- **Herramientas de depuración**: escenarios simulados y red simulada (ver [Depuración](#depuración)).
- **Móvil**: diseño adaptable, áreas táctiles de 44–48 px, modo claro/oscuro, respeta `prefers-reduced-motion`.

## Requisitos
- Python 3.11+
- Node 20+
- `ffmpeg` en el PATH (`brew install ffmpeg` / `apt install ffmpeg`)

## Cómo usarlo
```bash
git clone git@github.com:cpinan/hls-web.git && cd hls-web

python3 -m venv backend/.venv && source backend/.venv/bin/activate
make install          # dependencias de backend y frontend

make dev-api          # terminal 1 → http://localhost:8000/docs
make dev-web          # terminal 2 → http://localhost:5173
```
Pega una URL, elige la calidad y pulsa:
- **Procesar y descargar**: procesa y, al terminar, el navegador baja el archivo solo.
- **Solo procesar**: queda en tu biblioteca; la ves en el reproductor o la bajas cuando quieras.

Los videos quedan en `backend/downloads/` (junto a la base `hlsweb.db`). Desde cada tarjeta puedes
**Reproducir** (o ampliar a lightbox / Picture-in-Picture), **Descargar**, marcar ★, **Reintentar** o **Eliminar**.

Para probar con un stream en localhost (desactiva la protección SSRF; **solo desarrollo**):
```bash
HLSWEB_ALLOW_PRIVATE_URLS=true make dev-api
```

### Desde el móvil
Con la API corriendo (`make dev-api`), en otra terminal:
```bash
make share      # imprime las URL para el teléfono y abre el frontend en la red
```
- **Misma Wi-Fi:** abre la URL `http://<ip-de-tu-pc>:5173` que imprime (teléfono y computador en la misma red;
  si no carga, revisa que la Wi-Fi no tenga "aislamiento de clientes" y que no uses datos móviles).
- **Probar sin red real:** añade `?debug` a la URL y lanza un escenario del panel 🐞 (requiere `make dev-api-debug`).
- **Instalar como app:** en Android Chrome *Menú → Instalar aplicación*; en iOS Safari *Compartir → Añadir a pantalla de inicio*.
- **Desde cualquier lugar (gratis, privado):** instala [Tailscale](https://tailscale.com/download) en el equipo y el
  teléfono, con la misma cuenta; abre `http://<nombre>.<tailnet>.ts.net:5173`. No se expone nada a internet.
- Solo el puerto 5173 queda visible; la API escucha en `localhost` y el proxy de Vite la alcanza.
- Esta app **no tiene login**: úsala solo en redes de confianza (tu Wi-Fi o tu tailnet), nunca expuesta a internet.

## Pruebas
Todo se ejecuta desde la raíz con `make`:

| Comando | Qué corre |
|---|---|
| `make test-fast` | Backend sin ffmpeg (rápido, para iterar) |
| `make test` | Backend completo + frontend |
| `make lint` · `make typecheck` | ruff + eslint · mypy + tsc |
| **`make check`** | **Todo lo anterior + build.** Es lo que corre el CI |

Estado actual: **131 pruebas de backend** (pytest) y **276 de frontend** (Vitest + Testing Library).

**Backend** (`backend/tests/`)
- Unitarias: repositorios (memoria y SQLite con el mismo contrato), config, hashing, seguridad SSRF, registro de descargadores.
- API con un `FakeDownloader`: ciclo de vida, cancelar, reintentar, eliminar, SSE, 404/409/422.
- SQLite: persistencia entre reinicios, migraciones, recuperación al arrancar, escrituras concurrentes.
- Duplicados: por URL y por contenido, archivos compartidos entre jobs.
- Reproducción: stream con `Range` (206), duración con ffprobe, relleno de duraciones.
- Biblioteca: favoritos y vistas (atómicas con concurrencia), estadísticas sin contar dos veces archivos compartidos.
- Migraciones SQLite v1→v4 con datos intactos, y rechazo de una BD más nueva que la app.
- Integración con ffmpeg (`@pytest.mark.integration`): HLS plano y AES-128, servidor que falla a propósito
  (reintentos, backoff cancelable, 4xx sin reintento), miniaturas.
- Los tests esperan con `wait_for`, nunca con `sleep` fijos.

**Frontend** (`frontend/src/**/*.test.*`)
- Cliente HTTP + zod, reintentos y backoff, caché local (`persist`), red simulada, ETA y formatos.
- Hooks: reconexión del SSE, parcheo de la caché sin tocar las demás filas.
- Componentes por rol/label (accesibilidad): formulario (ambos botones), tarjetas, lista, panel de depuración, `App` completa.
- Descarga automática (una sola vez, tras recargar, se cancela si falla) y reproductor (reanuda, guarda cada segundo, marca "visto").
- Lightbox (Esc, clic fuera, foco atrapado, mismo `<video>` al cambiar de modo) y Picture-in-Picture (soportado, rechazado, oculto mientras suena).
- Biblioteca: estantes (seguir viendo / favoritos / más vistos / recientes), filtros, orden, búsqueda, grupos por día,
  estrella optimista con reversión, errores traducidos, avisos, tema y portapapeles.

Un solo test:
```bash
cd backend  && pytest tests/test_dedupe.py::test_force_downloads_again -q
cd frontend && npx vitest run src/features/jobs/JobRow.test.tsx
```

## Depuración
```bash
make dev-api-debug     # escenarios simulados + URLs locales
make dev-web
```
Aparece el botón **🐞 Depuración** (o añade `?debug` a la URL): lanza escenarios (`ok`, `slow`, `flaky`,
`network`, `fail`, `late-fail`, `stall`), simula pérdida de red/latencia y muestra un registro de eventos.
Checklist completa en [`docs/DEBUGGING.md`](docs/DEBUGGING.md).

## Configuración
Variables de entorno `HLSWEB_*` (plantilla en [`.env.example`](.env.example), cópiala a `backend/.env`):
carpeta y base de datos, concurrencia, reintentos de red, CORS, modo debug.

## Arquitectura
```
backend/app/   api → services → repositories (SQLite) / downloaders (hls, yt-dlp)   ·   security (SSRF)
frontend/src/  lib (api+zod, retry, persist, playback, shelves, theme, toast, debug)  ·  features/jobs  ·  features/debug
```
Más detalle en [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Convenciones y flujo de trabajo en [`CLAUDE.md`](CLAUDE.md).

## Límites
- Sin DRM (Widevine/FairPlay) y la protección SSRF (`security.py`) no se debilita fuera de desarrollo.
- Mantén yt-dlp al día: `pip install -U yt-dlp`. Si un sitio falla tras actualizar, suele ser un cambio del sitio.
- Verificado a mano: descarga real de Dailymotion con yt-dlp y streams HLS locales. No probado: YouTube/Vimeo,
  el `Dockerfile`, y reproducción/PiP en navegadores móviles reales (la lógica está cubierta por pruebas).
- CI (GitHub Actions) corre `make check` en cada push.
