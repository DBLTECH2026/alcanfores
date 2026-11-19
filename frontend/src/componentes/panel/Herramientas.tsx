import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useSearchParams } from 'react-router-dom';
import { Ellipsis, RefreshCw, Search, SlidersHorizontal, X, type LucideIcon } from 'lucide-react';
import { Boton } from '@/componentes/ui/Boton';
import { Vacio } from '@/componentes/ui/Datos';
import { Insignia, InsigniaEstado, type TonoInsignia } from '@/componentes/ui/Estados';
import { Modal, type PropsModal } from '@/componentes/ui/Modal';
import { cx } from '@/utilidades/cx';
import {
  BadgeCheck,
  Ban,
  CalendarCheck,
  CalendarClock,
  CalendarX,
  CircleCheck,
  CircleDashed,
  CirclePause,
  CircleX,
  Clock,
  FileText,
  FlaskConical,
  Hourglass,
  Mail,
  MessageCircle,
  Send,
  ShieldCheck,
  TriangleAlert,
  UserRound,
} from 'lucide-react';
import './panel-extra.css';

// ------------------------------ estados propios del panel ------------------------------
type Def = [TonoInsignia, LucideIcon, string];
/** Estados que no están en `ESTADOS` (ui/Estados): notificaciones, recordatorios, comprobantes, usuarios, roles. */
export const ESTADOS_PANEL: Record<string, Record<string, Def>> = {
  notificacion: {
    PENDIENTE: ['amarillo', Hourglass, 'Pendiente'],
    ENVIADO: ['menta', Send, 'Enviado'],
    FALLIDO: ['rojo', TriangleAlert, 'Fallido'],
    SIMULADO: ['neutro', FlaskConical, 'Simulado'],
  },
  canal: { WHATSAPP: ['menta', MessageCircle, 'WhatsApp'], CORREO: ['neutro', Mail, 'Correo'] },
  recordatorio: {
    PROGRAMADO: ['amarillo', CalendarClock, 'Programado'],
    ENVIADO: ['menta', CalendarCheck, 'Enviado'],
    CANCELADO: ['gris', CalendarX, 'Cancelado'],
    OMITIDO: ['punteado', CircleDashed, 'Omitido'],
  },
  comprobante: { EMITIDO: ['menta', BadgeCheck, 'Emitido'], ANULADO: ['gris', Ban, 'Anulado'] },
  tipoComprobante: { BOLETA: ['neutro', FileText, 'Boleta'], FACTURA: ['noche', FileText, 'Factura'], NOTA_CREDITO: ['rojo-borde', FileText, 'Nota de crédito'] },
  usuario: { ACTIVO: ['menta', CircleCheck, 'Activo'], INACTIVO: ['gris', CirclePause, 'Inactivo'] },
  rol: { ADMIN: ['noche', ShieldCheck, 'Administrador'], RECEPCION: ['neutro', UserRound, 'Recepción'] },
  servicio: { UP: ['menta', CircleCheck, 'En línea'], DOWN: ['rojo', CircleX, 'Caído'] },
  hora: { PENDIENTE: ['amarillo', Clock, 'Pendiente'] },
};

/** Insignia de estado del panel: busca primero en ESTADOS_PANEL y luego en los del sistema. */
export function Estado({ dominio, estado, texto, tamano }: { dominio: string; estado: string; texto?: string; tamano?: 'sm' | 'md' | 'lg' }) {
  const d = ESTADOS_PANEL[dominio]?.[estado];
  if (d) {
    const [tono, icono, et] = d;
    return (
      <Insignia tono={tono} icono={icono} tamano={tamano}>
        {texto ?? et}
      </Insignia>
    );
  }
  return <InsigniaEstado dominio={dominio} estado={estado} texto={texto} tamano={tamano} />;
}

// ------------------------------ hooks ------------------------------
/** Devuelve `valor` con retraso (búsquedas mientras se escribe). */
export function useRetraso<T>(valor: T, ms = 350): T {
  const [v, setV] = useState(valor);
  useEffect(() => {
    const t = window.setTimeout(() => setV(valor), ms);
    return () => window.clearTimeout(t);
  }, [valor, ms]);
  return v;
}

/**
 * Filtros en la URL (?estado=…&q=…&page=…): se pueden compartir, el buscador de la barra superior llega con ?q= y
 * «Atrás» vuelve al mismo filtro. Cambiar un filtro reinicia la página a 0.
 */
