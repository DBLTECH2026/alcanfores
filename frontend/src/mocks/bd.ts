/**
 * Base de datos SIMULADA para desarrollo sin back-end (VITE_MOCK=true).
 * Coherente con la semilla del SPEC: 4 canchas, reseñas, turnos de hoy + 14 días y reservas de ejemplo.
 * Se guarda en localStorage («alc-mock-bd») y se regenera cada día (las fechas son relativas a hoy).
 */
import type { CanchaDetalle, Cancelacion, Deporte, EventoLinea, Resena } from '@/api/tipos';
import { ahoraLima, deLima, hoyLima, isoLima, sumarDias } from '@/utilidades/fecha';

export interface CanchaMock extends Omit<CanchaDetalle, 'calificacion' | 'resumenResenas'> {
  orden: number;
}
export interface ResenaMock extends Resena {
  idCancha: number;
  codigoReserva: string;
  estado: 'PUBLICADA' | 'OCULTA';
}
export interface ClienteMock {
  dni: string;
  nombres: string;
  apellidos: string;
  telefono: string;
  correo: string;
}
export interface ReservaMock {
  codigo: string;
  idTurno: number;
  idCancha: number;
  fecha: string;
  horaInicio: string;
  horaFin: string;
  cliente: ClienteMock;
  estado: 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA';
  estadoPago: 'SIN_PAGO' | 'EN_REVISION' | 'OBSERVADO' | 'ADELANTO_PAGADO' | 'PAGADO_TOTAL';
  asistencia: 'PENDIENTE' | 'ASISTIO' | 'NO_ASISTIO';
  origen: 'WEB' | 'PRESENCIAL' | 'TELEFONICA';
  metodo: string;
  montoTotal: number;
  montoAdelanto: number;
  montoPagado: number;
  venceEn: string | null;
  fechaRegistro: string;
  linea: EventoLinea[];
  cancelacion: Cancelacion | null;
  comprobante: { tipo: 'BOLETA' | 'FACTURA'; ruc?: string; razonSocial?: string; direccionFiscal?: string };
}
export interface PagoMock {
  id: number;
  codigo: string;
  metodo: string;
  canal: 'COMPROBANTE' | 'PASARELA' | 'CAJA';
  tipoPago: 'ADELANTO' | 'TOTAL' | 'SALDO';
  monto: number;
  estado: 'PENDIENTE' | 'VALIDADO' | 'RECHAZADO' | 'ANULADO' | 'REEMBOLSADO';
  observacion: string | null;
  codigoOperacion: string | null;
  fecha: string;
  comprobante: { serieNumero: string; tipo: 'BOLETA' | 'FACTURA' | 'NOTA_CREDITO' } | null;
}
export interface IntentoMock {
  referencia: string;
  codigoReserva: string;
  tipoPago: 'ADELANTO' | 'TOTAL';
  proveedor: 'BETA' | 'MERCADO_PAGO';
  monto: number;
  estado: 'CREADO' | 'REQUIERE_3DS' | 'APROBADO' | 'RECHAZADO' | 'EXPIRADO';
  motivo: string | null;
  expiraEn: string;
  consultas: number;
}
export interface BdMock {
  version: number;
  dia: string;
  canchas: CanchaMock[];
  resenas: ResenaMock[];
  bloqueos: Array<{ idCancha: number; fecha: string; hora: string; motivo: string }>;
  reservas: ReservaMock[];
  pagos: PagoMock[];
  intentos: IntentoMock[];
  correlativos: Record<string, number>;
  login: Record<string, { fallidos: number; bloqueadoHasta: string | null }>;
  claves: Record<string, string>;
}

const VERSION = 4;
const CLAVE = 'alc-mock-bd';

export const COMPLEJO = {
  nombre: 'Complejo Deportivo Los Alcanfores',
  lema: 'Reserva tu cancha. Juega cuando quieras.',
  direccion: 'Carretera Central km 11.5, Santa Clara, Ate',
  referencia: 'Paradero Caseta, a dos cuadras de la UTP Santa Clara',
  whatsapp: '+51 914 995 187',
  correo: 'informes.alcanfores@gmail.com',
  mapaUrl: 'https://www.google.com/maps/search/?api=1&query=-12.0262,-76.8918',
  latitud: -12.0262,
  longitud: -76.8918,
  horario: { primerTurno: '08:00', ultimoTurno: '23:00', duracionMin: 60, inicioNoche: '18:00', diasVisibles: 14 },
  reglas: { adelantoPct: 50, devolucionHoras: 24, minutosParaPagar: 20, recordatorioHoras: 2 },
  pagos: { yapeNumero: '900 111 222', yapeTitular: 'Complejo Deportivo Alcanfores', plinNumero: '900 111 222' },
};

