import { QueryClientProvider } from "@tanstack/react-query";
import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getPlayback, resetPlayback, savePlayback } from "../../lib/playback";
import { FakeEventSource, job, renderWithClient } from "../../test/utils";
import { JobRow } from "./JobRow";

beforeEach(() => {
  resetPlayback();
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource);
});
afterEach(() => vi.unstubAllGlobals());

describe("JobRow", () => {
  it("muestra progreso accesible y permite cancelar un job activo", async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    renderWithClient(<JobRow job={job({ percent: 42.4 })} onCancel={onCancel} />);

    expect(screen.getByRole("progressbar", { name: /Progreso de/ })).toHaveAttribute("aria-valuenow", "42");
    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(onCancel).toHaveBeenCalledWith("abc");
  });

  it("job terminado: enlace de descarga, sin cancelar y sin abrir SSE", () => {
    renderWithClient(<JobRow job={job({ status: "done", percent: 100, title: "Mi video" })} onCancel={vi.fn()} />);

    expect(screen.getByRole("link", { name: "Descargar" })).toHaveAttribute("href", "/api/v1/jobs/abc/file");
    expect(screen.queryByRole("button", { name: "Cancelar" })).not.toBeInTheDocument();
    expect(FakeEventSource.instances).toHaveLength(0);
  });

  it("muestra el error del job con role=alert", () => {
    renderWithClient(<JobRow job={job({ status: "error", error: "sitio no soportado" })} onCancel={vi.fn()} />);
    expect(screen.getByRole("alert")).toHaveTextContent("sitio no soportado");
  });

  it("job fallido o cancelado ofrece Reintentar", async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    renderWithClient(<JobRow job={job({ status: "error", error: "x" })} onCancel={vi.fn()} onRetry={onRetry} />);
    await user.click(screen.getByRole("button", { name: "Reintentar" }));
    expect(onRetry).toHaveBeenCalledWith("abc");
  });

  it("un job activo o terminado con éxito NO ofrece Reintentar", () => {
    const { unmount } = renderWithClient(<JobRow job={job()} onCancel={vi.fn()} onRetry={vi.fn()} />);
    expect(screen.queryByRole("button", { name: "Reintentar" })).not.toBeInTheDocument();
    unmount();
    renderWithClient(<JobRow job={job({ status: "done", percent: 100 })} onCancel={vi.fn()} onRetry={vi.fn()} />);
    expect(screen.queryByRole("button", { name: "Reintentar" })).not.toBeInTheDocument();
  });

  it("muestra que está reconectando con el origen y en qué intento", () => {
    renderWithClient(<JobRow job={job({ retries: 3 })} onCancel={vi.fn()} />);
    expect(screen.getByText(/Reconectando con el origen… intento 3/)).toBeInTheDocument();
    expect(screen.getByText("Reintentando")).toBeInTheDocument();
  });

  it("avisa cuando se pierde la conexión de progreso en vivo", () => {
    renderWithClient(<JobRow job={job()} onCancel={vi.fn()} />);
    act(() => FakeEventSource.instances[0]!.fail());
    expect(screen.getByText(/Reconectando para ver el progreso/)).toBeInTheDocument();
  });

  it("en cola: progreso indeterminado", () => {
    renderWithClient(<JobRow job={job({ status: "queued", percent: 0 })} onCancel={vi.fn()} />);
    expect(screen.getByText("Esperando turno")).toBeInTheDocument();
  });

  it("muestra la miniatura con versión en la URL y alt descriptivo", () => {
    renderWithClient(<JobRow job={job({ title: "Mi video", thumbnail_version: 2 })} onCancel={vi.fn()} />);
    const img = screen.getByRole("img", { name: "Vista previa de Mi video" });
    expect(img).toHaveAttribute("src", "/api/v1/jobs/abc/thumbnail?v=2");
  });

  it("sin miniatura no renderiza <img>", () => {
    renderWithClient(<JobRow job={job()} onCancel={vi.fn()} />);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("al reemplazarse la miniatura se carga la nueva versión", () => {
    const { rerender, client } = renderWithClient(<JobRow job={job({ thumbnail_version: 1 })} onCancel={vi.fn()} />);
    expect(screen.getByRole("img")).toHaveAttribute("src", expect.stringContaining("v=1"));
    rerender(
      <QueryClientProvider client={client}>
        <JobRow job={job({ thumbnail_version: 2 })} onCancel={vi.fn()} />
      </QueryClientProvider>,
    );
    expect(screen.getByRole("img")).toHaveAttribute("src", expect.stringContaining("v=2"));
  });

  it("muestra tamaño y fecha del video descargado", () => {
    const created = new Date(Date.now() - 5 * 60_000).toISOString();
    renderWithClient(
      <JobRow job={job({ status: "done", percent: 100, size_bytes: 5 * 1024 * 1024, created_at: created })} onCancel={vi.fn()} />,
    );
    expect(screen.getByText(/example\.com · 5,0 MB · hace 5 minutos/)).toBeInTheDocument();
  });

  it("no muestra tamaño mientras descarga", () => {
    renderWithClient(<JobRow job={job({ size_bytes: 1024 })} onCancel={vi.fn()} />);
    expect(screen.queryByText(/KB/)).not.toBeInTheDocument();
  });

  it("eliminar pide confirmación antes de borrar", async () => {
    const user = userEvent.setup();
    const onDelete = vi.fn();
    renderWithClient(<JobRow job={job({ status: "done", percent: 100 })} onCancel={vi.fn()} onDelete={onDelete} />);

    await user.click(screen.getByRole("button", { name: "Eliminar" }));
    expect(onDelete).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Conservar" })); // arrepentirse
    expect(screen.getByRole("button", { name: "Eliminar" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Eliminar" }));
    await user.click(screen.getByRole("button", { name: "Sí, eliminar" }));
    expect(onDelete).toHaveBeenCalledWith("abc");
  });

  it("un job activo no se puede eliminar", () => {
    renderWithClient(<JobRow job={job()} onCancel={vi.fn()} onDelete={vi.fn()} />);
    expect(screen.queryByRole("button", { name: "Eliminar" })).not.toBeInTheDocument();
  });

  it("resaltado: se lleva a la vista", () => {
    const scroll = vi.fn();
    Element.prototype.scrollIntoView = scroll;
    renderWithClient(<JobRow job={job({ status: "done" })} onCancel={vi.fn()} highlight />);
    expect(scroll).toHaveBeenCalled();
  });

  describe("reproductor en línea", () => {
    const done = (extra = {}) => job({ status: "done", percent: 100, title: "Mi video", ...extra });

    it("Reproducir abre el reproductor y Cerrar vista previa lo oculta", async () => {
      const user = userEvent.setup();
      renderWithClient(<JobRow job={done()} onCancel={vi.fn()} />);
      expect(screen.queryByLabelText(/Reproductor de/)).not.toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "Reproducir" }));
      expect(screen.getByLabelText("Reproductor de Mi video")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Cerrar vista previa" })).toHaveAttribute("aria-expanded", "true");

      await user.click(screen.getByRole("button", { name: "Cerrar vista previa" }));
      expect(screen.queryByLabelText(/Reproductor de/)).not.toBeInTheDocument();
    });

    it("recuerda que estaba abierto: tras recargar sigue abierto", async () => {
      const user = userEvent.setup();
      const first = renderWithClient(<JobRow job={done()} onCancel={vi.fn()} />);
      await user.click(screen.getByRole("button", { name: "Reproducir" }));
      first.unmount();

      renderWithClient(<JobRow job={done()} onCancel={vi.fn()} />);
      expect(screen.getByLabelText("Reproductor de Mi video")).toBeInTheDocument();
      expect(getPlayback("abc")?.open).toBe(true);
    });

    it("no ofrece reproducir un job que aún no terminó", () => {
      renderWithClient(<JobRow job={job()} onCancel={vi.fn()} />);
      expect(screen.queryByRole("button", { name: "Reproducir" })).not.toBeInTheDocument();
    });

    it("muestra la duración sobre la miniatura", () => {
      renderWithClient(<JobRow job={done({ duration_seconds: 3725 })} onCancel={vi.fn()} />);
      expect(screen.getByText("1:02:05")).toBeInTheDocument();
    });

    it("muestra por dónde vas: 'Visto 1:23 / 5:00'", () => {
      savePlayback("abc", { t: 83, d: 300 });
      renderWithClient(<JobRow job={done({ duration_seconds: 300 })} onCancel={vi.fn()} />);
      expect(screen.getByText("Visto 1:23 / 5:00")).toBeInTheDocument();
    });

    it("muestra 'Visto' cuando llegaste al final", () => {
      savePlayback("abc", { t: 0, d: 300, watched: true });
      renderWithClient(<JobRow job={done()} onCancel={vi.fn()} />);
      expect(screen.getByText("Visto")).toBeInTheDocument();
    });
  });

  describe("tarjeta de video", () => {
    const done = (extra = {}) => job({ status: "done", percent: 100, title: "Mi video", ...extra });

    it("un video listo no muestra barra ni porcentaje (ya no aportan nada)", () => {
      renderWithClient(<JobRow job={done()} onCancel={vi.fn()} />);
      expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
      expect(screen.queryByText("100%")).not.toBeInTheDocument();
    });

    it("sin título muestra el sitio y no la URL cruda", () => {
      renderWithClient(<JobRow job={job({ title: null, url: "https://www.dailymotion.com/video/x8abc" })} onCancel={vi.fn()} />);
      expect(screen.getByRole("heading", { name: "dailymotion.com · x8abc" })).toBeInTheDocument();
    });

    it("traduce el error técnico y deja el original en 'Ver detalles'", () => {
      renderWithClient(
        <JobRow job={job({ status: "error", error: "ERROR: Private video. Sign in if you've been granted access" })} onCancel={vi.fn()} />,
      );
      const alert = screen.getByRole("alert");
      expect(alert).toHaveTextContent("Este video es privado.");
      expect(screen.getByText("Ver detalles")).toBeInTheDocument();
      expect(alert).toHaveTextContent("Sign in if you've been granted access");
    });

    it("Ampliar abre el lightbox y Reducir vuelve a la tarjeta, recordándolo", async () => {
      const user = userEvent.setup();
      renderWithClient(<JobRow job={done()} onCancel={vi.fn()} />);
      await user.click(screen.getByRole("button", { name: "Reproducir" }));
      await user.click(screen.getByRole("button", { name: "Ampliar" }));

      expect(screen.getByRole("dialog", { name: "Reproductor de Mi video" })).toBeInTheDocument();
      expect(getPlayback("abc")).toMatchObject({ open: true, lightbox: true });

      await user.click(screen.getByRole("button", { name: "Reducir" }));
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      expect(screen.getByLabelText("Reproductor de Mi video")).toBeInTheDocument();
      expect(getPlayback("abc")).toMatchObject({ open: true, lightbox: false });
    });

    it("tras recargar con el lightbox abierto, sigue abierto", () => {
      savePlayback("abc", { open: true, lightbox: true });
      renderWithClient(<JobRow job={done()} onCancel={vi.fn()} />);
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    it("Cerrar reproductor cierra todo", async () => {
      const user = userEvent.setup();
      savePlayback("abc", { open: true, lightbox: true });
      renderWithClient(<JobRow job={done()} onCancel={vi.fn()} />);
      await user.click(screen.getByRole("button", { name: "Cerrar reproductor" }));
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      expect(getPlayback("abc")).toMatchObject({ open: false, lightbox: false });
    });

    it("si cierras el reproductor con el video en PiP, el <video> sigue montado y oculto", async () => {
      const user = userEvent.setup();
      const { container } = renderWithClient(<JobRow job={done()} onCancel={vi.fn()} />);
      await user.click(screen.getByRole("button", { name: "Reproducir" }));
      const video = container.querySelector("video")!;

      act(() => void video.dispatchEvent(new Event("enterpictureinpicture")));
      await user.click(screen.getByRole("button", { name: "Cerrar vista previa" }));

      expect(container.querySelector("video")).toBe(video); // misma instancia: el PiP no se corta
      expect(container.querySelector(".player")).toHaveAttribute("hidden");

      act(() => void video.dispatchEvent(new Event("leavepictureinpicture"))); // cierras la ventana PiP
      expect(container.querySelector("video")).toBeNull();
    });

    it("con progreso guardado el botón principal dice 'Continuar 1:23'", () => {
      savePlayback("abc", { t: 83, d: 300 });
      renderWithClient(<JobRow job={done()} onCancel={vi.fn()} />);
      expect(screen.getByRole("button", { name: "Continuar 1:23" })).toBeInTheDocument();
    });

    it("eliminar es un botón de icono con nombre accesible", async () => {
      const user = userEvent.setup();
      const onDelete = vi.fn();
      renderWithClient(<JobRow job={done()} onCancel={vi.fn()} onDelete={onDelete} />);
      await user.click(screen.getByRole("button", { name: "Eliminar" }));
      await user.click(screen.getByRole("button", { name: "Sí, eliminar" }));
      expect(onDelete).toHaveBeenCalledWith("abc");
    });
  });
});
