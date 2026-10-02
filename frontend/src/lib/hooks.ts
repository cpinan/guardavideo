import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useSyncExternalStore } from "react";
import { useDebug } from "./debug";

function subscribeOnline(cb: () => void) {
  window.addEventListener("online", cb);
  window.addEventListener("offline", cb);
  return () => {
    window.removeEventListener("online", cb);
    window.removeEventListener("offline", cb);
  };
}

/** Conectividad del navegador, más la simulada desde el panel de depuración. */
export function useOnline(): boolean {
  const browser = useSyncExternalStore(subscribeOnline, () => navigator.onLine, () => true);
  const simulatedOffline = useDebug((s) => s.offline);
  return browser && !simulatedOffline;
}

/** Al recuperar la conexión, refresca todo lo que esté en caché sin esperar al próximo intento. */
export function useRefetchOnReconnect(online: boolean) {
  const qc = useQueryClient();
  const wasOnline = useRef(online);
  useEffect(() => {
    if (online && !wasOnline.current) void qc.invalidateQueries(); // solo en la transición
    wasOnline.current = online;
  }, [online, qc]);
}

export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = title;
  }, [title]);
}
