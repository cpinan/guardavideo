import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { job, renderWithClient } from "../../test/utils";
import { LibraryStats } from "./LibraryStats";

const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status }));
afterEach(() => vi.unstubAllGlobals());

describe("LibraryStats", () => {
  it("resume videos, tamaño y espacio libre", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) =>
        url.endsWith("/stats")
          ? json({ videos: 14, bytes: 1_288_490_189, disk_free_bytes: 80 * 1024 ** 3 })
          : json([job({ status: "done" })]),
      ),
    );
    renderWithClient(<LibraryStats />);
    const stats = await screen.findByLabelText("Resumen de la biblioteca");
    expect(stats).toHaveTextContent("14 videos · 1,2 GB · 80,0 GB libres");
  });

  it("singular con un video", async () => {
    vi.stubGlobal("fetch", vi.fn((url: string) => (url.endsWith("/stats") ? json({ videos: 1, bytes: 2048, disk_free_bytes: 0 }) : json([]))));
    renderWithClient(<LibraryStats />);
    expect(await screen.findByLabelText("Resumen de la biblioteca")).toHaveTextContent("1 video · 2,0 KB");
  });

  it("sin videos o si falla la consulta no muestra nada", async () => {
    vi.stubGlobal("fetch", vi.fn((url: string) => (url.endsWith("/stats") ? json({}, 500) : json([]))));
    renderWithClient(<LibraryStats />);
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.queryByLabelText("Resumen de la biblioteca")).not.toBeInTheDocument();
  });
});
