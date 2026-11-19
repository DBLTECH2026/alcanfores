/**
 * Tipos de los endpoints del PANEL (`/api/admin/**`, `/api/estado`), tomados de las respuestas reales del back
 * (OpenAPI de cada servicio + docs/servicios/*.md). Los del portal siguen en `@/api/tipos`.
 */
import type { Asistencia, Deporte, EstadoPago, EstadoReserva, Franja, Pagina, Rol } from '@/api/tipos';

export type { Pagina };
export type Origen = 'WEB' | 'PRESENCIAL' | 'TELEFONICA';
export type MetodoCaja = 'EFECTIVO' | 'YAPE' | 'PLIN' | 'TARJETA_POS';
export type TipoPagoCaja = 'ADELANTO' | 'TOTAL' | 'SALDO';
export type PaginaConConteos<T> = Pagina<T> & { conteos: Record<string, number> };

export interface SolicitudComprobante {
  tipo: 'BOLETA' | 'FACTURA';
  ruc?: string;
  razonSocial?: string;
  direccionFiscal?: string;
}

// ------------------------------ dashboard ------------------------------
export interface Dashboard {
  fecha: string;
  kpis: { reservasHoy: number; porValidar: number; confirmadasHoy: number; ocupacionHoyPct: number; noShowMesPct: number; reservasSemana: number };
  proximas: Array<{ codigoReserva: string; cliente: string; cancha: string; fecha: string; horaInicio: string; estado: EstadoReserva; estadoPago: EstadoPago }>;
  agenda: Array<{
    idCancha: number;
    nombre: string;
    turnos: Array<{ horaInicio: string; estado: 'DISPONIBLE' | 'RESERVADO' | 'BLOQUEADO'; codigoReserva: string | null; cliente: string | null; estadoPago: EstadoPago | null }>;
  }>;
  alertas: Array<{ tipo: string; mensaje: string; codigoReserva: string | null }>;
}

// ------------------------------ reservas ------------------------------
export interface FilaReserva {
  codigoReserva: string;
  cliente: { nombres: string; apellidos: string; dni: string; telefono: string };
  cancha: string;
  fecha: string;
  horaInicio: string;
  horaFin: string;
  estado: EstadoReserva;
  estadoPago: EstadoPago;
  asistencia: Asistencia;
  origen: Origen;
  montoTotal: number;
  montoPagado: number;
  fechaRegistro: string;
}

