import type { SVGProps } from 'react';
import type { Deporte } from '@/api/tipos';
import { cx } from '@/utilidades/cx';
import { LOGOS, rutaBusto, rutaIlustracion, type IdBusto, type IdIlustracion, type VarianteLogo } from '@/utilidades/recursos';

/** Logo (los marcos usan las versiones SIN eslogan). */
export function Logo({ variante = 'horizontal', className, alt = 'Alcanfores' }: { variante?: VarianteLogo; className?: string; alt?: string }) {
  return <img src={LOGOS[variante]} alt={alt} className={cx('block w-auto', className)} decoding="async" />;
}

/** Ilustración estilo B del prototipo (IL-01 … IL-30). `alt` vacío = decorativa. */
export function Ilustracion({ id, className, alt = '', carga = 'lazy' }: { id: IdIlustracion; className?: string; alt?: string; carga?: 'lazy' | 'eager' }) {
  return (
    <img
      src={rutaIlustracion(id)}
      alt={alt}
      aria-hidden={alt ? undefined : true}
      className={cx('il', className)}
      loading={carga}
      decoding="async"
    />
  );
}

/** Busto de Rosa / Lucho en un cuadro (cabeceras y modales del panel). */
export function Busto({ id, className = 'cab-busto' }: { id: IdBusto; className?: string }) {
  return (
    <span className={className} aria-hidden>
      <img src={rutaBusto(id)} alt="" />
    </span>
  );
}

/** Patrón de líneas de cancha (IL-31): SVG de una tinta, toma el color de `currentColor`. */
export function PatronCancha({ className }: { className?: string }) {
  return (
    <svg className={cx('patron-cancha', className)} viewBox="0 0 400 260" fill="none" stroke="currentColor" strokeWidth={3} aria-hidden>
      <rect x="6" y="6" width="388" height="248" rx="6" />
      <path d="M200 6v248" />
      <circle cx="200" cy="130" r="42" />
      <circle cx="200" cy="130" r="4" fill="currentColor" />
      <path d="M6 70h56v120H6M394 70h-56v120h56M6 102h22v56H6M394 102h-22v56h22" />
      <path d="M62 104a30 30 0 0 1 0 52M338 104a30 30 0 0 0 0 52" />
    </svg>
  );
}

const SVG_DEPORTE: Record<'FUTBOL' | 'VOLEY' | 'BASQUET', JSX.Element> = {
  FUTBOL: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8.8l3.04 2.21-1.16 3.58h-3.76l-1.16-3.58z" />
      <path d="M12 8.8V3M15.04 11.01l5.5-1.79M13.88 14.59l3.4 4.69M10.12 14.59l-3.4 4.69M8.96 11.01l-5.5-1.79" />
    </>
  ),
  VOLEY: (
    <>
      <path d="M11.1 7.1a16.55 16.55 0 0 1 10.9 4" />
      <path d="M12 12a12.6 12.6 0 0 1-8.7 5" />
      <path d="M16.8 13.6a16.55 16.55 0 0 1-9 7.5" />
      <path d="M20.7 17a12.8 12.8 0 0 0-8.7-5 13.3 13.3 0 0 1 0-10" />
      <path d="M6.3 3.8a16.55 16.55 0 0 0 1.9 11.5" />
      <circle cx="12" cy="12" r="10" />
    </>
  ),
  BASQUET: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 3v18M3 12h18" />
      <path d="M5.6 5.6c2.6 2.1 3.9 4.2 3.9 6.4s-1.3 4.3-3.9 6.4M18.4 5.6c-2.6 2.1-3.9 4.2-3.9 6.4s1.3 4.3 3.9 6.4" />
    </>
  ),
};

export function familiaDeporte(d: Deporte): 'FUTBOL' | 'VOLEY' | 'BASQUET' {
  if (d === 'VOLEY' || d === 'MULTIUSO') return 'VOLEY';
  if (d === 'BASQUET') return 'BASQUET';
  return 'FUTBOL';
}

/** Ícono de deporte con el mismo trazo que Lucide (fútbol, vóley, básquet). */
export function IconoDeporte({ deporte, className, ...resto }: { deporte: Deporte } & SVGProps<SVGSVGElement>) {
  return (
    <svg className={cx('ico', className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" aria-hidden {...resto}>
      {SVG_DEPORTE[familiaDeporte(deporte)]}
    </svg>
  );
}

/** Ilustración por deporte (IL-05/06/07). */
export function ilustracionDeporte(d: Deporte): IdIlustracion {
  const f = familiaDeporte(d);
  return f === 'VOLEY' ? 'IL-06-voley' : f === 'BASQUET' ? 'IL-07-basquet' : 'IL-05-futbol';
}
