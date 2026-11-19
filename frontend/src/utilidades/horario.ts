import type { DiaHorario, Franja } from '@/api/tipos';
import { fmtHora12 } from './fecha';

/**
 * v2 (SPEC_V2 §A): utilidades del horario propio de cada cancha.
 * El cierre es la hora en que TERMINA el último turno; medianoche llega como '24:00' o '23:59'.
 */
export const DIAS_SEMANA = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'] as const;
export const DIAS_CORTOS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'] as const;

/** 'HH:mm' → minutos (23:59 y 24:00 = 1440). */
export function aMinutos(h: string): number {
  if (h.startsWith('23:59') || h.startsWith('24:00')) return 1440;
  const [H = 0, M = 0] = h.split(':').map(Number);
  return H * 60 + M;
}
export function deMinutos(m: number): string {
  if (m >= 1440) return '24:00';
  return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
}

/** Horario por defecto (SPEC_V2 §0): todos los días 8:00 a. m.–11:00 p. m. */
export function semanaPorDefecto(apertura = '08:00', cierre = '23:00'): DiaHorario[] {
  return [1, 2, 3, 4, 5, 6, 7].map((d) => ({ diaSemana: d as DiaHorario['diaSemana'], abierto: true, apertura, cierre }));
}

/** Mensaje si el horario de un día no calza con la duración del turno (mismo texto que el back). */
export function errorDia(d: DiaHorario, duracion: number): string | null {
  if (!d.abierto) return null;
  const a = aMinutos(d.apertura);
  const c = aMinutos(d.cierre);
  if (c <= a) return 'El cierre debe ser después de la apertura.';
  const sobra = (c - a) % duracion;
  if (!sobra) return null;
  const antes = deMinutos(c - sobra);
  const despues = deMinutos(c - sobra + duracion);
  const dur = duracion % 60 === 0 ? `${duracion / 60} h` : `${duracion} min`;
  const s = sobra % 60 === 0 ? `${sobra / 60} hora${sobra === 60 ? '' : 's'}` : `${sobra} min`;
  const txt = `Con turnos de ${dur}, de ${fmtHora12(d.apertura)} a ${fmtHora12(d.cierre)} sobra ${s}: ajusta el cierre a ${fmtHora12(antes)}${aMinutos(despues) <= 1440 ? ` o a ${fmtHora12(despues)}` : ''}`;
  return txt.endsWith('.') ? txt : `${txt}.`;
}

export interface TurnoCalculado {
  horaInicio: string;
  horaFin: string;
  precio: number;
  franja: Franja;
}
/** Turnos que saldrían un día (vista previa local; el back tiene la misma regla). */
export function turnosDelDia(d: DiaHorario | undefined, duracion: number, inicioNoche: string, precioDia: number, precioNoche: number): TurnoCalculado[] {
  if (!d?.abierto || !duracion) return [];
  const a = aMinutos(d.apertura);
  const c = aMinutos(d.cierre);
  const noche = aMinutos(inicioNoche);
  const lista: TurnoCalculado[] = [];
  for (let m = a; m + duracion <= c; m += duracion) {
    const esNoche = m >= noche;
    lista.push({ horaInicio: deMinutos(m), horaFin: deMinutos(m + duracion), precio: esNoche ? precioNoche : precioDia, franja: esNoche ? 'NOCHE' : 'DIA' });
  }
  return lista;
}

/** «Lun a vie 8:00 a. m.–11:00 p. m. · Sáb y dom 7:00 a. m.–11:00 p. m.» (agrupa días seguidos con el mismo horario). */
export function textoSemana(dias: DiaHorario[]): string {
  const orden = [...dias].sort((a, b) => a.diaSemana - b.diaSemana);
  const clave = (d: DiaHorario) => (d.abierto ? `${d.apertura}-${d.cierre}` : 'cerrado');
  const grupos: Array<{ desde: number; hasta: number; d: DiaHorario }> = [];
  for (const d of orden) {
    const g = grupos[grupos.length - 1];
    if (g && clave(g.d) === clave(d) && g.hasta === d.diaSemana - 1) g.hasta = d.diaSemana;
    else grupos.push({ desde: d.diaSemana, hasta: d.diaSemana, d });
  }
  if (grupos.length === 1 && grupos[0] && grupos[0].d.abierto && orden.length === 7) {
    return `Todos los días ${fmtHora12(grupos[0].d.apertura)}–${fmtHora12(grupos[0].d.cierre)}`;
  }
  return grupos
    .map((g) => {
      const a = DIAS_CORTOS[g.desde - 1] ?? '';
      const b = (DIAS_CORTOS[g.hasta - 1] ?? '').toLowerCase();
      const dias = g.desde === g.hasta ? a : g.hasta === g.desde + 1 ? `${a} y ${b}` : `${a} a ${b}`;
      return `${dias} ${g.d.abierto ? `${fmtHora12(g.d.apertura)}–${fmtHora12(g.d.cierre)}` : 'cerrado'}`;
    })
    .join(' · ');
}

/** Opciones de hora cada 30 min ('00:00' … '24:00'). */
export const HORAS_MEDIA = Array.from({ length: 49 }, (_, i) => deMinutos(i * 30));
