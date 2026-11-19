import { z } from 'zod';
import { MSJ, esquemaCelular, esquemaCorreo, esquemaDni, esquemaMotivo, esquemaNombre, esquemaUsuario } from '@/esquemas/comunes';
import { RE_CELULAR, RE_OPERACION, normalizarCelular, rucValido } from '@/utilidades/documentos';

/**
 * Esquemas Zod del PANEL con los MISMOS mensajes que devuelve el back (Bean Validation de cada servicio,
 * comprobados con peticiones reales). Los del SPEC §3 vienen de `MSJ`.
 */
export const MSJ_PANEL = {
  clave: 'La contraseña debe tener de 8 a 64 caracteres, con al menos una mayúscula, una minúscula y un número.',
  bloqueo: 'Indica el motivo del bloqueo (3 a 120 caracteres).',
  rangoHoras: 'La hora final debe ser posterior a la inicial.',
  rangoFechas: 'La fecha final debe ser igual o posterior a la inicial.',
  monto: 'Ingresa un monto mayor a cero con hasta 2 decimales.',
  respuesta: 'La respuesta debe tener entre 10 y 500 caracteres.',
  mensajeWa: 'Escribe el mensaje de prueba (1 a 1000 caracteres).',
  correoUsuario: 'Ingresa un correo válido de hasta 100 caracteres.',
  apellidos: 'Ingresa los apellidos.',
  nota: 'La nota tiene como máximo 255 caracteres.',
  turno: 'Elige un turno.',
  origen: 'Indica si la reserva es presencial o telefónica.',
  tipoPago: 'Indica si el pago es adelanto o total.',
  // cancha (catalogo-service)
  canchaNombre: 'El nombre debe tener entre 3 y 60 caracteres.',
  canchaDeporte: 'Elige el deporte de la cancha.',
  canchaSuperficie: 'La superficie debe tener entre 3 y 40 caracteres.',
  canchaCapacidad: 'La capacidad va de 2 a 30 jugadores.',
  canchaDescripcion: 'La descripción corta debe tener entre 10 y 255 caracteres.',
  canchaDescripcionLarga: 'La descripción larga tiene como máximo 2000 caracteres.',
  canchaMedidas: 'Escribe las medidas como «30 x 18 m».',
  canchaCaracteristica: 'Cada característica debe tener entre 2 y 60 caracteres.',
  canchaCaracteristicas: 'Agrega como máximo 12 características.',
  canchaPrecio: 'El precio debe estar entre S/ 10.00 y S/ 999.00.',
  canchaNoche: 'El precio de noche no puede ser menor que el de día.',
  canchaOrden: 'El orden va de 1 a 99.',
  leyenda: 'La leyenda tiene como máximo 120 caracteres.',
} as const;

const tieneClave = (v: string) => /[A-Z]/.test(v) && /[a-z]/.test(v) && /\d/.test(v);
export const esquemaClave = z
  .string()
  .min(8, MSJ_PANEL.clave)
  .max(64, MSJ_PANEL.clave)
  .refine(tieneClave, MSJ_PANEL.clave);

// ------------------------------ reservas y pagos ------------------------------
export const esquemaRechazo = z.object({ observacion: esquemaMotivo });
export const esquemaAnular = z.object({ motivo: esquemaMotivo });
export const esquemaCancelarAdmin = z.object({ motivo: esquemaMotivo, devolucion: z.boolean() });

const RE_MONTO = /^\d{1,4}(\.\d{1,2})?$/;
/** Monto escrito («45», «45.5», «45.50») → número; > 0 y 2 decimales. */
export const esquemaMonto = z
  .string()
  .trim()
  .regex(RE_MONTO, MSJ_PANEL.monto)
  .transform(Number)
  .refine((n) => n > 0, MSJ_PANEL.monto);

