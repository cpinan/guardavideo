export interface Sample {
  t: number; // ms
  p: number; // porcentaje
}

const WINDOW_MS = 20_000;
const STALE_MS = 10_000;

/** Segundos restantes según el ritmo de las últimas muestras; null si aún no se puede estimar. */
export function estimateEta(samples: Sample[], now: number): number | null {
  const recent = samples.filter((s) => now - s.t <= WINDOW_MS);
  const first = recent[0];
  const last = recent[recent.length - 1];
  if (!first || !last || last === first) return null;
  if (now - last.t > STALE_MS) return null; // sin avance reciente: no inventes un número
  const dt = (last.t - first.t) / 1000;
  const dp = last.p - first.p;
  if (dt < 1 || dp <= 0) return null;
  return Math.max(0, (100 - last.p) / (dp / dt));
}

export function formatEta(seconds: number): string {
  const s = Math.max(1, Math.round(seconds));
  if (s < 60) return `~${s} s`;
  const m = Math.floor(s / 60);
  if (m < 60) return `~${m} min ${s % 60} s`;
  return `~${Math.floor(m / 60)} h ${m % 60} min`;
}
