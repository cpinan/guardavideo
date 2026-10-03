#!/usr/bin/env node
// Auditoría de UI en Chrome headless vía DevTools Protocol (sin dependencias).
// Emula teléfono (390/320), tablet (820) y escritorio (1280) en claro y oscuro, abre estados
// (enlace con DRM, sitios compatibles, lightbox, vista previa) y revisa en cada pantalla:
//   - scroll horizontal de la página (scrollWidth > ancho de la ventana)
//   - objetivos táctiles < 44 px en dispositivos táctiles
//   - controles sin nombre accesible e imágenes sin alt
// Guarda capturas + report.json. Sale con código 1 si hay desborde u objetivos pequeños.
//
// Uso (con `make dev-api` y `make dev-web` corriendo):
//   node tools/ui-audit.mjs [url=http://localhost:5173/] [carpeta=ui-audit-out]
//   CHROME=/ruta/a/chrome node tools/ui-audit.mjs
import { spawn } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";

const APP = process.argv[2] ?? "http://localhost:5173/";
const OUT = resolve(process.argv[3] ?? "ui-audit-out");
const PORT = 9333 + Math.floor(Math.random() * 500);
const CHROME =
  process.env.CHROME ??
  ["/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", "/usr/bin/google-chrome", "/usr/bin/chromium"].find(
    existsSync,
  );
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

if (!CHROME) {
  console.error("No encontré Chrome. Define CHROME=/ruta/al/binario.");
  process.exit(2);
}
mkdirSync(OUT, { recursive: true });

const chrome = spawn(
  CHROME,
  ["--headless=new", "--disable-gpu", "--hide-scrollbars", `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${mkdtempSync(join(tmpdir(), "ui-audit-"))}`, "about:blank"],
  { stdio: "ignore" },
);
process.on("exit", () => chrome.kill());

let target;
for (let i = 0; i < 40 && !target; i++) {
  await sleep(250);
  target = await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: "PUT" })
    .then((r) => r.json())
    .catch(() => undefined);
}
if (!target) throw new Error("Chrome no abrió el puerto de depuración");

const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((r) => (ws.onopen = r));
let seq = 0;
const pending = new Map();
ws.onmessage = (m) => {
  const msg = JSON.parse(m.data);
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg);
    pending.delete(msg.id);
  }
};
const send = (method, params = {}) =>
  new Promise((res, rej) => {
    const id = ++seq;
    pending.set(id, (msg) => (msg.error ? rej(new Error(`${method}: ${msg.error.message}`)) : res(msg.result)));
    ws.send(JSON.stringify({ id, method, params }));
  });
const evaluate = async (expr) =>
  (await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true })).result.value;

await send("Page.enable");
await send("Runtime.enable");

async function setup({ width, height, mobile, dark }) {
  await send("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 1, mobile });
  await send("Emulation.setTouchEmulationEnabled", { enabled: mobile, maxTouchPoints: mobile ? 5 : 1 });
  await send("Emulation.setEmulatedMedia", {
    features: [
      { name: "prefers-color-scheme", value: dark ? "dark" : "light" },
      { name: "prefers-reduced-motion", value: "reduce" }, // capturas estables, sin animaciones a medias
    ],
  });
}

/** Carga la app con un localStorage dado (estado de UI: reproductor abierto, etc.). */
async function load(storage = {}) {
  await send("Page.navigate", { url: APP });
  await sleep(1500);
  const sets = Object.entries(storage)
    .map(([k, v]) => `localStorage.setItem(${JSON.stringify(k)}, ${JSON.stringify(v)});`)
    .join("");
  await evaluate(`(() => { localStorage.clear(); ${sets} })()`);
  await send("Page.reload");
  await sleep(3500);
}

const CHECKS = `(() => {
  const vw = document.documentElement.clientWidth;
  const touch = matchMedia("(pointer: coarse)").matches;
  const r = { vw, scrollW: document.documentElement.scrollWidth, overflow: [], small: [], unlabeled: [], noAlt: 0 };
  const name = (el) => (el.getAttribute("aria-label") || el.textContent || el.title || el.tagName).trim().replace(/\\s+/g, " ").slice(0, 40);
  const visible = (el) => { const s = getComputedStyle(el); const b = el.getBoundingClientRect(); return s.visibility !== "hidden" && s.display !== "none" && b.width > 0 && b.height > 0; };
  for (const el of document.querySelectorAll("body *")) {
    if (!visible(el) || el.closest(".shelf-row, .jobs.rail, .filters")) continue; // carruseles: desbordan a propósito
    const b = el.getBoundingClientRect();
    if (b.right > vw + 1) r.overflow.push(el.tagName.toLowerCase() + "." + [...el.classList].join(".") + " → " + Math.round(b.right));
  }
  for (const el of document.querySelectorAll("button, a[href], select, input:not([type=checkbox]), summary, [role=button]")) {
    if (!visible(el) || el.getAttribute("aria-hidden") === "true" || el.closest(".debug-toggle, .debug-panel")) continue;
    const b = el.getBoundingClientRect();
    // las estrellas amplían su área táctil con ::after (inset -8px)
    const extra = getComputedStyle(el, "::after").content !== "none" && el.classList.contains("star") ? 16 : 0;
    if (touch && (b.height + extra < 44 || b.width + extra < 44)) r.small.push(name(el) + " " + Math.round(b.width) + "×" + Math.round(b.height));
    const label = el.getAttribute("aria-label") || el.textContent.trim() || el.title || el.closest("label") || (el.id && document.querySelector('label[for="' + el.id + '"]'));
    if (!label) r.unlabeled.push(el.outerHTML.slice(0, 80));
  }
  r.noAlt = [...document.querySelectorAll("img")].filter((i) => !i.hasAttribute("alt")).length;
  r.overflow = [...new Set(r.overflow)].slice(0, 15);
  r.small = [...new Set(r.small)].slice(0, 30);
  return r;
})()`;

async function shot(name, full) {
  const metrics = await send("Page.getLayoutMetrics");
  const res = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: full,
    clip: full
      ? { x: 0, y: 0, width: Math.ceil(metrics.cssLayoutViewport.clientWidth), height: Math.min(Math.ceil(metrics.cssContentSize.height), 4000), scale: 1 }
      : undefined,
  });
  writeFileSync(join(OUT, `${name}.png`), Buffer.from(res.data, "base64"));
}

