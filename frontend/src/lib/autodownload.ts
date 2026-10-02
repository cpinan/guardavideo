import { useSyncExternalStore } from "react";
import { storage } from "./storage";

/**
 * Jobs que el usuario pidió con "Procesar y descargar": cuando terminen, el navegador baja el archivo.
 * Se guarda en localStorage: si recargas la página mientras procesa, la descarga automática sigue.
 */
const KEY = "hlsweb:auto:v1";
const MAX = 50;

function load(): string[] {
  try {
    const parsed: unknown = JSON.parse(storage.get(KEY) ?? "[]");
    return Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === "string") : [];
  } catch {
    return [];
  }
}

let ids = new Set(load());
const listeners = new Set<() => void>();

function commit(next: Set<string>) {
  ids = next;
  storage.set(KEY, JSON.stringify([...ids].slice(-MAX)));
  listeners.forEach((l) => l());
}

export const autoDownload = {
  has: (id: string) => ids.has(id),
  add: (id: string) => ids.has(id) || commit(new Set([...ids, id])),
  remove: (id: string) => {
    if (!ids.has(id)) return;
    const next = new Set(ids);
    next.delete(id);
    commit(next);
  },
  /** Solo para tests: vuelve a leer localStorage. */
  reload: () => {
    ids = new Set(load());
    listeners.forEach((l) => l());
  },
};

const subscribe = (cb: () => void) => {
  listeners.add(cb);
  return () => void listeners.delete(cb);
};

/** Solo se re-renderiza la fila cuyo estado cambió. */
export function useWillAutoDownload(id: string): boolean {
  return useSyncExternalStore(subscribe, () => ids.has(id), () => false);
}

/** Dispara la descarga del navegador (el servidor responde con Content-Disposition: attachment). */
export function triggerDownload(url: string) {
  const a = document.createElement("a");
  a.href = url;
  a.download = "";
  a.rel = "noopener";
  a.style.display = "none";
  document.body.appendChild(a);
  a.click();
  a.remove();
}
