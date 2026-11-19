// Capturas de «Recuperar contraseña», «Correo», celular de administradores, recordatorios y asistencia (v2, 50 en adelante).
// Uso: npm run dev (o dev:v2) en otra terminal → node scripts/capturas-v2-recuperar.mjs [portal|panel|todo] [DNI o correo del cliente]
// Variables: BASE (http://localhost:5186), SALIDA (carpeta; por defecto ../docs/capturas-reales/v2), CLAVE_CLIENTE (la nueva del cliente).
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const AQUI = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.env.BASE || 'http://localhost:5186';
const SALIDA = process.env.SALIDA || path.resolve(AQUI, '..', '..', 'docs', 'capturas-reales', 'v2');
const QUE = process.argv[2] || 'todo';
const CLIENTE = process.argv[3] || '99010003';
const CLAVE_CLIENTE = process.env.CLAVE_CLIENTE || 'Cancha2026';
const ADMIN_PRUEBA = process.env.ADMIN_PRUEBA || 'prueba.recupera';
const CLAVE_ADMIN_PRUEBA = 'Recupera2026';
const SMTP_FALSO = 'smtp.noexiste-alcanfores.pe';
mkdirSync(SALIDA, { recursive: true });

let nav;
try {
  nav = await chromium.launch();
} catch {
  nav = await chromium.launch({ channel: 'chrome' });
}
const problemas = [];
const pausa = (ms) => new Promise((r) => setTimeout(r, ms));
const ANCHOS = (process.env.ANCHOS ?? '1440,390').split(',').map(Number);
const PAUSA_MS = Number(process.env.PAUSA_MS ?? 65000);
/** El limitador repone ~1 llamada cada 12 s: se espera entre pasos del asistente (0 con mocks). */
const PASO_MS = Number(process.env.PASO_MS ?? 13000);
let recorridos = 0;

async function sesion(ancho) {
  const ctx = await nav.newContext({ viewport: { width: ancho, height: ancho < 600 ? 844 : 900 }, locale: 'es-PE', timezoneId: 'America/Lima' });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => problemas.push(`[${ancho}] error JS: ${e.message}`));
  page.on('console', (m) => m.type() === 'error' && !/Failed to load resource|maps|gstatic/i.test(m.text()) && problemas.push(`[${ancho}] consola: ${m.text().slice(0, 160)}`));
  page.on('response', async (r) => {
    const u = new URL(r.url()).pathname;
    if (/\/api\/(auth\/recuperar|admin\/correo|auth\/yo|admin\/usuarios$|admin\/configuracion)/.test(u) && r.request().method() !== 'GET')
      console.log('   ', r.request().method(), u, r.status(), (await r.text().catch(() => '')).slice(0, 200));
  });
  const foto = async (nombre, { completa = true } = {}) => {
    await pausa(450);
    const estilo = completa ? await page.addStyleTag({ content: '.mc-header,.ma-topbar,.barra-fija,[class*="lg:sticky"],.ma-sidebar-in{position:static!important}' }) : null;
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
    await pausa(600);
  };
  return { ctx, page, foto, ir };
}

/**
 * Pulsa y espera `listo`. Si el gateway responde 429 («Espera N segundos…»), espera esos segundos y vuelve a pulsar
 * (el error se ve en la pantalla igual que lo vería el cliente).
 */
