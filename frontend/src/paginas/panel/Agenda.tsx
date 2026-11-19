import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { z } from 'zod';
import {
  CalendarCog,
  CalendarDays,
  CalendarPlus,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  CircleCheck,
  Eye,
  History,
  Hourglass,
  Lock,
  LockOpen,
  RefreshCw,
  UserCheck,
  UserRound,
  UserX,
  Wrench,
} from 'lucide-react';
import { reservasAdminApi, turnosApi } from '@/api/admin';
import type { AgendaDia, TurnoAgenda } from '@/api/admin/tipos';
import { esErrorApi } from '@/api/cliente';
import { useAuth } from '@/contexto/AuthContext';
import { useToast } from '@/contexto/ToastContext';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { ErrorCarga, MenuFila, ModalConfirmar } from '@/componentes/panel/Herramientas';
import { Boton } from '@/componentes/ui/Boton';
import { AreaTexto, Casilla, CampoTexto, Selector } from '@/componentes/ui/Campo';
import { Esqueleto, Segmentado, Vacio } from '@/componentes/ui/Datos';
import { InsigniaEstado } from '@/componentes/ui/Estados';
import { IconoDeporte } from '@/componentes/ui/Marca';
import { ahoraLima, fmtFecha, fmtRango24, hoyLima, sumarDias } from '@/utilidades/fecha';
import { fmtSoles } from '@/utilidades/formato';
import { cx } from '@/utilidades/cx';
import { esquemaBloqueo, esquemaGenerar, esquemaRango } from './esquemasPanel';

type Cancha = AgendaDia['canchas'][number];
type Celda = { cancha: Cancha; turno: TurnoAgenda };

function useEsMovil() {
  const [m, setM] = useState(() => window.matchMedia('(max-width: 767px)').matches);
  useEffect(() => {
    const mq = window.matchMedia('(max-width: 767px)');
    const f = () => setM(mq.matches);
    mq.addEventListener('change', f);
    return () => mq.removeEventListener('change', f);
  }, []);
  return m;
}

