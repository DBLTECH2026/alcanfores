/** Tipos del contrato (SPEC §5). JSON camelCase, fechas 'YYYY-MM-DD', horas 'HH:mm', fecha-hora ISO sin zona, dinero número. */

export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface ErrorCampo {
  campo: string;
  mensaje: string;
}
export interface CuerpoError {
  timestamp?: string;
  status: number;
  codigo: string;
  mensaje: string;
  ruta?: string;
  errores?: ErrorCampo[];
}

// ------------------------------ auth ------------------------------
export type Rol = 'ADMIN' | 'RECEPCION';
export interface UsuarioSesion {
  id: number;
  nombres: string;
  apellidos: string;
  usuario: string;
  correo: string;
  rol: Rol;
}
export interface RespuestaLogin {
  token: string;
  tipo: 'Bearer';
  expiraEn: string;
  usuario: UsuarioSesion;
}

// ------------------------------ catálogo ------------------------------
export type Deporte = 'FUTBOL_5' | 'FUTBOL_6' | 'FUTBOL_7' | 'VOLEY' | 'BASQUET' | 'MULTIUSO';

export interface Complejo {
  nombre: string;
  lema: string;
  direccion: string;
  referencia: string;
  whatsapp: string;
  correo: string;
  mapaUrl: string;
  latitud: number;
  longitud: number;
  horario: { primerTurno: string; ultimoTurno: string; duracionMin: number; inicioNoche: string; diasVisibles: number };
  reglas: { adelantoPct: number; devolucionHoras: number; minutosParaPagar: number; recordatorioHoras: number };
  pagos: { yapeNumero: string; yapeTitular: string; plinNumero: string };
}

export interface Calificacion {
  promedio: number;
  total: number;
}
export interface CanchaResumen {
  id: number;
  slug: string;
  nombre: string;
  deporte: Deporte;
  deporteTexto: string;
  superficie: string;
  capacidad: number;
  techada: boolean;
  iluminacionLed: boolean;
  descripcion: string;
  precioDia: number;
  precioNoche: number;
  fotoPrincipal: string | null;
  calificacion: Calificacion;
}
export interface FotoCancha {
  id: number;
  /** Ruta del gateway: `/api/catalogo/fotos/{uuid}.jpg`. */
  url: string;
  leyenda: string | null;
  principal: boolean;
  orden?: number;
  ancho?: number | null;
  alto?: number | null;
}
export interface CanchaDetalle extends CanchaResumen {
  descripcionLarga: string | null;
  medidas: string | null;
  caracteristicas: string[];
  fotos: FotoCancha[];
  resumenResenas: Calificacion & { distribucion: Record<'1' | '2' | '3' | '4' | '5', number> };
  /** v2 (SPEC_V2 §A): horario propio de la cancha. */
  horario?: HorarioPublico | null;
  feriado?: FeriadoCancha | null;
}

// ------------------------------ v2: horario por cancha y calendario ------------------------------
/** 1 = lunes … 7 = domingo (ISO). */
export type DiaSemana = 1 | 2 | 3 | 4 | 5 | 6 | 7;
export interface DiaHorario {
  diaSemana: DiaSemana;
  abierto: boolean;
  /** 'HH:mm' */
  apertura: string;
  /** 'HH:mm'; hora en que TERMINA el último turno ('24:00' o '23:59' = medianoche). */
  cierre: string;
}
export interface HorarioPublico {
  duracionTurnoMin: number;
  inicioNoche?: string;
  dias: DiaHorario[];
  /** «Lun a vie 8:00 a. m.–11:00 p. m. · Sáb y dom 7:00 a. m.–11:00 p. m.» */
  texto: string;
}
export interface FeriadoCancha {
  modo: 'ABIERTA' | 'CERRADA';
  /** Adicional en soles POR TURNO cuando abre en feriado. */
  recargo: number;
}
export type TipoDiaEspecial = 'FERIADO' | 'NO_LABORABLE';
export interface DiaEspecial {
  id?: number;
  fecha: string;
  tipo: TipoDiaEspecial;
  nombre: string;
  /** null = todas las canchas. */
  canchas: number[] | null;
}
export interface Resena {
  id: number;
  autor: string;
  puntuacion: number;
  comentario: string;
  fecha: string;
  respuesta: string | null;
  respondidoEn?: string | null;
  verificada: boolean;
  /** v2: CLIENTE (reserva verificada) o COMPLEJO (agregada por el administrador). */
  origen?: 'CLIENTE' | 'COMPLEJO';
}
export type OrdenResenas = 'recientes' | 'mejor' | 'peor';
export interface NuevaResena {
  codigoReserva: string;
  /** v1: DNI. v2: con sesión de cliente el DNI sale del token. */
  dni?: string;
  puntuacion: number;
  comentario: string;
}

