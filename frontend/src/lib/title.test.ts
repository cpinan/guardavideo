import { describe, expect, it } from "vitest";
import { displayTitle } from "./title";

describe("displayTitle", () => {
  it("usa el título si existe", () => expect(displayTitle({ title: "Mi video", url: "https://a.test/x" })).toBe("Mi video"));
  it.each([
    ["https://www.dailymotion.com/video/x8abc", "dailymotion.com · x8abc"],
    ["https://cdn.example.com/a/index.m3u8?t=1", "cdn.example.com · index"],
    ["https://vimeo.com/", "vimeo.com"],
    ["https://a.test/mi%20video.mp4", "a.test · mi video"],
  ])("%s -> %s", (url, text) => expect(displayTitle({ title: null, url })).toBe(text));
  it("URL inválida: la devuelve", () => expect(displayTitle({ title: null, url: "basura" })).toBe("basura"));
});