/** Datos del comprobante (boleta o factura) con las reglas del SPEC §3. */
const camposComprobante = {
  tipoComprobante: z.enum(['BOLETA', 'FACTURA']),
  ruc: z.string().trim().optional().default(''),
  razonSocial: z.string().trim().optional().default(''),
  direccionFiscal: z.string().trim().optional().default(''),
};
function validarFactura(d: { tipoComprobante: 'BOLETA' | 'FACTURA'; ruc: string; razonSocial: string; direccionFiscal: string }, ctx: z.RefinementCtx, prefijo = '') {
  if (d.tipoComprobante !== 'FACTURA') return;
  if (!rucValido(d.ruc)) ctx.addIssue({ code: z.ZodIssueCode.custom, path: [`${prefijo}ruc`], message: MSJ.ruc });
  else if (!/^(10|20)/.test(d.ruc)) ctx.addIssue({ code: z.ZodIssueCode.custom, path: [`${prefijo}ruc`], message: MSJ.factura });
  if (d.razonSocial.length < 3 || d.razonSocial.length > 150) ctx.addIssue({ code: z.ZodIssueCode.custom, path: [`${prefijo}razonSocial`], message: MSJ.factura });
  if (d.direccionFiscal.length < 5 || d.direccionFiscal.length > 200) ctx.addIssue({ code: z.ZodIssueCode.custom, path: [`${prefijo}direccionFiscal`], message: MSJ.factura });
}
export const comprobanteDe = (d: { tipoComprobante: 'BOLETA' | 'FACTURA'; ruc: string; razonSocial: string; direccionFiscal: string }) =>
  d.tipoComprobante === 'FACTURA'
    ? { tipo: 'FACTURA' as const, ruc: d.ruc, razonSocial: d.razonSocial, direccionFiscal: d.direccionFiscal }
    : { tipo: 'BOLETA' as const };

const METODOS_CAJA = ['EFECTIVO', 'YAPE', 'PLIN', 'TARJETA_POS'] as const;

/** Cobro en caja: monto > 0, ≤ saldo pendiente; Yape/Plin piden número de operación. */
export function crearEsquemaCaja(saldo: number) {
  return z
    .object({
      tipoPago: z.enum(['ADELANTO', 'TOTAL', 'SALDO']),
      metodo: z.enum(METODOS_CAJA, { errorMap: () => ({ message: MSJ.metodo }) }),
      monto: esquemaMonto,
      codigoOperacion: z.string().trim().optional().default(''),
      ...camposComprobante,
    })
    .superRefine((d, ctx) => {
      if (d.monto > saldo + 0.001) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['monto'], message: `El monto no puede superar el saldo de S/ ${saldo.toFixed(2)}.` });
      if ((d.metodo === 'YAPE' || d.metodo === 'PLIN') && !RE_OPERACION.test(d.codigoOperacion))
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['codigoOperacion'], message: MSJ.operacion });
      if (d.codigoOperacion && !RE_OPERACION.test(d.codigoOperacion)) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['codigoOperacion'], message: MSJ.operacion });
      validarFactura(d, ctx);
    });
}

/** Reserva manual (POST /api/admin/reservas): cliente + turno + origen + nota + pago inicial opcional. */
export function crearEsquemaManual(total: number) {
  return z
    .object({
      idTurno: z.number({ invalid_type_error: MSJ_PANEL.turno, required_error: MSJ_PANEL.turno }).int().positive(MSJ_PANEL.turno),
      dni: esquemaDni,
      nombres: esquemaNombre,
      apellidos: esquemaNombre,
      telefono: esquemaCelular,
      correo: esquemaCorreo,
      origen: z.enum(['PRESENCIAL', 'TELEFONICA'], { errorMap: () => ({ message: MSJ_PANEL.origen }) }),
      nota: z.string().trim().max(255, MSJ_PANEL.nota).optional().default(''),
      conPago: z.boolean(),
      tipoPago: z.enum(['ADELANTO', 'TOTAL'], { errorMap: () => ({ message: MSJ_PANEL.tipoPago }) }),
      metodo: z.enum(METODOS_CAJA, { errorMap: () => ({ message: MSJ.metodo }) }),
      monto: z.string().trim().optional().default(''),
      codigoOperacion: z.string().trim().optional().default(''),
      informado: z.boolean().refine((v) => v, 'Confirma que informaste al cliente del aviso de privacidad.'),
      ...camposComprobante,
    })
    .superRefine((d, ctx) => {
      if (!d.conPago) return;
      const m = esquemaMonto.safeParse(d.monto);
      if (!m.success) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['monto'], message: MSJ_PANEL.monto });
      else if (total > 0 && m.data > total + 0.001) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['monto'], message: `El monto no puede superar el total de S/ ${total.toFixed(2)}.` });
      else if (d.tipoPago === 'TOTAL' && total > 0 && Math.abs(m.data - total) > 0.001)
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['monto'], message: `El pago total es S/ ${total.toFixed(2)}.` });
      if ((d.metodo === 'YAPE' || d.metodo === 'PLIN') && !RE_OPERACION.test(d.codigoOperacion))
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['codigoOperacion'], message: MSJ.operacion });
      validarFactura(d, ctx);
    });
}

