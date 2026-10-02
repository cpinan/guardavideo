import type { Playback } from "./playback";
import type { Job } from "./schemas";

export interface Shelves {
  /** Videos que dejaste a medias, el último visto primero. */
  continueWatching: Job[];
  favorites: Job[];
  /** Los más reproducidos (al menos una vez). */
  mostViewed: Job[];
  /** Terminados en los últimos días. */
  recent: Job[];
}

const RECENT_DAYS = 7;
const MS_DAY = 86_400_000;

const time = (iso: string | null) => {
  const t = iso ? new Date(iso).getTime() : 0;
  return Number.isNaN(t) ? 0 : t;
};

/** Cuánto se vio, 0..1 (0 si no hay datos o ya lo terminaste). */
export function watchedFraction(job: Job, pb: Playback | undefined): number {
  const total = job.duration_seconds ?? pb?.d ?? 0;
  if (!pb || pb.watched || pb.t < 1 || total <= 0) return 0;
  return Math.min(1, pb.t / total);
}

export function buildShelves(
  jobs: Job[],
  playback: Readonly<Record<string, Playback>>,
  { limit = 8, now = Date.now() }: { limit?: number; now?: number } = {},
): Shelves {
  const done = jobs.filter((j) => j.status === "done");
  const newestFirst = (a: Job, b: Job) => time(b.created_at) - time(a.created_at);

  const continueWatching = done
    .filter((j) => watchedFraction(j, playback[j.id]) > 0 && watchedFraction(j, playback[j.id]) < 0.98)
    .sort((a, b) => (playback[b.id]?.at ?? 0) - (playback[a.id]?.at ?? 0))
    .slice(0, limit);

  const favorites = done.filter((j) => j.starred).sort(newestFirst).slice(0, limit);

  const mostViewed = done
    .filter((j) => j.view_count > 0)
    .sort((a, b) => b.view_count - a.view_count || time(b.last_viewed_at) - time(a.last_viewed_at))
    .slice(0, limit);

  const recent = done
    .filter((j) => now - time(j.created_at) <= RECENT_DAYS * MS_DAY)
    .sort(newestFirst)
    .slice(0, limit);

  return { continueWatching, favorites, mostViewed, recent };
}

export type SortKey = "recent" | "views" | "name" | "size";
export const SORTS: { id: SortKey; label: string }[] = [
  { id: "recent", label: "Más recientes" },
  { id: "views", label: "Más vistos" },
  { id: "name", label: "Nombre (A–Z)" },
  { id: "size", label: "Tamaño" },
];

export function sortJobs(jobs: Job[], key: SortKey, nameOf: (j: Job) => string): Job[] {
  const copy = jobs.slice();
  switch (key) {
    case "views":
      return copy.sort((a, b) => b.view_count - a.view_count || time(b.last_viewed_at) - time(a.last_viewed_at));
    case "name":
      return copy.sort((a, b) => nameOf(a).localeCompare(nameOf(b), "es", { sensitivity: "base" }));
    case "size":
      return copy.sort((a, b) => (b.size_bytes ?? -1) - (a.size_bytes ?? -1));
    default:
      return jobs; // ya viene más nuevo primero
  }
}