const report = {};
async function run(name, fn, full = false) {
  try {
    await fn();
    report[name] = await evaluate(CHECKS);
    await shot(name, full);
  } catch (e) {
    report[name] = { error: String(e) };
  }
}

const PHONE = { width: 390, height: 844, mobile: true };
await setup({ ...PHONE, dark: false });
await load();
const doneId = await evaluate(
  `fetch("/api/v1/jobs").then((r) => r.json()).then((js) => js.filter((j) => j.status === "done").pop()?.id)`,
);
const player = (lightbox) => ({
  "hlsweb:playback:v1": JSON.stringify({ [doneId]: { t: 0, d: 0, open: true, lightbox, watched: false, at: Date.now() } }),
});

await run("phone-light", () => load(), true);
await setup({ ...PHONE, dark: true });
await run("phone-dark", () => load(), true);
await run("phone-drm-url", async () => {
  await load();
  await evaluate(`document.querySelector("input[type=url]").focus()`);
  await send("Input.insertText", { text: "https://www.netflix.com/watch/1" });
  await sleep(400);
});
await run("phone-providers", async () => {
  await load();
  await evaluate(`(() => { const d = document.querySelector("details.providers"); d.open = true; d.scrollIntoView(); })()`);
  await sleep(400);
});
if (doneId) await run("phone-lightbox", () => load(player(true)));
await setup({ width: 320, height: 640, mobile: true, dark: false });
await run("phone320", () => load(), true);
await setup({ width: 820, height: 1180, mobile: true, dark: false });
await run("tablet", () => load(), true);
await setup({ width: 1280, height: 900, mobile: false, dark: false });
await run("desktop-light", () => load(), true);
if (doneId) await run("desktop-inline-player", () => load(player(false)));
await setup({ width: 1280, height: 900, mobile: false, dark: true });
await run("desktop-dark", () => load(), true);

writeFileSync(join(OUT, "report.json"), JSON.stringify(report, null, 2));
ws.close();

let failed = false;
for (const [name, r] of Object.entries(report)) {
  if (r.error) {
    console.log(`✖ ${name}: ${r.error}`);
    failed = true;
    continue;
  }
  const problems = [
    r.scrollW > r.vw && `página de ${r.scrollW}px en ventana de ${r.vw}px (${r.overflow.join(", ") || "sin culpable visible"})`,
    r.small.length && `objetivos táctiles pequeños: ${r.small.join(", ")}`,
    r.unlabeled.length && `${r.unlabeled.length} controles sin nombre`,
    r.noAlt && `${r.noAlt} imágenes sin alt`,
  ].filter(Boolean);
  failed ||= problems.length > 0;
  console.log(`${problems.length ? "✖" : "✔"} ${name}${problems.length ? "\n    " + problems.join("\n    ") : ""}`);
}
console.log(`\nCapturas y report.json en ${OUT}`);
process.exit(failed ? 1 : 0);
