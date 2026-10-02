import { act, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getPlayback, resetPlayback, savePlayback } from "../../lib/playback";
import { FakeEventSource, job, renderWithClient } from "../../test/utils";
import { LibraryShelves } from "./LibraryShelves";
import { JobList } from "./JobList";

const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status }));
const ago = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();
const done = (id: string, extra = {}) =>
  job({ id, title: `Video ${id}`, status: "done", percent: 100, duration_seconds: 100, created_at: ago(1), ...extra });

beforeEach(() => {
  resetPlayback();
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource);
});
afterEach(() => vi.unstubAllGlobals());

const shelf = (name: string) => screen.getByRole("region", { name: new RegExp(name) });

describe("LibraryShelves", () => {
  it("sin nada que mostrar no renderiza nada", () => {
    const { container } = renderWithClient(<LibraryShelves jobs={[done("a")]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("Favoritos aparece aunque haya pocos videos", () => {
    renderWithClient(<LibraryShelves jobs={[done("a", { starred: true })]} />);
    expect(within(shelf("Favoritos")).getByRole("button", { name: "Reproducir Video a" })).toBeInTheDocument();
  });

  it("Seguir viendo usa el progreso local y muestra por dónde vas", () => {
    savePlayback("a", { t: 25, d: 100 });
    renderWithClient(<LibraryShelves jobs={[done("a"), done("b")]} />);
    const section = shelf("Seguir viendo");
    expect(within(section).getByText("Visto 0:25 / 1:40")).toBeInTheDocument();
    expect(within(section).queryByText("Video b")).not.toBeInTheDocument();
  });

  it("Más vistos y Recién descargados esperan a tener al menos 4 videos", () => {
    const few = [done("a", { view_count: 3 }), done("b")];
    const { unmount } = renderWithClient(<LibraryShelves jobs={few} />);
    expect(screen.queryByRole("region", { name: /Más vistos/ })).not.toBeInTheDocument();
    unmount();

    renderWithClient(<LibraryShelves jobs={[...few, done("c"), done("d")]} />);
    expect(within(shelf("Más vistos")).getByText("3 vistas")).toBeInTheDocument();
    expect(shelf("Recién descargados")).toBeInTheDocument();
  });

  it("al pulsar una tarjeta abre ese video en el lightbox", async () => {
    const user = userEvent.setup();
    renderWithClient(<LibraryShelves jobs={[done("a", { starred: true })]} />);
    await user.click(screen.getByRole("button", { name: "Reproducir Video a" }));
    expect(getPlayback("a")).toMatchObject({ open: true, lightbox: true });
  });

  it("la estrella de la tarjeta alterna el favorito (PATCH)", async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn(() => json(done("a", { starred: false })));
    vi.stubGlobal("fetch", fetchMock);
    renderWithClient(<LibraryShelves jobs={[done("a", { starred: true })]} />);
    await user.click(screen.getByRole("button", { name: "Quitar Video a de favoritos" }));
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/jobs/a", expect.objectContaining({ method: "PATCH" }));
  });
});

describe("JobList: favoritos, orden y estantes", () => {
  const JOBS = [
    done("a", { starred: true, view_count: 1, size_bytes: 50, created_at: ago(5) }),
    done("b", { view_count: 7, size_bytes: 500, created_at: ago(4) }),
    done("c", { size_bytes: 5, created_at: ago(3) }),
    done("d", { view_count: 3, size_bytes: 5000, created_at: ago(2) }),
  ];
  const rows = () => screen.getAllByRole("heading", { level: 3 }).map((h) => h.textContent).filter((t) => t?.startsWith("Video"));

  it("muestra los estantes y los oculta al buscar o filtrar", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("fetch", vi.fn(() => json(JOBS)));
    renderWithClient(<JobList />);
    await screen.findByRole("heading", { name: "Descargas" });
    expect(screen.getByRole("region", { name: /Favoritos/ })).toBeInTheDocument();

    await user.type(screen.getByLabelText("Buscar descargas"), "video");
    expect(screen.queryByRole("region", { name: /Favoritos/ })).not.toBeInTheDocument();
    await user.clear(screen.getByLabelText("Buscar descargas"));
    expect(screen.getByRole("region", { name: /Favoritos/ })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Listas/ }));
    expect(screen.queryByRole("region", { name: /Favoritos/ })).not.toBeInTheDocument();
  });

  it("el filtro Favoritos deja solo los marcados", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("fetch", vi.fn(() => json(JOBS)));
    renderWithClient(<JobList />);
    await screen.findByRole("heading", { name: "Descargas" });
    await user.click(screen.getByRole("button", { name: /★ Favoritos\s*1/ }));
    expect(rows()).toEqual(["Video a"]);
  });

  it("ordenar por vistas / nombre / tamaño cambia el orden y quita los grupos por día", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("fetch", vi.fn(() => json(JOBS)));
    const { container } = renderWithClient(<JobList />);
    await screen.findByRole("heading", { name: "Descargas" });
    expect(container.querySelectorAll(".day").length).toBeGreaterThan(0);

    await user.selectOptions(screen.getByLabelText("Ordenar por"), "views");
    expect(rows()).toEqual(["Video b", "Video d", "Video a", "Video c"]);
    expect(container.querySelectorAll(".day")).toHaveLength(0); // ordenado: sin grupos por día

    await user.selectOptions(screen.getByLabelText("Ordenar por"), "size");
    expect(rows()).toEqual(["Video d", "Video b", "Video a", "Video c"]);

    await user.selectOptions(screen.getByLabelText("Ordenar por"), "name");
    expect(rows()).toEqual(["Video a", "Video b", "Video c", "Video d"]);
  });

  it("las filas muestran el conteo de vistas y la estrella", async () => {
    vi.stubGlobal("fetch", vi.fn(() => json(JOBS)));
    renderWithClient(<JobList />);
    await screen.findByRole("heading", { name: "Descargas" });
    expect(screen.getAllByText(/7 vistas/).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("button", { name: /de favoritos$/ }).length).toBeGreaterThan(0);
  });
});

describe("estrella y vistas en la fila", () => {
  it("marcar favorito es optimista y se revierte si el servidor falla", async () => {
    const user = userEvent.setup();
    let release!: (r: Response) => void;
    vi.stubGlobal(
      "fetch",
      vi.fn((_u: string, init?: RequestInit) =>
        init?.method === "PATCH" ? new Promise<Response>((r) => (release = r)) : json([done("a")]),
      ),
    );
    renderWithClient(<JobList />);
    const star = await screen.findByRole("button", { name: "Marcar Video a como favorito" });
    await user.click(star);

    // optimista: ya está marcada aunque el servidor no respondió
    const marked = await screen.findAllByRole("button", { name: "Quitar Video a de favoritos" });
    marked.forEach((b) => expect(b).toHaveAttribute("aria-pressed", "true"));
    await act(async () => release(new Response("{}", { status: 500 })));
    const reverted = await screen.findAllByRole("button", { name: "Marcar Video a como favorito" });
    reverted.forEach((b) => expect(b).toHaveAttribute("aria-pressed", "false"));
    expect(screen.queryByRole("button", { name: "Quitar Video a de favoritos" })).not.toBeInTheDocument();
  });
});
