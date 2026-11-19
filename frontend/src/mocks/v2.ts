/**
 * Modo HÍBRIDO de desarrollo (`npm run dev:v2`, VITE_MOCK=v2): MSW responde SOLO los endpoints nuevos de la v2
 * (SPEC_V2) mientras el back real todavía es v1; todo lo demás pasa al gateway. No entra al build ni se usa con el back v2.
 */
import { bypass, delay, http, HttpResponse } from 'msw';
import { setupWorker } from 'msw/browser';
import type { ConfigCorreo, DiaEspecialAdmin, HorarioAdmin, ResenaAdmin, SolicitudCorreo } from '@/api/admin/tipos';
import type { CuentaCliente } from '@/api/tipos';
import { hoyLima, isoLima, ahoraLima } from '@/utilidades/fecha';
import { semanaPorDefecto, textoSemana } from '@/utilidades/horario';

const CLAVE = 'alc-mock-v2';
interface Bd {
  calendario: DiaEspecialAdmin[];
  horarios: Record<string, HorarioAdmin>;
  resenas: ResenaAdmin[];
  cuentas: Array<CuentaCliente & { contrasena: string }>;
  sec: number;
  /** Recuperar contraseña y correo (SMTP). */
  correo?: ConfigCorreo & { clave?: string };
  telefonos?: Record<string, string | null>;
}
const FERIADOS: Array<[string, string]> = [
  ['01-01', 'Año Nuevo'], ['05-01', 'Día del Trabajo'], ['06-07', 'Batalla de Arica y Día de la Bandera'], ['06-29', 'San Pedro y San Pablo'],
  ['07-23', 'Día de la Fuerza Aérea'], ['07-28', 'Fiestas Patrias'], ['07-29', 'Fiestas Patrias'], ['08-06', 'Batalla de Junín'],
  ['08-30', 'Santa Rosa de Lima'], ['10-08', 'Combate de Angamos'], ['11-01', 'Todos los Santos'], ['12-08', 'Inmaculada Concepción'],
  ['12-09', 'Batalla de Ayacucho'], ['12-25', 'Navidad'],
];
function leer(): Bd {
  try {
    const b = JSON.parse(localStorage.getItem(CLAVE) ?? 'null') as Bd | null;
    if (b) return b;
  } catch {
    /* nueva */
  }
  const b: Bd = { calendario: [], horarios: {}, resenas: [], cuentas: [], sec: 1 };
  for (const [md, n] of FERIADOS) b.calendario.push({ id: b.sec++, fecha: `2026-${md}`, tipo: 'FERIADO', nombre: n, canchas: null });
  b.calendario.push({ id: b.sec++, fecha: '2026-11-27', tipo: 'NO_LABORABLE', nombre: 'Mantenimiento del grass', canchas: [2] });
  return b;
}
let bd = leer();
const guardar = () => localStorage.setItem(CLAVE, JSON.stringify(bd));
const ok = (d: unknown, status = 200) => HttpResponse.json(d as never, { status });
const err = (status: number, codigo: string, mensaje: string, errores: Array<{ campo: string; mensaje: string }> = []) => HttpResponse.json({ status, codigo, mensaje, errores }, { status });
const horario = (id: string): HorarioAdmin => bd.horarios[id] ?? { duracionTurnoMin: 60, inicioNoche: '18:00', dias: semanaPorDefecto(), feriado: { modo: 'ABIERTA', recargo: 10 } };
const dniDe = (req: Request) => /cliente-(\d{8})/.exec(req.headers.get('Authorization') ?? '')?.[1] ?? null;
const sesion = (c: CuentaCliente) => ({ token: `x.cliente-${c.dni}.y`, tipo: 'Bearer', expiraEn: isoLima(ahoraLima().add(8, 'hour')), cliente: c });
const sinClave = ({ contrasena: _c, ...c }: Bd['cuentas'][number]) => c;

