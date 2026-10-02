import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, cancelJob, createJob, deleteJob, eventsUrl, fileUrl, getHealth, listJobs, recordView, retryJob, setStarred, thumbnailUrl } from "./api";
import { JobSchema } from "./schemas";
import { job } from "../test/utils";

const reply = (status: number, body: unknown) =>
  vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response(JSON.stringify(body), { status }))));

afterEach(() => vi.unstubAllGlobals());

describe("api", () => {
  it("createJob envía JSON y valida la respuesta con zod", async () => {
    reply(201, job({ status: "queued" }));
    const created = await createJob("https://example.com/v.mp4", 720);

    expect(created.id).toBe("abc");
    const [url, init] = vi.mocked(fetch).mock.calls[0] ?? [];
    expect(url).toBe("/api/v1/jobs");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init?.body as string)).toEqual({ url: "https://example.com/v.mp4", quality: 720 });
  });

  it("createJob solo manda force cuando es true", async () => {
    reply(201, job());
    await createJob("https://example.com/v.mp4", undefined, true);
    expect(JSON.parse(vi.mocked(fetch).mock.calls[0]?.[1]?.body as string).force).toBe(true);
    await createJob("https://example.com/v.mp4");
    expect(JSON.parse(vi.mocked(fetch).mock.calls[1]?.[1]?.body as string)).not.toHaveProperty("force");
  });

  it("deleteJob usa DELETE /record y acepta 204 sin cuerpo", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(deleteJob("abc")).resolves.toBeUndefined();
    const [url, init] = vi.mocked(fetch).mock.calls[0] ?? [];
    expect(url).toBe("/api/v1/jobs/abc/record");
    expect(init?.method).toBe("DELETE");
  });

  it("deleteJob propaga el 409 si el job sigue activo", async () => {
    reply(409, { detail: "Cancela la descarga antes de eliminarla" });
    await expect(deleteJob("abc")).rejects.toMatchObject({ status: 409 });
  });

  it("createJob sin calidad manda null", async () => {
    reply(201, job());
    await createJob("https://example.com/v.mp4");
    expect(JSON.parse(vi.mocked(fetch).mock.calls[0]?.[1]?.body as string).quality).toBeNull();
  });

  it("usa el detail del backend en los errores", async () => {
    reply(400, { detail: "La URL apunta a una red no permitida" });
    await expect(createJob("http://127.0.0.1")).rejects.toMatchObject({
      status: 400,
      message: "La URL apunta a una red no permitida",
    });
  });

  it.each([
    [422, /no son válidas/],
    [404, /No se encontró/],
    [500, /inesperado/],
  ])("mensaje amigable para %i sin detail", async (status, message) => {
    reply(status, {});
    const err = await listJobs().catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).message).toMatch(message);
  });

  it("rechaza respuestas que rompen el contrato", async () => {
    reply(200, [{ id: "x" }]);
    await expect(listJobs()).rejects.toThrow();
  });

  it("cancelJob usa DELETE", async () => {
    reply(200, job({ status: "cancelled" }));
    await cancelJob("abc");
    expect(vi.mocked(fetch).mock.calls[0]?.[1]?.method).toBe("DELETE");
  });

  it("setStarred usa PATCH con el cuerpo { starred }", async () => {
    reply(200, job({ starred: true }));
    const updated = await setStarred("abc", true);
    expect(updated.starred).toBe(true);
    const [url, init] = vi.mocked(fetch).mock.calls[0] ?? [];
    expect(url).toBe("/api/v1/jobs/abc");
    expect(init?.method).toBe("PATCH");
    expect(JSON.parse(init?.body as string)).toEqual({ starred: true });
  });

  it("recordView usa POST /view", async () => {
    reply(200, job({ view_count: 3 }));
    await expect(recordView("abc")).resolves.toMatchObject({ view_count: 3 });
    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe("/api/v1/jobs/abc/view");
  });

  it("retryJob usa POST /retry", async () => {
    reply(202, job({ status: "queued" }));
    await retryJob("abc");
    const [url, init] = vi.mocked(fetch).mock.calls[0] ?? [];
    expect(url).toBe("/api/v1/jobs/abc/retry");
    expect(init?.method).toBe("POST");
  });

  it("getHealth lee /health y asume debug=false si falta", async () => {
    reply(200, { status: "ok" });
    await expect(getHealth()).resolves.toEqual({ status: "ok", debug: false });
    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe("/health");
  });

  it("construye las URLs de archivo, eventos y miniatura (con versión)", () => {
    expect(fileUrl(job())).toBe("/api/v1/jobs/abc/file");
    expect(eventsUrl("abc")).toBe("/api/v1/jobs/abc/events");
    expect(thumbnailUrl(job({ thumbnail_version: 3 }))).toBe("/api/v1/jobs/abc/thumbnail?v=3");
  });

  it("un fallo de red lanza ApiError con status 0", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    await expect(listJobs()).rejects.toMatchObject({ status: 0, message: "Sin conexión con el servidor" });
  });
});

describe("JobSchema", () => {
  it("rechaza un estado desconocido", () => {
    expect(JobSchema.safeParse({ ...job(), status: "exploded" }).success).toBe(false);
  });
});
