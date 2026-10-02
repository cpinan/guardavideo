import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { FakeEventSource, job, renderWithClient } from "../../test/utils";
import { JobList } from "./JobList";

const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status }));
const daysAgo = (n: number) => new Date(Date.now() - n * 86_400_000).toISOString();

const JOBS = [
  job({ id: "a", title: "Receta de pan", status: "done", percent: 100, created_at: daysAgo(3) }),
  job({ id: "b", title: "Paisaje", status: "done", percent: 100, created_at: daysAgo(1) }),
  job({ id: "c", title: "Roto", status: "error", error: "boom", created_at: daysAgo(0) }),
  job({ id: "d", title: "En proceso", status: "running", percent: 30, created_at: daysAgo(0) }),
];

beforeEach(() => {
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource);
});
afterEach(() => vi.unstubAllGlobals());

const titles = () => screen.getAllByRole("heading", { level: 3 }).map((h) => h.textContent);

describe("JobList: filtros, búsqueda y grupos", () => {
  it("agrupa por día con encabezados Hoy / Ayer", async () => {
    vi.stubGlobal("fetch", vi.fn(() => json(JOBS)));
    renderWithClient(<JobList />);
    await screen.findByText("Receta de pan");
    expect(screen.getByRole("heading", { name: "Hoy" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Ayer" })).toBeInTheDocument();
    expect(screen.getByRole("list", { name: "Descargas · Hoy" })).toBeInTheDocument();
  });

  it("los filtros muestran conteos y filtran", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("fetch", vi.fn(() => json(JOBS)));
    renderWithClient(<JobList />);
    await screen.findByText("Receta de pan");

    expect(screen.getByRole("button", { name: /Todas\s*4/ })).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: /Listas\s*2/ }));
    expect(titles().filter((t) => ["Receta de pan", "Paisaje", "Roto", "En proceso"].includes(t ?? ""))).toEqual([
      "Paisaje",
      "Receta de pan",
    ]);

    await user.click(screen.getByRole("button", { name: /Con error\s*1/ }));
    expect(screen.getByText("Roto")).toBeInTheDocument();
    expect(screen.queryByText("Paisaje")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /En curso\s*1/ }));
    expect(screen.getByText("En proceso")).toBeInTheDocument();
  });

  it("no ofrece filtros con cero elementos", async () => {
    vi.stubGlobal("fetch", vi.fn(() => json([JOBS[0]])));
    renderWithClient(<JobList />);
    await screen.findByText("Receta de pan");
    expect(screen.queryByRole("button", { name: /Con error/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /En curso/ })).not.toBeInTheDocument();
  });

  it("busca por título (sin distinguir mayúsculas) y avisa si nada coincide", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("fetch", vi.fn(() => json(JOBS)));
    renderWithClient(<JobList />);
    await screen.findByText("Receta de pan");

    await user.type(screen.getByLabelText("Buscar descargas"), "PAISAJ");
    expect(screen.getByText("Paisaje")).toBeInTheDocument();
    expect(screen.queryByText("Receta de pan")).not.toBeInTheDocument();

    await user.clear(screen.getByLabelText("Buscar descargas"));
    await user.type(screen.getByLabelText("Buscar descargas"), "zzz");
    expect(screen.getByText(/Nada coincide/)).toBeInTheDocument();
  });

  it("'Limpiar con error' pide confirmación y elimina solo los fallidos", async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn((_url: string, init?: RequestInit) =>
      init?.method === "DELETE" ? Promise.resolve(new Response(null, { status: 204 })) : json(JOBS),
    );
    vi.stubGlobal("fetch", fetchMock);
    renderWithClient(<JobList />);
    await screen.findByText("Roto");

    await user.click(screen.getByRole("button", { name: "Limpiar con error (1)" }));
    expect(screen.getByText(/¿Eliminar 1 registro con error\?/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Conservar" })); // arrepentirse
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining("/record"), expect.anything());

    await user.click(screen.getByRole("button", { name: "Limpiar con error (1)" }));
    await user.click(screen.getByRole("button", { name: "Sí, eliminar" }));

    await waitFor(() => expect(screen.queryByText("Roto")).not.toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/jobs/c/record", expect.objectContaining({ method: "DELETE" }));
    expect(screen.getByText("Paisaje")).toBeInTheDocument(); // los demás siguen
  });

  it("estado vacío con enlace de ejemplo", async () => {
    const user = userEvent.setup();
    const onExample = vi.fn();
    vi.stubGlobal("fetch", vi.fn(() => json([])));
    renderWithClient(<JobList onExample={onExample} />);
    await user.click(await screen.findByRole("button", { name: "Probar con un enlace de ejemplo" }));
    expect(onExample).toHaveBeenCalled();
  });

  it("cada tarjeta mantiene su lista accesible dentro del grupo", async () => {
    vi.stubGlobal("fetch", vi.fn(() => json(JOBS)));
    renderWithClient(<JobList />);
    await screen.findByText("Receta de pan");
    const hoy = screen.getByRole("list", { name: "Descargas · Hoy" });
    expect(within(hoy).getAllByRole("listitem")).toHaveLength(2);
  });
});
