import type { CSSProperties, ReactNode } from 'react';
import { ArrowDownRight, ArrowUpRight, ChevronLeft, ChevronRight, type LucideIcon } from 'lucide-react';
import { cx } from '@/utilidades/cx';
import { iniciales } from '@/utilidades/formato';
import type { IdIlustracion } from '@/utilidades/recursos';
import { Ilustracion } from './Marca';

// ------------------------------ TarjetaKpi ------------------------------
export type TonoKpi = 'normal' | 'verde' | 'gol' | 'rojo' | 'noche' | 'gris' | 'alerta' | 'oscuro';

/** Indicador del panel: etiqueta + ícono en cuadro teñido + número grande + detalle. */
export function TarjetaKpi({
  etiqueta,
  valor,
  detalle,
  icono: Icono,
  tono = 'normal',
  delta,
  barra,
  activa,
  alPulsar,
  className,
}: {
  etiqueta: string;
  valor: ReactNode;
  detalle?: ReactNode;
  icono?: LucideIcon;
  tono?: TonoKpi;
  delta?: { texto: string; baja?: boolean };
  barra?: number;
  activa?: boolean;
  alPulsar?: () => void;
  className?: string;
}) {
  const clase = cx(
    'kpi',
    ['verde', 'gol', 'rojo', 'noche', 'gris'].includes(tono) && `t-${tono}`,
    (tono === 'alerta' || tono === 'oscuro') && `kpi-${tono}`,
    activa && 'activa',
    className,
  );
  const contenido = (
    <>
      <div className="kpi-cab">
        <span className="kpi-et">{etiqueta}</span>
        {Icono && (
          <span className="kpi-ico">
            <Icono className="ico" aria-hidden />
          </span>
        )}
      </div>
      <div className="kpi-val">{valor}</div>
      {barra !== undefined && (
        <div className="kpi-barra">
          <i style={{ width: `${Math.max(0, Math.min(100, barra))}%` }} />
        </div>
      )}
      {(detalle || delta) && (
        <div className="kpi-det">
          {delta && (
            <span className={cx('kpi-delta', delta.baja && 'baja')}>
              {delta.baja ? <ArrowDownRight className="ico ico-14" aria-hidden /> : <ArrowUpRight className="ico ico-14" aria-hidden />}
              {delta.texto}
            </span>
          )}
          {detalle && <span>{detalle}</span>}
        </div>
      )}
    </>
  );
  return alPulsar ? (
    <button type="button" className={clase} aria-pressed={activa} onClick={alPulsar}>
      {contenido}
    </button>
  ) : (
    <div className={clase}>{contenido}</div>
  );
}

// ------------------------------ Vacío ------------------------------
/** Estado vacío / error: ilustración + título ≤ 5 palabras + 1 acción. */
export function Vacio({ ilustracion, titulo, texto, acciones, compacto, className }: { ilustracion?: IdIlustracion; titulo: ReactNode; texto?: ReactNode; acciones?: ReactNode; compacto?: boolean; className?: string }) {
  return (
    <div className={cx('vacio', compacto && 'compacto', className)}>
      {ilustracion && <Ilustracion id={ilustracion} />}
      <h3 className="vacio-titulo">{titulo}</h3>
      {texto && <p className="vacio-texto">{texto}</p>}
      {acciones && <div className="grupo-botones">{acciones}</div>}
    </div>
  );
}

// ------------------------------ Cargando ------------------------------
export function Spinner({ grande, className, etiqueta = 'Cargando…' }: { grande?: boolean; className?: string; etiqueta?: string }) {
  return <span className={cx('spinner', grande && 'spinner-lg', className)} role="status" aria-label={etiqueta} />;
}
/** Bloque gris pulsante (skeleton). */
export function Esqueleto({ ancho = '100%', alto = 12, radio = 8, className }: { ancho?: string | number; alto?: number; radio?: number; className?: string }) {
  return <span className={cx('skel block', className)} style={{ width: ancho, height: alto, borderRadius: radio }} aria-hidden />;
}
/** Cargando de página completa (centrado). */
export function Cargando({ texto = 'Cargando…', className }: { texto?: string; className?: string }) {
  return (
    <div className={cx('grid place-items-center gap-3 py-16 text-texto-suave', className)} role="status" aria-live="polite">
      <span className="spinner spinner-lg text-verde" aria-hidden />
      <span className="t-sec">{texto}</span>
    </div>
  );
}

// ------------------------------ Avatar ------------------------------
export function Avatar({ nombre, tamano = 'md', admin }: { nombre: string; tamano?: 'sm' | 'md' | 'lg'; admin?: boolean }) {
  return (
    <span className={cx('avatar', tamano !== 'md' && `avatar-${tamano}`, admin && 'admin')} aria-hidden>
      {iniciales(nombre)}
    </span>
  );
}

