import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X, type LucideIcon } from 'lucide-react';
import { cx } from '@/utilidades/cx';
import type { IdBusto, IdIlustracion } from '@/utilidades/recursos';
import { Busto, Ilustracion } from './Marca';

export interface PropsModal {
  abierto: boolean;
  alCerrar: () => void;
  titulo: ReactNode;
  subtitulo?: ReactNode;
  children?: ReactNode;
  /** Botones del pie en orden [secundario, primario] (en móvil el primario queda arriba). */
  acciones?: ReactNode;
  ilustracion?: IdIlustracion;
  icono?: LucideIcon;
  tono?: 'normal' | 'peligro' | 'aviso';
  /** Busto (modales oscuros del panel). */
  busto?: IdBusto;
  /** Franja de montos del panel: [[etiqueta, valor, 'verde'?]] */
  franja?: Array<[string, string, string?]>;
  /** 'hoja' = hoja inferior en < 768 px y modal centrado desde 768. */
  variante?: 'centro' | 'hoja';
  tamano?: 'sm' | 'md' | 'lg' | 'xl';
  /** Sin botón X (decisiones obligatorias). */
  sinCerrar?: boolean;
  /** No cerrar al tocar el fondo. */
  estatico?: boolean;
  className?: string;
}

const ENFOCABLES = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Modal accesible (role=dialog, aria-modal, foco atrapado, Esc cierra, devuelve el foco).
 * En el portal es blanco; dentro del panel (.ma) se vuelve oscuro automáticamente (panel.css).
 */
export function Modal({
  abierto,
  alCerrar,
  titulo,
  subtitulo,
  children,
  acciones,
  ilustracion,
  icono: Icono,
  tono = 'normal',
  busto,
  franja,
  variante = 'centro',
  tamano = 'md',
  sinCerrar,
  estatico,
  className,
}: PropsModal) {
  const idTitulo = useId();
  const caja = useRef<HTMLDivElement>(null);
  const cerrarRef = useRef(alCerrar);
  cerrarRef.current = alCerrar;

  useEffect(() => {
    if (!abierto) return undefined;
    const previo = document.activeElement as HTMLElement | null;
    const scroll = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const t = window.setTimeout(() => {
      const el = caja.current?.querySelector<HTMLElement>('[data-autofoco]') ?? caja.current?.querySelector<HTMLElement>(ENFOCABLES);
      (el ?? caja.current)?.focus();
    }, 10);
    const alTeclear = (e: KeyboardEvent) => {
      // Solo responde el diálogo de más arriba: si encima hay otro (visor de la foto, otro modal),
      // su Esc lo cierra a él y este modal sigue abierto.
      const dialogos = document.querySelectorAll('[role="dialog"][aria-modal="true"]');
      if (dialogos[dialogos.length - 1] !== caja.current) return;
      if (e.key === 'Escape' && !sinCerrar) {
        e.stopPropagation();
        cerrarRef.current();
      }
      if (e.key === 'Tab' && caja.current) {
        const items = Array.from(caja.current.querySelectorAll<HTMLElement>(ENFOCABLES));
        if (!items.length) return;
        const primero = items[0];
        const ultimo = items[items.length - 1];
        if (e.shiftKey && document.activeElement === primero) {
          e.preventDefault();
          ultimo?.focus();
        } else if (!e.shiftKey && document.activeElement === ultimo) {
          e.preventDefault();
          primero?.focus();
        }
      }
    };
    document.addEventListener('keydown', alTeclear);
    return () => {
      window.clearTimeout(t);
      document.removeEventListener('keydown', alTeclear);
      document.body.style.overflow = scroll;
      previo?.focus?.();
    };
  }, [abierto, sinCerrar]);

  if (!abierto) return null;
  const centrado = Boolean(ilustracion);

  return createPortal(
    <div
      className={cx('capa-modal', variante === 'hoja' && 'hoja')}
      onMouseDown={(e) => {
        if (!estatico && !sinCerrar && e.target === e.currentTarget) alCerrar();
      }}
    >
      <div
        ref={caja}
        className={cx('modal animate-entrar', tamano !== 'md' && `modal-${tamano}`, centrado && 'centrado', className)}
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitulo}
        tabIndex={-1}
      >
        <div className="hoja-asa" aria-hidden />
        {ilustracion && (
          <div className="modal-ilustracion">
            <Ilustracion id={ilustracion} carga="eager" />
          </div>
        )}
        <div className="modal-cab">
          {busto && !ilustracion ? (
            <Busto id={busto} className="modal-busto" />
          ) : Icono && !ilustracion ? (
            <span className={cx('modal-icono', tono === 'peligro' && 'peligro', tono === 'aviso' && 'aviso')}>
              <Icono className="ico ico-24" aria-hidden />
            </span>
          ) : null}
          <div className="modal-cab-txt">
            <h2 className="modal-titulo" id={idTitulo}>
              {titulo}
            </h2>
            {subtitulo && <p className="modal-sub">{subtitulo}</p>}
          </div>
          {!sinCerrar && !centrado && (
            <button type="button" className="modal-cerrar" aria-label="Cerrar" onClick={alCerrar}>
              <X className="ico" aria-hidden />
            </button>
          )}
        </div>
        {franja && (
          <div className="modal-franja" style={{ ['--n' as string]: franja.length }}>
            {franja.map(([k, v, c]) => (
              <div key={k}>
                <span>{k}</span>
                <b className={c}>{v}</b>
              </div>
            ))}
          </div>
        )}
        {children && <div className="modal-cuerpo">{children}</div>}
        {acciones && <div className="modal-acciones">{acciones}</div>}
      </div>
    </div>,
    document.body,
  );
}