/** /admin/agenda — grilla canchas × horas del día (prototipo adm-30) con bloqueos, asistencia y generación de turnos. */
export default function Agenda() {
  useTituloPagina('Agenda');
  const navigate = useNavigate();
  const toast = useToast();
  const { tieneRol } = useAuth();
  const esAdmin = tieneRol('ADMIN');
  const movil = useEsMovil();
  const [sp, setSp] = useSearchParams();
  const fecha = sp.get('fecha') || hoyLima();
  const ponerFecha = (f: string) => setSp(f === hoyLima() ? {} : { fecha: f }, { replace: true });
  const [version, setVersion] = useState(0);
  const ag = useCarga(() => turnosApi.agenda(fecha), `${fecha}-${version}`);
  const [verPasados, setVerPasados] = useState(false);
  const [canchaMovil, setCanchaMovil] = useState<number | null>(null);
  const [modal, setModal] = useState<
    | { tipo: 'bloquear' | 'desbloquear'; celda: Celda }
    | { tipo: 'ASISTIO' | 'NO_ASISTIO'; celda: Celda }
    | { tipo: 'rango' }
    | { tipo: 'generar' }
    | null
  >(null);
  const recargar = () => {
    setModal(null);
    setVersion((v) => v + 1);
  };

  const canchas = useMemo(() => ag.datos?.canchas ?? [], [ag.datos]);
  useEffect(() => {
    if (canchaMovil === null && canchas[0]) setCanchaMovil(canchas[0].idCancha);
  }, [canchas, canchaMovil]);
  const visibles = movil ? canchas.filter((c) => c.idCancha === canchaMovil) : canchas;
  const horas = useMemo(() => [...new Set(canchas.flatMap((c) => c.turnos.map((t) => t.horaInicio)))].sort(), [canchas]);
  const esHoy = fecha === hoyLima();
  const ahora = ahoraLima().format('HH:mm');
  const finDe = (h: string) => canchas.flatMap((c) => c.turnos).find((t) => t.horaInicio === h)?.horaFin ?? h;
  const pasadas = esHoy ? horas.filter((h) => finDe(h) <= ahora) : fecha < hoyLima() ? horas : [];
  const enCurso = esHoy ? horas.find((h) => h <= ahora && finDe(h) > ahora) : undefined;
  const mostrar = verPasados ? horas : horas.filter((h) => !pasadas.includes(h));

  const celdaDe = (c: Cancha, h: string) => c.turnos.find((t) => t.horaInicio === h);
  const libres = (c: Cancha) => c.turnos.filter((t) => t.estado === 'DISPONIBLE' && !pasadas.includes(t.horaInicio)).length;

  const desbloquear = async (c: Celda) => {
    try {
      await turnosApi.desbloquear(c.turno.idTurno);
      toast.exito('Turno liberado');
      recargar();
    } catch {
      /* toast del interceptor */
    }
  };
  const asistencia = async (c: Celda, a: 'ASISTIO' | 'NO_ASISTIO') => {
    if (!c.turno.reserva) return;
    try {
      await reservasAdminApi.asistencia(c.turno.reserva.codigoReserva, a);
      toast.exito(a === 'ASISTIO' ? 'Asistencia registrada' : 'No asistió registrado');
      recargar();
    } catch {
      /* toast del interceptor */
    }
  };

  const celda = (c: Cancha, h: string) => {
    const t = celdaDe(c, h);
    if (!t) return <div key={c.idCancha} className="agenda-celda pasado" aria-hidden />;
    const pasado = pasadas.includes(h);
    const r = t.reserva;
    const info: Celda = { cancha: c, turno: t };
    if (r) {
      const conf = r.estado === 'CONFIRMADA';
      return (
        <div key={c.idCancha} className={cx('agenda-celda', conf ? 'confirmada' : 'pendiente', pasado && 'pasado')}>
          <div className="fila">
            <button type="button" className="cod bg-transparent border-0 p-0 cursor-pointer text-left tnum" onClick={() => navigate(`/admin/reservas/${r.codigoReserva}`)}>
              {r.codigoReserva}
            </button>
            <InsigniaEstado dominio={r.estadoPago === 'EN_REVISION' ? 'estadoPago' : 'reserva'} estado={r.estadoPago === 'EN_REVISION' ? 'EN_REVISION' : r.estado} tamano="sm" />
          </div>
          <span className="sub">
            <UserRound className="ico" aria-hidden />
            <span className="truncar">{r.cliente}</span>
          </span>
          {conf && r.asistencia !== 'PENDIENTE' && <InsigniaEstado dominio="asistencia" estado={r.asistencia} tamano="sm" />}
          <div className="flex justify-end -mb-1">
            <MenuFila
              acciones={[
                { texto: 'Ver reserva', icono: Eye, alPulsar: () => navigate(`/admin/reservas/${r.codigoReserva}`) },
                { texto: 'Asistió', icono: UserCheck, alPulsar: () => setModal({ tipo: 'ASISTIO', celda: info }), oculta: !conf || r.asistencia !== 'PENDIENTE' },
                { texto: 'No asistió', icono: UserX, alPulsar: () => setModal({ tipo: 'NO_ASISTIO', celda: info }), oculta: !conf || r.asistencia !== 'PENDIENTE' },
              ]}
            />
          </div>
        </div>
      );
    }
    if (t.estado === 'BLOQUEADO')
      return (
        <div key={c.idCancha} className={cx('agenda-celda bloqueado', pasado && 'pasado')}>
          <span className="sub !text-profundo font-semibold">
            <Wrench className="ico" aria-hidden />
            Bloqueado
          </span>
          <span className="sub">{t.motivoBloqueo}</span>
          {!pasado && (
            <div className="flex justify-end -mb-1">
              <MenuFila acciones={[{ texto: 'Desbloquear', icono: LockOpen, alPulsar: () => setModal({ tipo: 'desbloquear', celda: info }) }]} />
            </div>
          )}
        </div>
      );
    return (
      <div key={c.idCancha} className={cx('agenda-celda', !pasado && c.activa && 'libre', pasado && 'pasado')}>
        <div className="fila">
          <span className="sub">{pasado ? (h === enCurso ? 'En curso' : 'Pasado') : 'Libre'}</span>
          <span className="precio">{fmtSoles(t.precio, { decimales: 0 })}</span>
        </div>
        {!pasado && (
          <div className="flex justify-end -mb-1 mt-auto">
            <MenuFila
              acciones={[
                { texto: 'Reservar aquí', icono: CalendarPlus, alPulsar: () => navigate(`/admin/nueva-reserva?idCancha=${c.idCancha}&fecha=${fecha}&idTurno=${t.idTurno}`) },
                { texto: 'Bloquear', icono: Lock, alPulsar: () => setModal({ tipo: 'bloquear', celda: info }) },
              ]}
            />
          </div>
        )}
      </div>
    );
  };

  const cols = visibles.length || 1;
  return (
    <>
      <CabeceraPagina
        titulo="Agenda"
        subtitulo="Turnos del día por cancha, bloqueos y asistencia"
        acciones={
          <>
            <Boton variante="secundario" className="!flex-none" icono={ChevronLeft} etiqueta="Día anterior" onClick={() => ponerFecha(sumarDias(fecha, -1))} />
            <label className="bb-fechas cursor-pointer flex-1 justify-center min-w-0">
              <CalendarDays className="ico" aria-hidden />
              <b>{esHoy ? 'Hoy · ' : ''}{fmtFecha(fecha, 'corta')}</b>
              <input type="date" className="sr-only" value={fecha} onChange={(e) => e.target.value && ponerFecha(e.target.value)} aria-label="Elegir fecha" />
            </label>
            <Boton variante="secundario" className="!flex-none" icono={ChevronRight} etiqueta="Día siguiente" onClick={() => ponerFecha(sumarDias(fecha, 1))} />
          </>
        }
      />

      <div className="flex flex-wrap items-center gap-3 justify-between">
        <div className="leyenda-panel" aria-label="Leyenda">
          <span>
            <i style={{ borderLeftColor: 'var(--grass)' }} />
            Libre
          </span>
          <span>
            <i style={{ borderLeftColor: 'var(--amarillo)', background: '#FFFCF0' }} />
            Pendiente
          </span>
          <span>
            <i style={{ borderLeftColor: 'var(--verde)', background: '#F6FBF7' }} />
            Confirmada
          </span>
          <span>
            <i style={{ borderLeftColor: 'var(--profundo)', borderStyle: 'dashed' }} />
            Bloqueado
          </span>
          <span>
            <Hourglass className="ico ico-14" aria-hidden />
            En revisión
          </span>
          <span>
            <CircleCheck className="ico ico-14" aria-hidden />
            Validado
          </span>
        </div>
        <div className="flex flex-wrap gap-2">
          <Boton variante="secundario" tamano="sm" icono={RefreshCw} onClick={() => setVersion((v) => v + 1)}>
            Actualizar
          </Boton>
          <Boton variante="secundario" tamano="sm" icono={Lock} onClick={() => setModal({ tipo: 'rango' })}>
            Bloquear rango
          </Boton>
          {esAdmin && (
            <Boton variante="oscuro" tamano="sm" icono={CalendarCog} onClick={() => setModal({ tipo: 'generar' })}>
              Generar turnos
            </Boton>
          )}
        </div>
      </div>

      {ag.error ? (
        <ErrorCarga alReintentar={ag.recargar} />
      ) : !ag.datos ? (
        <div className="card card-pad grid gap-3">
          <Esqueleto alto={50} />
          <Esqueleto alto={70} />
          <Esqueleto alto={70} />
        </div>
      ) : horas.length === 0 ? (
        <div className="card">
          <Vacio
            ilustracion="IL-26-sin-turnos"
            titulo="Sin turnos este día"
            texto={esAdmin ? 'Genera los turnos de esa fecha.' : 'Pide al administrador que genere los turnos.'}
            acciones={esAdmin ? <Boton icono={CalendarCog} onClick={() => setModal({ tipo: 'generar' })}>Generar turnos</Boton> : undefined}
          />
        </div>
      ) : (
        <section className="card card-pad grid gap-3 min-w-0">
          {movil && (
            <div className="overflow-x-auto min-w-0">
            <Segmentado
              etiqueta="Cancha"
              bloque
              pequeno
              valor={canchaMovil !== null ? String(canchaMovil) : null}
              alCambiar={(v) => setCanchaMovil(Number(v))}
              opciones={canchas.map((c) => ({ valor: String(c.idCancha), texto: c.nombre.split(' · ')[0]?.replace('Cancha ', 'C') ?? '' }))}
            />
            </div>
          )}
          <div className="agenda-scroll">
            <div className="agenda" style={{ ['--cols' as string]: cols, minWidth: movil ? undefined : 64 + cols * 158 }}>
              <div className="agenda-cab">
                <span />
                {visibles.map((c) => (
                  <div key={c.idCancha} className="agenda-cancha">
                    <span className="td-ico">
                      <IconoDeporte deporte={c.deporte} className="ico" />
                    </span>
                    <div className="min-w-0">
                      <b className="truncar">{c.nombre.split(' · ')[0]}</b>
                      <small>{c.activa ? c.nombre.split(' · ')[1] ?? 'Multiusos' : 'Inactiva'}</small>
                    </div>
                    <span className="libres">{libres(c)} libres</span>
                  </div>
                ))}
              </div>
              {pasadas.length > 0 && (
                <button type="button" className="bb-filtros w-full justify-between !h-[42px] border-dashed" onClick={() => setVerPasados((p) => !p)} aria-expanded={verPasados}>
                  <span className="inline-flex items-center gap-2">
                    <History className="ico ico-18" aria-hidden />
                    <b className="tnum">{fmtRango24(pasadas[0] ?? '', finDe(pasadas[pasadas.length - 1] ?? ''))}</b>
                    <span className="t-sec font-normal">Pasados</span>
                  </span>
                  <span className="inline-flex items-center gap-1">
                    {verPasados ? 'Ocultar' : 'Ver'}
                    {verPasados ? <ChevronUp className="ico ico-16" aria-hidden /> : <ChevronDown className="ico ico-16" aria-hidden />}
                  </span>
                </button>
              )}
              {mostrar.map((h) => (
                <div key={h} className="contents">
                  {esHoy && h > ahora && mostrar[mostrar.indexOf(h) - 1] !== undefined && (mostrar[mostrar.indexOf(h) - 1] ?? '') <= ahora && (
                    <div className="agenda-ahora" style={{ gridColumn: '1 / -1' }}>
                      <span>{ahora}</span>
                      <i />
                    </div>
                  )}
                  <div className="agenda-fila">
                    <div className="agenda-hora">
                      {h}
                      {h === enCurso && <small className="!text-verde font-bold">EN CURSO</small>}
                    </div>
                    {visibles.map((c) => celda(c, h))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* bloquear un turno */}
      <ModalBloquear celda={modal?.tipo === 'bloquear' ? modal.celda : null} fecha={fecha} alCerrar={() => setModal(null)} alHecho={recargar} />
      <ModalConfirmar
        abierto={modal?.tipo === 'desbloquear'}
        alCerrar={() => setModal(null)}
        titulo="Desbloquear turno"
        subtitulo={modal?.tipo === 'desbloquear' ? `${modal.celda.cancha.nombre} · ${fmtFecha(fecha, 'corta')} · ${fmtRango24(modal.celda.turno.horaInicio, modal.celda.turno.horaFin)}` : ''}
        busto="BU-rosa-agenda"
        textoConfirmar="Desbloquear"
        iconoConfirmar={LockOpen}
        alConfirmar={() => (modal?.tipo === 'desbloquear' ? desbloquear(modal.celda) : undefined)}
      >
        <div className="caja-aviso">
          <div>
            <LockOpen className="ico" aria-hidden />
            El turno vuelve a estar libre en el portal
          </div>
        </div>
      </ModalConfirmar>
      <ModalConfirmar
        abierto={modal?.tipo === 'ASISTIO' || modal?.tipo === 'NO_ASISTIO'}
        alCerrar={() => setModal(null)}
        titulo={modal?.tipo === 'ASISTIO' ? 'Marcar asistencia' : 'Marcar no asistió'}
        subtitulo={modal && 'celda' in modal && modal.celda.turno.reserva ? `${modal.celda.turno.reserva.codigoReserva} · ${modal.celda.turno.reserva.cliente}` : ''}
        textoConfirmar={modal?.tipo === 'ASISTIO' ? 'Asistió' : 'No asistió'}
        iconoConfirmar={modal?.tipo === 'ASISTIO' ? UserCheck : UserX}
        peligro={modal?.tipo === 'NO_ASISTIO'}
        alConfirmar={() => (modal && (modal.tipo === 'ASISTIO' || modal.tipo === 'NO_ASISTIO') ? asistencia(modal.celda, modal.tipo) : undefined)}
      />
      <ModalRango abierto={modal?.tipo === 'rango'} canchas={canchas} fecha={fecha} horas={horas} alCerrar={() => setModal(null)} alHecho={recargar} />
      {esAdmin && <ModalGenerar abierto={modal?.tipo === 'generar'} canchas={canchas} fecha={fecha} alCerrar={() => setModal(null)} alHecho={recargar} />}
    </>
  );
}

function ModalBloquear({ celda, fecha, alCerrar, alHecho }: { celda: Celda | null; fecha: string; alCerrar: () => void; alHecho: () => void }) {
  const toast = useToast();
  const {
    register,
    handleSubmit,
    setError,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof esquemaBloqueo>>({ resolver: zodResolver(esquemaBloqueo), mode: 'onTouched', defaultValues: { motivo: '' } });
  useEffect(() => reset({ motivo: '' }), [celda, reset]);
  const enviar = handleSubmit(async ({ motivo }) => {
    if (!celda) return;
    try {
      await turnosApi.bloquear(celda.turno.idTurno, motivo);
      toast.exito('Turno bloqueado');
      alHecho();
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['motivo']);
      if (!aplicados) toast.error(esErrorApi(e) ? e.message : 'No se pudo bloquear.');
    }
  });
  return (
    <ModalConfirmar
      abierto={celda !== null}
      alCerrar={alCerrar}
      titulo="Bloquear turno"
      subtitulo={celda ? `${celda.cancha.nombre} · ${fmtFecha(fecha, 'corta')}` : ''}
      busto="BU-rosa-agenda"
      franja={celda ? [['Turno', fmtRango24(celda.turno.horaInicio, celda.turno.horaFin)], ['Tarifa', fmtSoles(celda.turno.precio)], ['Franja', celda.turno.franja === 'NOCHE' ? 'Noche' : 'Día']] : undefined}
      textoConfirmar={isSubmitting ? 'Bloqueando…' : 'Bloquear'}
      iconoConfirmar={Lock}
      alConfirmar={enviar}
    >
      <form className="grid gap-3" onSubmit={enviar} noValidate>
        <AreaTexto label="Motivo" requerido rows={2} maxLength={120} contador={{ actual: (watch('motivo') ?? '').length, max: 120 }} placeholder="Mantenimiento del grass" error={errors.motivo?.message} {...register('motivo')} />
        <div className="caja-aviso">
          <div>
            <Lock className="ico" aria-hidden />
            No se podrá reservar en el portal
          </div>
        </div>
      </form>
    </ModalConfirmar>
  );
}

function ModalRango({ abierto, canchas, fecha, horas, alCerrar, alHecho }: { abierto: boolean; canchas: Cancha[]; fecha: string; horas: string[]; alCerrar: () => void; alHecho: () => void }) {
  const toast = useToast();
  const finales = useMemo(() => [...new Set(canchas.flatMap((c) => c.turnos.map((t) => t.horaFin)))].sort(), [canchas]);
  const {
    register,
    handleSubmit,
    setError,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof esquemaRango>>({ resolver: zodResolver(esquemaRango), mode: 'onTouched' });
  useEffect(() => {
    if (abierto) reset({ idCancha: String(canchas[0]?.idCancha ?? ''), fecha, horaDesde: horas[0] ?? '', horaHasta: finales[1] ?? finales[0] ?? '', motivo: '' });
  }, [abierto, canchas, fecha, horas, finales, reset]);
  const enviar = handleSubmit(async (d) => {
    try {
      const r = await turnosApi.bloquearRango({ ...d, idCancha: Number(d.idCancha) });
      toast.exito(`${r.bloqueados} bloqueados${r.omitidos ? ` · ${r.omitidos} omitidos (${r.horasOmitidas.join(', ')})` : ''}`);
      alHecho();
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['idCancha', 'fecha', 'horaDesde', 'horaHasta', 'motivo']);
      if (!aplicados) toast.error(esErrorApi(e) ? e.message : 'No se pudo bloquear.');
    }
  });
  return (
    <ModalConfirmar abierto={abierto} alCerrar={alCerrar} titulo="Bloquear rango" subtitulo="Varios turnos seguidos de una cancha" busto="BU-rosa-agenda" textoConfirmar={isSubmitting ? 'Bloqueando…' : 'Bloquear'} iconoConfirmar={Lock} alConfirmar={enviar}>
      <form className="grid gap-3" onSubmit={enviar} noValidate>
        <div className="cajas2">
          <Selector label="Cancha" requerido opciones={canchas.map((c) => ({ valor: String(c.idCancha), texto: c.nombre }))} error={errors.idCancha?.message} {...register('idCancha')} />
          <CampoTexto label="Fecha" requerido type="date" min={hoyLima()} error={errors.fecha?.message} {...register('fecha')} />
          <Selector label="Desde" requerido opciones={horas.map((h) => ({ valor: h, texto: h }))} error={errors.horaDesde?.message} {...register('horaDesde')} />
          <Selector label="Hasta" requerido opciones={finales.map((h) => ({ valor: h, texto: h }))} error={errors.horaHasta?.message} {...register('horaHasta')} />
        </div>
        <AreaTexto label="Motivo" requerido rows={2} maxLength={120} contador={{ actual: (watch('motivo') ?? '').length, max: 120 }} placeholder="Torneo interno" error={errors.motivo?.message} {...register('motivo')} />
        <div className="caja-aviso">
          <div>
            <Lock className="ico" aria-hidden />
            Los turnos con reserva no se tocan
          </div>
        </div>
      </form>
    </ModalConfirmar>
  );
}

function ModalGenerar({ abierto, canchas, fecha, alCerrar, alHecho }: { abierto: boolean; canchas: Cancha[]; fecha: string; alCerrar: () => void; alHecho: () => void }) {
  const toast = useToast();
  const {
    register,
    handleSubmit,
    setError,
    reset,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof esquemaGenerar>>({ resolver: zodResolver(esquemaGenerar), mode: 'onTouched' });
  useEffect(() => {
    if (abierto) reset({ desde: fecha < hoyLima() ? hoyLima() : fecha, hasta: sumarDias(hoyLima(), 14), idsCancha: canchas.map((c) => c.idCancha) });
  }, [abierto, fecha, canchas, reset]);
  const ids = watch('idsCancha') ?? [];
  const enviar = handleSubmit(async (d) => {
    try {
      const r = await turnosApi.generar({ desde: d.desde, hasta: d.hasta, idsCancha: d.idsCancha.length ? d.idsCancha : undefined });
      toast.exito(`${r.creados} turnos creados · ${r.existentes} ya existían`);
      alHecho();
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['desde', 'hasta']);
      if (!aplicados) toast.error(esErrorApi(e) ? e.message : 'No se pudo generar.');
    }
  });
  return (
    <ModalConfirmar abierto={abierto} alCerrar={alCerrar} titulo="Generar turnos" subtitulo="No duplica los que ya existen" busto="BU-rosa-agenda" textoConfirmar={isSubmitting ? 'Generando…' : 'Generar'} iconoConfirmar={CalendarCog} alConfirmar={enviar}>
      <form className="grid gap-3" onSubmit={enviar} noValidate>
        <div className="cajas2">
          <CampoTexto label="Desde" requerido type="date" error={errors.desde?.message} {...register('desde')} />
          <CampoTexto label="Hasta" requerido type="date" error={errors.hasta?.message} {...register('hasta')} />
        </div>
        <span className="etiqueta-osc">Canchas</span>
        <div className="grid gap-2">
          {canchas.map((c) => (
            <Casilla
              key={c.idCancha}
              checked={ids.includes(c.idCancha)}
              onChange={(e) => setValue('idsCancha', e.target.checked ? [...ids, c.idCancha] : ids.filter((x) => x !== c.idCancha))}
            >
              {c.nombre}
            </Casilla>
          ))}
        </div>
        <div className="caja-aviso">
          <div>
            <CalendarCog className="ico" aria-hidden />
            Usa el horario y las tarifas de Configuración
          </div>
        </div>
      </form>
    </ModalConfirmar>
  );
}
