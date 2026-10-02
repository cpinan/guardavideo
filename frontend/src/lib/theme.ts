import { useSyncExternalStore } from "react";
import { storage } from "./storage";

/** "system" sigue al sistema operativo; light/dark lo fuerzan. */
export type Theme = "system" | "light" | "dark";
const KEY = "hlsweb:theme:v1";
const ORDER: Theme[] = ["system", "light", "dark"];

function load(): Theme {
  const v = storage.get(KEY);
  return v === "light" || v === "dark" ? v : "system";
}

let theme = load();
const listeners = new Set<() => void>();

export function applyTheme(t: Theme = theme) {
  if (typeof document === "undefined") return;
  if (t === "system") document.documentElement.removeAttribute("data-theme");
  else document.documentElement.setAttribute("data-theme", t);
}

export function setTheme(t: Theme) {
  theme = t;
  if (t === "system") storage.remove(KEY);
  else storage.set(KEY, t);
  applyTheme(t);
  listeners.forEach((l) => l());
}

export const cycleTheme = () => setTheme(ORDER[(ORDER.indexOf(theme) + 1) % ORDER.length]!);

const subscribe = (cb: () => void) => {
  listeners.add(cb);
  return () => void listeners.delete(cb);
};
export const useTheme = () => useSyncExternalStore(subscribe, () => theme, () => "system" as Theme);
