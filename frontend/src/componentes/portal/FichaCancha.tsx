import { useState, type KeyboardEvent, type MouseEvent, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { CalendarPlus, ChevronRight, Clock, CloudRain, Flag, Images, Layers, Lightbulb, Moon, Pencil, Ruler, Sun, Users } from 'lucide-react';
import type { CanchaDetalle, Complejo } from '@/api/tipos';
import { BotonEnlace } from '@/componentes/ui/Boton';
import { Estrellas } from '@/componentes/ui/Estrellas';
import { IconoDeporte, Ilustracion, ilustracionDeporte } from '@/componentes/ui/Marca';
import { VisorImagen } from '@/componentes/ui/VisorImagen';
import { ResumenCalificacion, iconoCaracteristica } from '@/componentes/portal/Canchas';
import { fmtHora12 } from '@/utilidades/fecha';
import { fmtSoles } from '@/utilidades/formato';
import { textoSemana } from '@/utilidades/horario';
import { cx } from '@/utilidades/cx';

/** Partes del detalle público que el editor visual del panel deja tocar (SPEC_V2 §F). */
export type ParteFicha = 'nombre' | 'galeria' | 'descripcion' | 'caracteristicas' | 'precios' | 'resenas';
export interface ModoEditor {
  activa: ParteFicha | null;
  alElegir: (p: ParteFicha) => void;
}

const ETIQUETA_PARTE: Record<ParteFicha, string> = {
  nombre: 'Nombre y resumen',
  galeria: 'Fotos',
  descripcion: 'Descripción',
  caracteristicas: 'Características',
  precios: 'Precios y horario',
  resenas: 'Reseñas',
};

/**
 * Envoltorio de una parte: en el portal no hace nada; en el editor la marca (`data-edit`), la resalta al pasar
 * y al hacer clic abre su panel. Los enlaces y botones de adentro no actúan en el editor.
 */
function Parte({ id, editor, children, className }: { id: ParteFicha; editor?: ModoEditor; children: ReactNode; className?: string }) {
  if (!editor) return <div className={className}>{children}</div>;
  const elegir = (e: MouseEvent | KeyboardEvent) => {
    e.preventDefault();
    e.stopPropagation();
    editor.alElegir(id);
  };
  return (
    <div
      data-edit={id}
      role="button"
      tabIndex={0}
      aria-label={`Editar ${ETIQUETA_PARTE[id].toLowerCase()}`}
      aria-pressed={editor.activa === id}
      className={cx('parte-edit', editor.activa === id && 'activa', className)}
      onClickCapture={elegir}
      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && e.target === e.currentTarget && elegir(e)}
    >
      <span className="parte-edit-etq" aria-hidden>
        <Pencil className="ico" />
        {ETIQUETA_PARTE[id]}
      </span>
      {children}
    </div>
  );
}

function Galeria({ cancha, editor }: { cancha: CanchaDetalle; editor?: ModoEditor }) {
  const fotos = cancha.fotos.length ? [...cancha.fotos].sort((a, b) => Number(b.principal) - Number(a.principal) || (a.orden ?? 0) - (b.orden ?? 0)) : [];
  const [activa, setActiva] = useState(0);
  const [visor, setVisor] = useState<number | null>(null);
  if (!fotos.length) {
    return (
      <div className="galeria-principal grid place-items-center cursor-default">
        <Ilustracion id={ilustracionDeporte(cancha.deporte)} className="w-[60%] h-auto" />
      </div>
    );
  }
  const actual = fotos[activa] ?? fotos[0];
  return (
    <div className="galeria">
      <button type="button" className="galeria-principal" onClick={() => !editor && setVisor(activa)} aria-label={`Ver fotos de ${cancha.nombre} en pantalla completa`}>
        <img src={actual?.url} alt={actual?.leyenda ?? cancha.nombre} />
        <span className="galeria-contador">
          <Images className="ico ico-16" aria-hidden />
          {`${activa + 1} / ${fotos.length}`}
        </span>
      </button>
      {fotos.length > 1 && (
        <div className="galeria-miniaturas" role="tablist" aria-label="Fotos de la cancha">
          {fotos.slice(0, 4).map((f, i) => (
            <button key={f.id} type="button" role="tab" aria-selected={i === activa} aria-label={f.leyenda ?? `Foto ${i + 1}`} className={cx('galeria-mini', i === activa && 'activa')} onClick={() => setActiva(i)}>
              <img src={f.url} alt="" loading="lazy" />
            </button>
          ))}
        </div>
      )}
      {visor !== null && (
        <VisorImagen
          imagenes={fotos.map((f) => ({ url: f.url, leyenda: f.leyenda }))}
          indice={visor}
          alCambiar={(i) => {
            setVisor(i);
            setActiva(i);
          }}
          alCerrar={() => setVisor(null)}
          titulo={`Fotos de ${cancha.nombre}`}
        />
      )}
    </div>
  );
}

