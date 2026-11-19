import { http, urlApi } from './cliente';
import type {
  CanchaDetalle,
  CanchaResumen,
  Complejo,
  ComprobanteEnviado,
  CuentaCliente,
  DashboardHoy,
  DiaEspecial,
  DatosComprobante,
  DiaCalendario,
  Disponibilidad,
  DocumentoDni,
  DocumentoRuc,
  EstadoIntentoRespuesta,
  IntentoCreado,
  IntentoPasarela,
  MetodosPago,
  NuevaResena,
  NuevaReserva,
  OrdenResenas,
  PagoCliente,
  Pagina,
  PreferenciaMp,
  RegistroCliente,
  ReservaDeCliente,
  RespuestaLoginCliente,
  Resena,
  ReservaCreada,
  ReservaDetalle,
  RespuestaLogin,
  ResultadoPasarela,
  TipoPago,
  UsuarioSesion,
} from './tipos';

/** Clientes por servicio (rutas del gateway, SPEC §1.1). Cada función devuelve el cuerpo ya tipado. */

// ------------------------------ auth-service ------------------------------
export const authApi = {
  login: (usuario: string, contrasena: string) =>
    http.post<RespuestaLogin>('/auth/login', { usuario, contrasena }, { silenciarToast: true }).then((r) => r.data),
  yo: () => http.get<UsuarioSesion>('/auth/yo').then((r) => r.data),
  cambiarContrasena: (datos: { actual: string; nueva: string; confirmacion: string }) =>
    http.post<void>('/auth/cambiar-contrasena', datos, { silenciarToast: true }).then((r) => r.data),
};

// ------------------------------ catalogo-service ------------------------------
export const catalogoApi = {
  complejo: () => http.get<Complejo>('/catalogo/complejo').then((r) => r.data),
  canchas: () => http.get<CanchaResumen[]>('/catalogo/canchas').then((r) => r.data),
  cancha: (idOSlug: string | number) => http.get<CanchaDetalle>(`/catalogo/canchas/${idOSlug}`, { silenciarToast: true }).then((r) => r.data),
  resenas: (idCancha: number, { page = 0, size = 5, orden = 'recientes' as OrdenResenas } = {}) =>
    http.get<Pagina<Resena>>(`/catalogo/canchas/${idCancha}/resenas`, { params: { page, size, orden } }).then((r) => r.data),
  publicarResena: (idCancha: number, datos: NuevaResena) =>
    http.post<Resena>(`/catalogo/canchas/${idCancha}/resenas`, datos, { silenciarToast: true }).then((r) => r.data),
  /** v2: feriados y días no laborables para pintar el calendario del portal. */
  calendario: (desde: string, hasta: string) =>
    http.get<DiaEspecial[]>('/catalogo/calendario', { params: { desde, hasta }, silenciarToast: true }).then((r) => r.data),
};

// ------------------------------ v2: cuentas de clientes ------------------------------
export const cuentaApi = {
  registro: (datos: RegistroCliente) => http.post<RespuestaLoginCliente>('/auth/clientes/registro', datos, { silenciarToast: true }).then((r) => r.data),
  login: (usuario: string, contrasena: string) =>
    http.post<RespuestaLoginCliente>('/auth/clientes/login', { usuario, contrasena }, { silenciarToast: true }).then((r) => r.data),
  yo: () => http.get<CuentaCliente>('/auth/clientes/yo', { silenciarToast: true }).then((r) => r.data),
  editar: (datos: Pick<CuentaCliente, 'nombres' | 'apellidos' | 'correo' | 'telefono'>) =>
    http.put<CuentaCliente>('/auth/clientes/yo', datos, { silenciarToast: true }).then((r) => r.data),
  cambiarContrasena: (datos: { actual: string; nueva: string; confirmacion: string }) =>
    http.post<void>('/auth/clientes/cambiar-contrasena', datos, { silenciarToast: true }).then((r) => r.data),
  reservas: (f: { estado?: string; page?: number; size?: number } = {}) =>
    http.get<Pagina<ReservaDeCliente>>('/cliente/reservas', { params: f, silenciarToast: true }).then((r) => r.data),
  reserva: (codigo: string) => http.get<ReservaDeCliente>(`/cliente/reservas/${codigo}`, { silenciarToast: true }).then((r) => r.data),
};

// ------------------------------ reservas-service ------------------------------
export const reservasApi = {
  disponibilidad: (fecha: string, idCancha?: number) =>
    http.get<Disponibilidad>('/disponibilidad', { params: { fecha, idCancha }, silenciarToast: true }).then((r) => r.data),
  calendario: (desde: string, dias = 14, idCancha?: number) =>
    http.get<DiaCalendario[]>('/disponibilidad/calendario', { params: { desde, dias, idCancha }, silenciarToast: true }).then((r) => r.data),
  crear: (datos: NuevaReserva) => http.post<ReservaCreada>('/reservas', datos, { silenciarToast: true }).then((r) => r.data),
  detalle: (codigo: string, dni: string) =>
    http.get<ReservaDetalle>(`/reservas/${codigo}`, { params: { dni }, silenciarToast: true }).then((r) => r.data),
  cancelar: (codigo: string, dni: string, motivo: string) =>
    http.post<ReservaDetalle>(`/reservas/${codigo}/cancelar`, { dni, motivo }, { silenciarToast: true }).then((r) => r.data),
  urlConstancia: (codigo: string, dni: string) => urlApi(`/reservas/${codigo}/constancia.pdf?dni=${encodeURIComponent(dni)}`),
};

