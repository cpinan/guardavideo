import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { getHealth } from "../../lib/api";
import {
  clearDebugLog,
  setSimulatedLatency,
  setSimulatedOffline,
  useDebug,
} from "../../lib/debug";
import { HEALTH_KEY, JOBS_KEY } from "../../lib/keys";
import { clearCachedJobs } from "../../lib/persist";
import { useCreateJob } from "../jobs/useJobs";

/** Espejo de app/downloaders/debug.py (SCENARIOS). El backend debe correr con HLSWEB_DEBUG=true. */
export const SCENARIOS = [
  { id: "ok", label: "Éxito rápido", hint: "Termina en ~4 s, con miniatura" },
  { id: "slow", label: "Lento (30 s)", hint: "Para cancelar o recargar la página a medias" },
  { id: "flaky", label: "Red inestable", hint: "Pierde la red 3 veces y se recupera solo" },
  { id: "network", label: "Sin red (agota reintentos)", hint: "Falla tras 5 reintentos" },
  { id: "fail", label: "Falla al instante", hint: "Error inmediato" },
  { id: "late-fail", label: "Falla al 60 %", hint: "Error a mitad de la descarga" },
  { id: "stall", label: "Se cuelga", hint: "Queda al 40 % hasta cancelar" },
] as const;

const LATENCIES = [0, 300, 1500, 4000];

function useDebugAvailable() {
  const forced = new URLSearchParams(window.location.search).has("debug");
  const { data } = useQuery({ queryKey: HEALTH_KEY, queryFn: getHealth, retry: false, staleTime: Infinity });
  return forced || data?.debug === true;
}

export function DebugPanel() {
  const available = useDebugAvailable();
  const [open, setOpen] = useState(false);
  const qc = useQueryClient();
  const create = useCreateJob();
  const offline = useDebug((s) => s.offline);
  const latency = useDebug((s) => s.latencyMs);
  const log = useDebug((s) => s.log);

  if (!available) return null;

  return (
    <>
      <button
        type="button"
        className="debug-toggle"
        aria-expanded={open}
        aria-controls="debug-panel"
        onClick={() => setOpen((o) => !o)}
      >
        <span aria-hidden="true">🐞</span>
        <span className="debug-label">Depuración{offline ? " · sin red" : ""}</span>
      </button>

      {open && (
        <aside id="debug-panel" className="debug-panel" aria-label="Herramientas de depuración">
          <section>
            <h3>Escenarios de descarga</h3>
            <div className="scenarios">
              {SCENARIOS.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  className="btn ghost"
                  title={s.hint}
                  onClick={() => create.mutate({ url: `http://debug.test/${s.id}`, force: true })}
                >
                  {s.label}
                </button>
              ))}
            </div>
            {create.error && (
              <p role="alert" className="err small">
                {create.error.message}. ¿El backend corre con <code>make dev-api-debug</code>?
              </p>
            )}
          </section>

          <section>
            <h3>Red simulada</h3>
            <label className="check">
              <input
                type="checkbox"
                checked={offline}
                onChange={(e) => setSimulatedOffline(e.target.checked)}
              />
              Simular sin conexión
            </label>
            <label className="inline">
              Latencia
              <select value={latency} onChange={(e) => setSimulatedLatency(Number(e.target.value))}>
                {LATENCIES.map((ms) => (
                  <option key={ms} value={ms}>
                    {ms ? `${ms} ms` : "Ninguna"}
                  </option>
                ))}
              </select>
            </label>
          </section>

          <section>
            <h3>Caché</h3>
            <div className="scenarios">
              <button type="button" className="btn ghost" onClick={() => window.location.reload()}>
                Recargar página
              </button>
              <button
                type="button"
                className="btn ghost"
                onClick={() => {
                  clearCachedJobs();
                  qc.removeQueries({ queryKey: JOBS_KEY });
                  window.location.reload();
                }}
              >
                Borrar caché y recargar
              </button>
            </div>
          </section>

          <section>
            <div className="log-head">
              <h3>Registro</h3>
              <button type="button" className="btn ghost small" onClick={clearDebugLog}>
                Limpiar
              </button>
            </div>
            <ol className="log" aria-label="Registro de eventos" tabIndex={0}>
              {log.length === 0 && <li className="muted">Sin eventos todavía.</li>}
              {log
                .slice()
                .reverse()
                .map((e) => (
                  <li key={e.id} className={e.kind}>
                    <time>{new Date(e.at).toLocaleTimeString()}</time>
                    <b>{e.kind}</b> {e.message}
                  </li>
                ))}
            </ol>
          </section>
        </aside>
      )}
    </>
  );
}