export function useFiltrosUrl() {
  const [sp, setSp] = useSearchParams();
  const leer = useCallback((k: string) => sp.get(k) ?? '', [sp]);
  const poner = useCallback(
    (cambios: Record<string, string | number | null | undefined>) => {
      setSp(
        (prev) => {
          const n = new URLSearchParams(prev);
          for (const [k, v] of Object.entries(cambios)) {
            if (v === null || v === undefined || v === '') n.delete(k);
            else n.set(k, String(v));
          }
          if (!('page' in cambios)) n.delete('page');
          return n;
        },
        { replace: true },
      );
    },
    [setSp],
  );
  const limpiar = useCallback((conservar: string[] = []) => {
    setSp(
      (prev) => {
        const n = new URLSearchParams();
        for (const k of conservar) {
          const v = prev.get(k);
          if (v) n.set(k, v);
        }
        return n;
      },
      { replace: true },
    );
  }, [setSp]);
  return { sp, leer, poner, limpiar, pagina: Number(sp.get('page') ?? 0) || 0 };
}

// ------------------------------ barra de búsqueda + filtros (prototipo filtroBar) ------------------------------
export function BarraBusqueda({
  valor,
  alCambiar,
  placeholder = 'Buscar…',
  filtrosActivos,
  alAbrirFiltros,
  conteo,
  extra,
}: {
  valor: string;
  alCambiar: (v: string) => void;
  placeholder?: string;
  filtrosActivos?: number;
  alAbrirFiltros?: () => void;
  conteo?: ReactNode;
  extra?: ReactNode;
}) {
  return (
    <div className="bb">
      <label className="bb-buscar">
        <Search className="ico ico-18" aria-hidden />
        <input type="search" value={valor} onChange={(e) => alCambiar(e.target.value)} placeholder={placeholder} aria-label={placeholder} />
        {valor && (
          <button type="button" className="bb-limpiar" aria-label="Borrar búsqueda" onClick={() => alCambiar('')}>
            <X className="ico ico-16" aria-hidden />
          </button>
        )}
      </label>
      {extra}
      {alAbrirFiltros && (
        <button type="button" className={cx('bb-filtros', (filtrosActivos ?? 0) > 0 && 'activo')} onClick={alAbrirFiltros} aria-haspopup="dialog">
          <SlidersHorizontal className="ico ico-18" aria-hidden />
          Filtros
          {(filtrosActivos ?? 0) > 0 && <i>{filtrosActivos}</i>}
        </button>
      )}
      {conteo !== undefined && <span className="bb-conteo tnum">{conteo}</span>}
    </div>
  );
}

/** Chips de filtros aplicados con «x» para quitar cada uno. */
export function FiltrosActivos({ items, alLimpiar }: { items: Array<{ clave: string; texto: string; alQuitar: () => void }>; alLimpiar?: () => void }) {
  if (!items.length) return null;
  return (
    <div className="bb-activos" aria-label="Filtros aplicados">
      {items.map((i) => (
        <span key={i.clave} className="fa">
          {i.texto}
          <button type="button" className="fa-x" aria-label={`Quitar ${i.texto}`} onClick={i.alQuitar}>
            <X className="ico" aria-hidden />
          </button>
        </span>
      ))}
      {alLimpiar && items.length > 1 && (
        <button type="button" className="enlace text-[12.5px] bg-transparent border-0 cursor-pointer" onClick={alLimpiar}>
          Limpiar todo
        </button>
      )}
    </div>
  );
}

