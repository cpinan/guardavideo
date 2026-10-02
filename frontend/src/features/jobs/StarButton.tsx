import type { Job } from "../../lib/schemas";
import { displayTitle } from "../../lib/title";
import { useToggleStar } from "./useJobs";

/** Estrella de favorito. aria-pressed + nombre con el título: se entiende sin ver el icono. */
export function StarButton({ job }: { job: Job }) {
  const toggle = useToggleStar();
  const name = displayTitle(job);
  return (
    <button
      type="button"
      className={`star${job.starred ? " on" : ""}`}
      aria-pressed={job.starred}
      aria-label={job.starred ? `Quitar ${name} de favoritos` : `Marcar ${name} como favorito`}
      title={job.starred ? "Quitar de favoritos" : "Marcar como favorito"}
      onClick={() => toggle.mutate({ id: job.id, starred: !job.starred })}
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9-5.2-2.8-5.2 2.8 1-5.9-4.3-4.1 5.9-.8z" />
      </svg>
    </button>
  );
}
