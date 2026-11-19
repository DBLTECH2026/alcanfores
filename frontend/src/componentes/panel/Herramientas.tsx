import { useCallback, useEffect, useLayoutEffect, useRef, useState, useSyncExternalStore, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useSearchParams } from 'react-router-dom';
import { Ellipsis, RefreshCw, RotateCcw, Search, SlidersHorizontal, X, type LucideIcon } from 'lucide-react';
import { Boton } from '@/componentes/ui/Boton';
import { Vacio } from '@/componentes/ui/Datos';
import { Insignia, InsigniaEstado, type TonoInsignia } from '@/componentes/ui/Estados';
import { Modal, type PropsModal } from '@/componentes/ui/Modal';
import { PanelLateral } from '@/componentes/ui/PanelLateral';
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

/**
 * Panel lateral de filtros (entra de derecha a izquierda; prototipo panelFiltros). Los filtros se aplican al tocarlos.
 * Usa PanelLateral: en celular min(86vw, 360px) y desde 641 px 380 px; cuerpo con scroll y pie fijo con «Limpiar» y «Ver resultados».
 */
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
  const ver =
    resultados === undefined ? 'Ver resultados' : `Ver ${resultados.toLocaleString('es-PE')}\u00a0${resultados === 1 ? 'resultado' : 'resultados'}`;
  return (
    <PanelLateral
      abierto={abierto}
      alCerrar={alCerrar}
      titulo={titulo}
      subtitulo={subtitulo}
      icono={SlidersHorizontal}
      etiquetaCerrar="Cerrar filtros"
      ancho="amplio"
      className="panel-filtros"
      claseCuerpo="pf-cuerpo"
      pie={
        <>
          {alLimpiar && (
            <Boton variante="secundario" icono={RotateCcw} onClick={alLimpiar}>
              Limpiar
            </Boton>
          )}
          <Boton variante="oscuro" onClick={alCerrar}>
            {ver}
          </Boton>
        </>
      }
    >
      {children}
    </PanelLateral>
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
  /** Acción que borra o anula: va al final, separada y en rojo. */
  peligro?: boolean;
  oculta?: boolean;
  /** Se ve pero no se puede pulsar (p. ej. asistencia antes de hora). */
  deshabilitada?: boolean;
  /** Por qué está deshabilitada (se muestra debajo del texto, en gris). */
  motivo?: string;
}

/** De qué fila es el menú: en celular encabeza el panel lateral (p. ej. «ALC-7K2M9Q» · «Lucía Mendoza · Cancha 1 · 19:00»). */
export interface ContextoMenu {
  titulo: string;
  subtitulo?: string;
  /** Fila opcional bajo el título (p. ej. insignias de estado). */
  detalle?: ReactNode;
}

/** Hasta este ancho el menú ⋯ y los filtros se abren como panel lateral. */
const CONSULTA_CELULAR = '(max-width: 640px)';
const suscribirCelular = (avisar: () => void) => {
  const mq = window.matchMedia(CONSULTA_CELULAR);
  mq.addEventListener('change', avisar);
  return () => mq.removeEventListener('change', avisar);
};
/** true en pantallas de hasta 640 px (se actualiza al girar o cambiar el tamaño). */
export function useCelular(): boolean {
  return useSyncExternalStore(
    suscribirCelular,
    () => window.matchMedia(CONSULTA_CELULAR).matches,
    () => false,
  );
}

/** Peligrosas siempre al final (separadas por un divisor). */
function separarAcciones(acciones: AccionFila[]) {
  const visibles = acciones.filter((a) => !a.oculta);
  return { visibles, seguras: visibles.filter((a) => !a.peligro), peligrosas: visibles.filter((a) => a.peligro) };
}

/**
 * Botón ⋯ con las acciones de una fila.
 * - Desde 641 px: menú desplegable anclado al botón, que nunca se sale de la pantalla (se dibuja fuera de la tabla para que no lo corte el scroll).
 * - Hasta 640 px: panel lateral que entra de derecha a izquierda, con el contexto de la fila arriba, acciones grandes
 *   y las de peligro separadas al fondo.
 */
