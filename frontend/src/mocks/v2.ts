/**
 * Modo HÍBRIDO de desarrollo (`npm run dev:v2`, VITE_MOCK=v2): MSW responde SOLO los endpoints nuevos de la v2
 * (SPEC_V2) mientras el back real todavía es v1; todo lo demás pasa al gateway. No entra al build ni se usa con el back v2.
 */
import { bypass, delay, http, HttpResponse } from 'msw';
import { setupWorker } from 'msw/browser';
import type { DiaEspecialAdmin, HorarioAdmin, ResenaAdmin } from '@/api/admin/tipos';
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

const manejadores = [
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
