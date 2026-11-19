import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { z } from 'zod';
import {
  Ban,
  BadgeCheck,
  CircleCheck,
  Eye,
  FileCode,
  FileText,
  History,
  Hourglass,
  Image as IconoImagen,
  Receipt,
  ReceiptText,
  RefreshCw,
  TriangleAlert,
  Wallet,
  X,
} from 'lucide-react';
import { comprobantesApi, pagosAdminApi } from '@/api/admin';
import type { Comprobante, FilaPago } from '@/api/admin/tipos';
import { esErrorApi } from '@/api/cliente';
import { useAuth } from '@/contexto/AuthContext';
import { useToast } from '@/contexto/ToastContext';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { CabeceraPagina, usePanel } from '@/componentes/panel/PanelLayout';
import {
  BarraBusqueda,
  CeldaDoc,
  ErrorCarga,
  Estado,
  FiltrosActivos,
  GrupoFiltro,
  MenuFila,
  ModalConfirmar,
  OpcionesFiltro,
  PanelFiltros,
  useFiltrosUrl,
  useRetraso,
} from '@/componentes/panel/Herramientas';
import { ICONO_METODO, ModalRechazar, ModalValidar, TEXTO_METODO, TEXTO_TIPO_PAGO } from '@/componentes/panel/ModalesReserva';
import { VisorComprobante } from '@/componentes/panel/VisorComprobante';
import { abrirArchivo, descargarArchivo } from '@/componentes/panel/archivos';
import { Boton } from '@/componentes/ui/Boton';
import { AreaTexto } from '@/componentes/ui/Campo';
import { Paginacion, Pestanas, Tabla, TarjetaKpi, Vacio } from '@/componentes/ui/Datos';
import { Modal } from '@/componentes/ui/Modal';
import { fmtFecha, fmtFechaHora } from '@/utilidades/fecha';
import { fmtSoles } from '@/utilidades/formato';
import { esquemaAnular } from './esquemasPanel';

const TEXTO_CANAL: Record<string, string> = { COMPROBANTE: 'Comprobante Yape/Plin', PASARELA: 'Pago en línea', CAJA: 'Caja' };
const TEXTO_TIPO_COMP: Record<string, string> = { BOLETA: 'Boleta', FACTURA: 'Factura', NOTA_CREDITO: 'Nota de crédito' };

/** /admin/pagos — pagos (validar, rechazar, ver imagen) y comprobantes electrónicos simulados (PDF, XML, anular → NC). */
export default function Pagos() {
  useTituloPagina('Pagos y comprobantes');
  const f = useFiltrosUrl();
  const pestana = f.leer('pestana') === 'comprobantes' ? 'comprobantes' : 'pagos';
  return (
    <>
      <CabeceraPagina titulo="Pagos y comprobantes" subtitulo="Cobros, validaciones, boletas, facturas y notas de crédito" busto="BU-lucho-tarjeta" />
      <Pestanas
        etiqueta="Sección"
        activa={pestana}
        alCambiar={(p) => {
          f.limpiar([]);
          f.poner({ pestana: p === 'pagos' ? null : p });
        }}
        items={[
          { valor: 'pagos', texto: 'Pagos', icono: Wallet },
          { valor: 'comprobantes', texto: 'Comprobantes', icono: ReceiptText },
        ]}
      />
      {pestana === 'pagos' ? <ListaPagos /> : <ListaComprobantes />}
    </>
  );
}

