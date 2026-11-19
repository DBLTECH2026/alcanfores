// Capturas REALES de la v2 (SPEC_V2 §F) contra el back que esté corriendo detrás de Vite (puerto 5186).
// Uso: npm run dev (otra terminal)  →  node scripts/capturas-v2.mjs [panel|portal|todo] [DNI de la cuenta de prueba]
// Salida: ../docs/capturas-reales/v2/NN-descripcion-1440.png (y -390.png en el portal).
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const AQUI = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.env.BASE || 'http://localhost:5186';
const SALIDA = path.resolve(AQUI, '..', '..', 'docs', 'capturas-reales', 'v2');
const QUE = process.argv[2] || 'todo';
const DNI = process.argv[3] || '99010003';
const NOMBRES = process.env.NOMBRES || 'Carlos';
const APELLIDOS = process.env.APELLIDOS || 'Mendoza Huamán';
const IMAGEN = path.join(AQUI, 'comprobante-prueba.png');
const CLAVE = 'Cancha2026';
mkdirSync(SALIDA, { recursive: true });

let nav;
try {
  nav = await chromium.launch();
} catch {
  nav = await chromium.launch({ channel: 'chrome' });
}
const problemas = [];
const pausa = (ms) => new Promise((r) => setTimeout(r, ms));

async function sesion(ancho) {
  const ctx = await nav.newContext({ viewport: { width: ancho, height: ancho < 600 ? 844 : 900 }, locale: 'es-PE', timezoneId: 'America/Lima' });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => problemas.push(`[${ancho}] error JS: ${e.message}`));
  page.on('response', async (r) => {
    if (r.request().method() !== 'GET' && /\/api\/(admin\/reservas|reservas)$/.test(new URL(r.url()).pathname)) console.log('   POST', new URL(r.url()).pathname, r.status(), (await r.text().catch(() => '')).slice(0, 160));
  });
  page.on('console', (m) => m.type() === 'error' && !/Failed to load resource|maps|gstatic/i.test(m.text()) && problemas.push(`[${ancho}] consola: ${m.text().slice(0, 160)}`));
  const foto = async (nombre, { completa = true } = {}) => {
    await pausa(500);
    const estilo = completa ? await page.addStyleTag({ content: '.mc-header,.ma-topbar,.barra-fija,[class*="lg:sticky"],.ma-sidebar-in,.horario-lado,.editor-panel{position:static!important}' }) : null;
    if (completa) await page.evaluate(() => window.scrollTo(0, 0));
    await pausa(150);
    const desborde = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    if (desborde > 1) problemas.push(`[${ancho}] ${nombre}: desborde horizontal de ${desborde}px`);
    await page.screenshot({ path: path.join(SALIDA, `${nombre}-${ancho}.png`), fullPage: completa });
    if (estilo) await estilo.evaluate((n) => n.remove());
    console.log('  ✓', `${nombre}-${ancho}.png`);
  };
  const ir = async (ruta) => {
    await page.goto(BASE + ruta, { waitUntil: 'networkidle' });
    await pausa(700);
  };
  return { ctx, page, foto, ir };
}

async function entrarPanel(page) {
  await page.goto(BASE + '/admin/login', { waitUntil: 'networkidle' });
  await page.getByRole('textbox', { name: 'Usuario' }).fill('rhuaman');
  await page.locator('input[type=password]').fill('Alcanfores2026');
  await page.keyboard.press('Enter');
  await page.waitForURL('**/admin/hoy', { timeout: 20000 });
  await pausa(800);
}
const dialogo = (page) => page.locator('[role=dialog]').last();

