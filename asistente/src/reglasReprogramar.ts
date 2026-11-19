// Reglas del cambio de horario para el cliente y Canchita, las MISMAS de reservas-service (ReprogramadorReservas y
// ReglasReserva), para que la vista previa de reprogramar_reserva no ofrezca un cambio que el back va a rechazar. Quien
// decide sigue siendo el back: al confirmar, sus 409/422 llegan al modelo con su mensaje tal cual. Mismos mensajes y
// mismo redondeo (montos en céntimos, HALF_UP a 2 decimales como Dinero.redondear / Dinero.porcentaje).
import type { ReservaDetalle } from "./api.js";
import { instanteDesdeLima } from "./tiempo.js";

/** ReglasReserva.HORAS_REPROGRAMAR_CLIENTE: el cliente y Canchita cambian hasta 2 horas antes del partido. */
export const HORAS_PARA_CAMBIAR = 2;

export const NO_REPROGRAMABLE = "RESERVA_NO_REPROGRAMABLE";
export const NO_PERMITIDA = "REPROGRAMACION_NO_PERMITIDA";
export const MONTO_MENOR = "REPROGRAMAR_MONTO_MENOR";
export const ADELANTO_INSUFICIENTE = "REPROGRAMAR_ADELANTO_INSUFICIENTE";
export const MISMO_HORARIO = "REPROGRAMAR_MISMO_HORARIO";

/** Los textos de ReprogramadorReservas (más uno genérico para cuando no se sabe el motivo). */
export const MENSAJES = {
  cancelada: "Esta reserva está cancelada; ya no se puede cambiar el horario.",
  asistencia: "Ya se registró la asistencia de esta reserva; ya no se puede cambiar el horario.",
  empezo: "El partido de esta reserva ya empezó; ya no se puede cambiar el horario.",
  plazo: `Los cambios de horario se hacen hasta ${HORAS_PARA_CAMBIAR} horas antes del partido; escríbenos para ayudarte.`,
  limite: "Ya cambiaste el horario una vez; escríbenos para ayudarte.",
  otro: "Ya no se puede cambiar el horario de esta reserva por aquí; escríbenos para ayudarte.",
  mismoHorario: "Elegiste el mismo horario que ya tienes. Elige otro día, hora o cancha.",
  montoMenor: "El nuevo horario cuesta menos de lo que ya pagaste. Elige otro horario o acércate al complejo.",
  adelanto: "El nuevo horario pide un adelanto mayor del que ya pagaste. Elige otro horario o escríbenos para ayudarte.",
} as const;

export interface Rechazo {
  codigo: string;
  mensaje: string;
}

const rechazo = (codigo: string, mensaje: string): Rechazo => ({ codigo, mensaje });

/**
 * Por qué el cliente (o Canchita) ya no puede cambiar el horario, en el mismo orden que el back: cancelada, con
 * asistencia, ya empezó, fuera del plazo de 2 h y, si nada de eso, su único cambio ya se usó. Si el back dice
 * `puedeReprogramar: true` no se discute (null). Si no envía la bandera (versión anterior) solo se revisa lo que se ve
 * en la reserva y el resto lo decide el back al confirmar.
 */
export function motivoSinCambio(r: ReservaDetalle, momento: Date): Rechazo | null {
  if (r.puedeReprogramar === true) return null;
  if (r.estado !== "PENDIENTE" && r.estado !== "CONFIRMADA") return rechazo(NO_REPROGRAMABLE, MENSAJES.cancelada);
  if (r.asistencia && r.asistencia !== "PENDIENTE") return rechazo(NO_REPROGRAMABLE, MENSAJES.asistencia);
  const inicio = instanteDesdeLima(`${r.fecha}T${r.horaInicio}`);
  if (inicio && momento.getTime() >= inicio.getTime()) return rechazo(NO_REPROGRAMABLE, MENSAJES.empezo);
  if (inicio && momento.getTime() >= inicio.getTime() - HORAS_PARA_CAMBIAR * 3_600_000) {
    return rechazo(NO_PERMITIDA, MENSAJES.plazo);
  }
  if (r.puedeReprogramar === undefined) return null;
  // Vigente y a tiempo pero sin permiso: solo queda el límite de un cambio del cliente. `reprogramaciones` cuenta también
  // los del panel (que no lo gastan); si no hay ninguno (p. ej. relojes desfasados) va el texto genérico.
  return rechazo(NO_PERMITIDA, (r.reprogramaciones ?? 0) > 0 ? MENSAJES.limite : MENSAJES.otro);
}

