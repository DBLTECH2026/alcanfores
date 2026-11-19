/**
 * Manejadores MSW: implementan las respuestas del SPEC §5 (portal, pasarela beta, login y tablero) sobre la BD simulada.
 * Reglas iguales al back: validaciones con los mismos mensajes, 409 TURNO_OCUPADO, 404 con mensaje único, 423 por bloqueo…
 */
import { delay, http, HttpResponse, type JsonBodyType } from 'msw';
import type { ErrorCampo, Turno } from '@/api/tipos';
import { MSJ } from '@/esquemas/comunes';
import {
  RE_CELULAR,
  RE_CODIGO_RESERVA,
  RE_DNI,
  RE_NOMBRE,
  RE_OPERACION,
  RE_USUARIO,
  detectarMarca,
  luhnValido,
  normalizarCelular,
  rucValido,
  tipoPorBytes,
} from '@/utilidades/documentos';
import { ahoraLima, deLima, hoyLima, isoLima, sumarDias } from '@/utilidades/fecha';
import { enmascararDni } from '@/utilidades/formato';
import {
  COMPLEJO,
  HORAS,
  USUARIOS,
  adelanto,
  decodificarTurno,
  esDemo409,
  guardarBd,
  horaFin,
  idTurno,
  nuevoCodigo,
  obtenerBd,
  ocupadoFicticio,
  precioTurno,
  type CanchaMock,
  type IntentoMock,
  type PagoMock,
  type ReservaMock,
} from './bd';

const API = '/api';
const espera = () => delay(120 + Math.floor(Math.random() * 220));

// ------------------------------ respuestas ------------------------------
function error(status: number, codigo: string, mensaje: string, ruta: string, errores: ErrorCampo[] = []) {
  return HttpResponse.json({ timestamp: isoLima(), status, codigo, mensaje, ruta, errores }, { status });
}
function validacion(ruta: string, errores: ErrorCampo[]) {
  return error(400, 'VALIDACION', 'Revisa los datos marcados.', ruta, errores);
}
const ok = (datos: JsonBodyType, status = 200) => HttpResponse.json(datos, { status });
const ruta = (req: Request) => new URL(req.url).pathname;

// ------------------------------ reglas ------------------------------
function expirarVencidas() {
  const bd = obtenerBd();
  const ahora = ahoraLima();
  let cambio = false;
  for (const r of bd.reservas) {
    if (r.estado === 'PENDIENTE' && r.venceEn && deLima(r.venceEn).isBefore(ahora)) {
      r.estado = 'CANCELADA';
      r.cancelacion = { canceladoPor: 'SISTEMA', motivo: 'Venció el plazo para pagar', fecha: isoLima(), devolucion: false, montoDevolucion: null };
      r.linea.push({ evento: 'CANCELADA', fecha: isoLima(), detalle: 'Venció el plazo para pagar' });
      bd.pagos.filter((p) => p.codigo === r.codigo && p.estado === 'PENDIENTE').forEach((p) => (p.estado = 'ANULADO'));
      cambio = true;
    }
  }
  for (const i of bd.intentos) {
    if ((i.estado === 'CREADO' || i.estado === 'REQUIERE_3DS') && deLima(i.expiraEn).isBefore(ahora)) {
      i.estado = 'EXPIRADO';
      cambio = true;
    }
  }
  if (cambio) guardarBd();
}

const vigente = (r: ReservaMock) => r.estado === 'PENDIENTE' || r.estado === 'CONFIRMADA';

function estadoTurno(c: CanchaMock, fecha: string, hora: string): Turno['estado'] {
  const bd = obtenerBd();
  const inicio = deLima(`${fecha}T${hora}:00`);
  if (inicio.isBefore(ahoraLima().add(30, 'minute'))) return 'PASADO';
  if (esDemo409(c.id, fecha, hora)) return 'DISPONIBLE';
  const id = idTurno(c.id, fecha, hora);
  if (bd.reservas.some((r) => r.idTurno === id && vigente(r))) return 'OCUPADO';
  if (bd.bloqueos.some((b) => b.idCancha === c.id && b.fecha === fecha && b.hora === hora)) return 'BLOQUEADO';
  if (ocupadoFicticio(c.id, fecha, hora)) return 'OCUPADO';
  return 'DISPONIBLE';
}

function turnosDe(c: CanchaMock, fecha: string): Turno[] {
  return HORAS.map((h) => ({
    idTurno: idTurno(c.id, fecha, h),
    horaInicio: h,
    horaFin: horaFin(h),
    precio: precioTurno(c, h),
    franja: h >= COMPLEJO.horario.inicioNoche ? ('NOCHE' as const) : ('DIA' as const),
    estado: estadoTurno(c, fecha, h),
  }));
}

function enRango(fecha: string): boolean {
  const hoy = hoyLima();
  return fecha >= hoy && fecha <= sumarDias(hoy, COMPLEJO.horario.diasVisibles);
}

function calificacion(idCancha: number) {
  const rs = obtenerBd().resenas.filter((r) => r.idCancha === idCancha && r.estado === 'PUBLICADA');
  const total = rs.length;
  const promedio = total ? Math.round((rs.reduce((a, r) => a + r.puntuacion, 0) / total) * 10) / 10 : 0;
  const distribucion = { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 } as Record<'1' | '2' | '3' | '4' | '5', number>;
  rs.forEach((r) => (distribucion[String(r.puntuacion) as '1'] += 1));
  return { promedio, total, distribucion };
}

function resumenCancha(c: CanchaMock) {
  const { promedio, total } = calificacion(c.id);
  const { fotos: _f, caracteristicas: _c, descripcionLarga: _d, medidas: _m, orden: _o, ...resto } = c;
  return { ...resto, calificacion: { promedio, total } };
}

function nombreCancha(id: number) {
  return obtenerBd().canchas.find((c) => c.id === id);
}

function detalleReserva(r: ReservaMock) {
  const c = nombreCancha(r.idCancha);
  const inicio = deLima(`${r.fecha}T${r.horaInicio}:00`);
  const ahora = ahoraLima();
  const yaReseno = obtenerBd().resenas.some((x) => x.codigoReserva === r.codigo);
  return {
    codigoReserva: r.codigo,
    estado: r.estado,
    estadoPago: r.estadoPago,
    venceEn: r.venceEn,
    cancha: { id: r.idCancha, nombre: c?.nombre ?? '', deporte: c?.deporte ?? 'FUTBOL_5' },
    fecha: r.fecha,
    horaInicio: r.horaInicio,
    horaFin: r.horaFin,
    montoTotal: r.montoTotal,
    montoAdelanto: r.montoAdelanto,
    cliente: { nombres: r.cliente.nombres, apellidos: r.cliente.apellidos, dniEnmascarado: enmascararDni(r.cliente.dni), telefono: r.cliente.telefono, correo: r.cliente.correo },
    asistencia: r.asistencia,
    montoPagado: r.montoPagado,
    saldo: Math.max(0, Math.round((r.montoTotal - r.montoPagado) * 100) / 100),
    cancelacion: r.cancelacion,
    linea: r.linea,
    puedeCancelar: vigente(r) && ahora.isBefore(inicio),
    puedeReenviarComprobante: r.estado === 'PENDIENTE' && (r.estadoPago === 'SIN_PAGO' || r.estadoPago === 'OBSERVADO'),
    puedeResenar: r.estado === 'CONFIRMADA' && (r.asistencia === 'ASISTIO' || ahora.isAfter(inicio)) && !yaReseno,
  };
}

