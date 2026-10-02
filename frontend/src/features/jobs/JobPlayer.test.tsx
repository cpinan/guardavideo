import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPlayback, getPlayback, resetPlayback, savePlayback } from "../../lib/playback";
import { job } from "../../test/utils";
import { JobPlayer } from "./JobPlayer";

const done = job({ status: "done", percent: 100, thumbnail_version: 1 });

/** jsdom no reproduce: simulamos lo que haría el elemento <video>. */
function fakeMedia(video: HTMLVideoElement, { duration = 300, currentTime = 0 } = {}) {
  let time = currentTime;
  let ended = false;
  Object.defineProperty(video, "duration", { configurable: true, get: () => duration });
  Object.defineProperty(video, "ended", { configurable: true, get: () => ended });
  Object.defineProperty(video, "currentTime", { configurable: true, get: () => time, set: (v: number) => (time = v) });
  return { setTime: (t: number) => (time = t), time: () => time, end: () => (ended = true) };
}

const getVideo = () => screen.getByLabelText("Reproductor de abc") as HTMLVideoElement;
const renderPlayer = () => render(<JobPlayer job={done} name="abc" />);

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date", "setTimeout", "clearTimeout"] });
  localStorage.clear();
  resetPlayback();
});
afterEach(() => vi.useRealTimers());

describe("JobPlayer", () => {
  it("usa el stream en línea y la miniatura como póster", () => {
    renderPlayer();
    expect(getVideo()).toHaveAttribute("src", "/api/v1/jobs/abc/stream");
    expect(getVideo()).toHaveAttribute("poster", "/api/v1/jobs/abc/thumbnail?v=1");
    expect(getVideo()).toHaveAttribute("controls");
  });

  it("guarda la duración al cargar los metadatos", () => {
    renderPlayer();
    fakeMedia(getVideo(), { duration: 245 });
    fireEvent.loadedMetadata(getVideo());
    expect(getPlayback("abc")?.d).toBe(245);
  });

  it("guarda el segundo actual mientras se reproduce, como mucho una vez por segundo", () => {
    renderPlayer();
    const media = fakeMedia(getVideo());
    fireEvent.loadedMetadata(getVideo());

    vi.setSystemTime(10_000);
    media.setTime(12.5);
    fireEvent.timeUpdate(getVideo());
    expect(getPlayback("abc")?.t).toBe(12.5);

    vi.setSystemTime(10_300); // < 1 s después: se ignora
    media.setTime(12.8);
    fireEvent.timeUpdate(getVideo());
    expect(getPlayback("abc")?.t).toBe(12.5);

    vi.setSystemTime(11_200);
    media.setTime(13.7);
    fireEvent.timeUpdate(getVideo());
    expect(getPlayback("abc")?.t).toBe(13.7);
  });

  it("al pausar o saltar guarda al instante", () => {
    renderPlayer();
    const media = fakeMedia(getVideo());
    media.setTime(61);
    fireEvent.pause(getVideo());
    expect(getPlayback("abc")?.t).toBe(61);
    media.setTime(120);
    fireEvent.seeked(getVideo());
    expect(getPlayback("abc")?.t).toBe(120);
  });

  it("al 'recargar' reanuda en el segundo guardado y lo avisa", () => {
    savePlayback("abc", { t: 83.4, d: 300 });
    flushPlayback();
    resetPlayback(); // recarga de la página

    renderPlayer();
    const media = fakeMedia(getVideo());
    fireEvent.loadedMetadata(getVideo());

    expect(media.time()).toBe(83.4);
    expect(screen.getByRole("status")).toHaveTextContent("Continúas desde 1:23");
  });

  it("no reanuda si lo dejaste casi al final o ya lo viste", () => {
    savePlayback("abc", { t: 299.5, d: 300 });
    renderPlayer();
    const media = fakeMedia(getVideo());
    fireEvent.loadedMetadata(getVideo());
    expect(media.time()).toBe(0);
  });

  it("al terminar queda como visto y la próxima vez empieza de cero", () => {
    savePlayback("abc", { t: 200, d: 300 });
    const first = renderPlayer();
    fakeMedia(getVideo()).end(); // un <video> real tiene ended=true al terminar
    fireEvent.ended(getVideo());
    expect(getPlayback("abc")).toMatchObject({ t: 0, watched: true });
    first.unmount(); // cerrar no debe pisar el "visto"
    expect(getPlayback("abc")).toMatchObject({ t: 0, watched: true });

    renderPlayer();
    const media = fakeMedia(getVideo());
    fireEvent.loadedMetadata(getVideo());
    expect(media.time()).toBe(0);
  });

  it("al cerrar el reproductor conserva el último segundo", () => {
    const { unmount } = renderPlayer();
    fakeMedia(getVideo()).setTime(45);
    unmount();
    expect(getPlayback("abc")?.t).toBe(45);
  });

  it("un formato que el navegador no soporta muestra un aviso útil", () => {
    renderPlayer();
    fireEvent.error(getVideo());
    expect(screen.getByRole("alert")).toHaveTextContent("no puede reproducir este formato");
  });
});