export const USUARIOS = [
  { id: 1, usuario: 'rhuaman', nombres: 'Rosa', apellidos: 'Huamán', correo: 'rosa.huaman@alcanfores.pe', rol: 'ADMIN' as const, clave: 'Alcanfores2026' },
  { id: 2, usuario: 'mtorres', nombres: 'Marco', apellidos: 'Torres', correo: 'marco.torres@alcanfores.pe', rol: 'RECEPCION' as const, clave: 'Recepcion2026' },
  { id: 3, usuario: 'docente', nombres: 'Docente', apellidos: 'UTP', correo: 'docente@utp.edu.pe', rol: 'ADMIN' as const, clave: 'UTP2026' },
];

const COMUNES = ['Camerinos con duchas', 'Estacionamiento', 'Iluminación LED', 'Explanada para eventos'];
const F = (n: string) => `/mock/fotos/${n}.webp`;

function canchasSemilla(): CanchaMock[] {
  const base = (
    id: number,
    slug: string,
    nombre: string,
    deporte: Deporte,
    deporteTexto: string,
    superficie: string,
    capacidad: number,
    precioDia: number,
    precioNoche: number,
    medidas: string,
    descripcion: string,
    descripcionLarga: string,
    extra: string[],
    fotos: Array<[string, string]>,
  ): CanchaMock => ({
    id,
    slug,
    nombre,
    deporte,
    deporteTexto,
    superficie,
    capacidad,
    techada: deporte !== 'MULTIUSO',
    iluminacionLed: true,
    descripcion,
    descripcionLarga,
    medidas,
    caracteristicas: [...extra, ...COMUNES],
    precioDia,
    precioNoche,
    fotoPrincipal: F(fotos[0]?.[0] ?? 'ARQ-03-canchas-techadas'),
    fotos: fotos.map(([archivo, leyenda], i) => ({ id: id * 10 + i, url: F(archivo), leyenda, principal: i === 0 })),
    orden: id,
  });
  return [
    base(1, 'cancha-1-futbol-5', 'Cancha 1', 'FUTBOL_5', 'Fútbol 5', 'Grass sintético', 10, 60, 80, '30 × 18 m', 'Techada, grass sintético de alto tránsito y luz LED. Ideal para pichangas de 5 contra 5.',
      'La Cancha 1 es la favorita para las pichangas de la semana: techada, con grass sintético de alto tránsito y caucho nuevo, arcos reglamentarios y mallas laterales para que la pelota no se vaya.\n\nLa iluminación LED deja la cancha como de día en los turnos de noche. A pocos pasos están los camerinos con duchas y el estacionamiento.',
      ['Techada', 'Grass sintético', 'Arcos reglamentarios'], [['ARQ-03-canchas-techadas', 'Vista general bajo techo'], ['IL-05-futbol', 'Arco y grass sintético'], ['ARQ-05-explanada-eventos', 'Explanada junto a la cancha']]),
    base(2, 'cancha-2-futbol-6', 'Cancha 2', 'FUTBOL_6', 'Fútbol 6', 'Grass sintético', 12, 70, 90, '36 × 20 m', 'Techada y un poco más amplia: perfecta para 6 contra 6 con cambios.',
      'Un poco más amplia que la Cancha 1, pensada para equipos de 6 con cambios. Techada, con grass sintético y mallas perimetrales.\n\nTiene banca para suplentes y acceso directo a los camerinos.',
      ['Techada', 'Grass sintético', 'Banca de suplentes'], [['IL-05-futbol', 'Arco de la Cancha 2'], ['ARQ-03-canchas-techadas', 'Techo y luces LED'], ['ARQ-05-explanada-eventos', 'Explanada para eventos']]),
    base(3, 'cancha-3-futbol-7', 'Cancha 3', 'FUTBOL_7', 'Fútbol 7', 'Grass sintético', 14, 90, 120, '45 × 25 m', 'La más grande del complejo: fútbol 7 techado para campeonatos y empresas.',
      'La cancha más grande del complejo, ideal para campeonatos internos, ligas de empresas y partidos de 7 contra 7.\n\nTechada, con grass sintético, tribuna lateral pequeña y sonido para eventos.',
      ['Techada', 'Grass sintético', 'Tribuna lateral'], [['ARQ-05-explanada-eventos', 'Cancha 3 y tribuna'], ['ARQ-03-canchas-techadas', 'Techo de la Cancha 3'], ['IL-05-futbol', 'Arco reglamentario']]),
    base(4, 'losa-multiusos', 'Losa multiusos', 'MULTIUSO', 'Vóley y básquet', 'Losa de concreto', 12, 40, 50, '28 × 15 m', 'Losa de concreto para vóley y básquet, con tableros y net reglamentarios.',
      'Losa de concreto pintada para vóley y básquet. Tiene tableros de básquet con aro reforzado y postes de vóley con net reglamentaria.\n\nAl aire libre, con iluminación LED para los turnos de noche.',
      ['Vóley y básquet', 'Tableros y net reglamentarios'], [['ARQ-04-losa-multiusos', 'Losa multiusos'], ['IL-06-voley', 'Net de vóley'], ['IL-07-basquet', 'Tablero de básquet']]),
  ];
}

