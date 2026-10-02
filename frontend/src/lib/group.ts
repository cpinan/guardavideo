import type { Job } from "./schemas";

export interface Group {
  label: string;
  jobs: Job[];
}

const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();

export function dayLabel(iso: string | null, now = new Date()): string {
  if (!iso) return "Anteriores";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "Anteriores";
  const days = Math.round((startOfDay(now) - startOfDay(date)) / 86_400_000);
  if (days <= 0) return "Hoy";
  if (days === 1) return "Ayer";
  const text = date.toLocaleDateString("es", { weekday: "long", day: "numeric", month: "long" });
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** Agrupa jobs consecutivos del mismo día (la lista ya viene ordenada, más nuevo primero). */
export function groupByDay(jobs: Job[], now = new Date()): Group[] {
  const groups: Group[] = [];
  for (const job of jobs) {
    const label = dayLabel(job.created_at, now);
    const last = groups[groups.length - 1];
    if (last?.label === label) last.jobs.push(job);
    else groups.push({ label, jobs: [job] });
  }
  return groups;
}
