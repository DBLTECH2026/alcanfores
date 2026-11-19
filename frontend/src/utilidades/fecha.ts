import dayjs, { type Dayjs } from 'dayjs';
import 'dayjs/locale/es';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';
import customParseFormat from 'dayjs/plugin/customParseFormat';

dayjs.extend(utc);
dayjs.extend(timezone);
dayjs.extend(customParseFormat);
dayjs.locale('es');

/** Todo el sistema trabaja en hora de Lima (SPEC §0). */
export const ZONA = 'America/Lima';
dayjs.tz.setDefault(ZONA);

/** Ahora en Lima (independiente de la zona del dispositivo). */
export function ahoraLima(): Dayjs {
  return dayjs().tz(ZONA);
}
/** Hoy en Lima como 'YYYY-MM-DD'. */
export function hoyLima(): string {
  return ahoraLima().format('YYYY-MM-DD');
}
/** Interpreta una fecha-hora ISO sin zona ('2026-11-20T19:00:00') como hora de Lima. */
export function deLima(iso: string): Dayjs {
  return dayjs.tz(iso.length <= 10 ? `${iso}T00:00:00` : iso.slice(0, 19), ZONA);
}
/** Fecha-hora ISO sin zona en hora de Lima (formato del contrato). */
export function isoLima(d: Dayjs = ahoraLima()): string {
  return d.tz(ZONA).format('YYYY-MM-DDTHH:mm:ss');
}
export function sumarDias(fecha: string, n: number): string {
  return dayjs(fecha, 'YYYY-MM-DD').add(n, 'day').format('YYYY-MM-DD');
}

const DIAS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];
const DIAS_L = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'set', 'oct', 'nov', 'dic'];
const MESES_L = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'setiembre', 'octubre', 'noviembre', 'diciembre'];

function partes(f: string) {
  const [y = 0, m = 1, d = 1] = f.slice(0, 10).split('-').map(Number);
  return { y, m, d, w: new Date(Date.UTC(y, m - 1, d)).getUTCDay() };
}

export type EstiloFecha = 'corta' | 'media' | 'larga' | 'larga-anio' | 'dia-mes' | 'dia-mes-anio' | 'numero' | 'relativa' | 'mes-anio';

/**
 * fmtFecha('2026-10-16', estilo) — mismos estilos que el prototipo:
 * corta «vie 16 oct» · media «vie 16 oct 2026» · larga «viernes 16 de octubre» · relativa «Hoy» / «Mañana» / corta.
 */
export function fmtFecha(fecha: string | null | undefined, estilo: EstiloFecha = 'corta'): string {
  if (!fecha) return '';
  const { y, m, d, w } = partes(fecha);
  switch (estilo) {
    case 'media': return `${DIAS[w]} ${d} ${MESES[m - 1]} ${y}`;
    case 'larga': return `${DIAS_L[w]} ${d} de ${MESES_L[m - 1]}`;
    case 'larga-anio': return `${DIAS_L[w]} ${d} de ${MESES_L[m - 1]} de ${y}`;
    case 'dia-mes': return `${d} ${MESES[m - 1]}`;
    case 'dia-mes-anio': return `${d} ${MESES[m - 1]} ${y}`;
    case 'numero': return `${String(d).padStart(2, '0')}/${String(m).padStart(2, '0')}/${y}`;
    case 'mes-anio': return `${MESES_L[m - 1]} ${y}`;
    case 'relativa': {
      const hoy = hoyLima();
      if (fecha.slice(0, 10) === hoy) return 'Hoy';
      if (fecha.slice(0, 10) === sumarDias(hoy, 1)) return 'Mañana';
      return `${DIAS[w]} ${d} ${MESES[m - 1]}`;
    }
    default: return `${DIAS[w]} ${d} ${MESES[m - 1]}`;
  }
}
export function diaSemanaCorto(fecha: string): string {
  return DIAS[partes(fecha).w] ?? '';
}
export function mesCorto(fecha: string): string {
  return MESES[partes(fecha).m - 1] ?? '';
}
export function numeroDia(fecha: string): number {
  return partes(fecha).d;
}

/** Espacio duro y unión sin corte: «8:00 p. m.» y «8:00–9:00» nunca se parten en dos líneas. */
const ED = '\u00a0';
const UNIR = '\u2060';
const AM = `a.${ED}m.`;
const PM = `p.${ED}m.`;
/** Guion de rango que no deja cortar la línea a ninguno de sus lados. */
const GUION = `${UNIR}–${UNIR}`;

