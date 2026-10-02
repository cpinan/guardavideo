import { describe, expect, it } from "vitest";
import { detectSource, hostOf, isValidUrl } from "./url";

describe("isValidUrl", () => {
  it.each(["https://youtu.be/abc", "http://localhost:8000/x.m3u8"])("acepta %s", (u) => {
    expect(isValidUrl(u)).toBe(true);
  });
  it.each(["", "youtube.com", "ftp://x.com/a", "javascript:alert(1)"])("rechaza %j", (u) => {
    expect(isValidUrl(u)).toBe(false);
  });
});

describe("detectSource", () => {
  it("distingue HLS directo, sitios web y escenarios de depuración", () => {
    expect(detectSource("https://cdn.example/a/index.m3u8?t=1")).toMatchObject({ kind: "hls" });
    expect(detectSource("https://www.youtube.com/watch?v=1")).toEqual({ kind: "site", label: "Sitio web · youtube.com" });
    expect(detectSource("http://debug.test/flaky")).toMatchObject({ kind: "debug" });
  });
  it("null si la URL no es válida", () => {
    expect(detectSource("nope")).toBeNull();
  });
  it("hostOf quita www y tolera basura", () => {
    expect(hostOf("https://www.vimeo.com/1")).toBe("vimeo.com");
    expect(hostOf("basura")).toBe("basura");
  });
});
