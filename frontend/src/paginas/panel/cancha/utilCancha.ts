import type { HorarioAdmin } from '@/api/admin/tipos';
import { semanaPorDefecto } from '@/utilidades/horario';
import { esquemaCancha } from '../esquemasPanel';
import type { Entrada, Salida } from '../CanchaFormulario';

/** Horario por defecto si el back aún no lo trae (SPEC_V2 §0: 8:00 a. m.–11:00 p. m., turnos de 1 h, feriado abierto con S/ 10). */
export const HORARIO_DEFECTO: HorarioAdmin = { duracionTurnoMin: 60, inicioNoche: '18:00', dias: semanaPorDefecto(), feriado: { modo: 'ABIERTA', recargo: 10 } };

/** Errores de esquemaCancha por campo (para pintarlos en los paneles). */
export function erroresDe(entrada: Entrada): Record<string, string> {
  const r = esquemaCancha.safeParse(entrada);
  if (r.success) return {};
  const e: Record<string, string> = {};
  for (const i of r.error.issues) {
    const k = String(i.path[0] ?? '');
    if (k && !e[k]) e[k] = i.message;
  }
  return e;
}

/** Cuerpo del PUT a partir de la entrada del formulario (ya validada). */
export function cuerpoDe(entrada: Entrada) {
  const d = esquemaCancha.parse(entrada) as Salida;
  return { ...d, medidas: d.medidas || undefined, descripcionLarga: d.descripcionLarga || undefined };
}
