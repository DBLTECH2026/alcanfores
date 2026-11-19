import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  AlarmClock,
  Ban,
  CalendarDays,
  CircleCheck,
  CircleX,
  Clock,
  Eye,
  FileText,
  Globe,
  HandCoins,
  Hourglass,
  List,
  Plus,
  RefreshCw,
} from 'lucide-react';
import { pagosAdminApi, reservasAdminApi } from '@/api/admin';
import type { FilaReserva, ReservaAdmin } from '@/api/admin/tipos';
import { useCarga } from '@/hooks/useCarga';
import { useCanchas, useComplejo } from '@/hooks/useDatosPublicos';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { useToast } from '@/contexto/ToastContext';
import { CabeceraPagina, usePanel } from '@/componentes/panel/PanelLayout';
import {
  BarraBusqueda,
  CeldaDoc,
  ErrorCarga,
  FiltrosActivos,
  GrupoFiltro,
  MenuFila,
  OpcionesFiltro,
  PanelFiltros,
  useFiltrosUrl,
  useRetraso,
} from '@/componentes/panel/Herramientas';
import { ICONO_ORIGEN, ModalCancelar, ModalCobrar, ModalValidar, TEXTO_ORIGEN } from '@/componentes/panel/ModalesReserva';
import { abrirArchivo } from '@/componentes/panel/archivos';
import { Boton, BotonEnlace } from '@/componentes/ui/Boton';
import { Etiqueta, InsigniaEstado } from '@/componentes/ui/Estados';
import { Paginacion, Tabla, TarjetaKpi, Vacio } from '@/componentes/ui/Datos';
import { ahoraLima, deLima, fmtFecha, fmtRango24 } from '@/utilidades/fecha';
import { fmtCelular, fmtSoles } from '@/utilidades/formato';
import ReservaDetalle from './ReservaDetalle';

const TEXTOS: Record<string, Record<string, string>> = {
  estado: { PENDIENTE: 'Pendiente', CONFIRMADA: 'Confirmada', CANCELADA: 'Cancelada' },
  estadoPago: { SIN_PAGO: 'Sin pago', EN_REVISION: 'En revisión', OBSERVADO: 'Observado', ADELANTO_PAGADO: 'Adelanto pagado', PAGADO_TOTAL: 'Pagado' },
  origen: TEXTO_ORIGEN,
};
const TAMANIO = 15;

/** /admin/reservas y /admin/reservas/ALC-XXXXXX (la subruta muestra el detalle). */
export default function Reservas() {
  const codigo = useParams()['*'];
  if (codigo) return <ReservaDetalle codigo={codigo.toUpperCase()} />;
  return <ListaReservas />;
}

