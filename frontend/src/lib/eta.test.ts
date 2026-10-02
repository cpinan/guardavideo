import { describe, expect, it } from "vitest";
import { estimateEta, formatEta } from "./eta";

describe("estimateEta", () => {
  it("null con menos de dos muestras", () => {
    expect(estimateEta([], 0)).toBeNull();
    expect(estimateEta([{ t: 0, p: 10 }], 0)).toBeNull();
  });

  it("calcula según el ritmo: 10 % cada 2 s con 50 % hecho = 10 s", () => {
    const samples = [
      { t: 0, p: 40 },
      { t: 2000, p: 50 },
    ];
    expect(estimateEta(samples, 2000)).toBeCloseTo(10);
  });

  it("null si el progreso se detuvo (no inventa un número)", () => {
    const samples = [
      { t: 0, p: 40 },
      { t: 2000, p: 50 },
    ];
    expect(estimateEta(samples, 20_000)).toBeNull();
  });

  it("null si no avanzó", () => {
    expect(estimateEta([{ t: 0, p: 50 }, { t: 5000, p: 50 }], 5000)).toBeNull();
  });

  it("ignora muestras fuera de la ventana", () => {
    const samples = [
      { t: 0, p: 0 },
      { t: 59_000, p: 50 },
      { t: 61_000, p: 60 },
    ];
    expect(estimateEta(samples, 61_000)).toBeCloseTo(8);
  });
});

describe("formatEta", () => {
  it.each([
    [0.2, "~1 s"],
    [42, "~42 s"],
    [125, "~2 min 5 s"],
    [3900, "~1 h 5 min"],
  ])("%d s -> %s", (s, text) => expect(formatEta(s)).toBe(text));
});