async function pulsarConReintento(page, pulsar, listo, intentos = 6) {
  for (let i = 0; i < intentos; i += 1) {
    const r429 = page.waitForResponse((r) => /\/api\/auth\/recuperar\//.test(r.url()) && r.request().method() === 'POST', { timeout: 60000 });
    await pulsar();
    const resp = await r429.catch(() => null);
    if (resp && resp.status() === 429) {
      const cuerpo = await resp.json().catch(() => ({}));
      const seg = Number(/(\d+)\s*segundo/.exec(cuerpo.mensaje ?? '')?.[1] ?? 12);
      console.log(`    429: ${cuerpo.mensaje ?? ''} → espero ${seg + 2} s`);
      await pausa((seg + 2) * 1000);
      continue;
    }
    await listo();
    return;
  }
  throw new Error('El gateway siguió respondiendo 429');
}

/** Recorre el asistente: paso 1 → canales → código (con «Usar» del modo prueba) → nueva contraseña → listo. */
async function recuperar({ page, foto, ir }, { ruta, usuario, clave, prefijo, numeros, conError }) {
  // El gateway deja 5 llamadas por minuto por IP en /auth/recuperar/** y 60 s entre códigos de una cuenta.
  if (recorridos++ > 0) {
    console.log(`  (pausa de ${PAUSA_MS / 1000} s por el límite del gateway)`);
    await pausa(PAUSA_MS);
  }
  await ir(ruta);
  await page.getByRole('textbox').first().fill(usuario);
  await foto(`${numeros[0]}-${prefijo}-paso1-cuenta`);
  await pulsarConReintento(
    page,
    () => page.getByRole('button', { name: 'Continuar' }).click(),
    () => page.getByText('¿Dónde recibes el código?').waitFor({ timeout: 20000 }),
  );
  await foto(`${numeros[1]}-${prefijo}-paso2-canal`);
  await pausa(PASO_MS);
  await pulsarConReintento(
    page,
    () => page.locator('.rec-canal:not([disabled])').first().click(),
    () => page.getByText('Escribe el código').waitFor({ timeout: 45000 }),
  );
  await pausa(400);
  await foto(`${numeros[2]}-${prefijo}-paso3-codigo`);
  if (conError) {
    await pausa(PASO_MS);
    await pulsarConReintento(
      page,
      () => page.getByLabel('Dígito 1').fill('000000'),
      () => page.locator('.otp.is-error').first().waitFor({ timeout: 15000 }),
    );
    await foto(`${numeros[2]}b-${prefijo}-codigo-incorrecto`);
  }
  const usar = page.locator('.rec-prueba button');
  if (!(await usar.count())) {
    problemas.push(`${prefijo}: no llegó codigoPrueba (¿perfil local?)`);
    return;
  }
  await pausa(PASO_MS);
  await pulsarConReintento(
    page,
    () => usar.click(),
    () => page.getByText('Crea tu nueva contraseña').waitFor({ timeout: 15000 }),
  );
  await page.getByLabel('Nueva contraseña').fill(clave.slice(0, 6));
  await page.getByLabel('Repite la contraseña').click();
  await foto(`${numeros[3]}-${prefijo}-paso4-nueva`);
  await page.getByLabel('Nueva contraseña').fill(clave);
  await page.getByLabel('Repite la contraseña').fill(clave);
  await pausa(PASO_MS);
  await pulsarConReintento(
    page,
    () => page.getByRole('button', { name: 'Guardar contraseña' }).click(),
    () => page.getByText('Contraseña actualizada').waitFor({ timeout: 15000 }),
  );
  await foto(`${numeros[4]}-${prefijo}-listo`);
}

async function entrarPanel(page, usuario = 'rhuaman', clave = 'Alcanfores2026') {
  await page.goto(BASE + '/admin/login', { waitUntil: 'networkidle' });
  await page.getByRole('textbox', { name: 'Usuario' }).fill(usuario);
  await page.locator('input[type=password]').fill(clave);
  await page.keyboard.press('Enter');
  await page.waitForURL('**/admin/hoy', { timeout: 20000 });
  await pausa(500);
}

if (QUE === 'portal' || QUE === 'todo') {
  for (const ancho of ANCHOS) {
    console.log(`Portal ${ancho}`);
    const s = await sesion(ancho);
    await s.ir('/ingresar');
    await s.foto('50-ingresar-olvidaste');
    await recuperar(s, { ruta: '/recuperar', usuario: CLIENTE, clave: CLAVE_CLIENTE, prefijo: 'recuperar-cliente', numeros: [51, 52, 53, 54, 55], conError: ancho === 1440 });
    await s.ctx.close();
  }
}

if (QUE === 'panel' || QUE === 'todo') {
  if (process.env.SOLO_RECUPERAR !== '1') {
  console.log('Panel 1440');
  const s = await sesion(1440);
  const { page, foto, ir } = s;
  await entrarPanel(page);

  // Usuarios: crear (o editar) el administrador de prueba con celular.
  await ir('/admin/usuarios?q=' + ADMIN_PRUEBA);
  const existe = await page.getByText('@' + ADMIN_PRUEBA).count();
  if (existe) {
    await page.locator('.td-mas').first().click();
    await page.getByRole('menuitem', { name: 'Editar' }).click();
  } else await page.getByRole('button', { name: 'Nuevo usuario' }).click();
  const dlg = page.getByRole('dialog');
  const caja = (n) => dlg.getByRole('textbox', { name: n, exact: true });
  if (!existe) {
    await caja('Nombres').fill('Prueba');
    await caja('Apellidos').fill('Recupera');
    await caja('Usuario').fill(ADMIN_PRUEBA);
    await caja('Correo').fill('prueba.recupera@alcanfores.pe');
    await dlg.getByRole('radio', { name: 'Administrador' }).click();
    await dlg.locator('input[name=contrasena]').fill(CLAVE_ADMIN_PRUEBA);
    await dlg.locator('input[name=confirmacion]').fill(CLAVE_ADMIN_PRUEBA);
  }
  await dlg.locator('input[name=telefono]').fill('987 654 321');
  await foto('60-usuarios-celular', { completa: false });
  await dlg.getByRole('button', { name: existe ? 'Guardar' : 'Crear usuario' }).click();
  await pausa(1500);
  await ir('/admin/usuarios');
  await foto('61-usuarios-contacto');

  // Mi cuenta
  await ir('/admin/cuenta');
  await foto('62-mi-cuenta-datos');

  // Reglas de reserva: recordatorios en chips + asistencia desde (se agrega «15 min», se guarda y se deja como estaba).
  await ir('/admin/configuracion/reglas');
  await page.getByRole('button', { name: 'Agregar', exact: true }).first().click().catch(() => {});
  await pausa(300);
  await foto('67-reglas-recordatorios');
  const rapida = page.locator('.chip-rapida', { hasText: /^15 min$/ });
  if ((await rapida.count()) && (await rapida.isEnabled())) {
    await rapida.click();
    await page.getByRole('button', { name: 'Guardar', exact: true }).click();
    await page.getByText('Guardado', { exact: true }).waitFor({ timeout: 15000 }).catch(() => problemas.push('reglas: no se guardó'));
    await pausa(600);
    await foto('67b-reglas-recordatorios-guardados', { completa: false });
    await page.getByRole('button', { name: 'Quitar 15 min antes' }).click();
    await page.getByRole('button', { name: 'Guardar', exact: true }).click();
    await pausa(1500);
  }

  // Asistencia: detalle de una reserva confirmada futura («Desde las HH:mm» según «Marcar asistencia desde»).
  try {
    await ir('/admin/reservas?estado=CONFIRMADA');
    const cod = (await page.locator('text=/ALC-[A-Z0-9]{6}/').first().innerText()).match(/ALC-[A-Z0-9]{6}/)?.[0];
    if (cod) {
      await ir('/admin/reservas/' + cod);
      await page.getByRole('heading', { name: 'Asistencia' }).scrollIntoViewIfNeeded();
      await foto('69-reserva-asistencia', { completa: false });
    }
  } catch (e) {
    problemas.push('asistencia: ' + e.message.slice(0, 120));
  }

  // Notificaciones: WhatsApp con la tarjeta ilustrada (si hay alguno con IMAGEN).
  await ir('/admin/notificaciones?canal=WHATSAPP');
  const fila = page.locator('.noti-fila, [class*="bandeja"] button').first();
  if (await fila.count()) await fila.click().catch(() => {});
  await pausa(800);
  await foto('68-notificaciones-whatsapp-tarjeta', { completa: false });
  await s.ctx.close();

  }
  // Recuperar del panel (público): con el administrador de prueba.
  for (const ancho of ANCHOS) {
    console.log(`Panel · recuperar ${ancho}`);
    const r = await sesion(ancho);
    await r.ir('/admin/login');
    await r.foto('56-admin-login-olvidaste', { completa: false });
    await recuperar(r, { ruta: '/admin/recuperar', usuario: ADMIN_PRUEBA, clave: CLAVE_ADMIN_PRUEBA, prefijo: 'recuperar-admin', numeros: ['57', '58', '59', '59b', '59c'], conError: false });
    await r.ctx.close();
  }

  // Configuración → Correo (AL FINAL: guarda un servidor inexistente y desde ahí los correos fallan hasta corregirlo).
  if (process.env.SIN_CORREO !== '1') {
    const c = await sesion(1440);
    const { page, foto, ir } = c;
    await entrarPanel(page);
    // Configuración → Correo
    await ir('/admin/configuracion');
    await foto('63-configuracion-tarjeta-correo');
    await ir('/admin/configuracion/correo');
    await foto('64-correo-estado');
    await page.locator('input[name=remitente]').fill('reservas@alcanfores.pe');
    await page.getByRole('button', { name: 'Usar datos de Hostinger' }).click();
    await pausa(300);
    await foto('65-correo-hostinger');
    // Servidor inexistente: se guarda y la prueba muestra el error.
    await page.locator('input[name=host]').fill(SMTP_FALSO);
    await page.locator('input[name=usuario]').fill('reservas@alcanfores.pe');
    await page.locator('input[name=clave]').fill('ClaveFalsa2026');
    await page.getByRole('button', { name: 'Guardar', exact: true }).click();
    await page.getByText('Configurado').first().waitFor({ timeout: 15000 }).catch(() => problemas.push('correo: no pasó a Configurado'));
    await page.locator('input[name=destino]').fill('luis.prueba@gmail.com');
    await page.getByRole('button', { name: 'Enviar correo de prueba' }).click();
    await page.locator('.alerta-error, .alerta-exito, .alerta-advertencia').last().waitFor({ timeout: 60000 });
    await foto('66-correo-prueba-error');
    await c.ctx.close();
  }
}

await nav.close();
if (problemas.length) {
  console.log('\nProblemas:');
  for (const p of problemas) console.log(' -', p);
} else console.log('\nSin problemas.');
