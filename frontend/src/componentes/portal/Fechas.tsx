import { useEffect, useRef, useState } from 'react';
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react';
import type { DiaCalendario } from '@/api/tipos';
import { Boton } from '@/componentes/ui/Boton';
import { Modal } from '@/componentes/ui/Modal';
import { diaSemanaCorto, fmtFecha, hoyLima, numeroDia } from '@/utilidades/fecha';
import { fmtSoles } from '@/utilidades/formato';
import { cx } from '@/utilidades/cx';

/** Marca de un día para la cancha elegida: feriado con recargo, feriado / no laborable cerrado. */
export interface MarcaDia {
  tipo: 'feriado' | 'cerrado';
  nombre: string;
  recargo?: number;
}

function etiquetaDia(d: DiaCalendario, marca: MarcaDia | undefined, hoy: string): { texto: string; clase?: string; off?: boolean } {
  if (marca?.tipo === 'cerrado' || (d.total === 0 && d.fecha !== hoy)) return { texto: 'Cerrado', clase: 'dia-off', off: true };
  if (d.fecha === hoy && d.libres === 0) return { texto: 'Cerrado', clase: 'dia-off', off: true };
  if (d.libres === 0) return { texto: 'Lleno', clase: 'dia-lleno' };
  if (marca?.tipo === 'feriado') return { texto: marca.recargo ? `+${fmtSoles(marca.recargo, { decimales: 0 })}` : 'Feriado', clase: 'dia-feriado' };
  return { texto: `${d.libres} libres` };
}

/**
 * Tira de próximos días que NUNCA se corta: desplazamiento horizontal con flechas (desde tablet) y el botón «Calendario»
 * para elegir cualquier fecha del rango permitido en una vista de mes.
 */
export function TiraFechas({ dias, seleccionada, alElegir, marcas }: { dias: DiaCalendario[]; seleccionada: string | null; alElegir: (f: string) => void; marcas?: Map<string, MarcaDia> }) {
  const hoy = hoyLima();
  const tira = useRef<HTMLDivElement>(null);
  const [bordes, setBordes] = useState({ izq: false, der: false });
  const [calendario, setCalendario] = useState(false);
  const medir = () => {
    const el = tira.current;
    if (!el) return;
    setBordes({ izq: el.scrollLeft > 4, der: el.scrollLeft + el.clientWidth < el.scrollWidth - 4 });
  };
  useEffect(() => {
    medir();
    const el = tira.current;
    if (!el) return undefined;
    const ro = new ResizeObserver(medir);
    ro.observe(el);
    return () => ro.disconnect();
  }, [dias]);
  // La fecha elegida siempre a la vista.
  useEffect(() => {
    tira.current?.querySelector<HTMLElement>('[aria-checked="true"]')?.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
  }, [seleccionada]);
  const mover = (d: number) => tira.current?.scrollBy({ left: d * tira.current.clientWidth * 0.8, behavior: 'smooth' });

  return (
    <div className="fechas">
      <div className={cx('tira-flechas', bordes.izq && 'con-izq', bordes.der && 'con-der')}>
        {bordes.izq && (
          <button type="button" className="tira-flecha izq" aria-label="Días anteriores" onClick={() => mover(-1)}>
            <ChevronLeft className="ico" aria-hidden />
          </button>
        )}
        <div className="date-strip sin-sangria" role="radiogroup" aria-label="Elige la fecha" ref={tira} onScroll={medir}>
          {dias.map((d) => {
            const m = marcas?.get(d.fecha);
            const e = etiquetaDia(d, m, hoy);
            const sel = d.fecha === seleccionada;
            return (
              <button
                key={d.fecha}
                type="button"
                role="radio"
                aria-checked={sel}
                aria-label={`${fmtFecha(d.fecha, 'larga')}, ${m ? `${m.nombre}, ` : ''}${e.texto}`}
                title={m?.nombre}
                className={cx('dia', d.fecha === hoy && 'dia-hoy', sel && 'dia-sel', e.clase && !sel && e.clase, sel && e.clase === 'dia-feriado' && 'dia-feriado')}
                onClick={() => alElegir(d.fecha)}
              >
                <span className="dia-sem">{d.fecha === hoy ? 'Hoy' : diaSemanaCorto(d.fecha)}</span>
                <span className="dia-num">{numeroDia(d.fecha)}</span>
                <span className="dia-mes tnum">{e.texto}</span>
              </button>
            );
          })}
        </div>
        {bordes.der && (
          <button type="button" className="tira-flecha der" aria-label="Días siguientes" onClick={() => mover(1)}>
            <ChevronRight className="ico" aria-hidden />
          </button>
        )}
      </div>
      <Boton variante="secundario" tamano="sm" icono={CalendarDays} className="justify-self-start" onClick={() => setCalendario(true)}>
        Ver calendario
      </Boton>
      <ModalCalendario abierto={calendario} dias={dias} marcas={marcas} seleccionada={seleccionada} alCerrar={() => setCalendario(false)} alElegir={alElegir} />
    </div>
  );
}

