import { useId, useRef, type KeyboardEvent } from 'react';
import { CircleAlert } from 'lucide-react';
import { cx } from '@/utilidades/cx';

function Estrella({ relleno, className }: { relleno: number; className?: string }) {
  const id = useId();
  return (
    <svg viewBox="0 0 24 24" className={cx('estrella', className)} aria-hidden>
      <defs>
        <linearGradient id={id}>
          <stop offset={`${relleno * 100}%`} stopColor="currentColor" />
          <stop offset={`${relleno * 100}%`} stopColor="transparent" />
        </linearGradient>
      </defs>
      <path
        d="M12 2.6l2.82 5.72 6.31.92-4.57 4.45 1.08 6.29L12 17.01l-5.64 2.97 1.08-6.29L2.87 9.24l6.31-.92z"
        fill={`url(#${id})`}
        stroke="currentColor"
        strokeWidth={1.6}
        strokeLinejoin="round"
      />
    </svg>
  );
}

/** Estrellas de solo lectura (admite medias): <Estrellas valor={4.6} />. */
export function Estrellas({ valor, tamano = 16, className, conTexto }: { valor: number; tamano?: number; className?: string; conTexto?: boolean }) {
  return (
    <span className={cx('estrellas', className)} style={{ ['--tam' as string]: `${tamano}px` }}>
      <span className="sr-only">{`${valor.toFixed(1)} de 5 estrellas`}</span>
      {[1, 2, 3, 4, 5].map((n) => (
        <Estrella key={n} relleno={Math.max(0, Math.min(1, valor - (n - 1)))} />
      ))}
      {conTexto && (
        <b className="tnum ml-1 text-[13px]" aria-hidden>
          {valor.toFixed(1)}
        </b>
      )}
    </span>
  );
}

const TEXTOS = ['', 'Mala', 'Regular', 'Buena', 'Muy buena', 'Excelente'];

/** Selector de 1 a 5 estrellas accesible (radiogroup; flechas cambian el valor). */
export function EstrellasEntrada({ valor, alCambiar, error, etiqueta = 'Tu calificación', id }: { valor: number; alCambiar: (v: number) => void; error?: string; etiqueta?: string; id?: string }) {
  const refs = useRef<Array<HTMLButtonElement | null>>([]);
  const auto = useId();
  const idc = id ?? auto;
  const mover = (e: KeyboardEvent, n: number) => {
    let nuevo = n;
    if (e.key === 'ArrowRight' || e.key === 'ArrowUp') nuevo = Math.min(5, n + 1);
    else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') nuevo = Math.max(1, n - 1);
    else return;
    e.preventDefault();
    alCambiar(nuevo);
    refs.current[nuevo - 1]?.focus();
  };
  return (
    <div className={cx('campo', error && 'is-error')}>
      <span className="campo-label" id={`${idc}-l`}>
        {etiqueta}
        <span className="req" aria-hidden>
          *
        </span>
      </span>
      <div className="flex items-center gap-3 flex-wrap">
        <div className="estrellas-entrada" role="radiogroup" aria-labelledby={`${idc}-l`} aria-describedby={error ? `${idc}-e` : undefined} aria-invalid={error ? true : undefined}>
          {[1, 2, 3, 4, 5].map((n) => (
            <button
              key={n}
              ref={(el) => {
                refs.current[n - 1] = el;
              }}
              type="button"
              role="radio"
              aria-checked={valor === n}
              aria-label={`${n} ${n === 1 ? 'estrella' : 'estrellas'}: ${TEXTOS[n]}`}
              tabIndex={valor ? (valor === n ? 0 : -1) : n === 1 ? 0 : -1}
              className={cx('estrella-btn', n <= valor && 'activa')}
              onClick={() => alCambiar(n)}
              onKeyDown={(e) => mover(e, valor || n)}
            >
              <Estrella relleno={n <= valor ? 1 : 0} />
            </button>
          ))}
        </div>
        <span className="t-sec font-semibold text-noche min-w-[80px]" aria-live="polite">
          {TEXTOS[valor]}
        </span>
      </div>
      {error && (
        <div className="campo-error" id={`${idc}-e`} role="alert">
          <CircleAlert className="ico" aria-hidden />
          <span>{error}</span>
        </div>
      )}
    </div>
  );
}
