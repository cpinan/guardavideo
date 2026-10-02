import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { job } from "../test/utils";
import { useJobsQuery } from "../features/jobs/useJobs";
import { ApiError } from "./api";
import { createQueryClient, MAX_RETRIES, retryDelay, shouldRetry, sseDelay } from "./retry";

afterEach(() => vi.unstubAllGlobals());

describe("shouldRetry", () => {
  it.each([
    ["red caída", new ApiError(0, "x")],
    ["500", new ApiError(500, "x")],
    ["503", new ApiError(503, "x")],
    ["408", new ApiError(408, "x")],
    ["429", new ApiError(429, "x")],
  ])("reintenta: %s", (_, err) => expect(shouldRetry(0, err)).toBe(true));

  it.each([
    ["400", new ApiError(400, "x")],
    ["404", new ApiError(404, "x")],
    ["422", new ApiError(422, "x")],
    ["error genérico (contrato roto)", new Error("zod")],
  ])("NO reintenta: %s", (_, err) => expect(shouldRetry(0, err)).toBe(false));

  it("se rinde tras MAX_RETRIES", () => {
    const err = new ApiError(0, "x");
    expect(shouldRetry(MAX_RETRIES - 1, err)).toBe(true);
    expect(shouldRetry(MAX_RETRIES, err)).toBe(false);
  });
});

describe("backoff", () => {
  it("crece exponencialmente con tope", () => {
    expect([0, 1, 2, 3, 4, 5, 9].map(retryDelay)).toEqual([1000, 2000, 4000, 8000, 15000, 15000, 15000]);
    expect([1, 2, 3, 6, 20].map(sseDelay)).toEqual([500, 1000, 2000, 15000, 15000]);
  });
});

describe("createQueryClient (integración)", () => {
  it("se recupera solo cuando la red vuelve tras fallar", async () => {
    const client = createQueryClient();
    client.setDefaultOptions({ queries: { ...client.getDefaultOptions().queries, retryDelay: 1 } });
    const ok = () => Promise.resolve(new Response(JSON.stringify([job()]), { status: 200 }));
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new TypeError("Failed to fetch"))
      .mockRejectedValueOnce(new TypeError("Failed to fetch"))
      .mockImplementation(ok);
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    const { result } = renderHook(() => useJobsQuery(), { wrapper });

    await waitFor(() => expect(result.current.data).toHaveLength(1));
    expect(fetchMock).toHaveBeenCalledTimes(3); // 2 fallos + 1 éxito
    expect(result.current.isError).toBe(false);
  });

  it("no reintenta un 404", async () => {
    const client = createQueryClient();
    const fetchMock = vi.fn(() => Promise.resolve(new Response("{}", { status: 404 })));
    vi.stubGlobal("fetch", fetchMock);
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    const { result } = renderHook(() => useJobsQuery(), { wrapper });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
