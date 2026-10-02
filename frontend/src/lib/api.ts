import { z } from "zod";
import { debugLog, getDebugState } from "./debug";
import { HealthSchema, type Job, JobSchema } from "./schemas";

const ROOT = import.meta.env.VITE_API_URL ?? "";
const BASE = `${ROOT}/api/v1`;
const NETWORK_MESSAGE = "Sin conexión con el servidor";

/** status 0 = la petición nunca llegó al servidor (red caída, servidor apagado, CORS...). */
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
  get isNetwork() {
    return this.status === 0;
  }
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Envía la petición (con red simulada y registro) y lanza ApiError si no fue 2xx. */
async function send(url: string, init?: RequestInit): Promise<Response> {
  const method = init?.method ?? "GET";
  const { offline, latencyMs } = getDebugState();
  if (latencyMs) await sleep(latencyMs);
  if (offline) {
    debugLog("net", `✖ ${method} ${url} — sin red (simulado)`);
    throw new ApiError(0, NETWORK_MESSAGE);
  }

  let res: Response;
  try {
    res = await fetch(url, init);
  } catch {
    debugLog("net", `✖ ${method} ${url} — sin respuesta`);
    throw new ApiError(0, NETWORK_MESSAGE);
  }
  debugLog("net", `${res.ok ? "✔" : "✖"} ${method} ${url} → ${res.status}`);

  if (!res.ok) {
    const body = await res.json().catch(() => null);
    const detail = typeof body?.detail === "string" ? body.detail : undefined;
    throw new ApiError(res.status, detail ?? friendly(res.status));
  }
  return res;
}

async function request<T>(url: string, schema: z.ZodType<T, z.ZodTypeDef, unknown>, init?: RequestInit) {
  return schema.parse(await (await send(url, init)).json());
}

function friendly(status: number): string {
  if (status === 422) return "La URL o la calidad no son válidas";
  if (status === 404) return "No se encontró el recurso";
  if (status === 409) return "Esa acción no es posible en el estado actual";
  return "Ocurrió un error inesperado";
}

const api = (path: string) => `${BASE}${path}`;
const json = (method: string, body?: unknown): RequestInit => ({
  method,
  ...(body === undefined
    ? {}
    : { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }),
});

export const listJobs = () => request(api("/jobs"), z.array(JobSchema));

/** `force`: descargar de nuevo aunque el servidor ya tenga ese video (por defecto lo reutiliza). */
export const createJob = (url: string, quality?: number, force = false) =>
  request(api("/jobs"), JobSchema, json("POST", { url, quality: quality ?? null, ...(force && { force }) }));

/** Quita del historial un job terminado (y borra su archivo en el servidor). */
export const deleteJob = async (id: string): Promise<void> => {
  await send(api(`/jobs/${id}/record`), json("DELETE"));
};

export const cancelJob = (id: string) => request(api(`/jobs/${id}`), JobSchema, json("DELETE"));

export const retryJob = (id: string) => request(api(`/jobs/${id}/retry`), JobSchema, json("POST"));

export const getHealth = () => request(`${ROOT}/health`, HealthSchema);

export const fileUrl = (job: Job) => api(`/jobs/${job.id}/file`);
export const eventsUrl = (id: string) => api(`/jobs/${id}/events`);
/** `?v=` cambia cada vez que la miniatura se reemplaza: así el navegador no muestra la vieja. */
export const thumbnailUrl = (job: Job) =>
  api(`/jobs/${job.id}/thumbnail?v=${job.thumbnail_version}`);
