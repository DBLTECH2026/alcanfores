import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, HandCoins, Layers, RefreshCw, TriangleAlert, X } from 'lucide-react';
import { catalogoApi, reservasApi } from '@/api/servicios';
import type { Turno } from '@/api/tipos';
import { MAX_TURNOS, useReserva } from '@/contexto/ReservaContext';
import { useToast } from '@/contexto/ToastContext';
import { useCanchas, useComplejo } from '@/hooks/useDatosPublicos';
import { useCarga } from '@/hooks/useCarga';
import { Boton } from '@/componentes/ui/Boton';
import { Alerta } from '@/componentes/ui/Estados';
import { Esqueleto, Vacio } from '@/componentes/ui/Datos';
import { GrillaTurnos, LeyendaTurnos } from '@/componentes/portal/Asistente';
import { SelectorCancha } from '@/componentes/portal/SelectorCancha';
import { TiraFechas, type MarcaDia } from '@/componentes/portal/Fechas';
import { fmtFecha, fmtHace, hoyLima, isoLima, sumarDias } from '@/utilidades/fecha';
import { fmtSoles, plural } from '@/utilidades/formato';
import { CabezaPaso, HoraLimaCorta } from './CabezaPaso';

export default function Paso1() {
  const r = useReserva();
  const toast = useToast();
  const canchas = useCanchas();
  const { datos: complejo } = useComplejo();
  const dias = complejo?.horario.diasVisibles ?? 14;
  const lista = useMemo(() => canchas.datos ?? [], [canchas.datos]);
  const idCancha = r.idCancha ?? lista[0]?.id ?? null;
  const cancha = lista.find((c) => c.id === idCancha) ?? null;
  const [refresco, setRefresco] = useState(0);
  const [actualizado, setActualizado] = useState<string | null>(null);
  const [, setTic] = useState(0);

  useEffect(() => {
    if (r.idCancha === null && lista[0]) r.elegirCancha(lista[0].id);
  }, [r, lista]);

  // «hace X min» se refresca cada 30 s
  useEffect(() => {
    const t = window.setInterval(() => setTic((n) => n + 1), 30000);
    return () => window.clearInterval(t);
  }, []);

  const hoy = hoyLima();
  const cal = useCarga(() => reservasApi.calendario(hoy, dias + 1, idCancha ?? undefined), idCancha ? `${idCancha}-${dias}-${refresco}` : null);
  // v2: feriados y días no laborables del rango + cómo trata los feriados esta cancha.
  const especiales = useCarga(() => catalogoApi.calendario(hoy, sumarDias(hoy, dias)).catch(() => []), `esp-${hoy}-${dias}`);
  const detalle = useCarga(() => catalogoApi.cancha(idCancha ?? 0).catch(() => null), idCancha ? `det-${idCancha}` : null);
  const marcas = useMemo(() => {
    const m = new Map<string, MarcaDia>();
    const feriado = detalle.datos?.feriado;
    for (const d of especiales.datos ?? []) {
      const aplica = !d.canchas?.length || (idCancha !== null && d.canchas.includes(idCancha));
      if (d.tipo === 'NO_LABORABLE' && aplica) m.set(d.fecha, { tipo: 'cerrado', nombre: d.nombre });
      if (d.tipo === 'FERIADO') m.set(d.fecha, feriado?.modo === 'CERRADA' ? { tipo: 'cerrado', nombre: d.nombre } : { tipo: 'feriado', nombre: d.nombre, recargo: feriado?.recargo });
    }
    return m;
  }, [especiales.datos, detalle.datos, idCancha]);

  // Fecha por defecto: la elegida si sigue visible; si no, el primer día con turnos libres.
  useEffect(() => {
    if (!cal.datos?.length) return;
    if (r.fecha && cal.datos.some((d) => d.fecha === r.fecha)) return;
    const primero = cal.datos.find((d) => d.libres > 0) ?? cal.datos[0];
    if (primero) r.elegirFecha(primero.fecha);
  }, [cal.datos, r]);

  const fecha = r.fecha;
  const disp = useCarga(async () => {
    const d = await reservasApi.disponibilidad(fecha ?? hoy, idCancha ?? undefined);
    setActualizado(isoLima());
    return d;
  }, idCancha && fecha ? `${idCancha}-${fecha}-${refresco}` : null);

  const turnos: Turno[] = disp.datos?.canchas.find((c) => c.idCancha === idCancha)?.turnos ?? [];
  const marcados = r.turnos.filter((t) => t.fecha === fecha && t.idCancha === idCancha).map((t) => t.idTurno);

  // Si un turno marcado se ocupó al actualizar, se quita de la selección.
  useEffect(() => {
    if (!disp.datos || !r.turnos.length) return;
    const perdidos = r.turnos.filter((m) => m.fecha === fecha && m.idCancha === idCancha && turnos.find((x) => x.idTurno === m.idTurno && x.estado !== 'DISPONIBLE'));
    if (perdidos.length) {
      perdidos.forEach((p) => r.alternarTurno(p));
      toast.advertencia('Un horario que marcaste se acaba de ocupar');
    }
  }, [disp.datos]); // eslint-disable-line react-hooks/exhaustive-deps

  const alternar = (t: Turno) => {
    if (!cancha || !fecha) return;
    if (!marcados.includes(t.idTurno) && marcados.length >= MAX_TURNOS) {
      toast.info(`Puedes marcar hasta ${MAX_TURNOS} turnos`);
      return;
    }
    r.alternarTurno({ idTurno: t.idTurno, idCancha: cancha.id, nombreCancha: cancha.nombre, deporte: cancha.deporte, fecha, horaInicio: t.horaInicio, horaFin: t.horaFin, precio: t.precio, franja: t.franja, feriado: t.feriado });
  };

  const actualizar = () => setRefresco((n) => n + 1);
  const siguienteConLibres = cal.datos?.find((d) => d.fecha > (fecha ?? '') && d.libres > 0);
  const marcaHoy = fecha ? marcas.get(fecha) : undefined;
  const sel = r.turno && r.turno.fecha === fecha && r.turno.idCancha === idCancha ? r.turno : null;
  const hayLibres = turnos.some((t) => t.estado === 'DISPONIBLE');

  return (
    <div className="grid gap-5">
      <CabezaPaso
        titulo="Elige tu horario"
        ilustracion="IL-02-paso1-horario"
        apoyo={
          <>
            <HoraLimaCorta />
            <span className="inline-flex items-center gap-1.5">
              <HandCoins className="ico ico-14" aria-hidden />
              {`Adelanto ${complejo?.reglas.adelantoPct ?? 50} %`}
            </span>
            <a className="enlace" href="/terminos" target="_blank" rel="noopener">
              Ver condiciones
            </a>
          </>
        }
      />

      {r.turnoPerdido && (
        <Alerta tipo="advertencia" icono={TriangleAlert} titulo="Alguien se adelantó" alCerrar={() => r.elegirTurno(null)}>
          {`${r.turnoPerdido.horarioTexto} del ${fmtFecha(r.turnoPerdido.fecha)} ya no está libre. Tus datos siguen guardados: elige otro horario.`}
        </Alerta>
      )}

      {canchas.cargando ? (
        <Esqueleto alto={72} radio={14} />
      ) : canchas.error ? (
        <Vacio ilustracion="IL-19-sin-conexion" titulo="Sin conexión con el servidor" compacto acciones={<Boton variante="secundario" icono={RefreshCw} onClick={canchas.recargar}>Reintentar</Boton>} />
      ) : lista.length === 0 ? (
        <Vacio ilustracion="IL-25-sin-canchas" titulo="Sin canchas por ahora" compacto />
      ) : (
        <SelectorCancha canchas={lista} activa={idCancha} alElegir={r.elegirCancha} />
      )}

      {idCancha && (
        <section className="grid gap-3 min-w-0" aria-labelledby="h-fecha">
          <h2 id="h-fecha" className="inline-flex items-center gap-2 text-[14px] font-semibold">
            <CalendarDays className="ico ico-16 text-verde" aria-hidden />
            {fecha ? fmtFecha(fecha, 'larga') : 'Fecha'}
            {marcaHoy && <span className="t-sec font-normal">{`· ${marcaHoy.nombre}`}</span>}
          </h2>
          {cal.cargando && !cal.datos ? (
            <div className="flex gap-2 overflow-hidden">
              {Array.from({ length: 8 }, (_, i) => (
                <Esqueleto key={i} ancho={60} alto={76} radio={12} className="shrink-0" />
              ))}
            </div>
          ) : cal.datos ? (
            <TiraFechas dias={cal.datos} seleccionada={fecha} alElegir={r.elegirFecha} marcas={marcas} />
          ) : cal.error ? (
            <Vacio ilustracion="IL-19-sin-conexion" titulo="No pudimos cargar las fechas" compacto acciones={<Boton variante="secundario" icono={RefreshCw} onClick={actualizar}>Reintentar</Boton>} />
          ) : null}
        </section>
      )}

      {idCancha && fecha && (
        <section className="grid gap-4" aria-labelledby="h-turnos">
          <h2 id="h-turnos" className="sr-only">
            Horarios disponibles
          </h2>
          {marcaHoy?.tipo === 'feriado' && (
            <Alerta tipo="info" titulo={`Feriado: ${marcaHoy.nombre}`}>
              {marcaHoy.recargo ? `Cada turno lleva un adicional de ${fmtSoles(marcaHoy.recargo, { decimales: 0 })} (ya incluido en el precio).` : 'La cancha abre con su precio normal.'}
            </Alerta>
          )}
          <LeyendaTurnos />
          {/* Una sola línea: la pista de varios turnos y cuándo se actualizó (con el botón de actualizar). */}
          <div className="turnos-nota">
            {hayLibres && (
              <span className="turnos-nota-txt">
                <Layers className="ico ico-16 text-verde" aria-hidden />
                Marca uno o varios turnos
              </span>
            )}
            <span className="turnos-nota-estado" role="status">
              {disp.cargando ? 'Cargando…' : actualizado ? fmtHace(actualizado) : ''}
              <Boton variante="fantasma" icono={RefreshCw} etiqueta="Actualizar disponibilidad" cargando={disp.cargando} onClick={actualizar} />
            </span>
          </div>
          {disp.cargando && !disp.datos ? (
            <div className="grilla-turnos" aria-busy="true" aria-label="Cargando horarios…">
              {Array.from({ length: 12 }, (_, i) => (
                <Esqueleto key={i} alto={64} radio={12} />
              ))}
            </div>
          ) : disp.error ? (
            <div className="card" role="alert">
              <Vacio ilustracion="IL-19-sin-conexion" titulo="No pudimos cargar los horarios" compacto acciones={<Boton variante="secundario" icono={RefreshCw} onClick={actualizar}>Reintentar</Boton>} />
            </div>
          ) : turnos.length === 0 ? (
            <div className="card">
              <Vacio ilustracion="IL-26-sin-turnos" titulo={marcaHoy?.tipo === 'cerrado' ? `Cerrado: ${marcaHoy.nombre}` : 'Sin horarios ese día'} texto={marcaHoy ? undefined : 'La cancha no abre ese día.'} compacto />
            </div>
          ) : !hayLibres ? (
            <div className="grid gap-4">
              <div className="card">
                <Vacio
                  ilustracion="IL-08-sin-horarios"
                  titulo="Sin horarios libres"
                  compacto
                  acciones={
                    siguienteConLibres ? (
                      <Boton variante="secundario" icono={CalendarDays} onClick={() => r.elegirFecha(siguienteConLibres.fecha)}>
                        {`Ver ${fmtFecha(siguienteConLibres.fecha)}`}
                      </Boton>
                    ) : undefined
                  }
                />
              </div>
              <GrillaTurnos turnos={turnos} seleccionados={[]} alElegir={alternar} />
            </div>
          ) : (
            <>
              <GrillaTurnos turnos={turnos} seleccionados={marcados} alElegir={alternar} />
              {sel && sel.partes.length > 1 && (
                <div className="seleccion-turnos" role="status">
                  <b className="tnum">{`${plural(sel.partes.length, 'turno', 'turnos')} · ${sel.horarioTexto}`}</b>
                  <span className="tnum">{`Total ${fmtSoles(sel.precio)}`}</span>
                  <Boton variante="fantasma" tamano="sm" icono={X} onClick={() => r.elegirTurno(null)}>
                    Quitar
                  </Boton>
                </div>
              )}
            </>
          )}
        </section>
      )}
    </div>
  );
}
