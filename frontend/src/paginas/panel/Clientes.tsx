import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, BadgeCheck, CalendarCheck, CalendarDays, IdCard, Mail, MessageCircle, Phone, Plus, UserCheck, UserX, UsersRound } from 'lucide-react';
import { clientesApi } from '@/api/admin';
import type { ClienteAdmin, FilaReserva } from '@/api/admin/tipos';
import { esErrorApi } from '@/api/cliente';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { BarraBusqueda, ErrorCarga, useFiltrosUrl, useRetraso } from '@/componentes/panel/Herramientas';
import { BotonEnlace, BotonExterno } from '@/componentes/ui/Boton';
import { Avatar, Cargando, Paginacion, Tabla, TarjetaKpi, Vacio } from '@/componentes/ui/Datos';
import { InsigniaEstado } from '@/componentes/ui/Estados';
import { fmtFecha, fmtRango24 } from '@/utilidades/fecha';
import { fmtCelular, fmtSoles } from '@/utilidades/formato';

/** /admin/clientes (lista con métricas) y /admin/clientes/{dni} (historial). */
export default function Clientes() {
  const dni = useParams()['*'];
  if (dni) return <HistorialCliente dni={dni} />;
  return <ListaClientes />;
}

const pctNoShow = (c: ClienteAdmin) => (c.asistidas + c.noShows > 0 ? Math.round((c.noShows / (c.asistidas + c.noShows)) * 100) : 0);

