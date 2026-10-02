import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { job, makeClient } from "../test/utils";
import { JOBS_KEY } from "./keys";
import { storage } from "./storage";
import { clearCachedJobs, JOBS_CACHE_KEY, loadCachedJobs, persistJobs } from "./persist";

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe("loadCachedJobs", () => {
  it("devuelve undefined sin caché", () => {
    expect(loadCachedJobs()).toBeUndefined();
  });

  it("carga y valida una lista guardada", () => {
    localStorage.setItem(JOBS_CACHE_KEY, JSON.stringify([job({ title: "Guardado" })]));
    expect(loadCachedJobs()?.[0]?.title).toBe("Guardado");
  });

  it("tolera datos de versiones anteriores (campos nuevos con default)", () => {
    const old: Record<string, unknown> = { ...job() };
    delete old.retries;
    delete old.thumbnail_version;
    localStorage.setItem(JOBS_CACHE_KEY, JSON.stringify([old]));
    expect(loadCachedJobs()?.[0]).toMatchObject({ retries: 0, thumbnail_version: 0 });
  });

  it.each(["{no es json", JSON.stringify([{ id: 1 }]), JSON.stringify({ a: 1 })])(
    "descarta caché corrupta o fuera de contrato: %s",
    (raw) => {
      localStorage.setItem(JOBS_CACHE_KEY, raw);
      expect(loadCachedJobs()).toBeUndefined();
      expect(localStorage.getItem(JOBS_CACHE_KEY)).toBeNull(); // se limpia para no fallar siempre
    },
  );

  it("no revienta si localStorage lanza", () => {
    const blocked = () => {
      throw new Error("blocked");
    };
    vi.stubGlobal("localStorage", { getItem: blocked, setItem: blocked, removeItem: blocked });
    expect(loadCachedJobs()).toBeUndefined();
    vi.unstubAllGlobals();
  });
});

describe("persistJobs", () => {
  it("guarda con throttle: muchos cambios seguidos = una sola escritura", () => {
    const qc = makeClient();
    const setItem = vi.spyOn(storage, "set"); // independiente de cómo implemente Storage cada entorno
    const stop = persistJobs(qc, 500);

    qc.setQueryData(JOBS_KEY, [job({ percent: 1 })]);
    qc.setQueryData(JOBS_KEY, [job({ percent: 2 })]);
    qc.setQueryData(JOBS_KEY, [job({ percent: 3 })]);
    expect(setItem).not.toHaveBeenCalled();

    vi.advanceTimersByTime(500);
    expect(setItem).toHaveBeenCalledTimes(1);
    expect(JSON.parse(localStorage.getItem(JOBS_CACHE_KEY)!)[0].percent).toBe(3);
    stop();
    setItem.mockRestore();
  });

  it("ignora otras queries", () => {
    const qc = makeClient();
    const stop = persistJobs(qc, 10);
    qc.setQueryData(["otra"], [1]);
    vi.advanceTimersByTime(50);
    expect(localStorage.getItem(JOBS_CACHE_KEY)).toBeNull();
    stop();
  });

  it("al detenerse guarda lo pendiente (no pierde el último cambio)", () => {
    const qc = makeClient();
    const stop = persistJobs(qc, 10_000);
    qc.setQueryData(JOBS_KEY, [job({ percent: 77 })]);
    stop();
    expect(JSON.parse(localStorage.getItem(JOBS_CACHE_KEY)!)[0].percent).toBe(77);
  });

  it("clearCachedJobs borra", () => {
    localStorage.setItem(JOBS_CACHE_KEY, "[]");
    clearCachedJobs();
    expect(localStorage.getItem(JOBS_CACHE_KEY)).toBeNull();
  });
});
