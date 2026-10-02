# Preparación de entrevista: preguntas y casos

Generado desde las flashcards. Categorías: FastAPI, React, Diseño, Arquitectura, Testing, UI y a11y, Casos, Android.

## FastAPI

### ¿Qué es FastAPI y por qué es rápido?

Framework web ASGI construido sobre Starlette y Pydantic. Es rápido por ASGI/async (se sirve con uvicorn) y porque Pydantic v2 valida con un núcleo en Rust. Genera OpenAPI y /docs automáticamente a partir de los type hints.

### WSGI vs ASGI

WSGI es síncrono: una petición ocupa un worker y no soporta conexiones largas. ASGI es asíncrono y soporta WebSockets, SSE y conexiones de larga duración. FastAPI es ASGI; Flask/Django clásico son WSGI.

### def vs async def en un endpoint

async def corre en el event loop: nunca bloquees ahí dentro (time.sleep, requests, CPU pesada) porque congelas todo el servidor.
def normal lo ejecuta FastAPI en un threadpool, así que es seguro con código bloqueante.
Regla: async def solo si usas librerías async (await).

### ¿Qué hace Depends?

Inyección de dependencias: funciones reutilizables (auth, sesión de BD, paginación) que declaras como parámetros. Se resuelven por request, se cachean dentro de la misma request y en tests se reemplazan con app.dependency_overrides.

### ¿Cómo valida FastAPI la entrada?

Con tipos y modelos Pydantic: HttpUrl, Field(ge=, le=), etc. Si falla, responde 422 con el detalle del error automáticamente. La anotación decide si el dato viene del path, query o body.

### ¿Para qué sirve response_model?

Valida y filtra la salida: evita exponer campos sensibles (ej. password_hash) y documenta el esquema de respuesta en OpenAPI.

### Tareas largas: ¿cómo las manejas?

BackgroundTasks: solo cosas cortas tras responder.
ThreadPool/ProcessPool: trabajo local simple (lo que usa este proyecto).
Cola + workers (Celery, RQ, arq con Redis): trabajos pesados, reintentos, durabilidad y escalado.
Limitación del proyecto: el estado está en memoria, se pierde al reiniciar y no funciona con varios workers.

### SSE vs WebSocket vs polling

Polling: simple pero desperdicia requests y tiene latencia.
SSE: unidireccional servidor→cliente sobre HTTP, reconecta solo (EventSource). Ideal para progreso.
WebSocket: bidireccional, ideal para chat o juegos, más complejo de escalar y balancear.

### Seguridad básica de una API

Auth con OAuth2/JWT vía Depends, CORS con orígenes explícitos, rate limiting, validación estricta, límites de tamaño, HTTPS, no exponer rutas internas del disco y protección SSRF si el servidor hace requests a URLs del usuario.

### ¿Cómo pruebas una API FastAPI?

TestClient (o httpx.AsyncClient para async) con pytest. Usas fixtures y dependency_overrides para simular BD o auth. Pruebas unitarias para la lógica en core.py y pocas pruebas de integración para los endpoints.

### Lifespan events

Código de arranque y cierre (abrir/cerrar pool de BD, cargar modelos, iniciar clientes). Se define con el parámetro lifespan= y un async context manager; reemplaza a los antiguos on_event('startup'/'shutdown').

### Códigos HTTP que debes saber usar

201 creado, 202 aceptado (procesamiento asíncrono), 204 sin contenido, 400/422 entrada inválida, 401 no autenticado, 403 sin permiso, 404 no existe, 409 conflicto/estado inválido, 429 rate limit, 5xx error del servidor.

## React

### Virtual DOM y reconciliación

React mantiene una representación en memoria de la UI. Al cambiar el estado, renderiza de nuevo, compara (diff) con el árbol anterior y aplica solo los cambios mínimos al DOM real.

### ¿Por qué importa la key en listas?

Permite a React identificar qué elemento es cuál entre renders. Debe ser estable y única (un id, no el índice si la lista se reordena). Con claves malas se pierde estado local o se re-montan componentes.

### useEffect: dependencias y cleanup

