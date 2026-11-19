import { Fragment, useEffect, useId, useRef, useState, type ReactNode, type RefObject, type TouchEvent } from 'react';
import { createPortal } from 'react-dom';
import { X, type LucideIcon } from 'lucide-react';
import { cx } from '@/utilidades/cx';
import '@/componentes/panel/panel-extra.css';

const ENFOCABLES = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
/** Lo que dura la salida (igual que la animación pl-salir de panel-extra.css). */
const SALIDA_MS = 200;

export interface PropsPanelLateral {
  abierto: boolean;
  alCerrar: () => void;
  /** Título en negrita (p. ej. el código ALC-XXXXX o «Filtros»). */
  titulo: ReactNode;
  /** Línea gris bajo el título (p. ej. cliente · cancha · hora). */
  subtitulo?: ReactNode;
  /** Fila opcional bajo el título (p. ej. insignias de estado). */
  detalle?: ReactNode;
  children: ReactNode;
  /** Pie fijo (botones o acciones de peligro); respeta la zona segura inferior del iPhone. */
  pie?: ReactNode;
  /** Ícono en cuadro a la izquierda del título. */
  icono?: LucideIcon;
  etiquetaCerrar?: string;
  /** Adónde vuelve el foco al cerrar. Por defecto, al elemento que lo tenía al abrir. */
  devolverFoco?: RefObject<HTMLElement>;
  /** «amplio» = 380 px desde 641 px (Filtros). En celular siempre min(86vw, 360px). */
  ancho?: 'normal' | 'amplio';
  className?: string;
  claseCuerpo?: string;
}

/**
 * «Cliente · Cancha 2 · dom 29 nov 19:00–20:00»: cada tramo es una pieza que solo baja entera a la línea siguiente
 * (la fecha no se parte en «dom 29 / nov»). Un tramo más largo que la línea sí se parte por dentro.
 */
function SubtituloEnTramos({ texto }: { texto: string }) {
  const tramos = texto.split(' · ');
  if (tramos.length < 2) return <>{texto}</>;
  return (
    <>
      {tramos.map((t, i) => (
        <Fragment key={i}>
          <span className="pl-sub-tramo">
            {t}
            {i < tramos.length - 1 && ' ·'}
          </span>
          {i < tramos.length - 1 && ' '}
        </Fragment>
      ))}
    </>
  );
}

/**
 * Panel lateral que entra de DERECHA a IZQUIERDA (menú ⋯ de las filas y Filtros en celular).
 * Fondo oscurecido, cabecera con contexto y X de 40 px, cuerpo con scroll y pie fijo.
 * Cierra con la X, Esc, tocando el fondo o deslizando hacia la derecha; bloquea el scroll de la página,
 * atrapa el foco y lo devuelve al cerrar. Respeta prefers-reduced-motion. Estilos en panel/panel-extra.css (.pl-*).
 */
