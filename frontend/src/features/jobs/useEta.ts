import { useEffect, useRef, useState } from "react";
import { estimateEta, type Sample } from "../../lib/eta";
import type { Job } from "../../lib/schemas";

/** Tiempo restante estimado a partir del ritmo real del progreso (null si no se puede saber). */
export function useEta(job: Job): number | null {
  const samples = useRef<Sample[]>([]);
  const [eta, setEta] = useState<number | null>(null);

  useEffect(() => {
    if (job.status !== "running") {
      samples.current = [];
      setEta(null);
      return;
    }
    const now = Date.now();
    samples.current = [...samples.current, { t: now, p: job.percent }].slice(-40);
    setEta(estimateEta(samples.current, now));
  }, [job.percent, job.status]);

  return eta;
}
