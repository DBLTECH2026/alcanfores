import {
  forwardRef,
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';
import { CircleAlert, CircleCheck, type LucideIcon } from 'lucide-react';
import { cx } from '@/utilidades/cx';

interface PropsCampo {
  /** Etiqueta visible (1–2 palabras). */
  label?: ReactNode;
  /** Ayuda corta bajo el campo (≤ 6 palabras). Se oculta si hay error. */
  ayuda?: ReactNode;
  /** Mensaje de error (del esquema Zod o del back). */
  error?: string;
  requerido?: boolean;
  opcional?: boolean;
  /** Borde verde + check (dato verificado o válido). */
  valido?: boolean;
  /** Fondo punteado de solo lectura. */
  lectura?: boolean;
  /** Contador «n/max» a la derecha de la ayuda. */
  contador?: { actual: number; max: number };
  className?: string;
}

interface PropsControl {
  /** Texto fijo a la izquierda («+51», «S/»). */
  prefijo?: ReactNode;
  /** Ícono a la izquierda. */
  icono?: LucideIcon;
  /** Texto fijo a la derecha. */
  sufijo?: ReactNode;
  /** Botón dentro del control a la derecha (lupa, ojo). */
  accion?: ReactNode;
}

/** Envoltura de campo: etiqueta + control + ayuda / error accesibles (aria-describedby, role=alert). */
export function Campo({
  id,
  label,
  ayuda,
  error,
  requerido,
  opcional,
  valido,
  lectura,
  contador,
  className,
  children,
}: PropsCampo & { id: string; children: ReactNode }) {
  return (
    <div className={cx('campo', error && 'is-error', valido && !error && 'is-valido', lectura && 'is-lectura', className)}>
      {label && (
        <label className="campo-label" htmlFor={id}>
          {label}
          {requerido && (
            <span className="req" aria-hidden>
              *
            </span>
          )}
          {opcional && <span className="opc">(opcional)</span>}
        </label>
      )}
      {children}
      {error ? (
        <div className="campo-error" id={`${id}-d`} role="alert">
          <CircleAlert className="ico" aria-hidden />
          <span>{error}</span>
        </div>
      ) : ayuda || contador ? (
        <div className="campo-ayuda" id={`${id}-d`}>
          <span>{ayuda}</span>
          {contador && (
            <span className="tnum">
              {contador.actual}/{contador.max}
            </span>
          )}
        </div>
      ) : null}
    </div>
  );
}

function Afijos({ prefijo, icono: Icono }: PropsControl) {
  if (prefijo) return <span className="control-afijo">{prefijo}</span>;
  if (Icono)
    return (
      <span className="control-icono">
        <Icono className="ico ico-18" aria-hidden />
      </span>
    );
  return null;
}

function Final({ sufijo, accion, valido }: PropsControl & { valido?: boolean }) {
  if (accion) return <>{accion}</>;
  if (sufijo) return <span className="control-afijo der">{sufijo}</span>;
  if (valido)
    return (
      <span className="control-ok">
        <CircleCheck className="ico ico-18" aria-hidden />
      </span>
    );
  return null;
}

type PropsInput = PropsCampo & PropsControl & Omit<InputHTMLAttributes<HTMLInputElement>, 'prefix'>;

/** Campo de texto (text, tel, email, password, search…). Compatible con `register` de react-hook-form. */
export const CampoTexto = forwardRef<HTMLInputElement, PropsInput>(function CampoTexto(
  { label, ayuda, error, requerido, opcional, valido, lectura, contador, className, prefijo, icono, sufijo, accion, id, ...input },
  ref,
) {
  const auto = useId();
  const idc = id ?? auto;
  return (
    <Campo id={idc} {...{ label, ayuda, error, requerido, opcional, valido, lectura, contador, className }}>
      <div className="control">
        <Afijos prefijo={prefijo} icono={icono} />
        <input
          ref={ref}
          id={idc}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || ayuda || contador ? `${idc}-d` : undefined}
          aria-required={requerido || undefined}
          readOnly={lectura || input.readOnly}
          {...input}
        />
        <Final sufijo={sufijo} accion={accion} valido={valido && !error} />
      </div>
    </Campo>
  );
});

