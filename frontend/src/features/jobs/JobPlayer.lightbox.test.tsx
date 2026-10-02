import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetPlayback } from "../../lib/playback";
import { loadPrefs } from "../../lib/prefs";
import { job } from "../../test/utils";
import { JobPlayer, type PlayerMode } from "./JobPlayer";

const done = job({ status: "done", percent: 100 });
const video = (c: HTMLElement) => c.querySelector("video") as HTMLVideoElement;

beforeEach(() => resetPlayback());
afterEach(() => {
  delete (document as unknown as Record<string, unknown>).pictureInPictureEnabled;
  delete (document as unknown as Record<string, unknown>).pictureInPictureElement;
  document.body.style.overflow = "";
});

describe("lightbox", () => {
  it("es un diálogo modal con el título y botones para reducir y cerrar", () => {
    render(<JobPlayer job={done} name="Mi video" mode="lightbox" onToggleLightbox={vi.fn()} onClose={vi.fn()} />);
    expect(screen.getByRole("dialog", { name: "Reproductor de Mi video" })).toHaveAttribute("aria-modal", "true");
    expect(screen.getByRole("button", { name: "Reducir" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cerrar reproductor" })).toHaveFocus();
  });

  it("Escape y clic en el fondo lo cierran; clic en el video no", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const { container } = render(<JobPlayer job={done} name="x" mode="lightbox" onClose={onClose} />);

    await user.click(video(container));
    expect(onClose).not.toHaveBeenCalled();
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole("dialog")); // el fondo
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it("bloquea el scroll del fondo y lo restaura al salir", () => {
    const { rerender } = render(<JobPlayer job={done} name="x" mode="lightbox" onClose={vi.fn()} />);
    expect(document.body.style.overflow).toBe("hidden");
    rerender(<JobPlayer job={done} name="x" mode="inline" />);
    expect(document.body.style.overflow).toBe("");
  });

  it("atrapa el foco: Shift+Tab en el primer control no escapa al fondo", () => {
    render(<JobPlayer job={done} name="x" mode="lightbox" onToggleLightbox={vi.fn()} onClose={vi.fn()} />);
    screen.getByRole("button", { name: "Reducir" }).focus(); // primer control del diálogo
    const back = new KeyboardEvent("keydown", { key: "Tab", shiftKey: true, cancelable: true, bubbles: true });
    document.dispatchEvent(back);
    expect(back.defaultPrevented).toBe(true); // el componente reubica el foco al final

    screen.getByRole("button", { name: "Cerrar reproductor" }).focus();
    const forward = new KeyboardEvent("keydown", { key: "Tab", cancelable: true, bubbles: true });
    document.dispatchEvent(forward);
    expect(forward.defaultPrevented).toBe(false); // en medio del recorrido, Tab sigue normal
  });

  it("cambiar de modo NO recrea el <video> (no se corta la reproducción)", () => {
    const { container, rerender } = render(<JobPlayer job={done} name="x" mode="inline" />);
    const el = video(container);
    for (const mode of ["lightbox", "hidden", "inline"] as PlayerMode[]) {
      rerender(<JobPlayer job={done} name="x" mode={mode} />);
      expect(video(container)).toBe(el);
    }
  });

  it("modo hidden oculta el reproductor pero lo deja montado", () => {
    const { container } = render(<JobPlayer job={done} name="x" mode="hidden" />);
    expect(container.querySelector(".player")).toHaveAttribute("hidden");
    expect(video(container)).toBeInTheDocument();
  });

  it("en la tarjeta ofrece Ampliar", async () => {
    const user = userEvent.setup();
    const onToggle = vi.fn();
    render(<JobPlayer job={done} name="x" mode="inline" onToggleLightbox={onToggle} />);
    await user.click(screen.getByRole("button", { name: "Ampliar" }));
    expect(onToggle).toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});

describe("Picture-in-Picture", () => {
  const enablePip = (request = vi.fn().mockResolvedValue(undefined), exit = vi.fn().mockResolvedValue(undefined)) => {
    Object.defineProperty(document, "pictureInPictureEnabled", { configurable: true, value: true });
    Object.defineProperty(document, "exitPictureInPicture", { configurable: true, value: exit });
    Object.defineProperty(HTMLVideoElement.prototype, "requestPictureInPicture", { configurable: true, value: request });
    return { request, exit };
  };

  it("sin soporte del navegador no muestra el botón", () => {
    render(<JobPlayer job={done} name="x" />);
    expect(screen.queryByRole("button", { name: "Picture-in-Picture" })).not.toBeInTheDocument();
  });

  it("entra en PiP y avisa cuando empieza y termina", async () => {
    const user = userEvent.setup();
    const { request } = enablePip();
    const onPipChange = vi.fn();
    const { container } = render(<JobPlayer job={done} name="x" onPipChange={onPipChange} />);

    await user.click(screen.getByRole("button", { name: "Picture-in-Picture" }));
    expect(request).toHaveBeenCalled();

    act(() => void video(container).dispatchEvent(new Event("enterpictureinpicture")));
    expect(onPipChange).toHaveBeenLastCalledWith(true);
    expect(screen.getByRole("button", { name: "Picture-in-Picture" })).toHaveAttribute("aria-pressed", "true");

    act(() => void video(container).dispatchEvent(new Event("leavepictureinpicture")));
    expect(onPipChange).toHaveBeenLastCalledWith(false);
  });

  it("si ya está en PiP, el botón sale de PiP", async () => {
    const user = userEvent.setup();
    const { exit, request } = enablePip();
    const { container } = render(<JobPlayer job={done} name="x" />);
    Object.defineProperty(document, "pictureInPictureElement", { configurable: true, value: video(container) });

    await user.click(screen.getByRole("button", { name: "Picture-in-Picture" }));
    expect(exit).toHaveBeenCalled();
    expect(request).not.toHaveBeenCalled();
  });

  it("si el navegador lo rechaza no rompe la UI", async () => {
    const user = userEvent.setup();
    enablePip(vi.fn().mockRejectedValue(new Error("NotAllowedError")));
    render(<JobPlayer job={done} name="x" />);
    await user.click(screen.getByRole("button", { name: "Picture-in-Picture" }));
    expect(screen.getByRole("button", { name: "Picture-in-Picture" })).toHaveAttribute("aria-pressed", "false");
  });
});

describe("preferencias del reproductor", () => {
  it("aplica volumen y velocidad guardados y guarda los cambios", () => {
    localStorage.setItem("hlsweb:player-prefs:v1", JSON.stringify({ volume: 0.3, muted: false, rate: 1.5 }));
    const { container } = render(<JobPlayer job={done} name="x" />);
    const v = video(container);
    Object.defineProperty(v, "duration", { configurable: true, value: 100 });

    fireEvent.loadedMetadata(v);
    expect(v.volume).toBeCloseTo(0.3);
    expect(v.playbackRate).toBe(1.5);

    v.volume = 0.8;
    fireEvent.volumeChange(v);
    v.playbackRate = 2;
    fireEvent.rateChange(v);
    expect(loadPrefs()).toMatchObject({ volume: 0.8, rate: 2 });
  });
});

describe("conteo de vistas", () => {
  it("avisa una sola vez por sesión del reproductor, en la primera reproducción", () => {
    const onPlayStart = vi.fn();
    const { container } = render(<JobPlayer job={done} name="x" onPlayStart={onPlayStart} />);
    const v = video(container);
    expect(onPlayStart).not.toHaveBeenCalled(); // cargar no es ver
    fireEvent.play(v);
    fireEvent.pause(v);
    fireEvent.play(v);
    expect(onPlayStart).toHaveBeenCalledTimes(1);
  });

  it("cambiar de modo no cuenta otra vista (misma sesión)", () => {
    const onPlayStart = vi.fn();
    const { container, rerender } = render(<JobPlayer job={done} name="x" mode="inline" onPlayStart={onPlayStart} />);
    fireEvent.play(video(container));
    rerender(<JobPlayer job={done} name="x" mode="lightbox" onPlayStart={onPlayStart} />);
    fireEvent.play(video(container));
    expect(onPlayStart).toHaveBeenCalledTimes(1);
  });
});