Corre después del render. El array de dependencias controla cuándo se re-ejecuta ([] = una vez al montar). La función de retorno hace cleanup (cerrar EventSource, quitar listeners, cancelar timers). En StrictMode (desarrollo) los efectos corren dos veces para detectar cleanups faltantes.

### useState vs useReducer

useState para estado simple e independiente. useReducer cuando hay varios valores relacionados o transiciones con reglas (acciones explícitas, lógica testeable). Lo que se puede calcular a partir de otro estado no se guarda: se deriva en el render.

### useMemo, useCallback y React.memo

Memorizan valores, funciones y componentes para evitar trabajo o re-renders innecesarios. Úsalos tras medir un problema real o cuando pasas props a componentes memoizados o dependencias de efectos. Usarlos en todas partes añade complejidad sin beneficio.

### Estado del servidor vs estado de UI

El estado del servidor (datos remotos) tiene caché, invalidación, reintentos y puede quedar obsoleto: TanStack Query lo maneja (staleTime, invalidateQueries, deduplicación). El estado de UI (inputs, modales) vive en useState/useReducer o contexto.

### Componentes controlados vs no controlados

Controlado: el valor vive en estado de React (value + onChange); permite validar y reaccionar en vivo. No controlado: el DOM guarda el valor y lo lees con ref o FormData; menos código para formularios simples.

### Context: ventajas y problema

Evita el prop drilling para datos globales (tema, usuario). Problema: todos los consumidores re-renderizan cuando cambia el valor. Mitiga separando contextos, memoizando el valor o usando una librería de estado (Zustand, Redux).

### Custom hooks

Funciones que empiezan con use y reutilizan lógica con estado (ej. useJobEvents para SSE). Respetan las reglas de hooks: solo en el nivel superior y solo en componentes u otros hooks.

### Race conditions al hacer fetch en un efecto

Una respuesta lenta anterior puede pisar a una más nueva. Se resuelve con AbortController o una bandera 'cancelled' en el cleanup, o delegando en TanStack Query, que ya lo maneja.

### ¿Qué provoca un re-render?

Cambio de estado propio, cambio de props del padre, cambio de un contexto consumido. Un padre que re-renderiza re-renderiza a sus hijos salvo que estén memoizados.

### Code splitting

React.lazy + Suspense (o import() dinámico en el router) para cargar componentes bajo demanda y reducir el bundle inicial.

### TypeScript: unión de literales, unknown vs any

type Status = 'queued' | 'done' limita valores y permite exhaustividad en switch. unknown obliga a verificar el tipo antes de usarlo; any desactiva el chequeo de tipos.

### Accesibilidad mínima

HTML semántico (button, label, form), aria-invalid y mensajes de error asociados, foco visible, contraste suficiente, navegación por teclado y texto alternativo en imágenes.

## Diseño

### ¿Cómo escalarías el descargador?

Persistir jobs en BD (Postgres); cola en Redis; varios workers separados de la API; archivos en almacenamiento de objetos (S3) con URLs firmadas; progreso publicado por Redis pub/sub; límites por usuario, expiración de archivos y observabilidad (logs, métricas).

### ¿Cómo implementarías cancelar un job?

DELETE /jobs/{id} marca el job como cancelado y activa un threading.Event (o una señal en Redis si hay workers separados). El bucle de descarga revisa el evento entre segmentos, limpia archivos temporales y deja el estado en 'cancelled'.

### ¿Qué es SSRF y cómo se mitiga?

El atacante hace que tu servidor pida URLs internas (localhost, 169.254.169.254, redes privadas). Mitigaciones: resolver el host y rechazar IPs privadas/loopback/link-local, allowlist de dominios, limitar redirecciones y revalidar tras cada una, y aislar la red del worker.

### ¿Cómo hacer SSE reanudable?

Enviar un id: por evento. El navegador reconecta solo y manda el header Last-Event-ID; el servidor reenvía lo que se perdió. En este caso basta con enviar el estado actual al reconectar.

### HLS en una frase

Un master playlist lista calidades; cada media playlist lista segmentos cortos (.ts o fMP4). El reproductor elige calidad según el ancho de banda (ABR) y descarga segmentos por HTTP. Puede usar cifrado AES-128 con clave en una URL; eso no es DRM.