// ------------------------------ PANEL ------------------------------
async function panel() {
  const { page, foto, ir, ctx } = await sesion(1440);
  await entrarPanel(page);
  console.log('Panel');
  await pausa(4500); // que se vaya el toast de bienvenida
  await foto('01-barra-lateral-abierta', { completa: false });
  await page.getByRole('button', { name: 'Plegar menú' }).click();
  await pausa(400);
  const caja = await page.locator('.ma-item').nth(2).boundingBox();
  if (caja) await page.mouse.move(caja.x + caja.width / 2, caja.y + caja.height / 2);
  await foto('02-barra-lateral-plegada', { completa: false });
  await page.getByRole('button', { name: 'Desplegar menú' }).click();

  // 4 · campana de avisos
  await page.locator('button.mt-avisos').click();
  await page.waitForSelector('.avisos-lista, .vacio', { timeout: 15000 });
  await foto('03-modal-avisos-comprobantes', { completa: false });
  await page.keyboard.press('Escape');

  // 2 · configuración
  await ir('/admin/configuracion');
  await foto('04-configuracion-tarjetas');
  for (const [n, id] of [['05', 'complejo'], ['06', 'contacto'], ['07', 'reglas'], ['08', 'comprobantes'], ['09', 'whatsapp']]) {
    await ir(`/admin/configuracion/${id}`);
    await foto(`${n}-configuracion-${id}`);
  }
  await ir('/admin/configuracion/feriados');
  await foto('10-feriados-calendario');
  await page.getByRole('button', { name: 'Día no laborable' }).click();
  await pausa(400);
  await dialogo(page).getByLabel('Motivo').fill('Mantenimiento general');
  await foto('11-feriados-modal-no-laborable', { completa: false });
  await page.keyboard.press('Escape');

  // 3 · canchas
  await ir('/admin/canchas');
  await foto('12-canchas-tarjetas');
  await ir('/admin/canchas/1');
  await page.locator('[data-edit="nombre"]').click();
  await page.getByRole('textbox', { name: 'Resumen' }).fill('Cancha techada de fútbol 5 con grass sintético e iluminación LED. Ideal para la pichanga con amigos.');
  await foto('13-editor-visual-panel-nombre', { completa: false });
  await page.getByRole('button', { name: 'Descartar' }).click();
  await page.locator('[data-edit="galeria"]').click();
  await foto('14-editor-visual-panel-fotos', { completa: false });
  await page.locator('[data-edit="resenas"]').click();
  await pausa(500);
  await foto('15-editor-visual-resenas', { completa: false });
  await page.getByRole('button', { name: 'Agregar reseña del complejo' }).click();
  await page.getByRole('textbox', { name: 'Autor' }).fill('Club Los Halcones');
  await page.locator('.resena-form [role=radio]').nth(4).click().catch(() => undefined);
  await page.locator('.resena-form textarea').fill('Jugamos el campeonato interno aquí: grass parejo y buena luz de noche.');
  await foto('16-editor-visual-resena-nueva', { completa: false });
  await page.locator('.resena-form').getByRole('button', { name: 'Agregar' }).click();
  await pausa(1200);
  await foto('17-editor-visual-resena-agregada', { completa: false });
  await ir('/admin/canchas/1/horario');
  await foto('18-horario-y-vista-previa');
  await page.getByRole('radio', { name: '2 h' }).click();
  await pausa(600);
  await foto('19-horario-turnos-2h-aviso');
  await ir('/admin/canchas/1/configuracion');
  await foto('20-cancha-configuracion');

  // 5 · agenda: bloquear rango
  await ir('/admin/agenda?fecha=' + manana());
  await foto('21-agenda');
  await page.getByRole('button', { name: 'Bloquear horas' }).click();
  await pausa(400);
  const d = dialogo(page);
  await d.getByLabel('Desde').selectOption('10:00');
  await d.getByLabel('Hasta').selectOption('17:00');
  await d.getByLabel('Motivo').fill('Mantenimiento del grass');
  await foto('22-agenda-modal-bloquear-rango', { completa: false });
  await d.getByRole('button', { name: 'Bloquear', exact: true }).click();
  await pausa(2500);
  await page.keyboard.press('Escape');
  await foto('23-agenda-con-bloqueo-de-rango');
  // desbloquear para dejar la agenda como estaba
  await page.getByRole('button', { name: 'Bloquear horas' }).click();
  await pausa(300);
  await dialogo(page).getByRole('radio', { name: 'Desbloquear' }).click();
  await dialogo(page).getByLabel('Desde').selectOption('10:00');
  await dialogo(page).getByLabel('Hasta').selectOption('17:00');
  await foto('24-agenda-modal-desbloquear', { completa: false });
  await dialogo(page).getByRole('button', { name: 'Desbloquear', exact: true }).click();
  await pausa(2000);

  // 6 · reserva manual de varios turnos
  await ir('/admin/nueva-reserva');
  await page.getByRole('button', { name: 'Cambiar' }).click();
  await pausa(400);
  await foto('25-nueva-reserva-selector-canchas', { completa: false });
  await page.keyboard.press('Escape');
  await page.locator('.tira-dias button').nth(2).click();
  await pausa(1200);
  const libres = page.locator('.turno-p:not([disabled])');
  await libres.nth(4).click();
  await libres.nth(5).click();
  await foto('26-nueva-reserva-varios-turnos');
  const fechaManual = await page.locator('.tira-dias button[aria-pressed="true"]').getAttribute('aria-label').catch(() => null);
  await page.getByRole('textbox', { name: 'DNI' }).fill('99020006');
  await page.getByRole('button', { name: 'Buscar DNI' }).click();
  await pausa(4000);
  await page.getByRole('textbox', { name: 'Celular' }).fill('987654321');
  await page.getByRole('textbox', { name: 'Correo' }).fill('jorge.quispe@correo.pe');
  await page.getByLabel(/aviso de privacidad/).check({ force: true });
  await page.getByRole('button', { name: 'Registrar reserva' }).click();
  await pausa(3000);
  await foto('27-nueva-reserva-registrada', { completa: false });
  const codigo = (await page.locator('.ticket-codigo').textContent().catch(() => '')) || '';
  if (codigo) {
    await ir(`/admin/reservas/${codigo.trim()}`);
    await foto('28-reserva-varios-turnos-detalle');
    await ir('/admin/agenda?fecha=' + pasado(2));
    await foto('29-agenda-reserva-varios-turnos');
  } else problemas.push(`no se registró la reserva manual (${fechaManual})`);
  await ctx.close();
}

