import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getDebugState, resetDebugState } from "../../lib/debug";
import { JOBS_CACHE_KEY } from "../../lib/persist";
import { FakeEventSource, job, renderWithClient } from "../../test/utils";
import { DebugPanel, SCENARIOS } from "./DebugPanel";

const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status }));
const health = (debug: boolean) => json({ status: "ok", debug });

beforeEach(() => vi.stubGlobal("EventSource", FakeEventSource));
afterEach(() => {
  vi.unstubAllGlobals();
  resetDebugState();
  window.history.replaceState(null, "", "/");
});

describe("DebugPanel", () => {
  it("no aparece si el backend no está en modo debug", async () => {
    const fetchMock = vi.fn(() => health(false));
    vi.stubGlobal("fetch", fetchMock);
    renderWithClient(<DebugPanel />);
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(screen.queryByRole("button", { name: /Depuración/ })).not.toBeInTheDocument();
  });

  it("aparece si /health dice debug=true y se abre y cierra", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("fetch", vi.fn(() => health(true)));
    renderWithClient(<DebugPanel />);

    const toggle = await screen.findByRole("button", { name: /Depuración/ });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    await user.click(toggle);
    expect(screen.getByRole("complementary", { name: "Herramientas de depuración" })).toBeInTheDocument();
    await user.click(toggle);
    expect(screen.queryByRole("complementary")).not.toBeInTheDocument();
  });

  it("?debug en la URL lo fuerza aunque /health falle", async () => {
    window.history.replaceState(null, "", "/?debug=1");
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new Error("x"))));
    renderWithClient(<DebugPanel />);
    expect(await screen.findByRole("button", { name: /Depuración/ })).toBeInTheDocument();
  });

  it("cada escenario crea un job contra http://debug.test/<escenario>", async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn((_url: string, init?: RequestInit) =>
      init?.method === "POST" ? json(job({ status: "queued" }), 201) : health(true),
    );
    vi.stubGlobal("fetch", fetchMock);
    renderWithClient(<DebugPanel />);
    await user.click(await screen.findByRole("button", { name: /Depuración/ }));

    await user.click(screen.getByRole("button", { name: "Red inestable" }));

    await waitFor(() => {
      const post = fetchMock.mock.calls.find(([, init]) => init?.method === "POST");
      expect(JSON.parse(post![1]!.body as string).url).toBe("http://debug.test/flaky");
    });
  });

  it("ofrece todos los escenarios del backend", () => {
    expect(SCENARIOS.map((s) => s.id).sort()).toEqual(
      ["fail", "flaky", "late-fail", "network", "ok", "slow", "stall"].sort(),
    );
  });

  it("el interruptor simula pérdida de red y lo indica en el botón", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("fetch", vi.fn(() => health(true)));
    renderWithClient(<DebugPanel />);
    await user.click(await screen.findByRole("button", { name: /Depuración/ }));

    await user.click(screen.getByRole("checkbox", { name: "Simular sin conexión" }));

    expect(getDebugState().offline).toBe(true);
    expect(screen.getByRole("button", { name: /sin red/ })).toBeInTheDocument();
  });

  it("el selector de latencia la aplica", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("fetch", vi.fn(() => health(true)));
    renderWithClient(<DebugPanel />);
    await user.click(await screen.findByRole("button", { name: /Depuración/ }));
    await user.selectOptions(screen.getByLabelText("Latencia"), "1500");
    expect(getDebugState().latencyMs).toBe(1500);
  });

  it("muestra el registro de eventos y se puede limpiar", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("fetch", vi.fn(() => health(true)));
    renderWithClient(<DebugPanel />);
    await user.click(await screen.findByRole("button", { name: /Depuración/ }));

    await user.click(screen.getByRole("checkbox", { name: "Simular sin conexión" }));
    const logList = screen.getByRole("list", { name: "Registro de eventos" });
    expect(logList).toHaveTextContent("SIN RED");

    await user.click(screen.getByRole("button", { name: "Limpiar" }));
    expect(logList).toHaveTextContent("Sin eventos todavía.");
  });

  it("'Borrar caché y recargar' elimina la lista guardada", async () => {
    const user = userEvent.setup();
    localStorage.setItem(JOBS_CACHE_KEY, "[]");
    const reload = vi.fn();
    vi.stubGlobal("location", { ...window.location, reload, search: "" });
    vi.stubGlobal("fetch", vi.fn(() => health(true)));
    renderWithClient(<DebugPanel />);
    await user.click(await screen.findByRole("button", { name: /Depuración/ }));

    await act(async () => {
      await user.click(screen.getByRole("button", { name: "Borrar caché y recargar" }));
    });

    expect(localStorage.getItem(JOBS_CACHE_KEY)).toBeNull();
    expect(reload).toHaveBeenCalled();
  });
});
