import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { AlignLeft, Heading, Images, MessageSquareQuote, MousePointerClick, RotateCcw, Save, Sparkles, Tag, X, type LucideIcon } from 'lucide-react';
import type { CanchaAdmin, ResenaAdmin } from '@/api/admin/tipos';
import type { CanchaDetalle } from '@/api/tipos';
import { useComplejo } from '@/hooks/useDatosPublicos';
import { Boton } from '@/componentes/ui/Boton';
import { Vacio } from '@/componentes/ui/Datos';
import { ItemResena } from '@/componentes/portal/Canchas';
import { FichaCancha, type ParteFicha } from '@/componentes/portal/FichaCancha';
import { cx } from '@/utilidades/cx';
import type { Entrada } from '../CanchaFormulario';
import { erroresDe } from './utilCancha';
import { PanelCaracteristicas, PanelDescripcion, PanelFotos, PanelNombre, PanelPrecios, PanelResenas } from './PanelesEditor';

const PARTES: Array<{ id: ParteFicha; texto: string; icono: LucideIcon }> = [
  { id: 'nombre', texto: 'Nombre', icono: Heading },
  { id: 'galeria', texto: 'Fotos', icono: Images },
  { id: 'descripcion', texto: 'Descripción', icono: AlignLeft },
  { id: 'caracteristicas', texto: 'Características', icono: Sparkles },
  { id: 'precios', texto: 'Precios', icono: Tag },
  { id: 'resenas', texto: 'Reseñas', icono: MessageSquareQuote },
];
/** Campo del borrador → parte del detalle que lo muestra (para abrir el panel con el error). */
const PARTE_DE: Record<string, ParteFicha> = { nombre: 'nombre', descripcion: 'nombre', descripcionLarga: 'descripcion', caracteristicas: 'caracteristicas', precioDia: 'precios', precioNoche: 'precios' };
/** Ancho de escritorio en el que se pinta el detalle (luego se escala para que entre). */
const ANCHO = 980;

