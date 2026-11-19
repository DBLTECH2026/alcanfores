import { Fragment, useEffect, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlarmClock, CircleAlert, FileText, Image as ImageIcon, Inbox, Mail, MessageCircle, Paperclip, Play, RefreshCw, RotateCw, Send, X } from 'lucide-react';
import { notificacionesApi } from '@/api/admin';
import type { Adjunto, FilaNotificacion, Recordatorio, ResultadoRecordatorios } from '@/api/admin/tipos';
import { useAuth } from '@/contexto/AuthContext';
import { useToast } from '@/contexto/ToastContext';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { BarraBusqueda, ErrorCarga, Estado, FiltrosActivos, GrupoFiltro, ModalConfirmar, OpcionesFiltro, PanelFiltros, useFiltrosUrl, useRetraso } from '@/componentes/panel/Herramientas';
import { Boton } from '@/componentes/ui/Boton';
import { Cargando, Paginacion, Pestanas, Tabla, Vacio } from '@/componentes/ui/Datos';
import { Alerta } from '@/componentes/ui/Estados';
import { fmtFechaHora, fmtHace } from '@/utilidades/fecha';
import { cx } from '@/utilidades/cx';
import { fmtMinutosAntes } from '@/utilidades/formato';

const EVENTOS: Record<string, string> = {
  'reserva.registrada': 'Reserva registrada',
  'pago.registrado': 'Comprobante recibido',
  'pago.rechazado': 'Comprobante observado',
  'reserva.confirmada': 'Reserva confirmada',
  'comprobante.emitido': 'Comprobante emitido',
  'reserva.cancelada': 'Reserva cancelada',
  'reserva.asistencia': 'Invitación a calificar',
  recordatorio: 'Recordatorio',
  'whatsapp.prueba': 'Prueba de WhatsApp',
  'pago.validado': 'Saldo pagado',
  'cuenta.codigo': 'Código para cambiar la contraseña',
  'cuenta.contrasena': 'Contraseña cambiada',
  'correo.prueba': 'Prueba de correo',
};

/** Texto de WhatsApp con *negritas* (sin HTML del back: se arma con nodos React). */
export function TextoWhatsapp({ texto }: { texto: string }) {
  const partes = texto.split(/(\*[^*\n]+\*)/g);
  return (
    <>
      {partes.map((p, i) => (p.startsWith('*') && p.endsWith('*') && p.length > 2 ? <b key={i}>{p.slice(1, -1)}</b> : <Fragment key={i}>{p}</Fragment>))}
    </>
  );
}

/** /admin/notificaciones — bandeja de avisos (correo y WhatsApp) con vista previa, reintento y recordatorios. */
export default function Notificaciones() {
  useTituloPagina('Notificaciones');
  const { tieneRol } = useAuth();
  const f = useFiltrosUrl();
  const pestana = f.leer('pestana') === 'recordatorios' && tieneRol('ADMIN') ? 'recordatorios' : 'bandeja';
  return (
    <>
      <CabeceraPagina titulo="Notificaciones" subtitulo="Lo que el sistema envió por correo y WhatsApp" busto="BU-rosa-laptop" />
      {tieneRol('ADMIN') && (
        <Pestanas
          etiqueta="Sección"
          activa={pestana}
          alCambiar={(p) => {
            f.limpiar([]);
            f.poner({ pestana: p === 'bandeja' ? null : p });
          }}
          items={[
            { valor: 'bandeja', texto: 'Bandeja', icono: Inbox },
            { valor: 'recordatorios', texto: 'Recordatorios', icono: AlarmClock },
          ]}
        />
      )}
      {pestana === 'bandeja' ? <Bandeja /> : <Recordatorios />}
    </>
  );
}

