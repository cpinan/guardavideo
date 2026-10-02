import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import type { ReactElement } from "react";
import type { Job } from "../lib/schemas";

export function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
}

export function renderWithClient(ui: ReactElement, client = makeClient()) {
  return { client, ...render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>) };
}

export const job = (overrides: Partial<Job> = {}): Job => ({
  id: "abc",
  url: "https://example.com/v.mp4",
  status: "running",
  percent: 40,
  title: null,
  error: null,
  retries: 0,
  thumbnail_version: 0,
  created_at: null,
  size_bytes: null,
  duration_seconds: null,
  ...overrides,
});

/** EventSource falso: permite emitir eventos a mano y comprobar que se cierra. */
export class FakeEventSource {
  static instances: FakeEventSource[] = [];
  onmessage: ((e: { data: string }) => void) | null = null;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  readyState = 0;
  closed = false;
  constructor(public url: string) {
    FakeEventSource.instances.push(this);
  }
  emit(data: unknown) {
    this.onmessage?.({ data: JSON.stringify(data) });
  }
  open() {
    this.readyState = 1;
    this.onopen?.();
  }
  /** Error transitorio: el navegador reconecta solo (CONNECTING) o se rinde (CLOSED). */
  fail(giveUp = false) {
    this.readyState = giveUp ? 2 : 0;
    this.onerror?.();
  }
  close() {
    this.closed = true;
    this.readyState = 2;
  }
}
