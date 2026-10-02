import { useSyncExternalStore } from "react";
import { z } from "zod";
import { storage } from "./storage";

/**
 * Posición de reproducción por video, en localStorage: al recargar la página el video
 * vuelve al minuto/segundo donde lo dejaste (y, si tenías el reproductor abierto, sigue abierto).
 */
const KEY = "hlsweb:playback:v1";
const MAX_ENTRIES = 200;
const PERSIST_MS = 500;

const EntrySchema = z.object({
  t: z.number().min(0), // segundo actual
  d: z.number().min(0), // duración total
  open: z.boolean().default(false), // reproductor abierto
  lightbox: z.boolean().default(false), // ampliado a pantalla completa de la página
  watched: z.boolean().default(false), // llegó al final
  at: z.number().default(0),
});
export type Playback = z.infer<typeof EntrySchema>;

function load(): Record<string, Playback> {
  try {
    const parsed = z.record(EntrySchema).safeParse(JSON.parse(storage.get(KEY) ?? "{}"));
    return parsed.success ? parsed.data : {};
  } catch {
    return {};
  }
}

let state = load();
const listeners = new Set<() => void>();
let timer: ReturnType<typeof setTimeout> | undefined;

function persist() {
  timer = undefined;
  const newest = Object.entries(state).sort(([, a], [, b]) => b.at - a.at).slice(0, MAX_ENTRIES);
  storage.set(KEY, JSON.stringify(Object.fromEntries(newest)));
}

/** Escribe ya (al pausar, cerrar la pestaña, desmontar): no esperes al throttle. */
export function flushPlayback() {
  if (timer) {
    clearTimeout(timer);
    persist();
  }
}

if (typeof window !== "undefined") {
  window.addEventListener("pagehide", flushPlayback);
  document.addEventListener("visibilitychange", () => document.hidden && flushPlayback());
}

export const getAllPlayback = (): Readonly<Record<string, Playback>> => state;

/** Todas las posiciones guardadas (para "Seguir viendo"). Cambia de referencia al guardar. */
export function useAllPlayback(): Readonly<Record<string, Playback>> {
  return useSyncExternalStore(subscribe, () => state, () => ({}));
}

export const getPlayback = (id: string): Playback | undefined => state[id];

export function savePlayback(id: string, patch: Partial<Omit<Playback, "at">>) {
  const base: Playback = state[id] ?? { t: 0, d: 0, open: false, lightbox: false, watched: false, at: 0 };
  state = { ...state, [id]: { ...base, ...patch, at: Date.now() } };
  listeners.forEach((l) => l());
  timer ??= setTimeout(persist, PERSIST_MS);
}

export function clearPlayback(id: string) {
  if (!(id in state)) return;
  const rest = { ...state };
  delete rest[id];
  state = rest;
  listeners.forEach((l) => l());
  timer ??= setTimeout(persist, PERSIST_MS);
}

/** Solo para tests. */
export function resetPlayback() {
  clearTimeout(timer);
  timer = undefined;
  state = load();
  listeners.forEach((l) => l());
}

const subscribe = (cb: () => void) => {
  listeners.add(cb);
  return () => void listeners.delete(cb);
};

/** La entrada es inmutable: la referencia solo cambia cuando cambia ese video. */
export function usePlayback(id: string): Playback | undefined {
  return useSyncExternalStore(subscribe, () => state[id], () => undefined);
}
