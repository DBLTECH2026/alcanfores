import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowRight,
  Banknote,
  CalendarCheck,
  CalendarClock,
  CalendarDays,
  Check,
  Clock,
  Hourglass,
  Info,
  Lock,
  MapPin,
  Moon,
  RefreshCw,
  Store,
  Sun,
  Wallet,
  Wrench,
  type LucideIcon,
} from 'lucide-react';
import { catalogoApi, reservasApi } from '@/api/servicios';
import { reservasAdminApi } from '@/api/admin';
import type { ReservaAdmin } from '@/api/admin/tipos';
import { esErrorApi } from '@/api/cliente';
import type { ReservaDetalle, Turno } from '@/api/tipos';
import { MAX_TURNOS } from '@/contexto/ReservaContext';
import { useToast } from '@/contexto/ToastContext';
import { useCanchas, useComplejo } from '@/hooks/useDatosPublicos';
import { useCarga } from '@/hooks/useCarga';
import { Boton } from '@/componentes/ui/Boton';
import { AreaTexto } from '@/componentes/ui/Campo';
import { Esqueleto, Vacio } from '@/componentes/ui/Datos';
import { Alerta, Insignia } from '@/componentes/ui/Estados';
import { IconoDeporte } from '@/componentes/ui/Marca';
import { Modal } from '@/componentes/ui/Modal';
import { LeyendaTurnos, calcularAdelanto } from '@/componentes/portal/Asistente';
import { TiraFechas, type MarcaDia } from '@/componentes/portal/Fechas';
import { horarioDeReserva } from '@/componentes/portal/EstadoReserva';
import { fmtFecha, fmtHora12, fmtHora24, hoyLima, sumarDias, textoHorario } from '@/utilidades/fecha';
import { desgloseIgv, fmtSoles } from '@/utilidades/formato';
import { cx } from '@/utilidades/cx';
import './reprogramar.css';

/**
 * Cambiar el horario de una reserva (portal y panel) en UN solo paso: cancha, fecha y turnos nuevos, con el
 * «Ahora → Nuevo» y la cuenta (nuevo total con IGV, ya pagado y saldo). Lo pagado se conserva.
 * - cliente: POST /api/reservas/{codigo}/reprogramar {dni, idsTurno}; una sola vez y hasta 2 h antes (lo decide el back
 *   con `puedeReprogramar`).
 * - panel: POST /api/admin/reservas/{codigo}/reprogramar {idsTurno, motivo}; motivo obligatorio (nota interna).
 * Los errores 409/422 se muestran dentro del modal, sin cerrarlo. En celular es la hoja del Modal.
 */
type PropsCliente = { modo: 'cliente'; reserva: ReservaDetalle | null; dni: string; alCerrar: () => void; alReprogramar: (r: ReservaDetalle) => void };
type PropsPanel = { modo: 'panel'; reserva: ReservaAdmin | null; alCerrar: () => void; alReprogramar: (r: ReservaAdmin) => void };
export type PropsReprogramar = PropsCliente | PropsPanel;

/** Lo que el modal usa de la reserva (igual en el detalle del cliente y en el del panel). */
type ReservaBase = Pick<ReservaDetalle, 'codigoReserva' | 'cancha' | 'fecha' | 'horaInicio' | 'horaFin' | 'turnos' | 'horarioTexto' | 'montoTotal' | 'montoPagado'>;

const MOTIVO_MIN = 5;
const MOTIVO_MAX = 200;
const corto = (nombre: string) => nombre.split(' · ')[0] || nombre;
const redondear = (n: number) => Math.round(n * 100) / 100;

export function ModalReprogramar(p: PropsReprogramar) {
  if (!p.reserva) return null;
  // La key reinicia la selección cada vez que se abre para otra reserva.
  return <Contenido key={p.reserva.codigoReserva} p={p} reserva={p.reserva} />;
}

