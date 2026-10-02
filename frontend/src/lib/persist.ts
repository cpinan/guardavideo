import type { QueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { debugLog } from "./debug";
import { JOBS_KEY } from "./keys";
import { type Job, JobSchema } from "./schemas";
import { storage } from "./storage";

export const JOBS_CACHE_KEY = "hlsweb:jobs:v1";

/**
 * Última lista conocida, para pintar al instante tras recargar la página.
 * Se valida con zod: un caché viejo o corrupto se descarta en vez de romper la app.
 */
export function loadCachedJobs(): Job[] | undefined {
  const raw = storage.get(JOBS_CACHE_KEY);
  if (!raw) return undefined;
  try {
    const parsed = z.array(JobSchema).safeParse(JSON.parse(raw));
    if (parsed.success) {
      debugLog("cache", `Caché local cargada (${parsed.data.length} descargas)`);
      return parsed.data;
    }
  } catch {
    /* JSON inválido */
  }
  storage.remove(JOBS_CACHE_KEY);
  return undefined;
}

export function clearCachedJobs() {
  storage.remove(JOBS_CACHE_KEY);
  debugLog("cache", "Caché local borrada");
}

/** Guarda la lista cuando cambia (con throttle: el SSE actualiza varias veces por segundo). */
export function persistJobs(qc: QueryClient, throttleMs = 500): () => void {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const save = () => {
    timer = undefined;
    const data = qc.getQueryData<Job[]>(JOBS_KEY);
    if (data) storage.set(JOBS_CACHE_KEY, JSON.stringify(data));
  };
  const unsubscribe = qc.getQueryCache().subscribe((event) => {
    if (event.type === "updated" && event.query.queryKey[0] === JOBS_KEY[0] && !timer) {
      timer = setTimeout(save, throttleMs);
    }
  });
  return () => {
    unsubscribe();
    if (timer) {
      clearTimeout(timer);
      save();
    }
  };
}
