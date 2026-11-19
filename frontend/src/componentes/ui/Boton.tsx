import { forwardRef, type AnchorHTMLAttributes, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { Link, type LinkProps } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import { cx } from '@/utilidades/cx';

export type VarianteBoton =
  | 'primario'
  | 'secundario'
  | 'oscuro'
  | 'fantasma'
  | 'peligro'
  | 'peligro-contorno'
  | 'whatsapp'
  | 'claro'
  | 'contorno-claro';
export type TamanoBoton = 'sm' | 'md' | 'lg';

interface PropsComunes {
  variante?: VarianteBoton;
  tamano?: TamanoBoton;
  /** Ocupa todo el ancho. */
  bloque?: boolean;
  icono?: LucideIcon;
  iconoDer?: LucideIcon;
  /** Muestra spinner y bloquea el botón. */
  cargando?: boolean;
  textoCargando?: string;
  /** Para botones de solo ícono (obligatorio si no hay texto). */
  etiqueta?: string;
  children?: ReactNode;
  className?: string;
}

export function clasesBoton({ variante = 'primario', tamano = 'md', bloque, soloIcono, cargando, className }: PropsComunes & { soloIcono?: boolean }) {
  return cx('btn', `btn-${variante}`, tamano !== 'md' && `btn-${tamano}`, bloque && 'btn-bloque', soloIcono && 'btn-icono', cargando && 'is-cargando', className);
}

function Contenido({ icono: Icono, iconoDer: IconoDer, cargando, textoCargando, children }: PropsComunes) {
  if (cargando) {
    return (
      <>
        <span className="spinner" aria-hidden />
        {(textoCargando ?? children) && <span>{textoCargando ?? children}</span>}
      </>
    );
  }
  return (
    <>
      {Icono && <Icono className="ico" aria-hidden />}
      {children !== undefined && children !== null && children !== false && <span>{children}</span>}
      {IconoDer && <IconoDer className="ico" aria-hidden />}
    </>
  );
}

/** Botón del sistema (variantes del prototipo). Un solo `primario` verde por vista. */
export const Boton = forwardRef<HTMLButtonElement, PropsComunes & ButtonHTMLAttributes<HTMLButtonElement>>(function Boton(
  { variante, tamano, bloque, icono, iconoDer, cargando, textoCargando, etiqueta, children, className, type = 'button', disabled, onClick, ...resto },
  ref,
) {
  const soloIcono = !children && Boolean(icono);
  return (
    <button
      ref={ref}
      type={type}
      className={clasesBoton({ variante, tamano, bloque, soloIcono, cargando, className })}
      disabled={disabled}
      aria-busy={cargando || undefined}
      aria-disabled={cargando || undefined}
      aria-label={etiqueta}
      onClick={cargando ? (e) => e.preventDefault() : onClick}
      {...resto}
    >
      <Contenido icono={icono} iconoDer={iconoDer} cargando={cargando} textoCargando={textoCargando}>
        {children}
      </Contenido>
    </button>
  );
});

/** Botón que navega dentro de la app (React Router). */
export function BotonEnlace({ variante, tamano, bloque, icono, iconoDer, etiqueta, children, className, ...resto }: PropsComunes & LinkProps) {
  return (
    <Link className={clasesBoton({ variante, tamano, bloque, soloIcono: !children && Boolean(icono), className })} aria-label={etiqueta} {...resto}>
      <Contenido icono={icono} iconoDer={iconoDer}>
        {children}
      </Contenido>
    </Link>
  );
}

/** Botón que abre un enlace externo o una descarga (PDF, WhatsApp). */
export function BotonExterno({ variante, tamano, bloque, icono, iconoDer, etiqueta, children, className, ...resto }: PropsComunes & AnchorHTMLAttributes<HTMLAnchorElement>) {
  return (
    <a
      className={clasesBoton({ variante, tamano, bloque, soloIcono: !children && Boolean(icono), className })}
      aria-label={etiqueta}
      rel={resto.target === '_blank' ? 'noopener noreferrer' : undefined}
      {...resto}
    >
      <Contenido icono={icono} iconoDer={iconoDer}>
        {children}
      </Contenido>
    </a>
  );
}

/** Enlace verde subrayado del sistema. */
export function Enlace({ className, icono: Icono, children, ...resto }: LinkProps & { icono?: LucideIcon }) {
  return (
    <Link className={cx('enlace inline-flex items-center gap-1', className)} {...resto}>
      {children}
      {Icono && <Icono className="ico ico-16" aria-hidden />}
    </Link>
  );
}