/**
 * Detalle PÚBLICO de una cancha: lo usan el portal (/canchas/:slug) y el editor visual del panel (con `editor`),
 * así lo que se edita es exactamente lo que se publica. Las reseñas llegan como `resenas` (cada pantalla las carga a su modo).
 */
export function FichaCancha({
  cancha,
  complejo,
  resenas,
  accionResenas,
  formResena,
  editor,
}: {
  cancha: CanchaDetalle;
  complejo: Complejo | null;
  resenas: ReactNode;
  accionResenas?: ReactNode;
  formResena?: ReactNode;
  editor?: ModoEditor;
}) {
  const h = complejo?.horario;
  const horario = cancha.horario;
  const inicioNoche = horario?.inicioNoche ?? h?.inicioNoche ?? '18:00';
  const textoHorario = horario?.texto || (horario?.dias?.length ? textoSemana(horario.dias) : h ? `Todos los días ${fmtHora12(h.primerTurno)}–${fmtHora12(h.ultimoTurno)}` : '');
  const feriado = cancha.feriado;

  return (
    <div className="grid gap-6 lg:grid-cols-[1.45fr_1fr] lg:gap-10 lg:items-start">
      {/* ---------- columna principal ---------- */}
      <div className="grid gap-7 min-w-0">
        <Parte id="galeria" editor={editor}>
          <Galeria cancha={cancha} editor={editor} />
        </Parte>

        <Parte id="descripcion" editor={editor}>
          <section className="grid gap-3" aria-labelledby="h-desc">
            <h2 id="h-desc" className="t-h2">
              Sobre la cancha
            </h2>
            <p className="text-[15.5px] leading-relaxed text-noche whitespace-pre-line">{cancha.descripcionLarga || cancha.descripcion}</p>
            <ul className="flex flex-wrap gap-x-5 gap-y-2 t-sec">
              <li className="inline-flex items-center gap-1.5">
                <Layers className="ico ico-16 text-verde" aria-hidden />
                {cancha.superficie}
              </li>
              <li className="inline-flex items-center gap-1.5">
                <Users className="ico ico-16 text-verde" aria-hidden />
                {`${cancha.capacidad} jugadores`}
              </li>
              {cancha.medidas && (
                <li className="inline-flex items-center gap-1.5">
                  <Ruler className="ico ico-16 text-verde" aria-hidden />
                  {cancha.medidas}
                </li>
              )}
              {cancha.techada && (
                <li className="inline-flex items-center gap-1.5">
                  <CloudRain className="ico ico-16 text-verde" aria-hidden />
                  Techada
                </li>
              )}
              {cancha.iluminacionLed && (
                <li className="inline-flex items-center gap-1.5">
                  <Lightbulb className="ico ico-16 text-verde" aria-hidden />
                  Luz LED
                </li>
              )}
            </ul>
          </section>
        </Parte>

        {(cancha.caracteristicas.length > 0 || editor) && (
          <Parte id="caracteristicas" editor={editor}>
            <section className="grid gap-3" aria-labelledby="h-carac">
              <h2 id="h-carac" className="t-h2">
                Características
              </h2>
              {cancha.caracteristicas.length ? (
                <ul className="caracteristicas">
                  {cancha.caracteristicas.map((c) => {
                    const I = iconoCaracteristica(c);
                    return (
                      <li key={c} className="caracteristica">
                        <span className="caracteristica-ico">
                          <I className="ico ico-18" aria-hidden />
                        </span>
                        {c}
                      </li>
                    );
                  })}
                </ul>
              ) : (
                <p className="t-sec">Sin características: agrega la primera.</p>
              )}
            </section>
          </Parte>
        )}

        {/* ---------- reseñas ---------- */}
        <Parte id="resenas" editor={editor}>
          <section className="grid gap-5" aria-labelledby="h-resenas">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div className="grid gap-1">
                <span className="kicker kicker-raya">Reseñas</span>
                <h2 id="h-resenas" className="t-h2">
                  Lo que dicen los jugadores
                </h2>
              </div>
              {accionResenas}
            </div>
            <div className="grid gap-6 md:grid-cols-[260px_1fr] md:gap-8">
              <div className="card card-pad self-start">
                <ResumenCalificacion promedio={cancha.resumenResenas.promedio} total={cancha.resumenResenas.total} distribucion={cancha.resumenResenas.distribucion} />
              </div>
              <div className="grid gap-2 content-start min-w-0">{resenas}</div>
            </div>
            {formResena}
          </section>
        </Parte>
      </div>

      {/* ---------- columna lateral ---------- */}
      <aside className={cx('order-first lg:order-none grid gap-4', !editor && 'lg:sticky lg:top-[92px]')}>
        <div className="card overflow-hidden">
          <Parte id="nombre" editor={editor}>
            <div className="p-[18px] grid gap-3">
              <span className="badge b-noche justify-self-start">
                <IconoDeporte deporte={cancha.deporte} />
                <span>{cancha.deporteTexto}</span>
              </span>
              <h1 className="t-h1">{cancha.nombre}</h1>
              <a href="#h-resenas" className="inline-flex items-center gap-2 text-[14px] hover:underline">
                <Estrellas valor={cancha.resumenResenas.promedio} tamano={18} />
                <b className="tnum">{cancha.resumenResenas.total ? cancha.resumenResenas.promedio.toFixed(1) : 'Nueva'}</b>
                <span className="t-sec tnum">{`(${cancha.resumenResenas.total} reseñas)`}</span>
              </a>
              <p className="t-sec">{cancha.descripcion}</p>
            </div>
          </Parte>
          <Parte id="precios" editor={editor}>
            <div className="grid grid-cols-2 border-t border-borde">
              <div className="p-4 grid gap-1 border-r border-borde">
                <span className="inline-flex items-center gap-1.5 font-semibold text-[13.5px]">
                  <Sun className="ico ico-16 text-amarillo-ocre" aria-hidden />
                  Día
                </span>
                <span className="t-mini tnum">{`Antes de las ${fmtHora12(inicioNoche)}`}</span>
                <span className="desde-val">
                  {fmtSoles(cancha.precioDia, { decimales: 0 })}
                  <small>/ turno</small>
                </span>
              </div>
              <div className="p-4 grid gap-1">
                <span className="inline-flex items-center gap-1.5 font-semibold text-[13.5px]">
                  <Moon className="ico ico-16" aria-hidden />
                  Noche
                </span>
                <span className="t-mini tnum">{`Desde las ${fmtHora12(inicioNoche)}`}</span>
                <span className="desde-val">
                  {fmtSoles(cancha.precioNoche, { decimales: 0 })}
                  <small>/ turno</small>
                </span>
              </div>
            </div>
            {(textoHorario || feriado) && (
              <ul className="ficha-horario">
                {textoHorario && (
                  <li>
                    <Clock className="ico" aria-hidden />
                    <span>{horario?.duracionTurnoMin && horario.duracionTurnoMin !== 60 ? `${textoHorario} · turnos de ${horario.duracionTurnoMin} min` : textoHorario}</span>
                  </li>
                )}
                {feriado && (
                  <li>
                    <Flag className="ico" aria-hidden />
                    <span>{feriado.modo === 'CERRADA' ? 'Cerrada en feriados' : feriado.recargo > 0 ? `Feriados: +${fmtSoles(feriado.recargo, { decimales: 0 })} por turno` : 'Abre en feriados'}</span>
                  </li>
                )}
              </ul>
            )}
          </Parte>
          <div className="p-[18px] pt-4 border-t border-borde grid gap-2 bg-superficie-suave">
            {editor ? (
              <span className="btn btn-primario btn-lg btn-bloque pointer-events-none" aria-hidden>
                <CalendarPlus className="ico" />
                <span>Reservar esta cancha</span>
              </span>
            ) : (
              <BotonEnlace to={`/reservar?cancha=${cancha.id}`} icono={CalendarPlus} tamano="lg" bloque>
                Reservar esta cancha
              </BotonEnlace>
            )}
            <span className="t-mini text-center">{`Adelanto ${complejo?.reglas.adelantoPct ?? 50} % · el saldo, al llegar`}</span>
          </div>
        </div>
      </aside>
    </div>
  );
}

/** Migas «Canchas › Nombre» del detalle público. */
export function MigasCancha({ nombre }: { nombre: string }) {
  return (
    <nav className="flex items-center gap-1.5 text-[13px] text-texto-suave" aria-label="Migas">
      <Link to="/canchas" className="hover:underline">
        Canchas
      </Link>
      <ChevronRight className="ico ico-14" aria-hidden />
      <span aria-current="page" className="text-noche font-semibold">
        {nombre}
      </span>
    </nav>
  );
}