// ------------------------------ ListaDatos ------------------------------
/** Lista de definición «clave · valor». */
export function ListaDatos({ pares, amplio, className }: { pares: Array<[ReactNode, ReactNode]>; amplio?: boolean; className?: string }) {
  return (
    <dl className={cx('dl', amplio && 'amplio', className)} style={{ padding: 0 }}>
      {pares.map(([k, v], i) => (
        <div key={i}>
          <dt>{k}</dt>
          <dd>{v}</dd>
        </div>
      ))}
    </dl>
  );
}

// ------------------------------ Pestañas ------------------------------
export interface Pestana<T extends string> {
  valor: T;
  texto: string;
  icono?: LucideIcon;
  cuenta?: number;
  alerta?: boolean;
}
/** Pestañas con contador (en el panel se ven como segmentos con borde). */
export function Pestanas<T extends string>({ items, activa, alCambiar, etiqueta = 'Secciones' }: { items: Array<Pestana<T>>; activa: T; alCambiar: (v: T) => void; etiqueta?: string }) {
  return (
    <div className="pestanas" role="tablist" aria-label={etiqueta}>
      {items.map((p) => {
        const Icono = p.icono;
        return (
          <button key={p.valor} type="button" role="tab" aria-selected={p.valor === activa} className={cx('pestana', p.valor === activa && 'activa')} onClick={() => alCambiar(p.valor)}>
            {Icono && <Icono className="ico ico-18" aria-hidden />}
            {p.texto}
            {p.cuenta !== undefined && <span className={cx('cuenta', p.alerta && 'cuenta-alerta')}>{p.cuenta}</span>}
          </button>
        );
      })}
    </div>
  );
}

// ------------------------------ Segmentado ------------------------------
export interface OpcionSeg<T extends string> {
  valor: T;
  texto: ReactNode;
  icono?: LucideIcon;
}
/** Selector segmentado (radiogroup). */
export function Segmentado<T extends string>({
  opciones,
  valor,
  alCambiar,
  etiqueta,
  bloque,
  pequeno,
  className,
}: {
  opciones: Array<OpcionSeg<T>>;
  valor: T | null;
  alCambiar: (v: T) => void;
  etiqueta: string;
  bloque?: boolean;
  pequeno?: boolean;
  className?: string;
}) {
  return (
    <div className={cx('seg', bloque && 'bloque', pequeno && 'seg-sm', className)} role="radiogroup" aria-label={etiqueta}>
      {opciones.map((o) => {
        const Icono = o.icono;
        const activo = o.valor === valor;
        return (
          <button key={o.valor} type="button" role="radio" aria-checked={activo} className={cx('seg-op', activo && 'seg-activo')} onClick={() => alCambiar(o.valor)}>
            {Icono && <Icono className="ico ico-18" aria-hidden />}
            {o.texto}
          </button>
        );
      })}
    </div>
  );
}

// ------------------------------ Tabla ------------------------------
export interface Columna<F> {
  clave: string;
  titulo: string;
  alinear?: 'izq' | 'der' | 'centro';
  ancho?: string;
  /** false = no se muestra en la tarjeta de móvil. */
  enTarjeta?: boolean;
  celda: (fila: F) => ReactNode;
}
/**
 * Tabla del sistema. En < 1024 px se vuelve lista de tarjetas (2 columnas entre 768 y 1023).
 * `tituloTarjeta` = clave de la columna que encabeza la tarjeta; `acciones` = botones al pie (tarjeta) o columna final.
 */
