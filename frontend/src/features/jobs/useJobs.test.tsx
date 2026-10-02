import { describe, expect, it } from "vitest";
import type { Job } from "../../lib/schemas";
import { job, makeClient } from "../../test/utils";
import { JOBS_KEY, replaceJob } from "./useJobs";

describe("replaceJob", () => {
  it("reemplaza solo el job cambiado y conserva la referencia de los demás", () => {
    const qc = makeClient();
    const a = job({ id: "a" });
    const b = job({ id: "b" });
    qc.setQueryData(JOBS_KEY, [a, b]);

    const next = job({ id: "b", percent: 90 });
    replaceJob(qc, next);

    const [first, second] = qc.getQueryData<Job[]>(JOBS_KEY) ?? [];
    expect(first).toBe(a);
    expect(second).toEqual(next);
  });
});
