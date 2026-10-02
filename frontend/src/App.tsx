import { useState } from "react";
import { ConnectionBanner } from "./components/ConnectionBanner";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { DebugPanel } from "./features/debug/DebugPanel";
import { JobForm, type SubmitMode } from "./features/jobs/JobForm";
import { JobList } from "./features/jobs/JobList";
import { useAutoDownload } from "./features/jobs/useAutoDownload";
import { useCreateJob, useJobsQuery } from "./features/jobs/useJobs";
import { fileUrl } from "./lib/api";
import { autoDownload, triggerDownload } from "./lib/autodownload";
import { useDocumentTitle, useOnline, useRefetchOnReconnect } from "./lib/hooks";
import { isActive } from "./lib/schemas";

const APP_NAME = "HLS Web";

/** Si el archivo ya existe lo baja ya; si no, lo marca para bajarlo cuando termine de procesarse. */
function requestDownload(id: string, readyUrl: string | null) {
  if (readyUrl) triggerDownload(readyUrl);
  else autoDownload.add(id);
}

function useProgressTitle() {
  const { data } = useJobsQuery();
  const active = (data ?? []).filter(isActive);
  const avg = active.length ? active.reduce((sum, j) => sum + j.percent, 0) / active.length : 0;
  useDocumentTitle(active.length ? `(${active.length}) ${Math.round(avg)}% · ${APP_NAME}` : APP_NAME);
}

export default function App() {
  const create = useCreateJob();
  const [reused, setReused] = useState<{
    id: string;
    url: string;
    quality?: number;
    mode: SubmitMode;
  } | null>(null);
  useProgressTitle();
  useAutoDownload();
  useRefetchOnReconnect(useOnline());

  return (
    <>
      <ConnectionBanner />
      <main>
        <header className="hero">
          <svg className="logo" viewBox="0 0 32 32" aria-hidden="true">
            <rect width="32" height="32" rx="9" />
            <path d="M16 7v12m0 0l-5-5m5 5l5-5M9 24h14" />
          </svg>
          <div>
            <h1>{APP_NAME}</h1>
            <p className="muted">Descarga videos con progreso en vivo.</p>
          </div>
        </header>

        <JobForm
          pending={create.isPending}
          error={create.error?.message}
          onSubmit={async (url, quality, mode) => {
            setReused(null);
            const { job, reused: wasReused } = await create.mutateAsync({ url, quality });
            if (mode === "download") requestDownload(job.id, job.status === "done" ? fileUrl(job) : null);
            if (wasReused) setReused({ id: job.id, url, quality, mode });
          }}
        />
        {reused && (
          <div role="status" className="notice info">
            <span>
              {reused.mode === "download"
                ? "Ya tenías este video: lo bajamos directo, sin procesarlo otra vez."
                : "Ya tenías este video: lo resaltamos abajo en vez de procesarlo otra vez."}
            </span>
            <button
              type="button"
              className="btn ghost"
              onClick={() => {
                const { url, quality, mode } = reused;
                setReused(null);
                void create.mutateAsync({ url, quality, force: true }).then(({ job }) => {
                  if (mode === "download") requestDownload(job.id, null);
                });
              }}
            >
              Procesar de nuevo
            </button>
          </div>
        )}
        <ErrorBoundary>
          <JobList highlightId={reused?.id} />
        </ErrorBoundary>
      </main>
      <DebugPanel />
    </>
  );
}
