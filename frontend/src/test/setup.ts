import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Algunas versiones de Node exponen un `localStorage` global incompleto que tapa el de jsdom.
if (typeof globalThis.localStorage?.clear !== "function") {
  const data = new Map<string, string>();
  const memory: Storage = {
    get length() {
      return data.size;
    },
    clear: () => data.clear(),
    getItem: (k) => data.get(k) ?? null,
    key: (i) => [...data.keys()][i] ?? null,
    removeItem: (k) => void data.delete(k),
    setItem: (k, v) => void data.set(k, String(v)),
  };
  Object.defineProperty(globalThis, "localStorage", { value: memory, configurable: true });
}

afterEach(() => {
  cleanup();
  localStorage.clear(); // la caché local no debe filtrarse entre tests
});
