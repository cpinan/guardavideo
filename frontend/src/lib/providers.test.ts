import { describe, expect, it } from "vitest";
import { CATEGORIES, findProvider, PROVIDERS } from "./providers";

describe("findProvider", () => {
  it("reconoce el dominio, sus subdominios y los acortadores", () => {
    expect(findProvider("youtube.com")?.name).toBe("YouTube");
    expect(findProvider("m.youtube.com")?.name).toBe("YouTube");
    expect(findProvider("youtu.be")?.name).toBe("YouTube");
    expect(findProvider("WWW.Dailymotion.com")?.name).toBe("Dailymotion");
  });

  it("no confunde dominios que solo terminan igual", () => {
    expect(findProvider("notyoutube.com")).toBeUndefined();
    expect(findProvider("youtube.com.evil.example")).toBeUndefined();
  });

  it("marca los servicios con cuenta y los protegidos con DRM", () => {
    expect(findProvider("www.instagram.com")?.access).toBe("login");
    expect(findProvider("www.netflix.com")?.access).toBe("drm");
    expect(findProvider("vimeo.com")?.access).toBe("public");
  });
});

describe("PROVIDERS", () => {
  it("cada dominio pertenece a un solo proveedor", () => {
    const domains = PROVIDERS.flatMap((p) => p.domains);
    expect(new Set(domains).size).toBe(domains.length);
  });

  it("cada proveedor público tiene una categoría conocida", () => {
    for (const p of PROVIDERS.filter((x) => x.access === "public")) {
      expect(CATEGORIES.map((c) => c.id)).toContain(p.category);
    }
  });
});
