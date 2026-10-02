import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "./api";

export const MAX_RETRIES = 5;

/** Reintenta solo lo que puede arreglarse solo: red caída, 5xx, timeouts y rate limits. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= MAX_RETRIES) return false;
  if (!(error instanceof ApiError)) return false; // p. ej. contrato roto (zod): reintentar no ayuda
  return error.isNetwork || error.status >= 500 || error.status === 408 || error.status === 429;
}

/** Backoff exponencial con tope: 1 s, 2 s, 4 s, 8 s, 15 s. */
export const retryDelay = (attempt: number) => Math.min(1000 * 2 ** attempt, 15_000);

/** Espera para reabrir el SSE tras `failures` fallos seguidos (tope 15 s). */
export const sseDelay = (failures: number) => Math.min(500 * 2 ** (failures - 1), 15_000);

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 5_000,
        retry: shouldRetry,
        retryDelay,
        refetchOnWindowFocus: false,
        refetchOnReconnect: true,
        networkMode: "always", // los errores de red se ven y se reintentan, no se pausan en silencio
      },
      // Las mutaciones NO se reintentan solas: crear un job dos veces sería peor que fallar.
    },
  });
}