export interface FiltrosReservas {
  estado?: string;
  estadoPago?: string;
  fecha?: string;
  desde?: string;
  hasta?: string;
  idCancha?: string;
  origen?: string;
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ReservaAdmin {
  codigoReserva: string;
  estado: EstadoReserva;
  estadoPago: EstadoPago;
  asistencia: Asistencia;
  origen: Origen;
  metodoPagoPrevisto: string | null;
  cancha: { id: number; nombre: string; deporte: Deporte };
  idTurno: number;
  fecha: string;
  horaInicio: string;
  horaFin: string;
  franja: Franja;
  montoTotal: number;
  montoAdelanto: number;
  montoPagado: number;
  saldo: number;
  venceEn: string | null;
  fechaRegistro: string;
  confirmadaEn: string | null;
  registradoPor: number | null;
  nota: string | null;
  cliente: { idCliente: number; dni: string; nombres: string; apellidos: string; telefono: string; correo: string; verificadoReniec: boolean };
  cancelacion: { canceladoPor: string; motivo: string; fecha: string; devolucion: boolean; montoDevolucion: number | null } | null;
  linea: Array<{ evento: string; fecha: string; detalle: string | null }>;
  puedeCancelar: boolean;
  puedeMarcarAsistencia: boolean;
  puedeCobrarSaldo: boolean;
}

export interface NuevaReservaManual {
  idTurno: number;
  cliente: { dni: string; nombres: string; apellidos: string; telefono: string; correo: string };
  origen: 'PRESENCIAL' | 'TELEFONICA';
  nota?: string;
  pagoInicial?: { tipoPago: 'ADELANTO' | 'TOTAL'; metodo: MetodoCaja; monto: number; codigoOperacion?: string; comprobante: SolicitudComprobante };
}
export interface RespuestaManual {
  codigoReserva: string;
  estado: EstadoReserva;
  estadoPago: EstadoPago;
  montoTotal: number;
  montoAdelanto: number;
  pagoRegistrado: boolean | null;
  aviso: string | null;
}

// ------------------------------ turnos / agenda ------------------------------
export interface TurnoAgenda {
  idTurno: number;
  horaInicio: string;
  horaFin: string;
  precio: number;
  franja: Franja;
  estado: 'DISPONIBLE' | 'RESERVADO' | 'BLOQUEADO';
  motivoBloqueo: string | null;
  reserva: { codigoReserva: string; cliente: string; telefono: string; estado: EstadoReserva; estadoPago: EstadoPago; asistencia: Asistencia } | null;
}
export interface AgendaDia {
  fecha: string;
  canchas: Array<{ idCancha: number; nombre: string; deporte: Deporte; activa: boolean; turnos: TurnoAgenda[] }>;
}

// ------------------------------ clientes ------------------------------
export interface ClienteAdmin {
  idCliente: number;
  dni: string;
  nombres: string;
  apellidos: string;
  telefono: string;
  correo: string;
  verificadoReniec: boolean;
  fechaRegistro: string;
  totalReservas: number;
  asistidas: number;
  noShows: number;
  ultimaReserva: string | null;
}
export interface HistorialCliente {
  cliente: ClienteAdmin;
  reservas: FilaReserva[];
}

// ------------------------------ pagos y comprobantes ------------------------------
export interface EnlaceComprobante {
  id: number;
  tipo: string;
  serieNumero: string;
  estado: string;
  urlPdf: string;
  urlXml: string;
}
export interface FilaPago {
  id: number;
  codigoReserva: string;
  cliente: { dni: string; nombres: string; apellidos: string; telefono: string; correo: string };
  metodo: string;
  canal: 'COMPROBANTE' | 'PASARELA' | 'CAJA';
  tipoPago: TipoPagoCaja;
  monto: number;
  estado: 'PENDIENTE' | 'VALIDADO' | 'RECHAZADO' | 'ANULADO' | 'REEMBOLSADO';
  codigoOperacion: string | null;
  tieneArchivo: boolean;
  fechaEnvio: string;
  fechaValidacion: string | null;
  validadoPor: string | null;
  comprobante: EnlaceComprobante | null;
}
export interface DetallePago extends Omit<FilaPago, 'tieneArchivo' | 'comprobante'> {
  concepto: string;
  urlArchivo: string | null;
  observacion: string | null;
  referenciaPasarela: string | null;
  marcaTarjeta: string | null;
  ultimos4: string | null;
  codigoAutorizacion: string | null;
  facturacion: { tipo: string; tipoDoc: string; numDoc: string; nombre: string; direccion: string | null } | null;
  comprobantes: EnlaceComprobante[];
  puedeValidar: boolean;
}
export interface ResultadoCobro {
  idPago: number;
  estado: string;
  monto: number;
  comprobante: EnlaceComprobante | null;
}
export interface CobroCaja {
  codigoReserva: string;
  tipoPago: TipoPagoCaja;
  metodo: MetodoCaja;
  monto: number;
  codigoOperacion?: string;
  comprobante: SolicitudComprobante;
}
export interface Comprobante {
  id: number;
  tipo: 'BOLETA' | 'FACTURA' | 'NOTA_CREDITO';
  codigoTipo: string;
  serieNumero: string;
  fechaEmision: string;
  codigoReserva: string;
  idPago: number;
  clienteTipoDoc: string;
  clienteNumDoc: string;
  clienteNombre: string;
  clienteDireccion: string | null;
  descripcion: string;
  opGravada: number;
  igv: number;
  total: number;
  montoLetras: string;
  valorResumen: string;
  estado: 'EMITIDO' | 'ANULADO';
  estadoSunat: string;
  idReferencia: number | null;
  serieNumeroReferencia: string | null;
  motivo: string | null;
  urlPdf: string;
  urlXml: string;
}

// ------------------------------ reportes ------------------------------
export interface ReporteReservas {
  desde: string;
  hasta: string;
  totales: { registradas: number; confirmadas: number; canceladas: number; pendientes: number; asistidas: number; noAsistio: number; tasaNoShowPct: number; ocupacionPct: number };
  porCancha: Array<{ idCancha: number; cancha: string; reservas: number; ocupacionPct: number }>;
  porDia: Array<{ fecha: string; registradas: number; confirmadas: number; canceladas: number }>;
  porHora: Array<{ hora: string; reservas: number }>;
  porOrigen: Array<{ origen: string; reservas: number }>;
  cancelaciones: { porActor: Record<string, number>; motivosFrecuentes: Array<{ motivo: string; veces: number }> };
  topClientes: Array<{ dni: string; nombre: string; reservas: number; asistidas: number }>;
}
export interface Agregado {
  clave: string;
  texto: string;
  total: number;
  cantidad: number;
}
export interface ReporteIngresos {
  desde: string;
  hasta: string;
  total: number;
  cantidadPagos: number;
  ticketPromedio: number;
  porMetodo: Agregado[];
  porCanal: Agregado[];
  porTipoPago: Agregado[];
  porDia: Array<{ fecha: string; total: number; cantidad: number }>;
  porTipoComprobante: Agregado[];
  tiempoMedioValidacionMin: number | null;
  pagosRechazados: number;
  reembolsado: number;
}

// ------------------------------ catálogo ------------------------------
export interface FotoAdmin {
  id: number;
  url: string;
  leyenda: string | null;
  principal: boolean;
  orden: number;
  ancho: number | null;
  alto: number | null;
}
export interface CanchaAdmin {
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
  descripcionLarga: string | null;
  medidas: string | null;
  caracteristicas: string[];
  precioDia: number;
  precioNoche: number;
  fotoPrincipal: string | null;
  fotos: FotoAdmin[];
  calificacion: { promedio: number; total: number };
  estado: 'ACTIVA' | 'INACTIVA';
  orden: number;
  creadoEn: string;
  actualizadoEn: string;
}
export interface SolicitudCancha {
  nombre: string;
  deporte: Deporte;
  superficie: string;
  capacidad: number;
  descripcion: string;
  descripcionLarga?: string;
  techada: boolean;
  iluminacionLed: boolean;
  medidas?: string;
  caracteristicas: string[];
  precioDia: number;
  precioNoche: number;
  estado?: 'ACTIVA' | 'INACTIVA';
  orden?: number;
}
export interface ResenaAdmin {
  id: number;
  idCancha: number;
  cancha: string;
  codigoReserva: string;
  autor: string;
  puntuacion: number;
  comentario: string;
  estado: 'PUBLICADA' | 'OCULTA';
  respuesta: string | null;
  respondidoEn: string | null;
  creadoEn: string;
}
export type Configuracion = Record<string, string>;

// ------------------------------ notificaciones ------------------------------
export interface Adjunto {
  tipo: 'CONSTANCIA' | 'COMPROBANTE';
  referencia: string;
  nombreArchivo: string;
  estado: 'PENDIENTE' | 'ADJUNTADO' | 'NO_DISPONIBLE';
  detalle: string | null;
}
export interface FilaNotificacion {
  id: number;
  codigoReserva: string | null;
  evento: string;
  eventoTexto: string;
  canal: 'WHATSAPP' | 'CORREO';
  destino: string;
  asunto: string;
  estado: 'PENDIENTE' | 'ENVIADO' | 'FALLIDO' | 'SIMULADO';
  intentos: number;
  ultimoError: string | null;
  adjuntos: number;
  creadoEn: string;
  enviadoEn: string | null;
}
export interface DetalleNotificacion extends Omit<FilaNotificacion, 'adjuntos'> {
  formato: 'HTML' | 'TEXTO';
  cuerpo: string;
  vistaPrevia: string | null;
  adjuntos: Adjunto[];
  reintentable: boolean;
}
export interface Recordatorio {
  id: number;
  codigoReserva: string;
  cliente: string;
  telefono: string;
  cancha: string;
  inicioReserva: string;
  enviarEn: string;
  estado: 'PROGRAMADO' | 'ENVIADO' | 'CANCELADO' | 'OMITIDO';
  enviadoEn: string | null;
  detalle: string | null;
}
export interface ResultadoRecordatorios {
  revisados: number;
  enviados: number;
  omitidos: number;
  cancelados: number;
}
export interface EstadoWhatsapp {
  configurado: boolean;
  instancia: string;
  estado: 'open' | 'connecting' | 'close' | 'NO_CONFIGURADO' | string;
  numero: string | null;
}
export interface ConexionWhatsapp {
  qrBase64: string | null;
  codigoEmparejamiento: string | null;
  estado: string;
  mensaje: string | null;
}

// ------------------------------ auth ------------------------------
export interface UsuarioAdmin {
  id: number;
  nombres: string;
  apellidos: string;
  usuario: string;
  correo: string;
  rol: Rol;
  estado: 'ACTIVO' | 'INACTIVO';
  bloqueado: boolean;
  bloqueadoHasta: string | null;
  ultimoAcceso: string | null;
  creadoEn: string;
  actualizadoEn: string;
}
export interface SolicitudUsuario {
  nombres: string;
  apellidos: string;
  usuario: string;
  correo: string;
  rol: Rol;
  contrasena?: string;
}
export interface EntradaBitacora {
  id: number;
  servicio: string;
  idActor: number | null;
  actor: string;
  rol: string;
  accion: string;
  entidad: string | null;
  idEntidad: string | null;
  detalle: string | null;
  ip: string | null;
  fecha: string;
}

// ------------------------------ gateway ------------------------------
export interface EstadoSistema {
  generadoEn: string;
  servicios: Array<{ nombre: string; estado: 'UP' | 'DOWN' | string; instancias: number; latenciaMs: number | null }>;
}