function ListaClientes() {
  useTituloPagina('Clientes');
  const navigate = useNavigate();
  const f = useFiltrosUrl();
  const [busqueda, setBusqueda] = useState(f.leer('q'));
  const q = useRetraso(busqueda);
  useEffect(() => {
    if (q !== f.leer('q')) f.poner({ q });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);
  const lista = useCarga(() => clientesApi.listar(f.leer('q'), f.pagina, 15), `${f.leer('q')}-${f.pagina}`);

  return (
    <>
      <CabeceraPagina titulo="Clientes" subtitulo="Quiénes reservan, cuánto juegan y si asisten" busto="BU-rosa-panel" />
      <BarraBusqueda valor={busqueda} alCambiar={setBusqueda} placeholder="DNI, nombre, celular o correo" conteo={lista.datos ? `${lista.datos.totalElementos} clientes` : ''} />
      {lista.error ? (
        <ErrorCarga alReintentar={lista.recargar} />
      ) : (
        <Tabla<ClienteAdmin>
          etiqueta="Clientes"
          filas={lista.datos?.contenido ?? []}
          claveFila={(c) => c.dni}
          cargando={lista.cargando}
          alPulsarFila={(c) => navigate(`/admin/clientes/${c.dni}`)}
          vacio={lista.cargando ? 'Cargando…' : <Vacio ilustracion="IL-24-sin-resultados" titulo="Sin clientes" compacto />}
          columnas={[
            {
              clave: 'cliente',
              titulo: 'Cliente',
              celda: (c) => (
                <div className="td-doc">
                  <Avatar nombre={`${c.nombres} ${c.apellidos}`} tamano="sm" />
                  <div className="min-w-0">
                    <b className="block truncar">{`${c.nombres} ${c.apellidos}`}</b>
                    <span className="sub t-sec tnum inline-flex items-center gap-1">
                      DNI {c.dni}
                      {c.verificadoReniec && <BadgeCheck className="ico ico-14 text-verde" aria-label="Verificado en RENIEC" />}
                    </span>
                  </div>
                </div>
              ),
            },
            {
              clave: 'contacto',
              titulo: 'Contacto',
              celda: (c) => (
                <div>
                  <span className="block tnum">{fmtCelular(c.telefono)}</span>
                  <span className="sub t-sec">{c.correo}</span>
                </div>
              ),
            },
            { clave: 'reservas', titulo: 'Reservas', alinear: 'der', celda: (c) => <b className="tnum">{c.totalReservas}</b> },
            { clave: 'asistidas', titulo: 'Asistió', alinear: 'der', celda: (c) => <span className="tnum">{c.asistidas}</span> },
            {
              clave: 'noshow',
              titulo: 'No asistió',
              alinear: 'der',
              celda: (c) => (
                <span className={c.noShows > 0 ? 'tnum text-rojo font-semibold' : 'tnum'}>
                  {c.noShows}
                  {c.noShows > 0 && <span className="sub t-sec"> · {pctNoShow(c)} %</span>}
                </span>
              ),
            },
            { clave: 'ultima', titulo: 'Última reserva', celda: (c) => <span className="tnum">{c.ultimaReserva ? fmtFecha(c.ultimaReserva, 'dia-mes-anio') : '—'}</span> },
          ]}
          pie={lista.datos && <Paginacion pagina={lista.datos.pagina} totalPaginas={lista.datos.totalPaginas} totalElementos={lista.datos.totalElementos} tamanio={lista.datos.tamanio} alCambiar={(p) => f.poner({ page: p || null })} etiqueta="clientes" />}
        />
      )}
    </>
  );
}

function HistorialCliente({ dni }: { dni: string }) {
  useTituloPagina(`Cliente ${dni}`);
  const navigate = useNavigate();
  const h = useCarga(() => clientesApi.historial(dni), dni);
  if (h.error)
    return esErrorApi(h.error) && h.error.status === 404 ? (
      <div className="card">
        <Vacio ilustracion="IL-17-no-encontrada" titulo="Cliente no encontrado" texto={`DNI ${dni}`} acciones={<BotonEnlace to="/admin/clientes" variante="secundario" icono={ArrowLeft}>Volver</BotonEnlace>} />
      </div>
    ) : (
      <ErrorCarga alReintentar={h.recargar} />
    );
  if (!h.datos) return <Cargando texto="Cargando cliente…" />;
  const c = h.datos.cliente;
  const nombre = `${c.nombres} ${c.apellidos}`;
  const gastado = h.datos.reservas.reduce((s, r) => s + r.montoPagado, 0);
  return (
    <>
      <CabeceraPagina
        titulo={nombre}
        subtitulo={`Cliente desde ${fmtFecha(c.fechaRegistro.slice(0, 10), 'dia-mes-anio')}`}
        volver="/admin/clientes"
        acciones={
          <>
            <BotonExterno href={`https://wa.me/51${c.telefono}`} target="_blank" variante="whatsapp" icono={MessageCircle}>
              WhatsApp
            </BotonExterno>
            <BotonEnlace to="/admin/nueva-reserva" icono={Plus}>
              Nueva reserva
            </BotonEnlace>
          </>
        }
      />
      <div className="kpis">
        <TarjetaKpi etiqueta="Reservas" valor={c.totalReservas} icono={CalendarDays} tono="noche" detalle={c.ultimaReserva ? `última ${fmtFecha(c.ultimaReserva, 'dia-mes')}` : undefined} />
        <TarjetaKpi etiqueta="Asistió" valor={c.asistidas} icono={UserCheck} tono="verde" />
        <TarjetaKpi etiqueta="No asistió" valor={c.noShows} icono={UserX} tono="rojo" detalle={`${pctNoShow(c)} % de sus partidos`} />
        <TarjetaKpi etiqueta="Pagado" valor={fmtSoles(gastado)} icono={CalendarCheck} tono="gris" detalle="en total" />
      </div>
      <section className="pc">
        <div className="pc-cuerpo flex flex-wrap items-center gap-x-6 gap-y-3">
          <Avatar nombre={nombre} tamano="lg" />
          <span className="dato-ico">
            <IdCard className="ico" aria-hidden />
            DNI {c.dni}
            {c.verificadoReniec && <BadgeCheck className="ico !text-verde" aria-label="Verificado en RENIEC" />}
          </span>
          <a className="dato-ico no-underline" href={`tel:+51${c.telefono}`}>
            <Phone className="ico" aria-hidden />
            {fmtCelular(c.telefono, { prefijo: true })}
          </a>
          <span className="dato-ico">
            <Mail className="ico" aria-hidden />
            {c.correo}
          </span>
          <span className="dato-ico">
            <UsersRound className="ico" aria-hidden />
            {c.verificadoReniec ? 'Verificado en RENIEC' : 'Datos escritos a mano'}
          </span>
        </div>
      </section>
      <h2 className="t-h3">Historial</h2>
      <Tabla<FilaReserva>
        etiqueta="Reservas del cliente"
        filas={h.datos.reservas}
        claveFila={(r) => r.codigoReserva}
        alPulsarFila={(r) => navigate(`/admin/reservas/${r.codigoReserva}`)}
        vacio={<Vacio ilustracion="IL-26-sin-turnos" titulo="Sin reservas" compacto />}
        columnas={[
          { clave: 'codigo', titulo: 'Código', celda: (r) => <b className="tnum whitespace-nowrap">{r.codigoReserva}</b> },
          {
            clave: 'turno',
            titulo: 'Turno',
            celda: (r) => (
              <div>
                <span className="block">{`${fmtFecha(r.fecha, 'media')} · ${fmtRango24(r.horaInicio, r.horaFin)}`}</span>
                <span className="sub t-sec">{r.cancha}</span>
              </div>
            ),
          },
          { clave: 'estado', titulo: 'Reserva', celda: (r) => <InsigniaEstado dominio="reserva" estado={r.estado} /> },
          { clave: 'pago', titulo: 'Pago', celda: (r) => <InsigniaEstado dominio="estadoPago" estado={r.estadoPago} /> },
          { clave: 'asistencia', titulo: 'Asistencia', celda: (r) => (r.estado === 'CONFIRMADA' ? <InsigniaEstado dominio="asistencia" estado={r.asistencia} /> : <span className="t-sec">—</span>) },
          { clave: 'origen', titulo: 'Origen', celda: (r) => <InsigniaEstado dominio="origen" estado={r.origen} /> },
          { clave: 'monto', titulo: 'Pagado', alinear: 'der', celda: (r) => <span className="tnum">{`${fmtSoles(r.montoPagado)} / ${fmtSoles(r.montoTotal)}`}</span> },
        ]}
      />
    </>
  );
}
