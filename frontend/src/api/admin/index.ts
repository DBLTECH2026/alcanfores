import { http } from '@/api/cliente';
import type { Rol } from '@/api/tipos';
import type {
  AgendaDia,
  CanchaAdmin,
  CobroCaja,
  Comprobante,
  Configuracion,
  ConexionWhatsapp,
  Dashboard,
  DetalleNotificacion,
  DetallePago,
  EntradaBitacora,
  EstadoSistema,
  EstadoWhatsapp,
  FilaNotificacion,
  FilaPago,
  FilaReserva,
  FiltrosReservas,
  FotoAdmin,
  HistorialCliente,
  ClienteAdmin,
  NuevaReservaManual,
  Pagina,
  PaginaConConteos,
  Recordatorio,
  ReporteIngresos,
  ReporteReservas,
  ResenaAdmin,
  ReservaAdmin,
  RespuestaManual,
  ResultadoCobro,
  ResultadoRecordatorios,
  SolicitudCancha,
  SolicitudUsuario,
  TurnoAgenda,
  UsuarioAdmin,
} from './tipos';

/**
 * Clientes HTTP del PANEL (rutas del gateway, SPEC §1.1 y §5). El interceptor de `cliente.ts` pone el token,
 * el X-Request-Id, muestra el toast de error y cierra la sesión ante 401. Las acciones que pintan errores por campo
 * en un formulario usan `silenciarToast`.
 */

type Parametros = Record<string, string | number | boolean | null | undefined>;
/** Quita los filtros vacíos para no mandar `?estado=&q=`. */
export function limpiar(p: Parametros): Record<string, string | number | boolean> {
  return Object.fromEntries(Object.entries(p).filter(([, v]) => v !== undefined && v !== null && v !== '')) as Record<string, string | number | boolean>;
}
const S = { silenciarToast: true } as const;
const d = <T>(p: Promise<{ data: T }>) => p.then((r) => r.data);

/** Las URL del back vienen con `/api/...`; el cliente ya tiene `/api` como base. */
export const rutaApi = (url: string) => url.replace(/^\/api(?=\/)/, '');

export const dashboardApi = {
  obtener: (fecha?: string) => d(http.get<Dashboard>('/admin/dashboard', { params: limpiar({ fecha }), ...S })),
};

export const reservasAdminApi = {
  listar: (f: FiltrosReservas) => d(http.get<PaginaConConteos<FilaReserva>>('/admin/reservas', { params: limpiar({ ...f }), ...S })),
  detalle: (codigo: string) => d(http.get<ReservaAdmin>(`/admin/reservas/${codigo}`, S)),
  crearManual: (datos: NuevaReservaManual) => d(http.post<RespuestaManual>('/admin/reservas', datos, S)),
  cancelar: (codigo: string, motivo: string, devolucion: boolean | null) =>
    d(http.post<ReservaAdmin>(`/admin/reservas/${codigo}/cancelar`, limpiar({ motivo, devolucion }), S)),
  asistencia: (codigo: string, asistencia: 'ASISTIO' | 'NO_ASISTIO') => d(http.post<ReservaAdmin>(`/admin/reservas/${codigo}/asistencia`, { asistencia })),
  rutaConstancia: (codigo: string) => `/admin/reservas/${codigo}/constancia.pdf`,
};

export const turnosApi = {
  agenda: (fecha: string, idCancha?: number) => d(http.get<AgendaDia>('/admin/turnos', { params: limpiar({ fecha, idCancha }), ...S })),
  bloquear: (idTurno: number, motivo: string) => d(http.post<TurnoAgenda>(`/admin/turnos/${idTurno}/bloquear`, { motivo }, S)),
  desbloquear: (idTurno: number, motivo?: string) => d(http.post<TurnoAgenda>(`/admin/turnos/${idTurno}/desbloquear`, limpiar({ motivo }))),
  bloquearRango: (datos: { idCancha: number; fecha: string; horaDesde: string; horaHasta: string; motivo: string }) =>
    d(http.post<{ bloqueados: number; omitidos: number; horasOmitidas: string[] }>('/admin/turnos/bloquear-rango', datos, S)),
  generar: (datos: { desde: string; hasta: string; idsCancha?: number[] }) => d(http.post<{ creados: number; existentes: number }>('/admin/turnos/generar', datos, S)),
};

export const clientesApi = {
  listar: (q: string, page: number, size = 20) => d(http.get<Pagina<ClienteAdmin>>('/admin/clientes', { params: limpiar({ q, page, size }), ...S })),
  historial: (dni: string) => d(http.get<HistorialCliente>(`/admin/clientes/${dni}`, S)),
};

export const pagosAdminApi = {
  listar: (f: Parametros) => d(http.get<PaginaConConteos<FilaPago>>('/admin/pagos', { params: limpiar(f), ...S })),
  detalle: (id: number) => d(http.get<DetallePago>(`/admin/pagos/${id}`, S)),
  rutaArchivo: (id: number) => `/admin/pagos/${id}/archivo`,
  validar: (id: number) => d(http.post<ResultadoCobro>(`/admin/pagos/${id}/validar`, {})),
  rechazar: (id: number, observacion: string) => d(http.post<DetallePago>(`/admin/pagos/${id}/rechazar`, { observacion }, S)),
  caja: (datos: CobroCaja) => d(http.post<ResultadoCobro>('/admin/pagos/caja', datos, S)),
};

export const comprobantesApi = {
  listar: (f: Parametros) => d(http.get<Pagina<Comprobante>>('/admin/comprobantes', { params: limpiar(f), ...S })),
  anular: (id: number, motivo: string) => d(http.post<Comprobante>(`/admin/comprobantes/${id}/anular`, { motivo }, S)),
};

