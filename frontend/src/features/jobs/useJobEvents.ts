import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { eventsUrl } from "../../lib/api";
import { debugLog, useDebug } from "../../lib/debug";
import { JOBS_KEY } from "../../lib/keys";
import { sseDelay } from "../../lib/retry";
import { isActive, type Job, JobSchema } from "../../lib/schemas";
import { replaceJob } from "./useJobs";

export type Connection = "idle" | "live" | "reconnecting";
const CLOSED = 2; // EventSource.CLOSED (literal: no depende del global en tests)
const RESYNC_AFTER_FAILURES = 3;

/**
 * Mientras el job esté activo, escucha su SSE y parchea la caché de React Query.
 * La caché es la única fuente de verdad: la UI no duplica el estado del job en useState.
 *
 * Reconexión: si el navegador reconecta solo (readyState CONNECTING) lo dejamos; si se rinde
 * (CLOSED) reabrimos nosotros con backoff exponencial. Al recuperarse, refrescamos la lista
 * para no perder cambios ocurridos durante el corte.
 */
export function useJobEvents(job: Job): Connection {
  const qc = useQueryClient();
  const active = isActive(job);
  const simulatedOffline = useDebug((s) => s.offline);
  const [connection, setConnection] = useState<Connection>("idle");

  useEffect(() => {
    if (!active) {
      setConnection("idle");
      return;
    }
    if (simulatedOffline) {
      setConnection("reconnecting");
      return;
    }

    let es: EventSource | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let failures = 0;

    const open = () => {
      es = new EventSource(eventsUrl(job.id));
      es.onopen = () => {
        debugLog("sse", `${job.id}: conectado`);
        if (failures > 0) void qc.invalidateQueries({ queryKey: JOBS_KEY }); // resincroniza
        failures = 0;
        setConnection("live");
      };
      es.onmessage = (e) => {
        let data: unknown;
        try {
          data = JSON.parse(e.data);
        } catch {
          return;
        }
        const parsed = JobSchema.safeParse(data);
        if (!parsed.success) return; // ignora mensajes que no cumplen el contrato
        failures = 0;
        setConnection("live");
        replaceJob(qc, parsed.data);
        if (!isActive(parsed.data)) es?.close();
      };
      es.onerror = () => {
        failures += 1;
        setConnection("reconnecting");
        debugLog("sse", `${job.id}: conexión perdida (fallo ${failures})`);
        if (failures === RESYNC_AFTER_FAILURES) void qc.invalidateQueries({ queryKey: JOBS_KEY });
        if (es?.readyState === CLOSED) {
          es.close();
          const wait = sseDelay(failures);
          debugLog("sse", `${job.id}: reintento manual en ${wait} ms`);
          timer = setTimeout(open, wait);
        }
      };
    };
    open();

    return () => {
      clearTimeout(timer);
      es?.close();
    };
  }, [active, job.id, qc, simulatedOffline]);

  return connection;
}