### REST: idempotencia

GET, PUT y DELETE son idempotentes; POST no. Para POST que deben ser seguros de reintentar se usa una Idempotency-Key. Crear un recurso asíncrono suele responder 201/202 con la URL del recurso para consultar su estado.

### Cómo explicar este proyecto en 30 segundos

Una API FastAPI recibe una URL y crea un job que se ejecuta en un pool de hilos; usa un descargador HLS propio o yt-dlp según la fuente. El progreso se transmite por SSE a un frontend React que usa TanStack Query para la lista y un hook con EventSource para el progreso en vivo. Incluye validación, protección SSRF y pruebas.

## Arquitectura

### ¿Cómo está organizado el backend y por qué?

Capas: api (HTTP) → services (casos de uso) → repositories/downloaders (adaptadores). Las rutas son delgadas, el servicio no conoce FastAPI y se prueba sin HTTP. Las dependencias apuntan hacia adentro y el servicio depende de Protocols, no de clases concretas.

### ¿Por qué Downloader es un Protocol?

Es un puerto: HlsDownloader y YtDlpDownloader son adaptadores intercambiables. Agregar una fuente = una clase nueva, sin tocar el servicio (Open/Closed). En tests se inyecta un FakeDownloader. Protocol da tipado estructural: no exige herencia.

### App factory y lifespan

create_app(settings, downloaders) crea la app sin estado global: cada test arma la suya con otra configuración y descargadores falsos. El lifespan crea y cierra recursos (el pool de hilos) de forma ordenada.

### Configuración 12-factor

Settings con pydantic-settings lee variables de entorno (HLSWEB_*). El mismo artefacto sirve para dev, staging y prod, no hay secretos en el código y los valores se validan al arrancar (tipos, rangos).

### Estado compartido entre hilos

El repositorio protege con lock y devuelve copias, no el objeto compartido. Las transiciones críticas (cancelar vs arrancar un job) se hacen bajo un lock. Así se evitan condiciones de carrera entre el hilo del worker y el de la petición.

### Errores de dominio → HTTP

El servicio lanza JobNotFoundError, InvalidJobStateError, UrlNotAllowedError; exception handlers los traducen a 404, 409 y 400. Las rutas no repiten try/except y el dominio no depende de HTTP.

### Versionado y contrato

Las rutas bajo /api/v1 permiten evolucionar sin romper clientes. El contrato público (schemas.py) está separado del modelo interno (domain.py), así no se filtra file_path. En el cliente, zod valida las respuestas en runtime.

### Descargas con memoria acotada

Cada segmento se escribe a disco y se concatena en orden, en vez de guardar todos los bytes en memoria. Además: reintentos con backoff exponencial, clave AES descargada una sola vez (caché con lock) y limpieza de temporales al cancelar.

### Frontend organizado por features

features/jobs/ agrupa componentes, hooks, tests y utilidades del mismo dominio; lib/ tiene lo transversal (api, schemas). Facilita mover o borrar una feature y evita carpetas globales gigantes de components/ y hooks/.

### Server state como única fuente de verdad

El SSE parchea la caché de React Query (setQueryData) en vez de duplicar el job en useState. Un solo lugar de verdad evita estados desincronizados entre la lista, la fila y el servidor.

### Observabilidad mínima

Logs con job_id, endpoint /health (incluye ffmpeg) y, en producción, métricas (duración, errores por descargador), trazas y alertas. Sin esto, un yt-dlp roto se descubre por quejas de usuarios.

## Testing

### Pirámide de tests del proyecto

Muchas unitarias rápidas (SSRF, selección de variante, AES), pruebas de API con FakeDownloader, pocas de integración con ffmpeg real, y pruebas de componentes en el frontend. Además análisis estático (ruff, mypy, eslint, tsc) en CI.

### Fake vs mock

Un fake es una implementación simple y funcional (FakeDownloader escribe un archivo y reporta progreso); un mock verifica llamadas. Prefiere fakes en los bordes: los tests dependen del comportamiento, no de cómo se llama internamente.

### Integración sin depender de internet