// ---------- recuperar contraseña (en memoria) ----------
interface Recuperacion { codigo: string; expira: number; intentos: number; envios: number }
const recuperaciones = new Map<string, Recuperacion>();
const tokens = new Map<string, string>();
const enmascararCorreo = (c: string) => c.replace(/^(.{2})[^@]*(@.*)$/, '$1***$2');
const enmascararCel = (t: string) => `*** *** ${t.slice(-3)}`;
const idPanel = () => (JSON.parse(localStorage.getItem('alc-sesion') ?? 'null') as { usuario?: { id: number } } | null)?.usuario?.id ?? 0;
const correoInicial = (): ConfigCorreo => ({ configurado: false, origen: 'NINGUNO', host: null, puerto: null, usuario: null, remitente: null, nombreRemitente: null, seguridad: null, tieneClave: false });
const sinClaveCorreo = (c: ConfigCorreo & { clave?: string }): ConfigCorreo => {
  const { clave: _c, ...resto } = c;
  return resto;
};
const MSJ_INTENTOS = 'Muchos intentos. Pide un código nuevo.';

const manejadores = [
  // ---------- recuperar contraseña ----------
  http.post('/api/auth/recuperar/opciones', async ({ request }) => {
    await delay(350);
    const b = (await request.json()) as { tipoCuenta: string; usuario: string };
    if (b.usuario === '00000000' || b.usuario === 'noexiste') return err(404, 'CUENTA_NO_ENCONTRADA', 'No encontramos una cuenta con esos datos.', [{ campo: 'usuario', mensaje: 'No encontramos una cuenta con esos datos.' }]);
    const cuenta = bd.cuentas.find((c) => c.dni === b.usuario || c.correo === b.usuario);
    const correo = cuenta?.correo ?? (b.usuario.includes('@') ? b.usuario : b.tipoCuenta === 'ADMIN' ? `${b.usuario}@alcanfores.pe` : 'cliente@gmail.com');
    const tel = b.tipoCuenta === 'ADMIN' ? (bd.telefonos?.yo ?? null) : (cuenta?.telefono ?? '987654659');
    const canales = [{ canal: 'CORREO', destino: enmascararCorreo(correo) }, ...(tel ? [{ canal: 'WHATSAPP', destino: enmascararCel(tel) }] : [])];
    return ok({ canales });
  }),
  http.post('/api/auth/recuperar/enviar', async ({ request }) => {
    await delay(600);
    const b = (await request.json()) as { tipoCuenta: string; usuario: string; canal: 'CORREO' | 'WHATSAPP' };
    const k = `${b.tipoCuenta}:${b.usuario}`;
    const previo = recuperaciones.get(k);
    if (previo && previo.envios >= 5) return err(429, 'DEMASIADOS_ENVIOS', 'Pediste muchos códigos. Espera 15 minutos e intenta de nuevo.');
    const r: Recuperacion = { codigo: String(Math.floor(100000 + Math.random() * 900000)), expira: Date.now() + 10 * 60000, intentos: 0, envios: (previo?.envios ?? 0) + 1 };
    recuperaciones.set(k, r);
    const destino = b.canal === 'CORREO' ? (b.usuario.includes('@') ? enmascararCorreo(b.usuario) : 'cl***@gmail.com') : '*** *** 659';
    return ok({ destino, expiraEn: isoLima(ahoraLima().add(10, 'minute')), reenviarEn: isoLima(ahoraLima().add(45, 'second')), simulado: b.canal === 'WHATSAPP' || !bd.correo?.configurado, codigoPrueba: r.codigo });
  }),
  http.post('/api/auth/recuperar/verificar', async ({ request }) => {
    await delay(400);
    const b = (await request.json()) as { tipoCuenta: string; usuario: string; codigo: string };
    const r = recuperaciones.get(`${b.tipoCuenta}:${b.usuario}`);
    if (!r || Date.now() > r.expira) return err(422, 'CODIGO_VENCIDO', 'El código venció. Pide otro.', [{ campo: 'codigo', mensaje: 'El código venció. Pide otro.' }]);
    if (r.intentos >= 5) return err(422, 'DEMASIADOS_INTENTOS', MSJ_INTENTOS, [{ campo: 'codigo', mensaje: MSJ_INTENTOS }]);
    if (r.codigo !== b.codigo) {
      r.intentos += 1;
      if (r.intentos >= 5) return err(422, 'DEMASIADOS_INTENTOS', MSJ_INTENTOS, [{ campo: 'codigo', mensaje: MSJ_INTENTOS }]);
      const m = `El código no es correcto. Te quedan ${5 - r.intentos} intentos.`;
      return err(422, 'CODIGO_INCORRECTO', m, [{ campo: 'codigo', mensaje: m }]);
    }
    const t = `rec-${Math.random().toString(36).slice(2)}`;
    tokens.set(t, `${b.tipoCuenta}:${b.usuario}`);
    recuperaciones.delete(`${b.tipoCuenta}:${b.usuario}`);
    return ok({ tokenRestablecer: t, expiraEn: isoLima(ahoraLima().add(15, 'minute')) });
  }),
  http.post('/api/auth/recuperar/restablecer', async ({ request }) => {
    await delay(400);
    const b = (await request.json()) as { tokenRestablecer: string; nueva: string; confirmacion: string };
    if (!tokens.has(b.tokenRestablecer)) return err(422, 'TOKEN_VENCIDO', 'El permiso para cambiar la contraseña venció. Empieza de nuevo.');
    if (b.nueva !== b.confirmacion) return err(400, 'VALIDACION', 'Revisa los datos marcados.', [{ campo: 'confirmacion', mensaje: 'Las contraseñas no coinciden.' }]);
    const [tipo, usuario] = (tokens.get(b.tokenRestablecer) ?? '').split(':');
    if (tipo === 'CLIENTE') bd.cuentas = bd.cuentas.map((c) => (c.dni === usuario || c.correo === usuario ? { ...c, contrasena: b.nueva } : c));
    tokens.delete(b.tokenRestablecer);
    guardar();
    return new HttpResponse(null, { status: 204 });
  }),
  // ---------- WhatsApp con tarjeta ilustrada (adjunto IMAGEN) si el back aún no la manda ----------
  http.get('/api/admin/notificaciones/:id', async ({ request }) => {
    const r = await fetch(bypass(request));
    if (!r.ok) return r;
    const n = (await r.json()) as { canal: string; evento: string; adjuntos: Array<{ tipo: string }> };
    if (n.canal !== 'WHATSAPP' || n.adjuntos.some((a) => a.tipo === 'IMAGEN')) return ok(n);
    const archivo = n.evento.toLowerCase().replace(/[_.]/g, '-').replace(/^recordatorio.*/, 'recordatorio-futbol');
    return ok({ ...n, adjuntos: [{ tipo: 'IMAGEN', referencia: archivo, nombreArchivo: `${archivo}.jpg`, estado: 'ADJUNTADO', detalle: null }, ...n.adjuntos] });
  }),
  // ---------- correo (SMTP) ----------
  http.get('/api/admin/correo', () => ok(sinClaveCorreo(bd.correo ?? correoInicial()))),
  http.put('/api/admin/correo', async ({ request }) => {
    await delay(400);
    const b = (await request.json()) as SolicitudCorreo;
    const clave = b.clave || bd.correo?.clave || '';
    bd.correo = { configurado: true, origen: 'PANEL', host: b.host, puerto: b.puerto, usuario: b.usuario, remitente: b.remitente, nombreRemitente: b.nombreRemitente, seguridad: b.seguridad, tieneClave: Boolean(clave), clave };
    guardar();
    return ok(sinClaveCorreo(bd.correo));
  }),
  http.post('/api/admin/correo/prueba', async ({ request }) => {
    await delay(1200);
    const b = (await request.json()) as { destino: string };
    const c = bd.correo;
    if (!c?.configurado) return ok({ destino: b.destino, enviado: false, simulado: true, estado: 'SIMULADO', mensaje: 'Sin servidor de correo.' });
    if (!c.host?.endsWith('hostinger.com')) return err(502, 'CORREO_NO_ENVIADO', `No se pudo conectar con ${c.host ?? ''}:${c.puerto ?? ''}. Revisa el servidor y el puerto.`);
    return ok({ destino: b.destino, enviado: true, simulado: false, estado: 'ENVIADO' });
  }),
  // ---------- administradores: celular ----------
  http.get('/api/auth/yo', async ({ request }) => {
    const r = await fetch(bypass(request));
    if (!r.ok) return r;
    const u = (await r.json()) as { id: number; correo: string; telefono?: string | null };
    const extra = bd.telefonos ?? {};
    return ok({ ...u, telefono: u.telefono ?? extra[`u${u.id}`] ?? null, correo: extra[`c${u.id}`] ?? u.correo });
  }),
  http.put('/api/auth/yo', async ({ request }) => {
    const b = (await request.json()) as { telefono: string | null; correo: string };
    const id = idPanel();
    bd.telefonos = { ...(bd.telefonos ?? {}), [`u${id}`]: b.telefono, yo: b.telefono, [`c${id}`]: b.correo };
    guardar();
    const r = await fetch(bypass(new Request(new URL('/api/auth/yo', request.url), { headers: request.headers })));
    const u = r.ok ? ((await r.json()) as Record<string, unknown>) : {};
    return ok({ ...u, telefono: b.telefono, correo: b.correo });
  }),
  http.get('/api/admin/usuarios', async ({ request }) => {
    const r = await fetch(bypass(request));
    if (!r.ok) return r;
    const p = (await r.json()) as { contenido: Array<{ id: number; telefono?: string | null }> };
    return ok({ ...p, contenido: p.contenido.map((u) => ({ ...u, telefono: u.telefono ?? bd.telefonos?.[`u${u.id}`] ?? null })) });
  }),
  http.post('/api/admin/usuarios', async ({ request }) => {
    const b = (await request.clone().json()) as { telefono?: string | null };
    const r = await fetch(bypass(request));
    if (!r.ok) return r;
    const u = (await r.json()) as { id: number };
    bd.telefonos = { ...(bd.telefonos ?? {}), [`u${u.id}`]: b.telefono ?? null };
    guardar();
    return ok({ ...u, telefono: b.telefono ?? null }, 201);
  }),
  http.put('/api/admin/usuarios/:id', async ({ request, params }) => {
    const b = (await request.clone().json()) as { telefono?: string | null };
    const r = await fetch(bypass(request));
    if (!r.ok) return r;
    const u = (await r.json()) as { id: number };
    bd.telefonos = { ...(bd.telefonos ?? {}), [`u${String(params.id)}`]: b.telefono ?? null };
    guardar();
    return ok({ ...u, telefono: b.telefono ?? null });
  }),

  // ---------- calendario ----------
  http.get('/api/admin/calendario', ({ request }) => {
    const anio = new URL(request.url).searchParams.get('anio') ?? hoyLima().slice(0, 4);
    return ok(bd.calendario.filter((d) => d.fecha.startsWith(anio)).sort((a, b) => a.fecha.localeCompare(b.fecha)));
  }),
  http.post('/api/admin/calendario/feriados-peru', ({ request }) => {
    const anio = new URL(request.url).searchParams.get('anio') ?? hoyLima().slice(0, 4);
    let creados = 0;
    for (const [md, n] of FERIADOS)
      if (!bd.calendario.some((d) => d.fecha === `${anio}-${md}`)) {
        bd.calendario.push({ id: bd.sec++, fecha: `${anio}-${md}`, tipo: 'FERIADO', nombre: n, canchas: null });
        creados += 1;
      }
    guardar();
    return ok({ creados, existentes: FERIADOS.length - creados });
  }),
  http.post('/api/admin/calendario', async ({ request }) => {
    const b = (await request.json()) as DiaEspecialAdmin;
    if (bd.calendario.some((d) => d.fecha === b.fecha)) return err(409, 'DIA_ESPECIAL_DUPLICADO', 'Esa fecha ya está en el calendario.', [{ campo: 'fecha', mensaje: 'Esa fecha ya está en el calendario.' }]);
    const d = { ...b, id: bd.sec++, canchas: b.canchas?.length ? b.canchas : null };
    bd.calendario.push(d);
    guardar();
    return ok(d, 201);
  }),
  http.put('/api/admin/calendario/:id', async ({ params, request }) => {
    const b = (await request.json()) as DiaEspecialAdmin;
    bd.calendario = bd.calendario.map((d) => (d.id === Number(params.id) ? { ...d, ...b, id: d.id } : d));
    guardar();
    return ok(bd.calendario.find((d) => d.id === Number(params.id)));
  }),
  http.delete('/api/admin/calendario/:id', ({ params }) => {
    bd.calendario = bd.calendario.filter((d) => d.id !== Number(params.id));
    guardar();
    return new HttpResponse(null, { status: 204 });
  }),
  http.get('/api/catalogo/calendario', ({ request }) => {
    const u = new URL(request.url).searchParams;
    return ok(bd.calendario.filter((d) => d.fecha >= (u.get('desde') ?? '') && d.fecha <= (u.get('hasta') ?? '9999')));
  }),
  // ---------- horario por cancha ----------
  http.get('/api/admin/canchas/:id/horario', ({ params }) => ok(horario(String(params.id)))),
  http.put('/api/admin/canchas/:id/horario', async ({ params, request }) => {
    await delay(300);
    bd.horarios[String(params.id)] = (await request.json()) as HorarioAdmin;
    guardar();
    return ok(horario(String(params.id)));
  }),
  // Detalle público: agrega horario y feriado de la v2.
  http.get('/api/catalogo/canchas/:idOSlug', async ({ request, params }) => {
    if (String(params.idOSlug).includes('/')) return undefined;
    const r = await fetch(bypass(request));
    if (!r.ok) return r;
    const c = (await r.json()) as { id: number };
    const h = horario(String(c.id));
    return ok({ ...c, horario: { duracionTurnoMin: h.duracionTurnoMin, inicioNoche: h.inicioNoche, dias: h.dias, texto: textoSemana(h.dias) }, feriado: h.feriado });
  }),
  http.get('/api/admin/canchas', async ({ request }) => {
    const r = await fetch(bypass(request));
    if (!r.ok) return r;
    const lista = (await r.json()) as Array<{ id: number }>;
    return ok(lista.map((c) => ({ ...c, horario: { ...horario(String(c.id)), texto: textoSemana(horario(String(c.id)).dias) }, feriado: horario(String(c.id)).feriado })));
  }),
  // ---------- reseñas de una cancha (v1 + las del complejo) ----------
  http.get('/api/admin/canchas/:id/resenas', async ({ params, request }) => {
    const u = new URL(`/api/admin/resenas?idCancha=${params.id}&size=100`, request.url);
    const r = await fetch(bypass(new Request(u, { headers: request.headers })));
    const v1 = r.ok ? ((await r.json()) as { contenido: ResenaAdmin[] }).contenido.map((x) => ({ ...x, origen: 'CLIENTE' as const, fecha: x.creadoEn })) : [];
    return ok([...bd.resenas.filter((x) => x.idCancha === Number(params.id)), ...v1]);
  }),
  http.post('/api/admin/canchas/:id/resenas', async ({ params, request }) => {
    const b = (await request.json()) as { autor: string; puntuacion: number; comentario: string; fecha: string };
    const n: ResenaAdmin = { id: 900000 + bd.sec++, idCancha: Number(params.id), cancha: '', codigoReserva: null, origen: 'COMPLEJO', autor: b.autor, puntuacion: b.puntuacion, comentario: b.comentario, fecha: b.fecha, estado: 'PUBLICADA', respuesta: null, respondidoEn: null, creadoEn: `${b.fecha}T12:00:00` };
    bd.resenas.push(n);
    guardar();
    return ok(n, 201);
  }),
  http.all('/api/admin/resenas/:id', async ({ params, request }) => {
    const id = Number(params.id);
    if (id < 900000) return undefined;
    if (request.method === 'DELETE') bd.resenas = bd.resenas.filter((x) => x.id !== id);
    else {
      const b = (await request.json()) as Partial<ResenaAdmin>;
      bd.resenas = bd.resenas.map((x) => (x.id === id ? { ...x, ...b } : x));
    }
    guardar();
    return request.method === 'DELETE' ? new HttpResponse(null, { status: 204 }) : ok(bd.resenas.find((x) => x.id === id));
  }),
  http.post('/api/admin/resenas/:id/respuesta', async ({ params, request }) => {
    const id = Number(params.id);
    if (id < 900000) return undefined;
    const b = (await request.json()) as { respuesta: string };
    bd.resenas = bd.resenas.map((x) => (x.id === id ? { ...x, respuesta: b.respuesta, respondidoEn: isoLima() } : x));
    guardar();
    return ok(bd.resenas.find((x) => x.id === id));
  }),
  // ---------- cuentas de clientes ----------
  http.post('/api/auth/clientes/registro', async ({ request }) => {
    await delay(400);
    const b = (await request.json()) as CuentaCliente & { contrasena: string };
    if (bd.cuentas.some((c) => c.dni === b.dni)) return err(409, 'DNI_REGISTRADO', 'Ese DNI ya tiene una cuenta. Ingresa con tu contraseña.', [{ campo: 'dni', mensaje: 'Ese DNI ya tiene una cuenta.' }]);
    if (bd.cuentas.some((c) => c.correo === b.correo)) return err(409, 'CORREO_REGISTRADO', 'Ese correo ya está en otra cuenta.', [{ campo: 'correo', mensaje: 'Ese correo ya está en otra cuenta.' }]);
    const c = { id: bd.sec++, dni: b.dni, nombres: b.nombres, apellidos: b.apellidos, correo: b.correo, telefono: b.telefono, contrasena: b.contrasena };
    bd.cuentas.push(c);
    guardar();
    return ok(sesion(sinClave(c)), 201);
  }),
  http.post('/api/auth/clientes/login', async ({ request }) => {
    await delay(400);
    const b = (await request.json()) as { usuario: string; contrasena: string };
    const c = bd.cuentas.find((x) => (x.dni === b.usuario || x.correo === b.usuario) && x.contrasena === b.contrasena);
    return c ? ok(sesion(sinClave(c))) : err(401, 'CREDENCIALES_INVALIDAS', 'DNI, correo o contraseña incorrectos.');
  }),
  http.get('/api/auth/clientes/yo', ({ request }) => {
    const c = bd.cuentas.find((x) => x.dni === dniDe(request));
    return c ? ok(sinClave(c)) : err(401, 'NO_AUTENTICADO', 'Tu sesión terminó.');
  }),
  http.put('/api/auth/clientes/yo', async ({ request }) => {
    const b = (await request.json()) as Partial<CuentaCliente>;
    bd.cuentas = bd.cuentas.map((x) => (x.dni === dniDe(request) ? { ...x, ...b, dni: x.dni } : x));
    guardar();
    const c = bd.cuentas.find((x) => x.dni === dniDe(request));
    return c ? ok(sinClave(c)) : err(401, 'NO_AUTENTICADO', 'Tu sesión terminó.');
  }),
  http.post('/api/auth/clientes/cambiar-contrasena', async ({ request }) => {
    const b = (await request.json()) as { actual: string; nueva: string };
    const c = bd.cuentas.find((x) => x.dni === dniDe(request));
    if (!c || c.contrasena !== b.actual) return err(400, 'VALIDACION', 'Revisa los datos marcados.', [{ campo: 'actual', mensaje: 'La contraseña actual no es correcta.' }]);
    c.contrasena = b.nueva;
    guardar();
    return new HttpResponse(null, { status: 204 });
  }),
  // «Mis reservas»: se arma con el historial del cliente del panel (necesita la sesión del panel abierta en este navegador).
  http.get('/api/cliente/reservas', async ({ request }) => {
    const dni = dniDe(request);
    if (!dni) return err(401, 'NO_AUTENTICADO', 'Tu sesión terminó.');
    const panel = (JSON.parse(localStorage.getItem('alc-sesion') ?? 'null') as { token?: string } | null)?.token;
    if (!panel) return ok({ contenido: [], pagina: 0, tamanio: 100, totalElementos: 0, totalPaginas: 0 });
    const r = await fetch(bypass(new Request(new URL(`/api/admin/clientes/${dni}`, request.url), { headers: { Authorization: `Bearer ${panel}` } })));
    const h = r.ok ? ((await r.json()) as { reservas: Array<Record<string, unknown> & { cancha: string; estado: string; fecha: string }> }).reservas : [];
    const contenido = h.map((x) => ({ ...x, cancha: { id: Number(/(\d)/.exec(x.cancha)?.[1] ?? 1), nombre: x.cancha, deporte: 'FUTBOL_5' }, puedeResenar: x.estado === 'CONFIRMADA' && x.fecha < hoyLima(), resenada: false }));
    return ok({ contenido, pagina: 0, tamanio: 100, totalElementos: contenido.length, totalPaginas: 1 });
  }),
];

export async function iniciarMocksV2() {
  bd = leer();
  const worker = setupWorker(...manejadores);
  await worker.start({ onUnhandledRequest: 'bypass', quiet: true, serviceWorker: { url: '/mockServiceWorker.js' } });
  console.info('[mock v2] endpoints nuevos simulados; el resto va al back real.');
}
