import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { resetToasts, showToast } from "../lib/toast";
import { Toaster } from "./Toaster";
import { ThemeToggle } from "./ThemeToggle";
import { setTheme } from "../lib/theme";

afterEach(() => {
  act(() => resetToasts());
  setTheme("system");
});

describe("Toaster", () => {
  it("muestra el aviso en una región status y permite cerrarlo", async () => {
    const user = userEvent.setup();
    render(<Toaster />);
    act(() => void showToast("Descarga iniciada: Mi video"));
    expect(screen.getByRole("status")).toHaveTextContent("Descarga iniciada: Mi video");
    await user.click(screen.getByRole("button", { name: "Cerrar aviso" }));
    expect(screen.queryByText(/Descarga iniciada/)).not.toBeInTheDocument();
  });

  it("acción como enlace (descargar otra vez) y como botón", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<Toaster />);
    act(() => {
      showToast("a", { label: "Otra vez", href: "/api/v1/jobs/x/file" });
      showToast("b", { label: "Deshacer", onClick });
    });
    expect(screen.getByRole("link", { name: "Otra vez" })).toHaveAttribute("href", "/api/v1/jobs/x/file");
    await user.click(screen.getByRole("button", { name: "Deshacer" }));
    expect(onClick).toHaveBeenCalled();
  });
});

describe("ThemeToggle", () => {
  it("rota el tema y lo anuncia en su nombre accesible", async () => {
    const user = userEvent.setup();
    render(<ThemeToggle />);
    expect(screen.getByRole("button", { name: "Tema: Sistema. Cambiar" })).toBeInTheDocument();
    await user.click(screen.getByRole("button"));
    expect(screen.getByRole("button", { name: "Tema: Claro. Cambiar" })).toBeInTheDocument();
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
    await user.click(screen.getByRole("button"));
    expect(screen.getByRole("button", { name: "Tema: Oscuro. Cambiar" })).toBeInTheDocument();
  });
});