ffmpeg genera un stream HLS (plano y cifrado) en un directorio temporal y un servidor HTTP local lo sirve. Es determinista y rápido. Se marca con @pytest.mark.integration y se omite si no hay ffmpeg.

### Tests intermitentes (flaky)

Causas: sleeps fijos, orden de ejecución, estado compartido, red real, relojes. Soluciones: esperas con timeout y polling (wait_for), eventos de sincronización (threading.Event), aislar estado por test (fixtures, app factory) y dobles para red y tiempo.

### Probar concurrencia y cancelación

Un FakeDownloader con un Event que lo mantiene 'colgado' permite cancelar un job running de forma determinista. Con max_concurrent_jobs=1 se fuerza un job en cola para probar la cancelación en cola. El estado final se espera con wait_for.

### Probar SSE

client.stream() del TestClient lee las líneas 'data:' hasta que el job termina. Se verifica que el progreso es monótono y que el último estado es terminal. Un 404 debe ocurrir antes de abrir el stream.

### Testing Library

Consulta por rol, label y texto visible (getByRole, getByLabelText), como lo haría el usuario. userEvent simula interacción real. Evita probar detalles de implementación (estado interno, clases CSS). Para red: fetch falso o MSW; para SSE: un EventSource falso.

### Cobertura: ¿qué porcentaje buscar?

El porcentaje es un indicador, no la meta. Importa cubrir las ramas de riesgo (errores, cancelación, seguridad, estados vacíos) y que los tests fallen si el comportamiento se rompe. 100% con asserts débiles da falsa seguridad.

### CI

En cada push: ruff + format --check, mypy, pytest, eslint, vitest y build. Falla rápido, cachea dependencias e instala ffmpeg para integración. Se puede añadir un job nocturno que pruebe yt-dlp contra URLs reales para detectar roturas.

## UI y a11y

### Formularios accesibles

Label visible asociada con htmlFor/useId, aria-invalid y aria-describedby apuntando al error, role='alert' para anunciarlo, validar al salir del campo (onBlur) en vez de molestar mientras escribe, y deshabilitar el envío si es inválido o está pendiente.

### Los cuatro estados de la UI

Cargando, vacío, error y éxito, cada uno con un mensaje útil y una acción de recuperación (reintentar, cancelar). El botón muestra 'Enviando…' y se deshabilita para evitar el doble envío.

### Progreso accesible

<progress> con aria-label descriptivo (nombre del video) y valor numérico visible. role='status' o aria-live='polite' para cambios importantes; no anuncies cada porcentaje, saturarías al lector de pantalla.

### Teclado y foco

Todo debe operarse con teclado: usa elementos semánticos (button, a, form) en vez de div clicables, foco visible (:focus-visible) y un orden de tabulación lógico.

### Responsive y táctil

Mobile-first: flex-wrap, controles de al menos 44 px, textos largos con ellipsis y title. Respeta prefers-color-scheme y prefers-reduced-motion.

### Rendimiento de listas en vivo

Un evento SSE solo debe re-renderizar la fila afectada: actualizar la caché de forma inmutable conservando las referencias de los demás jobs y envolver JobRow en React.memo. Mide con el Profiler de React DevTools antes de optimizar más.

### Error boundary

Atrapa errores de render de un subárbol y muestra un fallback con botón de reintento en vez de pantalla en blanco. No atrapa errores en event handlers ni código asíncrono: esos se manejan con try/catch o con los estados de error de la mutación.

## Casos

### Caso: el servidor se reinicia con descargas en curso

Hoy los jobs viven en memoria y se pierden. Solución: persistir jobs en BD; al arrancar, marcar como interrumpidos los que estaban running (o reencolarlos si la descarga es reanudable con Range); workers idempotentes, temporales con nombre determinista y limpieza de huérfanos.

### Caso: 1000 usuarios simultáneos

Separar API y workers: la API solo encola (202) en Redis/SQS y workers autoescalables consumen. Límite por usuario y 429 como backpressure, archivos en S3 con URLs firmadas, CDN para entregar y métricas de cola para escalar. Ojo con ancho de banda y con la legalidad.

### Caso: un usuario pega http://169.254.169.254/...

