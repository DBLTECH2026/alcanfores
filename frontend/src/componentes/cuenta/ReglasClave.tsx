import { CircleCheck } from 'lucide-react';
import { MSJ_CLAVE_CLIENTE, REGLAS_CLAVE } from '@/esquemas/otros';
import { cx } from '@/utilidades/cx';

/**
 * El mensaje largo de Zod repite las reglas que ya muestra <ReglasClave>: en el campo va uno corto
 * y la lista marca en rojo lo que falta (`conError`).
 */
export function errorClave(mensaje: string | undefined): { error: string | undefined; conError: boolean } {
  return mensaje === MSJ_CLAVE_CLIENTE ? { error: 'Te falta cumplir lo de abajo.', conError: true } : { error: mensaje, conError: false };
}

/**
 * Requisitos de la contraseña nueva, que se marcan en verde al cumplirse (registro, cambio de clave y recuperación).
 * Columnas al ancho de su texto (≈ 270 px): «8 o más caracteres» no se parte a 360 px; más angosto, una sola columna.
 */
export function ReglasClave({ valor, conError, className }: { valor: string; conError?: boolean; className?: string }) {
  return (
    <ul
      className={cx('reglas-clave grid-cols-[repeat(2,max-content)] gap-x-5 max-[349px]:grid-cols-1', conError && 'con-error', className)}
      aria-label="Requisitos de la contraseña"
    >
      {REGLAS_CLAVE.map((r) => {
        const ok = r.cumple(valor);
        return (
          <li key={r.texto} className={cx(ok && 'ok')}>
            {ok ? <CircleCheck className="ico ico-16" aria-hidden /> : <span className="punto" aria-hidden />}
            <span>{r.texto}</span>
            <span className="sr-only">{ok ? '(cumple)' : '(falta)'}</span>
          </li>
        );
      })}
    </ul>
  );
}