// ------------------------------ agenda ------------------------------
export const esquemaMotivoBloqueo = z.string().trim().min(3, MSJ_PANEL.bloqueo).max(120, MSJ_PANEL.bloqueo);
export const esquemaBloqueo = z.object({ motivo: esquemaMotivoBloqueo });
export const esquemaRango = z
  .object({
    idCancha: z.string().min(1, 'Elige la cancha.'),
    fecha: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Elige la fecha.'),
    horaDesde: z.string().regex(/^\d{2}:\d{2}$/, 'Elige la hora inicial.'),
    horaHasta: z.string().regex(/^\d{2}:\d{2}$/, 'Elige la hora final.'),
    motivo: esquemaMotivoBloqueo,
  })
  .refine((d) => d.horaHasta > d.horaDesde, { path: ['horaHasta'], message: MSJ_PANEL.rangoHoras });
export const esquemaGenerar = z
  .object({
    desde: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Elige la fecha inicial.'),
    hasta: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Elige la fecha final.'),
    idsCancha: z.array(z.number()),
  })
  .refine((d) => d.hasta >= d.desde, { path: ['hasta'], message: MSJ_PANEL.rangoFechas });

// ------------------------------ catálogo ------------------------------
const RE_MEDIDAS = /^$|^\d{1,3}([.,]\d{1,2})? ?x ?\d{1,3}([.,]\d{1,2})? ?m$/;
const precio = z
  .string()
  .trim()
  .regex(/^\d{1,3}(\.\d{1,2})?$/, MSJ_PANEL.canchaPrecio)
  .transform(Number)
  .refine((n) => n >= 10 && n <= 999, MSJ_PANEL.canchaPrecio);
export const esquemaCancha = z
  .object({
    nombre: z.string().trim().min(3, MSJ_PANEL.canchaNombre).max(60, MSJ_PANEL.canchaNombre),
    deporte: z.enum(['FUTBOL_5', 'FUTBOL_6', 'FUTBOL_7', 'VOLEY', 'BASQUET', 'MULTIUSO'], { errorMap: () => ({ message: MSJ_PANEL.canchaDeporte }) }),
    superficie: z.string().trim().min(3, MSJ_PANEL.canchaSuperficie).max(40, MSJ_PANEL.canchaSuperficie),
    capacidad: z
      .string()
      .trim()
      .regex(/^\d{1,2}$/, MSJ_PANEL.canchaCapacidad)
      .transform(Number)
      .refine((n) => n >= 2 && n <= 30, MSJ_PANEL.canchaCapacidad),
    descripcion: z.string().trim().min(10, MSJ_PANEL.canchaDescripcion).max(255, MSJ_PANEL.canchaDescripcion),
    descripcionLarga: z.string().trim().max(2000, MSJ_PANEL.canchaDescripcionLarga).optional().default(''),
    techada: z.boolean(),
    iluminacionLed: z.boolean(),
    medidas: z.string().trim().max(40, MSJ_PANEL.canchaMedidas).regex(RE_MEDIDAS, MSJ_PANEL.canchaMedidas).optional().default(''),
    caracteristicas: z
      .array(z.string().trim().min(2, MSJ_PANEL.canchaCaracteristica).max(60, MSJ_PANEL.canchaCaracteristica))
      .max(12, MSJ_PANEL.canchaCaracteristicas),
    precioDia: precio,
    precioNoche: precio,
    orden: z
      .string()
      .trim()
      .regex(/^\d{1,2}$/, MSJ_PANEL.canchaOrden)
      .transform(Number)
      .refine((n) => n >= 1 && n <= 99, MSJ_PANEL.canchaOrden),
    estado: z.enum(['ACTIVA', 'INACTIVA']),
  })
  .refine((d) => d.precioNoche >= d.precioDia, { path: ['precioNoche'], message: MSJ_PANEL.canchaNoche });
export const esquemaCaracteristica = z.string().trim().min(2, MSJ_PANEL.canchaCaracteristica).max(60, MSJ_PANEL.canchaCaracteristica);
export const esquemaLeyenda = z.string().trim().max(120, MSJ_PANEL.leyenda);
export const esquemaRespuesta = z.object({ respuesta: z.string().trim().min(10, MSJ_PANEL.respuesta).max(500, MSJ_PANEL.respuesta) });

// ------------------------------ usuarios ------------------------------
const campoUsuario = {
  nombres: esquemaNombre,
  apellidos: z.string().trim().min(1, MSJ_PANEL.apellidos).pipe(esquemaNombre),
  usuario: esquemaUsuario,
  correo: z.string().trim().min(1, MSJ_PANEL.correoUsuario).max(100, MSJ_PANEL.correoUsuario).email(MSJ_PANEL.correoUsuario),
  rol: z.enum(['ADMIN', 'RECEPCION']),
};
export const esquemaUsuarioNuevo = z
  .object({ ...campoUsuario, contrasena: esquemaClave, confirmacion: z.string() })
  .refine((d) => d.contrasena === d.confirmacion, { path: ['confirmacion'], message: 'Las contraseñas no coinciden.' });
