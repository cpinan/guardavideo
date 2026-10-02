import { useSyncExternalStore } from "react";

/** Avisos efímeros (p. ej. "Descarga iniciada"). Fuera de React para poder usarlos desde cualquier lugar. */
export interface Toast {
  id: number;
  message: string;
  /** Acción opcional: un enlace (descarga de nuevo) o un botón. */
  action?: { label: string; href?: string; onClick?: () => void };
}

const DURATION_MS = 6000;
let nextId = 1;
let toasts: Toast[] = [];
const listeners = new Set<() => void>();
const timers = new Map<number, ReturnType<typeof setTimeout>>();

function set(next: Toast[]) {
  toasts = next;
  listeners.forEach((l) => l());
}

export function dismissToast(id: number) {
  clearTimeout(timers.get(id));
  timers.delete(id);
  set(toasts.filter((t) => t.id !== id));
}

export function showToast(message: string, action?: Toast["action"]): number {
  const id = nextId++;
  set([...toasts.slice(-2), { id, message, action }]); // máximo 3 a la vez
  timers.set(id, setTimeout(() => dismissToast(id), DURATION_MS));
  return id;
}

export function resetToasts() {
  timers.forEach(clearTimeout);
  timers.clear();
  set([]);
}

const subscribe = (cb: () => void) => {
  listeners.add(cb);
  return () => void listeners.delete(cb);
};
export const useToasts = () => useSyncExternalStore(subscribe, () => toasts, () => toasts);