/** '20:00' → «8:00 p. m.» (el portal usa 12 h; con espacios duros). */
export function fmtHora12(h: string | null | undefined): string {
  if (!h) return '';
  // v2: un cierre a medianoche llega como 23:59(:59) o 24:00.
  if (h.startsWith('23:59') || h.startsWith('24:00')) return `12:00${ED}${AM}`;
  const [H = 0, M = 0] = h.split(':').map(Number);
  const s = H >= 12 ? PM : AM;
  const h12 = H % 12 === 0 ? 12 : H % 12;
  return `${h12}:${String(M).padStart(2, '0')}${ED}${s}`;
}
/** '8:00' → '08:00' (el panel usa 24 h). */
export function fmtHora24(h: string | null | undefined): string {
  if (!h) return '';
  if (h.startsWith('23:59') || h.startsWith('24:00')) return '24:00';
  const [H = 0, M = 0] = h.split(':').map(Number);
  return `${String(H).padStart(2, '0')}:${String(M).padStart(2, '0')}`;
}
/** ('20:00','21:00') → «8:00–9:00 p. m.» · ('11:00','13:00') → «11:00 a. m. – 1:00 p. m.» (sin cortes dentro de cada hora ni en el guion pegado). */
export function fmtRango12(ini: string, fin: string): string {
  const a = fmtHora12(ini);
  const b = fmtHora12(fin);
  // «p. m.» mide 5 caracteres y « p. m.» (con su espacio duro) 6.
  return a.slice(-5) === b.slice(-5) ? `${a.slice(0, -6)}${GUION}${b}` : `${a}${ED}– ${b}`;
}
export function fmtRango24(ini: string, fin: string): string {
  return `${fmtHora24(ini)}${GUION}${fmtHora24(fin)}`;
}
/** '2026-10-13T11:32:00' → «13 oct 2026, 11:32 a. m.» */
export function fmtFechaHora(iso: string | null | undefined, { h24 = false, sinAnio = false } = {}): string {
  if (!iso) return '';
  const [f = '', h = ''] = iso.split('T');
  const { y, m, d } = partes(f);
  return `${d} ${MESES[m - 1]}${sinAnio ? '' : ` ${y}`}, ${h24 ? fmtHora24(h) : fmtHora12(h.slice(0, 5))}`;
}
/** 150 → «2 h 30 min» */
export function fmtDuracion(min: number): string {
  const m = Math.abs(Math.round(min));
  if (m < 60) return `${m} min`;
  if (m < 1440) {
    const h = Math.floor(m / 60);
    const r = m % 60;
    return r ? `${h} h ${r} min` : `${h} h`;
  }
  const d = Math.floor(m / 1440);
  const h = Math.floor((m % 1440) / 60);
  return h ? `${d} d ${h} h` : `${d} d`;
}
/** «hace 5 min» respecto de ahora en Lima. */
export function fmtHace(iso: string): string {
  const min = ahoraLima().diff(deLima(iso), 'minute');
  if (min < 1) return 'hace un momento';
  return `hace ${fmtDuracion(min)}`;
}

/**
 * v2: texto del horario de varios turnos, uniendo los seguidos (igual que `horarioTexto` del back):
 * [19–20, 20–21] → «7:00–9:00 p. m.» · [9–10, 19–20] → «9:00–10:00 a. m. y 7:00–8:00 p. m.» (h24 → «19:00–21:00»).
 */
export function textoHorario(turnos: Array<{ horaInicio: string; horaFin: string }>, h24 = false): string {
  const orden = [...turnos].sort((a, b) => a.horaInicio.localeCompare(b.horaInicio));
  const tramos: Array<[string, string]> = [];
  for (const t of orden) {
    const ultimo = tramos[tramos.length - 1];
    if (ultimo && ultimo[1] === t.horaInicio) ultimo[1] = t.horaFin;
    else tramos.push([t.horaInicio, t.horaFin]);
  }
  const txt = tramos.map(([a, b]) => (h24 ? fmtRango24(a, b) : fmtRango12(a, b)));
  return txt.length <= 1 ? (txt[0] ?? '') : `${txt.slice(0, -1).join(', ')} y ${txt[txt.length - 1]}`;
}

/** Asistencia por defecto: se marca desde 30 min antes del partido (`asistencia_desde_min`). */
export const ASISTENCIA_DESDE_MIN = 30;
/** Hora desde la que se puede marcar la asistencia de un turno (Lima). */
export function inicioAsistencia(fecha: string, horaInicio: string, desdeMin = ASISTENCIA_DESDE_MIN): Dayjs {
  return deLima(`${fecha}T${horaInicio.slice(0, 5)}:00`).subtract(desdeMin, 'minute');
}
/** Respaldo del front cuando el back aún no devuelve `puedeMarcarAsistencia`. */
export function puedeMarcarAsistencia(fecha: string, horaInicio: string, desdeMin = ASISTENCIA_DESDE_MIN): boolean {
  return !ahoraLima().isBefore(inicioAsistencia(fecha, horaInicio, desdeMin));
}
