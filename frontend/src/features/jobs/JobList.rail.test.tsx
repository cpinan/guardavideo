import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetPlayback } from "../../lib/playback";
import { FakeEventSource, job, renderWithClient } from "../../test/utils";
import { JobList } from "./JobList";

const json = (body: unknown) => Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
const now = new Date().toISOString();

const JOBS = [
  job({ id: "a", title: "Listo uno", status: "done", percent: 100, created_at: now }),
  job({ id: "b", title: "Bajando", status: "running", percent: 30, created_at: now }),
  job({ id: "c", title: "Listo dos", status: "done", percent: 100, created_at: now }),
];

/** jsdom no trae matchMedia: simulamos un teléfono (o un escritorio). */
const viewport = (phone: boolean) =>
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: phone && query.includes("max-width"),
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));

beforeEach(() => {
  resetPlayback();
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource);
  vi.stubGlobal("fetch", vi.fn(() => json(JOBS)));
});
afterEach(() => vi.unstubAllGlobals());

describe("JobList: carrusel de terminados por día", () => {
  it("separa los terminados en su propia lista; los activos quedan aparte", async () => {
    viewport(false);
    renderWithClient(<JobList />);
    await screen.findByText("Bajando");

    const active = screen.getByRole("list", { name: "Descargas · Hoy" });
    expect(within(active).getAllByRole("listitem")).toHaveLength(1);
    expect(active).toHaveTextContent("Bajando");

    const rail = screen.getByRole("list", { name: "Listos · Hoy" });
    expect(within(rail).getAllByRole("listitem").map((li) => li.querySelector("h3")?.textContent)).toEqual([
      "Listo dos",
      "Listo uno",
    ]);
  });

  it("en el teléfono, Reproducir desde el carrusel abre el reproductor ampliado", async () => {
    viewport(true);
    const user = userEvent.setup();
    renderWithClient(<JobList />);
    const rail = await screen.findByRole("list", { name: "Listos · Hoy" });

    await user.click(within(rail).getAllByRole("button", { name: "Reproducir" })[0]!);
    expect(screen.getByRole("dialog", { name: "Reproductor de Listo dos" })).toBeInTheDocument();
  });

  it("en escritorio, Reproducir sigue abriendo la vista previa en la tarjeta", async () => {
    viewport(false);
    const user = userEvent.setup();
    renderWithClient(<JobList />);
    const rail = await screen.findByRole("list", { name: "Listos · Hoy" });

    await user.click(within(rail).getAllByRole("button", { name: "Reproducir" })[0]!);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(within(rail).getByRole("button", { name: "Cerrar vista previa" })).toBeInTheDocument();
  });
});
