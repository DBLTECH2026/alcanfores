import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { esErrorApi } from '@/api/cliente';

/**
 * Pinta en el formulario los errores por campo del back (`errores[].campo`).
 * `mapa` traduce nombres del back a campos del formulario (p. ej. 'cliente.dni' → 'dni').
 * Por defecto quita los prefijos `cliente.` y `comprobante.`. Devuelve los campos que no pudo ubicar.
 */
export function aplicarErroresServidor<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  campos: ReadonlyArray<string>,
  mapa: Record<string, string> = {},
): { aplicados: number; sinUbicar: Array<{ campo: string; mensaje: string }> } {
  if (!esErrorApi(error)) return { aplicados: 0, sinUbicar: [] };
  let aplicados = 0;
  const sinUbicar: Array<{ campo: string; mensaje: string }> = [];
  let primero = true;
  for (const { campo, mensaje } of error.errores) {
    const destino = mapa[campo] ?? campo.replace(/^(cliente|comprobante)\./, '');
    if (campos.includes(destino)) {
      setError(destino as Path<T>, { type: 'servidor', message: mensaje }, { shouldFocus: primero });
      primero = false;
      aplicados += 1;
    } else sinUbicar.push({ campo, mensaje });
  }
  return { aplicados, sinUbicar };
}

/** Separa errores del back por paso del asistente según el nombre del campo. */
export function erroresPorCampo(error: unknown): Record<string, string> {
  if (!esErrorApi(error)) return {};
  return Object.fromEntries(error.errores.map((e) => [e.campo.replace(/^(cliente|comprobante)\./, ''), e.mensaje]));
}