function ListaReservas() {
  useTituloPagina('Reservas');
  const navigate = useNavigate();
  const toast = useToast();
  const { refrescarContador } = usePanel();
  const canchas = useCanchas();
  const complejo = useComplejo();
  const f = useFiltrosUrl();
  const [busqueda, setBusqueda] = useState(f.leer('q'));
  const q = useRetraso(busqueda);
  const [filtrosAbiertos, setFiltrosAbiertos] = useState(false);
  const [version, setVersion] = useState(0);
  const [accion, setAccion] = useState<{ tipo: 'validar'; idPago: number } | { tipo: 'cancelar' | 'cobrar'; reserva: ReservaAdmin } | null>(null);

  // la búsqueda de la barra superior llega por ?q=
  const qUrl = f.leer('q');
  useEffect(() => setBusqueda(qUrl), [qUrl]);
  useEffect(() => {
    if (q !== f.leer('q')) f.poner({ q });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);

  const filtros = {
    estado: f.leer('estado'),
    estadoPago: f.leer('estadoPago'),
    idCancha: f.leer('idCancha'),
    origen: f.leer('origen'),
    fecha: f.leer('fecha'),
    desde: f.leer('desde'),
    hasta: f.leer('hasta'),
    q: f.leer('q'),
    sort: f.leer('sort') || 'fechaRegistro,desc',
  };
  const clave = JSON.stringify({ ...filtros, page: f.pagina, version });
  const lista = useCarga(() => reservasAdminApi.listar({ ...filtros, page: f.pagina, size: TAMANIO }), clave);
  const conteos = lista.datos?.conteos ?? {};
  const total = (conteos.PENDIENTE ?? 0) + (conteos.CONFIRMADA ?? 0) + (conteos.CANCELADA ?? 0);

  const activos = (
    [
    ['estado', 'Estado'],
    ['estadoPago', 'Pago'],
    ['idCancha', 'Cancha'],
    ['origen', 'Origen'],
    ['fecha', 'Fecha'],
    ['desde', 'Desde'],
    ['hasta', 'Hasta'],
    ] as Array<[string, string]>
  )
    .filter(([k]) => filtros[k as keyof typeof filtros])
    .map(([k, et]) => {
      const v = filtros[k as keyof typeof filtros];
      const texto =
        k === 'idCancha'
          ? canchas.datos?.find((c) => String(c.id) === v)?.nombre ?? `Cancha ${v}`
          : k === 'fecha' || k === 'desde' || k === 'hasta'
            ? `${et}: ${fmtFecha(v, 'dia-mes')}`
            : TEXTOS[k]?.[v] ?? v;
      return { clave: k, texto, alQuitar: () => f.poner({ [k]: null }) };
    });

  const kpi = (estado: string, estadoPago: string) => filtros.estado === estado && filtros.estadoPago === estadoPago;
  const ponerKpi = (estado: string, estadoPago: string) => f.poner({ estado: estado || null, estadoPago: estadoPago || null });

  const abrirAccion = async (tipo: 'validar' | 'cancelar' | 'cobrar', r: FilaReserva) => {
    try {
      if (tipo === 'validar') {
        const pagos = await pagosAdminApi.listar({ q: r.codigoReserva, estado: 'PENDIENTE', size: 5 });
        const p = pagos.contenido[0];
        if (p) setAccion({ tipo: 'validar', idPago: p.id });
        else toast.info('No hay comprobante pendiente');
      } else {
        const det = await reservasAdminApi.detalle(r.codigoReserva);
        setAccion({ tipo, reserva: det });
      }
    } catch {
      /* el interceptor ya avisó */
    }
  };
  const tras = () => {
    setAccion(null);
    setVersion((v) => v + 1);
    refrescarContador();
  };

  const urgente = (r: FilaReserva) => {
    if (r.estado !== 'PENDIENTE') return null;
    const min = deLima(`${r.fecha}T${r.horaInicio}:00`).diff(ahoraLima(), 'minute');
    if (min < 0) return <Etiqueta tono="vencido">vencido</Etiqueta>;
    if (min < 360) return <Etiqueta tono="urgente" icono={AlarmClock}>urgente</Etiqueta>;
    return null;
  };

  return (
    <>
      <CabeceraPagina
        titulo="Reservas"
        subtitulo="Reservas, comprobantes por validar y asistencia"
        acciones={
          <>
            <Boton variante="secundario" icono={RefreshCw} onClick={() => setVersion((v) => v + 1)}>
              Actualizar
            </Boton>
            <BotonEnlace to="/admin/nueva-reserva" icono={Plus}>
              Nueva reserva
            </BotonEnlace>
          </>
        }
      />

      <div className="kpis">
        <TarjetaKpi etiqueta="Todas" valor={total} icono={List} tono="noche" detalle="registradas" activa={kpi('', '')} alPulsar={() => ponerKpi('', '')} />
        <TarjetaKpi etiqueta="Por validar" valor={conteos.EN_REVISION ?? 0} icono={Hourglass} tono="gol" detalle="comprobantes en revisión" activa={kpi('', 'EN_REVISION')} alPulsar={() => ponerKpi('', 'EN_REVISION')} />
        <TarjetaKpi etiqueta="Pendientes" valor={conteos.PENDIENTE ?? 0} icono={Clock} tono="gris" detalle="sin adelanto validado" activa={kpi('PENDIENTE', '')} alPulsar={() => ponerKpi('PENDIENTE', '')} />
        <TarjetaKpi etiqueta="Confirmadas" valor={conteos.CONFIRMADA ?? 0} icono={CircleCheck} tono="verde" detalle="con adelanto validado" activa={kpi('CONFIRMADA', '')} alPulsar={() => ponerKpi('CONFIRMADA', '')} />
        <TarjetaKpi etiqueta="Canceladas" valor={conteos.CANCELADA ?? 0} icono={CircleX} tono="rojo" detalle="por el cliente, el panel o el sistema" activa={kpi('CANCELADA', '')} alPulsar={() => ponerKpi('CANCELADA', '')} />
      </div>

      <BarraBusqueda
        valor={busqueda}
        alCambiar={setBusqueda}
        placeholder="Código, DNI, nombre o celular"
        filtrosActivos={activos.length}
        alAbrirFiltros={() => setFiltrosAbiertos(true)}
        conteo={lista.datos ? `${lista.datos.totalElementos} reservas` : ''}
      />
      <FiltrosActivos items={activos} alLimpiar={() => f.limpiar(['q'])} />

      {lista.error ? (
        <ErrorCarga alReintentar={lista.recargar} />
      ) : (
        <Tabla<FilaReserva>
          etiqueta="Reservas"
          filas={lista.datos?.contenido ?? []}
          claveFila={(r) => r.codigoReserva}
          cargando={lista.cargando}
          alPulsarFila={(r) => navigate(`/admin/reservas/${r.codigoReserva}`)}
          vacio={
            lista.cargando ? (
              'Cargando…'
            ) : (
              <Vacio ilustracion="IL-24-sin-resultados" titulo="Sin resultados" texto="Prueba con otros filtros." compacto acciones={activos.length || filtros.q ? <Boton variante="secundario" onClick={() => { setBusqueda(''); f.limpiar(); }}>Limpiar filtros</Boton> : undefined} />
            )
          }
          insignias={(r) => (
            <>
              <InsigniaEstado dominio="reserva" estado={r.estado} />
              <InsigniaEstado dominio="estadoPago" estado={r.estadoPago} />
            </>
          )}
          columnas={[
            {
              clave: 'codigo',
              titulo: 'Código',
              celda: (r) => <CeldaDoc icono={ICONO_ORIGEN[r.origen] ?? Globe} titulo={<span className="tnum tracking-[.03em]">{r.codigoReserva}</span>} sub={TEXTO_ORIGEN[r.origen]} extra={urgente(r)} />,
            },
            {
              clave: 'cliente',
              titulo: 'Cliente',
              celda: (r) => (
                <div>
                  <span className="block">{`${r.cliente.nombres} ${r.cliente.apellidos}`}</span>
                  <span className="sub t-sec tnum">{fmtCelular(r.cliente.telefono)}</span>
                </div>
              ),
            },
            {
              clave: 'turno',
              titulo: 'Turno',
              celda: (r) => (
                <div>
                  <span className="block">{`${r.cancha.split(' · ')[0]} · ${fmtFecha(r.fecha, 'relativa')}`}</span>
                  <span className="sub t-sec tnum">{fmtRango24(r.horaInicio, r.horaFin)}</span>
                </div>
              ),
            },
            { clave: 'estado', titulo: 'Reserva', enTarjeta: false, celda: (r) => <InsigniaEstado dominio="reserva" estado={r.estado} /> },
            { clave: 'pago', titulo: 'Comprobante', enTarjeta: false, celda: (r) => <InsigniaEstado dominio="estadoPago" estado={r.estadoPago} /> },
            { clave: 'asistencia', titulo: 'Asistencia', celda: (r) => (r.estado === 'CONFIRMADA' ? <InsigniaEstado dominio="asistencia" estado={r.asistencia} /> : <span className="t-sec">—</span>) },
            {
              clave: 'monto',
              titulo: 'Pagado',
              alinear: 'der',
              celda: (r) => (
                <div className="tnum">
                  <b className="block">{fmtSoles(r.montoPagado)}</b>
                  <span className="sub t-sec">de {fmtSoles(r.montoTotal)}</span>
                </div>
              ),
            },
          ]}
          acciones={(r) => (
            <MenuFila
              acciones={[
                { texto: 'Ver detalle', icono: Eye, alPulsar: () => navigate(`/admin/reservas/${r.codigoReserva}`) },
                { texto: 'Validar comprobante', icono: CircleCheck, alPulsar: () => void abrirAccion('validar', r), oculta: r.estadoPago !== 'EN_REVISION' },
                { texto: 'Cobrar en caja', icono: HandCoins, alPulsar: () => void abrirAccion('cobrar', r), oculta: r.estado === 'CANCELADA' || r.montoPagado >= r.montoTotal || r.estadoPago === 'EN_REVISION' },
                { texto: 'Constancia PDF', icono: FileText, alPulsar: () => void abrirArchivo(reservasAdminApi.rutaConstancia(r.codigoReserva)) },
                { texto: 'Cancelar reserva', icono: Ban, peligro: true, alPulsar: () => void abrirAccion('cancelar', r), oculta: r.estado === 'CANCELADA' || r.asistencia !== 'PENDIENTE' },
              ]}
            />
          )}
          pie={
            lista.datos && (
              <Paginacion
                pagina={lista.datos.pagina}
                totalPaginas={lista.datos.totalPaginas}
                totalElementos={lista.datos.totalElementos}
                tamanio={lista.datos.tamanio}
                alCambiar={(p) => f.poner({ page: p || null })}
                etiqueta="reservas"
              />
            )
          }
        />
      )}

      <PanelFiltros abierto={filtrosAbiertos} alCerrar={() => setFiltrosAbiertos(false)} subtitulo="Se aplican al instante" alLimpiar={() => f.limpiar(['q'])} resultados={lista.datos?.totalElementos}>
        <GrupoFiltro titulo="Estado">
          <OpcionesFiltro
            valor={filtros.estado}
            alCambiar={(v) => f.poner({ estado: v })}
            opciones={[
              { valor: 'PENDIENTE', texto: 'Pendiente', icono: Clock, cuenta: conteos.PENDIENTE },
              { valor: 'CONFIRMADA', texto: 'Confirmada', icono: CircleCheck, cuenta: conteos.CONFIRMADA },
              { valor: 'CANCELADA', texto: 'Cancelada', icono: CircleX, cuenta: conteos.CANCELADA },
            ]}
          />
        </GrupoFiltro>
        <GrupoFiltro titulo="Estado de pago">
          <OpcionesFiltro
            valor={filtros.estadoPago}
            alCambiar={(v) => f.poner({ estadoPago: v })}
            opciones={Object.entries(TEXTOS.estadoPago ?? {}).map(([valor, texto]) => ({ valor, texto, cuenta: valor === 'EN_REVISION' ? conteos.EN_REVISION : undefined }))}
          />
        </GrupoFiltro>
        <GrupoFiltro titulo="Cancha">
          <OpcionesFiltro valor={filtros.idCancha} alCambiar={(v) => f.poner({ idCancha: v })} opciones={(canchas.datos ?? []).map((c) => ({ valor: String(c.id), texto: c.nombre.split(' · ')[0] ?? c.nombre }))} />
        </GrupoFiltro>
        <GrupoFiltro titulo="Origen">
          <OpcionesFiltro valor={filtros.origen} alCambiar={(v) => f.poner({ origen: v })} opciones={Object.entries(TEXTO_ORIGEN).map(([valor, texto]) => ({ valor, texto, icono: ICONO_ORIGEN[valor] }))} />
        </GrupoFiltro>
        <GrupoFiltro titulo="Fecha del turno">
          <div className="pf-fechas">
            <label>
              Día exacto
              <input type="date" value={filtros.fecha} onChange={(e) => f.poner({ fecha: e.target.value, desde: null, hasta: null })} />
            </label>
            <span />
            <label>
              Desde
              <input type="date" value={filtros.desde} max={filtros.hasta || undefined} onChange={(e) => f.poner({ desde: e.target.value, fecha: null })} />
            </label>
            <label>
              Hasta
              <input type="date" value={filtros.hasta} min={filtros.desde || undefined} onChange={(e) => f.poner({ hasta: e.target.value, fecha: null })} />
            </label>
          </div>
        </GrupoFiltro>
        <GrupoFiltro titulo="Orden">
          <select aria-label="Orden" value={filtros.sort} onChange={(e) => f.poner({ sort: e.target.value })}>
            <option value="fechaRegistro,desc">Registradas recientes</option>
            <option value="fecha,asc">Turno más próximo</option>
            <option value="fecha,desc">Turno más lejano</option>
            <option value="montoTotal,desc">Monto mayor</option>
          </select>
        </GrupoFiltro>
        <span className="dato-ico">
          <CalendarDays className="ico" aria-hidden />
          Hora de Lima
        </span>
      </PanelFiltros>

      <ModalValidar idPago={accion?.tipo === 'validar' ? accion.idPago : null} alCerrar={() => setAccion(null)} alHecho={tras} />
      <ModalCobrar reserva={accion?.tipo === 'cobrar' ? accion.reserva : null} alCerrar={() => setAccion(null)} alHecho={() => { setVersion((v) => v + 1); refrescarContador(); }} />
      <ModalCancelar reserva={accion?.tipo === 'cancelar' ? accion.reserva : null} devolucionHoras={complejo.datos?.reglas.devolucionHoras} alCerrar={() => setAccion(null)} alHecho={tras} />
    </>
  );
}