function pasado(n) {
  const d = new Date(Date.now() - 5 * 3600 * 1000 + n * 24 * 3600 * 1000);
  return d.toISOString().slice(0, 10);
}
function manana() {
  const d = new Date(Date.now() - 5 * 3600 * 1000 + 24 * 3600 * 1000);
  return d.toISOString().slice(0, 10);
}

// ------------------------------ PORTAL ------------------------------
async function portal(ancho) {
  const { page, foto, ir, ctx } = await sesion(ancho);
  console.log('Portal', ancho);
  // 7 · cuentas
  await ir('/ingresar');
  await foto('30-ingresar');
  await ir('/crear-cuenta');
  await page.getByRole('textbox', { name: 'DNI' }).fill(DNI);
  await pausa(1000);
  await page.waitForFunction(() => !document.body.innerText.includes('Consultando RENIEC'), null, { timeout: 30000 }).catch(() => undefined);
  if (!(await page.getByRole('textbox', { name: 'Nombres' }).inputValue())) {
    await page.getByRole('textbox', { name: 'Nombres' }).fill(NOMBRES);
    await page.getByRole('textbox', { name: 'Apellidos' }).fill(APELLIDOS);
  }
  await page.getByRole('textbox', { name: 'Celular' }).fill('987 654 321');
  await page.getByRole('textbox', { name: 'Correo' }).fill(`cuenta.${DNI}@correo.com`);
  await page.locator('input[type=password]').nth(0).fill(CLAVE);
  await page.locator('input[type=password]').nth(1).fill(CLAVE);
  await page.locator('input[type=checkbox]').nth(0).check({ force: true });
  await page.locator('input[type=checkbox]').nth(1).check({ force: true });
  await foto('31-crear-cuenta');
  // La cuenta se crea solo si no existe (CREAR=1); así no se gasta el límite de registros por minuto.
  if (process.env.CREAR === '1') {
    await page.getByRole('button', { name: 'Crear cuenta' }).last().click();
    await pausa(2500);
  }
  await page.evaluate(() => localStorage.removeItem('alc-cuenta'));
  // Si la cuenta ya existe se ingresa.
  await ir('/ingresar');
  await page.getByRole('textbox', { name: 'DNI o correo' }).fill(DNI);
  await page.locator('input[type=password]').fill(CLAVE);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await page.waitForURL('**/mi-cuenta', { timeout: 8000 }).catch(async () => {
    // Límite de ingresos por minuto del gateway: se espera y se reintenta una vez.
    await pausa(61000);
    await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  });
  await page.waitForURL('**/mi-cuenta', { timeout: 15000 }).catch(async () => {
    problemas.push(`[${ancho}] no se pudo ingresar con ${DNI}: ${await page.locator('.alerta').first().textContent().catch(() => '')}`);
  });
  await ir('/mi-cuenta');
  await foto('32-mi-cuenta-mis-reservas');
  await page.getByRole('radio', { name: 'Todas' }).click();
  await foto('33-mis-reservas-todas');
  await ir('/mi-cuenta/resenas');
  await foto('34-mis-resenas');
  if (ancho > 600) {
    const calificar = page.getByRole('link', { name: 'Calificar' }).first();
    if (await calificar.count()) {
      await calificar.click();
      await page.waitForSelector('#h-calificar', { timeout: 15000 });
      await pausa(1200);
      await page.locator('.estrellas-entrada button, [role=radiogroup] [role=radio]').nth(4).click().catch(() => undefined);
      await page.getByLabel('Comentario').fill('Jugamos con los amigos del trabajo: grass parejo, buena luz y atención rápida.');
      await page.locator('#h-calificar').scrollIntoViewIfNeeded();
      await foto('34b-calificar-con-cuenta', { completa: false });
      await page.getByRole('button', { name: 'Publicar reseña' }).click();
      await pausa(2500);
      await foto('34c-resena-publicada', { completa: false });
    } else problemas.push(`[${ancho}] la cuenta ${DNI} no tiene partidos por calificar`);
  }
  await ir('/mi-cuenta/datos');
  await foto('35-mis-datos');

  // 1 · paso 1: selector de cancha, calendario y varios turnos
  await ir('/reservar?cancha=1');
  await page.getByRole('button', { name: 'Cambiar' }).click();
  await pausa(500);
  await foto('36-reservar-modal-canchas', { completa: false });
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Ver calendario' }).click();
  await pausa(500);
  await foto('37-reservar-calendario-mes', { completa: false });
  await page.keyboard.press('Escape');
  await page.locator('.dia').nth(2).click();
  await pausa(1500);
  const chips = page.locator('.chip-turno.ct-disponible');
  const n = await chips.count();
  for (const i of [n - 3, n - 2]) if (i >= 0) await chips.nth(i).click();
  await pausa(500);
  await foto('38-reservar-varios-turnos');
  if (ancho < 600) await foto('38b-reservar-varios-turnos-pantalla', { completa: false });
  await page.getByRole('button', { name: 'Continuar' }).first().click();
  await pausa(800);
  await foto('39-reservar-paso2-con-cuenta');
  if (ancho > 600) {
    // Reserva REAL de varios turnos con Yape (queda en revisión → aparece en la campana del panel).
    await page.getByLabel(/términos y condiciones/).check({ force: true });
    await page.getByLabel(/política de privacidad/).check({ force: true });
    await page.locator('button:not(.sr-only):visible', { hasText: 'Continuar' }).first().click();
    await pausa(800);
    await page.getByRole('radio', { name: /^Yape/ }).click();
    await page.getByLabel('N.º de operación').fill(String(Date.now()).slice(-8));
    await page.setInputFiles('input[name="archivo"]', IMAGEN);
    await page.waitForSelector('text=Lista');
    await foto('39b-reservar-paso3-varios-turnos');
    await page.locator('button:not(.sr-only):visible', { hasText: 'Confirmar reserva' }).first().click();
    await page.waitForURL(/\/reservar\/listo\//, { timeout: 20000 }).catch(() => problemas.push('no llegó a «Listo» tras reservar varios turnos'));
    await pausa(1500);
    await foto('39c-reserva-varios-turnos-registrada');
  }
  await ctx.close();
  await aviso(ancho);
}

/** Rehace capturas sueltas sin crear datos nuevos (salvo una reseña si hay partidos por calificar). */
async function retoque() {
  {
    const { page, foto, ir, ctx } = await sesion(1440);
    await entrarPanel(page);
    await pausa(4500); // que se vaya el toast de bienvenida
    await page.locator('button.mt-avisos').click();
    await page.waitForSelector('.avisos-lista, .vacio', { timeout: 15000 });
    await foto('03-modal-avisos-comprobantes', { completa: false });
    await page.keyboard.press('Escape');
    await ir('/admin/agenda?fecha=' + pasado(2));
    await foto('29-agenda-reserva-varios-turnos');
    await ctx.close();
  }
  for (const ancho of [1440, 390]) {
    const { page, foto, ir, ctx } = await sesion(ancho);
    await ir('/ingresar');
    await page.getByRole('textbox', { name: 'DNI o correo' }).fill(DNI);
    await page.locator('input[type=password]').fill(CLAVE);
    await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
    await page.waitForURL('**/mi-cuenta', { timeout: 15000 });
    await pausa(1500);
    await foto('32-mi-cuenta-mis-reservas');
    if (ancho > 600) {
      await ir('/mi-cuenta/resenas');
      await page.getByRole('link', { name: 'Calificar' }).first().click();
      await page.waitForSelector('#h-calificar', { timeout: 15000 });
      await pausa(1200);
      await page.getByLabel('Comentario').fill('Buena cancha y puntual el cambio de turno. Volveremos el próximo mes.');
      await page.locator('[role=radiogroup] [role=radio]').nth(3).click().catch(() => undefined);
      await page.getByRole('button', { name: 'Publicar reseña' }).click();
      await pausa(4500);
      await page.locator('#h-calificar').scrollIntoViewIfNeeded();
      await foto('34c-resena-publicada', { completa: false });
    }
    await ir('/reservar?cancha=1');
    await page.locator('.dia').nth(3).click();
    await pausa(1500);
    const chips = page.locator('.chip-turno.ct-disponible');
    await chips.nth(5).click();
    await chips.nth(6).click();
    await page.locator('button:not(.sr-only):visible', { hasText: 'Continuar' }).first().click();
    await pausa(1000);
    await foto('39-reservar-paso2-con-cuenta');
    await ctx.close();
  }
}

/** Aviso de reintento: la disponibilidad responde 503 dos veces (como durante un despliegue) y luego se recupera. */
async function aviso(ancho) {
  const { page, foto, ctx } = await sesion(ancho);
  let fallos = 0;
  await page.route('**/api/disponibilidad?*', (r) => (fallos++ < 2 ? r.fulfill({ status: 503, body: '' }) : r.continue()));
  await page.goto(BASE + '/reservar?cancha=1');
  await page.waitForSelector('.aviso-reintento', { timeout: 15000 }).catch(() => problemas.push(`[${ancho}] no apareció el aviso de reintento`));
  await foto('40-aviso-actualizando-sistema', { completa: false });
  await page.waitForSelector('.chip-turno', { timeout: 20000 }).catch(() => problemas.push(`[${ancho}] no se recuperó tras el reintento`));
  await pausa(600);
  await foto('41-tras-reintento-horarios', { completa: false });
  await ctx.close();
}

// Primero el portal: la reserva de varios turnos que deja en revisión aparece luego en la campana del panel.
if (QUE === 'portal' || QUE === 'todo') {
  await portal(1440);
  await portal(390);
}
if (QUE === 'panel' || QUE === 'todo') await panel();
if (QUE === 'retoque') await retoque();
if (QUE === 'barra') {
  const { page, foto, ctx } = await sesion(1440);
  await entrarPanel(page);
  await pausa(4500);
  await foto('01-barra-lateral-abierta', { completa: false });
  await page.getByRole('button', { name: 'Plegar menú' }).click();
  await pausa(400);
  const caja = await page.locator('.ma-item').nth(2).boundingBox();
  if (caja) await page.mouse.move(caja.x + caja.width / 2, caja.y + caja.height / 2);
  await foto('02-barra-lateral-plegada', { completa: false });
  await page.getByRole('button', { name: 'Desplegar menú' }).click();
  await ctx.close();
}
if (QUE === 'cuenta') {
  for (const ancho of [1440, 390]) {
    const { page, foto, ir, ctx } = await sesion(ancho);
    await ir('/ingresar');
    await page.getByRole('textbox', { name: 'DNI o correo' }).fill(DNI);
    await page.locator('input[type=password]').fill(CLAVE);
    await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
    await page.waitForURL('**/mi-cuenta', { timeout: 15000 });
    await pausa(4500);
    await foto('32-mi-cuenta-mis-reservas');
    await page.getByRole('radio', { name: 'Todas' }).click();
    await foto('33-mis-reservas-todas');
    await ir('/mi-cuenta/resenas');
    await foto('34-mis-resenas');
    await ir('/mi-cuenta/datos');
    await foto('35-mis-datos');
    await ctx.close();
  }
}
if (QUE === 'aviso') {
  await aviso(1440);
  await aviso(390);
}
await nav.close();
if (problemas.length) {
  console.log('\nRevisar:');
  for (const p of problemas) console.log(' -', p);
}
