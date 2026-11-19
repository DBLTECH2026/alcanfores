import { useState } from 'react';
import { ExternalLink, Expand, ImageOff, RotateCw, ZoomIn, ZoomOut } from 'lucide-react';
import { pagosAdminApi } from '@/api/admin';
import { Spinner } from '@/componentes/ui/Datos';
import { VisorImagen } from '@/componentes/ui/VisorImagen';
import { cx } from '@/utilidades/cx';
import { useArchivo } from './archivos';

/**
 * Imagen del comprobante Yape/Plin (protegida con token) con zoom, giro, pantalla completa y abrir en pestaña
 * (prototipo adm-m10). `alto` = alto del lienzo.
 */
export function VisorComprobante({ idPago, leyenda }: { idPago: number; leyenda?: string }) {
  const { url, cargando, error } = useArchivo(pagosAdminApi.rutaArchivo(idPago));
  const [zoom, setZoom] = useState(1);
  const [giro, setGiro] = useState(0);
  const [grande, setGrande] = useState(false);

  return (
    <div className="visor-panel" aria-label="Comprobante enviado por el cliente">
      <div className="visor-panel-barra">
        <button type="button" aria-label="Alejar" disabled={zoom <= 1} onClick={() => setZoom((z) => Math.max(1, z - 0.5))}>
          <ZoomOut className="ico" aria-hidden />
        </button>
        <span className="tnum w-12 text-center">{Math.round(zoom * 100)} %</span>
        <button type="button" aria-label="Acercar" disabled={zoom >= 3} onClick={() => setZoom((z) => Math.min(3, z + 0.5))}>
          <ZoomIn className="ico" aria-hidden />
        </button>
        <button type="button" aria-label="Girar" onClick={() => setGiro((g) => (g + 90) % 360)}>
          <RotateCw className="ico" aria-hidden />
        </button>
        <button type="button" aria-label="Pantalla completa" disabled={!url} onClick={() => setGrande(true)}>
          <Expand className="ico" aria-hidden />
        </button>
        {url && (
          <a href={url} target="_blank" rel="noopener noreferrer" className="ml-auto grid place-items-center w-[34px] h-[34px] rounded-[9px] text-[#DCE5E0] hover:bg-white/10" aria-label="Abrir en otra pestaña">
            <ExternalLink className="ico ico-18" aria-hidden />
          </a>
        )}
      </div>
      <div className={cx('visor-panel-lienzo', zoom > 1 && 'con-zoom')}>
        {cargando ? (
          <Spinner grande className="text-white" etiqueta="Cargando comprobante" />
        ) : error || !url ? (
          <div className="grid justify-items-center gap-2 text-[#A9B9B1] text-[13px]">
            <ImageOff className="ico ico-32" aria-hidden />
            Sin imagen
          </div>
        ) : (
          <img
            src={url}
            alt="Captura del comprobante de pago"
            style={{ transform: giro ? `rotate(${giro}deg)` : undefined, width: zoom > 1 ? `${zoom * 100}%` : undefined }}
            onClick={() => setZoom((z) => (z > 1 ? 1 : 2))}
          />
        )}
      </div>
      {grande && url && <VisorImagen imagenes={[{ url, leyenda: leyenda ?? 'Comprobante' }]} indice={0} alCambiar={() => undefined} alCerrar={() => setGrande(false)} titulo="Comprobante" />}
    </div>
  );
}
