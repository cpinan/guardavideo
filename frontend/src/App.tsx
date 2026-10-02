import { useState } from "react";
import { ConnectionBanner } from "./components/ConnectionBanner";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { DebugPanel } from "./features/debug/DebugPanel";
import { JobForm } from "./features/jobs/JobForm";
import { JobList } from "./features/jobs/JobList";
import { useCreateJob, useJobsQuery } from "./features/jobs/useJobs";
import { useDocumentTitle, useOnline, useRefetchOnReconnect } from "./lib/hooks";
import { isActive } from "./lib/schemas";

const APP_NAME = "HLS Web";

function useProgressTitle() {
  const { data } = useJobsQuery();
  const active = (data ?? []).filter(isActive);
  const avg = active.length ? active.reduce((sum, j) => sum + j.percent, 0) / active.length : 0;
  useDocumentTitle(active.length ? `(${active.length}) ${Math.round(avg)}% · ${APP_NAME}` : APP_NAME);
}

export default function App() {
  const create = useCreateJob();
  const [reused, setReused] = useState<{ id: string; url: string; quality?: number } | null>(null);
  useProgressTitle();
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
          onSubmit={async (url, quality) => {
            setReused(null);
            const { job, reused: wasReused } = await create.mutateAsync({ url, quality });
            if (wasReused) setReused({ id: job.id, url, quality });
          }}
        />
        {reused && (
          <div role="status" className="notice info">
            <span>Ya tenías este video: lo resaltamos abajo en vez de descargarlo otra vez.</span>
            <button
              type="button"
              className="btn ghost"
              onClick={() => {
                const { url, quality } = reused;
                setReused(null);
                create.mutate({ url, quality, force: true });
              }}
            >
              Descargar de nuevo
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
