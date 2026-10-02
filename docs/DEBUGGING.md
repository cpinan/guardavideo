# Depuración y validación de escenarios

```bash
make dev-api-debug   # API con HLSWEB_DEBUG=true + URLs locales permitidas
make dev-web         # http://localhost:5173
```

Con el backend en modo debug aparece el botón **🐞 Depuración** (abajo a la derecha). También se
puede forzar con `http://localhost:5173/?debug`.

## Cómo funciona
- Backend: `app/downloaders/debug.py` atiende `http://debug.test/<escenario>`. El dominio `.test`
  está reservado (RFC 2606): no hay tráfico de red y la protección SSRF no se toca (solo se omite
  para ese host cuando `HLSWEB_DEBUG=true`). Sin debug, esas URLs se rechazan con 400.
- Frontend: `lib/debug.ts` simula red caída/latencia en `api.ts` y en el SSE, y guarda un registro.

## Checklist de escenarios
| Escenario | Qué validar |
|---|---|
| `ok` | Cola → descarga → listo; miniatura aparece atenuada y recupera color; botón Descargar |
| `slow` | **Recargar la página a mitad**: la lista aparece al instante (caché), "Sincronizando…" y el progreso sigue |
| `flaky` | Insignia "Reintentando", barra ámbar, "Reconectando con el origen… intento N"; se recupera sola |
| `network` | 5 reintentos y luego Error con botón **Reintentar** |
| `fail` / `late-fail` | Error inmediato / al 60 %, animación de error, **Reintentar** vuelve a encolar |
| `stall` | Se queda al 40 %; **Cancelar** lo libera; **Reintentar** lo vuelve a lanzar |

## Red simulada (panel → Red simulada)
- **Sin conexión**: banner naranja, las peticiones fallan sin salir del navegador, el SSE se cierra;
  la lista conserva lo guardado. Al desactivar: se refresca todo solo.
- **Latencia** 300 / 1500 / 4000 ms: comprueba esqueletos, "Enviando…" y que no se envíe dos veces.

## Qué se cachea y dónde
| Dato | Dónde | Notas |
|---|---|---|
| Lista de descargas | `localStorage` `hlsweb:jobs:v1` | Validada con zod; el servidor siempre manda |
| Borrador del formulario | `localStorage` `hlsweb:draft:v1` | Se limpia al enviar con éxito |
| Miniaturas | HTTP (`ETag` + `?v=`) | Cambian de URL cuando se reemplazan |

## Reintentos
- Consultas (GET): 5 reintentos con 1 s, 2 s, 4 s, 8 s, 15 s; solo red caída, 5xx, 408 y 429.
  Si la lista falla se reintenta sola cada 10 s.
- Mutaciones (POST/DELETE): **no** se reintentan solas (evita duplicar descargas).
- SSE: si el navegador se rinde, reabre con backoff (0,5 s → 15 s) y refresca la lista al volver.
- Backend: cada segmento/petición HLS y yt-dlp reintenta `HLSWEB_NETWORK_RETRIES` veces; un 4xx
  (salvo 408/429) no se reintenta; cancelar interrumpe la espera.
- Job fallido/cancelado: `POST /api/v1/jobs/{id}/retry` lo vuelve a encolar con el mismo id.

## Base de datos local (SQLite)
- Archivo: `<HLSWEB_DOWNLOAD_DIR>/hlsweb.db` (o `HLSWEB_DB_PATH`). Inspección: `sqlite3 backend/downloads/hlsweb.db 'select id,status,size_bytes,file_path from jobs'`.
- Guarda cada descarga (estado, título, tamaño, fecha, rutas, hashes). Sobrevive a reinicios del servidor.
- Migraciones con `PRAGMA user_version` (hoy v2); una BD más nueva que la app se rechaza.
- Al arrancar (`services/recovery.py`): en cola/descargando → error "Interrumpido…" (se puede **Reintentar**);
  "listo" sin archivo en disco → error "El archivo ya no existe"; miniaturas desaparecidas se olvidan.
  Un apagado normal (Ctrl+C) deja los jobs en curso como *cancelados*; un crash, como *error*.
- `DELETE /api/v1/jobs/{id}/record` (botón **Eliminar**, con confirmación): quita el registro, el video y la
  miniatura. Solo jobs terminados; nunca borra fuera de la carpeta de descargas.

## Evitar descargas repetidas (SHA-256)
- **Por URL**: `sha256(URL canónica + calidad)`. Canónica = esquema/host en minúsculas y sin `#fragmento`
  (ruta y query se respetan). Si ya hay un job en curso, o terminado con su archivo en disco, `POST /jobs`
  devuelve ese job con **200** (en vez de 201) y la UI avisa y lo resalta. `force: true` / "Descargar de nuevo" lo evita.
  Jobs con error o cancelados, o cuyo archivo se borró a mano, sí se vuelven a descargar.
- **Por contenido**: al terminar se calcula el SHA-256 del archivo; si otro job tiene uno idéntico se borra el
  duplicado y ambos apuntan al mismo archivo (que solo se elimina con el último job que lo usa).
  Ojo: el mismo video por rutas distintas (HLS vs. yt-dlp) suele dar bytes distintos; esto detecta archivos idénticos.
