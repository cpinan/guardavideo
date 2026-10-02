# Arquitectura y decisiones

## Backend (FastAPI)
```
app/
  main.py            create_app() + lifespan + handlers de excepciones   (composición)
  api/jobs.py        rutas HTTP delgadas, versionadas en /api/v1          (capa HTTP)
  schemas.py         contrato público (Pydantic)
  services/jobs.py   JobService: casos de uso, cola, cancelación, SSE     (aplicación)
  domain.py          Job, Status                                          (dominio)
  repositories.py    Protocol JobRepository + InMemory (thread-safe)      (puerto/adaptador)
  downloaders/       Protocol Downloader: hls.py, ytdlp.py, registry.py   (puerto/adaptadores)
  security.py        protección SSRF
  config.py          Settings por variables de entorno (HLSWEB_*)
```
Dependencias hacia adentro: api -> services -> domain; services depende de *Protocols*, no de implementaciones.

| Decisión | Por qué | Costo / límite |
|---|---|---|
| App factory + lifespan | Tests con config y descargadores falsos; sin globales | Un poco más de código de arranque |
| `Downloader` como Protocol | Agregar una fuente = una clase; Open/Closed | Hay que mantener el orden en el registro |
| Repositorio detrás de un Protocol | Pasar a Postgres sin tocar el servicio | In-memory pierde jobs al reiniciar y no escala a varios procesos |
| Errores de dominio -> handlers | Rutas sin try/except repetido; códigos HTTP consistentes | |
| Segmentos a disco | Memoria acotada con videos grandes | Más I/O |
| Fail fast (SSRF en `submit`) | 400 inmediato en vez de un job que falla después | DNS bloqueante dentro del endpoint sync |
| SSE con latido | Sobrevive a proxies con timeout | Polling interno cada 300 ms |
| 404 antes de abrir el stream | Tras el primer byte ya no se puede cambiar el status | |

## Frontend (React + TypeScript)
- **Por features** (`features/jobs/`): componentes, hooks, tests y utilidades juntos.
- **Server state en React Query como única fuente de verdad**; el SSE *parchea la caché* (`setQueryData`) y conserva las referencias de los jobs que no cambiaron, así `React.memo` evita re-renders.
- **Contrato validado con zod** en el borde (respuestas y eventos SSE).
- **Accesibilidad**: `label` visibles, `aria-invalid`/`aria-describedby`, `role="alert"` en errores, `progress` con nombre accesible, foco visible, objetivos táctiles de 44 px, modo oscuro y `prefers-reduced-motion`.
- **Resiliencia**: `ErrorBoundary`, estados de carga/vacío/error, cleanup de `EventSource`.
- **Proxy de Vite** en desarrollo: mismo origen, sin CORS.

## Estrategia de pruebas
| Nivel | Qué | Cómo |
|---|---|---|
| Unitarias | SSRF, selección de variante, descifrado AES, URL | pytest / vitest, sin red |
| API | rutas, códigos HTTP, cancelación, SSE | TestClient + `FakeDownloader` (sin ffmpeg ni red) |
| Integración | HLS real (plano y AES-128) | ffmpeg genera el stream, servidor HTTP local, marcador `integration` |
| Componentes | formulario, fila, hook SSE | Testing Library: se consulta por rol/label, `EventSource` falso |
| Estáticas | ruff, mypy, eslint, tsc | CI en cada push |

Principios: dobles en los bordes (red, tiempo), esperas con *timeout* y no `sleep` fijos, probar comportamiento visible y no implementación.

## Qué falta para producción
Persistencia (Postgres) y cola (Redis + workers separados), autenticación y cuotas por usuario, almacenamiento de objetos con URLs firmadas, expiración de archivos, métricas/trazas, límites de tamaño y duración, y una revisión legal de qué fuentes se permiten.
