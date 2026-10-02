import type { Job } from "./schemas";

/** Nombre legible mientras aún no hay título: "dailymotion.com · x8abc" en vez de la URL cruda. */
export function displayTitle(job: Pick<Job, "title" | "url">): string {
  if (job.title) return job.title;
  try {
    const { hostname, pathname } = new URL(job.url);
    const host = hostname.replace(/^www\./, "");
    const tail = pathname.split("/").filter(Boolean).pop();
    const clean = tail ? decodeURIComponent(tail).replace(/\.(m3u8|mp4|webm|mkv)$/i, "") : "";
    return clean ? `${host} · ${clean}` : host;
  } catch {
    return job.url;
  }
}
