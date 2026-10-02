import { describe, expect, it } from "vitest";
import { friendlyError } from "./errors";

describe("friendlyError", () => {
  it.each([
    ["ERROR: [youtube] abc: Private video. Sign in if you've been granted access", /privado/],
    ["Sign in to confirm you're not a bot", /iniciar sesión/],
    ["The uploader has not made this video available in your country", /tu país/],
    ["ERROR: Video unavailable", /ya no existe/],
    ["ERROR: Unsupported URL: https://x.test", /No se encontró un video/],
    ["HTTP Error 403: Forbidden", /acceso denegado/],
    ["HTTP Error 429: Too Many Requests", /limitando/],
    ["Read timed out.", /conexión/],
    ["ffmpeg falló: Invalid data", /ffmpeg/],
    ["Interrumpido: el servidor se reinició mientras descargaba", /servidor se reinició/],
    ["El archivo ya no existe en disco", /ya no está en el disco/],
  ])("%s", (raw, expected) => {
    const f = friendlyError(raw);
    expect(f.message).toMatch(expected);
    expect(f.details).toBe(raw); // el original queda disponible en "Ver detalles"
  });

  it("un error desconocido se muestra tal cual, sin inventar una causa", () => {
    expect(friendlyError("algo raro pasó")).toEqual({ message: "algo raro pasó", details: null });
  });
});