const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Setiembre', 'Octubre', 'Noviembre', 'Diciembre'];
const SEMANA = ['Lu', 'Ma', 'Mi', 'Ju', 'Vi', 'Sá', 'Do'];

/** Calendario de mes: días fuera del rango deshabilitados; feriados con recargo y días cerrados marcados. */
export function ModalCalendario({
  abierto,
  dias,
  marcas,
  seleccionada,
  alElegir,
  alCerrar,
}: {
  abierto: boolean;
  dias: DiaCalendario[];
  marcas?: Map<string, MarcaDia>;
  seleccionada: string | null;
  alElegir: (f: string) => void;
  alCerrar: () => void;
}) {
  const hoy = hoyLima();
  const porFecha = new Map(dias.map((d) => [d.fecha, d]));
  const primera = dias[0]?.fecha ?? hoy;
  const ultima = dias[dias.length - 1]?.fecha ?? hoy;
  const [mes, setMes] = useState(() => (seleccionada ?? primera).slice(0, 7));
  useEffect(() => {
    if (abierto) setMes((seleccionada ?? primera).slice(0, 7));
  }, [abierto, seleccionada, primera]);
  const [y, m] = mes.split('-').map(Number) as [number, number];
  const total = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const vacios = (new Date(Date.UTC(y, m - 1, 1)).getUTCDay() + 6) % 7;
  const desplazar = (d: number) => {
    const f = new Date(Date.UTC(y, m - 1 + d, 1));
    setMes(`${f.getUTCFullYear()}-${String(f.getUTCMonth() + 1).padStart(2, '0')}`);
  };
  return (
    <Modal abierto={abierto} alCerrar={alCerrar} titulo="Elige la fecha" subtitulo={`Puedes reservar hasta el ${fmtFecha(ultima, 'larga')}`} tamano="sm" variante="hoja">
      <div className="cal-portal">
        <div className="cal-portal-cab">
          <Boton variante="fantasma" tamano="sm" icono={ChevronLeft} etiqueta="Mes anterior" disabled={mes <= primera.slice(0, 7)} onClick={() => desplazar(-1)} />
          <b>{`${MESES[m - 1]} ${y}`}</b>
          <Boton variante="fantasma" tamano="sm" icono={ChevronRight} etiqueta="Mes siguiente" disabled={mes >= ultima.slice(0, 7)} onClick={() => desplazar(1)} />
        </div>
        <div className="cal-portal-grid" role="grid" aria-label={`${MESES[m - 1]} ${y}`}>
          {SEMANA.map((s) => (
            <span key={s} className="cal-portal-sem" aria-hidden>
              {s}
            </span>
          ))}
          {Array.from({ length: vacios }, (_, i) => (
            <span key={`v${i}`} />
          ))}
          {Array.from({ length: total }, (_, i) => {
            const f = `${mes}-${String(i + 1).padStart(2, '0')}`;
            const d = porFecha.get(f);
            const marca = marcas?.get(f);
            const e = d ? etiquetaDia(d, marca, hoy) : null;
            const fuera = !d;
            return (
              <button
                key={f}
                type="button"
                disabled={fuera || e?.off}
                aria-pressed={f === seleccionada}
                aria-label={`${fmtFecha(f, 'larga')}${marca ? `, ${marca.nombre}` : ''}${e ? `, ${e.texto}` : ', fuera de rango'}`}
                title={marca?.nombre}
                className={cx('cal-portal-dia', f === hoy && 'hoy', e?.clase, f === seleccionada && 'sel')}
                onClick={() => {
                  alElegir(f);
                  alCerrar();
                }}
              >
                <span className="tnum">{i + 1}</span>
                {e && !fuera && <small>{e.clase === 'dia-feriado' ? e.texto : e.off ? '' : e.clase === 'dia-lleno' ? 'Lleno' : d?.libres}</small>}
              </button>
            );
          })}
        </div>
        <div className="cal-portal-ley">
          <span>
            <i className="feriado" />
            Feriado (con adicional)
          </span>
          <span>
            <i className="cerrado" />
            Cerrado
          </span>
          <span>
            <i className="libre" />
            Turnos libres
          </span>
        </div>
      </div>
    </Modal>
  );
}
