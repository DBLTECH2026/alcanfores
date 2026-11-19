// Recorre el portal y el login con el back-end simulado (VITE_MOCK=true) y guarda capturas a 390 y 1440 px.
// Uso: npm run dev:mock  (en otra terminal)  →  npm run capturas  [URL base]  [carpeta]
// Por defecto: http://localhost:5186 → ../docs/capturas-front
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const AQUI = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.argv[2] || 'http://localhost:5186';
const SALIDA = path.resolve(process.argv[3] || path.join(AQUI, '..', '..', 'docs', 'capturas-front'));
const IMAGEN = path.join(AQUI, 'comprobante-prueba.png');
mkdirSync(SALIDA, { recursive: true });

let navegador;
try {
  navegador = await chromium.launch();
} catch {
  // Sin el Chromium de Playwright descargado, usa el Chrome instalado.
  navegador = await chromium.launch({ channel: 'chrome' });
}

const problemas = [];
const pausa = (ms) => new Promise((r) => setTimeout(r, ms));

async function sesion(ancho) {
  const contexto = await navegador.newContext({ viewport: { width: ancho, height: ancho < 600 ? 844 : 900 }, locale: 'es-PE', timezoneId: 'America/Lima', deviceScaleFactor: 1 });
  const page = await contexto.newPage();
  page.on('pageerror', (e) => problemas.push(`[${ancho}] error JS: ${e.message}`));
  page.on('console', (m) => m.type() === 'error' && !/maps|google|gstatic|ERR_|Failed to load resource/i.test(m.text()) && problemas.push(`[${ancho}] consola: ${m.text()}`));
  const foto = async (nombre, { completa = true } = {}) => {
    await pausa(450);
    // En página completa, lo «sticky» (cabeceras, barra inferior móvil, resumen lateral) se pinta en su lugar natural.
    const estilo = completa ? await page.addStyleTag({ content: '.mc-header,.ma-topbar,.barra-fija,[class*="lg:sticky"],.ma-sidebar-in{position:static!important}' }) : null;
    await page.evaluate(() => window.scrollTo(0, 0));
    await pausa(150);
    const desborde = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    if (desborde > 1) problemas.push(`[${ancho}] ${nombre}: desborde horizontal de ${desborde}px`);
    await page.screenshot({ path: path.join(SALIDA, `${nombre}-${ancho}.png`), fullPage: completa });
    if (estilo) await estilo.evaluate((n) => n.remove());
    console.log('  ✓', `${nombre}-${ancho}.png`);
  };
  const ir = async (ruta) => {
    await page.goto(BASE + ruta, { waitUntil: 'networkidle' });
    await pausa(500);
  };
  const boton = (texto) => page.locator('button:visible:not(.sr-only), a:visible').filter({ hasText: texto }).first();
  return { contexto, page, foto, ir, boton };
}

async function elegirTurno(page, { cancha, dia, hora } = {}) {
  if (cancha) await page.locator('.chip-cancha').filter({ hasText: cancha }).click();
  await page.waitForSelector('.dia');
  if (dia !== undefined) await page.locator('.dia').nth(dia).click();
  await page.waitForSelector('.chip-turno');
  await pausa(300);
  if (hora) await page.locator(`.chip-turno[aria-label^="${hora}"]`).first().click();
  else await page.locator('.chip-turno.ct-disponible').first().click();
}

async function datos(page, { dni, correo, factura }) {
  await page.getByRole('textbox', { name: 'DNI' }).fill(dni);
  await page.waitForSelector('text=Verificado con RENIEC');
  await page.getByRole('textbox', { name: 'Celular' }).fill('987 654 321');
  await page.getByRole('textbox', { name: 'Correo' }).fill(correo);
  if (factura) {
    await page.getByRole('radio', { name: 'Factura' }).click();
    await page.getByRole('textbox', { name: 'RUC' }).fill(factura);
    await page.waitForSelector('text=Verificado con SUNAT');
  }
  await page.getByLabel(/términos y condiciones/).check({ force: true });
  await page.getByLabel(/política de privacidad/).check({ force: true });
}