function buscarReserva(codigo: string, dni: string | null): ReservaMock | null {
  const r = obtenerBd().reservas.find((x) => x.codigo === codigo.toUpperCase());
  return r && dni && r.cliente.dni === dni ? r : null;
}
const NO_ENCONTRADA = 'No encontramos una reserva con ese código y DNI.';

function emitirComprobante(r: ReservaMock): PagoMock['comprobante'] {
  const bd = obtenerBd();
  const serie = r.comprobante.tipo === 'FACTURA' ? 'F001' : 'B001';
  bd.correlativos[serie] = (bd.correlativos[serie] ?? 0) + 1;
  return { serieNumero: `${serie}-${String(bd.correlativos[serie]).padStart(8, '0')}`, tipo: r.comprobante.tipo };
}

function aprobarIntento(i: IntentoMock, metodo: string, ultimos4: string) {
  const bd = obtenerBd();
  const r = bd.reservas.find((x) => x.codigo === i.codigoReserva);
  i.estado = 'APROBADO';
  i.motivo = null;
  if (!r) return;
  const comp = emitirComprobante(r);
  bd.pagos.push({ id: bd.pagos.length + 1, codigo: r.codigo, metodo, canal: 'PASARELA', tipoPago: i.tipoPago, monto: i.monto, estado: 'VALIDADO', observacion: null, codigoOperacion: null, fecha: isoLima(), comprobante: comp });
  r.montoPagado = Math.round((r.montoPagado + i.monto) * 100) / 100;
  r.estado = 'CONFIRMADA';
  r.estadoPago = r.montoPagado >= r.montoTotal ? 'PAGADO_TOTAL' : 'ADELANTO_PAGADO';
  r.venceEn = null;
  r.linea.push(
    { evento: 'PAGO_VALIDADO', fecha: isoLima(), detalle: `${metodo === 'YAPE' ? 'Yape' : metodo} •••• ${ultimos4} · Alcanfores Pay` },
    { evento: 'CONFIRMADA', fecha: isoLima(), detalle: null },
    { evento: 'COMPROBANTE_EMITIDO', fecha: isoLima(), detalle: comp?.serieNumero ?? null },
  );
}

// ------------------------------ documentos ------------------------------
const PERSONAS: Record<string, [string, string, string]> = {
  '72345678': ['DIETRI', 'DIAZ', 'ASTO'],
  '45678912': ['JORGE LUIS', 'CHUCO', 'RAMIREZ'],
  '70112233': ['ROCIO', 'CHAVEZ', 'LOZANO'],
  '76543210': ['BRAYAN', 'QUISPE', 'HUAMAN'],
};
const POOL_N = ['ANA LUCIA', 'CARLOS', 'MARIA FERNANDA', 'JOSE', 'LUCIA', 'MIGUEL ANGEL', 'GABRIELA', 'RODRIGO', 'XIMENA', 'SEBASTIAN'];
const POOL_A = ['GARCIA', 'MENDOZA', 'ROJAS', 'FLORES', 'TORRES', 'SANCHEZ', 'CASTRO', 'VARGAS', 'RAMOS', 'HUAMAN'];

function persona(dni: string): [string, string, string] {
  const p = PERSONAS[dni];
  if (p) return p;
  const n = dni.split('').reduce((a, d) => a + Number(d), 0);
  return [POOL_N[n % POOL_N.length] ?? 'ANA', POOL_A[(n * 3) % POOL_A.length] ?? 'GARCIA', POOL_A[(n * 7 + 1) % POOL_A.length] ?? 'ROJAS'];
}

