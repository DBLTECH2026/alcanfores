import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  AlarmClock,
  ArrowRight,
  CalendarDays,
  ChartNoAxesColumn,
  ChevronDown,
  ChevronUp,
  CircleCheck,
  ClipboardList,
  Clock,
  Hourglass,
  Plus,
  RefreshCw,
  Smartphone,
  TriangleAlert,
  UserRound,
  UserX,
  Wrench,
} from 'lucide-react';
import { dashboardApi, pagosAdminApi, reservasAdminApi } from '@/api/admin';
import type { Dashboard, FilaPago, FilaReserva } from '@/api/admin/tipos';
import { useAuth } from '@/contexto/AuthContext';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { DatoIcono, ErrorCarga } from '@/componentes/panel/Herramientas';
import { TEXTO_METODO, horasA24 } from '@/componentes/panel/ModalesReserva';
import { Boton, BotonEnlace } from '@/componentes/ui/Boton';
import { Etiqueta, InsigniaEstado } from '@/componentes/ui/Estados';
import { Esqueleto, Tabla, TarjetaKpi, Vacio } from '@/componentes/ui/Datos';
import { ahoraLima, deLima, fmtFecha, fmtHace, fmtHora24, hoyLima } from '@/utilidades/fecha';
import { fmtSoles } from '@/utilidades/formato';
import { cx } from '@/utilidades/cx';

type Proxima = Dashboard['proximas'][number];
interface PorValidar {
  reserva: FilaReserva;
  pago: FilaPago | undefined;
}

/** Alertas visibles antes de «Ver todas». */
const MAX_ALERTAS = 5;

/** Minutos que faltan para el inicio del turno (negativo si ya empezó). */
const minutosPara = (fecha: string, hora: string) => deLima(`${fecha}T${hora}:00`).diff(ahoraLima(), 'minute');

/**
 * /admin/hoy — tablero del día (prototipo adm-02): indicadores, comprobantes por validar, agenda de hoy por cancha,
 * próximas reservas y alertas. Todo de /api/admin/dashboard + reservas EN_REVISION + pagos PENDIENTE.
 */
