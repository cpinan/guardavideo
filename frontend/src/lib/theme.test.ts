import { afterEach, describe, expect, it } from "vitest";
import { applyTheme, cycleTheme, setTheme } from "./theme";

afterEach(() => {
  setTheme("system");
  localStorage.clear();
});

describe("theme", () => {
  it("system no fuerza nada; light/dark ponen data-theme", () => {
    setTheme("dark");
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    setTheme("system");
    expect(document.documentElement.hasAttribute("data-theme")).toBe(false);
  });

  it("recuerda la elección", () => {
    setTheme("light");
    expect(localStorage.getItem("hlsweb:theme:v1")).toBe("light");
    setTheme("system");
    expect(localStorage.getItem("hlsweb:theme:v1")).toBeNull();
  });

  it("cycleTheme rota sistema → claro → oscuro → sistema", () => {
    cycleTheme();
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
    cycleTheme();
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    cycleTheme();
    expect(document.documentElement.hasAttribute("data-theme")).toBe(false);
  });

  it("applyTheme aplica el guardado al arrancar", () => {
    setTheme("dark");
    document.documentElement.removeAttribute("data-theme");
    applyTheme();
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
  });
});
