import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { JobForm } from "./JobForm";

describe("JobForm", () => {
  it("deshabilita el botón y muestra error accesible con una URL inválida", async () => {
    const user = userEvent.setup();
    render(<JobForm onSubmit={vi.fn()} pending={false} />);

    await user.type(screen.getByLabelText("URL del video"), "no-es-url");
    await user.tab(); // blur

    expect(screen.getByRole("button", { name: "Descargar" })).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent("URL http(s) válida");
    expect(screen.getByLabelText("URL del video")).toBeInvalid();
  });

  it("envía url y calidad, y limpia el campo", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<JobForm onSubmit={onSubmit} pending={false} />);

    await user.type(screen.getByLabelText("URL del video"), "https://vimeo.com/1");
    await user.selectOptions(screen.getByLabelText("Calidad"), "720");
    await user.click(screen.getByRole("button", { name: "Descargar" }));

    expect(onSubmit).toHaveBeenCalledWith("https://vimeo.com/1", 720);
    expect(screen.getByLabelText("URL del video")).toHaveValue("");
  });

  it("muestra el error del servidor", () => {
    render(<JobForm onSubmit={vi.fn()} pending={false} error="La URL apunta a una red no permitida" />);
    expect(screen.getByRole("alert")).toHaveTextContent("red no permitida");
  });

  it("guarda el borrador: tras recargar la página no se pierde lo escrito", async () => {
    const user = userEvent.setup();
    const { unmount } = render(<JobForm onSubmit={vi.fn()} pending={false} />);
    await user.type(screen.getByLabelText("URL del video"), "https://vimeo.com/99");
    await user.selectOptions(screen.getByLabelText("Calidad"), "480");
    unmount(); // "recarga"

    render(<JobForm onSubmit={vi.fn()} pending={false} />);
    expect(screen.getByLabelText("URL del video")).toHaveValue("https://vimeo.com/99");
    expect(screen.getByLabelText("Calidad")).toHaveValue("480");
  });

  it("si el envío falla conserva la URL para reintentar sin volver a escribirla", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockRejectedValue(new Error("Sin conexión"));
    render(<JobForm onSubmit={onSubmit} pending={false} error="Sin conexión con el servidor" />);

    await user.type(screen.getByLabelText("URL del video"), "https://vimeo.com/1");
    await user.click(screen.getByRole("button", { name: "Descargar" }));

    expect(screen.getByLabelText("URL del video")).toHaveValue("https://vimeo.com/1");
    expect(screen.getByRole("alert")).toHaveTextContent("Sin conexión");
  });

  it("tras un envío correcto limpia el campo y el borrador", async () => {
    const user = userEvent.setup();
    render(<JobForm onSubmit={vi.fn().mockResolvedValue(undefined)} pending={false} />);
    await user.type(screen.getByLabelText("URL del video"), "https://vimeo.com/1");
    await user.click(screen.getByRole("button", { name: "Descargar" }));

    await waitFor(() => expect(screen.getByLabelText("URL del video")).toHaveValue(""));
    expect(localStorage.getItem("hlsweb:draft:v1")).toBeNull();
  });

  it("indica qué descargador se usará", async () => {
    const user = userEvent.setup();
    render(<JobForm onSubmit={vi.fn()} pending={false} />);
    await user.type(screen.getByLabelText("URL del video"), "https://cdn.example.com/a.m3u8");
    expect(screen.getByText("Stream HLS directo")).toBeInTheDocument();
  });

  it("no envía dos veces mientras está pendiente", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<JobForm onSubmit={onSubmit} pending />);
    await user.type(screen.getByLabelText("URL del video"), "https://vimeo.com/1{enter}");
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
