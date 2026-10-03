import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PROVIDERS } from "../../lib/providers";
import { ProvidersList } from "./ProvidersList";

describe("ProvidersList", () => {
  it("empieza plegada y muestra cuántos sitios son compatibles", () => {
    render(<ProvidersList />);
    const count = PROVIDERS.filter((p) => p.access === "public").length;
    expect(screen.getByText(`Sitios compatibles (${count})`)).toBeInTheDocument();
    expect(screen.getByText("YouTube")).not.toBeVisible();
  });

  it("al abrirla lista los sitios por categoría, los que piden cuenta y los que usan DRM", async () => {
    const user = userEvent.setup();
    render(<ProvidersList />);
    await user.click(screen.getByText(/^Sitios compatibles/));

    const video = screen.getByRole("list", { name: "Plataformas de video" });
    expect(within(video).getByText("YouTube")).toBeVisible();
    expect(within(screen.getByRole("list", { name: "Requieren cuenta (aún no soportado)" })).getByText("Instagram")).toBeVisible();
    expect(within(screen.getByRole("list", { name: "No compatibles (DRM)" })).getByText("Netflix")).toBeVisible();
  });
});
