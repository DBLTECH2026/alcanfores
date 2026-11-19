/**
 * Textos de la bitácora en español (con tildes) para las acciones, entidades y roles que registra cada servicio
 * (`auditor.registrar(...)` y `bitacora.registrar(...)` del back). Si llega uno nuevo que no está aquí, se muestra
 * con `legible()` como respaldo.
 */
export const TEXTO_ACCION: Record<string, string> = {
  // Acceso y cuentas del panel
  LOGIN_EXITOSO: 'Inicio de sesión correcto',
  LOGIN_FALLIDO: 'Inicio de sesión fallido',
  LOGIN_BLOQUEADO: 'Inicio de sesión bloqueado',
  LOGIN_INACTIVO: 'Inicio de sesión de usuario inactivo',
  LOGIN_CLIENTE_BLOQUEADO: 'Inicio de sesión de cliente bloqueado',
  CAMBIAR_CONTRASENA: 'Cambiar contraseña',
  ENVIAR_CODIGO_RECUPERACION: 'Enviar código de recuperación',
  RECUPERAR_CONTRASENA: 'Recuperar contraseña',
  RESTABLECER_CONTRASENA: 'Restablecer contraseña',
  EDITAR_MIS_DATOS: 'Editar mis datos',
  CREAR_USUARIO: 'Crear usuario',
  EDITAR_USUARIO: 'Editar usuario',
  ACTIVAR_USUARIO: 'Activar usuario',
  DESACTIVAR_USUARIO: 'Desactivar usuario',
  // Cuenta del cliente
  REGISTRAR_CUENTA: 'Registrar cuenta',
  EDITAR_CUENTA: 'Editar cuenta',
  // Canchas y fotos
  CREAR_CANCHA: 'Crear cancha',
  EDITAR_CANCHA: 'Editar cancha',
  EDITAR_HORARIO_CANCHA: 'Editar horario de cancha',
  CAMBIAR_ESTADO_CANCHA: 'Cambiar estado de cancha',
  SUBIR_FOTO: 'Subir foto',
  EDITAR_FOTO: 'Editar foto',
  BORRAR_FOTO: 'Borrar foto',
  // Reseñas
  CREAR_RESENA: 'Crear reseña',
  CREAR_RESENA_COMPLEJO: 'Crear reseña del complejo',
  EDITAR_RESENA_COMPLEJO: 'Editar reseña del complejo',
  BORRAR_RESENA_COMPLEJO: 'Borrar reseña del complejo',
  RESPONDER_RESENA: 'Responder reseña',
  PUBLICAR_RESENA: 'Publicar reseña',
  OCULTAR_RESENA: 'Ocultar reseña',
  // Calendario y configuración
  CREAR_DIA_ESPECIAL: 'Crear día especial',
  EDITAR_DIA_ESPECIAL: 'Editar día especial',
  BORRAR_DIA_ESPECIAL: 'Borrar día especial',
  CARGAR_FERIADOS_PERU: 'Cargar feriados del Perú',
  EDITAR_CONFIGURACION: 'Editar configuración',
  // Turnos y reservas
  GENERAR_TURNOS: 'Generar turnos',
  BLOQUEAR_TURNO: 'Bloquear turno',
  DESBLOQUEAR_TURNO: 'Desbloquear turno',
  BLOQUEAR_RANGO: 'Bloquear horario',
  DESBLOQUEAR_RANGO: 'Desbloquear horario',
  REGISTRAR_RESERVA_MANUAL: 'Registrar reserva manual',
  CANCELAR_RESERVA: 'Cancelar reserva',
  REPROGRAMAR_RESERVA: 'Reprogramar reserva',
  REGISTRAR_ASISTENCIA: 'Registrar asistencia',
  VENCER_RESERVAS: 'Vencer reservas sin pago',
  REEMBOLSO_SIMULADO: 'Devolución simulada',
  // Pagos y comprobantes
  VALIDAR_PAGO: 'Validar pago',
  VALIDAR_PAGO_IA: 'Validar pago con IA',
  RECHAZAR_PAGO: 'Rechazar pago',
  COBRAR_CAJA: 'Cobrar en caja',
  ADJUNTAR_FOTO_CAJA: 'Adjuntar foto del pago',
  ANULAR_COMPROBANTE: 'Anular comprobante',
  // Notificaciones
  CONECTAR_WHATSAPP: 'Conectar WhatsApp',
  DESCONECTAR_WHATSAPP: 'Desconectar WhatsApp',
  PRUEBA_WHATSAPP: 'Prueba de WhatsApp',
  CONFIGURAR_CORREO: 'Configurar correo',
  PRUEBA_CORREO: 'Prueba de correo',
  REINTENTAR_NOTIFICACION: 'Reintentar notificación',
  EJECUTAR_RECORDATORIOS: 'Enviar recordatorios',
};

export const TEXTO_ENTIDAD: Record<string, string> = {
  administrador: 'Usuario del panel',
  cuenta_cliente: 'Cuenta de cliente',
  cancha: 'Cancha',
  cancha_foto: 'Foto de cancha',
  resena: 'Reseña',
  dia_especial: 'Día especial',
  configuracion: 'Configuración',
  config_correo: 'Servidor de correo',
  turno: 'Turno',
  reserva: 'Reserva',
  pago: 'Pago',
  comprobante: 'Comprobante',
  notificacion: 'Notificación',
  recordatorio: 'Recordatorio',
  whatsapp: 'WhatsApp',
};

export const TEXTO_ROL: Record<string, string> = {
  ADMIN: 'Administrador',
  RECEPCION: 'Recepción',
  CLIENTE: 'Cliente',
  SISTEMA: 'Sistema',
};

export const TEXTO_SERVICIO: Record<string, string> = {
  'auth-service': 'Accesos',
  'catalogo-service': 'Catálogo',
  'reservas-service': 'Reservas',
  'pagos-service': 'Pagos',
  'notificaciones-service': 'Notificaciones',
};

/** Respaldo: «ALGO_NUEVO» → «Algo nuevo» (sin tildes; solo para valores que aún no están en los diccionarios). */
export const legible = (s: string) => s.charAt(0).toUpperCase() + s.slice(1).toLowerCase().replace(/_/g, ' ');

export const textoAccion = (a: string) => TEXTO_ACCION[a] ?? legible(a);
export const textoEntidad = (e: string) => TEXTO_ENTIDAD[e] ?? legible(e);
export const textoRol = (r: string) => TEXTO_ROL[r] ?? legible(r);
export const textoServicio = (s: string) => TEXTO_SERVICIO[s] ?? s.replace('-service', '');
