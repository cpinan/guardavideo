import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { JobForm } from "./JobForm";

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: query.includes("max-width"),
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
});
afterEach(() => vi.unstubAllGlobals());

const actions = () => screen.queryByRole("button", { name: "Procesar y descargar" });

describe("JobForm compacto en el teléfono", () => {
  it("vacío y sin foco muestra solo la URL; al tocarla aparecen calidad y acciones", async () => {
    const user = userEvent.setup();
    render(<JobForm onSubmit={vi.fn()} pending={false} />);
    expect(screen.getByLabelText("URL del video")).toBeInTheDocument();
    expect(screen.queryByLabelText("Calidad")).not.toBeInTheDocument();
    expect(actions()).not.toBeInTheDocument();

    await user.click(screen.getByLabelText("URL del video"));
    expect(screen.getByLabelText("Calidad")).toBeInTheDocument();
    expect(actions()).toBeInTheDocument();
  });

  it("moverse entre sus controles no lo pliega; salir del formulario vacío sí", async () => {
    const user = userEvent.setup();
    render(
      <>
        <JobForm onSubmit={vi.fn()} pending={false} />
        <button type="button">fuera</button>
      </>,
    );
    await user.click(screen.getByLabelText("URL del video"));
    await user.click(screen.getByLabelText("Calidad"));
    expect(actions()).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "fuera" }));
    expect(actions()).not.toBeInTheDocument();
  });

  it("con una URL escrita sigue desplegado aunque pierda el foco", async () => {
    const user = userEvent.setup();
    render(
      <>
        <JobForm onSubmit={vi.fn()} pending={false} />
        <button type="button">fuera</button>
      </>,
    );
    await user.type(screen.getByLabelText("URL del video"), "https://vimeo.com/1");
    await user.click(screen.getByRole("button", { name: "fuera" }));
    expect(actions()).toBeEnabled();
  });
});