// ------------------------------ utilidades ------------------------------
let semilla = 7;
function azar(): number {
  semilla = (semilla * 16807) % 2147483647;
  return semilla / 2147483647;
}
const ALFABETO = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
export function nuevoCodigo(existentes: string[]): string {
  let c = '';
  do {
    c = `ALC-${Array.from({ length: 6 }, () => ALFABETO[Math.floor(Math.random() * ALFABETO.length)]).join('')}`;
  } while (existentes.includes(c));
  return c;
}
export function idTurno(idCancha: number, fecha: string, hora: string): number {
  return Number(`${idCancha}${fecha.replace(/-/g, '')}${hora.slice(0, 2)}`);
}
export function decodificarTurno(id: number): { idCancha: number; fecha: string; hora: string } | null {
  const s = String(id);
  if (s.length !== 11) return null;
  const idCancha = Number(s[0]);
  const fecha = `${s.slice(1, 5)}-${s.slice(5, 7)}-${s.slice(7, 9)}`;
  return { idCancha, fecha, hora: `${s.slice(9, 11)}:00` };
}
export const HORAS = Array.from({ length: 15 }, (_, i) => `${String(8 + i).padStart(2, '0')}:00`);
export const horaFin = (h: string) => `${String((Number(h.slice(0, 2)) + 1) % 24).padStart(2, '0')}:00`;
export const adelanto = (total: number) => Math.round(total * COMPLEJO.reglas.adelantoPct) / 100;
export function precioTurno(c: CanchaMock, hora: string): number {
  return hora >= COMPLEJO.horario.inicioNoche ? c.precioNoche : c.precioDia;
}

/** Turno de demostración del 409: Cancha 2, mañana, 9:00 p. m. (se ve libre, pero «otra persona» lo toma). */
export function esDemo409(idCancha: number, fecha: string, hora: string): boolean {
  return idCancha === 2 && fecha === sumarDias(hoyLima(), 1) && hora === '21:00';
}

/** Ocupación «de otros clientes» determinística (~25 %) para que la grilla se vea real. */
export function ocupadoFicticio(idCancha: number, fecha: string, hora: string): boolean {
  const n = Number(fecha.replace(/-/g, '').slice(-4)) * 31 + idCancha * 17 + Number(hora.slice(0, 2)) * 7;
  return ((n * 2654435761) >>> 0) % 100 < 22;
}

// ------------------------------ semilla ------------------------------
const NOMBRES = ['Valeria', 'Renzo', 'Andrea', 'Kevin', 'Milagros', 'Erick', 'Fiorella', 'Diego', 'Marta', 'Albert', 'Camila', 'Luis'];
const APELLIDOS = ['Salazar Ríos', 'Quispe Mamani', 'Ramos Pérez', 'Huanca Flores', 'Castillo Vega', 'Vargas Soto', 'Torres León', 'Paredes Cruz'];

