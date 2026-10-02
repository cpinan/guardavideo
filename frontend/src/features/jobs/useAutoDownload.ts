import { useEffect } from "react";
import { fileUrl } from "../../lib/api";
import { autoDownload, triggerDownload } from "../../lib/autodownload";
import { useJobsQuery } from "./useJobs";

/**
 * Observa la lista de jobs: cuando uno pedido con "Procesar y descargar" termina, baja el archivo.
 * Funciona igual si recargaste la página mientras procesaba (el pedido está en localStorage).
 */
export function useAutoDownload() {
  const { data } = useJobsQuery();
  useEffect(() => {
    for (const job of data ?? []) {
      if (!autoDownload.has(job.id)) continue;
      if (job.status === "done") {
        autoDownload.remove(job.id); // primero quitar: StrictMode ejecuta el efecto dos veces
        triggerDownload(fileUrl(job));
      } else if (job.status === "error" || job.status === "cancelled") {
        autoDownload.remove(job.id);
      }
    }
  }, [data]);
}
