import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import { resetDebugState, setSimulatedOffline } from "./lib/debug";
import { JOBS_CACHE_KEY } from "./lib/persist";
import { FakeEventSource, job, renderWithClient } from "./test/utils";

const json = (body: unknown, status = 200) =>
  Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));

beforeEach(() => vi.stubGlobal("EventSource", FakeEventSource));
afterEach(() => {
  vi.unstubAllGlobals();
  resetDebugState();
});

describe("App (integración con fetch simulado)", () => {
  it("muestra el estado vacío", async () => {
    vi.stubGlobal("fetch", vi.fn(() => json([])));
    renderWithClient(<App />);
    expect(await screen.findByText("Aún no hay descargas.")).toBeInTheDocument();
  });

  it("crea un job y lo muestra al instante", async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn((_url: string, init?: RequestInit) =>
      init?.method === "POST" ? json(job({ status: "queued", percent: 0 }), 201) : json([]),
    );
    vi.stubGlobal("fetch", fetchMock);
    renderWithClient(<App />);

    await screen.findByText("Aún no hay descargas.");
    await user.type(screen.getByLabelText("URL del video"), "https://example.com/v.mp4");
    await user.click(screen.getByRole("button", { name: "Descargar" }));

    expect(await screen.findByText("En cola")).toBeInTheDocument();
  });

  it("muestra el mensaje del servidor cuando falla la creación", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      vi.fn((_url: string, init?: RequestInit) =>
        init?.method === "POST" ? json({ detail: "La URL apunta a una red no permitida" }, 400) : json([]),
      ),
    );
    renderWithClient(<App />);

    await user.type(screen.getByLabelText("URL del video"), "http://127.0.0.1/x.m3u8");
    await user.click(screen.getByRole("button", { name: "Descargar" }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("red no permitida"));
  });

  it("informa cuando la API no responde", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new Error("network"))));
    renderWithClient(<App />);
    expect(await screen.findByText("No se pudo conectar con la API")).toBeInTheDocument();
  });

  describe("caché local (recarga de la página)", () => {
    it("pinta al instante la última lista conocida y la confirma con el servidor", async () => {
      localStorage.setItem(JOBS_CACHE_KEY, JSON.stringify([job({ id: "c1", title: "Desde caché", status: "done", percent: 100 })]));
      let resolve!: (r: Response) => void;
      vi.stubGlobal(
        "fetch",
        vi.fn((url: string) =>
          url === "/health" ? json({ status: "ok", debug: false }) : new Promise<Response>((r) => (resolve = r)),
        ),
      );

      renderWithClient(<App />);

      // el servidor aún no respondió: ya se ve lo guardado + aviso de sincronización
      expect(screen.getByText("Desde caché")).toBeInTheDocument();
      expect(screen.getByText(/Sincronizando/)).toBeInTheDocument();

      resolve(new Response(JSON.stringify([job({ id: "c1", title: "Desde servidor", status: "done", percent: 100 })]), { status: 200 }));
      expect(await screen.findByText("Desde servidor")).toBeInTheDocument();
      expect(screen.queryByText(/Sincronizando/)).not.toBeInTheDocument();
    });

    it("el servidor manda: si ya no tiene el job, desaparece de la lista", async () => {
      localStorage.setItem(JOBS_CACHE_KEY, JSON.stringify([job({ title: "Fantasma", status: "done" })]));
      vi.stubGlobal("fetch", vi.fn(() => json([])));
      renderWithClient(<App />);
      expect(await screen.findByText("Aún no hay descargas.")).toBeInTheDocument();
    });

    it("si el servidor está caído sigue mostrando lo guardado con un aviso, no una pantalla de error", async () => {
      localStorage.setItem(JOBS_CACHE_KEY, JSON.stringify([job({ title: "Guardado", status: "done" })]));
      vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("Failed to fetch"))));
      renderWithClient(<App />);

      expect(await screen.findByText(/Mostrando datos guardados/)).toBeInTheDocument();
      expect(screen.getByText("Guardado")).toBeInTheDocument();
    });

    it("ignora una caché corrupta", async () => {
      localStorage.setItem(JOBS_CACHE_KEY, "{corrupto");
      vi.stubGlobal("fetch", vi.fn(() => json([])));
      renderWithClient(<App />);
      expect(await screen.findByText("Aún no hay descargas.")).toBeInTheDocument();
    });
  });

  describe("conexión", () => {
    it("muestra un aviso cuando no hay red y lo quita al volver", async () => {
      vi.stubGlobal("fetch", vi.fn(() => json([])));
      renderWithClient(<App />);
      await screen.findByText("Aún no hay descargas.");
      expect(screen.queryByText(/Sin conexión\./)).not.toBeInTheDocument();

      act(() => setSimulatedOffline(true));
      expect(screen.getByText(/Sin conexión\./)).toBeInTheDocument();

      act(() => setSimulatedOffline(false));
      expect(screen.queryByText(/Sin conexión\./)).not.toBeInTheDocument();
    });

    it("al volver la red refresca la lista sin recargar", async () => {
      const fetchMock = vi.fn(() => json([]));
      vi.stubGlobal("fetch", fetchMock);
      renderWithClient(<App />);
      await screen.findByText("Aún no hay descargas.");
      const before = fetchMock.mock.calls.length;

      act(() => setSimulatedOffline(true));
      act(() => setSimulatedOffline(false));

      await waitFor(() => expect(fetchMock.mock.calls.length).toBeGreaterThan(before));
    });

    it("el título de la pestaña refleja el progreso", async () => {
      vi.stubGlobal("fetch", vi.fn(() => json([job({ percent: 40 })])));
      renderWithClient(<App />);
      await waitFor(() => expect(document.title).toBe("(1) 40% · HLS Web"));
    });
  });

  it("reintentar un job fallido llama a POST /retry y lo deja en cola", async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (init?.method === "POST" && url.endsWith("/retry")) return json(job({ status: "queued", percent: 0 }), 202);
      return json([job({ status: "error", error: "boom" })]);
    });
    vi.stubGlobal("fetch", fetchMock);
    renderWithClient(<App />);

    await user.click(await screen.findByRole("button", { name: "Reintentar" }));

    expect(await screen.findByText("En cola")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/jobs/abc/retry", expect.objectContaining({ method: "POST" }));
  });

  describe("evitar descargas repetidas", () => {
    it("si el servidor ya tenía el video avisa y no duplica la fila", async () => {
      const user = userEvent.setup();
      const existing = job({ id: "dup", title: "Ya estaba", status: "done", percent: 100 });
      vi.stubGlobal(
        "fetch",
        vi.fn((_url: string, init?: RequestInit) => (init?.method === "POST" ? json(existing, 200) : json([existing]))),
      );
      renderWithClient(<App />);
      await screen.findByText("Ya estaba");

      await user.type(screen.getByLabelText("URL del video"), "https://example.com/v.mp4");
      await user.click(screen.getByRole("button", { name: "Descargar" }));

      expect(await screen.findByText(/Ya tenías este video/)).toBeInTheDocument();
      expect(screen.getAllByText("Ya estaba")).toHaveLength(1);
    });

    it("'Descargar de nuevo' reenvía la petición con force", async () => {
      const user = userEvent.setup();
      const existing = job({ id: "dup", status: "done", percent: 100 });
      const fetchMock = vi.fn((_url: string, init?: RequestInit) => {
        if (init?.method !== "POST") return json([existing]);
        const forced = JSON.parse(init.body as string).force === true;
        return forced ? json(job({ id: "nuevo", status: "queued" }), 201) : json(existing, 200);
      });
      vi.stubGlobal("fetch", fetchMock);
      renderWithClient(<App />);
      await screen.findByText("Listo");

      await user.type(screen.getByLabelText("URL del video"), "https://example.com/v.mp4");
      await user.click(screen.getByRole("button", { name: "Descargar" }));
      await user.click(await screen.findByRole("button", { name: "Descargar de nuevo" }));

      expect(await screen.findByText("En cola")).toBeInTheDocument();
      expect(screen.getAllByRole("listitem")).toHaveLength(2);
      expect(screen.queryByText(/Ya tenías este video/)).not.toBeInTheDocument();
    });
  });

  it("eliminar quita la fila y llama a DELETE /record", async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn((_url: string, init?: RequestInit) =>
      init?.method === "DELETE" ? Promise.resolve(new Response(null, { status: 204 })) : json([job({ status: "done", percent: 100 })]),
    );
    vi.stubGlobal("fetch", fetchMock);
    renderWithClient(<App />);

    await user.click(await screen.findByRole("button", { name: "Eliminar" }));
    await user.click(screen.getByRole("button", { name: "Sí, eliminar" }));

    expect(await screen.findByText("Aún no hay descargas.")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/jobs/abc/record", expect.objectContaining({ method: "DELETE" }));
  });
});