export const esquemaUsuarioEditar = z.object(campoUsuario);
export const esquemaRestablecer = z
  .object({ nueva: esquemaClave, confirmacion: z.string() })
  .refine((d) => d.nueva === d.confirmacion, { path: ['confirmacion'], message: 'Las contraseñas no coinciden.' });

// ------------------------------ WhatsApp ------------------------------
export const esquemaPruebaWa = z.object({
  telefono: esquemaCelular,
  mensaje: z.string().trim().min(1, MSJ_PANEL.mensajeWa).max(1000, MSJ_PANEL.mensajeWa),
});
export const esquemaConectarWa = z.object({
  telefono: z
    .string()
    .transform(normalizarCelular)
    .refine((v) => v === '' || RE_CELULAR.test(v), MSJ.celular),
});

// ------------------------------ configuración (catalogo-service · ClaveConfiguracion) ------------------------------
export type TipoClave = 'PORCENTAJE' | 'ENTERO' | 'HORA' | 'CELULAR' | 'TEXTO' | 'CORREO' | 'URL' | 'LATITUD' | 'LONGITUD' | 'RUC';
export interface DefClave {
  clave: string;
  etiqueta: string;
  tipo: TipoClave;
  min?: number;
  max?: number;
  sufijo?: string;
  ayuda?: string;
}
export const GRUPOS_CONFIG: Array<{ id: string; titulo: string; claves: DefClave[] }> = [
  {
    id: 'complejo',
    titulo: 'Complejo',
    claves: [
      { clave: 'nombre_complejo', etiqueta: 'Nombre comercial', tipo: 'TEXTO', min: 3, max: 80 },
      { clave: 'lema', etiqueta: 'Lema del portal', tipo: 'TEXTO', min: 3, max: 120 },
      { clave: 'direccion', etiqueta: 'Dirección', tipo: 'TEXTO', min: 5, max: 200 },
      { clave: 'referencia', etiqueta: 'Referencia', tipo: 'TEXTO', min: 5, max: 200 },
      { clave: 'mapa_url', etiqueta: 'Enlace del mapa', tipo: 'URL', max: 500 },
      { clave: 'latitud', etiqueta: 'Latitud', tipo: 'LATITUD', min: -90, max: 90 },
      { clave: 'longitud', etiqueta: 'Longitud', tipo: 'LONGITUD', min: -180, max: 180 },
    ],
  },
  {
    id: 'contacto',
    titulo: 'Contacto y pagos',
    claves: [
      { clave: 'whatsapp', etiqueta: 'WhatsApp de atención', tipo: 'CELULAR' },
      { clave: 'correo', etiqueta: 'Correo de informes', tipo: 'CORREO', max: 100 },
      { clave: 'yape_numero', etiqueta: 'Yape (número)', tipo: 'CELULAR' },
      { clave: 'yape_titular', etiqueta: 'Yape (titular)', tipo: 'TEXTO', min: 3, max: 60 },
      { clave: 'plin_numero', etiqueta: 'Plin (número)', tipo: 'CELULAR' },
      { clave: 'ruc_emisor', etiqueta: 'RUC emisor (ficticio)', tipo: 'RUC' },
      { clave: 'razon_social_emisor', etiqueta: 'Razón social (ficticia)', tipo: 'TEXTO', min: 3, max: 150 },
    ],
  },
  {
    id: 'horario',
    titulo: 'Horario',
    claves: [
      { clave: 'primer_turno', etiqueta: 'Primer turno', tipo: 'HORA' },
      { clave: 'ultimo_turno', etiqueta: 'Cierre', tipo: 'HORA', ayuda: 'El último turno empieza una duración antes' },
      { clave: 'duracion_min', etiqueta: 'Duración del turno', tipo: 'ENTERO', min: 30, max: 180, sufijo: 'min' },
      { clave: 'inicio_noche', etiqueta: 'Tarifa de noche desde', tipo: 'HORA' },
      { clave: 'dias_visibles', etiqueta: 'Días para reservar', tipo: 'ENTERO', min: 1, max: 60, sufijo: 'días' },
    ],
  },
  {
    id: 'reglas',
    titulo: 'Reglas',
    claves: [
      { clave: 'adelanto_pct', etiqueta: 'Adelanto', tipo: 'PORCENTAJE', min: 0, max: 100, sufijo: '%' },
      { clave: 'minutos_para_pagar', etiqueta: 'Plazo para pagar', tipo: 'ENTERO', min: 5, max: 1440, sufijo: 'min' },
      { clave: 'devolucion_horas', etiqueta: 'Devolución si cancela antes de', tipo: 'ENTERO', min: 1, max: 168, sufijo: 'h' },
      { clave: 'recordatorio_horas', etiqueta: 'Recordatorio antes del partido', tipo: 'ENTERO', min: 1, max: 48, sufijo: 'h' },
    ],
  },
];
export const DEFS_CONFIG = GRUPOS_CONFIG.flatMap((g) => g.claves);

