import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ChevronLeft, ChevronRight, X, ZoomIn, ZoomOut, ExternalLink } from 'lucide-react';
import { cx } from '@/utilidades/cx';

export interface ImagenVisor {
  url: string;
  leyenda?: string | null;
}

/**
 * Visor a pantalla completa (galería de la cancha, comprobantes en el panel).
 * Teclado: ← → cambian, Esc cierra, + / − acercan. Miniaturas abajo; deslizar con el dedo en móvil.
 */
export function VisorImagen({ imagenes, indice, alCerrar, alCambiar, titulo = 'Galería' }: { imagenes: ImagenVisor[]; indice: number; alCerrar: () => void; alCambiar: (i: number) => void; titulo?: string }) {
  const [zoom, setZoom] = useState(1);
  const cerrarBtn = useRef<HTMLButtonElement>(null);
  const toque = useRef<number | null>(null);
  const total = imagenes.length;
  const actual = imagenes[indice];
  const ir = (d: number) => {
    setZoom(1);
    alCambiar((indice + d + total) % total);
  };
  const irRef = useRef(ir);
  irRef.current = ir;
  const cerrarRef = useRef(alCerrar);
  cerrarRef.current = alCerrar;

  useEffect(() => {
    const previo = document.activeElement as HTMLElement | null;
    const scroll = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    cerrarBtn.current?.focus();
    const alTeclear = (e: KeyboardEvent) => {
      if (e.key === 'Escape') cerrarRef.current();
      else if (e.key === 'ArrowRight') irRef.current(1);
      else if (e.key === 'ArrowLeft') irRef.current(-1);
      else if (e.key === '+') setZoom((z) => Math.min(3, z + 0.5));
      else if (e.key === '-') setZoom((z) => Math.max(1, z - 0.5));
    };
    document.addEventListener('keydown', alTeclear);
    return () => {
      document.removeEventListener('keydown', alTeclear);
      document.body.style.overflow = scroll;
      previo?.focus?.();
    };
  }, []);

  if (!actual) return null;
  return createPortal(
    <div className="visor-pantalla sobre-oscuro" role="dialog" aria-modal="true" aria-label={titulo}>
      <div className="visor-pantalla-barra">
        <span className="tnum text-[14px] font-semibold">
          {indice + 1} / {total}
        </span>
        <div className="flex items-center gap-1 ml-auto">
          <button type="button" className="visor-btn" aria-label="Alejar" disabled={zoom <= 1} onClick={() => setZoom((z) => Math.max(1, z - 0.5))}>
            <ZoomOut className="ico" aria-hidden />
          </button>
          <span className="visor-zoom">{Math.round(zoom * 100)} %</span>
          <button type="button" className="visor-btn" aria-label="Acercar" disabled={zoom >= 3} onClick={() => setZoom((z) => Math.min(3, z + 0.5))}>
            <ZoomIn className="ico" aria-hidden />
          </button>
          <a className="visor-btn" href={actual.url} target="_blank" rel="noopener noreferrer" aria-label="Abrir en otra pestaña">
            <ExternalLink className="ico" aria-hidden />
          </a>
          <button ref={cerrarBtn} type="button" className="visor-btn" aria-label="Cerrar visor" onClick={alCerrar}>
            <X className="ico" aria-hidden />
          </button>
        </div>
      </div>
      <div
        className="visor-pantalla-lienzo"
        onTouchStart={(e) => {
          toque.current = e.touches[0]?.clientX ?? null;
        }}
        onTouchEnd={(e) => {
          const x = e.changedTouches[0]?.clientX;
          if (toque.current !== null && x !== undefined && zoom === 1 && Math.abs(x - toque.current) > 50) ir(x < toque.current ? 1 : -1);
          toque.current = null;
        }}
      >
        {total > 1 && (
          <button type="button" className="visor-flecha izq" aria-label="Foto anterior" onClick={() => ir(-1)}>
            <ChevronLeft className="ico ico-24" aria-hidden />
          </button>
        )}
        <div className={cx('visor-pantalla-marco', zoom > 1 && 'con-zoom')}>
          <img
            src={actual.url}
            alt={actual.leyenda ?? `Foto ${indice + 1} de ${total}`}
            style={{ transform: `scale(${zoom})` }}
            onClick={() => setZoom((z) => (z > 1 ? 1 : 2))}
          />
        </div>
        {total > 1 && (
          <button type="button" className="visor-flecha der" aria-label="Foto siguiente" onClick={() => ir(1)}>
            <ChevronRight className="ico ico-24" aria-hidden />
          </button>
        )}
      </div>
      {actual.leyenda && <p className="visor-leyenda">{actual.leyenda}</p>}
      {total > 1 && (
        <div className="visor-miniaturas" role="tablist" aria-label="Miniaturas">
          {imagenes.map((im, i) => (
            <button
              key={im.url + i}
              type="button"
              role="tab"
              aria-selected={i === indice}
              aria-label={`Ver foto ${i + 1}`}
              className={cx('visor-mini', i === indice && 'activa')}
              onClick={() => {
                setZoom(1);
                alCambiar(i);
              }}
            >
              <img src={im.url} alt="" />
            </button>
          ))}
        </div>
      )}
    </div>,
    document.body,
  );
}