export const reportesApi = {
  reservas: (desde: string, hasta: string) => d(http.get<ReporteReservas>('/admin/reportes/reservas', { params: { desde, hasta }, ...S })),
  ingresos: (desde: string, hasta: string) => d(http.get<ReporteIngresos>('/admin/reportes/ingresos', { params: { desde, hasta }, ...S })),
  rutaExcel: (desde: string, hasta: string) => `/admin/reportes/reservas.xlsx?desde=${desde}&hasta=${hasta}`,
};

export const canchasAdminApi = {
  listar: () => d(http.get<CanchaAdmin[]>('/admin/canchas', S)),
  obtener: (id: number) => d(http.get<CanchaAdmin>(`/admin/canchas/${id}`, S)),
  crear: (datos: SolicitudCancha) => d(http.post<CanchaAdmin>('/admin/canchas', datos, S)),
  editar: (id: number, datos: SolicitudCancha) => d(http.put<CanchaAdmin>(`/admin/canchas/${id}`, datos, S)),
  estado: (id: number, estado: 'ACTIVA' | 'INACTIVA') => d(http.patch<CanchaAdmin>(`/admin/canchas/${id}/estado`, { estado })),
  subirFoto: (id: number, archivo: File, leyenda: string, alProgresar?: (pct: number) => void) => {
    const fd = new FormData();
    fd.append('archivo', archivo, archivo.name);
    if (leyenda) fd.append('leyenda', leyenda);
    return d(
      http.post<FotoAdmin>(`/admin/canchas/${id}/fotos`, fd, {
        ...S,
        timeout: 60000,
        onUploadProgress: (e) => alProgresar?.(e.total ? Math.round((e.loaded / e.total) * 100) : 0),
      }),
    );
  },
  editarFoto: (id: number, idFoto: number, datos: { leyenda?: string; orden?: number; principal?: boolean }) =>
    d(http.patch<FotoAdmin>(`/admin/canchas/${id}/fotos/${idFoto}`, datos, S)),
  borrarFoto: (id: number, idFoto: number) => d(http.delete<void>(`/admin/canchas/${id}/fotos/${idFoto}`)),
};

export const resenasAdminApi = {
  listar: (f: Parametros) => d(http.get<Pagina<ResenaAdmin>>('/admin/resenas', { params: limpiar(f), ...S })),
  estado: (id: number, estado: 'PUBLICADA' | 'OCULTA') => d(http.patch<ResenaAdmin>(`/admin/resenas/${id}`, { estado })),
  responder: (id: number, respuesta: string) => d(http.post<ResenaAdmin>(`/admin/resenas/${id}/respuesta`, { respuesta }, S)),
};

export const configuracionApi = {
  obtener: () => d(http.get<Configuracion>('/admin/configuracion', S)),
  guardar: (cambios: Configuracion) => d(http.put<Configuracion>('/admin/configuracion', cambios, S)),
};

export const notificacionesApi = {
  listar: (f: Parametros) => d(http.get<Pagina<FilaNotificacion>>('/admin/notificaciones', { params: limpiar(f), ...S })),
  detalle: (id: number) => d(http.get<DetalleNotificacion>(`/admin/notificaciones/${id}`, S)),
  reintentar: (id: number) => d(http.post<DetalleNotificacion>(`/admin/notificaciones/${id}/reintentar`, {})),
  recordatorios: (f: Parametros) => d(http.get<Pagina<Recordatorio>>('/admin/recordatorios', { params: limpiar(f), ...S })),
  ejecutarRecordatorios: () => d(http.post<ResultadoRecordatorios>('/admin/recordatorios/ejecutar', {})),
};

export const whatsappApi = {
  estado: () => d(http.get<EstadoWhatsapp>('/admin/whatsapp/estado', S)),
  conectar: (telefono?: string) => d(http.post<ConexionWhatsapp>('/admin/whatsapp/conectar', telefono ? { telefono } : {}, S)),
  desconectar: () => d(http.post<{ estado: string; mensaje: string }>('/admin/whatsapp/desconectar', {})),
  prueba: (telefono: string, mensaje: string) => d(http.post<DetalleNotificacion>('/admin/whatsapp/prueba', { telefono, mensaje }, S)),
};

export const usuariosApi = {
  listar: (f: { q?: string; rol?: string; estado?: string; page?: number; size?: number }) =>
    d(http.get<Pagina<UsuarioAdmin>>('/admin/usuarios', { params: limpiar({ ...f, sort: 'id,asc' }), ...S })),
  crear: (datos: SolicitudUsuario) => d(http.post<UsuarioAdmin>('/admin/usuarios', datos, S)),
  editar: (id: number, datos: Omit<SolicitudUsuario, 'contrasena'>) => d(http.put<UsuarioAdmin>(`/admin/usuarios/${id}`, datos, S)),
  estado: (id: number, estado: 'ACTIVO' | 'INACTIVO') => d(http.patch<UsuarioAdmin>(`/admin/usuarios/${id}/estado`, { estado })),
  restablecer: (id: number, nueva: string) => d(http.post<Record<string, string>>(`/admin/usuarios/${id}/restablecer-contrasena`, { nueva }, S)),
};

export const bitacoraApi = {
  listar: (f: Parametros) => d(http.get<Pagina<EntradaBitacora>>('/admin/bitacora', { params: limpiar({ sort: 'fecha,desc', ...f }), ...S })),
  opciones: () => d(http.get<{ servicios: string[]; acciones: string[] }>('/admin/bitacora/opciones', S)),
};

export const estadoApi = {
  obtener: () => d(http.get<EstadoSistema>('/estado', S)),
};

export type { Rol };