for (const ancho of [390, 1440]) {
  console.log(`\n== ${ancho} px ==`);
  const { contexto, page, foto, ir, boton } = await sesion(ancho);

  // ---------- conocer ----------
  await ir('/');
  await foto('01-inicio');
  await ir('/canchas');
  await foto('02-canchas');
  await ir('/canchas/cancha-1-futbol-5');
  await foto('03-cancha-detalle');
  await page.locator('.galeria-principal').click();
  await page.waitForSelector('.visor-pantalla');
  await foto('04-cancha-visor', { completa: false });
  await page.keyboard.press('Escape');

  // ---------- reseña verificada (?resena=) ----------
  await ir('/canchas/cancha-1-futbol-5?resena=ALC-H2P6WD');
  await page.getByRole('button', { name: 'Publicar reseña' }).click();
  await foto('05-resena-errores');
  await page.getByRole('textbox', { name: 'DNI' }).fill('45678912');
  await page.getByRole('radio', { name: /^5 estrellas/ }).click();
  await page.getByLabel('Comentario').fill('Jugamos el jueves y todo impecable: luces, grass y la atención. Volveremos.');
  await page.getByRole('button', { name: 'Publicar reseña' }).click();
  await page.waitForSelector('text=Tu reseña ya está publicada');
  await foto('06-resena-publicada');

  // ---------- reserva con Yape ----------
  await ir('/reservar');
  await elegirTurno(page);
  await foto('10-paso1-horario');
  await boton(/^Continuar$/).click();
  await boton(/^Continuar$/).click();
  await pausa(300);
  await foto('11-paso2-errores');
  await datos(page, { dni: '72345678', correo: 'dietri.diaz@correo.pe' });
  await foto('12-paso2-datos');
  await boton(/^Continuar$/).click();
  await page.getByRole('radio', { name: /^Yape/ }).click();
  await foto('13-paso3-metodos');
  await page.getByLabel('N.º de operación').fill('12345678');
  await page.setInputFiles('input[name="archivo"]', IMAGEN);
  await page.waitForSelector('text=Lista');
  await foto('14-paso3-yape');
  await boton(/Confirmar reserva/).click();
  await page.waitForURL(/\/reservar\/listo\//, { timeout: 15000 });
  await foto('15-listo-yape');

  // ---------- reserva con tarjeta (Alcanfores Pay beta) + factura ----------
  await ir('/reservar');
  await elegirTurno(page, { cancha: 'Cancha 3', dia: 2 });
  await boton(/^Continuar$/).click();
  await datos(page, { dni: '45678912', correo: 'jorge.chuco@correo.pe', factura: '20612345679' });
  await foto('16-paso2-factura');
  await boton(/^Continuar$/).click();
  await page.getByRole('radio', { name: /Alcanfores Pay/ }).click();
  await foto('17-paso3-tarjeta');
  await boton(/Confirmar reserva/).click();
  await page.waitForURL(/\/pasarela\//, { timeout: 15000 });
  await page.waitForSelector('.tarjeta-3d');
  const referencia = page.url().split('/').pop();
  await foto('20-pasarela');
  await page.locator('.prueba').filter({ hasText: '0002' }).click();
  await foto('21-pasarela-tarjeta-llena');
  await page.getByRole('button', { name: /^Pagar/ }).click();
  await page.waitForSelector('text=Pago rechazado');
  await foto('22-pasarela-rechazado');
  await page.getByRole('button', { name: 'Intentar de nuevo' }).click();
  await page.locator('.prueba').filter({ hasText: '3184' }).click();
  await page.getByRole('button', { name: /^Pagar/ }).click();
  await page.waitForSelector('text=Verificación 3-D Secure');
  await page.getByLabel('Código de verificación').fill('123456');
  await foto('23-pasarela-3ds', { completa: false });
  await page.getByRole('button', { name: 'Verificar' }).click();
  await page.waitForSelector('text=Pago aprobado');
  await foto('24-pasarela-aprobado');
  await page.getByRole('button', { name: 'Volver al comercio' }).click();
  await page.waitForURL(/\/reservar\/listo\//);
  await foto('25-listo-tarjeta');
  await ir(`/pago/resultado?ref=${referencia}`);
  await page.waitForSelector('text=¡Pago aprobado!');
  await foto('26-pago-resultado');

  // ---------- 409 TURNO_OCUPADO ----------
  await ir('/reservar');
  await elegirTurno(page, { cancha: 'Cancha 2', dia: 1, hora: '9:00 p. m.' });
  await boton(/^Continuar$/).click();
  await datos(page, { dni: '70112244', correo: 'prueba.409@correo.pe' });
  await boton(/^Continuar$/).click();
  await page.getByRole('radio', { name: /Alcanfores Pay/ }).click();
  await boton(/Confirmar reserva/).click();
  await page.waitForSelector('text=Alguien se adelantó');
  await foto('27-conflicto-409', { completa: false });
  await page.getByRole('button', { name: 'Elige otro horario' }).click();
  await pausa(600);
  await foto('28-paso1-tras-409');

  // ---------- mi reserva ----------
  await page.evaluate(() => sessionStorage.clear());
  await ir('/mi-reserva');
  await foto('30-mi-reserva-consulta');
  await page.getByRole('textbox', { name: 'Código', exact: true }).fill('ALC-XXXXXX');
  await page.getByRole('textbox', { name: 'DNI' }).fill('12345678');
  await page.getByRole('button', { name: 'Consultar' }).click();
  await page.waitForSelector('text=No encontramos una reserva');
  await foto('31-mi-reserva-no-encontrada');
  await ir('/mi-reserva?codigo=ALC-7K3Q9P');
  await page.getByRole('textbox', { name: 'DNI' }).fill('72345678');
  await page.getByRole('button', { name: 'Consultar' }).click();
  await page.waitForSelector('text=Historial');
  await foto('32-mi-reserva-confirmada');
  await page.getByRole('button', { name: 'Cancelar reserva' }).click();
  await page.getByLabel('Motivo').fill('Se suspendió el partido por un viaje.');
  await foto('33-mi-reserva-cancelar', { completa: false });
  await page.getByRole('button', { name: 'Sí, cancelar' }).click();
  await page.waitForSelector('.cancelacion');
  await foto('34-mi-reserva-cancelada');
  await page.getByRole('button', { name: 'Consultar otra' }).click();
  await page.getByRole('textbox', { name: 'Código', exact: true }).fill('ALC-M4R8TB');
  await page.getByRole('textbox', { name: 'DNI' }).fill('70112233');
  await page.getByRole('button', { name: 'Consultar' }).click();
  await page.waitForSelector('text=Historial');
  await foto('35-mi-reserva-observada');
  await page.getByRole('button', { name: 'Reenviar comprobante' }).click();
  await page.getByLabel('N.º de operación').fill('87654321');
  await page.setInputFiles('input[name="archivo"]', IMAGEN);
  await page.waitForSelector('.modal >> text=Lista');
  await foto('36-mi-reserva-reenviar', { completa: false });
  await page.getByRole('button', { name: /^Enviar$/ }).click();
  await page.waitForSelector('text=Tu horario está separado');
  await foto('37-mi-reserva-en-revision');
  await page.getByRole('button', { name: 'Consultar otra' }).click();
  await page.getByRole('textbox', { name: 'Código', exact: true }).fill('ALC-H2P6WD');
  await page.getByRole('textbox', { name: 'DNI' }).fill('45678912');
  await page.getByRole('button', { name: 'Consultar' }).click();
  await page.waitForSelector('text=Historial');
  await foto('38-mi-reserva-jugada');

  // ---------- legales y 404 ----------
  await ir('/terminos');
  await foto('40-terminos');
  await ir('/privacidad');
  await foto('41-privacidad');
  await ir('/esta-pagina-no-existe');
  await foto('42-no-encontrada');

  // ---------- panel ----------
  await ir('/admin/login');
  await foto('50-login');
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await foto('51-login-errores');
  await page.getByLabel('Usuario').fill('rhuaman');
  await page.locator('input[name="contrasena"]').fill('ClaveIncorrecta1');
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForSelector('text=Usuario o contraseña incorrectos');
  await foto('52-login-credenciales');
  await page.locator('input[name="contrasena"]').fill('Alcanfores2026');
  await page.getByRole('button', { name: 'Mostrar contraseña' }).click();
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(/\/admin\/hoy/);
  await page.waitForSelector('.kpi-val');
  await foto('53-panel-hoy');
  if (ancho < 600) {
    await page.getByRole('button', { name: 'Abrir menú' }).click();
    await foto('54-panel-menu', { completa: false });
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: 'Cerrar menú' }).click();
  } else {
    await page.locator('.mt-usuario').click();
    await foto('54-panel-menu-usuario', { completa: false });
  }
  await ir('/admin/canchas');
  await foto('55-panel-en-construccion');

  await contexto.close();
}

await navegador.close();
console.log(problemas.length ? `\nProblemas:\n${problemas.join('\n')}` : '\nSin errores de consola ni desbordes.');
process.exitCode = problemas.length ? 1 : 0;