function useBusqueda(f: ReturnType<typeof useFiltrosUrl>) {
  const [busqueda, setBusqueda] = useState(f.leer('q'));
  const q = useRetraso(busqueda);
  useEffect(() => {
    if (q !== f.leer('q')) f.poner({ q });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);
  return [busqueda, setBusqueda] as const;
}

function FechasFiltro({ f }: { f: ReturnType<typeof useFiltrosUrl> }) {
  return (
    <GrupoFiltro titulo="Fecha">
      <div className="pf-fechas">
        <label>
          Desde
          <input type="date" value={f.leer('desde')} max={f.leer('hasta') || undefined} onChange={(e) => f.poner({ desde: e.target.value })} />
        </label>
        <label>
          Hasta
          <input type="date" value={f.leer('hasta')} min={f.leer('desde') || undefined} onChange={(e) => f.poner({ hasta: e.target.value })} />
        </label>
      </div>
    </GrupoFiltro>
  );
}

// ------------------------------ pagos ------------------------------
function ListaPagos() {
  const navigate = useNavigate();
  const { refrescarContador } = usePanel();
  const f = useFiltrosUrl();
  const [busqueda, setBusqueda] = useBusqueda(f);
  const [abierto, setAbierto] = useState(false);
  const [version, setVersion] = useState(0);
  const [modal, setModal] = useState<{ tipo: 'validar' | 'rechazar' | 'imagen'; pago: FilaPago } | null>(null);
  const filtros = { estado: f.leer('estado'), metodo: f.leer('metodo'), canal: f.leer('canal'), desde: f.leer('desde'), hasta: f.leer('hasta'), q: f.leer('q') };
  const lista = useCarga(() => pagosAdminApi.listar({ ...filtros, page: f.pagina, size: 15, sort: 'fechaEnvio,desc' }), JSON.stringify({ ...filtros, p: f.pagina, version }));
  const c = lista.datos?.conteos ?? {};
  const tras = () => {
    setModal(null);
    setVersion((v) => v + 1);
    refrescarContador();
  };
  const activos = (
    [
      ['estado', (v: string) => `Estado: ${v.toLowerCase()}`],
      ['metodo', (v: string) => TEXTO_METODO[v] ?? v],
      ['canal', (v: string) => TEXTO_CANAL[v] ?? v],
      ['desde', (v: string) => `Desde ${fmtFecha(v, 'dia-mes')}`],
      ['hasta', (v: string) => `Hasta ${fmtFecha(v, 'dia-mes')}`],
    ] as Array<[keyof typeof filtros, (v: string) => string]>
  )
    .filter(([k]) => filtros[k])
    .map(([k, t]) => ({ clave: k, texto: t(filtros[k]), alQuitar: () => f.poner({ [k]: null }) }));

  return (
    <>
      <div className="kpis">
        <TarjetaKpi etiqueta="En revisión" valor={c.PENDIENTE ?? 0} icono={Hourglass} tono="gol" detalle="por validar" activa={filtros.estado === 'PENDIENTE'} alPulsar={() => f.poner({ estado: filtros.estado === 'PENDIENTE' ? null : 'PENDIENTE' })} />
        <TarjetaKpi etiqueta="Validados" valor={c.VALIDADO ?? 0} icono={BadgeCheck} tono="verde" detalle="con comprobante" activa={filtros.estado === 'VALIDADO'} alPulsar={() => f.poner({ estado: filtros.estado === 'VALIDADO' ? null : 'VALIDADO' })} />
        <TarjetaKpi etiqueta="Rechazados" valor={c.RECHAZADO ?? 0} icono={TriangleAlert} tono="rojo" detalle="observados" activa={filtros.estado === 'RECHAZADO'} alPulsar={() => f.poner({ estado: filtros.estado === 'RECHAZADO' ? null : 'RECHAZADO' })} />
        <TarjetaKpi etiqueta="Reembolsados" valor={c.REEMBOLSADO ?? 0} icono={History} tono="gris" detalle="con nota de crédito" activa={filtros.estado === 'REEMBOLSADO'} alPulsar={() => f.poner({ estado: filtros.estado === 'REEMBOLSADO' ? null : 'REEMBOLSADO' })} />
        <TarjetaKpi etiqueta="Anulados" valor={c.ANULADO ?? 0} icono={Ban} tono="gris" detalle="por cancelación" activa={filtros.estado === 'ANULADO'} alPulsar={() => f.poner({ estado: filtros.estado === 'ANULADO' ? null : 'ANULADO' })} />
      </div>
      <BarraBusqueda
        valor={busqueda}
        alCambiar={setBusqueda}
        placeholder="Código de reserva, DNI, nombre u operación"
        filtrosActivos={activos.length}
        alAbrirFiltros={() => setAbierto(true)}
        conteo={lista.datos ? `${lista.datos.totalElementos} pagos` : ''}
        extra={<Boton variante="secundario" icono={RefreshCw} etiqueta="Actualizar" onClick={() => setVersion((v) => v + 1)} />}
      />
      <FiltrosActivos items={activos} alLimpiar={() => f.limpiar(['q'])} />
      {lista.error ? (
        <ErrorCarga alReintentar={lista.recargar} />
      ) : (
        <Tabla<FilaPago>
          etiqueta="Pagos"
          filas={lista.datos?.contenido ?? []}
          claveFila={(p) => p.id}
          cargando={lista.cargando}
          vacio={lista.cargando ? 'Cargando…' : <Vacio ilustracion="IL-24-sin-resultados" titulo="Sin pagos" compacto />}
          insignias={(p) => <Estado dominio="pago" estado={p.estado} />}
          columnas={[
            {
              clave: 'pago',
              titulo: 'Pago',
              celda: (p) => <CeldaDoc icono={ICONO_METODO[p.metodo] ?? Wallet} titulo={`${TEXTO_METODO[p.metodo] ?? p.metodo} · ${TEXTO_TIPO_PAGO[p.tipoPago]}`} sub={p.codigoOperacion && p.canal !== 'PASARELA' ? `Op. ${p.codigoOperacion}` : TEXTO_CANAL[p.canal]} />,
            },
            {
              clave: 'reserva',
              titulo: 'Reserva',
              celda: (p) => (
                <div>
                  <button type="button" className="enlace bg-transparent border-0 p-0 cursor-pointer tnum font-semibold" onClick={() => navigate(`/admin/reservas/${p.codigoReserva}`)}>
                    {p.codigoReserva}
                  </button>
                  <span className="sub t-sec block">{`${p.cliente.nombres} ${p.cliente.apellidos}`}</span>
                </div>
              ),
            },
            { clave: 'monto', titulo: 'Monto', alinear: 'der', celda: (p) => <b className="tnum">{fmtSoles(p.monto)}</b> },
            { clave: 'estado', titulo: 'Estado', enTarjeta: false, celda: (p) => <Estado dominio="pago" estado={p.estado} /> },
            {
              clave: 'fecha',
              titulo: 'Fecha',
              celda: (p) => (
                <div>
                  <span className="tnum block">{fmtFechaHora(p.fechaEnvio, { h24: true, sinAnio: true })}</span>
                  {p.validadoPor && <span className="sub t-sec">{p.validadoPor}</span>}
                </div>
              ),
            },
            {
              clave: 'comp',
              titulo: 'Comprobante',
              celda: (p) =>
                p.comprobante ? (
                  <button type="button" className="enlace bg-transparent border-0 p-0 cursor-pointer tnum" onClick={() => p.comprobante && void abrirArchivo(p.comprobante.urlPdf)}>
                    {p.comprobante.serieNumero}
                  </button>
                ) : (
                  <span className="t-sec">—</span>
                ),
            },
          ]}
          acciones={(p) => (
            <MenuFila
              acciones={[
                { texto: 'Validar', icono: CircleCheck, alPulsar: () => setModal({ tipo: 'validar', pago: p }), oculta: p.estado !== 'PENDIENTE' },
                { texto: 'Rechazar', icono: X, peligro: true, alPulsar: () => setModal({ tipo: 'rechazar', pago: p }), oculta: p.estado !== 'PENDIENTE' },
                { texto: 'Ver imagen', icono: IconoImagen, alPulsar: () => setModal({ tipo: 'imagen', pago: p }), oculta: !p.tieneArchivo },
                { texto: 'Ver reserva', icono: Eye, alPulsar: () => navigate(`/admin/reservas/${p.codigoReserva}`) },
                { texto: 'PDF', icono: FileText, alPulsar: () => p.comprobante && void abrirArchivo(p.comprobante.urlPdf), oculta: !p.comprobante },
                { texto: 'XML', icono: FileCode, alPulsar: () => p.comprobante && void descargarArchivo(p.comprobante.urlXml, `${p.comprobante.serieNumero}.xml`), oculta: !p.comprobante },
              ]}
            />
          )}
          pie={lista.datos && <Paginacion pagina={lista.datos.pagina} totalPaginas={lista.datos.totalPaginas} totalElementos={lista.datos.totalElementos} tamanio={lista.datos.tamanio} alCambiar={(p) => f.poner({ page: p || null })} etiqueta="pagos" />}
        />
      )}
      <PanelFiltros abierto={abierto} alCerrar={() => setAbierto(false)} alLimpiar={() => f.limpiar(['q'])} resultados={lista.datos?.totalElementos}>
        <GrupoFiltro titulo="Estado">
          <OpcionesFiltro valor={filtros.estado} alCambiar={(v) => f.poner({ estado: v })} opciones={['PENDIENTE', 'VALIDADO', 'RECHAZADO', 'REEMBOLSADO', 'ANULADO'].map((e) => ({ valor: e, texto: e.charAt(0) + e.slice(1).toLowerCase(), cuenta: c[e] }))} />
        </GrupoFiltro>
        <GrupoFiltro titulo="Método">
          <OpcionesFiltro valor={filtros.metodo} alCambiar={(v) => f.poner({ metodo: v })} opciones={Object.entries(TEXTO_METODO).map(([valor, texto]) => ({ valor, texto, icono: ICONO_METODO[valor] }))} />
        </GrupoFiltro>
        <GrupoFiltro titulo="Canal">
          <OpcionesFiltro valor={filtros.canal} alCambiar={(v) => f.poner({ canal: v })} opciones={Object.entries(TEXTO_CANAL).map(([valor, texto]) => ({ valor, texto }))} />
        </GrupoFiltro>
        <FechasFiltro f={f} />
      </PanelFiltros>
      <ModalValidar idPago={modal?.tipo === 'validar' ? modal.pago.id : null} alCerrar={() => setModal(null)} alHecho={tras} />
      <ModalRechazar idPago={modal?.tipo === 'rechazar' ? modal.pago.id : null} alCerrar={() => setModal(null)} alHecho={tras} />
      <Modal abierto={modal?.tipo === 'imagen'} alCerrar={() => setModal(null)} titulo="Comprobante" subtitulo={modal ? `${modal.pago.codigoReserva} · ${fmtSoles(modal.pago.monto)}` : ''} busto="BU-lucho-tarjeta" tamano="lg">
        {modal?.tipo === 'imagen' && <VisorComprobante idPago={modal.pago.id} />}
      </Modal>
    </>
  );
}

// ------------------------------ comprobantes ------------------------------
function ListaComprobantes() {
  const navigate = useNavigate();
  const { tieneRol } = useAuth();
  const f = useFiltrosUrl();
  const [busqueda, setBusqueda] = useBusqueda(f);
  const [abierto, setAbierto] = useState(false);
  const [version, setVersion] = useState(0);
  const [anular, setAnular] = useState<Comprobante | null>(null);
  const filtros = { tipo: f.leer('tipo'), estado: f.leer('estado'), desde: f.leer('desde'), hasta: f.leer('hasta'), q: f.leer('q') };
  const lista = useCarga(() => comprobantesApi.listar({ ...filtros, page: f.pagina, size: 15, sort: 'fechaEmision,desc' }), JSON.stringify({ ...filtros, p: f.pagina, version }));
  const activos = (
    [
      ['tipo', (v: string) => TEXTO_TIPO_COMP[v] ?? v],
      ['estado', (v: string) => (v === 'EMITIDO' ? 'Emitidos' : 'Anulados')],
      ['desde', (v: string) => `Desde ${fmtFecha(v, 'dia-mes')}`],
      ['hasta', (v: string) => `Hasta ${fmtFecha(v, 'dia-mes')}`],
    ] as Array<[keyof typeof filtros, (v: string) => string]>
  )
    .filter(([k]) => filtros[k])
    .map(([k, t]) => ({ clave: k, texto: t(filtros[k]), alQuitar: () => f.poner({ [k]: null }) }));

  return (
    <>
      <BarraBusqueda
        valor={busqueda}
        alCambiar={setBusqueda}
        placeholder="Serie y número, reserva, DNI, RUC o nombre"
        filtrosActivos={activos.length}
        alAbrirFiltros={() => setAbierto(true)}
        conteo={lista.datos ? `${lista.datos.totalElementos} comprobantes` : ''}
      />
      <FiltrosActivos items={activos} alLimpiar={() => f.limpiar(['q', 'pestana'])} />
      {lista.error ? (
        <ErrorCarga alReintentar={lista.recargar} />
      ) : (
        <Tabla<Comprobante>
          etiqueta="Comprobantes"
          filas={lista.datos?.contenido ?? []}
          claveFila={(c) => c.id}
          cargando={lista.cargando}
          vacio={lista.cargando ? 'Cargando…' : <Vacio ilustracion="IL-24-sin-resultados" titulo="Sin comprobantes" compacto />}
          insignias={(c) => (
            <>
              <Estado dominio="tipoComprobante" estado={c.tipo} />
              <Estado dominio="comprobante" estado={c.estado} />
            </>
          )}
          columnas={[
            {
              clave: 'serie',
              titulo: 'Comprobante',
              celda: (c) => <CeldaDoc icono={c.tipo === 'NOTA_CREDITO' ? Receipt : ReceiptText} titulo={<span className="tnum">{c.serieNumero}</span>} sub={c.serieNumeroReferencia ? `${TEXTO_TIPO_COMP[c.tipo]} de ${c.serieNumeroReferencia}` : TEXTO_TIPO_COMP[c.tipo]} />,
            },
            { clave: 'fecha', titulo: 'Emisión', celda: (c) => <span className="tnum">{fmtFechaHora(c.fechaEmision, { h24: true })}</span> },
            {
              clave: 'cliente',
              titulo: 'Cliente',
              celda: (c) => (
                <div className="max-w-[220px]">
                  <span className="block truncar">{c.clienteNombre}</span>
                  <span className="sub t-sec tnum">{`${c.clienteTipoDoc === '6' ? 'RUC' : 'DNI'} ${c.clienteNumDoc}`}</span>
                </div>
              ),
            },
            {
              clave: 'reserva',
              titulo: 'Reserva',
              celda: (c) => (
                <button type="button" className="enlace bg-transparent border-0 p-0 cursor-pointer tnum" onClick={() => navigate(`/admin/reservas/${c.codigoReserva}`)}>
                  {c.codigoReserva}
                </button>
              ),
            },
            {
              clave: 'total',
              titulo: 'Total',
              alinear: 'der',
              celda: (c) => (
                <div className="tnum">
                  <b className="block">{fmtSoles(c.total)}</b>
                  <span className="sub t-sec">IGV {fmtSoles(c.igv)}</span>
                </div>
              ),
            },
            { clave: 'estado', titulo: 'Estado', enTarjeta: false, celda: (c) => <Estado dominio="comprobante" estado={c.estado} /> },
          ]}
          acciones={(c) => (
            <div className="flex items-center gap-1 justify-end">
              <Boton variante="fantasma" tamano="sm" icono={FileText} etiqueta={`PDF ${c.serieNumero}`} onClick={() => void abrirArchivo(c.urlPdf)} />
              <Boton variante="fantasma" tamano="sm" icono={FileCode} etiqueta={`XML ${c.serieNumero}`} onClick={() => void descargarArchivo(c.urlXml, `${c.serieNumero}.xml`)} />
              {tieneRol('ADMIN') && c.estado === 'EMITIDO' && c.tipo !== 'NOTA_CREDITO' && (
                <MenuFila acciones={[{ texto: 'Anular con nota de crédito', icono: Ban, peligro: true, alPulsar: () => setAnular(c) }]} />
              )}
            </div>
          )}
          pie={lista.datos && <Paginacion pagina={lista.datos.pagina} totalPaginas={lista.datos.totalPaginas} totalElementos={lista.datos.totalElementos} tamanio={lista.datos.tamanio} alCambiar={(p) => f.poner({ page: p || null })} etiqueta="comprobantes" />}
        />
      )}
      <PanelFiltros abierto={abierto} alCerrar={() => setAbierto(false)} alLimpiar={() => f.limpiar(['q', 'pestana'])} resultados={lista.datos?.totalElementos}>
        <GrupoFiltro titulo="Tipo">
          <OpcionesFiltro valor={filtros.tipo} alCambiar={(v) => f.poner({ tipo: v })} opciones={Object.entries(TEXTO_TIPO_COMP).map(([valor, texto]) => ({ valor, texto }))} />
        </GrupoFiltro>
        <GrupoFiltro titulo="Estado">
          <OpcionesFiltro
            valor={filtros.estado}
            alCambiar={(v) => f.poner({ estado: v })}
            opciones={[
              { valor: 'EMITIDO', texto: 'Emitidos' },
              { valor: 'ANULADO', texto: 'Anulados' },
            ]}
          />
        </GrupoFiltro>
        <FechasFiltro f={f} />
      </PanelFiltros>
      <ModalAnular comprobante={anular} alCerrar={() => setAnular(null)} alHecho={() => setVersion((v) => v + 1)} />
    </>
  );
}

function ModalAnular({ comprobante, alCerrar, alHecho }: { comprobante: Comprobante | null; alCerrar: () => void; alHecho: () => void }) {
  const toast = useToast();
  const [nc, setNc] = useState<Comprobante | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    reset,
    watch,
    formState: { errors },
  } = useForm<z.infer<typeof esquemaAnular>>({ resolver: zodResolver(esquemaAnular), mode: 'onTouched', defaultValues: { motivo: '' } });
  useEffect(() => {
    reset({ motivo: '' });
    setNc(null);
  }, [comprobante, reset]);
  const enviar = handleSubmit(async ({ motivo }) => {
    if (!comprobante) return;
    try {
      const r = await comprobantesApi.anular(comprobante.id, motivo);
      setNc(r);
      toast.exito(`Nota de crédito ${r.serieNumero}`);
      alHecho();
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['motivo']);
      if (!aplicados) toast.error(esErrorApi(e) ? e.message : 'No se pudo anular.');
    }
  });
  if (nc)
    return (
      <Modal
        abierto
        alCerrar={alCerrar}
        titulo="Nota de crédito emitida"
        subtitulo={`${nc.serieNumero} · anula ${nc.serieNumeroReferencia ?? ''}`}
        ilustracion="IL-12-confirmada"
        acciones={
          <>
            <Boton variante="secundario" icono={FileText} onClick={() => void abrirArchivo(nc.urlPdf)}>
              Ver PDF
            </Boton>
            <Boton icono={CircleCheck} onClick={alCerrar}>
              Listo
            </Boton>
          </>
        }
      >
        <p className="m-0 text-center t-sec">Se envió al cliente por correo y WhatsApp.</p>
      </Modal>
    );
  return (
    <ModalConfirmar
      abierto={comprobante !== null}
      alCerrar={alCerrar}
      titulo="Anular comprobante"
      subtitulo={comprobante ? `${comprobante.serieNumero} · ${comprobante.clienteNombre}` : ''}
      busto="BU-lucho-tarjeta"
      franja={comprobante ? [['Total', fmtSoles(comprobante.total)], ['IGV', fmtSoles(comprobante.igv)], ['Reserva', comprobante.codigoReserva]] : undefined}
      peligro
      textoConfirmar="Emitir nota de crédito"
      iconoConfirmar={Ban}
      alConfirmar={enviar}
    >
      <form className="grid gap-3" onSubmit={enviar} noValidate>
        <AreaTexto label="Motivo" requerido rows={3} maxLength={255} contador={{ actual: (watch('motivo') ?? '').length, max: 255 }} placeholder="Error en los datos del cliente" error={errors.motivo?.message} {...register('motivo')} />
        <div className="caja-aviso">
          <div>
            <Receipt className="ico" aria-hidden />
            Se emite una nota de crédito ({comprobante?.tipo === 'FACTURA' ? 'FC01' : 'BC01'}) por el total
          </div>
          <div>
            <Ban className="ico" aria-hidden />
            El comprobante queda ANULADO (simulado, sin envío a SUNAT)
          </div>
        </div>
      </form>
    </ModalConfirmar>
  );
}
