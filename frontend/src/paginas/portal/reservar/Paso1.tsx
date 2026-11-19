import { useEffect, useMemo, useRef, useState } from 'react';
import { CalendarDays, HandCoins, RefreshCw, TriangleAlert } from 'lucide-react';
import { reservasApi } from '@/api/servicios';
import type { Turno } from '@/api/tipos';
import { useReserva } from '@/contexto/ReservaContext';
import { useToast } from '@/contexto/ToastContext';
import { useCanchas, useComplejo } from '@/hooks/useDatosPublicos';
import { useCarga } from '@/hooks/useCarga';
import { Boton } from '@/componentes/ui/Boton';
import { Alerta } from '@/componentes/ui/Estados';
import { Esqueleto, Vacio } from '@/componentes/ui/Datos';
import { GrillaTurnos, LeyendaTurnos, SelectorCanchas, TiraFechas } from '@/componentes/portal/Asistente';
import { fmtFecha, fmtHace, fmtHora12, hoyLima, isoLima } from '@/utilidades/fecha';
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
  const grilla = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (r.idCancha === null && lista[0]) r.elegirCancha(lista[0].id);
  }, [r, lista]);

  // «hace X min» se refresca cada 30 s
  useEffect(() => {
    const t = window.setInterval(() => setTic((n) => n + 1), 30000);
    return () => window.clearInterval(t);
  }, []);

  const cal = useCarga(() => reservasApi.calendario(hoyLima(), dias, idCancha ?? undefined), idCancha ? `${idCancha}-${refresco}` : null);

  // Fecha por defecto: la elegida si sigue visible; si no, el primer día con turnos libres.
  useEffect(() => {
    if (!cal.datos?.length) return;
    if (r.fecha && cal.datos.some((d) => d.fecha === r.fecha)) return;
    const primero = cal.datos.find((d) => d.libres > 0) ?? cal.datos[0];
    if (primero) r.elegirFecha(primero.fecha);
  }, [cal.datos, r]);

  const fecha = r.fecha;
  const disp = useCarga(async () => {
    const d = await reservasApi.disponibilidad(fecha ?? hoyLima(), idCancha ?? undefined);
    setActualizado(isoLima());
    return d;
  }, idCancha && fecha ? `${idCancha}-${fecha}-${refresco}` : null);

  const turnos: Turno[] = disp.datos?.canchas.find((c) => c.idCancha === idCancha)?.turnos ?? [];

  // Si el turno elegido se ocupó al actualizar, se quita la selección.
  useEffect(() => {
    if (!r.turno || !disp.datos || r.turno.fecha !== fecha || r.turno.idCancha !== idCancha) return;
    const t = turnos.find((x) => x.idTurno === r.turno?.idTurno);
    if (t && t.estado !== 'DISPONIBLE') {
      r.elegirTurno(null);
      toast.advertencia('Ese horario se acaba de ocupar');
    }
  }, [disp.datos]); // eslint-disable-line react-hooks/exhaustive-deps

  const elegir = (t: Turno) => {
    if (!cancha || !fecha) return;
    r.elegirTurno({ idTurno: t.idTurno, idCancha: cancha.id, nombreCancha: cancha.nombre, deporte: cancha.deporte, fecha, horaInicio: t.horaInicio, horaFin: t.horaFin, precio: t.precio, franja: t.franja });
  };

  const actualizar = () => setRefresco((n) => n + 1);
  const siguienteConLibres = cal.datos?.find((d) => d.fecha > (fecha ?? '') && d.libres > 0);

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
          {`Las ${fmtHora12(r.turnoPerdido.horaInicio)} del ${fmtFecha(r.turnoPerdido.fecha)} ya no están libres. Tus datos siguen guardados: elige otro horario.`}
        </Alerta>
      )}

      {canchas.cargando ? (
        <div className="flex gap-2">
          {[1, 2, 3].map((n) => (
            <Esqueleto key={n} ancho={150} alto={52} radio={14} />
          ))}
        </div>
      ) : canchas.error ? (
        <Vacio ilustracion="IL-19-sin-conexion" titulo="Sin conexión con el servidor" compacto acciones={<Boton variante="secundario" icono={RefreshCw} onClick={canchas.recargar}>Reintentar</Boton>} />
      ) : lista.length === 0 ? (
        <Vacio ilustracion="IL-25-sin-canchas" titulo="Sin canchas por ahora" compacto />
      ) : (
        <SelectorCanchas canchas={lista} activa={idCancha} alElegir={r.elegirCancha} />
      )}

      {idCancha && (
        <section className="grid gap-3" aria-labelledby="h-fecha">
          <h2 id="h-fecha" className="inline-flex items-center gap-2 text-[14px] font-semibold">
            <CalendarDays className="ico ico-16 text-verde" aria-hidden />
            {fecha ? fmtFecha(fecha, 'larga') : 'Fecha'}
          </h2>
          {cal.cargando && !cal.datos ? (
            <div className="flex gap-2 overflow-hidden">
              {Array.from({ length: 8 }, (_, i) => (
                <Esqueleto key={i} ancho={60} alto={76} radio={12} className="shrink-0" />
              ))}
            </div>
          ) : cal.datos ? (
            <TiraFechas dias={cal.datos} seleccionada={fecha} alElegir={r.elegirFecha} />
          ) : null}
        </section>
      )}

      {idCancha && fecha && (
        <section className="grid gap-4" aria-labelledby="h-turnos" ref={grilla}>
          <h2 id="h-turnos" className="sr-only">
            Horarios disponibles
          </h2>
          <LeyendaTurnos
            extra={
              <span className="ml-auto inline-flex items-center gap-1.5 t-mini" role="status">
                {disp.cargando ? 'Cargando…' : actualizado ? fmtHace(actualizado) : ''}
                <Boton variante="fantasma" tamano="sm" icono={RefreshCw} etiqueta="Actualizar disponibilidad" className="!w-9 !h-9 !min-h-9 -my-2 -mr-2" cargando={disp.cargando} onClick={actualizar} />
              </span>
            }
          />
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
              <Vacio ilustracion="IL-26-sin-turnos" titulo="Sin horarios publicados" texto="Aún no hay turnos para ese día." compacto />
            </div>
          ) : !turnos.some((t) => t.estado === 'DISPONIBLE') ? (
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
              <GrillaTurnos turnos={turnos} seleccionado={null} alElegir={elegir} />
            </div>
          ) : (
            <GrillaTurnos turnos={turnos} seleccionado={r.turno?.fecha === fecha && r.turno.idCancha === idCancha ? r.turno.idTurno : null} alElegir={elegir} />
          )}
        </section>
      )}
    </div>
  );
}
