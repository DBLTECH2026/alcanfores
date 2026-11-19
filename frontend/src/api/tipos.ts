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
  url: string;
  leyenda: string | null;
  principal: boolean;
}
export interface CanchaDetalle extends CanchaResumen {
  descripcionLarga: string | null;
  medidas: string | null;
  caracteristicas: string[];
  fotos: FotoCancha[];
  resumenResenas: Calificacion & { distribucion: Record<'1' | '2' | '3' | '4' | '5', number> };
}
export interface Resena {
  id: number;
  autor: string;
  puntuacion: number;
  comentario: string;
  fecha: string;
  respuesta: string | null;
  verificada: boolean;
}
export type OrdenResenas = 'recientes' | 'mejor' | 'peor';
export interface NuevaResena {
  codigoReserva: string;
  dni: string;
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
export type EstadoPago = 'SIN_PAGO' | 'EN_REVISION' | 'OBSERVADO' | 'ADELANTO_PAGADO' | 'PAGADO_TOTAL';
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
  idTurno: number;
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
}
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
  montoPagado: number;
  saldo: number;
  cancelacion: Cancelacion | null;
  linea: EventoLinea[];
  puedeCancelar: boolean;
  puedeReenviarComprobante: boolean;
  puedeResenar: boolean;
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
}
export type EstadoValidacion = 'PENDIENTE' | 'VALIDADO' | 'RECHAZADO' | 'ANULADO' | 'REEMBOLSADO';
export interface PagoCliente {
  metodo: string;
  canal: 'COMPROBANTE' | 'PASARELA' | 'CAJA';
  tipoPago: 'ADELANTO' | 'TOTAL' | 'SALDO';
  monto: number;
  estado: EstadoValidacion;
  observacion: string | null;
  fecha: string;
  comprobante: { serieNumero: string; urlPdf: string } | null;
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
  estado: EstadoIntento;
  codigoReserva: string;
  motivo?: string | null;
}
export interface PreferenciaMp {
  referencia: string;
  initPoint: string;
}

// ------------------------------ panel ------------------------------
export interface DashboardHoy {
  fecha: string;
  kpis: { reservasHoy: number; porValidar: number; confirmadasHoy: number; ocupacionHoyPct: number; noShowMesPct: number; reservasSemana: number };
  proximas: Array<{ codigoReserva: string; cancha: string; fecha: string; horaInicio: string; horaFin: string; cliente: string; estado: EstadoReserva; estadoPago: EstadoPago }>;
  agenda: Array<{ idCancha: number; nombre: string; turnos: Array<{ horaInicio: string; estado: string; codigoReserva: string | null; cliente: string | null; estadoPago: EstadoPago | null }> }>;
  alertas: Array<{ tipo: string; mensaje: string; codigoReserva: string | null }>;
}