const RE_HORA = /^([01]\d|2[0-3]):[0-5]\d$/;
const RE_CORREO_CONF = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;
const RE_DECIMAL = /^-?\d{1,3}(\.\d{1,8})?$/;

/** Valida un valor como lo hace `ValidadorConfiguracion` del back. Devuelve el mensaje o null. */
export function validarClave(def: DefClave, valor: string): string | null {
  const v = valor.trim();
  if (!v) return 'Este dato es obligatorio.';
  switch (def.tipo) {
    case 'PORCENTAJE':
    case 'ENTERO': {
      const msj = def.tipo === 'PORCENTAJE' ? `Ingresa un porcentaje entero entre ${def.min} y ${def.max}.` : `Ingresa un número entero entre ${def.min} y ${def.max}.`;
      if (!/^-?\d{1,9}$/.test(v)) return msj;
      const n = Number(v);
      return n < (def.min ?? 0) || n > (def.max ?? 0) ? msj : null;
    }
    case 'HORA':
      return RE_HORA.test(v) ? null : 'Usa el formato de hora HH:mm (por ejemplo, 08:00).';
    case 'CELULAR':
      return RE_CELULAR.test(normalizarCelular(v)) ? null : MSJ.celular;
    case 'CORREO':
      return v.length <= (def.max ?? 100) && RE_CORREO_CONF.test(v) ? null : `Ingresa un correo válido (máximo ${def.max} caracteres).`;
    case 'URL': {
      const msj = `Ingresa un enlace que empiece con https:// (máximo ${def.max} caracteres).`;
      if (v.length > (def.max ?? 500) || !v.startsWith('https://') || /\s/.test(v)) return msj;
      try {
        return new URL(v).hostname.includes('.') ? null : msj;
      } catch {
        return msj;
      }
    }
    case 'LATITUD':
    case 'LONGITUD': {
      const msj = `${def.tipo === 'LATITUD' ? 'La latitud' : 'La longitud'} debe ser un número entre ${def.min} y ${def.max} (con punto decimal).`;
      if (!RE_DECIMAL.test(v)) return msj;
      const n = Number(v);
      return n < (def.min ?? 0) || n > (def.max ?? 0) ? msj : null;
    }
    case 'RUC':
      return rucValido(v) ? null : MSJ.ruc;
    case 'TEXTO':
      if (v.length < (def.min ?? 0) || v.length > (def.max ?? 999)) return `Escribe entre ${def.min} y ${def.max} caracteres.`;
      return /[<>]/.test(v) ? 'No uses los caracteres < ni >.' : null;
    default:
      return null;
  }
}

/** Esquema Zod de la configuración completa (por clave + reglas entre claves del back). */
export const esquemaConfiguracion = z.record(z.string()).superRefine((c, ctx) => {
  for (const def of DEFS_CONFIG) {
    const m = validarClave(def, c[def.clave] ?? '');
    if (m) ctx.addIssue({ code: z.ZodIssueCode.custom, path: [def.clave], message: m });
  }
  const p = c.primer_turno ?? '';
  const u = c.ultimo_turno ?? '';
  const n = c.inicio_noche ?? '';
  const dur = Number(c.duracion_min);
  if (!RE_HORA.test(p) || !RE_HORA.test(u) || !RE_HORA.test(n) || !Number.isInteger(dur)) return;
  if (dur % 15 !== 0) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['duracion_min'], message: 'La duración debe ser múltiplo de 15 minutos.' });
  if (u <= p) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['ultimo_turno'], message: 'La hora de cierre debe ser posterior al primer turno.' });
  else {
    const min = (h: string) => Number(h.slice(0, 2)) * 60 + Number(h.slice(3));
    if (min(u) - min(p) < dur) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['duracion_min'], message: 'Entre el primer turno y el cierre no entra ni un turno de esa duración.' });
    if (n < p || n > u) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['inicio_noche'], message: 'La tarifa de noche debe empezar entre el primer turno y la hora de cierre.' });
  }
});