/** RENIEC/SUNAT pueden tardar ~11 s en decir «no existe» (el gateway espera 20 s): el front espera un poco más. */
const ESPERA_DOCUMENTOS_MS = 25000;

export const documentosApi = {
  dni: (dni: string) =>
    http.get<DocumentoDni>(`/documentos/dni/${dni}`, { silenciarToast: true, timeout: ESPERA_DOCUMENTOS_MS }).then((r) => r.data),
  ruc: (ruc: string) =>
    http.get<DocumentoRuc>(`/documentos/ruc/${ruc}`, { silenciarToast: true, timeout: ESPERA_DOCUMENTOS_MS }).then((r) => r.data),
};

// ------------------------------ pagos-service ------------------------------
export interface EnvioComprobante {
  codigoReserva: string;
  dni: string;
  metodo: 'YAPE' | 'PLIN';
  codigoOperacion: string;
  archivo: File;
  comprobante: DatosComprobante;
}

export const pagosApi = {
  metodos: () => http.get<MetodosPago>('/pagos/metodos').then((r) => r.data),
  enviarComprobante: (d: EnvioComprobante, alProgresar?: (pct: number) => void) => {
    const fd = new FormData();
    fd.append('codigoReserva', d.codigoReserva);
    fd.append('dni', d.dni);
    fd.append('metodo', d.metodo);
    fd.append('codigoOperacion', d.codigoOperacion);
    fd.append('archivo', d.archivo, d.archivo.name);
    // El back lo lee como texto (`@RequestParam String comprobante`): si va como Blob llega como archivo y se ignora la factura.
    fd.append('comprobante', JSON.stringify(d.comprobante));
    return http
      .post<ComprobanteEnviado>('/pagos/comprobante', fd, {
        silenciarToast: true,
        onUploadProgress: (e) => alProgresar?.(e.total ? Math.round((e.loaded / e.total) * 100) : 0),
      })
      .then((r) => r.data);
  },
  pagosDeReserva: (codigo: string, dni: string) =>
    http.get<PagoCliente[]>(`/pagos/reserva/${codigo}`, { params: { dni }, silenciarToast: true }).then((r) => r.data),
  /** Sin `comprobante`, pagos repite la boleta o factura que se pidió antes para la reserva. */
  crearIntento: (datos: { codigoReserva: string; dni: string; tipoPago: TipoPago; comprobante?: DatosComprobante }) =>
    http.post<IntentoCreado>('/pagos/pasarela/intentos', datos, { silenciarToast: true }).then((r) => r.data),
  estadoIntento: (ref: string) =>
    http.get<EstadoIntentoRespuesta>(`/pagos/intentos/${ref}/estado`, { silenciarToast: true }).then((r) => r.data),
  preferenciaMp: (datos: { codigoReserva: string; dni: string; tipoPago: TipoPago; comprobante: DatosComprobante }) =>
    http.post<PreferenciaMp>('/pagos/mercadopago/preferencias', datos, { silenciarToast: true }).then((r) => r.data),
  urlComprobante: (serieNumero: string, codigo: string, dni: string, ext: 'pdf' | 'xml') =>
    urlApi(`/comprobantes/${serieNumero}.${ext}?codigo=${encodeURIComponent(codigo)}&dni=${encodeURIComponent(dni)}`),
};

/** Pasarela «Alcanfores Pay · beta» (proveedor externo simulado dentro de pagos-service). */
export const pasarelaApi = {
  intento: (ref: string) => http.get<IntentoPasarela>(`/pasarela/intentos/${ref}`, { silenciarToast: true }).then((r) => r.data),
  tarjeta: (ref: string, d: { numero: string; titular: string; mesVencimiento: string; anioVencimiento: string; cvv: string; correo: string }) =>
    http.post<ResultadoPasarela>(`/pasarela/intentos/${ref}/tarjeta`, d, { silenciarToast: true }).then((r) => r.data),
  tresDs: (ref: string, codigo: string) =>
    http.post<ResultadoPasarela>(`/pasarela/intentos/${ref}/3ds`, { codigo }, { silenciarToast: true }).then((r) => r.data),
  yape: (ref: string, d: { celular: string; codigoAprobacion: string }) =>
    http.post<ResultadoPasarela>(`/pasarela/intentos/${ref}/yape`, d, { silenciarToast: true }).then((r) => r.data),
};

// ------------------------------ panel (base para el siguiente agente) ------------------------------
export const panelApi = {
  dashboard: (fecha?: string, { silenciar = false } = {}) =>
    http.get<DashboardHoy>('/admin/dashboard', { params: { fecha }, silenciarToast: silenciar }).then((r) => r.data),
};
