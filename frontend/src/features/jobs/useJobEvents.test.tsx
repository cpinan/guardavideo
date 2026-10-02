import { act, renderHook } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetDebugState, setSimulatedOffline } from "../../lib/debug";
import { FakeEventSource, job, makeClient } from "../../test/utils";
import { JOBS_KEY } from "./useJobs";
import { useJobEvents } from "./useJobEvents";
import type { Job } from "../../lib/schemas";

beforeEach(() => {
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource);
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
  resetDebugState();
});

function setup(initial: Job) {
  const client = makeClient();
  client.setQueryData(JOBS_KEY, [initial, job({ id: "otro" })]);
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const hook = renderHook(() => useJobEvents(initial), { wrapper });
  return { client, ...hook };
}

describe("useJobEvents", () => {
  it("parchea solo el job afectado y conserva la referencia de los demás", () => {
    const { client } = setup(job());
    const before = client.getQueryData<Job[]>(JOBS_KEY)!;

    act(() => FakeEventSource.instances[0]!.emit({ ...job(), percent: 75 }));

    const after = client.getQueryData<Job[]>(JOBS_KEY)!;
    expect(after[0]!.percent).toBe(75);
    expect(after[1]).toBe(before[1]); // misma referencia -> React.memo evita re-render
  });

  it("cierra la conexión cuando el job termina", () => {
    setup(job());
    const es = FakeEventSource.instances[0]!;
    act(() => es.emit({ ...job(), status: "done", percent: 100 }));
    expect(es.closed).toBe(true);
  });

  it("cierra la conexión al desmontar (sin fugas)", () => {
    const { unmount } = setup(job());
    unmount();
    expect(FakeEventSource.instances[0]!.closed).toBe(true);
  });

  it("ignora mensajes que no cumplen el contrato", () => {
    const { client } = setup(job());
    act(() => FakeEventSource.instances[0]!.emit({ basura: true }));
    expect(client.getQueryData<Job[]>(JOBS_KEY)![0]!.percent).toBe(40);
  });

  describe("reconexión", () => {
    it("pasa a 'reconnecting' ante un error y vuelve a 'live' al reconectar", () => {
      const { result } = setup(job());
      const es = FakeEventSource.instances[0]!;

      act(() => es.open());
      expect(result.current).toBe("live");
      act(() => es.fail()); // el navegador reconecta solo
      expect(result.current).toBe("reconnecting");
      expect(FakeEventSource.instances).toHaveLength(1); // no duplicamos conexiones
      act(() => es.open());
      expect(result.current).toBe("live");
    });

    it("si el navegador se rinde (CLOSED) reabre con backoff exponencial", () => {
      vi.useFakeTimers();
      setup(job());

      act(() => FakeEventSource.instances[0]!.fail(true));
      expect(FakeEventSource.instances).toHaveLength(1);
      act(() => void vi.advanceTimersByTime(500)); // 1er fallo: 500 ms
      expect(FakeEventSource.instances).toHaveLength(2);

      act(() => FakeEventSource.instances[1]!.fail(true));
      act(() => void vi.advanceTimersByTime(500));
      expect(FakeEventSource.instances).toHaveLength(2); // 2º fallo: ahora espera 1000 ms
      act(() => void vi.advanceTimersByTime(500));
      expect(FakeEventSource.instances).toHaveLength(3);
      expect(FakeEventSource.instances[0]!.closed).toBe(true); // la vieja se cierra: sin fugas
    });

    it("al recuperarse tras un corte, refresca la lista para no perder cambios", () => {
      const { client } = setup(job());
      const invalidate = vi.spyOn(client, "invalidateQueries");
      const es = FakeEventSource.instances[0]!;

      act(() => es.open()); // primera conexión: nada que resincronizar
      expect(invalidate).not.toHaveBeenCalled();
      act(() => es.fail());
      act(() => es.open());
      expect(invalidate).toHaveBeenCalledWith({ queryKey: JOBS_KEY });
    });

    it("cancelar el reintento al desmontar (sin timers huérfanos)", () => {
      vi.useFakeTimers();
      const { unmount } = setup(job());
      act(() => FakeEventSource.instances[0]!.fail(true));
      unmount();
      act(() => void vi.advanceTimersByTime(60_000));
      expect(FakeEventSource.instances).toHaveLength(1);
    });

    it("con red simulada caída cierra el SSE y lo reabre al volver", () => {
      const { result } = setup(job());
      const first = FakeEventSource.instances[0]!;

      act(() => setSimulatedOffline(true));
      expect(first.closed).toBe(true);
      expect(result.current).toBe("reconnecting");

      act(() => setSimulatedOffline(false));
      expect(FakeEventSource.instances).toHaveLength(2);
    });

    it("un job terminado no abre conexión y queda 'idle'", () => {
      const { result } = setup(job({ status: "done" }));
      expect(FakeEventSource.instances).toHaveLength(0);
      expect(result.current).toBe("idle");
    });
  });
});
