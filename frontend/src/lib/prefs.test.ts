import { describe, expect, it } from "vitest";
import { loadPrefs, savePrefs } from "./prefs";

describe("player prefs", () => {
  it("valores por defecto", () => expect(loadPrefs()).toEqual({ volume: 1, muted: false, rate: 1 }));

  it("guarda y mezcla parches", () => {
    savePrefs({ volume: 0.4 });
    savePrefs({ rate: 1.5 });
    expect(loadPrefs()).toEqual({ volume: 0.4, muted: false, rate: 1.5 });
  });

  it("ignora datos corruptos o fuera de rango", () => {
    localStorage.setItem("hlsweb:player-prefs:v1", "{no json");
    expect(loadPrefs().volume).toBe(1);
    localStorage.setItem("hlsweb:player-prefs:v1", JSON.stringify({ volume: 9, rate: 0 }));
    expect(loadPrefs()).toEqual({ volume: 1, muted: false, rate: 1 });
  });
});
