import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ErrorBoundary } from "./ErrorBoundary";

afterEach(() => vi.restoreAllMocks());

function Bomb({ explode }: { explode: boolean }) {
  if (explode) throw new Error("boom");
  return <p>todo bien</p>;
}

function Harness() {
  const [explode, setExplode] = useState(true);
  return (
    <>
      <button type="button" onClick={() => setExplode(false)}>
        arreglar
      </button>
      <ErrorBoundary>
        <Bomb explode={explode} />
      </ErrorBoundary>
    </>
  );
}

describe("ErrorBoundary", () => {
  it("muestra un mensaje recuperable en vez de pantalla en blanco", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const user = userEvent.setup();
    render(<Harness />);

    expect(screen.getByRole("alert")).toHaveTextContent("Algo salió mal");
    await user.click(screen.getByRole("button", { name: "arreglar" }));
    await user.click(screen.getByRole("button", { name: "Reintentar" }));
    expect(screen.getByText("todo bien")).toBeInTheDocument();
  });
});
