// Prueba real: día no laborable con reservas (409), crear y quitar uno libre, y guardar el horario de una cancha.
import { chromium } from 'playwright';
const nav = await chromium.launch({ channel: 'chrome' });
const page = await (await nav.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
const SAL = process.argv[2];
page.on('response', async (r) => /calendario|horario/.test(r.url()) && r.request().method() !== 'GET' && console.log(r.request().method(), new URL(r.url()).pathname + new URL(r.url()).search, r.status(), (await r.text().catch(() => '')).slice(0, 220)));
const B = 'http://localhost:5186';
await page.goto(B + '/admin/login', { waitUntil: 'networkidle' });
await page.getByRole('textbox', { name: 'Usuario' }).fill('rhuaman');
await page.locator('input[type=password]').fill('Alcanfores2026');
await page.keyboard.press('Enter');
await page.waitForURL('**/admin/hoy');
await page.goto(B + '/admin/configuracion/feriados', { waitUntil: 'networkidle' });
const nuevo = async (fecha, motivo) => {
  await page.getByRole('button', { name: 'Día no laborable' }).click();
  await page.waitForTimeout(400);
  const d = page.locator('[role=dialog]').last();
  await d.getByLabel('Fecha').fill(fecha);
  await d.getByLabel('Motivo').fill(motivo);
  await d.getByRole('button', { name: 'Guardar' }).click();
  await page.waitForTimeout(1800);
};
await nuevo('2026-11-21', 'Mantenimiento general');
await page.screenshot({ path: `${SAL}/12-feriados-dia-con-reservas-1440.png` });
await page.keyboard.press('Escape');
await nuevo('2026-12-15', 'Fumigación del complejo');
await page.screenshot({ path: `${SAL}/13-feriados-dia-agregado-1440.png`, fullPage: true });
// quitarlo
await page.locator('.dia-esp', { hasText: 'Fumigación' }).click();
await page.waitForTimeout(400);
await page.locator('[role=dialog]').last().getByRole('button', { name: 'Quitar' }).click();
await page.waitForTimeout(1500);
// horario: Losa multiuso, cerrar el domingo y volver a abrir (dos guardados reales)
await page.goto(B + '/admin/canchas/4/horario', { waitUntil: 'networkidle' });
await page.waitForTimeout(1500);
await page.getByRole('button', { name: 'Ver turnos del Domingo' }).click();
await page.locator('li', { hasText: 'Domingo' }).getByRole('switch').click();
await page.getByRole('button', { name: 'Guardar horario' }).click();
await page.waitForTimeout(2000);
await page.screenshot({ path: `${SAL}/14-horario-domingo-cerrado-1440.png` });
await page.locator('li', { hasText: 'Domingo' }).getByRole('switch').click();
await page.getByRole('button', { name: 'Guardar horario' }).click();
await page.waitForTimeout(2000);
await nav.close();
