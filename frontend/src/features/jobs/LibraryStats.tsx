import { useQuery } from "@tanstack/react-query";
import { getStats } from "../../lib/api";
import { formatBytes } from "../../lib/format";
import { STATS_KEY } from "../../lib/keys";
import { useJobsQuery } from "./useJobs";

/** "14 videos · 1,2 GB · 80 GB libres". Se vuelve a pedir cuando cambia la cantidad de terminados. */
export function LibraryStats() {
  const { data: jobs } = useJobsQuery();
  const doneCount = (jobs ?? []).filter((j) => j.status === "done").length;
  const { data } = useQuery({ queryKey: [...STATS_KEY, doneCount], queryFn: getStats, retry: false, staleTime: 30_000 });
  if (!data || data.videos === 0) return null;
  return (
    <p className="stats" aria-label="Resumen de la biblioteca">
      <b>{data.videos}</b> {data.videos === 1 ? "video" : "videos"} · {formatBytes(data.bytes)}
      {data.disk_free_bytes > 0 && <span className="muted"> · {formatBytes(data.disk_free_bytes)} libres</span>}
    </p>
  );
}
