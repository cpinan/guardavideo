import { describe, expect, it } from "vitest";
import { job } from "../test/utils";
import { dayLabel, groupByDay } from "./group";

const now = new Date(2026, 9, 2, 15, 0); // 2 oct 2026, local
const at = (y: number, m: number, d: number, h = 12) => new Date(y, m, d, h).toISOString();

describe("dayLabel", () => {
  it("hoy, ayer, otra fecha y sin fecha", () => {
    expect(dayLabel(at(2026, 9, 2, 1), now)).toBe("Hoy");
    expect(dayLabel(at(2026, 9, 1, 23), now)).toBe("Ayer");
    expect(dayLabel(at(2026, 8, 20), now)).toMatch(/^Domingo|^Sábado|^Lunes|^Martes|^Miércoles|^Jueves|^Viernes/);
    expect(dayLabel(null, now)).toBe("Anteriores");
    expect(dayLabel("nope", now)).toBe("Anteriores");
  });
});

describe("groupByDay", () => {
  it("agrupa consecutivos del mismo día y conserva el orden", () => {
    const jobs = [
      job({ id: "1", created_at: at(2026, 9, 2, 14) }),
      job({ id: "2", created_at: at(2026, 9, 2, 9) }),
      job({ id: "3", created_at: at(2026, 9, 1, 20) }),
      job({ id: "4", created_at: null }),
    ];
    const groups = groupByDay(jobs, now);
    expect(groups.map((g) => [g.label, g.jobs.map((j) => j.id)])).toEqual([
      ["Hoy", ["1", "2"]],
      ["Ayer", ["3"]],
      ["Anteriores", ["4"]],
    ]);
  });
});
