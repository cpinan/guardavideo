import { afterEach, describe, expect, it, vi } from "vitest";
import { job } from "../test/utils";
import { ApiError, createJob, listJobs } from "./api";
import { getDebugState, resetDebugState, setSimulatedLatency, setSimulatedOffline } from "./debug";

afterEach(() => {
  resetDebugState();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("simulación de red", () => {
  it("offline simulado: falla con ApiError(0) SIN llamar a fetch", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    setSimulatedOffline(true);

    const err = await listJobs().catch((e: unknown) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).isNetwork).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("al restablecer la red vuelve a funcionar", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response("[]", { status: 200 }))));
    setSimulatedOffline(true);
    setSimulatedOffline(false);
    await expect(listJobs()).resolves.toEqual([]);
  });

  it("un fallo real de fetch también se traduce a error de red", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("Failed to fetch"))));
    await expect(createJob("https://a.test/v.mp4")).rejects.toMatchObject({ status: 0 });
  });

  it("la latencia simulada retrasa la petición", async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify([job()]), { status: 200 })));
    vi.stubGlobal("fetch", fetchMock);
    setSimulatedLatency(1500);

    const pending = listJobs();
    await vi.advanceTimersByTimeAsync(1000);
    expect(fetchMock).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(600);
    await expect(pending).resolves.toHaveLength(1);
  });

  it("registra lo que pasa (máximo 60 entradas)", async () => {
    for (let i = 0; i < 70; i++) setSimulatedLatency(i);
    const { log } = getDebugState();
    expect(log).toHaveLength(60);
    expect(log.at(-1)?.message).toContain("69");
  });
});