// ------------------------------ reservas ------------------------------
export type EstadoTurnoPublico = 'DISPONIBLE' | 'OCUPADO' | 'BLOQUEADO' | 'PASADO';
export type Franja = 'DIA' | 'NOCHE';
export interface Turno {
  idTurno: number;
  horaInicio: string;
  horaFin: string;
  precio: number;
  franja: Franja;
  estado: EstadoTurnoPublico;
  /** v2: turno en feriado (el precio ya incluye el recargo). */
  feriado?: boolean;
  recargo?: number;
}
export interface DisponibilidadCancha {
  idCancha: number;
  nombre: string;
  deporte: Deporte;
  turnos: Turno[];
}
export interface Disponibilidad {
  fecha: string;
  canchas: DisponibilidadCancha[];
}
export interface DiaCalendario {
  fecha: string;
  libres: number;
  total: number;
}

export type EstadoReserva = 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA';
/** REEMBOLSADO: cancelada con devolución (montoPagado queda neto y saldo en 0). */
export type EstadoPago = 'SIN_PAGO' | 'EN_REVISION' | 'OBSERVADO' | 'ADELANTO_PAGADO' | 'PAGADO_TOTAL' | 'REEMBOLSADO';
export type Asistencia = 'PENDIENTE' | 'ASISTIO' | 'NO_ASISTIO';
export type MetodoPagoWeb = 'YAPE' | 'PLIN' | 'TARJETA' | 'MERCADO_PAGO';

export interface ClienteReserva {
  dni: string;
  nombres: string;
  apellidos: string;
  telefono: string;
  correo: string;
}
export interface NuevaReserva {
  /** v1: un turno. v2: `idsTurno` (1 a 8 turnos de la misma cancha y fecha). */
  idTurno?: number;
  idsTurno?: number[];
  cliente: ClienteReserva;
  metodoPago: MetodoPagoWeb;
  aceptaTerminos: boolean;
  aceptaPrivacidad: boolean;
}
export interface ReservaCreada {
  codigoReserva: string;
  estado: EstadoReserva;
  estadoPago: EstadoPago;
  venceEn: string | null;
  cancha: { id: number; nombre: string; deporte: Deporte };
  fecha: string;
  horaInicio: string;
  horaFin: string;
  montoTotal: number;
  montoAdelanto: number;
  cliente: { nombres: string; apellidos: string; dniEnmascarado: string; telefono: string; correo: string };
  /** v2: turnos de la reserva y texto del horario («7:00–9:00 p. m.»). */
  turnos?: TurnoReserva[];
  horarioTexto?: string;
}
export interface TurnoReserva {
  idTurno: number;
  horaInicio: string;
  horaFin: string;
  precio: number;
}
/**
 * Hito de la línea de tiempo (`reserva_historial`). Eventos reales del back: REGISTRADA, COMPROBANTE_RECIBIDO, OBSERVADO,
 * PAGO_VALIDADO, CONFIRMADA, ASISTENCIA (el `detalle` dice si asistió o no) y CANCELADA.
 */
export interface EventoLinea {
  evento: string;
  fecha: string;
  detalle: string | null;
}
export interface Cancelacion {
  canceladoPor: 'CLIENTE' | 'ADMINISTRADOR' | 'SISTEMA';
  motivo: string;
  fecha: string;
  devolucion: boolean;
  montoDevolucion: number | null;
}
export interface ReservaDetalle extends ReservaCreada {
  asistencia: Asistencia;
  metodoPagoPrevisto?: MetodoPagoWeb | string | null;
  montoPagado: number;
  saldo: number;
  cancelacion: Cancelacion | null;
  linea: EventoLinea[];
  puedeCancelar: boolean;
  puedeReenviarComprobante: boolean;
  puedeResenar: boolean;
}

// ------------------------------ v2: cuentas de clientes (SPEC_V2 §E) ------------------------------
export interface CuentaCliente {
  id: number;
  dni: string;
  nombres: string;
  apellidos: string;
  correo: string;
  telefono: string;
}
export interface RespuestaLoginCliente {
  token: string;
  tipo: 'Bearer';
  expiraEn: string;
  cliente: CuentaCliente;
}
export interface RegistroCliente {
  dni: string;
  nombres: string;
  apellidos: string;
  correo: string;
  telefono: string;
  contrasena: string;
  confirmacion: string;
  aceptaTerminos: boolean;
  aceptaPrivacidad: boolean;
}
/** Ítem de «Mis reservas» (GET /api/cliente/reservas). */
export interface ReservaDeCliente extends ReservaDetalle {
  resenada?: boolean;
}