function semillaBd(): BdMock {
  semilla = 7;
  const hoy = hoyLima();
  const ahora = ahoraLima();
  const canchas = canchasSemilla();
  const reservas: ReservaMock[] = [];
  const pagos: PagoMock[] = [];
  const correlativos: Record<string, number> = { B001: 120, F001: 14, BC01: 2, FC01: 0 };
  let idPago = 1;

  const crear = (r: Partial<ReservaMock> & { codigo: string; idCancha: number; fecha: string; hora: string; cliente: ClienteMock; pago?: { metodo: string; estado: PagoMock['estado']; monto: number; tipo?: PagoMock['tipoPago']; observacion?: string; canal?: PagoMock['canal'] } }) => {
    const c = canchas.find((x) => x.id === r.idCancha);
    if (!c) return;
    const total = precioTurno(c, r.hora);
    const registro = isoLima(deLima(`${r.fecha}T${r.hora}:00`).subtract(2 + Math.floor(azar() * 48), 'hour'));
    const res: ReservaMock = {
      codigo: r.codigo,
      idTurno: idTurno(c.id, r.fecha, r.hora),
      idCancha: c.id,
      fecha: r.fecha,
      horaInicio: r.hora,
      horaFin: horaFin(r.hora),
      cliente: r.cliente,
      estado: r.estado ?? 'CONFIRMADA',
      estadoPago: r.estadoPago ?? 'ADELANTO_PAGADO',
      asistencia: r.asistencia ?? 'PENDIENTE',
      origen: r.origen ?? 'WEB',
      metodo: r.pago?.metodo ?? 'YAPE',
      montoTotal: total,
      montoAdelanto: adelanto(total),
      montoPagado: 0,
      venceEn: r.venceEn ?? null,
      fechaRegistro: registro,
      linea: [{ evento: 'REGISTRADA', fecha: registro, detalle: 'Reserva web' }],
      cancelacion: r.cancelacion ?? null,
      comprobante: { tipo: 'BOLETA' },
    };
    if (r.pago) {
      const fechaPago = isoLima(deLima(registro).add(8, 'minute'));
      const monto = r.pago.monto;
      let comp: PagoMock['comprobante'] = null;
      if (r.pago.estado === 'VALIDADO') {
        correlativos.B001 = (correlativos.B001 ?? 0) + 1;
        comp = { serieNumero: `B001-${String(correlativos.B001).padStart(8, '0')}`, tipo: 'BOLETA' };
        res.montoPagado += monto;
      }
      pagos.push({ id: idPago++, codigo: res.codigo, metodo: r.pago.metodo, canal: r.pago.canal ?? 'COMPROBANTE', tipoPago: r.pago.tipo ?? 'ADELANTO', monto, estado: r.pago.estado, observacion: r.pago.observacion ?? null, codigoOperacion: '01234567', fecha: fechaPago, comprobante: comp });
      res.linea.push({ evento: 'PAGO_REGISTRADO', fecha: fechaPago, detalle: `${r.pago.metodo === 'TARJETA' ? 'Tarjeta' : r.pago.metodo === 'YAPE' ? 'Yape' : 'Plin'} · S/ ${monto.toFixed(2)}` });
      if (r.pago.estado === 'VALIDADO') {
        const v = isoLima(deLima(fechaPago).add(12, 'minute'));
        res.linea.push({ evento: 'PAGO_VALIDADO', fecha: v, detalle: 'Validado por Rosa H.' }, { evento: 'CONFIRMADA', fecha: v, detalle: null });
      }
      if (r.pago.estado === 'RECHAZADO') res.linea.push({ evento: 'PAGO_RECHAZADO', fecha: isoLima(deLima(fechaPago).add(15, 'minute')), detalle: r.pago.observacion ?? null });
    }
    if (res.asistencia === 'ASISTIO') res.linea.push({ evento: 'ASISTIO', fecha: `${res.fecha}T${res.horaInicio}:05`, detalle: null });
    if (res.cancelacion) res.linea.push({ evento: 'CANCELADA', fecha: res.cancelacion.fecha, detalle: res.cancelacion.motivo });
    reservas.push(res);
  };

  // --- reservas de demostración (las usa el README) ---
  crear({ codigo: 'ALC-7K3Q9P', idCancha: 1, fecha: sumarDias(hoy, 2), hora: '20:00', cliente: { dni: '72345678', nombres: 'Dietri', apellidos: 'Díaz Asto', telefono: '987654321', correo: 'dietri.diaz@correo.pe' }, pago: { metodo: 'YAPE', estado: 'VALIDADO', monto: 40 } });
  crear({ codigo: 'ALC-H2P6WD', idCancha: 1, fecha: sumarDias(hoy, -1), hora: '19:00', asistencia: 'ASISTIO', estadoPago: 'PAGADO_TOTAL', cliente: { dni: '45678912', nombres: 'Jorge', apellidos: 'Chuco Ramírez', telefono: '912345678', correo: 'jorge.chuco@correo.pe' }, pago: { metodo: 'TARJETA', canal: 'PASARELA', estado: 'VALIDADO', monto: 80, tipo: 'TOTAL' } });
  crear({ codigo: 'ALC-M4R8TB', idCancha: 2, fecha: sumarDias(hoy, 1), hora: '19:00', estado: 'PENDIENTE', estadoPago: 'OBSERVADO', venceEn: isoLima(ahora.add(10, 'hour')), cliente: { dni: '70112233', nombres: 'Rocío', apellidos: 'Chávez Lozano', telefono: '956123478', correo: 'rocio.chavez@correo.pe' }, pago: { metodo: 'PLIN', estado: 'RECHAZADO', monto: 30, observacion: 'El monto de la captura (S/ 30.00) no coincide con el adelanto (S/ 45.00).' } });
  crear({ codigo: 'ALC-C4X7MS', idCancha: 3, fecha: sumarDias(hoy, 3), hora: '18:00', estado: 'PENDIENTE', estadoPago: 'EN_REVISION', cliente: { dni: '76543210', nombres: 'Brayan', apellidos: 'Quispe Huamán', telefono: '934567812', correo: 'brayan.quispe@correo.pe' }, pago: { metodo: 'YAPE', estado: 'PENDIENTE', monto: 60 } });
  crear({ codigo: 'ALC-W6R2NC', idCancha: 4, fecha: sumarDias(hoy, -3), hora: '17:00', estado: 'CANCELADA', estadoPago: 'ADELANTO_PAGADO', cliente: { dni: '41236587', nombres: 'Marta', apellidos: 'Quispe Rojas', telefono: '923456781', correo: 'marta.quispe@correo.pe' }, pago: { metodo: 'YAPE', estado: 'VALIDADO', monto: 20 }, cancelacion: { canceladoPor: 'CLIENTE', motivo: 'Se suspendió el partido por un viaje.', fecha: isoLima(deLima(`${sumarDias(hoy, -5)}T10:12:00`)), devolucion: true, montoDevolucion: 20 } });

  // --- reservas de relleno (hoy y próximos días) para el panel ---
  for (let d = -2; d <= 6; d += 1) {
    const fecha = sumarDias(hoy, d);
    for (const c of canchas) {
      for (const hora of ['19:00', '20:00', '21:00']) {
        if (azar() < 0.45) continue;
        const n = Math.floor(azar() * NOMBRES.length);
        const a = Math.floor(azar() * APELLIDOS.length);
        const dni = String(40000000 + Math.floor(azar() * 39999999));
        const id = idTurno(c.id, fecha, hora);
        if (reservas.some((r) => r.idTurno === id) || esDemo409(c.id, fecha, hora)) continue;
        const pasado = d < 0;
        const pendiente = !pasado && azar() < 0.25;
        crear({
          codigo: nuevoCodigo(reservas.map((r) => r.codigo)),
          idCancha: c.id,
          fecha,
          hora,
          estado: pendiente ? 'PENDIENTE' : 'CONFIRMADA',
          estadoPago: pendiente ? 'EN_REVISION' : 'ADELANTO_PAGADO',
          asistencia: pasado ? (azar() < 0.85 ? 'ASISTIO' : 'NO_ASISTIO') : 'PENDIENTE',
          origen: azar() < 0.2 ? 'PRESENCIAL' : 'WEB',
          cliente: { dni, nombres: NOMBRES[n] ?? 'Luis', apellidos: APELLIDOS[a] ?? 'Ramos Pérez', telefono: `9${String(10000000 + Math.floor(azar() * 89999999))}`, correo: `${(NOMBRES[n] ?? 'luis').toLowerCase()}.${dni.slice(-3)}@correo.pe` },
          pago: { metodo: azar() < 0.5 ? 'YAPE' : 'PLIN', estado: pendiente ? 'PENDIENTE' : 'VALIDADO', monto: adelanto(precioTurno(c, hora)) },
        });
      }
    }
  }

  // --- reseñas (autores ficticios) ---
  const R: Array<[number, string, number, string, string | null, number]> = [
    [1, 'Diego R.', 5, 'La cancha está impecable y el grass no quema. Las luces LED se ven muy bien de noche.', 'Gracias, Diego. ¡Los esperamos para la revancha!', 3],
    [1, 'Valeria M.', 5, 'Reservé por la web en dos minutos y me llegó la boleta por WhatsApp. Muy práctico.', null, 6],
    [1, 'Luis P.', 4, 'Buena cancha y bien ubicada, a dos cuadras de la UTP. Los camerinos podrían tener más ganchos.', 'Gracias por el dato, Luis. Ya estamos instalando más ganchos.', 10],
    [1, 'Andrea S.', 5, 'Jugamos todos los jueves con la oficina. Siempre puntuales con el turno.', null, 15],
    [1, 'Kevin T.', 4, 'Muy buena atención. Solo que el estacionamiento se llena los viernes.', null, 22],
    [2, 'Mariana C.', 5, 'Ideal para 6 contra 6, se juega cómodo y con cambios. Volveremos.', null, 4],
    [2, 'Renzo S.', 4, 'Cancha amplia y techada; cuando llueve igual se juega. Recomendada.', 'Gracias, Renzo. ¡Nos vemos pronto!', 9],
    [2, 'Fiorella H.', 3, 'La cancha bien, pero esperamos unos minutos porque el turno anterior se pasó.', 'Disculpa la espera, Fiorella. Reforzamos el control de horarios.', 18],
    [2, 'Erick Q.', 5, 'Muy buena iluminación y el grass en buen estado. Excelente para jugar de noche.', null, 25],
    [3, 'Camila V.', 5, 'Hicimos el campeonato de la empresa aquí y todo salió perfecto. La tribuna ayuda.', 'Gracias, Camila. Fue un gusto recibirlos.', 5],
    [3, 'Albert V.', 5, 'La mejor cancha de fútbol 7 de la zona. Amplia y techada.', null, 12],
    [3, 'Milagros H.', 4, 'Muy buena, aunque los fines de semana hay que reservar con anticipación.', null, 20],
    [3, 'Jorge C.', 5, 'Pagué con tarjeta y la reserva quedó confirmada al toque. Cancha impecable.', null, 27],
    [4, 'Sofía L.', 5, 'Perfecta para vóley, la net está bien templada y la losa pareja.', null, 2],
    [4, 'Gabriel N.', 4, 'Jugamos básquet de noche y las luces alumbran bien. Buen precio.', 'Gracias, Gabriel. ¡Vuelvan pronto!', 8],
    [4, 'Paola R.', 4, 'Buena losa y fácil de reservar. Faltaría una banca más.', null, 16],
    [4, 'Martín G.', 5, 'Precio justo y buena atención. Ideal para el grupo de vóley de la UTP.', null, 30],
  ];
  const resenas: ResenaMock[] = R.map(([idCancha, autor, puntuacion, comentario, respuesta, dias], i) => ({
    id: i + 1,
    idCancha,
    codigoReserva: `ALC-SEED${String(i).padStart(2, '0')}`,
    autor,
    puntuacion,
    comentario,
    respuesta,
    fecha: isoLima(deLima(`${sumarDias(hoy, -dias)}T21:30:00`)),
    verificada: true,
    estado: 'PUBLICADA',
  }));

  return {
    version: VERSION,
    dia: hoy,
    canchas,
    resenas,
    bloqueos: [
      { idCancha: 1, fecha: sumarDias(hoy, 2), hora: '08:00', motivo: 'Mantenimiento del grass' },
      { idCancha: 1, fecha: sumarDias(hoy, 2), hora: '09:00', motivo: 'Mantenimiento del grass' },
      { idCancha: 4, fecha: sumarDias(hoy, 1), hora: '10:00', motivo: 'Pintado de líneas' },
    ],
    reservas,
    pagos,
    intentos: [],
    correlativos,
    login: {},
    claves: Object.fromEntries(USUARIOS.map((u) => [u.usuario, u.clave])),
  };
}

let bd: BdMock | null = null;

export function obtenerBd(): BdMock {
  if (bd && bd.dia === hoyLima()) return bd;
  try {
    const guardada = JSON.parse(localStorage.getItem(CLAVE) ?? 'null') as BdMock | null;
    if (guardada && guardada.version === VERSION && guardada.dia === hoyLima()) {
      bd = guardada;
      return bd;
    }
  } catch {
    /* se regenera */
  }
  bd = semillaBd();
  guardarBd();
  return bd;
}

export function guardarBd() {
  if (bd) localStorage.setItem(CLAVE, JSON.stringify(bd));
}

/** Reinicia los datos simulados (útil en pruebas). */
export function reiniciarBd() {
  localStorage.removeItem(CLAVE);
  bd = null;
  return obtenerBd();
}
