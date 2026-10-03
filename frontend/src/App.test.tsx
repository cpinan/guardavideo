import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import { resetDebugState, setSimulatedOffline } from "./lib/debug";
import { autoDownload } from "./lib/autodownload";
import { resetToasts } from "./lib/toast";
import { JOBS_CACHE_KEY } from "./lib/persist";
import { FakeEventSource, job, renderWithClient } from "./test/utils";

const json = (body: unknown, status = 200) =>
  Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));

let clicks: string[] = [];
beforeEach(() => {
  vi.stubGlobal("EventSource", FakeEventSource);
  clicks = [];
  FakeEventSource.instances = [];
  // jsdom no descarga: registramos qué enlaces "descargó" la app
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
    clicks.push(this.getAttribute("href") ?? "");
  });
});
afterEach(() => {
  resetToasts();
  vi.restoreAllMocks();
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
    await user.click(screen.getByRole("button", { name: "Procesar y descargar" }));

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
    await user.click(screen.getByRole("button", { name: "Procesar y descargar" }));

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
          url === "/api/v1/jobs" ? new Promise<Response>((r) => (resolve = r)) : json({ status: "ok", debug: false }),
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
      await waitFor(() => expect(document.title).toBe("(1) 40% · Guardavideo"));
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
      await user.click(screen.getByRole("button", { name: "Procesar y descargar" }));

      expect(await screen.findByText(/Ya tenías este video/)).toBeInTheDocument();
      expect(screen.getAllByText("Ya estaba")).toHaveLength(1);
    });

    it("'Procesar de nuevo' reenvía la petición con force", async () => {
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
      await user.click(screen.getByRole("button", { name: "Procesar y descargar" }));
      await user.click(await screen.findByRole("button", { name: "Procesar de nuevo" }));

      expect(await screen.findByText("En cola")).toBeInTheDocument();
      expect(within(screen.getByRole("region", { name: "Descargas" })).getAllByRole("listitem")).toHaveLength(2);
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

  describe("procesar y descargar", () => {
    const post = (body: unknown, status = 201) => (_url: string, init?: RequestInit) =>
      init?.method === "POST" ? json(body, status) : json([]);

    it("al terminar el job baja el archivo una sola vez", async () => {
      const user = userEvent.setup();
      vi.stubGlobal("fetch", vi.fn(post(job({ status: "queued", percent: 0 }))));
      renderWithClient(<App />);
      await screen.findByText("Aún no hay descargas.");

      await user.type(screen.getByLabelText("URL del video"), "https://example.com/v.mp4");
      await user.click(screen.getByRole("button", { name: "Procesar y descargar" }));
      expect(await screen.findByText(/Se descargará al terminar/)).toBeInTheDocument();
      expect(clicks).toEqual([]); // todavía procesando

      act(() => FakeEventSource.instances[0]!.emit(job({ status: "done", percent: 100 })));

      await waitFor(() => expect(clicks).toEqual(["/api/v1/jobs/abc/file"]));
      expect(await screen.findByText(/Descarga iniciada/)).toBeInTheDocument(); // aviso visible
      expect(screen.getByRole("link", { name: "Descargar otra vez" })).toHaveAttribute("href", "/api/v1/jobs/abc/file");
      expect(autoDownload.has("abc")).toBe(false);
      act(() => FakeEventSource.instances[0]!.emit(job({ status: "done", percent: 100, title: "otra vez" })));
      expect(clicks).toHaveLength(1); // no se repite
    });

    it("'Solo procesar' nunca descarga por su cuenta", async () => {
      const user = userEvent.setup();
      vi.stubGlobal("fetch", vi.fn(post(job({ status: "queued", percent: 0 }))));
      renderWithClient(<App />);
      await screen.findByText("Aún no hay descargas.");

      await user.type(screen.getByLabelText("URL del video"), "https://example.com/v.mp4");
      await user.click(screen.getByRole("button", { name: "Solo procesar" }));
      await screen.findByText("En cola");
      act(() => FakeEventSource.instances[0]!.emit(job({ status: "done", percent: 100 })));

      expect(await screen.findByRole("link", { name: "Descargar" })).toBeInTheDocument();
      expect(clicks).toEqual([]);
      expect(screen.queryByText(/Se descargará al terminar/)).not.toBeInTheDocument();
    });

    it("si el video ya estaba listo lo baja de inmediato", async () => {
      const user = userEvent.setup();
      const ready = job({ id: "listo", status: "done", percent: 100 });
      vi.stubGlobal("fetch", vi.fn((_u: string, init?: RequestInit) => (init?.method === "POST" ? json(ready, 200) : json([ready]))));
      renderWithClient(<App />);
      await screen.findByText("Listo");

      await user.type(screen.getByLabelText("URL del video"), "https://example.com/v.mp4");
      await user.click(screen.getByRole("button", { name: "Procesar y descargar" }));

      await waitFor(() => expect(clicks).toEqual(["/api/v1/jobs/listo/file"]));
      expect(await screen.findByText(/lo bajamos directo/)).toBeInTheDocument();
    });

    it("si el job falla se cancela la descarga automática pendiente", async () => {
      autoDownload.add("abc");
      vi.stubGlobal("fetch", vi.fn(() => json([job({ status: "error", error: "boom" })])));
      renderWithClient(<App />);
      await screen.findByText("boom");
      expect(autoDownload.has("abc")).toBe(false);
      expect(clicks).toEqual([]);
    });

    it("tras recargar la página mientras procesaba, igual baja al terminar", async () => {
      localStorage.setItem("hlsweb:auto:v1", JSON.stringify(["abc"]));
      autoDownload.reload(); // = arranque de la página
      vi.stubGlobal("fetch", vi.fn(() => json([job({ status: "done", percent: 100 })])));
      renderWithClient(<App />);
      await waitFor(() => expect(clicks).toEqual(["/api/v1/jobs/abc/file"]));
    });
  });

  it("el enlace de ejemplo del estado vacío rellena el formulario", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("fetch", vi.fn(() => json([])));
    renderWithClient(<App />);
    await user.click(await screen.findByRole("button", { name: "Probar con un enlace de ejemplo" }));
    expect((screen.getByLabelText("URL del video") as HTMLInputElement).value).toMatch(/\.m3u8$/);
    expect(screen.getByText("Stream HLS directo")).toBeInTheDocument();
  });
});
