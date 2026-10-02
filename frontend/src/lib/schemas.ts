import { z } from "zod";

/** El contrato se valida en runtime: si el backend cambia, falla de forma clara y no con "undefined". */
export const StatusSchema = z.enum(["queued", "running", "done", "error", "cancelled"]);
export type Status = z.infer<typeof StatusSchema>;

export const JobSchema = z.object({
  id: z.string(),
  url: z.string(),
  status: StatusSchema,
  percent: z.number(),
  title: z.string().nullable(),
  error: z.string().nullable(),
  // default: toleran caché local y backends anteriores a estos campos
  retries: z.number().default(0),
  thumbnail_version: z.number().default(0),
  created_at: z.string().nullable().default(null),
  size_bytes: z.number().nullable().default(null),
});
export type Job = z.infer<typeof JobSchema>;

export const HealthSchema = z.object({ status: z.string(), debug: z.boolean().default(false) });

export const isActive = (job: Job) => job.status === "queued" || job.status === "running";
export const isRetryable = (job: Job) => job.status === "error" || job.status === "cancelled";