/** Soles → céntimos enteros (los montos de la API ya vienen con 2 decimales). */
export function centimos(monto: number | string | null | undefined): number {
  const n = Number(monto ?? 0);
  return Number.isFinite(n) ? Math.round(n * 100) : 0;
}

/** Dinero.porcentaje: total × pct / 100 redondeado HALF_UP al céntimo (en céntimos, sin errores de coma flotante). */
export function adelantoEnCentimos(totalCentimos: number, adelantoPct: number): number {
  return Math.floor((totalCentimos * adelantoPct + 50) / 100);
}

export interface MontosCambio {
  /** Nuevo total (suma de los precios vigentes de los turnos nuevos), en céntimos. */
  total: number;
  /** Lo pagado y validado (montoPagado), en céntimos. */
  pagado: number;
  /** El comprobante que se está validando (montoEnRevision), en céntimos. */
  enRevision: number;
  adelantoPct: number;
}

/**
 * Comprometido = pagado + comprobante en revisión. El nuevo total no puede ser menor (REPROGRAMAR_MONTO_MENOR) y, si
 * ya pagó algo o tiene un comprobante en revisión, eso debe cubrir el adelanto del nuevo horario
 * (REPROGRAMAR_ADELANTO_INSUFICIENTE). No hay «el equipo revisa la diferencia»: el back simplemente no lo permite.
 */
export function revisarMontos(m: MontosCambio): Rechazo | null {
  const comprometido = m.pagado + m.enRevision;
  if (m.total < comprometido) return rechazo(MONTO_MENOR, MENSAJES.montoMenor);
  if (comprometido > 0 && comprometido < adelantoEnCentimos(m.total, m.adelantoPct)) {
    return rechazo(ADELANTO_INSUFICIENTE, MENSAJES.adelanto);
  }
  return null;
}

/** Mismos turnos que ya tiene (en cualquier orden): REPROGRAMAR_MISMO_HORARIO. */
export function mismoHorario(actuales: number[], nuevos: number[]): boolean {
  const a = new Set(actuales);
  const n = new Set(nuevos);
  return a.size === n.size && [...n].every((id) => a.has(id));
}

/**
 * El cambio YA está hecho: la reserva sigue vigente, tiene justo esos turnos y ya usó su cambio (`reprogramaciones > 0`
 * y `puedeReprogramar` no es true). Es el reintento tras un cambio que reservas guardó pero cuya respuesta no llegó
 * (tiempo agotado o conexión cortada): no es «Ya cambiaste el horario una vez» ni «mismo horario», es «ya quedó hecho».
 * Si aún puede cambiar (p. ej. el equipo la movió desde el panel, que no gasta su cambio) sigue siendo «mismo horario».
 */
export function cambioYaAplicado(r: ReservaDetalle, idsNuevos: number[]): boolean {
  return (r.estado === "PENDIENTE" || r.estado === "CONFIRMADA") && (r.reprogramaciones ?? 0) > 0
    && r.puedeReprogramar !== true && mismoHorario((r.turnos ?? []).map((t) => t.idTurno), idsNuevos);
}

/**
 * Plazo para pagar tras el cambio, como ReprogramadorReservas: `venceEn` se conserva, pero nunca queda después del nuevo
 * inicio (si el nuevo horario empieza antes, vence al empezar). Sin `venceEn` no hay plazo (el back lo deja en null).
 */
export function plazoTrasCambio(venceEn: string | null | undefined, nuevoInicio: Date | null): Date | null {
  const vence = instanteDesdeLima(venceEn);
  if (!vence) return null;
  return nuevoInicio && nuevoInicio.getTime() < vence.getTime() ? nuevoInicio : vence;
}