/** Pinta el detalle a ancho de escritorio y lo escala al espacio disponible (sin iframe). */
function Escalado({ children }: { children: ReactNode }) {
  const caja = useRef<HTMLDivElement>(null);
  const [zoom, setZoom] = useState(1);
  useLayoutEffect(() => {
    const el = caja.current;
    if (!el) return undefined;
    const medir = () => {
      const cs = getComputedStyle(el);
      const util = el.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight);
      setZoom(window.innerWidth >= 1024 ? Math.min(1, util / ANCHO) : 1);
    };
    medir();
    const ro = new ResizeObserver(medir);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return (
    <div ref={caja} className="vista-previa-caja">
      <div className="vista-previa" style={window.innerWidth >= 1024 ? { width: ANCHO, zoom } : undefined}>
        {children}
      </div>
    </div>
  );
}

/**
 * Apartado 1 · «Vista y contenido»: el detalle PÚBLICO real (FichaCancha, el mismo del portal) con el borrador;
 * clic en una parte → su panel a la derecha (en móvil, hoja inferior). Una sola acción principal: «Publicar cambios».
 */
export function EditorVisual({
  cancha,
  publica,
  entrada,
  cambiar,
  sucio,
  guardando,
  guardar,
  descartar,
  resenas,
  recargarResenas,
  recargarCancha,
  irAHorario,
}: {
  cancha: CanchaAdmin;
  publica: CanchaDetalle;
  entrada: Entrada;
  cambiar: (p: Partial<Entrada>) => void;
  sucio: boolean;
  guardando: boolean;
  guardar: () => Promise<Record<string, string> | null>;
  descartar: () => void;
  resenas: ResenaAdmin[];
  recargarResenas: () => void;
  recargarCancha: () => void;
  irAHorario: () => void;
}) {
  const { datos: complejo } = useComplejo();
  const [activa, setActiva] = useState<ParteFicha | null>(null);
  const [errores, setErrores] = useState<Record<string, string>>({});
  // Los errores se limpian mientras se corrige.
  useEffect(() => {
    if (Object.keys(errores).length) setErrores(erroresDe(entrada));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entrada]);

  const publicar = async () => {
    const e = await guardar();
    if (e && Object.keys(e).length) {
      setErrores(e);
      const primero = Object.keys(e).map((k) => PARTE_DE[k]).find(Boolean);
      if (primero) setActiva(primero);
    } else setErrores({});
  };

  const publicadas = resenas
    .filter((r) => r.estado === 'PUBLICADA')
    .slice(0, 4)
    .map((r) => ({ id: r.id, autor: r.autor, puntuacion: r.puntuacion, comentario: r.comentario, fecha: r.fecha ?? r.creadoEn, respuesta: r.respuesta, verificada: r.origen !== 'COMPLEJO' }));

  const panel = (p: ParteFicha) => {
    switch (p) {
      case 'nombre':
        return <PanelNombre entrada={entrada} cambiar={cambiar} errores={errores} />;
      case 'galeria':
        return <PanelFotos cancha={cancha} alCambiar={recargarCancha} />;
      case 'descripcion':
        return <PanelDescripcion entrada={entrada} cambiar={cambiar} errores={errores} />;
      case 'caracteristicas':
        return <PanelCaracteristicas entrada={entrada} cambiar={cambiar} errores={errores} />;
      case 'precios':
        return <PanelPrecios entrada={entrada} cambiar={cambiar} errores={errores} irAHorario={irAHorario} />;
      case 'resenas':
        return <PanelResenas idCancha={cancha.id} resenas={resenas} alCambiar={recargarResenas} />;
      default:
        return null;
    }
  };
  const info = PARTES.find((x) => x.id === activa);

  return (
    <div className="editor-visual">
      <div className="grid grid-cols-[minmax(0,1fr)] gap-3 min-w-0">
        <div className="editor-barra">
          <div className="editor-partes xl:flex-wrap xl:[&>.editor-parte]:px-2" role="toolbar" aria-label="Partes de la ficha">
            {PARTES.map((p) => (
              <button key={p.id} type="button" aria-pressed={activa === p.id} className="editor-parte" onClick={() => setActiva(p.id)}>
                <p.icono className="ico" aria-hidden />
                {p.texto}
              </button>
            ))}
          </div>
          <div className="flex gap-2 ml-auto">
            {sucio && (
              <Boton variante="fantasma" tamano="sm" icono={RotateCcw} onClick={descartar} disabled={guardando}>
                Descartar
              </Boton>
            )}
            {/* Una sola acción principal a la vista: con un panel de texto abierto, el botón vive en su pie. */}
            {!(activa && activa !== 'galeria' && activa !== 'resenas' && sucio) && (
              <Boton tamano="sm" icono={Save} disabled={!sucio} cargando={guardando} textoCargando="Publicando…" onClick={publicar}>
                Publicar cambios
              </Boton>
            )}
          </div>
        </div>
        <Escalado>
          <FichaCancha
            cancha={publica}
            complejo={complejo}
            editor={{ activa, alElegir: setActiva }}
            resenas={publicadas.length ? publicadas.map((r) => <ItemResena key={r.id} resena={r} />) : <Vacio ilustracion="IL-24-sin-resultados" titulo="Aún sin reseñas" compacto />}
          />
        </Escalado>
      </div>

      <aside className={cx('editor-panel', activa && 'abierto')} aria-label="Panel de edición">
        {activa && info ? (
          <>
            <div className="editor-panel-cab">
              <span className="td-ico">
                <info.icono className="ico" aria-hidden />
              </span>
              <b>{info.texto}</b>
              <Boton variante="fantasma" tamano="sm" icono={X} etiqueta="Cerrar panel" className="ml-auto" onClick={() => setActiva(null)} />
            </div>
            <div className="editor-panel-cuerpo">{panel(activa)}</div>
            {activa !== 'galeria' && activa !== 'resenas' && sucio && (
              <div className="editor-panel-pie">
                <Boton bloque icono={Save} cargando={guardando} textoCargando="Publicando…" onClick={publicar}>
                  Publicar cambios
                </Boton>
              </div>
            )}
          </>
        ) : (
          <div className="editor-panel-vacio">
            <span className="td-ico">
              <MousePointerClick className="ico" aria-hidden />
            </span>
            <b>Toca una parte de la ficha</b>
            <span>Se edita aquí y lo ves al instante.</span>
          </div>
        )}
      </aside>
    </div>
  );
}
