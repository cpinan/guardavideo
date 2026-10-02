import { memo, useMemo } from "react";
import { thumbnailUrl } from "../../lib/api";
import { formatClock, formatWhen } from "../../lib/format";
import { type Playback, savePlayback, useAllPlayback } from "../../lib/playback";
import type { Job } from "../../lib/schemas";
import { buildShelves, watchedFraction } from "../../lib/shelves";
import { displayTitle } from "../../lib/title";
import { StarButton } from "./StarButton";

type Kind = "continue" | "favorites" | "views" | "recent";

function subline(kind: Kind, job: Job, pb: Playback | undefined): string {
  const total = job.duration_seconds ?? pb?.d ?? 0;
  switch (kind) {
    case "continue":
      return `Visto ${formatClock(pb?.t ?? 0)}${total ? ` / ${formatClock(total)}` : ""}`;
    case "views":
      return `${job.view_count} ${job.view_count === 1 ? "vista" : "vistas"}`;
    case "recent":
      return job.created_at ? formatWhen(job.created_at) : "";
    default:
      return job.duration_seconds !== null ? formatClock(job.duration_seconds) : "";
  }
}

const ShelfCard = memo(function ShelfCard({ job, kind, pb }: { job: Job; kind: Kind; pb: Playback | undefined }) {
  const name = displayTitle(job);
  const fraction = watchedFraction(job, pb);
  return (
    <li className="shelf-card">
      <button
        type="button"
        className="shelf-open"
        aria-label={`Reproducir ${name}`}
        onClick={() => savePlayback(job.id, { open: true, lightbox: true })}
      >
        <span className="shelf-thumb">
          {job.thumbnail_version > 0 && <img src={thumbnailUrl(job)} alt="" loading="lazy" decoding="async" />}
          {job.duration_seconds !== null && <span className="duration">{formatClock(job.duration_seconds)}</span>}
          {fraction > 0 && (
            <span className="watch" aria-hidden="true">
              <span style={{ width: `${fraction * 100}%` }} />
            </span>
          )}
        </span>
        <span className="shelf-title">{name}</span>
        <span className="shelf-sub">{subline(kind, job, pb)}</span>
      </button>
      <StarButton job={job} />
    </li>
  );
});

/** Para no repetir lo mismo: con pocos videos las listas "vistos" y "recientes" no aportan. */
const MIN_FOR_AUTO_SHELVES = 4;

export function LibraryShelves({ jobs }: { jobs: Job[] }) {
  const playback = useAllPlayback();
  const shelves = useMemo(() => buildShelves(jobs, playback), [jobs, playback]);
  const enough = jobs.filter((j) => j.status === "done").length >= MIN_FOR_AUTO_SHELVES;

  const sections: { kind: Kind; title: string; icon: string; items: Job[] }[] = [
    { kind: "continue", title: "Seguir viendo", icon: "▶", items: shelves.continueWatching },
    { kind: "favorites", title: "Favoritos", icon: "★", items: shelves.favorites },
    { kind: "views", title: "Más vistos", icon: "🔥", items: enough ? shelves.mostViewed : [] },
    { kind: "recent", title: "Recién descargados", icon: "🕒", items: enough ? shelves.recent : [] },
  ];
  const visible = sections.filter((s) => s.items.length > 0);
  if (visible.length === 0) return null;

  return (
    <div className="shelves" aria-label="Tu biblioteca">
      {visible.map((s) => (
        <section key={s.kind} className="shelf" aria-labelledby={`shelf-${s.kind}`}>
          <h3 id={`shelf-${s.kind}`} className="shelf-heading">
            <span aria-hidden="true">{s.icon}</span> {s.title}
          </h3>
          <ul className="shelf-row">
            {s.items.map((job) => (
              <ShelfCard key={job.id} job={job} kind={s.kind} pb={playback[job.id]} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
