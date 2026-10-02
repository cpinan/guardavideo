import { describe, expect, it } from "vitest";
import { job } from "../test/utils";
import type { Playback } from "./playback";
import { buildShelves, sortJobs, watchedFraction } from "./shelves";

const NOW = new Date("2026-10-10T12:00:00Z").getTime();
const ago = (days: number) => new Date(NOW - days * 86_400_000).toISOString();
const pb = (t: number, extra: Partial<Playback> = {}): Playback => ({
  t, d: 100, open: false, lightbox: false, watched: false, at: 1, ...extra,
});
const ids = (jobs: { id: string }[]) => jobs.map((j) => j.id);
const done = (id: string, extra = {}) => job({ id, status: "done", percent: 100, duration_seconds: 100, created_at: ago(1), ...extra });

describe("watchedFraction", () => {
  it("proporción vista; 0 si no hay datos, ya lo viste o apenas empezó", () => {
    expect(watchedFraction(done("a"), pb(25))).toBe(0.25);
    expect(watchedFraction(done("a"), pb(25, { watched: true }))).toBe(0);
    expect(watchedFraction(done("a"), pb(0.5))).toBe(0);
    expect(watchedFraction(done("a"), undefined)).toBe(0);
    expect(watchedFraction(done("a", { duration_seconds: null }), pb(10, { d: 0 }))).toBe(0);
  });
  it("usa la duración guardada del reproductor si el servidor no la tiene", () => {
    expect(watchedFraction(done("a", { duration_seconds: null }), pb(50, { d: 200 }))).toBe(0.25);
  });
});

describe("buildShelves", () => {
  it("seguir viendo: a medias, el último visto primero; sin los terminados ni los casi terminados", () => {
    const jobs = [done("a"), done("b"), done("c"), done("d"), done("e")];
    const playback = {
      a: pb(20, { at: 100 }),
      b: pb(40, { at: 300 }),
      c: pb(99.5, { at: 400 }), // casi al final: ya no es "a medias"
      d: pb(50, { watched: true, at: 500 }),
    };
    expect(ids(buildShelves(jobs, playback, { now: NOW }).continueWatching)).toEqual(["b", "a"]);
  });

  it("favoritos: solo terminados y marcados", () => {
    const jobs = [done("a", { starred: true }), done("b"), job({ id: "c", status: "running", starred: true })];
    expect(ids(buildShelves(jobs, {}, { now: NOW }).favorites)).toEqual(["a"]);
  });

  it("más vistos: por conteo, desempata por la vista más reciente, ignora los de 0", () => {
    const jobs = [
      done("a", { view_count: 2, last_viewed_at: ago(5) }),
      done("b", { view_count: 5 }),
      done("c", { view_count: 2, last_viewed_at: ago(1) }),
      done("d"),
    ];
    expect(ids(buildShelves(jobs, {}, { now: NOW }).mostViewed)).toEqual(["b", "c", "a"]);
  });

  it("recién descargados: últimos 7 días, más nuevo primero", () => {
    const jobs = [done("old", { created_at: ago(20) }), done("a", { created_at: ago(5) }), done("b", { created_at: ago(1) })];
    expect(ids(buildShelves(jobs, {}, { now: NOW }).recent)).toEqual(["b", "a"]);
  });

  it("respeta el límite por estante", () => {
    const jobs = Array.from({ length: 12 }, (_, i) => done(`j${i}`, { starred: true }));
    expect(buildShelves(jobs, {}, { limit: 5, now: NOW }).favorites).toHaveLength(5);
  });
});

describe("sortJobs", () => {
  const jobs = [
    done("b", { title: "Beta", size_bytes: 10, view_count: 1 }),
    done("a", { title: "álamo", size_bytes: 300, view_count: 9 }),
    done("c", { title: "Cielo", size_bytes: null, view_count: 4 }),
  ];
  const name = (j: { title: string | null }) => j.title ?? "";
  it("recent no cambia el orden recibido", () => expect(ids(sortJobs(jobs, "recent", name))).toEqual(["b", "a", "c"]));
  it("views: más vistos primero", () => expect(ids(sortJobs(jobs, "views", name))).toEqual(["a", "c", "b"]));
  it("name: alfabético sin distinguir acentos ni mayúsculas", () => expect(ids(sortJobs(jobs, "name", name))).toEqual(["a", "b", "c"]));
  it("size: mayor primero y sin tamaño al final", () => expect(ids(sortJobs(jobs, "size", name))).toEqual(["a", "b", "c"]));
  it("no modifica el arreglo original", () => {
    const copy = ids(jobs);
    sortJobs(jobs, "name", name);
    expect(ids(jobs)).toEqual(copy);
  });
});
