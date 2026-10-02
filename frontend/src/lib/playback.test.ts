import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearPlayback, flushPlayback, getPlayback, resetPlayback, savePlayback } from "./playback";

const KEY = "hlsweb:playback:v1";
const stored = () => JSON.parse(localStorage.getItem(KEY) ?? "{}");

beforeEach(() => {
  vi.useFakeTimers();
  localStorage.clear();
  resetPlayback();
});
afterEach(() => vi.useRealTimers());

describe("playback", () => {
  it("guarda segundo y duración, y los recupera tras 'recargar'", () => {
    savePlayback("a", { t: 83.4, d: 300 });
    vi.advanceTimersByTime(600);
    expect(stored().a).toMatchObject({ t: 83.4, d: 300, watched: false, open: false });

    resetPlayback(); // = recarga: vuelve a leer localStorage
    expect(getPlayback("a")).toMatchObject({ t: 83.4, d: 300 });
  });

  it("escribe con throttle (muchas actualizaciones = una escritura)", () => {
    const set = vi.spyOn(Storage.prototype, "setItem");
    for (let t = 1; t <= 20; t++) savePlayback("a", { t, d: 100 });
    expect(stored().a).toBeUndefined();
    vi.advanceTimersByTime(600);
    expect(stored().a.t).toBe(20);
    set.mockRestore();
  });

  it("flushPlayback escribe ya (pestaña que se cierra)", () => {
    savePlayback("a", { t: 7, d: 10 });
    flushPlayback();
    expect(stored().a.t).toBe(7);
  });

  it("mezcla parches sin perder lo anterior", () => {
    savePlayback("a", { t: 10, d: 100 });
    savePlayback("a", { open: true });
    expect(getPlayback("a")).toMatchObject({ t: 10, d: 100, open: true });
  });

  it("clearPlayback elimina la entrada", () => {
    savePlayback("a", { t: 1, d: 2 });
    clearPlayback("a");
    vi.advanceTimersByTime(600);
    expect(getPlayback("a")).toBeUndefined();
    expect(stored().a).toBeUndefined();
  });

  it("cada entrada es inmutable: otra no cambia su referencia", () => {
    savePlayback("a", { t: 1, d: 2 });
    const a = getPlayback("a");
    savePlayback("b", { t: 5, d: 9 });
    expect(getPlayback("a")).toBe(a);
  });

  it("descarta datos corruptos o fuera de contrato", () => {
    localStorage.setItem(KEY, "{no json");
    resetPlayback();
    expect(getPlayback("a")).toBeUndefined();
    localStorage.setItem(KEY, JSON.stringify({ a: { t: -5, d: "x" } }));
    resetPlayback();
    expect(getPlayback("a")).toBeUndefined();
  });

  it("conserva como máximo 200 videos (los más recientes)", () => {
    for (let i = 0; i < 230; i++) {
      vi.setSystemTime(1_000 + i);
      savePlayback(`v${i}`, { t: 1, d: 2 });
    }
    flushPlayback();
    const keys = Object.keys(stored());
    expect(keys).toHaveLength(200);
    expect(keys).toContain("v229");
    expect(keys).not.toContain("v0");
  });
});
