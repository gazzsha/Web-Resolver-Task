// Screenshot generator for thesis Appendix B (Grafana dashboards).
// Uses Playwright (bundled with frontend devDeps).
// Drives Grafana provisioned dashboards exposed on localhost:3000.
//
// Run: cd frontend && node grafana-screenshots.mjs

import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.resolve(__dirname, '../thesis/figures/screenshots');
const BASE_URL = process.env.GRAFANA_URL ?? 'http://localhost:3000';

const DASHBOARDS = [
  { uid: 'web-resolver-jvm',      file: '11_grafana_jvm.png' },
  { uid: 'web-resolver-kafka',    file: '12_grafana_kafka.png' },
  { uid: 'web-resolver-postgres', file: '13_grafana_postgres.png' },
  { uid: 'web-resolver-sandbox',  file: '14_grafana_sandbox.png' },
  { uid: 'web-resolver-ai',       file: '15_grafana_ai.png' },
];

await mkdir(OUT_DIR, { recursive: true });

const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({
  viewport: { width: 1280, height: 900 },
  deviceScaleFactor: 2,
});
const page = await ctx.newPage();

for (const { uid, file } of DASHBOARDS) {
  const url = `${BASE_URL}/d/${uid}?refresh=30s&from=now-1h&to=now&kiosk=tv`;
  console.log(`→ ${url}`);
  await page.goto(url, { waitUntil: 'networkidle', timeout: 30_000 }).catch(() => {});
  // Allow panels to render data (queries + animations).
  await page.waitForTimeout(4_000);
  const out = path.join(OUT_DIR, file);
  await page.screenshot({ path: out, fullPage: false });
  console.log(`  saved ${out}`);
}

await browser.close();
console.log('done');
