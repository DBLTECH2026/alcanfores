// Recorrido rápido: abre rutas y guarda capturas de pantalla (viewport) en la carpeta dada.
// node ver.mjs <salida> <ancho> <ruta1> <ruta2> ...   (rutas /admin/* inician sesión como rhuaman)
import { chromium } from 'playwright';
import path from 'node:path';
import { mkdirSync } from 'node:fs';
const [salida, ancho, ...rutas] = process.argv.slice(2);
mkdirSync(salida, { recursive: true });
const BASE = 'http://localhost:5186';
let nav;
try { nav = await chromium.launch(); } catch { nav = await chromium.launch({ channel: 'chrome' }); }
const ctx = await nav.newContext({ viewport: { width: Number(ancho), height: Number(ancho) < 600 ? 844 : 900 }, locale: 'es-PE', timezoneId: 'America/Lima' });
const page = await ctx.newPage();
page.on('pageerror', (e) => console.log('ERROR JS', e.message));
page.on('console', (m) => m.type() === 'error' && console.log('CONSOLA', m.text().slice(0, 200)));
if (rutas.some((r) => r.startsWith('/admin'))) {
  await page.goto(BASE + '/admin/login', { waitUntil: 'networkidle' });
  await page.getByRole('textbox', { name: 'Usuario' }).fill('rhuaman');
  await page.locator('input[type=password]').fill('Alcanfores2026');
  await page.keyboard.press('Enter');
  await page.waitForURL('**/admin/hoy', { timeout: 15000 });
}
let i = 0;
for (const r of rutas) {
  i += 1;
  await page.goto(BASE + r, { waitUntil: 'networkidle' });
  await page.waitForTimeout(900);
  const nombre = `${String(i).padStart(2, '0')}-${r.replace(/[^a-z0-9]+/gi, '_').slice(0, 40)}-${ancho}.png`;
  await page.screenshot({ path: path.join(salida, nombre) });
  console.log('ok', nombre);
}
await nav.close();
