import { z } from "zod";
import { storage } from "./storage";

/** Preferencias del reproductor que valen para todos los videos: volumen y velocidad. */
const KEY = "hlsweb:player-prefs:v1";
const PrefsSchema = z.object({
  volume: z.number().min(0).max(1).default(1),
  muted: z.boolean().default(false),
  rate: z.number().min(0.25).max(4).default(1),
});
export type PlayerPrefs = z.infer<typeof PrefsSchema>;

export function loadPrefs(): PlayerPrefs {
  try {
    const parsed = PrefsSchema.safeParse(JSON.parse(storage.get(KEY) ?? "{}"));
    if (parsed.success) return parsed.data;
  } catch {
    /* JSON inválido */
  }
  return PrefsSchema.parse({});
}

export function savePrefs(patch: Partial<PlayerPrefs>) {
  storage.set(KEY, JSON.stringify({ ...loadPrefs(), ...patch }));
}