// ------------------------------ PDF / XML mínimos ------------------------------
function pdfSimple(lineas: string[]): Uint8Array {
  const esc = (t: string) => t.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[()\\]/g, (m) => `\\${m}`);
  const contenido = `BT /F1 13 Tf 56 780 Td 18 TL ${lineas.map((l, i) => `${i ? 'T* ' : ''}(${esc(l)}) Tj`).join(' ')} ET`;
  const objs = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${contenido.length} >>\nstream\n${contenido}\nendstream`,
  ];
  let pdf = '%PDF-1.4\n';
  const offsets: number[] = [];
  objs.forEach((o, i) => {
    offsets.push(pdf.length);
    pdf += `${i + 1} 0 obj\n${o}\nendobj\n`;
  });
  const xref = pdf.length;
  pdf += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n${offsets.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`).join('')}`;
  pdf += `trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;
  return new TextEncoder().encode(pdf);
}
const pdf = (lineas: string[], nombre: string) =>
  new HttpResponse(pdfSimple(lineas), { headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': `inline; filename="${nombre}"` } });

// ------------------------------ sesión ------------------------------
const b64 = (o: object) => btoa(unescape(encodeURIComponent(JSON.stringify(o)))).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
function tokenPara(u: (typeof USUARIOS)[number]) {
  const exp = Math.floor(Date.now() / 1000) + 8 * 3600;
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ sub: String(u.id), usuario: u.usuario, nombre: `${u.nombres} ${u.apellidos}`, rol: u.rol, iat: Math.floor(Date.now() / 1000), exp })}.firma-simulada`;
}
function usuarioDeToken(req: Request) {
  const h = req.headers.get('Authorization') ?? '';
  const t = h.startsWith('Bearer ') ? h.slice(7) : '';
  try {
    const payload = JSON.parse(decodeURIComponent(escape(atob((t.split('.')[1] ?? '').replace(/-/g, '+').replace(/_/g, '/'))))) as { usuario: string; exp: number };
    if (payload.exp * 1000 < Date.now()) return null;
    return USUARIOS.find((u) => u.usuario === payload.usuario) ?? null;
  } catch {
    return null;
  }
}
const sinToken = (req: Request) => error(401, 'NO_AUTENTICADO', 'Tu sesión terminó. Ingresa de nuevo.', ruta(req));

// ------------------------------ manejadores ------------------------------
export const manejadores = [
  // ===== catálogo =====
  http.get(`${API}/catalogo/complejo`, async () => {
    await espera();
    return ok(COMPLEJO);
  }),
  http.get(`${API}/catalogo/canchas`, async () => {
    await espera();
    return ok(obtenerBd().canchas.sort((a, b) => a.orden - b.orden).map(resumenCancha));
  }),
  http.get(`${API}/catalogo/canchas/:idOSlug`, async ({ params, request }) => {
    await espera();
    const c = obtenerBd().canchas.find((x) => String(x.id) === params.idOSlug || x.slug === params.idOSlug);
    if (!c) return error(404, 'NO_ENCONTRADO', 'No encontramos esa cancha.', ruta(request));
    const { orden: _o, ...resto } = c;
    const cal = calificacion(c.id);
    return ok({ ...resto, calificacion: { promedio: cal.promedio, total: cal.total }, resumenResenas: cal });
  }),
  http.get(`${API}/catalogo/canchas/:id/resenas`, async ({ params, request }) => {
    await espera();
    const u = new URL(request.url);
    const page = Number(u.searchParams.get('page') ?? 0);
    const size = Math.min(50, Number(u.searchParams.get('size') ?? 5));
    const orden = u.searchParams.get('orden') ?? 'recientes';
    const todas = obtenerBd()
      .resenas.filter((r) => r.idCancha === Number(params.id) && r.estado === 'PUBLICADA')
      .sort((a, b) => (orden === 'mejor' ? b.puntuacion - a.puntuacion || b.fecha.localeCompare(a.fecha) : orden === 'peor' ? a.puntuacion - b.puntuacion || b.fecha.localeCompare(a.fecha) : b.fecha.localeCompare(a.fecha)));
    const contenido = todas.slice(page * size, page * size + size).map(({ idCancha: _i, codigoReserva: _c, estado: _e, ...r }) => r);
    return ok({ contenido, pagina: page, tamanio: size, totalElementos: todas.length, totalPaginas: Math.ceil(todas.length / size) });
  }),
  http.post(`${API}/catalogo/canchas/:id/resenas`, async ({ params, request }) => {
    await espera();
    const b = (await request.json()) as { codigoReserva?: string; dni?: string; puntuacion?: number; comentario?: string };
    const errores: ErrorCampo[] = [];
    if (!RE_CODIGO_RESERVA.test(String(b.codigoReserva ?? '').toUpperCase())) errores.push({ campo: 'codigoReserva', mensaje: MSJ.codigo });
    if (!RE_DNI.test(String(b.dni ?? ''))) errores.push({ campo: 'dni', mensaje: MSJ.dni });
    if (!Number.isInteger(b.puntuacion) || (b.puntuacion ?? 0) < 1 || (b.puntuacion ?? 0) > 5) errores.push({ campo: 'puntuacion', mensaje: MSJ.puntuacion });
    const com = String(b.comentario ?? '').trim();
    if (com.length < 10 || com.length > 500 || /(https?:|www\.)/i.test(com)) errores.push({ campo: 'comentario', mensaje: MSJ.resena });
    if (errores.length) return validacion(ruta(request), errores);
    const bd = obtenerBd();
    const r = buscarReserva(String(b.codigoReserva), String(b.dni));
    if (!r) return error(404, 'NO_ENCONTRADO', NO_ENCONTRADA, ruta(request));
    if (bd.resenas.some((x) => x.codigoReserva === r.codigo)) return error(409, 'RESENA_DUPLICADA', 'Ya calificaste con esta reserva. ¡Gracias!', ruta(request));
    const inicio = deLima(`${r.fecha}T${r.horaInicio}:00`);
    if (r.idCancha !== Number(params.id) || r.estado !== 'CONFIRMADA' || !(r.asistencia === 'ASISTIO' || ahoraLima().isAfter(inicio))) {
      return error(422, 'RESENA_NO_ELEGIBLE', r.idCancha !== Number(params.id) ? 'Esa reserva es de otra cancha.' : 'Podrás calificar cuando juegues tu partido con una reserva confirmada.', ruta(request));
    }
    const nueva = {
      id: bd.resenas.length + 1,
      idCancha: r.idCancha,
      codigoReserva: r.codigo,
      autor: `${r.cliente.nombres.split(' ')[0]} ${r.cliente.apellidos.charAt(0)}.`,
      puntuacion: Number(b.puntuacion),
      comentario: com,
      respuesta: null,
      fecha: isoLima(),
      verificada: true,
      estado: 'PUBLICADA' as const,
    };
    bd.resenas.push(nueva);
    guardarBd();
    const { idCancha: _i, codigoReserva: _c, estado: _e, ...publica } = nueva;
    return ok(publica, 201);
  }),

  // ===== disponibilidad y reservas =====
  http.get(`${API}/disponibilidad/calendario`, async ({ request }) => {
    await espera();
    expirarVencidas();
    const u = new URL(request.url);
    const desde = u.searchParams.get('desde') ?? hoyLima();
    const dias = Math.min(31, Number(u.searchParams.get('dias') ?? 14));
    const idCancha = u.searchParams.get('idCancha');
    const canchas = obtenerBd().canchas.filter((c) => !idCancha || c.id === Number(idCancha));
    return ok(
      Array.from({ length: dias }, (_, i) => {
        const fecha = sumarDias(desde, i);
        const turnos = canchas.flatMap((c) => turnosDe(c, fecha));
        return { fecha, libres: turnos.filter((t) => t.estado === 'DISPONIBLE').length, total: turnos.length };
      }),
    );
  }),
  http.get(`${API}/disponibilidad`, async ({ request }) => {
    await espera();
    expirarVencidas();
    const u = new URL(request.url);
    const fecha = u.searchParams.get('fecha') ?? hoyLima();
    if (!enRango(fecha)) return validacion(ruta(request), [{ campo: 'fecha', mensaje: MSJ.fecha }]);
    const idCancha = u.searchParams.get('idCancha');
    const canchas = obtenerBd().canchas.filter((c) => !idCancha || c.id === Number(idCancha));
    return ok({ fecha, canchas: canchas.map((c) => ({ idCancha: c.id, nombre: c.nombre, deporte: c.deporte, turnos: turnosDe(c, fecha) })) });
  }),
  http.post(`${API}/reservas`, async ({ request }) => {
    await delay(500);
    expirarVencidas();
    const b = (await request.json()) as { idTurno?: number; cliente?: Record<string, string>; metodoPago?: string; aceptaTerminos?: boolean; aceptaPrivacidad?: boolean };
    const c = b.cliente ?? {};
    const errores: ErrorCampo[] = [];
    const nom = (v: string | undefined) => {
      const t = String(v ?? '').trim();
      return t.length >= 2 && t.length <= 60 && RE_NOMBRE.test(t) && !/\s{2,}/.test(t);
    };
    if (!RE_DNI.test(String(c.dni ?? ''))) errores.push({ campo: 'cliente.dni', mensaje: MSJ.dni });
    if (!nom(c.nombres)) errores.push({ campo: 'cliente.nombres', mensaje: MSJ.nombre });
    if (!nom(c.apellidos)) errores.push({ campo: 'cliente.apellidos', mensaje: MSJ.nombre });
    if (!RE_CELULAR.test(normalizarCelular(String(c.telefono ?? '')))) errores.push({ campo: 'cliente.telefono', mensaje: MSJ.celular });
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(c.correo ?? '')) || String(c.correo).length > 100) errores.push({ campo: 'cliente.correo', mensaje: MSJ.correo });
    if (!['YAPE', 'PLIN', 'TARJETA', 'MERCADO_PAGO'].includes(String(b.metodoPago))) errores.push({ campo: 'metodoPago', mensaje: MSJ.metodo });
    if (b.aceptaTerminos !== true) errores.push({ campo: 'aceptaTerminos', mensaje: MSJ.terminos });
    if (b.aceptaPrivacidad !== true) errores.push({ campo: 'aceptaPrivacidad', mensaje: MSJ.terminos });
    const t = decodificarTurno(Number(b.idTurno));
    if (!t) errores.push({ campo: 'idTurno', mensaje: MSJ.turno });
    if (errores.length || !t) return validacion(ruta(request), errores);
    const bd = obtenerBd();
    const cancha = bd.canchas.find((x) => x.id === t.idCancha);
    if (!cancha || !enRango(t.fecha)) return validacion(ruta(request), [{ campo: 'idTurno', mensaje: MSJ.turno }]);
    const estado = estadoTurno(cancha, t.fecha, t.hora);
    // Demostración del 409: la Cancha 2 a las 9:00 p. m. de mañana «la toma otra persona» justo antes.
    const demo409 = esDemo409(cancha.id, t.fecha, t.hora);
    if (estado === 'OCUPADO' || demo409) return error(409, 'TURNO_OCUPADO', 'Ese turno acaba de ser reservado por otra persona. Elige otro horario.', ruta(request));
    if (estado !== 'DISPONIBLE') return error(422, 'TURNO_NO_DISPONIBLE', MSJ.turno, ruta(request));
    const total = precioTurno(cancha, t.hora);
    const r: ReservaMock = {
      codigo: nuevoCodigo(bd.reservas.map((x) => x.codigo)),
      idTurno: Number(b.idTurno),
      idCancha: cancha.id,
      fecha: t.fecha,
      horaInicio: t.hora,
      horaFin: horaFin(t.hora),
      cliente: { dni: String(c.dni), nombres: String(c.nombres).trim(), apellidos: String(c.apellidos).trim(), telefono: normalizarCelular(String(c.telefono)), correo: String(c.correo).trim() },
      estado: 'PENDIENTE',
      estadoPago: 'SIN_PAGO',
      asistencia: 'PENDIENTE',
      origen: 'WEB',
      metodo: String(b.metodoPago),
      montoTotal: total,
      montoAdelanto: adelanto(total),
      montoPagado: 0,
      venceEn: isoLima(ahoraLima().add(COMPLEJO.reglas.minutosParaPagar, 'minute')),
      fechaRegistro: isoLima(),
      linea: [{ evento: 'REGISTRADA', fecha: isoLima(), detalle: 'Reserva web' }],
      cancelacion: null,
      comprobante: { tipo: 'BOLETA' },
    };
    bd.reservas.push(r);
    guardarBd();
    const { asistencia: _a, montoPagado: _m, saldo: _s, cancelacion: _c, linea: _l, puedeCancelar: _p, puedeReenviarComprobante: _r, puedeResenar: _x, ...creada } = detalleReserva(r);
    return ok(creada, 201);
  }),
  http.get(`${API}/reservas/:codigo/constancia.pdf`, async ({ params, request }) => {
    const r = buscarReserva(String(params.codigo), new URL(request.url).searchParams.get('dni'));
    if (!r) return error(404, 'NO_ENCONTRADO', NO_ENCONTRADA, ruta(request));
    const c = nombreCancha(r.idCancha);
    return pdf(
      [
        'Complejo Deportivo Los Alcanfores',
        'CONSTANCIA DE RESERVA (modo simulado)',
        '',
        `Codigo: ${r.codigo}`,
        `Cancha: ${c?.nombre ?? ''} - ${c?.deporteTexto ?? ''}`,
        `Fecha: ${r.fecha}   Hora: ${r.horaInicio} - ${r.horaFin}`,
        `Cliente: ${r.cliente.nombres} ${r.cliente.apellidos} (DNI ${enmascararDni(r.cliente.dni)})`,
        `Total: S/ ${r.montoTotal.toFixed(2)}   Pagado: S/ ${r.montoPagado.toFixed(2)}`,
        `Estado: ${r.estado} / ${r.estadoPago}`,
        '',
        COMPLEJO.direccion,
        COMPLEJO.referencia,
      ],
      `constancia-${r.codigo}.pdf`,
    );
  }),
  http.get(`${API}/reservas/:codigo`, async ({ params, request }) => {
    await espera();
    expirarVencidas();
    const r = buscarReserva(String(params.codigo), new URL(request.url).searchParams.get('dni'));
    return r ? ok(detalleReserva(r)) : error(404, 'NO_ENCONTRADO', NO_ENCONTRADA, ruta(request));
  }),
  http.post(`${API}/reservas/:codigo/cancelar`, async ({ params, request }) => {
    await espera();
    const b = (await request.json()) as { dni?: string; motivo?: string };
    const motivo = String(b.motivo ?? '').trim();
    if (motivo.length < 10 || motivo.length > 255) return validacion(ruta(request), [{ campo: 'motivo', mensaje: MSJ.motivo }]);
    const r = buscarReserva(String(params.codigo), String(b.dni ?? ''));
    if (!r) return error(404, 'NO_ENCONTRADO', NO_ENCONTRADA, ruta(request));
    const inicio = deLima(`${r.fecha}T${r.horaInicio}:00`);
    if (!vigente(r)) return error(422, 'NO_CANCELABLE', 'Esta reserva ya está cancelada.', ruta(request));
    if (!ahoraLima().isBefore(inicio)) return error(422, 'NO_CANCELABLE', 'Ya no se puede cancelar: el horario empezó.', ruta(request));
    const devolucion = inicio.diff(ahoraLima(), 'minute') >= COMPLEJO.reglas.devolucionHoras * 60 && r.montoPagado > 0;
    const bd = obtenerBd();
    r.estado = 'CANCELADA';
    r.venceEn = null;
    r.cancelacion = { canceladoPor: 'CLIENTE', motivo, fecha: isoLima(), devolucion, montoDevolucion: devolucion ? r.montoPagado : null };
    r.linea.push({ evento: 'CANCELADA', fecha: isoLima(), detalle: motivo });
    for (const p of bd.pagos.filter((x) => x.codigo === r.codigo)) {
      if (p.estado === 'PENDIENTE') p.estado = 'ANULADO';
      if (devolucion && p.estado === 'VALIDADO') {
        p.estado = 'REEMBOLSADO';
        bd.correlativos.BC01 = (bd.correlativos.BC01 ?? 0) + 1;
        bd.pagos.push({ id: bd.pagos.length + 1, codigo: r.codigo, metodo: p.metodo, canal: p.canal, tipoPago: p.tipoPago, monto: p.monto, estado: 'REEMBOLSADO', observacion: 'Devolución por cancelación', codigoOperacion: null, fecha: isoLima(), comprobante: { serieNumero: `BC01-${String(bd.correlativos.BC01).padStart(8, '0')}`, tipo: 'NOTA_CREDITO' } });
      }
    }
    guardarBd();
    return ok(detalleReserva(r));
  }),

  // ===== documentos =====
  http.get(`${API}/documentos/dni/:dni`, async ({ params, request }) => {
    await delay(450);
    const dni = String(params.dni);
    if (!RE_DNI.test(dni)) return validacion(ruta(request), [{ campo: 'dni', mensaje: MSJ.dni }]);
    if (dni === '00000000') return error(404, 'DNI_NO_ENCONTRADO', 'No encontramos ese DNI en RENIEC.', ruta(request));
    if (dni === '11111111') return error(503, 'CONSULTA_NO_DISPONIBLE', 'La consulta a RENIEC no está disponible. Escribe tus datos a mano.', ruta(request));
    const [nombres, paterno, materno] = persona(dni);
    return ok({ dni, nombres, apellidoPaterno: paterno, apellidoMaterno: materno, apellidos: `${paterno} ${materno}`, fuente: 'RENIEC (simulado)' });
  }),
  http.get(`${API}/documentos/ruc/:ruc`, async ({ params, request }) => {
    await delay(450);
    const ruc = String(params.ruc);
    if (!rucValido(ruc)) return validacion(ruta(request), [{ campo: 'ruc', mensaje: MSJ.ruc }]);
    if (ruc === '20000000001') return ok({ ruc, razonSocial: 'LOS ALCANFORES SPORT E.I.R.L. (FICTICIA)', direccion: 'CAR. CENTRAL KM 11.5, SANTA CLARA, ATE, LIMA', estado: 'ACTIVO', condicion: 'HABIDO', fuente: 'SUNAT (simulado)' });
    return ok({ ruc, razonSocial: `INVERSIONES DEMO ${ruc.slice(-4)} S.A.C.`, direccion: 'AV. JAVIER PRADO ESTE 1234, LA MOLINA, LIMA', estado: 'ACTIVO', condicion: 'HABIDO', fuente: 'SUNAT (simulado)' });
  }),

  // ===== pagos =====
  http.get(`${API}/pagos/metodos`, async () => {
    await espera();
    return ok({
      yape: { numero: COMPLEJO.pagos.yapeNumero, titular: COMPLEJO.pagos.yapeTitular },
      plin: { numero: COMPLEJO.pagos.plinNumero },
      tarjetaBeta: true,
      mercadoPago: localStorage.getItem('alc-mock-mp') === '1',
      tarjetasPrueba: [
        { numero: '4111 1111 1111 1111', marca: 'Visa', resultado: 'Aprobada' },
        { numero: '5555 5555 5555 4444', marca: 'Mastercard', resultado: 'Aprobada' },
        { numero: '3782 822463 10005', marca: 'Amex', resultado: 'Aprobada' },
        { numero: '4000 0027 6000 3184', marca: 'Visa', resultado: 'Pide 3-D Secure' },
        { numero: '4000 0000 0000 0002', marca: 'Visa', resultado: 'Fondos insuficientes' },
        { numero: '4000 0000 0000 9995', marca: 'Visa', resultado: 'Tarjeta bloqueada' },
        { numero: '4000 0000 0000 0069', marca: 'Visa', resultado: 'Tarjeta vencida' },
      ],
      yapePrueba: { codigoAprueba: '123456', codigoRechaza: '000000' },
    });
  }),
  http.post(`${API}/pagos/comprobante`, async ({ request }) => {
    await delay(700);
    const fd = await request.formData();
    const codigo = String(fd.get('codigoReserva') ?? '');
    const dni = String(fd.get('dni') ?? '');
    const metodo = String(fd.get('metodo') ?? '');
    const op = String(fd.get('codigoOperacion') ?? '');
    const archivo = fd.get('archivo');
    const errores: ErrorCampo[] = [];
    if (!['YAPE', 'PLIN'].includes(metodo)) errores.push({ campo: 'metodo', mensaje: MSJ.metodo });
    if (!RE_OPERACION.test(op)) errores.push({ campo: 'codigoOperacion', mensaje: MSJ.operacion });
    if (!(archivo instanceof File) || archivo.size > 5 * 1024 * 1024 || !(await tipoPorBytes(archivo))) errores.push({ campo: 'archivo', mensaje: MSJ.imagen });
    if (errores.length) return validacion(ruta(request), errores);
    const r = buscarReserva(codigo, dni);
    if (!r) return error(404, 'NO_ENCONTRADO', NO_ENCONTRADA, ruta(request));
    if (r.estado !== 'PENDIENTE' || !['SIN_PAGO', 'OBSERVADO'].includes(r.estadoPago)) return error(409, 'PAGO_NO_PERMITIDO', 'Ya enviaste un comprobante: está en revisión.', ruta(request));
    const bd = obtenerBd();
    if (bd.pagos.some((p) => p.codigoOperacion === op && p.estado !== 'RECHAZADO')) return validacion(ruta(request), [{ campo: 'codigoOperacion', mensaje: 'Ese número de operación ya se usó en otro pago.' }]);
    const comp = fd.get('comprobante');
    if (comp) {
      try {
        r.comprobante = JSON.parse(typeof comp === 'string' ? comp : await comp.text()) as ReservaMock['comprobante'];
      } catch {
        /* boleta por defecto */
      }
    }
    const id = bd.pagos.length + 1;
    const monto = Math.max(0, r.montoAdelanto - r.montoPagado) || r.montoAdelanto;
    bd.pagos.push({ id, codigo: r.codigo, metodo, canal: 'COMPROBANTE', tipoPago: 'ADELANTO', monto, estado: 'PENDIENTE', observacion: null, codigoOperacion: op, fecha: isoLima(), comprobante: null });
    r.estadoPago = 'EN_REVISION';
    r.venceEn = null;
    r.linea.push({ evento: 'COMPROBANTE_RECIBIDO', fecha: isoLima(), detalle: `${metodo === 'YAPE' ? 'Yape' : 'Plin'} · op. ${op}` });
    guardarBd();
    return ok({ idPago: id, estado: 'PENDIENTE', mensaje: 'Recibimos tu comprobante. Lo validamos en minutos.' }, 201);
  }),
  http.get(`${API}/pagos/reserva/:codigo`, async ({ params, request }) => {
    await espera();
    const dni = new URL(request.url).searchParams.get('dni');
    const r = buscarReserva(String(params.codigo), dni);
    if (!r) return error(404, 'NO_ENCONTRADO', NO_ENCONTRADA, ruta(request));
    return ok(
      obtenerBd()
        .pagos.filter((p) => p.codigo === r.codigo)
        .map((p) => ({
          metodo: p.metodo,
          canal: p.canal,
          tipoPago: p.tipoPago,
          monto: p.monto,
          estado: p.estado,
          observacion: p.observacion,
          fecha: p.fecha,
          idPago: p.id,
          comprobante: p.comprobante
            ? {
                id: p.id,
                tipo: p.comprobante.tipo ?? 'BOLETA',
                serieNumero: p.comprobante.serieNumero,
                estado: 'EMITIDO',
                urlPdf: `/api/comprobantes/${p.comprobante.serieNumero}.pdf?codigo=${r.codigo}&dni=${dni}`,
                urlXml: `/api/comprobantes/${p.comprobante.serieNumero}.xml?codigo=${r.codigo}&dni=${dni}`,
              }
            : null,
          notaCredito: null,
        })),
    );
  }),
  http.get(`${API}/comprobantes/:archivo`, async ({ params, request }) => {
    const [serieNumero = '', ext = ''] = String(params.archivo).split(/\.(?=pdf$|xml$)/);
    const u = new URL(request.url);
    const r = buscarReserva(u.searchParams.get('codigo') ?? '', u.searchParams.get('dni'));
    const p = obtenerBd().pagos.find((x) => x.comprobante?.serieNumero === serieNumero);
    if (!r || !p || p.codigo !== r.codigo) return error(404, 'NO_ENCONTRADO', 'No encontramos ese comprobante.', ruta(request));
    const total = p.monto;
    const gravada = Math.round((total / 1.18) * 100) / 100;
    if (ext === 'xml') {
      const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<!-- UBL 2.1 simulado, sin firma digital (proyecto académico) -->\n<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2">\n  <ID>${serieNumero}</ID>\n  <IssueDate>${p.fecha.slice(0, 10)}</IssueDate>\n  <AccountingSupplierParty>20000000001</AccountingSupplierParty>\n  <AccountingCustomerParty>${r.cliente.dni}</AccountingCustomerParty>\n  <TaxTotal>${(total - gravada).toFixed(2)}</TaxTotal>\n  <PayableAmount currencyID="PEN">${total.toFixed(2)}</PayableAmount>\n</Invoice>\n`;
      return new HttpResponse(xml, { headers: { 'Content-Type': 'application/xml' } });
    }
    return pdf(
      [
        'Los Alcanfores Sport E.I.R.L. (ficticia) - RUC 20000000001',
        `${p.comprobante?.tipo === 'NOTA_CREDITO' ? 'NOTA DE CREDITO' : p.comprobante?.tipo === 'FACTURA' ? 'FACTURA' : 'BOLETA'} ELECTRONICA ${serieNumero} (simulada)`,
        '',
        `Cliente: ${r.cliente.nombres} ${r.cliente.apellidos} - DNI ${r.cliente.dni}`,
        `Reserva ${r.codigo} - ${r.fecha} ${r.horaInicio}`,
        `Op. gravada: S/ ${gravada.toFixed(2)}   IGV 18%: S/ ${(total - gravada).toFixed(2)}   Total: S/ ${total.toFixed(2)}`,
        'Estado SUNAT: ACEPTADO (SIMULADO)',
      ],
      `${serieNumero}.pdf`,
    );
  }),
  http.post(`${API}/pagos/pasarela/intentos`, async ({ request }) => {
    await espera();
    const b = (await request.json()) as { codigoReserva?: string; dni?: string; tipoPago?: 'ADELANTO' | 'TOTAL' };
    const r = buscarReserva(String(b.codigoReserva ?? ''), String(b.dni ?? ''));
    if (!r) return error(404, 'NO_ENCONTRADO', NO_ENCONTRADA, ruta(request));
    if (r.estado !== 'PENDIENTE') return error(409, 'PAGO_NO_PERMITIDO', 'Esta reserva ya no espera pagos en línea.', ruta(request));
    const bd = obtenerBd();
    const referencia = `ALP-${Array.from({ length: 10 }, () => 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'[Math.floor(Math.random() * 32)]).join('')}`;
    const tipoPago = b.tipoPago === 'TOTAL' ? 'TOTAL' : 'ADELANTO';
    const i: IntentoMock = { referencia, codigoReserva: r.codigo, tipoPago, proveedor: 'BETA', monto: tipoPago === 'TOTAL' ? r.montoTotal : r.montoAdelanto, estado: 'CREADO', motivo: null, expiraEn: isoLima(ahoraLima().add(15, 'minute')), consultas: 0 };
    bd.intentos.push(i);
    guardarBd();
    return ok({ referencia, urlCheckout: `/pasarela/${referencia}`, expiraEn: i.expiraEn }, 201);
  }),
  http.post(`${API}/pagos/mercadopago/preferencias`, async ({ request }) => {
    await espera();
    if (localStorage.getItem('alc-mock-mp') !== '1') return error(409, 'MP_NO_CONFIGURADO', 'Mercado Pago no está activo en este momento.', ruta(request));
    const b = (await request.json()) as { codigoReserva?: string; dni?: string; tipoPago?: 'ADELANTO' | 'TOTAL' };
    const r = buscarReserva(String(b.codigoReserva ?? ''), String(b.dni ?? ''));
    if (!r) return error(404, 'NO_ENCONTRADO', NO_ENCONTRADA, ruta(request));
    const bd = obtenerBd();
    const referencia = `ALP-MP${Date.now().toString(36).toUpperCase().slice(-8)}`;
    bd.intentos.push({ referencia, codigoReserva: r.codigo, tipoPago: b.tipoPago === 'TOTAL' ? 'TOTAL' : 'ADELANTO', proveedor: 'MERCADO_PAGO', monto: b.tipoPago === 'TOTAL' ? r.montoTotal : r.montoAdelanto, estado: 'CREADO', motivo: null, expiraEn: isoLima(ahoraLima().add(15, 'minute')), consultas: 0 });
    guardarBd();
    return ok({ referencia, initPoint: `/pago/resultado?ref=${referencia}` });
  }),
  http.get(`${API}/pagos/intentos/:ref/estado`, async ({ params, request }) => {
    await espera();
    expirarVencidas();
    const bd = obtenerBd();
    const i = bd.intentos.find((x) => x.referencia === params.ref);
    if (!i) return error(404, 'NO_ENCONTRADO', 'No encontramos ese pago.', ruta(request));
    i.consultas += 1;
    // Mercado Pago simulado: el webhook «llega» en la segunda consulta.
    if (i.proveedor === 'MERCADO_PAGO' && i.estado === 'CREADO' && i.consultas >= 2) aprobarIntento(i, 'MERCADO_PAGO', '0000');
    guardarBd();
    return ok({ referencia: i.referencia, proveedor: i.proveedor, estado: i.estado, tipoPago: i.tipoPago, monto: i.monto, codigoReserva: i.codigoReserva, motivoRechazo: i.motivo, expiraEn: i.expiraEn, idPago: null, comprobante: null });
  }),

  // ===== pasarela Alcanfores Pay (beta) =====
  http.get(`${API}/pasarela/intentos/:ref`, async ({ params, request }) => {
    await espera();
    expirarVencidas();
    const bd = obtenerBd();
    const i = bd.intentos.find((x) => x.referencia === params.ref);
    if (!i) return error(404, 'NO_ENCONTRADO', 'No encontramos ese pago.', ruta(request));
    const r = bd.reservas.find((x) => x.codigo === i.codigoReserva);
    const c = r ? nombreCancha(r.idCancha) : undefined;
    return ok({
      referencia: i.referencia,
      comercio: COMPLEJO.nombre,
      concepto: r ? `${i.tipoPago === 'TOTAL' ? 'Pago total' : 'Adelanto'} · ${c?.nombre ?? ''} · ${r.fecha.split('-').reverse().join('/')} ${r.horaInicio}` : 'Reserva',
      monto: i.monto,
      moneda: 'PEN',
      estado: i.estado,
      expiraEn: i.expiraEn,
      codigoReserva: i.codigoReserva,
    });
  }),
  http.post(`${API}/pasarela/intentos/:ref/tarjeta`, async ({ params, request }) => {
    await delay(900);
    expirarVencidas();
    const bd = obtenerBd();
    const i = bd.intentos.find((x) => x.referencia === params.ref);
    if (!i) return error(404, 'NO_ENCONTRADO', 'No encontramos ese pago.', ruta(request));
    if (i.estado === 'EXPIRADO') return error(409, 'INTENTO_EXPIRADO', 'El tiempo para pagar terminó.', ruta(request));
    if (i.estado === 'APROBADO') return error(409, 'INTENTO_APROBADO', 'Este pago ya fue aprobado.', ruta(request));
    const b = (await request.json()) as { numero?: string; titular?: string; mesVencimiento?: string; anioVencimiento?: string; cvv?: string; correo?: string };
    const numero = String(b.numero ?? '').replace(/\D/g, '');
    const marca = detectarMarca(numero);
    const errores: ErrorCampo[] = [];
    if (!luhnValido(numero) || !marca) errores.push({ campo: 'numero', mensaje: MSJ.tarjeta });
    if (!/^\d{3,4}$/.test(String(b.cvv ?? '')) || String(b.cvv).length !== (marca === 'AMEX' ? 4 : 3)) errores.push({ campo: 'cvv', mensaje: MSJ.cvv });
    if (String(b.titular ?? '').trim().length < 3) errores.push({ campo: 'titular', mensaje: MSJ.titular });
    if (errores.length) return validacion(ruta(request), errores);
    const ultimos4 = numero.slice(-4);
    const resp = (estado: string, extra: object = {}) => ok({ estado, marca, ultimos4, ...extra });
    switch (numero) {
      case '4111111111111111':
      case '5555555555554444':
      case '378282246310005':
        aprobarIntento(i, 'TARJETA', ultimos4);
        guardarBd();
        return resp('APROBADO', { codigoAutorizacion: String(100000 + Math.floor(Math.random() * 899999)) });
      case '4000002760003184':
        i.estado = 'REQUIERE_3DS';
        guardarBd();
        return resp('REQUIERE_3DS');
      default: {
        const motivo = numero === '4000000000000002' ? 'Fondos insuficientes.' : numero === '4000000000009995' ? 'Tarjeta bloqueada. Comunícate con tu banco.' : numero === '4000000000000069' ? 'Tarjeta vencida.' : 'En modo beta solo funcionan las tarjetas de prueba.';
        i.estado = 'CREADO';
        i.motivo = motivo;
        guardarBd();
        return resp('RECHAZADO', { motivo });
      }
    }
  }),
  http.post(`${API}/pasarela/intentos/:ref/3ds`, async ({ params, request }) => {
    await delay(800);
    const bd = obtenerBd();
    const i = bd.intentos.find((x) => x.referencia === params.ref);
    if (!i) return error(404, 'NO_ENCONTRADO', 'No encontramos ese pago.', ruta(request));
    if (i.estado !== 'REQUIERE_3DS') return error(409, 'SIN_3DS', 'Este pago no está esperando verificación.', ruta(request));
    const b = (await request.json()) as { codigo?: string };
    if (b.codigo === '123456') {
      aprobarIntento(i, 'TARJETA', '3184');
      guardarBd();
      return ok({ estado: 'APROBADO', codigoAutorizacion: String(100000 + Math.floor(Math.random() * 899999)), marca: 'VISA', ultimos4: '3184' });
    }
    i.estado = 'CREADO';
    i.motivo = 'No se pudo verificar tu identidad (3-D Secure).';
    guardarBd();
    return ok({ estado: 'RECHAZADO', motivo: i.motivo, marca: 'VISA', ultimos4: '3184' });
  }),
  http.post(`${API}/pasarela/intentos/:ref/yape`, async ({ params, request }) => {
    await delay(900);
    expirarVencidas();
    const bd = obtenerBd();
    const i = bd.intentos.find((x) => x.referencia === params.ref);
    if (!i) return error(404, 'NO_ENCONTRADO', 'No encontramos ese pago.', ruta(request));
    if (i.estado === 'EXPIRADO') return error(409, 'INTENTO_EXPIRADO', 'El tiempo para pagar terminó.', ruta(request));
    const b = (await request.json()) as { celular?: string; codigoAprobacion?: string };
    const cel = normalizarCelular(String(b.celular ?? ''));
    const errores: ErrorCampo[] = [];
    if (!RE_CELULAR.test(cel)) errores.push({ campo: 'celular', mensaje: MSJ.celular });
    if (!/^\d{6}$/.test(String(b.codigoAprobacion ?? ''))) errores.push({ campo: 'codigoAprobacion', mensaje: MSJ.codigoAprobacion });
    if (errores.length) return validacion(ruta(request), errores);
    if (b.codigoAprobacion === '123456') {
      aprobarIntento(i, 'YAPE', cel.slice(-4));
      guardarBd();
      return ok({ estado: 'APROBADO', codigoAutorizacion: String(100000 + Math.floor(Math.random() * 899999)), marca: 'YAPE', ultimos4: cel.slice(-4) });
    }
    i.motivo = b.codigoAprobacion === '000000' ? 'Saldo insuficiente en Yape.' : 'Código de aprobación inválido o vencido.';
    guardarBd();
    return ok({ estado: 'RECHAZADO', motivo: i.motivo, marca: 'YAPE', ultimos4: cel.slice(-4) });
  }),

  // ===== auth =====
  http.post(`${API}/auth/login`, async ({ request }) => {
    await delay(500);
    const b = (await request.json()) as { usuario?: string; contrasena?: string };
    const usuario = String(b.usuario ?? '').trim().toLowerCase();
    if (!RE_USUARIO.test(usuario)) return validacion(ruta(request), [{ campo: 'usuario', mensaje: MSJ.usuario }]);
    const bd = obtenerBd();
    const estado = (bd.login[usuario] ??= { fallidos: 0, bloqueadoHasta: null });
    if (estado.bloqueadoHasta && deLima(estado.bloqueadoHasta).isAfter(ahoraLima())) {
      const min = Math.max(1, deLima(estado.bloqueadoHasta).diff(ahoraLima(), 'minute') + 1);
      return error(423, 'USUARIO_BLOQUEADO', `Demasiados intentos. Vuelve a intentar en ${min} min.`, ruta(request));
    }
    const u = USUARIOS.find((x) => x.usuario === usuario);
    if (!u || bd.claves[usuario] !== b.contrasena) {
      estado.fallidos += 1;
      if (estado.fallidos >= 5) {
        estado.fallidos = 0;
        estado.bloqueadoHasta = isoLima(ahoraLima().add(15, 'minute'));
        guardarBd();
        return error(423, 'USUARIO_BLOQUEADO', 'Demasiados intentos. Vuelve a intentar en 15 min.', ruta(request));
      }
      guardarBd();
      const quedan = 5 - estado.fallidos;
      return error(401, 'CREDENCIALES_INVALIDAS', `Usuario o contraseña incorrectos. ${quedan === 1 ? 'Te queda 1 intento.' : `Te quedan ${quedan} intentos.`}`, ruta(request));
    }
    estado.fallidos = 0;
    estado.bloqueadoHasta = null;
    guardarBd();
    return ok({
      token: tokenPara(u),
      tipo: 'Bearer',
      expiraEn: isoLima(ahoraLima().add(8, 'hour')),
      usuario: { id: u.id, nombres: u.nombres, apellidos: u.apellidos, usuario: u.usuario, correo: u.correo, rol: u.rol },
    });
  }),
  http.get(`${API}/auth/yo`, async ({ request }) => {
    const u = usuarioDeToken(request);
    if (!u) return sinToken(request);
    return ok({ id: u.id, nombres: u.nombres, apellidos: u.apellidos, usuario: u.usuario, correo: u.correo, rol: u.rol });
  }),
  http.post(`${API}/auth/cambiar-contrasena`, async ({ request }) => {
    await espera();
    const u = usuarioDeToken(request);
    if (!u) return sinToken(request);
    const b = (await request.json()) as { actual?: string; nueva?: string; confirmacion?: string };
    const bd = obtenerBd();
    if (bd.claves[u.usuario] !== b.actual) return validacion(ruta(request), [{ campo: 'actual', mensaje: 'La contraseña actual no es correcta.' }]);
    bd.claves[u.usuario] = String(b.nueva);
    guardarBd();
    return new HttpResponse(null, { status: 204 });
  }),

  // ===== panel: tablero =====
  http.get(`${API}/admin/dashboard`, async ({ request }) => {
    await espera();
    if (!usuarioDeToken(request)) return sinToken(request);
    expirarVencidas();
    const bd = obtenerBd();
    const hoy = hoyLima();
    const deHoy = bd.reservas.filter((r) => r.fecha === hoy && r.estado !== 'CANCELADA');
    const porValidar = bd.pagos.filter((p) => p.estado === 'PENDIENTE').length;
    const ocupados = bd.canchas.flatMap((c) => turnosDe(c, hoy)).filter((t) => t.estado === 'OCUPADO').length;
    const total = bd.canchas.length * HORAS.length;
    const mes = bd.reservas.filter((r) => r.fecha.slice(0, 7) === hoy.slice(0, 7) && r.asistencia !== 'PENDIENTE');
    const ahora = ahoraLima();
    const proximas = bd.reservas
      .filter((r) => vigente(r) && deLima(`${r.fecha}T${r.horaInicio}:00`).isAfter(ahora))
      .sort((a, b) => `${a.fecha}${a.horaInicio}`.localeCompare(`${b.fecha}${b.horaInicio}`))
      .slice(0, 5)
      .map((r) => ({ codigoReserva: r.codigo, cancha: nombreCancha(r.idCancha)?.nombre ?? '', fecha: r.fecha, horaInicio: r.horaInicio, horaFin: r.horaFin, cliente: `${r.cliente.nombres} ${r.cliente.apellidos.split(' ')[0]}`, estado: r.estado, estadoPago: r.estadoPago }));
    const alertas = bd.reservas
      .filter((r) => r.estado === 'PENDIENTE' && (r.estadoPago === 'EN_REVISION' || r.estadoPago === 'OBSERVADO'))
      .slice(0, 5)
      .map((r) => ({ tipo: r.estadoPago === 'EN_REVISION' ? 'URGENTE' : 'OBSERVADO', mensaje: r.estadoPago === 'EN_REVISION' ? `Comprobante por validar · ${nombreCancha(r.idCancha)?.nombre} ${r.horaInicio}` : `Comprobante observado · espera reenvío`, codigoReserva: r.codigo }));
    return ok({
      fecha: hoy,
      kpis: {
        reservasHoy: deHoy.length,
        porValidar,
        confirmadasHoy: deHoy.filter((r) => r.estado === 'CONFIRMADA').length,
        ocupacionHoyPct: Math.round((ocupados / total) * 100),
        noShowMesPct: mes.length ? Math.round((mes.filter((r) => r.asistencia === 'NO_ASISTIO').length / mes.length) * 100) : 0,
        reservasSemana: bd.reservas.filter((r) => r.fecha >= hoy && r.fecha <= sumarDias(hoy, 6) && r.estado !== 'CANCELADA').length,
      },
      proximas,
      agenda: [],
      alertas,
    });
  }),
  // Resto del panel: aún sin simular (las pantallas las construye el siguiente agente).
  http.all(`${API}/admin/*`, async ({ request }) => {
    if (!usuarioDeToken(request)) return sinToken(request);
    return error(503, 'SERVICIO_NO_DISPONIBLE', 'Esta parte del panel aún no tiene datos simulados.', ruta(request));
  }),
];