export function PanelLateral({
  abierto,
  alCerrar,
  titulo,
  subtitulo,
  detalle,
  children,
  pie,
  icono: Icono,
  etiquetaCerrar = 'Cerrar',
  devolverFoco,
  ancho = 'normal',
  className,
  claseCuerpo,
}: PropsPanelLateral) {
  const idTitulo = useId();
  const idSub = useId();
  const caja = useRef<HTMLElement>(null);
  const cerrarRef = useRef(alCerrar);
  cerrarRef.current = alCerrar;
  // Sigue montado mientras dura la animación de salida.
  const [montado, setMontado] = useState(abierto);
  const [arrastrado, setArrastrado] = useState(false);
  if (abierto && !montado) setMontado(true);
  if (abierto && arrastrado) setArrastrado(false);

  useEffect(() => {
    if (abierto || !montado) return undefined;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setMontado(false);
      return undefined;
    }
    const t = window.setTimeout(() => setMontado(false), SALIDA_MS);
    return () => window.clearTimeout(t);
  }, [abierto, montado]);

  // Abierto: bloquea el scroll, pone el foco dentro, atrapa Tab y escucha Esc. Al cerrar devuelve el foco.
  useEffect(() => {
    if (!abierto) return undefined;
    // Si se reabre durante la salida de un deslizamiento, borra la posición que dejó el dedo.
    caja.current?.style.removeProperty('transform');
    caja.current?.style.removeProperty('transition');
    const previo = devolverFoco?.current ?? (document.activeElement as HTMLElement | null);
    const scroll = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const t = window.setTimeout(() => {
      const c = caja.current;
      if (!c) return;
      const el =
        c.querySelector<HTMLElement>('[data-autofoco]') ??
        c.querySelector<HTMLElement>('.pl-cuerpo button:not(:disabled)') ??
        c.querySelector<HTMLElement>('.pl-x');
      (el ?? c).focus({ preventScroll: true });
    }, 30);
    const alTeclear = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        cerrarRef.current();
        return;
      }
      if (e.key !== 'Tab' || !caja.current) return;
      const items = Array.from(caja.current.querySelectorAll<HTMLElement>(ENFOCABLES));
      if (!items.length) return;
      const primero = items[0];
      const ultimo = items[items.length - 1];
      const dentro = caja.current.contains(document.activeElement);
      if (e.shiftKey && (document.activeElement === primero || !dentro)) {
        e.preventDefault();
        ultimo?.focus();
      } else if (!e.shiftKey && (document.activeElement === ultimo || !dentro)) {
        e.preventDefault();
        primero?.focus();
      }
    };
    document.addEventListener('keydown', alTeclear);
    const panel = caja.current;
    return () => {
      window.clearTimeout(t);
      document.removeEventListener('keydown', alTeclear);
      document.body.style.overflow = scroll;
      const activo = document.activeElement;
      const destino = previo;
      // Solo si el foco quedó en el panel o en el vacío (si ya se abrió un modal, no se lo quita).
      if (destino?.isConnected && (!activo || activo === document.body || panel?.contains(activo))) destino.focus({ preventScroll: true });
    };
  }, [abierto, devolverFoco]);

  // Deslizar hacia la derecha cierra (solo gestos horizontales; el scroll vertical sigue normal).
  const gesto = useRef<{ x: number; y: number; dx: number; horizontal: boolean | null } | null>(null);
  const alTocar = (e: TouchEvent) => {
    const p = e.touches[0];
    gesto.current = p ? { x: p.clientX, y: p.clientY, dx: 0, horizontal: null } : null;
  };
  const alMover = (e: TouchEvent) => {
    const g = gesto.current;
    const p = e.touches[0];
    const el = caja.current;
    if (!g || !p || !el) return;
    const dx = p.clientX - g.x;
    const dy = p.clientY - g.y;
    if (g.horizontal === null) {
      if (Math.abs(dx) < 8 && Math.abs(dy) < 8) return;
      g.horizontal = dx > 0 && Math.abs(dx) > Math.abs(dy) * 1.2;
    }
    if (!g.horizontal) return;
    g.dx = Math.max(0, dx);
    el.style.transition = 'none';
    el.style.transform = `translateX(${g.dx}px)`;
  };
  const alSoltar = () => {
    const g = gesto.current;
    const el = caja.current;
    gesto.current = null;
    if (!g?.horizontal || !el) return;
    el.style.transition = `transform ${SALIDA_MS}ms ease-out`;
    if (g.dx > Math.min(110, el.offsetWidth * 0.3)) {
      el.style.transform = 'translateX(105%)';
      setArrastrado(true);
      alCerrar();
    } else {
      el.style.transform = '';
    }
  };

  // El cierre al tocar el fondo exige que el toque empiece y termine en el fondo (no al arrastrar una selección).
  const abajoEnFondo = useRef(false);

  if (!montado) return null;
  return createPortal(
    <div
      className={cx('pl-capa', !abierto && 'saliendo')}
      onMouseDown={(e) => {
        abajoEnFondo.current = e.target === e.currentTarget;
      }}
      onClick={(e) => {
        // Los clics del portal no deben llegar a la fila de la tabla que lo abrió.
        e.stopPropagation();
        if (abierto && abajoEnFondo.current && e.target === e.currentTarget) alCerrar();
        abajoEnFondo.current = false;
      }}
    >
      <aside
        ref={caja}
        className={cx('pl', ancho === 'amplio' && 'pl-amplio', arrastrado && 'arrastrado', className)}
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitulo}
        aria-describedby={subtitulo ? idSub : undefined}
        tabIndex={-1}
        onTouchStart={alTocar}
        onTouchMove={alMover}
        onTouchEnd={alSoltar}
        onTouchCancel={alSoltar}
      >
        <header className="pl-cab">
          {Icono && (
            <span className="pl-ico" aria-hidden>
              <Icono className="ico" />
            </span>
          )}
          <div className="pl-cab-txt">
            <h2 id={idTitulo} className="pl-titulo">
              {titulo}
            </h2>
            {subtitulo && (
              <p id={idSub} className="pl-sub">
                {typeof subtitulo === 'string' ? <SubtituloEnTramos texto={subtitulo} /> : subtitulo}
              </p>
            )}
          </div>
          <button type="button" className="pl-x" aria-label={etiquetaCerrar} onClick={alCerrar}>
            <X className="ico" aria-hidden />
          </button>
          {detalle && <div className="pl-detalle">{detalle}</div>}
        </header>
        <div className={cx('pl-cuerpo', claseCuerpo)}>{children}</div>
        {pie && <footer className="pl-pie">{pie}</footer>}
      </aside>
    </div>,
    document.body,
  );
}