export default function Hoy() {
  useTituloPagina('Hoy');
  const { usuario } = useAuth();
  const navigate = useNavigate();
  const [cancha, setCancha] = useState<number | null>(null);
  const [version, setVersion] = useState(0);
  const d = useCarga(() => dashboardApi.obtener(hoyLima()), `hoy-${version}`);
  const pv = useCarga(async (): Promise<PorValidar[]> => {
    const [res, pagos] = await Promise.all([
      reservasAdminApi.listar({ estadoPago: 'EN_REVISION', size: 8, sort: 'fecha,asc' }),
      pagosAdminApi.listar({ estado: 'PENDIENTE', size: 50 }),
    ]);
    return res.contenido
      .map((r) => ({ reserva: r, pago: pagos.contenido.find((p) => p.codigoReserva === r.codigoReserva) }))
      .sort((a, b) => `${a.reserva.fecha}${a.reserva.horaInicio}`.localeCompare(`${b.reserva.fecha}${b.reserva.horaInicio}`));
  }, `pv-${version}`);
  const k = d.datos?.kpis;

  const agenda = useMemo(() => {
    const filas: Array<{ hora: string; idCancha: number; cancha: string; t: Dashboard['agenda'][number]['turnos'][number] }> = [];
    for (const c of d.datos?.agenda ?? []) {
      if (cancha !== null && c.idCancha !== cancha) continue;
      for (const t of c.turnos) if (t.estado !== 'DISPONIBLE') filas.push({ hora: t.horaInicio, idCancha: c.idCancha, cancha: c.nombre, t });
    }
    return filas.sort((a, b) => a.hora.localeCompare(b.hora) || a.idCancha - b.idCancha);
  }, [d.datos, cancha]);
  const ocupadasPorCancha = (id: number) => d.datos?.agenda.find((c) => c.idCancha === id)?.turnos.filter((t) => t.estado === 'RESERVADO').length ?? 0;
  const ahora = ahoraLima().format('HH:mm');
  const idxAhora = agenda.findIndex((f) => f.hora > ahora);
  const [verTodas, setVerTodas] = useState(false);
  const alertas = d.datos?.alertas ?? [];
  const alertasVistas = verTodas ? alertas : alertas.slice(0, MAX_ALERTAS);

  return (
    <>
      <CabeceraPagina
        titulo="Hoy"
        subtitulo={`${fmtFecha(hoyLima(), 'larga')} · hola, ${usuario?.nombres.split(' ')[0] ?? ''}`}
        acciones={
          <>
            <Boton variante="secundario" icono={RefreshCw} onClick={() => setVersion((v) => v + 1)} etiqueta="Actualizar" />
            <BotonEnlace to="/admin/agenda" variante="secundario" icono={CalendarDays}>
              Ver agenda
            </BotonEnlace>
            <BotonEnlace to="/admin/nueva-reserva" icono={Plus}>
              Nueva reserva
            </BotonEnlace>
          </>
        }
      />

      {d.error ? (
        <ErrorCarga alReintentar={d.recargar} />
      ) : (
        <>
          <div className="kpis">
            {!k ? (
              Array.from({ length: 6 }, (_, i) => (
                <div key={i} className="kpi">
                  <Esqueleto ancho="60%" alto={10} />
                  <Esqueleto ancho="40%" alto={26} />
                  <Esqueleto ancho="70%" alto={10} />
                </div>
              ))
            ) : (
              <>
                <TarjetaKpi etiqueta="Por validar" valor={k.porValidar} icono={Hourglass} tono="gol" detalle="comprobantes" alPulsar={() => navigate('/admin/reservas?estadoPago=EN_REVISION')} />
                <TarjetaKpi etiqueta="Reservas hoy" valor={k.reservasHoy} icono={ClipboardList} tono="noche" detalle={`en ${d.datos?.agenda.length ?? 0} canchas`} />
                <TarjetaKpi etiqueta="Confirmadas" valor={k.confirmadasHoy} icono={CircleCheck} tono="verde" detalle="con adelanto" />
                <TarjetaKpi etiqueta="Ocupación" valor={`${k.ocupacionHoyPct} %`} icono={ChartNoAxesColumn} tono="noche" barra={k.ocupacionHoyPct} />
                <TarjetaKpi etiqueta="No asistió" valor={`${k.noShowMesPct} %`} icono={UserX} tono="rojo" detalle="este mes" />
                <TarjetaKpi etiqueta="Semana" valor={k.reservasSemana} icono={CalendarDays} tono="gris" detalle="próximos 7 días" />
              </>
            )}
          </div>

          <div className="grid gap-4 xl:grid-cols-2 xl:items-start">
            {/* ------------------------- por validar ------------------------- */}
            <section className="pc" aria-labelledby="h-pv">
              <div className="pc-cab">
                <span className="td-ico">
                  <Hourglass className="ico" aria-hidden />
                </span>
                <h2 id="h-pv" className="pc-titular !text-[20px]">
                  Por validar
                </h2>
                {pv.datos && pv.datos.length > 0 && <span className="ma-cnt bg-amarillo text-noche px-1.5 rounded-[6px] text-[12px] font-bold">{pv.datos.length}</span>}
              </div>
              {pv.cargando && !pv.datos ? (
                <div className="grid gap-3 p-4">
                  <Esqueleto alto={56} />
                  <Esqueleto alto={56} />
                </div>
              ) : pv.error ? (
                <div className="p-4">
                  <Boton variante="secundario" icono={RefreshCw} onClick={pv.recargar}>
                    Reintentar
                  </Boton>
                </div>
              ) : !pv.datos?.length ? (
                <Vacio ilustracion="IL-23-todo-al-dia" titulo="Todo al día" texto="No hay comprobantes por validar." compacto />
              ) : (
                <ul className="grid">
                  {pv.datos.map(({ reserva: r, pago }, i) => {
                    const min = minutosPara(r.fecha, r.horaInicio);
                    return (
                      <li key={r.codigoReserva} className={cx('grid grid-cols-[auto_minmax(0,1fr)_auto] max-[479px]:grid-cols-[auto_minmax(0,1fr)] items-center gap-3 px-4 py-3', i > 0 && 'border-t border-[#EEF2F0]')}>
                        <div className="grid justify-items-center rounded-[10px] bg-amarillo-tinte px-2 py-1.5 w-[86px] flex-none">
                          <span className="text-[11.5px] text-texto-suave whitespace-nowrap">{fmtFecha(r.fecha, 'relativa')}</span>
                          <b className="font-titular text-[20px] leading-none tnum">{fmtHora24(r.horaInicio)}</b>
                        </div>
                        <div className="grid gap-1 min-w-0 flex-1">
                          <div className="flex items-center gap-2 flex-wrap">
                            <b className="tnum tracking-[.02em]">{r.codigoReserva}</b>
                            {min < 0 ? (
                              <Etiqueta tono="vencido">horario vencido</Etiqueta>
                            ) : min < 360 ? (
                              <Etiqueta tono="urgente" icono={AlarmClock}>
                                urgente
                              </Etiqueta>
                            ) : null}
                          </div>
                          <div className="flex flex-wrap gap-x-3 gap-y-1">
                            <DatoIcono icono={UserRound}>{`${r.cliente.nombres.split(' ')[0]} ${r.cliente.apellidos.split(' ')[0]}`}</DatoIcono>
                            <DatoIcono icono={CalendarDays}>{r.cancha}</DatoIcono>
                          </div>
                          {pago && (
                            <div className="flex flex-wrap gap-x-3 gap-y-1">
                              <DatoIcono icono={Smartphone}>
                                <b>{fmtSoles(pago.monto)}</b> · {TEXTO_METODO[pago.metodo] ?? pago.metodo}
                              </DatoIcono>
                              <DatoIcono icono={Clock}>{fmtHace(pago.fechaEnvio)}</DatoIcono>
                            </div>
                          )}
                        </div>
                        {/* En celular «Revisar» baja bajo los datos (col. 2) y los datos usan todo el ancho en vez de partirse en 7 líneas. */}
                        <BotonEnlace to={`/admin/reservas/${r.codigoReserva}`} variante={i === 0 ? 'primario' : 'secundario'} tamano="sm" iconoDer={ArrowRight} className="max-[479px]:col-start-2 max-[479px]:justify-self-start">
                          Revisar
                        </BotonEnlace>
                      </li>
                    );
                  })}
                </ul>
              )}
              <div className="px-4 py-1.5 border-t border-[#EEF2F0]">
                <Link to="/admin/reservas?estadoPago=EN_REVISION" className="enlace inline-flex items-center gap-1 text-[13.5px] min-h-[36px]">
                  Ver en Reservas
                  <ArrowRight className="ico ico-16" aria-hidden />
                </Link>
              </div>
            </section>

            {/* ------------------------- agenda de hoy ------------------------- */}
            <section className="pc" aria-labelledby="h-ag">
              <div className="pc-cab">
                <span className="td-ico">
                  <CalendarDays className="ico" aria-hidden />
                </span>
                <h2 id="h-ag" className="pc-titular !text-[20px]">
                  Agenda de hoy
                </h2>
                <div className="pc-der">
                  <Link to="/admin/agenda" className="enlace inline-flex items-center gap-1 text-[13.5px] min-h-[36px]">
                    Ver agenda
                    <ArrowRight className="ico ico-16" aria-hidden />
                  </Link>
                </div>
              </div>
              <div className="pc-cuerpo grid gap-3">
                <div className="flex flex-wrap gap-2" role="group" aria-label="Filtrar por cancha">
                  <button type="button" className={cx('pf-op', cancha === null && 'sel')} aria-pressed={cancha === null} onClick={() => setCancha(null)}>
                    Todas
                  </button>
                  {(d.datos?.agenda ?? []).map((c) => (
                    <button key={c.idCancha} type="button" className={cx('pf-op', cancha === c.idCancha && 'sel')} aria-pressed={cancha === c.idCancha} onClick={() => setCancha(c.idCancha)}>
                      {c.nombre.split(' · ')[0]}
                      <em>{ocupadasPorCancha(c.idCancha)}</em>
                    </button>
                  ))}
                </div>
                {!d.datos ? (
                  <Esqueleto alto={180} />
                ) : agenda.length === 0 ? (
                  <Vacio ilustracion="IL-30-hoy-sin-reservas" titulo="Sin reservas hoy" compacto acciones={<BotonEnlace to="/admin/nueva-reserva" variante="secundario" icono={Plus}>Nueva reserva</BotonEnlace>} />
                ) : (
                  <ol className="grid gap-2">
                    {agenda.map((f, i) => (
                      <li key={`${f.idCancha}-${f.hora}`} className="contents">
                        {i === idxAhora && (
                          <div className="agenda-ahora" aria-label={`Ahora ${ahora}`}>
                            <span>{ahora}</span>
                            <i />
                          </div>
                        )}
                        <div className="grid grid-cols-[56px_1fr] gap-3 items-start">
                          <div className="agenda-hora !pt-2">
                            {fmtHora24(f.hora)}
                          </div>
                          {f.t.estado === 'BLOQUEADO' ? (
                            <div className="agenda-celda bloqueado">
                              <span className="sub">
                                <Wrench className="ico" aria-hidden />
                                {f.cancha} · bloqueado
                              </span>
                            </div>
                          ) : (
                            <Link to={`/admin/reservas/${f.t.codigoReserva}`} className={cx('agenda-celda no-underline', f.t.estadoPago === 'ADELANTO_PAGADO' || f.t.estadoPago === 'PAGADO_TOTAL' ? 'confirmada' : 'pendiente')}>
                              <div className="fila">
                                <span className="cod">
                                  {f.cancha.split(' · ')[0]} · {f.t.codigoReserva}
                                </span>
                                {f.t.estadoPago && <InsigniaEstado dominio="estadoPago" estado={f.t.estadoPago} tamano="sm" />}
                              </div>
                              <span className="sub">
                                <UserRound className="ico" aria-hidden />
                                {f.t.cliente}
                              </span>
                            </Link>
                          )}
                        </div>
                      </li>
                    ))}
                  </ol>
                )}
              </div>
            </section>
          </div>

          <div className="grid gap-4 lg:grid-cols-[1.4fr_1fr] lg:items-start">
            <section className="grid gap-3" aria-labelledby="h-proximas">
              <h2 id="h-proximas" className="t-h3">
                Próximas reservas
              </h2>
              <Tabla<Proxima>
                etiqueta="Próximas reservas"
                filas={d.datos?.proximas ?? []}
                claveFila={(p) => p.codigoReserva}
                cargando={d.cargando}
                vacio={d.cargando ? 'Cargando…' : 'Sin reservas próximas'}
                alPulsarFila={(p) => navigate(`/admin/reservas/${p.codigoReserva}`)}
                columnas={[
                  { clave: 'codigo', titulo: 'Código', celda: (p) => <b className="tnum tracking-[.02em] whitespace-nowrap">{p.codigoReserva}</b> },
                  { clave: 'hora', titulo: 'Turno', celda: (p) => <span className="tnum">{`${fmtFecha(p.fecha, 'relativa')} · ${fmtHora24(p.horaInicio)}`}</span> },
                  { clave: 'cancha', titulo: 'Cancha', celda: (p) => p.cancha },
                  { clave: 'cliente', titulo: 'Cliente', celda: (p) => p.cliente },
                  { clave: 'estado', titulo: 'Pago', celda: (p) => <InsigniaEstado dominio="estadoPago" estado={p.estadoPago} /> },
                ]}
              />
            </section>
            <section className="pc" aria-labelledby="h-alertas">
              <div className="pc-cab">
                <h2 id="h-alertas">
                  <TriangleAlert className="ico !text-amarillo-ocre" aria-hidden />
                  Alertas
                </h2>
              </div>
              {d.datos && d.datos.alertas.length === 0 ? (
                <Vacio ilustracion="IL-23-todo-al-dia" titulo="Todo al día" compacto />
              ) : (
                <>
                  {/* Insignia y código arriba, el texto debajo a todo el ancho: se lee igual en el celular y en la columna angosta del escritorio. */}
                  <ul className="grid">
                    {alertasVistas.map((a, i) => (
                      <li key={i} className="flex flex-wrap items-center gap-x-3 gap-y-1.5 px-4 py-3 border-t border-[#EEF2F0] first:border-t-0 text-[13.5px]">
                        <Etiqueta tono={a.tipo === 'URGENTE' ? 'urgente' : a.tipo === 'VENCIDO' ? 'vencido' : 'neutro'} icono={a.tipo === 'URGENTE' ? AlarmClock : undefined}>
                          {a.tipo.toLowerCase().replace(/_/g, ' ')}
                        </Etiqueta>
                        {a.codigoReserva && (
                          <Link className="enlace text-[13px] tnum whitespace-nowrap ml-auto inline-flex items-center min-h-[36px]" to={`/admin/reservas/${a.codigoReserva}`}>
                            {a.codigoReserva}
                          </Link>
                        )}
                        <span className="basis-full">{horasA24(a.mensaje)}</span>
                      </li>
                    ))}
                  </ul>
                  {alertas.length > MAX_ALERTAS && (
                    <div className="px-4 py-3 border-t border-[#EEF2F0]">
                      <button type="button" className="enlace inline-flex items-center gap-1 text-[13.5px] min-h-[36px] bg-transparent border-0 p-0 cursor-pointer" aria-expanded={verTodas} onClick={() => setVerTodas((v) => !v)}>
                        {verTodas ? 'Ver menos' : `Ver todas (${alertas.length})`}
                        {verTodas ? <ChevronUp className="ico ico-16" aria-hidden /> : <ChevronDown className="ico ico-16" aria-hidden />}
                      </button>
                    </div>
                  )}
                </>
              )}
            </section>
          </div>
        </>
      )}
    </>
  );
}