/** Panel lateral de filtros (entra desde la derecha; prototipo panelFiltros). Los filtros se aplican al tocarlos. */
export function PanelFiltros({
  abierto,
  alCerrar,
  titulo = 'Filtros',
  subtitulo,
  children,
  alLimpiar,
  resultados,
}: {
  abierto: boolean;
  alCerrar: () => void;
  titulo?: string;
  subtitulo?: ReactNode;
  children: ReactNode;
  alLimpiar?: () => void;
  resultados?: number;
}) {
  const caja = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!abierto) return undefined;
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && alCerrar();
    document.addEventListener('keydown', esc);
    const t = window.setTimeout(() => caja.current?.querySelector<HTMLElement>('button, input, select')?.focus(), 30);
    return () => {
      document.removeEventListener('keydown', esc);
      window.clearTimeout(t);
    };
  }, [abierto, alCerrar]);
  if (!abierto) return null;
  return createPortal(
    <div className="panel-filtros-capa" onMouseDown={(e) => e.target === e.currentTarget && alCerrar()}>
      <aside ref={caja} className="panel-filtros animate-entrar-der" role="dialog" aria-modal="true" aria-label={titulo}>
        <div className="pf-cab">
          <span className="td-ico">
            <SlidersHorizontal className="ico" aria-hidden />
          </span>
          <div>
            <h2>{titulo}</h2>
            {subtitulo && <small>{subtitulo}</small>}
          </div>
          <button type="button" className="pf-x" aria-label="Cerrar filtros" onClick={alCerrar}>
            <X className="ico ico-18" aria-hidden />
          </button>
        </div>
        <div className="pf-cuerpo">{children}</div>
        <div className="pf-pie">
          {alLimpiar && (
            <Boton variante="secundario" onClick={alLimpiar}>
              Limpiar
            </Boton>
          )}
          <Boton variante="oscuro" onClick={alCerrar}>
            {resultados !== undefined ? `Ver ${resultados}` : 'Listo'}
          </Boton>
        </div>
      </aside>
    </div>,
    document.body,
  );
}

export function GrupoFiltro({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="pf-grupo">
      <h3>{titulo}</h3>
      {children}
    </section>
  );
}

export interface OpcionFiltro {
  valor: string;
  texto: string;
  icono?: LucideIcon;
  cuenta?: number;
}
/** Opciones en píldora: tocar la elegida la quita (vuelve a «Todas»). */
export function OpcionesFiltro({ opciones, valor, alCambiar }: { opciones: OpcionFiltro[]; valor: string; alCambiar: (v: string) => void }) {
  return (
    <div className="pf-ops">
      {opciones.map((o) => {
        const I = o.icono;
        const sel = o.valor === valor;
        return (
          <button key={o.valor || 'todas'} type="button" className={cx('pf-op', sel && 'sel')} aria-pressed={sel} onClick={() => alCambiar(sel ? '' : o.valor)}>
            {I && <I className="ico" aria-hidden />}
            {o.texto}
            {o.cuenta !== undefined && <em>{o.cuenta}</em>}
          </button>
        );
      })}
    </div>
  );
}

// ------------------------------ celdas ------------------------------
/** Celda con ícono en cuadro + título + subtítulo (prototipo celdaDoc). */
export function CeldaDoc({ icono: I, titulo, sub, extra, className }: { icono: LucideIcon; titulo: ReactNode; sub?: ReactNode; extra?: ReactNode; className?: string }) {
  return (
    <div className={cx('td-doc', className)}>
      <span className="td-ico">
        <I className="ico" aria-hidden />
      </span>
      <div className="min-w-0">
        <b className="block truncar">{titulo}</b>
        {sub && <span className="sub t-sec block">{sub}</span>}
        {extra}
      </div>
    </div>
  );
}