export function Tabla<F>({
  columnas,
  filas,
  claveFila,
  tituloTarjeta,
  insignias,
  acciones,
  vacio = 'Sin resultados',
  pie,
  etiqueta = 'Tabla',
  seleccionada,
  alPulsarFila,
  cargando,
}: {
  columnas: Array<Columna<F>>;
  filas: F[];
  claveFila: (f: F) => string | number;
  tituloTarjeta?: string;
  insignias?: (f: F) => ReactNode;
  acciones?: (f: F) => ReactNode;
  vacio?: ReactNode;
  pie?: ReactNode;
  etiqueta?: string;
  seleccionada?: (f: F) => boolean;
  alPulsarFila?: (f: F) => void;
  cargando?: boolean;
}) {
  const tk = tituloTarjeta ?? columnas[0]?.clave;
  const al = (c: Columna<F>) => cx(c.alinear === 'der' && 'num', c.alinear === 'centro' && 'centro');
  const colTitulo = columnas.find((c) => c.clave === tk);
  return (
    <div className="tabla-resp" aria-busy={cargando || undefined}>
      <div className="tabla-wrap">
        <div className="tabla-scroll">
          <table className="tabla" aria-label={etiqueta}>
            <thead>
              <tr>
                {columnas.map((c) => (
                  <th key={c.clave} scope="col" className={al(c)} style={c.ancho ? ({ width: c.ancho } as CSSProperties) : undefined}>
                    {c.titulo}
                  </th>
                ))}
                {acciones && <th scope="col" className="acc"><span className="sr-only">Acciones</span></th>}
              </tr>
            </thead>
            <tbody>
              {filas.length ? (
                filas.map((f) => (
                  <tr
                    key={claveFila(f)}
                    className={cx(seleccionada?.(f) && 'seleccionada', alPulsarFila && 'cursor-pointer')}
                    onClick={alPulsarFila ? () => alPulsarFila(f) : undefined}
                  >
                    {columnas.map((c) => (
                      <td key={c.clave} className={al(c)}>
                        {c.celda(f)}
                      </td>
                    ))}
                    {acciones && <td className="acc">{acciones(f)}</td>}
                  </tr>
                ))
              ) : (
                <tr className="tabla-vacia">
                  <td colSpan={columnas.length + (acciones ? 1 : 0)}>{vacio}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        {pie}
      </div>
      <div className="tabla-tarjetas">
        {filas.length ? (
          filas.map((f) => (
            <article key={claveFila(f)} className="tt-card">
              <div className="tt-cab">
                <div className="tt-titulo">{colTitulo?.celda(f)}</div>
              </div>
              {insignias && <div className="tt-badges">{insignias(f)}</div>}
              <dl className="tt-filas">
                {columnas
                  .filter((c) => c.clave !== tk && c.enTarjeta !== false && c.titulo)
                  .map((c) => (
                    <div key={c.clave}>
                      <dt>{c.titulo}</dt>
                      <dd>{c.celda(f)}</dd>
                    </div>
                  ))}
              </dl>
              {acciones && <div className="tt-acc">{acciones(f)}</div>}
            </article>
          ))
        ) : (
          <div className="tt-card t-sec text-center">{vacio}</div>
        )}
        {pie && <div className="card">{pie}</div>}
      </div>
    </div>
  );
}

// ------------------------------ Paginación ------------------------------
/** Paginación (página 0-based como el back): «1–5 de 23 reseñas» + botones. */
export function Paginacion({
  pagina,
  totalPaginas,
  totalElementos,
  tamanio,
  alCambiar,
  etiqueta = 'resultados',
}: {
  pagina: number;
  totalPaginas: number;
  totalElementos?: number;
  tamanio?: number;
  alCambiar: (p: number) => void;
  etiqueta?: string;
}) {
  if (totalPaginas <= 0) return null;
  const actual = pagina + 1;
  let nums: Array<number | '…'>;
  if (totalPaginas <= 5) nums = Array.from({ length: totalPaginas }, (_, i) => i + 1);
  else if (actual <= 3) nums = [1, 2, 3, 4, '…', totalPaginas];
  else if (actual >= totalPaginas - 2) nums = [1, '…', totalPaginas - 3, totalPaginas - 2, totalPaginas - 1, totalPaginas];
  else nums = [1, '…', actual - 1, actual, actual + 1, '…', totalPaginas];
  const desde = tamanio && totalElementos !== undefined ? pagina * tamanio + 1 : null;
  const hasta = tamanio && totalElementos !== undefined ? Math.min(totalElementos, (pagina + 1) * tamanio) : null;
  return (
    <nav className="paginacion" aria-label="Paginación">
      <span className="tnum">{desde !== null && totalElementos !== undefined ? `${desde}–${hasta} de ${totalElementos} ${etiqueta}` : ''}</span>
      <div className="pag-botones">
        <button type="button" className="pag-btn" aria-label="Página anterior" disabled={pagina <= 0} onClick={() => alCambiar(pagina - 1)}>
          <ChevronLeft className="ico" aria-hidden />
        </button>
        {nums.map((n, i) =>
          n === '…' ? (
            <span key={`e${i}`} className="px-1" aria-hidden>
              …
            </span>
          ) : (
            <button key={n} type="button" className={cx('pag-btn', n === actual && 'activa')} aria-current={n === actual ? 'page' : undefined} aria-label={`Página ${n}`} onClick={() => alCambiar(n - 1)}>
              {n}
            </button>
          ),
        )}
        <button type="button" className="pag-btn" aria-label="Página siguiente" disabled={pagina >= totalPaginas - 1} onClick={() => alCambiar(pagina + 1)}>
          <ChevronRight className="ico" aria-hidden />
        </button>
      </div>
    </nav>
  );
}