type PropsSelect = PropsCampo & Omit<PropsControl, 'accion' | 'sufijo'> & SelectHTMLAttributes<HTMLSelectElement> & {
  opciones: Array<{ valor: string; texto: string }>;
  vacio?: string;
};

/** Selector nativo con el estilo del sistema (flecha propia). */
export const Selector = forwardRef<HTMLSelectElement, PropsSelect>(function Selector(
  { label, ayuda, error, requerido, opcional, valido, lectura, contador, className, prefijo, icono, id, opciones, vacio, ...select },
  ref,
) {
  const auto = useId();
  const idc = id ?? auto;
  return (
    <Campo id={idc} {...{ label, ayuda, error, requerido, opcional, valido, lectura, contador, className }}>
      <div className="control control-chevron">
        <Afijos prefijo={prefijo} icono={icono} />
        <select ref={ref} id={idc} aria-invalid={error ? true : undefined} aria-describedby={error || ayuda ? `${idc}-d` : undefined} {...select}>
          {vacio !== undefined && <option value="">{vacio}</option>}
          {opciones.map((o) => (
            <option key={o.valor} value={o.valor}>
              {o.texto}
            </option>
          ))}
        </select>
      </div>
    </Campo>
  );
});

type PropsArea = PropsCampo & TextareaHTMLAttributes<HTMLTextAreaElement>;

/** Área de texto con contador opcional. */
export const AreaTexto = forwardRef<HTMLTextAreaElement, PropsArea>(function AreaTexto(
  { label, ayuda, error, requerido, opcional, valido, lectura, contador, className, id, rows = 3, ...area },
  ref,
) {
  const auto = useId();
  const idc = id ?? auto;
  return (
    <Campo id={idc} {...{ label, ayuda, error, requerido, opcional, valido, lectura, contador, className }}>
      <div className="control">
        <textarea
          ref={ref}
          id={idc}
          rows={rows}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || ayuda || contador ? `${idc}-d` : undefined}
          {...area}
        />
      </div>
    </Campo>
  );
});

type PropsCasilla = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & {
  children: ReactNode;
  error?: string;
  ayuda?: ReactNode;
  radio?: boolean;
};

/** Casilla (checkbox) o radio con la caja del sistema. El estado marcado se pinta con CSS (:checked). */
export const Casilla = forwardRef<HTMLInputElement, PropsCasilla>(function Casilla({ children, error, ayuda, radio, className, id, ...input }, ref) {
  const auto = useId();
  const idc = id ?? auto;
  return (
    <div className={cx('grid gap-1.5', className)}>
      <label className={cx('check', radio && 'radio', error && 'is-error', input.disabled && 'is-deshabilitado')} htmlFor={idc}>
        <input ref={ref} id={idc} type={radio ? 'radio' : 'checkbox'} aria-invalid={error ? true : undefined} aria-describedby={error ? `${idc}-e` : undefined} {...input} />
        <span className="check-caja" aria-hidden>
          {!radio && (
            <svg className="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20 6 9 17l-5-5" />
            </svg>
          )}
        </span>
        <span>
          {children}
          {ayuda && <span className="block t-sec mt-0.5">{ayuda}</span>}
        </span>
      </label>
      {error && (
        <div className="campo-error pl-[34px]" id={`${idc}-e`} role="alert">
          <CircleAlert className="ico" aria-hidden />
          <span>{error}</span>
        </div>
      )}
    </div>
  );
});

/** Interruptor accesible (role=switch). */
export function Interruptor({ activo, alCambiar, children, deshabilitado }: { activo: boolean; alCambiar: (v: boolean) => void; children?: ReactNode; deshabilitado?: boolean }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={activo}
      disabled={deshabilitado}
      className={cx('switch', activo && 'is-activo', deshabilitado && 'is-deshabilitado')}
      onClick={() => alCambiar(!activo)}
    >
      <span className="switch-pista" aria-hidden />
      {children && <span>{children}</span>}
    </button>
  );
}