function Contenido({ p, reserva }: { p: PropsReprogramar; reserva: ReservaBase }) {
  const toast = useToast();
  const panel = p.modo === 'panel';
  const hora = panel ? fmtHora24 : fmtHora12;
  const canchas = useCanchas();
  const { datos: complejo } = useComplejo();
  const dias = complejo?.horario.diasVisibles ?? 14;
  const pct = complejo?.reglas.adelantoPct ?? 50;
  const lista = useMemo(() => canchas.datos ?? [], [canchas.datos]);
  const actuales = useMemo(() => new Set((reserva.turnos ?? []).map((t) => t.idTurno)), [reserva.turnos]);

  const [idCancha, setIdCancha] = useState(reserva.cancha.id);
  const [fecha, setFecha] = useState(reserva.fecha);
  const [sel, setSel] = useState<Turno[]>([]);
  const [motivo, setMotivo] = useState('');
  const [errorMotivo, setErrorMotivo] = useState<string | null>(null);
  const [error, setError] = useState<{ titulo?: string; texto: string } | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [refresco, setRefresco] = useState(0);
  const refError = useRef<HTMLDivElement>(null);
  const refMotivo = useRef<HTMLTextAreaElement>(null);

  // Si la cancha de la reserva ya no está activa, se parte de la primera de la lista.
  useEffect(() => {
    if (lista.length && !lista.some((c) => c.id === idCancha) && lista[0]) setIdCancha(lista[0].id);
  }, [lista, idCancha]);

  const hoy = hoyLima();
  const cal = useCarga(() => reservasApi.calendario(hoy, dias + 1, idCancha), `rp-cal-${idCancha}-${dias}-${refresco}`);
  const especiales = useCarga(() => catalogoApi.calendario(hoy, sumarDias(hoy, dias)).catch(() => []), `rp-esp-${hoy}-${dias}`);
  const ficha = useCarga(() => catalogoApi.cancha(idCancha).catch(() => null), `rp-det-${idCancha}`);
  const marcas = useMemo(() => {
    const m = new Map<string, MarcaDia>();
    const feriado = ficha.datos?.feriado;
    for (const d of especiales.datos ?? []) {
      const aplica = !d.canchas?.length || d.canchas.includes(idCancha);
      if (d.tipo === 'NO_LABORABLE' && aplica) m.set(d.fecha, { tipo: 'cerrado', nombre: d.nombre });
      if (d.tipo === 'FERIADO') m.set(d.fecha, feriado?.modo === 'CERRADA' ? { tipo: 'cerrado', nombre: d.nombre } : { tipo: 'feriado', nombre: d.nombre, recargo: feriado?.recargo });
    }
    return m;
  }, [especiales.datos, ficha.datos, idCancha]);

  const disp = useCarga(() => reservasApi.disponibilidad(fecha, idCancha), `rp-disp-${idCancha}-${fecha}-${refresco}`);
  // Los turnos que ya ocupa ESTA reserva cuentan como libres para ella (correr una hora un partido de 2 turnos).
  const turnos = useMemo<Turno[]>(() => {
    if (disp.datos?.fecha !== fecha) return [];
    const propios = disp.datos.canchas.find((c) => c.idCancha === idCancha)?.turnos ?? [];
    return propios.map((t) => (actuales.has(t.idTurno) && t.estado === 'OCUPADO' ? { ...t, estado: 'DISPONIBLE' as const } : t));
  }, [disp.datos, fecha, idCancha, actuales]);
  const hayLibres = turnos.some((t) => t.estado === 'DISPONIBLE');

  // Si al actualizar un turno elegido ya no está libre, se quita de la selección.
  useEffect(() => {
    if (!sel.length || !turnos.length) return;
    const libres = new Set(turnos.filter((t) => t.estado === 'DISPONIBLE').map((t) => t.idTurno));
    const quedan = sel.filter((t) => libres.has(t.idTurno));
    if (quedan.length !== sel.length) setSel(quedan);
  }, [turnos, sel]);

  useEffect(() => {
    if (error) refError.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }, [error]);

  const elegirCancha = (id: number) => {
    if (id === idCancha) return;
    setIdCancha(id);
    setSel([]);
    setError(null);
  };
  const elegirFecha = (f: string) => {
    if (f === fecha) return;
    setFecha(f);
    setSel([]);
    setError(null);
  };
  const alternar = (t: Turno) => {
    setError(null);
    if (sel.some((s) => s.idTurno === t.idTurno)) {
      setSel(sel.filter((s) => s.idTurno !== t.idTurno));
      return;
    }
    if (sel.length >= MAX_TURNOS) {
      toast.info(`Puedes marcar hasta ${MAX_TURNOS} turnos`);
      return;
    }
    setSel([...sel, t].sort((a, b) => a.horaInicio.localeCompare(b.horaInicio)));
  };

  // ------------------------------ cuenta ------------------------------
  const cancha = lista.find((c) => c.id === idCancha) ?? null;
  const ids = sel.map((t) => t.idTurno);
  const hayNuevo = sel.length > 0;
  const mismo = hayNuevo && sel.length === actuales.size && ids.every((i) => actuales.has(i));
  const nuevoTotal = redondear(sel.reduce((s, t) => s + Number(t.precio || 0), 0));
  const enRevision = p.modo === 'cliente' ? Number(p.reserva?.montoEnRevision ?? 0) : 0;
  const pagado = Number(reserva.montoPagado || 0);
  const comprometido = redondear(pagado + enRevision);
  const total = hayNuevo ? nuevoTotal : Number(reserva.montoTotal || 0);
  const saldo = Math.max(0, redondear(total - comprometido));
  const diferencia = redondear(nuevoTotal - Number(reserva.montoTotal || 0));
  const menor = hayNuevo && !mismo && nuevoTotal + 0.001 < comprometido;
  const adelantoNuevo = calcularAdelanto(nuevoTotal, pct);
  // Solo el cliente: si ya pagó algo, debe cubrir el adelanto del nuevo horario (el panel sí puede, queda saldo a cobrar).
  const adelantoCorto = !panel && hayNuevo && !mismo && !menor && comprometido > 0 && comprometido + 0.001 < adelantoNuevo;
  const listo = hayNuevo && !mismo && !menor && !adelantoCorto;

  const guardar = async () => {
    if (!listo || enviando) return;
    const m = motivo.trim();
    if (panel && (m.length < MOTIVO_MIN || m.length > MOTIVO_MAX)) {
      setErrorMotivo(`Cuéntanos el motivo del cambio (${MOTIVO_MIN} a ${MOTIVO_MAX} caracteres).`);
      refMotivo.current?.focus();
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      if (p.modo === 'cliente') {
        const r = await reservasApi.reprogramar(reserva.codigoReserva, p.dni, ids);
        toast.exito('Listo, cambiaste el horario');
        p.alReprogramar(r);
      } else {
        const r = await reservasAdminApi.reprogramar(reserva.codigoReserva, ids, m);
        toast.exito('Reserva reprogramada');
        p.alReprogramar(r);
      }
    } catch (e) {
      setEnviando(false);
      if (!esErrorApi(e)) {
        setError({ texto: 'No se pudo cambiar el horario. Intenta de nuevo.' });
        return;
      }
      const campo = e.errores.find((x) => x.campo === 'motivo');
      if (campo && panel) {
        setErrorMotivo(campo.mensaje);
        refMotivo.current?.focus();
        return;
      }
      // 409: alguien se adelantó; se actualiza la disponibilidad y el turno perdido sale de la selección.
      if (e.status === 409) setRefresco((n) => n + 1);
      setError({ titulo: e.status === 409 ? 'Alguien se adelantó' : undefined, texto: e.message });
    }
  };

  const fechaNueva = hayNuevo ? fmtFecha(fecha) : '';
  const horarioNuevo = hayNuevo ? textoHorario(sel, panel) : '';
  const marcaHoy = marcas.get(fecha);
  const siguienteConLibres = cal.datos?.find((d) => d.fecha > fecha && d.libres > 0);
  const nombreCliente = p.modo === 'panel' && p.reserva ? `${p.reserva.cliente.nombres.split(' ')[0]} ${p.reserva.cliente.apellidos.split(' ')[0]}` : null;

  return (
    <Modal
      abierto
      alCerrar={p.alCerrar}
      titulo={panel ? 'Reprogramar reserva' : 'Cambiar horario'}
      subtitulo={nombreCliente ? `${reserva.codigoReserva} · ${nombreCliente}` : reserva.codigoReserva}
      icono={CalendarClock}
      variante="hoja"
      tamano="xl"
      estatico
      className="modal-reprogramar"
      acciones={
        <>
          <Boton variante="secundario" onClick={p.alCerrar}>
            Cancelar
          </Boton>
          <Boton icono={CalendarCheck} disabled={!listo} cargando={enviando} textoCargando="Guardando…" onClick={() => void guardar()}>
            {panel ? 'Reprogramar' : 'Cambiar horario'}
          </Boton>
        </>
      }
    >
      <div className="rp">
        <div className="rp-elegir">
          {lista.length > 1 && (
            <section className="rp-bloque" aria-labelledby="rp-h-cancha">
              <h3 id="rp-h-cancha" className="rp-tit">
                <MapPin className="ico" aria-hidden />
                Cancha
              </h3>
              <div className="rp-canchas" role="radiogroup" aria-labelledby="rp-h-cancha">
                {lista.map((c) => {
                  const activa = c.id === idCancha;
                  return (
                    <button key={c.id} type="button" role="radio" aria-checked={activa} className={cx('rp-cancha', activa && 'activa')} onClick={() => elegirCancha(c.id)}>
                      <IconoDeporte deporte={c.deporte} />
                      <span className="rp-cancha-txt">
                        <b>{corto(c.nombre)}</b>
                        <small>{c.deporteTexto}</small>
                      </span>
                    </button>
                  );
                })}
              </div>
            </section>
          )}

          <section className="rp-bloque" aria-labelledby="rp-h-fecha">
            <h3 id="rp-h-fecha" className="rp-tit">
              <CalendarDays className="ico" aria-hidden />
              {fmtFecha(fecha, 'larga')}
              {marcaHoy && <small>{`· ${marcaHoy.nombre}`}</small>}
            </h3>
            {cal.datos ? (
              <TiraFechas dias={cal.datos} seleccionada={fecha} alElegir={elegirFecha} marcas={marcas} />
            ) : cal.error ? (
              <Alerta tipo="error" acciones={<Boton variante="secundario" tamano="sm" icono={RefreshCw} onClick={cal.recargar}>Reintentar</Boton>}>
                No pudimos cargar las fechas.
              </Alerta>
            ) : (
              <div className="flex gap-2 overflow-hidden">
                {Array.from({ length: 7 }, (_, i) => (
                  <Esqueleto key={i} ancho={60} alto={76} radio={12} className="shrink-0" />
                ))}
              </div>
            )}
          </section>

          <section className="rp-bloque" aria-labelledby="rp-h-turnos">
            <h3 id="rp-h-turnos" className="rp-tit">
              <Clock className="ico" aria-hidden />
              Nuevo horario
              {hayLibres && <small>· marca uno o varios turnos</small>}
            </h3>
            {marcaHoy?.tipo === 'feriado' && marcaHoy.recargo ? (
              <p className="rp-nota">
                <Info className="ico" aria-hidden />
                {`Feriado: cada turno lleva ${fmtSoles(marcaHoy.recargo, { decimales: 0 })} más (ya incluido).`}
              </p>
            ) : null}
            <LeyendaTurnos
              extra={
                actuales.size > 0 ? (
                  <span className="leyenda-item">
                    <span className="leyenda-mue rp-mue" aria-hidden />
                    {panel ? 'Horario actual' : 'Tu horario actual'}
                  </span>
                ) : undefined
              }
            />
            {disp.cargando && !turnos.length ? (
              <div className="grilla-turnos" aria-busy="true" aria-label="Cargando horarios…">
                {Array.from({ length: 10 }, (_, i) => (
                  <Esqueleto key={i} alto={64} radio={12} />
                ))}
              </div>
            ) : disp.error ? (
              <Vacio ilustracion="IL-19-sin-conexion" titulo="No pudimos cargar los horarios" compacto acciones={<Boton variante="secundario" icono={RefreshCw} onClick={disp.recargar}>Reintentar</Boton>} />
            ) : turnos.length === 0 ? (
              <Vacio ilustracion="IL-26-sin-turnos" titulo={marcaHoy?.tipo === 'cerrado' ? `Cerrado: ${marcaHoy.nombre}` : 'Sin horarios ese día'} compacto />
            ) : (
              <>
                {!hayLibres && (
                  <Alerta
                    tipo="info"
                    titulo="Sin horarios libres ese día"
                    acciones={
                      siguienteConLibres ? (
                        <Boton variante="secundario" tamano="sm" icono={CalendarDays} onClick={() => elegirFecha(siguienteConLibres.fecha)}>
                          {`Ver ${fmtFecha(siguienteConLibres.fecha)}`}
                        </Boton>
                      ) : undefined
                    }
                  />
                )}
                <GrillaReprogramar turnos={turnos} seleccionados={ids} actuales={actuales} hora={hora} etiquetaActual={panel ? 'Actual' : 'Tu horario'} alElegir={alternar} />
              </>
            )}
          </section>
        </div>

        <div className="rp-lateral">
          <div className="rp-cambio" aria-live="polite">
            <div className="rp-lado">
              <span className="rp-k">
                <CalendarClock className="ico" aria-hidden />
                Ahora
              </span>
              <b>{corto(reserva.cancha.nombre)}</b>
              <span className="tnum">{fmtFecha(reserva.fecha)}</span>
              <span className="tnum">{horarioDeReserva(reserva, panel)}</span>
              <span className="rp-total tnum">{fmtSoles(reserva.montoTotal)}</span>
            </div>
            <span className="rp-flecha" aria-hidden>
              <ArrowRight className="ico" />
            </span>
            {hayNuevo ? (
              <div className="rp-lado nuevo">
                <span className="rp-k">
                  <CalendarCheck className="ico" aria-hidden />
                  Nuevo
                </span>
                <b>{cancha ? corto(cancha.nombre) : '—'}</b>
                <span className="tnum">{fechaNueva}</span>
                <span className="tnum">{horarioNuevo}</span>
                <span className="rp-total tnum">{fmtSoles(nuevoTotal)}</span>
              </div>
            ) : (
              <div className="rp-lado vacio">
                <span className="rp-k">Nuevo</span>
                <span>Elige cancha, fecha y turnos</span>
              </div>
            )}
          </div>

          <div className="rp-cuenta">
            <div className="fila-monto">
              <span className="inline-flex items-center gap-2">
                <Banknote className="ico ico-16" aria-hidden />
                {hayNuevo ? 'Nuevo total' : 'Total'}
              </span>
              <span className="rp-total-fila">
                {hayNuevo && !mismo && (
                  <Insignia tono={diferencia > 0 ? 'amarillo' : diferencia < 0 ? 'gris' : 'menta'} tamano="sm">
                    {diferencia === 0 ? 'Mismo precio' : `${diferencia > 0 ? '+' : '−'}${fmtSoles(Math.abs(diferencia))}`}
                  </Insignia>
                )}
                <b>{fmtSoles(total)}</b>
              </span>
            </div>
            {total > 0 && (
              <div className="fila-monto fila-igv">
                <span>Incluye IGV (18 %)</span>
                <span>{fmtSoles(desgloseIgv(total).igv)}</span>
              </div>
            )}
            <div className="fila-monto">
              <span className="inline-flex items-center gap-2">
                <Wallet className="ico ico-16" aria-hidden />
                Ya pagado
              </span>
              <b>{fmtSoles(pagado)}</b>
            </div>
            {enRevision > 0 && (
              <div className="fila-monto">
                <span className="inline-flex items-center gap-2">
                  <Hourglass className="ico ico-16" aria-hidden />
                  En revisión
                </span>
                <b>{fmtSoles(enRevision)}</b>
              </div>
            )}
            <div className="fila-monto destacada">
              <span className="inline-flex items-center gap-2">
                <Store className="ico ico-16" aria-hidden />
                {panel ? 'Saldo por cobrar' : 'Saldo al llegar'}
              </span>
              <b>{fmtSoles(saldo)}</b>
            </div>
          </div>

          {mismo && <Alerta tipo="info">Es el horario que ya tienes. Elige otro día, hora o cancha.</Alerta>}
          {menor && (
            <Alerta tipo="advertencia" titulo="Cuesta menos de lo pagado">
              {`El nuevo horario cuesta ${fmtSoles(nuevoTotal)} y ya ${panel ? 'pagó' : 'pagaste'} ${fmtSoles(comprometido)}. ${panel ? 'Elige otro horario.' : 'Elige otro horario o acércate al complejo.'}`}
            </Alerta>
          )}
          {adelantoCorto && (
            <Alerta tipo="advertencia" titulo="Falta adelanto">
              {`Este horario pide ${fmtSoles(adelantoNuevo)} de adelanto y pagaste ${fmtSoles(comprometido)}. Elige otro horario o escríbenos.`}
            </Alerta>
          )}

          {panel ? (
            <AreaTexto
              ref={refMotivo}
              label="Motivo del cambio"
              requerido
              rows={2}
              maxLength={MOTIVO_MAX}
              placeholder="Ej.: el cliente pidió pasar al sábado"
              ayuda="Nota interna: el cliente no la ve."
              contador={{ actual: motivo.length, max: MOTIVO_MAX }}
              error={errorMotivo ?? undefined}
              value={motivo}
              onChange={(e) => {
                setMotivo(e.target.value);
                if (errorMotivo) setErrorMotivo(null);
              }}
            />
          ) : (
            <p className="rp-nota">
              <Info className="ico" aria-hidden />
              Puedes cambiar el horario una sola vez. Lo pagado se conserva.
            </p>
          )}

          {error && (
            <div ref={refError}>
              <Alerta tipo="error" titulo={error.titulo}>
                {error.texto}
              </Alerta>
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}

const ETQ: Record<Turno['estado'], string> = { DISPONIBLE: 'disponible', OCUPADO: 'ocupado', BLOQUEADO: 'no disponible', PASADO: 'ya pasó' };

/**
 * Turnos en dos franjas (Día · Noche) con las piezas del Paso 1 (.grilla-turnos, .chip-turno). Los turnos actuales de la
 * reserva llevan la cinta «Tu horario» / «Actual» y se pueden volver a elegir. En el panel la hora va en 24 h.
 */
function GrillaReprogramar({
  turnos,
  seleccionados,
  actuales,
  hora,
  etiquetaActual,
  alElegir,
}: {
  turnos: Turno[];
  seleccionados: number[];
  actuales: Set<number>;
  hora: (h: string) => string;
  etiquetaActual: string;
  alElegir: (t: Turno) => void;
}) {
  const franjas: Array<{ clave: Turno['franja']; texto: string; icono: LucideIcon }> = [
    { clave: 'DIA', texto: 'Día', icono: Sun },
    { clave: 'NOCHE', texto: 'Noche', icono: Moon },
  ];
  return (
    <div className="grid gap-4" role="group" aria-label="Turnos (puedes marcar varios)">
      {franjas.map(({ clave, texto, icono: Icono }) => {
        const lista = turnos.filter((t) => t.franja === clave);
        if (!lista.length) return null;
        return (
          <section key={clave} className="grid gap-2" aria-label={`Turnos de ${texto.toLowerCase()}`}>
            <h4 className="rp-franja">
              <Icono className={cx('ico', clave === 'DIA' ? 'text-amarillo-ocre' : 'text-noche')} aria-hidden />
              {texto}
            </h4>
            <div className="grilla-turnos">
              {lista.map((t) => {
                const sel = seleccionados.includes(t.idTurno);
                const actual = actuales.has(t.idTurno);
                const noSel = t.estado !== 'DISPONIBLE';
                const clase = t.estado === 'OCUPADO' ? 'ct-ocupado' : t.estado === 'BLOQUEADO' ? 'ct-mantenimiento' : t.estado === 'PASADO' ? 'ct-pasado' : sel ? 'ct-seleccionado' : 'ct-disponible';
                const sub = t.estado === 'OCUPADO' ? 'Ocupado' : t.estado === 'BLOQUEADO' ? 'No disponible' : t.estado === 'PASADO' ? 'Pasado' : `${fmtSoles(t.precio, { decimales: 0 })}${t.feriado ? ' · feriado' : ''}`;
                const Marca = sel && !noSel ? Check : t.estado === 'OCUPADO' ? Lock : t.estado === 'BLOQUEADO' ? Wrench : null;
                return (
                  <button
                    key={t.idTurno}
                    type="button"
                    role="checkbox"
                    aria-checked={sel}
                    aria-disabled={noSel || undefined}
                    aria-label={`${hora(t.horaInicio)}, ${fmtSoles(t.precio)}${actual ? `, ${etiquetaActual.toLowerCase()}` : ''}, ${sel ? 'seleccionado' : ETQ[t.estado]}`}
                    className={cx('chip-turno', clase, actual && 'rp-actual')}
                    onClick={() => !noSel && alElegir(t)}
                  >
                    {actual && (
                      <span className="rp-cinta" aria-hidden>
                        {etiquetaActual}
                      </span>
                    )}
                    <span className="ct-hora">{hora(t.horaInicio)}</span>
                    <span className="ct-sub">{sub}</span>
                    {Marca && (
                      <span className="ct-marca" aria-hidden>
                        <Marca className="ico" />
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </section>
        );
      })}
    </div>
  );
}
