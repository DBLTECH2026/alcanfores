import { useEffect, useId, useRef, useState, type DragEvent } from 'react';
import { BadgeCheck, Camera, Image as IconoImagen, ImageDown, TriangleAlert, Trash2, Upload, ZoomIn } from 'lucide-react';
import { cx } from '@/utilidades/cx';
import { medirImagen, tipoPorBytes } from '@/utilidades/documentos';
import { LADO_MIN_IMAGEN, MSJ, TAMANIO_MAX_IMAGEN } from '@/esquemas/comunes';
import { VisorImagen } from './VisorImagen';

function peso(bytes: number): string {
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

/** Valida en el navegador lo mismo que el back: JPG/PNG por bytes mágicos, ≤ 5 MB, lado mínimo 200 px. */
export async function validarImagen(archivo: File): Promise<string | null> {
  if (archivo.size > TAMANIO_MAX_IMAGEN) return MSJ.imagen;
  const tipo = await tipoPorBytes(archivo);
  if (!tipo) return MSJ.imagen;
  const url = URL.createObjectURL(archivo);
  try {
    const { ancho, alto } = await medirImagen(url);
    if (Math.min(ancho, alto) < LADO_MIN_IMAGEN) return MSJ.imagen;
  } catch {
    return MSJ.imagen;
  } finally {
    URL.revokeObjectURL(url);
  }
  return null;
}

/**
 * Subida de imagen con vista previa (uploader del prototipo): elegir, tomar foto (móvil) o arrastrar.
 * Valida al elegir; si falla, muestra el error y no entrega el archivo.
 */
export function SubidaImagen({
  valor,
  alCambiar,
  error,
  alError,
  titulo = 'Sube tu comprobante',
  ayuda = 'JPG o PNG · máx. 5 MB',
  progreso,
  nombreCampo = 'archivo',
}: {
  valor: File | null;
  alCambiar: (f: File | null) => void;
  error?: string;
  alError?: (mensaje: string | null) => void;
  titulo?: string;
  ayuda?: string;
  /** 0–100 mientras se envía. */
  progreso?: number | null;
  nombreCampo?: string;
}) {
  const id = useId();
  const entrada = useRef<HTMLInputElement>(null);
  const camara = useRef<HTMLInputElement>(null);
  const [arrastrando, setArrastrando] = useState(false);
  const [vista, setVista] = useState<string | null>(null);
  const [verGrande, setVerGrande] = useState(false);
  const [validando, setValidando] = useState(false);

  useEffect(() => {
    if (!valor) {
      setVista(null);
      return undefined;
    }
    const url = URL.createObjectURL(valor);
    setVista(url);
    return () => URL.revokeObjectURL(url);
  }, [valor]);

  const elegir = async (f: File | undefined | null) => {
    if (!f) return;
    setValidando(true);
    const problema = await validarImagen(f);
    setValidando(false);
    if (problema) {
      alError?.(problema);
      alCambiar(null);
      return;
    }
    alError?.(null);
    alCambiar(f);
  };

  const alSoltar = (e: DragEvent) => {
    e.preventDefault();
    setArrastrando(false);
    void elegir(e.dataTransfer.files?.[0]);
  };

  const inputs = (
    <>
      <input
        ref={entrada}
        id={id}
        name={nombreCampo}
        type="file"
        accept="image/jpeg,image/png"
        className="sr-only"
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-e` : undefined}
        onChange={(e) => {
          void elegir(e.target.files?.[0]);
          e.target.value = '';
        }}
      />
      <input
        ref={camara}
        type="file"
        accept="image/jpeg,image/png"
        capture="environment"
        className="sr-only"
        tabIndex={-1}
        aria-hidden
        onChange={(e) => {
          void elegir(e.target.files?.[0]);
          e.target.value = '';
        }}
      />
    </>
  );

  if (valor && vista) {
    const enviando = progreso !== null && progreso !== undefined;
    return (
      <div className={cx('uploader up-archivo', !enviando && 'up-valido')} aria-busy={enviando || undefined}>
        {inputs}
        <div className="up-fila">
          <button type="button" className="up-mini relative" onClick={() => setVerGrande(true)} aria-label="Ver la imagen en grande">
            <img src={vista} alt="Vista previa del comprobante" />
            <span className="absolute right-1 bottom-1 grid place-items-center w-6 h-6 rounded-full bg-noche/80 text-white">
              <ZoomIn className="ico ico-14" aria-hidden />
            </span>
          </button>
          <div className="up-info">
            <div className="up-nombre">{valor.name}</div>
            <div className="up-meta tnum">{enviando ? `${progreso} % · ${peso(valor.size)}` : peso(valor.size)}</div>
            {enviando ? (
              <div className="up-progreso" role="progressbar" aria-valuenow={progreso} aria-valuemin={0} aria-valuemax={100}>
                <i style={{ width: `${progreso}%` }} />
              </div>
            ) : (
              <div className="mt-1.5">
                <span className="badge badge-sm b-verde">
                  <BadgeCheck className="ico" aria-hidden />
                  <span>Lista</span>
                </span>
              </div>
            )}
          </div>
          {!enviando && (
            <div className="up-acciones">
              <button type="button" className="btn btn-fantasma btn-sm" onClick={() => entrada.current?.click()}>
                <span>Cambiar</span>
              </button>
              <button type="button" className="btn btn-fantasma btn-sm btn-icono" aria-label="Quitar imagen" onClick={() => alCambiar(null)}>
                <Trash2 className="ico" aria-hidden />
              </button>
            </div>
          )}
        </div>
        {verGrande && <VisorImagen imagenes={[{ url: vista, leyenda: valor.name }]} indice={0} alCambiar={() => undefined} alCerrar={() => setVerGrande(false)} titulo="Comprobante" />}
      </div>
    );
  }

  return (
    <div className="grid gap-1.5">
      <div
        className={cx('uploader', arrastrando && 'is-arrastrando', error && 'up-error')}
        onDragOver={(e) => {
          e.preventDefault();
          setArrastrando(true);
        }}
        onDragLeave={() => setArrastrando(false)}
        onDrop={alSoltar}
      >
        {inputs}
        <span className="up-ico">{error ? <TriangleAlert className="ico" aria-hidden /> : arrastrando ? <ImageDown className="ico" aria-hidden /> : <Upload className="ico" aria-hidden />}</span>
        <div>
          <label className="up-titulo block cursor-pointer" htmlFor={id}>
            {arrastrando ? 'Suéltala aquí' : titulo}
          </label>
          <div className="up-ayuda">{ayuda}</div>
        </div>
        <div className="up-botones">
          <button type="button" className="btn btn-secundario btn-sm solo-movil" onClick={() => camara.current?.click()}>
            <Camera className="ico" aria-hidden />
            <span>Tomar foto</span>
          </button>
          <button type="button" className="btn btn-secundario btn-sm" onClick={() => entrada.current?.click()} disabled={validando}>
            {validando ? <span className="spinner" aria-hidden /> : <IconoImagen className="ico" aria-hidden />}
            <span>{validando ? 'Revisando…' : 'Elegir imagen'}</span>
          </button>
        </div>
        <div className="up-ayuda solo-escritorio">o arrástrala aquí</div>
      </div>
      {error && (
        <div className="campo-error justify-center" id={`${id}-e`} role="alert">
          <span>{error}</span>
        </div>
      )}
    </div>
  );
}
