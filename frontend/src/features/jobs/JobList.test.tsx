import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { FakeEventSource, job, renderWithClient } from "../../test/utils";
import { JobList } from "./JobList";

const reply = (status: number, body: unknown) =>
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })));

beforeEach(() => {
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource);
});
afterEach(() => vi.unstubAllGlobals());

describe("JobList", () => {
  it("estado vacío", async () => {
    reply(200, []);
    renderWithClient(<JobList />);
    expect(await screen.findByText("Aún no hay descargas.")).toBeInTheDocument();
  });

  it("error de red con role=alert", async () => {
    reply(500, {});
    renderWithClient(<JobList />);
    expect(await screen.findByRole("alert")).toHaveTextContent("No se pudo conectar");
  });

  it("muestra los más nuevos primero", async () => {
    reply(200, [
      job({ id: "1", status: "done", percent: 100, title: "Viejo" }),
      job({ id: "2", status: "done", percent: 100, title: "Nuevo" }),
    ]);
    renderWithClient(<JobList />);
    const items = await screen.findAllByRole("listitem");
    expect(items[0]).toHaveTextContent("Nuevo");
    expect(items[1]).toHaveTextContent("Viejo");
  });

  it("cancelar llama a DELETE y actualiza la fila", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify([job()]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(job({ status: "cancelled" })), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    renderWithClient(<JobList />);

    await user.click(await screen.findByRole("button", { name: "Cancelar" }));
    await waitFor(() => expect(screen.queryByRole("button", { name: "Cancelar" })).not.toBeInTheDocument());
    expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({ method: "DELETE" });
  });
});