export interface AccionFila {
  texto: string;
  icono: LucideIcon;
  alPulsar: () => void;
  peligro?: boolean;
  oculta?: boolean;
}
/** Botón ⋯ con menú de acciones (se dibuja fuera de la tabla para que no lo corte el scroll). */
export function MenuFila({ acciones, etiqueta = 'Más acciones' }: { acciones: AccionFila[]; etiqueta?: string }) {
  const visibles = acciones.filter((a) => !a.oculta);
  const boton = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<{ top: number; right: number } | null>(null);

  const cerrar = useCallback(() => setPos(null), []);
  useLayoutEffect(() => {
    if (!pos) return undefined;
    const fuera = (e: MouseEvent) => {
      if (!menu.current?.contains(e.target as Node) && !boton.current?.contains(e.target as Node)) cerrar();
    };
    const esc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        cerrar();
        boton.current?.focus();
      }
    };
    document.addEventListener('mousedown', fuera);
    document.addEventListener('keydown', esc);
    window.addEventListener('scroll', cerrar, true);
    window.addEventListener('resize', cerrar);
    menu.current?.querySelector<HTMLElement>('button')?.focus();
    return () => {
      document.removeEventListener('mousedown', fuera);
      document.removeEventListener('keydown', esc);
      window.removeEventListener('scroll', cerrar, true);
      window.removeEventListener('resize', cerrar);
    };
  }, [pos, cerrar]);

  if (!visibles.length) return null;
  const abrir = () => {
    const r = boton.current?.getBoundingClientRect();
    if (!r) return;
    const alto = visibles.length * 40 + 14;
    const top = r.bottom + 6 + alto > window.innerHeight ? Math.max(8, r.top - alto - 6) : r.bottom + 6;
    setPos({ top, right: Math.max(8, window.innerWidth - r.right) });
  };
  return (
    <>
      <button
        ref={boton}
        type="button"
        className="td-mas"
        aria-label={etiqueta}
        aria-haspopup="menu"
        aria-expanded={Boolean(pos)}
        onClick={(e) => {
          e.stopPropagation();
          if (pos) cerrar();
          else abrir();
        }}
      >
        <Ellipsis className="ico" aria-hidden />
      </button>
      {pos &&
        createPortal(
          <div ref={menu} className="menu-fila animate-entrar" role="menu" style={{ top: pos.top, right: pos.right }}>
            {visibles.map((a) => (
              <button
                key={a.texto}
                type="button"
                role="menuitem"
                className={cx(a.peligro && 'peligro')}
                onClick={(e) => {
                  e.stopPropagation();
                  cerrar();
                  a.alPulsar();
                }}
              >
                <a.icono className="ico" aria-hidden />
                {a.texto}
              </button>
            ))}
          </div>,
          document.body,
        )}
    </>
  );
}

// ------------------------------ estados de carga ------------------------------
/** Tarjeta de error de carga con «Reintentar». */
export function ErrorCarga({ alReintentar, titulo = 'Sin conexión con el servidor', texto }: { alReintentar: () => void; titulo?: string; texto?: ReactNode }) {
  return (
    <div className="card">
      <Vacio
        ilustracion="IL-19-sin-conexion"
        titulo={titulo}
        texto={texto}
        compacto
        acciones={
          <Boton variante="secundario" icono={RefreshCw} onClick={alReintentar}>
            Reintentar
          </Boton>
        }
      />
    </div>
  );
}

// ------------------------------ confirmación ------------------------------
/**
 * Modal de confirmación (oscuro en el panel) para acciones que cambian datos.
 * `alConfirmar` puede ser async: mientras corre, el botón muestra el spinner.
 */
export function ModalConfirmar({
  abierto,
  alCerrar,
  titulo,
  subtitulo,
  children,
  textoConfirmar = 'Confirmar',
  iconoConfirmar,
  peligro,
  alConfirmar,
  deshabilitado,
  busto = 'BU-rosa-panel',
  franja,
  tamano,
}: Pick<PropsModal, 'abierto' | 'alCerrar' | 'titulo' | 'subtitulo' | 'busto' | 'franja' | 'tamano'> & {
  children?: ReactNode;
  textoConfirmar?: string;
  iconoConfirmar?: LucideIcon;
  peligro?: boolean;
  alConfirmar: () => Promise<unknown> | void;
  deshabilitado?: boolean;
}) {
  const [enviando, setEnviando] = useState(false);
  const confirmar = async () => {
    setEnviando(true);
    try {
      await alConfirmar();
    } finally {
      setEnviando(false);
    }
  };
  return (
    <Modal
      abierto={abierto}
      alCerrar={() => !enviando && alCerrar()}
      titulo={titulo}
      subtitulo={subtitulo}
      busto={busto}
      franja={franja}
      tamano={tamano}
      acciones={
        <>
          <Boton variante="fantasma" onClick={alCerrar} disabled={enviando}>
            Volver
          </Boton>
          <Boton variante={peligro ? 'peligro' : 'primario'} icono={iconoConfirmar} cargando={enviando} disabled={deshabilitado} onClick={confirmar}>
            {textoConfirmar}
          </Boton>
        </>
      }
    >
      {children}
    </Modal>
  );
}

/** Tira de chips «dato» con ícono (resúmenes cortos dentro de tarjetas y modales). */
export function DatoIcono({ icono: I, children, className }: { icono: LucideIcon; children: ReactNode; className?: string }) {
  return (
    <span className={cx('dato-ico', className)}>
      <I className="ico" aria-hidden />
      <span>{children}</span>
    </span>
  );
}