function Bandeja() {
  const navigate = useNavigate();
  const toast = useToast();
  const { tieneRol } = useAuth();
  const f = useFiltrosUrl();
  const [busqueda, setBusqueda] = useState(f.leer('q'));
  const q = useRetraso(busqueda);
  useEffect(() => {
    if (q !== f.leer('q')) f.poner({ q });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);
  const [abierto, setAbierto] = useState(false);
  const [version, setVersion] = useState(0);
  const filtros = { canal: f.leer('canal'), estado: f.leer('estado'), evento: f.leer('evento'), desde: f.leer('desde'), hasta: f.leer('hasta'), q: f.leer('q') };
  const lista = useCarga(() => notificacionesApi.listar({ ...filtros, page: f.pagina, size: 12, sort: 'creadoEn,desc' }), JSON.stringify({ ...filtros, p: f.pagina, version }));
  const idSel = Number(f.leer('id')) || lista.datos?.contenido[0]?.id || null;
  const det = useCarga(() => notificacionesApi.detalle(idSel ?? 0), idSel ? `n-${idSel}-${version}` : null);
  const [reintentar, setReintentar] = useState(false);

  const activos = (
    [
      ['canal', (v: string) => (v === 'CORREO' ? 'Correo' : 'WhatsApp')],
      ['estado', (v: string) => v.charAt(0) + v.slice(1).toLowerCase()],
      ['evento', (v: string) => EVENTOS[v] ?? v],
      ['desde', (v: string) => `Desde ${v}`],
      ['hasta', (v: string) => `Hasta ${v}`],
    ] as Array<[keyof typeof filtros, (v: string) => string]>
  )
    .filter(([k]) => filtros[k])
    .map(([k, t]) => ({ clave: k, texto: t(filtros[k]), alQuitar: () => f.poner({ [k]: null }) }));

  const hacerReintento = async () => {
    if (!det.datos) return;
    try {
      const r = await notificacionesApi.reintentar(det.datos.id);
      det.setDatos(r);
      toast.exito(r.estado === 'FALLIDO' ? 'Volvió a fallar' : 'Aviso reenviado');
      setReintentar(false);
      setVersion((v) => v + 1);
    } catch {
      /* toast del interceptor */
    }
  };

  let vista: ReactNode = null;
  if (det.cargando && !det.datos) vista = <Cargando texto="Cargando aviso…" />;
  else if (det.datos) {
    const n = det.datos;
    vista = (
      <div className="grid gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <Estado dominio="canal" estado={n.canal} />
          <Estado dominio="notificacion" estado={n.estado} />
          <span className="t-sec text-[12.5px]">{n.eventoTexto}</span>
          {tieneRol('ADMIN') && n.reintentable && (
            <Boton tamano="sm" variante="oscuro" icono={RotateCw} className="ml-auto" onClick={() => setReintentar(true)}>
              Reintentar
            </Boton>
          )}
        </div>
        <div className="grid gap-1 text-[13px]">
          <b className="text-[15px]">{n.asunto}</b>
          <span className="t-sec">
            Para <b className="text-noche tnum">{n.destino}</b> · {fmtFechaHora(n.enviadoEn ?? n.creadoEn, { h24: true })} · {n.intentos} {n.intentos === 1 ? 'intento' : 'intentos'}
          </span>
          {n.codigoReserva && (
            <button type="button" className="enlace bg-transparent border-0 p-0 cursor-pointer w-fit tnum" onClick={() => navigate(`/admin/reservas/${n.codigoReserva}`)}>
              {n.codigoReserva}
            </button>
          )}
        </div>
        {n.ultimoError && (
          <Alerta tipo="error" titulo="Último error">
            {n.ultimoError}
          </Alerta>
        )}
        {n.adjuntos.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {n.adjuntos.map((a) => (
              <span key={a.referencia + a.nombreArchivo} className={cx('badge badge-sm', a.estado === 'ADJUNTADO' ? 'b-menta' : a.estado === 'NO_DISPONIBLE' ? 'b-rojo' : 'b-neutro')} title={a.detalle ?? undefined}>
                {a.tipo === 'IMAGEN' ? <ImageIcon className="ico" aria-hidden /> : <Paperclip className="ico" aria-hidden />}
                <span>{a.nombreArchivo}</span>
              </span>
            ))}
          </div>
        )}
        {n.canal === 'CORREO' ? (
          <iframe title={`Vista previa: ${n.asunto}`} className="prev-correo" sandbox="" srcDoc={n.vistaPrevia ?? n.cuerpo} />
        ) : (
          <div className="prev-wa" aria-label="Vista previa del WhatsApp">
            {n.adjuntos
              .filter((a) => a.tipo !== 'IMAGEN')
              .map((a) => (
                <div key={a.nombreArchivo} className="doc">
                  <FileText className="ico ico-24" aria-hidden />
                  {a.nombreArchivo}
                </div>
              ))}
            <div className={cx('burbuja', imagenWa(n) && 'con-imagen')}>
              {imagenWa(n) && <img src={imagenWa(n) ?? undefined} alt="Tarjeta ilustrada del aviso" loading="lazy" />}
              <TextoWhatsapp texto={n.cuerpo} />
              <small>{(n.enviadoEn ?? n.creadoEn).slice(11, 16)}</small>
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <>
      <BarraBusqueda
        valor={busqueda}
        alCambiar={setBusqueda}
        placeholder="Código, destino o asunto"
        filtrosActivos={activos.length}
        alAbrirFiltros={() => setAbierto(true)}
        conteo={lista.datos ? `${lista.datos.totalElementos} avisos` : ''}
        extra={<Boton variante="secundario" icono={RefreshCw} etiqueta="Actualizar" onClick={() => setVersion((v) => v + 1)} />}
      />
      <FiltrosActivos items={activos} alLimpiar={() => f.limpiar(['q'])} />
      {lista.error ? (
        <ErrorCarga alReintentar={lista.recargar} />
      ) : (
        <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)] xl:items-start">
          <section className="pc">
            {!lista.datos ? (
              <Cargando />
            ) : lista.datos.contenido.length === 0 ? (
              <Vacio ilustracion="IL-24-sin-resultados" titulo="Sin avisos" compacto />
            ) : (
              <ul className="grid">
                {lista.datos.contenido.map((n: FilaNotificacion) => (
                  <li key={n.id}>
                    <button
                      type="button"
                      className={cx('w-full text-left flex items-start gap-3 px-4 py-3 border-0 border-t border-[#EEF2F0] bg-white cursor-pointer hover:bg-[#F8FAF9]', n.id === idSel && '!bg-menta')}
                      aria-pressed={n.id === idSel}
                      onClick={() => f.poner({ id: n.id, page: f.pagina || null })}
                    >
                      <span className="td-ico">{n.canal === 'CORREO' ? <Mail className="ico" aria-hidden /> : <MessageCircle className="ico" aria-hidden />}</span>
                      <span className="grid gap-0.5 min-w-0 flex-1">
                        <b className="truncar text-[13.5px]">{n.eventoTexto}</b>
                        <span className="t-sec text-[12.5px] truncar">
                          {n.codigoReserva ?? ''} · {n.destino}
                        </span>
                        <span className="t-sec text-[11.5px]">{fmtHace(n.creadoEn)}</span>
                      </span>
                      <span className="grid justify-items-end gap-1">
                        <Estado dominio="notificacion" estado={n.estado} tamano="sm" />
                        {n.adjuntos > 0 && (
                          <span className="dato-ico text-[11.5px]">
                            <Paperclip className="ico" aria-hidden />
                            {n.adjuntos}
                          </span>
                        )}
                        {n.estado === 'FALLIDO' && <CircleAlert className="ico ico-16 text-rojo" aria-label="Fallido" />}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {lista.datos && lista.datos.totalPaginas > 1 && (
              <div className="border-t border-[#EEF2F0] px-2">
                <Paginacion pagina={lista.datos.pagina} totalPaginas={lista.datos.totalPaginas} totalElementos={lista.datos.totalElementos} tamanio={lista.datos.tamanio} alCambiar={(p) => f.poner({ page: p || null, id: null })} etiqueta="avisos" />
              </div>
            )}
          </section>
          <section className="pc xl:sticky xl:top-4" aria-label="Vista previa">
            <div className="pc-cab">
              <h2>
                <Send className="ico" aria-hidden />
                Vista previa
              </h2>
            </div>
            <div className="pc-cuerpo">{vista ?? <Vacio ilustracion="IL-23-todo-al-dia" titulo="Elige un aviso" compacto />}</div>
          </section>
        </div>
      )}
      <PanelFiltros abierto={abierto} alCerrar={() => setAbierto(false)} alLimpiar={() => f.limpiar(['q'])} resultados={lista.datos?.totalElementos}>
        <GrupoFiltro titulo="Canal">
          <OpcionesFiltro
            valor={filtros.canal}
            alCambiar={(v) => f.poner({ canal: v, id: null })}
            opciones={[
              { valor: 'CORREO', texto: 'Correo', icono: Mail },
              { valor: 'WHATSAPP', texto: 'WhatsApp', icono: MessageCircle },
            ]}
          />
        </GrupoFiltro>
        <GrupoFiltro titulo="Estado">
          <OpcionesFiltro valor={filtros.estado} alCambiar={(v) => f.poner({ estado: v, id: null })} opciones={['ENVIADO', 'SIMULADO', 'PENDIENTE', 'FALLIDO'].map((e) => ({ valor: e, texto: e.charAt(0) + e.slice(1).toLowerCase() }))} />
        </GrupoFiltro>
        <GrupoFiltro titulo="Aviso">
          <OpcionesFiltro valor={filtros.evento} alCambiar={(v) => f.poner({ evento: v, id: null })} opciones={Object.entries(EVENTOS).map(([valor, texto]) => ({ valor, texto }))} />
        </GrupoFiltro>
        <GrupoFiltro titulo="Fecha">
          <div className="pf-fechas">
            <label>
              Desde
              <input type="date" value={filtros.desde} max={filtros.hasta || undefined} onChange={(e) => f.poner({ desde: e.target.value, id: null })} />
            </label>
            <label>
              Hasta
              <input type="date" value={filtros.hasta} min={filtros.desde || undefined} onChange={(e) => f.poner({ hasta: e.target.value, id: null })} />
            </label>
          </div>
        </GrupoFiltro>
      </PanelFiltros>
      <ModalConfirmar abierto={reintentar} alCerrar={() => setReintentar(false)} titulo="Reintentar envío" subtitulo={det.datos?.asunto} busto="BU-rosa-laptop" textoConfirmar="Reintentar" iconoConfirmar={RotateCw} alConfirmar={hacerReintento}>
        <div className="caja-aviso">
          <div>
            <Send className="ico" aria-hidden />
            Se vuelve a enviar a {det.datos?.destino} con sus adjuntos
          </div>
        </div>
      </ModalConfirmar>
    </>
  );
}

function Recordatorios() {
  const toast = useToast();
  const f = useFiltrosUrl();
  const [version, setVersion] = useState(0);
  const estado = f.leer('estado');
  const lista = useCarga(() => notificacionesApi.recordatorios({ estado, page: f.pagina, size: 15, sort: 'enviarEn,desc' }), `${estado}-${f.pagina}-${version}`);
  const [ejecutar, setEjecutar] = useState(false);
  const [resultado, setResultado] = useState<ResultadoRecordatorios | null>(null);
  const correr = async () => {
    try {
      const r = await notificacionesApi.ejecutarRecordatorios();
      setResultado(r);
      toast.exito(`${r.enviados} enviados · ${r.revisados} revisados`);
      setEjecutar(false);
      setVersion((v) => v + 1);
    } catch {
      /* toast del interceptor */
    }
  };
  return (
    <>
      <div className="flex flex-wrap items-center gap-3 justify-between">
        <div className="pf-ops">
          {['', 'PROGRAMADO', 'ENVIADO', 'CANCELADO', 'OMITIDO'].map((e) => (
            <button key={e || 'todos'} type="button" className={cx('pf-op', estado === e && 'sel')} aria-pressed={estado === e} onClick={() => f.poner({ estado: e || null })}>
              {e ? e.charAt(0) + e.slice(1).toLowerCase() : 'Todos'}
            </button>
          ))}
        </div>
        <Boton icono={Play} onClick={() => setEjecutar(true)}>
          Ejecutar ahora
        </Boton>
      </div>
      {resultado && (
        <Alerta tipo="exito" titulo="Recordatorios ejecutados" alCerrar={() => setResultado(null)}>
          {resultado.revisados} revisados · {resultado.enviados} enviados · {resultado.omitidos} omitidos · {resultado.cancelados} cancelados
        </Alerta>
      )}
      {lista.error ? (
        <ErrorCarga alReintentar={lista.recargar} />
      ) : (
        <Tabla<Recordatorio>
          etiqueta="Recordatorios"
          filas={lista.datos?.contenido ?? []}
          claveFila={(r) => r.id}
          cargando={lista.cargando}
          vacio={lista.cargando ? 'Cargando…' : <Vacio ilustracion="IL-23-todo-al-dia" titulo="Sin recordatorios" compacto />}
          columnas={[
            { clave: 'codigo', titulo: 'Reserva', celda: (r) => <b className="tnum whitespace-nowrap">{r.codigoReserva}</b> },
            {
              clave: 'cliente',
              titulo: 'Cliente',
              celda: (r) => (
                <div>
                  <span className="block">{r.cliente}</span>
                  <span className="sub t-sec tnum">{r.telefono}</span>
                </div>
              ),
            },
            { clave: 'cancha', titulo: 'Partido', celda: (r) => <div><span className="block">{r.cancha}</span><span className="sub t-sec tnum">{fmtFechaHora(r.inicioReserva, { h24: true })}</span></div> },
            {
              clave: 'enviar',
              titulo: 'Se envía',
              celda: (r) => (
                <div>
                  <span className="block tnum">{fmtFechaHora(r.enviarEn, { h24: true, sinAnio: true })}</span>
                  {r.minutosAntes !== undefined && <span className="sub t-sec tnum">{fmtMinutosAntes(r.minutosAntes)} antes</span>}
                </div>
              ),
            },
            { clave: 'estado', titulo: 'Estado', celda: (r) => <Estado dominio="recordatorio" estado={r.estado} /> },
            { clave: 'detalle', titulo: 'Detalle', celda: (r) => <span className="celda-larga">{r.detalle ?? (r.enviadoEn ? `Enviado ${fmtFechaHora(r.enviadoEn, { h24: true, sinAnio: true })}` : '—')}</span> },
          ]}
          pie={lista.datos && <Paginacion pagina={lista.datos.pagina} totalPaginas={lista.datos.totalPaginas} totalElementos={lista.datos.totalElementos} tamanio={lista.datos.tamanio} alCambiar={(p) => f.poner({ page: p || null })} etiqueta="recordatorios" />}
        />
      )}
      <ModalConfirmar abierto={ejecutar} alCerrar={() => setEjecutar(false)} titulo="Ejecutar recordatorios" subtitulo="Corre ahora la tarea de cada minuto" busto="BU-rosa-laptop" textoConfirmar="Ejecutar" iconoConfirmar={Play} alConfirmar={correr}>
        <div className="caja-aviso">
          <div>
            <AlarmClock className="ico" aria-hidden />
            Envía los que ya llegaron a su hora
          </div>
          <div>
            <X className="ico" aria-hidden />
            Omite los que están a menos de 30 min del partido
          </div>
        </div>
      </ModalConfirmar>
    </>
  );
}

/**
 * Tarjeta ilustrada que acompaña al WhatsApp: `imagenTarjeta` del detalle (data URI) o, si no llega, la copia del JPG
 * en /public/whatsapp según el adjunto IMAGEN. Si la imagen no se pudo enviar (NO_DISPONIBLE) no se muestra.
 */
function imagenWa(n: { adjuntos: Adjunto[]; imagenTarjeta?: string | null }): string | null {
  const a = n.adjuntos.find((x) => x.tipo === 'IMAGEN');
  if (a?.estado === 'NO_DISPONIBLE') return null;
  if (n.imagenTarjeta) return n.imagenTarjeta;
  if (!a) return null;
  if (a.url && /^(https?:|\/)/.test(a.url)) return a.url;
  const base = (a.nombreArchivo || a.referencia || '').split(/[\\/]/).pop() ?? '';
  if (!base) return null;
  return `/whatsapp/${/\.(jpe?g|png|webp)$/i.test(base) ? base : `${base}.jpg`}`;
}
