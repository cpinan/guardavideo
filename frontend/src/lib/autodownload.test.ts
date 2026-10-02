import { afterEach, describe, expect, it, vi } from "vitest";
import { autoDownload, triggerDownload } from "./autodownload";

afterEach(() => {
  localStorage.clear();
  autoDownload.reload();
  vi.restoreAllMocks();
});

describe("autoDownload", () => {
  it("recuerda los pedidos entre recargas", () => {
    autoDownload.add("a");
    autoDownload.add("a"); // idempotente
    expect(JSON.parse(localStorage.getItem("hlsweb:auto:v1")!)).toEqual(["a"]);
    autoDownload.reload();
    expect(autoDownload.has("a")).toBe(true);
    autoDownload.remove("a");
    expect(autoDownload.has("a")).toBe(false);
    expect(JSON.parse(localStorage.getItem("hlsweb:auto:v1")!)).toEqual([]);
  });

  it("tolera datos corruptos", () => {
    localStorage.setItem("hlsweb:auto:v1", "{no es json");
    autoDownload.reload();
    expect(autoDownload.has("a")).toBe(false);
    localStorage.setItem("hlsweb:auto:v1", JSON.stringify([1, "ok", null]));
    autoDownload.reload();
    expect(autoDownload.has("ok")).toBe(true);
  });
});

describe("triggerDownload", () => {
  it("crea un enlace temporal con download, lo pulsa y lo limpia", () => {
    const seen: { href: string | null; download: string | null }[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
      seen.push({ href: this.getAttribute("href"), download: this.getAttribute("download") });
    });
    triggerDownload("/api/v1/jobs/x/file");
    expect(seen).toEqual([{ href: "/api/v1/jobs/x/file", download: "" }]);
    expect(document.querySelector("a[href='/api/v1/jobs/x/file']")).toBeNull();
  });
});
