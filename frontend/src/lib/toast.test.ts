import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { dismissToast, resetToasts, showToast, useToasts } from "./toast";

beforeEach(() => vi.useFakeTimers());
afterEach(() => {
  resetToasts();
  vi.useRealTimers();
});

describe("toast", () => {
  it("aparece y se oculta solo", () => {
    const { result } = renderHook(() => useToasts());
    act(() => void showToast("Hola"));
    expect(result.current.map((t) => t.message)).toEqual(["Hola"]);
    act(() => void vi.advanceTimersByTime(6100));
    expect(result.current).toEqual([]);
  });

  it("máximo 3 a la vez (los más viejos salen)", () => {
    const { result } = renderHook(() => useToasts());
    act(() => ["a", "b", "c", "d"].forEach((m) => showToast(m)));
    expect(result.current.map((t) => t.message)).toEqual(["b", "c", "d"]);
  });

  it("se puede cerrar a mano y conserva la acción", () => {
    const { result } = renderHook(() => useToasts());
    let id = 0;
    act(() => {
      id = showToast("x", { label: "Otra vez", href: "/f" });
    });
    expect(result.current[0]?.action).toEqual({ label: "Otra vez", href: "/f" });
    act(() => dismissToast(id));
    expect(result.current).toEqual([]);
  });
});