Es SSRF contra el endpoint de metadatos de la nube. Se rechaza al crear el job (resolver el host y exigir IP global), allowlist de dominios, límite de redirecciones revalidando cada una y worker aislado en una red sin acceso interno. En AWS, usar IMDSv2.

### Caso: yt-dlp deja de funcionar con un sitio

Los sitios cambian seguido. Actualizar la dependencia, job nocturno de verificación contra URLs de prueba, mensaje de error claro al usuario, métricas por descargador y yt-dlp aislado en el worker para poder actualizarlo sin redeploy de la API.

### Caso: el SSE se corta detrás de un proxy

Enviar latidos (comentarios ': keep-alive'), desactivar el buffering del proxy (X-Accel-Buffering: no / proxy_buffering off), subir timeouts de lectura y soportar reanudación con id/Last-Event-ID. Como último recurso, el cliente cae a polling.

### Caso: la lista parpadea o va lenta con muchas descargas

Perfilar con React Profiler. Causas típicas: crear objetos nuevos en cada evento, keys inestables, falta de memo. Solución: actualización inmutable que conserva referencias, React.memo en la fila, virtualización si hay cientos de filas y throttling de los eventos de progreso.

### Caso: pasar de memoria a PostgreSQL

Implementar un SqlJobRepository que cumpla el Protocol y cambiarlo en create_app: el servicio y las rutas no cambian. Agregar migraciones (Alembic), transacciones, índices por estado y usuario, y reemplazar el polling del SSE por LISTEN/NOTIFY o pub/sub.

### Caso: agregar autenticación y cuotas

Depends(current_user) con OAuth2/JWT; owner_id en el Job y filtrar todas las consultas por dueño (evita IDOR); rate limiting por usuario y cuotas de almacenamiento y duración. Las URLs de archivo deben exigir autorización o estar firmadas con expiración.

### Caso: ¿cómo lo desplegarías?

Imagen Docker (usuario no root, ffmpeg, HEALTHCHECK), configuración por variables de entorno, CI que corre lint, tipos y tests antes de construir, frontend estático en CDN, API detrás de un balanceador con /health, logs estructurados y métricas. Escalar con workers separados.

### Caso: uso indebido o contenido protegido

Definir términos de uso, limitar a fuentes permitidas (allowlist) y a contenido propio o autorizado, no evadir DRM, rate limiting, registro de auditoría y un proceso para reclamos de derechos de autor. Que algo sea técnicamente posible no implica que esté permitido: es un riesgo de producto.

### Caso: un job se queda 'running' para siempre

Timeouts en httpx y por job, heartbeat o lease del worker, un reaper que marque como error los jobs sin progreso en N minutos y cancelación cooperativa. En la cola: reintentos acotados y dead-letter queue.

## Android

### Arquitectura recomendada en Android

Capa de UI (Compose + ViewModel que expone StateFlow/UiState), capa de datos (repositorios con fuente única de verdad: Room/red) y capa de dominio opcional (use cases). Flujo unidireccional: la UI emite eventos, el ViewModel actualiza el estado, la UI lo observa.

### Descargas largas en Android

Trabajo diferible y garantizado: WorkManager con setForeground para mostrar notificación y evitar que el sistema lo mate. Para transferencias iniciadas por el usuario, considera los user-initiated data transfer jobs de Android 14+. Persistir el progreso en Room y reanudar con Range.

### Compose: state hoisting y rendimiento

El estado sube al ViewModel o al llamador; los composables reciben estado y callbacks (stateless, con preview y testeables). En listas: LazyColumn con key estable; evita leer estado cambiante en alcances amplios (derivedStateOf, remember) y mide el número de recomposiciones.

### Tests en Android

Unitarios con JUnit + coroutines-test (runTest) y Turbine para Flow; repositorios con fakes y Room in-memory; UI con las reglas de test de Compose (onNodeWithText, performClick); Hilt para inyectar fakes. Pirámide: muchos unitarios, pocos instrumentados.

### Cliente móvil para esta API

Retrofit o Ktor para REST, okhttp-sse para el progreso con reconexión y backoff, estado en el ViewModel, WorkManager para descargar y MediaStore (scoped storage) para guardar el archivo, y permiso de notificaciones en Android 13+.
