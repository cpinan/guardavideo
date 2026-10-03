import { useState } from "react";
import { ConnectionBanner } from "./components/ConnectionBanner";
import { ThemeToggle } from "./components/ThemeToggle";
import { Toaster } from "./components/Toaster";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { DebugPanel } from "./features/debug/DebugPanel";
import { JobForm, type SubmitMode } from "./features/jobs/JobForm";
import { ProvidersList } from "./features/jobs/ProvidersList";
import { JobList } from "./features/jobs/JobList";
import { LibraryStats } from "./features/jobs/LibraryStats";
import { useAutoDownload } from "./features/jobs/useAutoDownload";
import { useCreateJob, useJobsQuery } from "./features/jobs/useJobs";
import { fileUrl } from "./lib/api";
import { autoDownload, triggerDownload } from "./lib/autodownload";
import { showToast } from "./lib/toast";
import { useDocumentTitle, useOnline, useRefetchOnReconnect } from "./lib/hooks";
import { isActive } from "./lib/schemas";

const APP_NAME = "Guardavideo";
/** Stream de prueba público de Apple (no requiere cuenta). */
const EXAMPLE_URL = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8";

/** Si el archivo ya existe lo baja ya; si no, lo marca para bajarlo cuando termine de procesarse. */
function requestDownload(id: string, readyUrl: string | null) {
  if (readyUrl) {
    triggerDownload(readyUrl);
    showToast("Descarga iniciada", { label: "Descargar otra vez", href: readyUrl });
  } else autoDownload.add(id);
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
  const [example, setExample] = useState<{ url: string; nonce: number } | undefined>();
  useProgressTitle();
  useAutoDownload();
  useRefetchOnReconnect(useOnline());

  return (
    <>
      <ConnectionBanner />
      <main className="layout">
        <div className="side">
          <header className="hero">
            <svg className="logo" viewBox="0 0 32 32" aria-hidden="true">
              <rect width="32" height="32" rx="9" />
              <path d="M16 7v12m0 0l-5-5m5 5l5-5M9 24h14" />
            </svg>
            <div className="hero-text">
              <h1>{APP_NAME}</h1>
              <p className="muted">Descarga videos con progreso en vivo.</p>
            </div>
            <ThemeToggle />
          </header>

          <JobForm
            pending={create.isPending}
            error={create.error?.message}
            injectUrl={example}
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
          <LibraryStats />
          <ProvidersList />
        </div>

        <div className="content">
          <ErrorBoundary>
            <JobList
              highlightId={reused?.id}
              onExample={() => setExample({ url: EXAMPLE_URL, nonce: Date.now() })}
            />
          </ErrorBoundary>
        </div>
      </main>
      <DebugPanel />
      <Toaster />
    </>
  );
}
