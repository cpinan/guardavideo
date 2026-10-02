import { useSyncExternalStore } from "react";

/**
 * Estado de depuración fuera de React: lo leen api.ts y los hooks, y lo escribe el DebugPanel.
 * Permite simular conexión caída o lenta y ver un registro de lo que pasa en la red.
 */
export type LogKind = "net" | "sse" | "cache" | "sim";
export interface LogEntry {
  id: number;
  at: number;
  kind: LogKind;
  message: string;
}
export interface DebugState {
  offline: boolean;
  latencyMs: number;
  log: LogEntry[];
}

const MAX_LOG = 60;
let nextId = 1;
let state: DebugState = { offline: false, latencyMs: 0, log: [] };
const listeners = new Set<() => void>();

function set(next: DebugState) {
  state = next;
  listeners.forEach((l) => l());
}

export const getDebugState = () => state;

export function debugLog(kind: LogKind, message: string) {
  const entry = { id: nextId++, at: Date.now(), kind, message };
  set({ ...state, log: [...state.log, entry].slice(-MAX_LOG) });
}

export function setSimulatedOffline(offline: boolean) {
  set({ ...state, offline });
  debugLog("sim", offline ? "Conexión simulada: SIN RED" : "Conexión simulada: restablecida");
}

export function setSimulatedLatency(latencyMs: number) {
  set({ ...state, latencyMs });
  debugLog("sim", latencyMs ? `Latencia simulada: ${latencyMs} ms` : "Latencia simulada: ninguna");
}

export function clearDebugLog() {
  set({ ...state, log: [] });
}

export function resetDebugState() {
  set({ offline: false, latencyMs: 0, log: [] });
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Con selector: el componente solo se re-renderiza si cambia lo que le interesa. */
export function useDebug<T>(select: (s: DebugState) => T): T {
  return useSyncExternalStore(subscribe, () => select(state), () => select(state));
}
