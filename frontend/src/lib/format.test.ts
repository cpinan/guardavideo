import { describe, expect, it } from "vitest";
import { formatBytes, formatWhen } from "./format";

describe("formatBytes", () => {
  it.each([
    [0, "0 B"],
    [999, "999 B"],
    [1024, "1,0 KB"],
    [311_362, "304 KB"],
    [5 * 1024 * 1024, "5,0 MB"],
    [1536 * 1024 * 1024, "1,5 GB"],
  ])("%d -> %s", (n, text) => expect(formatBytes(n)).toBe(text));
});

describe("formatWhen", () => {
  const now = new Date("2026-10-02T12:00:00Z").getTime();
  const ago = (ms: number) => new Date(now - ms).toISOString();

  it("reciente", () => expect(formatWhen(ago(10_000), now)).toBe("ahora"));
  it("minutos", () => expect(formatWhen(ago(5 * 60_000), now)).toBe("hace 5 minutos"));
  it("horas", () => expect(formatWhen(ago(3 * 3_600_000), now)).toBe("hace 3 horas"));
  it("ayer", () => expect(formatWhen(ago(86_400_000), now)).toBe("ayer"));
  it("antiguo: fecha corta", () => expect(formatWhen(ago(30 * 86_400_000), now)).toMatch(/2026/));
  it("fecha inválida: vacío", () => expect(formatWhen("nope", now)).toBe(""));
});
