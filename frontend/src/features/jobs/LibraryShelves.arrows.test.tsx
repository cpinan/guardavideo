import { fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetPlayback } from "../../lib/playback";
import { job, renderWithClient } from "../../test/utils";
import { LibraryShelves } from "./LibraryShelves";

const fav = (id: string) => job({ id, title: `Video ${id}`, status: "done", percent: 100, starred: true });
const JOBS = [fav("a"), fav("b"), fav("c")];

/** jsdom no tiene layout: simulamos una fila de 1000 px visible en 300 px. */
function fakeOverflow(scrollWidth: number) {
  const proto = HTMLElement.prototype;
  const row = (el: HTMLElement) => el.classList.contains("shelf-row");
  vi.spyOn(proto, "scrollWidth", "get").mockImplementation(function (this: HTMLElement) {
    return row(this) ? scrollWidth : 0;
  });
  vi.spyOn(proto, "clientWidth", "get").mockImplementation(function (this: HTMLElement) {
    return row(this) ? 300 : 0;
  });
}

beforeEach(() => {
  resetPlayback();
  HTMLElement.prototype.scrollBy = vi.fn();
});
afterEach(() => vi.restoreAllMocks());

describe("LibraryShelves: flechas para desplazar con el ratón", () => {
  it("si la fila desborda, 'Ver más' desplaza casi una pantalla y 'anteriores' empieza deshabilitado", async () => {
    fakeOverflow(1000);
    const user = userEvent.setup();
    renderWithClient(<LibraryShelves jobs={JOBS} />);

    const prev = screen.getByRole("button", { name: "Ver anteriores en Favoritos" });
    const next = screen.getByRole("button", { name: "Ver más en Favoritos" });
    expect(prev).toBeDisabled();
    expect(next).toBeEnabled();

    await user.click(next);
    expect(HTMLElement.prototype.scrollBy).toHaveBeenCalledWith(expect.objectContaining({ left: 270 }));
  });

  it("al llegar al final se deshabilita 'Ver más' y se habilita 'anteriores'", () => {
    fakeOverflow(1000);
    renderWithClient(<LibraryShelves jobs={JOBS} />);
    const list = screen.getByRole("list", { name: "Favoritos" });

    list.scrollLeft = 700;
    fireEvent.scroll(list);

    expect(screen.getByRole("button", { name: "Ver más en Favoritos" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Ver anteriores en Favoritos" })).toBeEnabled();
  });

  it("sin desborde no muestra flechas", () => {
    fakeOverflow(300);
    renderWithClient(<LibraryShelves jobs={JOBS} />);
    expect(screen.queryByRole("button", { name: "Ver más en Favoritos" })).not.toBeInTheDocument();
  });
});
