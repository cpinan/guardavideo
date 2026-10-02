import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { cancelJob, createJob, deleteJob, listJobs, recordView, retryJob, setStarred } from "../../lib/api";
import { JOBS_KEY } from "../../lib/keys";
import { loadCachedJobs } from "../../lib/persist";
import { clearPlayback } from "../../lib/playback";
import type { Job } from "../../lib/schemas";

export { JOBS_KEY };

export const useJobsQuery = () =>
  useQuery({
    queryKey: JOBS_KEY,
    queryFn: listJobs,
    // Caché local: se pinta al instante tras recargar; updatedAt 0 = "viejo", así que refresca ya.
    initialData: loadCachedJobs,
    initialDataUpdatedAt: 0,
    // Si el servidor no responde, sigue intentando solo cada 10 s (se cura sin recargar).
    refetchInterval: (query) => (query.state.status === "error" ? 10_000 : false),
  });

export function useCreateJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ url, quality, force }: { url: string; quality?: number; force?: boolean }) => {
      const job = await createJob(url, quality, force);
      // El servidor devuelve el job existente si ya tenía ese video: no es uno nuevo.
      const reused = (qc.getQueryData<Job[]>(JOBS_KEY) ?? []).some((j) => j.id === job.id);
      return { job, reused };
    },
    // Insertamos el job devuelto en la caché: aparece al instante sin refetch.
    onSuccess: ({ job, reused }) =>
      qc.setQueryData<Job[]>(JOBS_KEY, (old = []) => (reused ? old.map((j) => (j.id === job.id ? job : j)) : [...old, job])),
  });
}

export function useCancelJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: cancelJob,
    onSuccess: (job) => replaceJob(qc, job),
  });
}

export function useDeleteJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: deleteJob,
    onSuccess: (_, id) => {
      clearPlayback(id);
      qc.setQueryData<Job[]>(JOBS_KEY, (old = []) => old.filter((j) => j.id !== id));
    },
  });
}

/** "Limpiar con error": elimina varios registros a la vez. */
export function useDeleteMany() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (ids: string[]) => {
      const results = await Promise.allSettled(ids.map((id) => deleteJob(id)));
      return ids.filter((_, i) => results[i]?.status === "fulfilled");
    },
    onSuccess: (deleted) => {
      deleted.forEach(clearPlayback);
      qc.setQueryData<Job[]>(JOBS_KEY, (old = []) => old.filter((j) => !deleted.includes(j.id)));
    },
  });
}

/** Favorito con actualización optimista: la estrella cambia al instante y vuelve atrás si falla. */
export function useToggleStar() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, starred }: { id: string; starred: boolean }) => setStarred(id, starred),
    onMutate: async ({ id, starred }) => {
      await qc.cancelQueries({ queryKey: JOBS_KEY });
      const previous = qc.getQueryData<Job[]>(JOBS_KEY);
      qc.setQueryData<Job[]>(JOBS_KEY, (old = []) => old.map((j) => (j.id === id ? { ...j, starred } : j)));
      return { previous };
    },
    onError: (_err, _vars, ctx) => ctx?.previous && qc.setQueryData(JOBS_KEY, ctx.previous),
    onSuccess: (job) => replaceJob(qc, job),
  });
}

/** Cuenta una reproducción. Es un extra: si falla, no molesta al usuario. */
export function useRecordView() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: recordView,
    onSuccess: (job) => replaceJob(qc, job),
    throwOnError: false,
  });
}

export function useRetryJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: retryJob,
    onSuccess: (job) => replaceJob(qc, job),
  });
}

/** Reemplaza solo el job que cambió y conserva la referencia de los demás (clave para React.memo). */
export function replaceJob(qc: ReturnType<typeof useQueryClient>, next: Job) {
  qc.setQueryData<Job[]>(JOBS_KEY, (old = []) => old.map((j) => (j.id === next.id ? next : j)));
}