export interface DocumentoDni {
  dni: string;
  nombres: string;
  apellidoPaterno: string;
  apellidoMaterno: string;
  apellidos: string;
  fuente: string;
}
export interface DocumentoRuc {
  ruc: string;
  razonSocial: string;
  direccion: string;
  estado: string;
  condicion: string;
  fuente: string;
}

// ------------------------------ pagos ------------------------------
export interface DatosComprobante {
  tipo: 'BOLETA' | 'FACTURA';
  ruc?: string;
  razonSocial?: string;
  direccionFiscal?: string;
}
export interface TarjetaPrueba {
  numero: string;
  marca?: string;
  resultado: string;
}
export interface MetodosPago {
  yape: { numero: string; titular: string };
  plin: { numero: string };
  tarjetaBeta: boolean;
  mercadoPago: boolean;
  tarjetasPrueba: TarjetaPrueba[];
  yapePrueba: { codigoAprueba: string; codigoRechaza: string };
  codigo3dsPrueba?: string;
  adelantoPct?: number;
}
export type EstadoValidacion = 'PENDIENTE' | 'VALIDADO' | 'RECHAZADO' | 'ANULADO' | 'REEMBOLSADO';
/** Enlace a un comprobante para el cliente (rutas del gateway con `?codigo=&dni=` ya incluidos). */
export interface ComprobanteEnlace {
  id: number;
  tipo: 'BOLETA' | 'FACTURA' | 'NOTA_CREDITO';
  serieNumero: string;
  estado: 'EMITIDO' | 'ANULADO';
  urlPdf: string;
  urlXml: string;
}
export interface PagoCliente {
  idPago: number;
  metodo: string;
  canal: 'COMPROBANTE' | 'PASARELA' | 'CAJA';
  tipoPago: 'ADELANTO' | 'TOTAL' | 'SALDO';
  monto: number;
  estado: EstadoValidacion;
  observacion: string | null;
  fecha: string;
  comprobante: ComprobanteEnlace | null;
  /** Nota de crédito emitida al cancelar con devolución. */
  notaCredito: ComprobanteEnlace | null;
  /** Boleta o factura que se pidió para ese pago (misma forma que `comprobante` de las solicitudes). */
  facturacion: FacturacionPedida | null;
}
export interface FacturacionPedida {
  tipo: 'BOLETA' | 'FACTURA';
  ruc: string | null;
  razonSocial: string | null;
  direccionFiscal: string | null;
}
export interface ComprobanteEnviado {
  idPago: number;
  estado: 'PENDIENTE';
  mensaje: string;
}
export type TipoPago = 'ADELANTO' | 'TOTAL';
export interface IntentoCreado {
  referencia: string;
  urlCheckout: string;
  expiraEn: string;
}
export type EstadoIntento = 'CREADO' | 'REQUIERE_3DS' | 'APROBADO' | 'RECHAZADO' | 'EXPIRADO';
export interface IntentoPasarela {
  referencia: string;
  comercio: string;
  concepto: string;
  monto: number;
  moneda: string;
  estado: EstadoIntento;
  expiraEn: string;
  codigoReserva: string;
  motivoRechazo?: string | null;
}
export interface ResultadoPasarela {
  estado: 'APROBADO' | 'RECHAZADO' | 'REQUIERE_3DS';
  codigoAutorizacion?: string;
  motivo?: string;
  marca?: string;
  ultimos4?: string;
}
export interface EstadoIntentoRespuesta {
  referencia: string;
  proveedor: 'BETA' | 'MERCADO_PAGO';
  estado: EstadoIntento;
  tipoPago: TipoPago;
  monto: number;
  codigoReserva: string;
  motivoRechazo: string | null;
  expiraEn: string | null;
  idPago: number | null;
  comprobante: ComprobanteEnlace | null;
}
export interface PreferenciaMp {
  referencia: string;
  initPoint: string;
  sandbox?: boolean;
  expiraEn?: string;
}

// ------------------------------ panel ------------------------------
export interface DashboardHoy {
  fecha: string;
  kpis: { reservasHoy: number; porValidar: number; confirmadasHoy: number; ocupacionHoyPct: number; noShowMesPct: number; reservasSemana: number };
  proximas: Array<{ codigoReserva: string; cancha: string; fecha: string; horaInicio: string; horaFin: string; cliente: string; estado: EstadoReserva; estadoPago: EstadoPago }>;
  agenda: Array<{ idCancha: number; nombre: string; turnos: Array<{ horaInicio: string; estado: string; codigoReserva: string | null; cliente: string | null; estadoPago: EstadoPago | null }> }>;
  alertas: Array<{ tipo: string; mensaje: string; codigoReserva: string | null }>;
}
