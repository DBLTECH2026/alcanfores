import { Link } from 'react-router-dom';
import { AlarmClock, CalendarDays, ChartNoAxesColumn, CircleCheck, ClipboardList, Hourglass, Plus, RefreshCw, TriangleAlert, UserX } from 'lucide-react';
import { panelApi } from '@/api/servicios';
import type { DashboardHoy } from '@/api/tipos';
import { useAuth } from '@/contexto/AuthContext';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { Boton, BotonEnlace } from '@/componentes/ui/Boton';
import { Etiqueta, InsigniaEstado } from '@/componentes/ui/Estados';
import { Esqueleto, Tabla, TarjetaKpi, Vacio } from '@/componentes/ui/Datos';
import { fmtFecha, fmtRango24, hoyLima } from '@/utilidades/fecha';

type Proxima = DashboardHoy['proximas'][number];

/**
 * /admin/hoy — página PROVISIONAL del tablero (indicadores, próximas reservas y alertas de /api/admin/dashboard).
 * La versión completa (agenda por cancha, validar desde aquí) la arma el siguiente agente con estos mismos componentes.
 */
export default function Hoy() {
  useTituloPagina('Hoy');
  const { usuario } = useAuth();
  const d = useCarga(() => panelApi.dashboard(hoyLima(), { silenciar: true }), 'hoy');
  const k = d.datos?.kpis;

  return (
    <>
      <CabeceraPagina
        titulo="Hoy"
        subtitulo={`${fmtFecha(hoyLima(), 'larga')} · hola, ${usuario?.nombres.split(' ')[0] ?? ''}`}
        acciones={
          <>
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
        <div className="card">
          <Vacio
            ilustracion="IL-19-sin-conexion"
            titulo="Sin conexión con el servidor"
            compacto
            acciones={
              <Boton variante="secundario" icono={RefreshCw} onClick={d.recargar}>
                Reintentar
              </Boton>
            }
          />
        </div>
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
                <TarjetaKpi etiqueta="Por validar" valor={k.porValidar} icono={Hourglass} tono="gol" detalle="comprobantes" />
                <TarjetaKpi etiqueta="Reservas hoy" valor={k.reservasHoy} icono={ClipboardList} tono="noche" detalle="en todas las canchas" />
                <TarjetaKpi etiqueta="Confirmadas" valor={k.confirmadasHoy} icono={CircleCheck} tono="verde" detalle="con adelanto" />
                <TarjetaKpi etiqueta="Ocupación" valor={`${k.ocupacionHoyPct} %`} icono={ChartNoAxesColumn} tono="noche" barra={k.ocupacionHoyPct} />
                <TarjetaKpi etiqueta="No asistió" valor={`${k.noShowMesPct} %`} icono={UserX} tono="rojo" detalle="este mes" />
                <TarjetaKpi etiqueta="Semana" valor={k.reservasSemana} icono={CalendarDays} tono="gris" detalle="reservas en 7 días" />
              </>
            )}
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
                columnas={[
                  { clave: 'codigo', titulo: 'Código', celda: (p) => <span className="cod">{p.codigoReserva}</span> },
                  { clave: 'hora', titulo: 'Hora', celda: (p) => <span className="tnum">{`${fmtFecha(p.fecha, 'relativa')} · ${fmtRango24(p.horaInicio, p.horaFin)}`}</span> },
                  { clave: 'cancha', titulo: 'Cancha', celda: (p) => p.cancha },
                  { clave: 'cliente', titulo: 'Cliente', celda: (p) => p.cliente },
                  { clave: 'estado', titulo: 'Estado', celda: (p) => <InsigniaEstado dominio="estadoPago" estado={p.estadoPago} /> },
                ]}
              />
            </section>
            <section className="card overflow-hidden" aria-labelledby="h-alertas">
              <div className="card-cab">
                <h2 id="h-alertas" className="t-h4 inline-flex items-center gap-2">
                  <TriangleAlert className="ico ico-18 text-amarillo-ocre" aria-hidden />
                  Alertas
                </h2>
              </div>
              {d.datos && d.datos.alertas.length === 0 ? (
                <Vacio ilustracion="IL-23-todo-al-dia" titulo="Todo al día" compacto />
              ) : (
                <ul className="grid">
                  {(d.datos?.alertas ?? []).map((a, i) => (
                    <li key={i} className="flex items-start gap-3 px-4 py-3 border-t border-borde-panel first:border-t-0 text-[13.5px]">
                      <Etiqueta tono={a.tipo === 'URGENTE' ? 'urgente' : a.tipo === 'VENCIDO' ? 'vencido' : 'neutro'} icono={a.tipo === 'URGENTE' ? AlarmClock : undefined}>
                        {a.tipo.toLowerCase()}
                      </Etiqueta>
                      <span className="flex-1">{a.mensaje}</span>
                      {a.codigoReserva && (
                        <Link className="enlace text-[13px] tnum" to={`/admin/reservas/${a.codigoReserva}`}>
                          {a.codigoReserva}
                        </Link>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </>
      )}
    </>
  );
}