export function MenuFila({ acciones, etiqueta = 'Más acciones', contexto }: { acciones: AccionFila[]; etiqueta?: string; contexto?: ContextoMenu }) {
  const { visibles, seguras, peligrosas } = separarAcciones(acciones);
  const celular = useCelular();
  const boton = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const [abierto, setAbierto] = useState(false);
  // null = recién abierto y aún sin medir (se dibuja oculto para medirlo).
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);
  // Posición del botón al abrir: un scroll de verdad (> 4 px) cierra; el rebote del toque no.
  const ancla = useRef<{ top: number; left: number } | null>(null);
  const desplegable = abierto && !celular;

  const cerrar = useCallback(() => {
    setAbierto(false);
    setPos(null);
  }, []);

  /** Coloca el menú SIEMPRE dentro de la pantalla (8 px de margen): por la izquierda del botón si cabe, si no por la derecha; abajo o arriba según el espacio. */
  const colocar = useCallback(() => {
    const b = boton.current?.getBoundingClientRect();
    const m = menu.current?.getBoundingClientRect();
    if (!b || !m) return;
    const M = 8;
    const vw = document.documentElement.clientWidth || window.innerWidth;
    const vh = window.innerHeight;
    let left = b.left + m.width <= vw - M ? b.left : b.right - m.width;
    left = Math.min(Math.max(M, left), Math.max(M, vw - m.width - M));
    let top: number;
    if (b.bottom + 6 + m.height <= vh - M) top = b.bottom + 6;
    else if (b.top - 6 - m.height >= M) top = b.top - 6 - m.height;
    else top = Math.max(M, vh - m.height - M);
    ancla.current = { top: b.top, left: b.left };
    setPos({ top, left });
  }, []);

  // Al abrir el desplegable: medir y colocar antes de pintar.
  useLayoutEffect(() => {
    if (desplegable) colocar();
  }, [desplegable, colocar]);

  // Ya colocado: foco en la primera acción disponible.
  const colocado = desplegable && pos !== null;
  useEffect(() => {
    if (colocado) menu.current?.querySelector<HTMLElement>('button:not(:disabled)')?.focus();
  }, [colocado]);

  useEffect(() => {
    if (!desplegable) return undefined;
    const fuera = (e: PointerEvent) => {
      if (!menu.current?.contains(e.target as Node) && !boton.current?.contains(e.target as Node)) cerrar();
    };
    const esc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        cerrar();
        boton.current?.focus();
      }
    };
    const alDesplazar = () => {
      const b = boton.current?.getBoundingClientRect();
      const a = ancla.current;
      if (!b || !a) return;
      if (Math.abs(b.top - a.top) > 4 || Math.abs(b.left - a.left) > 4) cerrar();
    };
    document.addEventListener('pointerdown', fuera);
    document.addEventListener('keydown', esc);
    window.addEventListener('scroll', alDesplazar, true);
    window.addEventListener('resize', colocar);
    return () => {
      document.removeEventListener('pointerdown', fuera);
      document.removeEventListener('keydown', esc);
      window.removeEventListener('scroll', alDesplazar, true);
      window.removeEventListener('resize', colocar);
    };
  }, [desplegable, cerrar, colocar]);

  if (!visibles.length) return null;

  const elegir = (a: AccionFila) => {
    cerrar();
    a.alPulsar();
  };
  /** Fila grande del panel lateral: ícono en cuadro, texto y, si está deshabilitada, su motivo. */
  const filaPanel = (a: AccionFila) => (
    <button key={a.texto} type="button" role="menuitem" className={cx('pl-accion', a.peligro && 'peligro')} disabled={a.deshabilitada} onClick={() => elegir(a)}>
      <span className="pl-accion-ico" aria-hidden>
        <a.icono className="ico" />
      </span>
      <span className="pl-accion-txt">
        {a.texto}
        {a.deshabilitada && a.motivo && <small>{a.motivo}</small>}
      </span>
    </button>
  );
  const filaMenu = (a: AccionFila) => (
    <button
      key={a.texto}
      type="button"
      role="menuitem"
      className={cx(a.peligro && 'peligro')}
      disabled={a.deshabilitada}
      title={a.deshabilitada ? a.motivo : undefined}
      onClick={(e) => {
        e.stopPropagation();
        // El foco pasa al ⋯ antes de cerrar: si la acción abre un modal, al cerrarlo vuelve aquí (no al <body>).
        boton.current?.focus({ preventScroll: true });
        elegir(a);
      }}
    >
      <a.icono className="ico" aria-hidden />
      <span className="menu-fila-txt">
        {a.texto}
        {a.deshabilitada && a.motivo && <small>{a.motivo}</small>}
      </span>
    </button>
  );

  return (
    <>
      <button
        ref={boton}
        type="button"
        className="td-mas"
        aria-label={contexto ? `${etiqueta}: ${contexto.titulo}` : etiqueta}
        aria-haspopup={celular ? 'dialog' : 'menu'}
        aria-expanded={abierto}
        onClick={(e) => {
          e.stopPropagation();
          if (abierto) cerrar();
          else setAbierto(true);
        }}
      >
        <Ellipsis className="ico" aria-hidden />
      </button>
      {celular && (
        <PanelLateral
          abierto={abierto}
          alCerrar={cerrar}
          titulo={contexto?.titulo ?? etiqueta}
          subtitulo={contexto?.subtitulo}
          detalle={contexto?.detalle}
          etiquetaCerrar="Cerrar acciones"
          devolverFoco={boton}
          className="pl-menu"
          pie={
            peligrosas.length > 0 && (
              <div className="pl-acciones" role="menu" aria-label="Acciones que no se pueden deshacer">
                {peligrosas.map(filaPanel)}
              </div>
            )
          }
        >
          <div className="pl-acciones" role="menu" aria-label={etiqueta}>
            {seguras.map(filaPanel)}
          </div>
        </PanelLateral>
      )}
      {desplegable &&
        createPortal(
          <div
            ref={menu}
            className={cx('menu-fila', pos && 'animate-entrar')}
            role="menu"
            aria-label={contexto ? `${etiqueta}: ${contexto.titulo}` : etiqueta}
            style={pos ? { top: pos.top, left: pos.left } : { top: 0, left: 0, visibility: 'hidden' }}
          >
            {seguras.map(filaMenu)}
            {seguras.length > 0 && peligrosas.length > 0 && <hr className="menu-fila-div" />}
            {peligrosas.map(filaMenu)}
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
