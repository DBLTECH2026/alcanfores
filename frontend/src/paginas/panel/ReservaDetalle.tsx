import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowLeft,
  BadgeCheck,
  Ban,
  CalendarDays,
  CalendarPlus,
  Check,
  CircleCheck,
  CircleX,
  ClipboardPlus,
  Clock,
  Copy,
  FileCode,
  FileText,
  Hash,
  History,
  HandCoins,
  Hourglass,
  IdCard,
  Mail,
  MapPin,
  MessageCircle,
  NotebookPen,
  Phone,
  ReceiptText,
  Send,
  Timer,
  TriangleAlert,
  Upload,
  UserCheck,
  UserRound,
  UserX,
  Wallet,
  X,
  type LucideIcon,
} from 'lucide-react';
import { clientesApi, pagosAdminApi, reservasAdminApi } from '@/api/admin';
import type { FilaPago, ReservaAdmin } from '@/api/admin/tipos';
import { esErrorApi } from '@/api/cliente';
import { useCarga } from '@/hooks/useCarga';
import { useComplejo } from '@/hooks/useDatosPublicos';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { useToast } from '@/contexto/ToastContext';
import { usePanel } from '@/componentes/panel/PanelLayout';
import { ErrorCarga, Estado, ModalConfirmar } from '@/componentes/panel/Herramientas';
import { ICONO_METODO, ModalCancelar, ModalCobrar, ModalRechazar, ModalValidar, TEXTO_METODO, TEXTO_TIPO_PAGO } from '@/componentes/panel/ModalesReserva';
import { VisorComprobante } from '@/componentes/panel/VisorComprobante';
import { abrirArchivo, descargarArchivo } from '@/componentes/panel/archivos';
import { Boton, BotonEnlace, BotonExterno } from '@/componentes/ui/Boton';
import { Alerta, InsigniaEstado } from '@/componentes/ui/Estados';
import { Avatar, Cargando, Vacio } from '@/componentes/ui/Datos';
import { Busto, IconoDeporte } from '@/componentes/ui/Marca';
import { LineaTiempo, type Hito } from '@/componentes/ui/Reserva';
import { ahoraLima, deLima, fmtDuracion, fmtFecha, fmtFechaHora, fmtHace } from '@/utilidades/fecha';
import { horarioDeReserva } from '@/componentes/portal/EstadoReserva';
import { fmtCelular, fmtSoles } from '@/utilidades/formato';

const EVENTOS: Record<string, { titulo: string; icono: LucideIcon; estado?: Hito['estado'] }> = {
  REGISTRADA: { titulo: 'Registrada', icono: ClipboardPlus },
  COMPROBANTE_RECIBIDO: { titulo: 'Comprobante recibido', icono: Upload },
  PAGO_REGISTRADO: { titulo: 'Pago registrado', icono: Upload },
  OBSERVADO: { titulo: 'Comprobante observado', icono: TriangleAlert, estado: 'error' },
  PAGO_RECHAZADO: { titulo: 'Comprobante observado', icono: TriangleAlert, estado: 'error' },
  PAGO_VALIDADO: { titulo: 'Pago validado', icono: BadgeCheck },
  CONFIRMADA: { titulo: 'Confirmada', icono: CircleCheck },
  ASISTENCIA: { titulo: 'Asistencia', icono: UserCheck },
  ASISTIO: { titulo: 'Asistió', icono: UserCheck },
  NO_ASISTIO: { titulo: 'No asistió', icono: UserX, estado: 'error' },
  CANCELADA: { titulo: 'Cancelada', icono: CircleX, estado: 'cancelado' },
  RECORDATORIO: { titulo: 'Recordatorio', icono: Send },
  COMPROBANTE_EMITIDO: { titulo: 'Comprobante emitido', icono: ReceiptText },
};
const legible = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ');

function hitos(r: ReservaAdmin): Hito[] {
  const h: Hito[] = r.linea.map((e) => {
    const ev = EVENTOS[e.evento];
    const noAsistio = e.evento === 'ASISTENCIA' && /no asisti/i.test(e.detalle ?? '');
    return {
      titulo: noAsistio ? 'No asistió' : ev?.titulo ?? legible(e.evento),
      detalle: e.detalle,
      fecha: e.fecha,
      estado: noAsistio ? 'error' : ev?.estado ?? 'hecho',
      icono: noAsistio ? UserX : ev?.icono,
    };
  });
  if (r.estado === 'PENDIENTE' && r.estadoPago === 'EN_REVISION') h.push({ titulo: 'Por validar', detalle: 'Revisa el comprobante', estado: 'actual', icono: Hourglass });
  if (r.estado === 'PENDIENTE' && (r.estadoPago === 'SIN_PAGO' || r.estadoPago === 'OBSERVADO'))
    h.push({ titulo: 'Esperando pago', detalle: r.venceEn ? `Vence ${fmtFechaHora(r.venceEn, { h24: true, sinAnio: true })}` : null, estado: 'actual', icono: Wallet });
  if (r.estado === 'CONFIRMADA' && r.asistencia === 'PENDIENTE') h.push({ titulo: 'Asistencia', detalle: 'Por marcar', estado: 'pendiente', icono: UserCheck });
  return h;
}

/** Detalle de una reserva (prototipo adm-11): turno, montos, asistencia, cliente, historial y pagos con el visor. */
export default function ReservaDetalle({ codigo }: { codigo: string }) {
  useTituloPagina(codigo);
  const toast = useToast();
  const { refrescarContador } = usePanel();
  const complejo = useComplejo();
  const [version, setVersion] = useState(0);
  const r = useCarga(() => reservasAdminApi.detalle(codigo), `${codigo}-${version}`);
  const pagos = useCarga(() => pagosAdminApi.listar({ q: codigo, size: 20, sort: 'fechaEnvio,desc' }), `p-${codigo}-${version}`);
  const dni = r.datos?.cliente.dni ?? null;
  const cliente = useCarga(() => clientesApi.historial(dni ?? ''), dni ? `c-${dni}-${version}` : null);
  const [modal, setModal] = useState<null | 'validar' | 'rechazar' | 'cobrar' | 'cancelar' | 'ASISTIO' | 'NO_ASISTIO'>(null);
  const [idPago, setIdPago] = useState<number | null>(null);

  const recargar = () => {
    setModal(null);
    setVersion((v) => v + 1);
    refrescarContador();
  };

  if (r.error) {
    const noExiste = esErrorApi(r.error) && r.error.status === 404;
    return noExiste ? (
      <div className="card">
        <Vacio ilustracion="IL-17-no-encontrada" titulo="Reserva no encontrada" texto={codigo} acciones={<BotonEnlace to="/admin/reservas" variante="secundario" icono={ArrowLeft}>Volver a reservas</BotonEnlace>} />
      </div>
    ) : (
      <ErrorCarga alReintentar={r.recargar} />
    );
  }
  if (!r.datos) return <Cargando texto="Cargando reserva…" />;
  const res = r.datos;
  const filasPago = (pagos.datos?.contenido ?? []).filter((p) => p.codigoReserva === res.codigoReserva);
  const pendiente = filasPago.find((p) => p.estado === 'PENDIENTE');
  const conImagen = pendiente ?? filasPago.find((p) => p.tieneArchivo);
  const inicio = deLima(`${res.fecha}T${res.horaInicio}:00`);
  const minutos = inicio.diff(ahoraLima(), 'minute');
  const enJuego = minutos <= 0 && deLima(`${res.fecha}T${res.horaFin}:00`).diff(ahoraLima(), 'minute') > 0;
  const nombre = `${res.cliente.nombres} ${res.cliente.apellidos}`;
  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(res.codigoReserva);
      toast.exito('Código copiado');
    } catch {
      toast.info(res.codigoReserva);
    }
  };
  const marcarAsistencia = async (a: 'ASISTIO' | 'NO_ASISTIO') => {
    try {
      await reservasAdminApi.asistencia(res.codigoReserva, a);
      toast.exito(a === 'ASISTIO' ? 'Asistencia registrada' : 'No asistió registrado');
      recargar();
    } catch {
      /* toast del interceptor */
    }
  };

  return (
    <>
      {/* cabecera con código e insignias */}
      <div className="ma-encabezado">
        <div className="ma-encabezado-txt">
          <Link to="/admin/reservas" className="cab-volver" aria-label="Volver a reservas">
            <ArrowLeft className="ico ico-18" aria-hidden />
          </Link>
          <Busto id="BU-rosa-panel" />
          <div>
            <h1 className="t-h1 flex items-center gap-2 tnum tracking-[.02em]">
              {res.codigoReserva}
              <button type="button" className="td-mas" aria-label="Copiar código" onClick={copiar}>
                <Copy className="ico" aria-hidden />
              </button>
            </h1>
            <div className="flex flex-wrap gap-1.5">
              <InsigniaEstado dominio="reserva" estado={res.estado} />
              <InsigniaEstado dominio="estadoPago" estado={res.estadoPago} />
              <InsigniaEstado dominio="origen" estado={res.origen} />
            </div>
          </div>
        </div>
        <div className="grupo-botones">
          <Boton variante="secundario" icono={FileText} onClick={() => void abrirArchivo(reservasAdminApi.rutaConstancia(res.codigoReserva))}>
            Constancia
          </Boton>
          {res.puedeCobrarSaldo && (
            <Boton variante={pendiente ? 'secundario' : 'primario'} icono={HandCoins} onClick={() => setModal('cobrar')}>
              Cobrar en caja
            </Boton>
          )}
          {res.puedeCancelar && (
            <Boton variante="peligro-contorno" icono={Ban} onClick={() => setModal('cancelar')}>
              Cancelar reserva
            </Boton>
          )}
        </div>
      </div>

      <div className="grid-det">
        <div className="grid gap-4 min-w-0">
          {/* turno + montos */}
          <section className="pc">
            <div className="det-turno">
              <span className="td-ico">
                <IconoDeporte deporte={res.cancha.deporte} className="ico" />
              </span>
              <div className="grid gap-1.5">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="det-hora">{horarioDeReserva(res, true)}</span>
                  {res.estado !== 'CANCELADA' && (
                    <span className="badge b-menta badge-sm">
                      <Timer className="ico" aria-hidden />
                      <span>{enJuego ? 'En juego' : minutos > 0 ? `En ${fmtDuracion(minutos)}` : 'Jugado'}</span>
                    </span>
                  )}
                </div>
                <div className="flex flex-wrap gap-x-4 gap-y-1 text-[13.5px] text-texto-suave">
                  <span className="dato-ico">
                    <CalendarDays className="ico" aria-hidden />
                    {fmtFecha(res.fecha, 'larga-anio')}
                  </span>
                  <span className="dato-ico">
                    <MapPin className="ico" aria-hidden />
                    {res.cancha.nombre} · {res.franja === 'NOCHE' ? 'noche' : 'día'}
                  </span>
                </div>
              </div>
            </div>
            <div className="montos3">
              <div>
                <span>
                  <ReceiptText className="ico" aria-hidden />
                  Total
                </span>
                <b>{fmtSoles(res.montoTotal)}</b>
              </div>
              <div>
                <span>
                  <Wallet className="ico" aria-hidden />
                  Pagado
                </span>
                <b className="verde">{fmtSoles(res.montoPagado)}</b>
              </div>
              <div>
                <span>
                  <HandCoins className="ico" aria-hidden />
                  Saldo
                </span>
                <b className={res.saldo > 0 && res.estado !== 'CANCELADA' ? '' : 'verde'}>{fmtSoles(res.saldo)}</b>
              </div>
            </div>
          </section>

          {res.cancelacion && (
            <Alerta tipo="error" titulo={`Cancelada por ${legible(res.cancelacion.canceladoPor).toLowerCase()} · ${fmtFechaHora(res.cancelacion.fecha, { h24: true })}`}>
              {res.cancelacion.motivo}
              {res.cancelacion.devolucion ? ` · Devolución ${fmtSoles(res.cancelacion.montoDevolucion)}` : ' · Sin devolución'}
            </Alerta>
          )}

          {/* asistencia */}
          {res.estado === 'CONFIRMADA' && (
            <section className="pc">
              <div className="pc-cab">
                <h2>
                  <UserCheck className="ico" aria-hidden />
                  Asistencia
                </h2>
                <div className="pc-der">
                  <InsigniaEstado dominio="asistencia" estado={res.asistencia} />
                </div>
              </div>
              <div className="pc-cuerpo grid gap-2">
                {res.asistencia === 'PENDIENTE' ? (
                  <>
                    <div className="flex flex-wrap gap-2">
                      <Boton variante={enJuego ? 'oscuro' : 'secundario'} icono={UserCheck} disabled={!res.puedeMarcarAsistencia} onClick={() => setModal('ASISTIO')}>
                        Asistió
                      </Boton>
                      <Boton variante="secundario" icono={UserX} disabled={!res.puedeMarcarAsistencia} onClick={() => setModal('NO_ASISTIO')}>
                        No asistió
                      </Boton>
                    </div>
                    {!res.puedeMarcarAsistencia && (
                      <span className="dato-ico">
                        <Clock className="ico" aria-hidden />
                        Desde las {inicio.subtract(15, 'minute').format('HH:mm')} del {fmtFecha(res.fecha, 'dia-mes')}
                      </span>
                    )}
                  </>
                ) : (
                  <span className="t-sec">Registrada. El cliente {res.asistencia === 'ASISTIO' ? 'recibió la invitación para calificar la cancha.' : 'no se presentó.'}</span>
                )}
              </div>
            </section>
          )}

          {/* cliente */}
          <section className="pc">
            <div className="pc-cab">
              <h2>
                <UserRound className="ico" aria-hidden />
                Cliente
              </h2>
              {cliente.datos && (
                <span className="pc-der dato-ico">
                  <CalendarPlus className="ico" aria-hidden />
                  Desde {fmtFecha(cliente.datos.cliente.fechaRegistro.slice(0, 10), 'dia-mes-anio')}
                </span>
              )}
            </div>
            <div className="pc-cuerpo grid gap-3">
              <div className="flex items-center gap-3">
                <Avatar nombre={nombre} tamano="lg" />
                <div className="min-w-0 flex-1">
                  <b className="block text-[16px]">{nombre}</b>
                  <span className="t-sec tnum">{fmtCelular(res.cliente.telefono, { prefijo: true })}</span>
                </div>
                <BotonExterno href={`tel:+51${res.cliente.telefono}`} variante="secundario" tamano="sm" icono={Phone} etiqueta="Llamar" />
                <BotonExterno href={`https://wa.me/51${res.cliente.telefono}`} target="_blank" variante="whatsapp" tamano="sm" icono={MessageCircle} etiqueta="WhatsApp" />
              </div>
              <div className="flex flex-wrap gap-x-5 gap-y-2">
                <span className="dato-ico">
                  <Mail className="ico" aria-hidden />
                  {res.cliente.correo}
                </span>
                <span className="dato-ico">
                  <IdCard className="ico" aria-hidden />
                  DNI {res.cliente.dni}
                  {res.cliente.verificadoReniec && <BadgeCheck className="ico !text-verde" aria-label="Verificado en RENIEC" />}
                </span>
              </div>
              {cliente.datos && (
                <div className="mini-kpis">
                  <Link to={`/admin/clientes/${res.cliente.dni}`} className="mini-kpi no-underline">
                    <CalendarDays className="ico" aria-hidden />
                    <b>{cliente.datos.cliente.totalReservas}</b> reservas
                  </Link>
                  <div className={cliente.datos.cliente.noShows > 0 ? 'mini-kpi rojo' : 'mini-kpi'}>
                    <UserX className="ico" aria-hidden />
                    <b>{cliente.datos.cliente.noShows}</b> no asistió
                  </div>
                </div>
              )}
              {res.nota && (
                <span className="dato-ico">
                  <NotebookPen className="ico" aria-hidden />
                  {res.nota}
                </span>
              )}
            </div>
          </section>

          {/* historial */}
          <section className="pc">
            <div className="pc-cab">
              <h2>
                <History className="ico" aria-hidden />
                Historial
              </h2>
              <span className="pc-der dato-ico">Registrada {fmtFechaHora(res.fechaRegistro, { h24: true })}</span>
            </div>
            <div className="pc-cuerpo">
              <LineaTiempo hitos={hitos(res)} />
            </div>
          </section>
        </div>

        {/* pagos y comprobante */}
        <aside className="grid gap-4 min-w-0">
          <section className="pc">
            <div className="pc-cab">
              <h2>
                <ReceiptText className="ico" aria-hidden />
                Comprobante
              </h2>
              {conImagen && (
                <div className="pc-der">
                  <Estado dominio="pago" estado={conImagen.estado} />
                </div>
              )}
            </div>
            <div className="pc-cuerpo grid gap-3">
              {pagos.cargando && !pagos.datos ? (
                <Cargando texto="Cargando pagos…" className="!py-8" />
              ) : !conImagen ? (
                <Vacio
                  ilustracion="IL-04-paso3-comprobante"
                  titulo={filasPago.length ? 'Pago sin imagen' : 'Sin comprobante'}
                  texto={res.estado === 'PENDIENTE' ? 'El cliente aún no envía su comprobante.' : undefined}
                  compacto
                  acciones={res.puedeCobrarSaldo ? <Boton variante="secundario" icono={HandCoins} onClick={() => setModal('cobrar')}>Cobrar en caja</Boton> : undefined}
                />
              ) : (
                <>
                  {conImagen.tieneArchivo && <VisorComprobante idPago={conImagen.id} leyenda={`${TEXTO_METODO[conImagen.metodo] ?? conImagen.metodo} · ${conImagen.codigoOperacion ?? ''}`} />}
                  <DatosPago pago={conImagen} esperado={conImagen.tipoPago === 'TOTAL' ? res.montoTotal : res.montoAdelanto} />
                  {conImagen.estado === 'PENDIENTE' && (
                    <div className="grid grid-cols-2 gap-2">
                      <Boton
                        variante="peligro-contorno"
                        icono={X}
                        onClick={() => {
                          setIdPago(conImagen.id);
                          setModal('rechazar');
                        }}
                      >
                        Rechazar
                      </Boton>
                      <Boton
                        icono={Check}
                        onClick={() => {
                          setIdPago(conImagen.id);
                          setModal('validar');
                        }}
                      >
                        Validar
                      </Boton>
                    </div>
                  )}
                </>
              )}
            </div>
          </section>

          {filasPago.length > 0 && (
            <section className="pc">
              <div className="pc-cab">
                <h2>
                  <Wallet className="ico" aria-hidden />
                  Pagos
                </h2>
                <span className="pc-der dato-ico">{filasPago.length}</span>
              </div>
              <ul className="grid">
                {filasPago.map((p) => {
                  const I = ICONO_METODO[p.metodo] ?? Wallet;
                  return (
                    <li key={p.id} className="flex items-center gap-3 px-4 py-3 border-t border-[#EEF2F0] first:border-t-0">
                      <span className="td-ico">
                        <I className="ico" aria-hidden />
                      </span>
                      <div className="min-w-0 flex-1">
                        <b className="tnum">{fmtSoles(p.monto)}</b> <span className="t-sec">· {TEXTO_TIPO_PAGO[p.tipoPago]} · {TEXTO_METODO[p.metodo] ?? p.metodo}</span>
                        <span className="block t-sec text-[12px]">{fmtFechaHora(p.fechaValidacion ?? p.fechaEnvio, { h24: true })}{p.validadoPor ? ` · ${p.validadoPor}` : ''}</span>
                      </div>
                      <Estado dominio="pago" estado={p.estado} tamano="sm" />
                      {p.comprobante && (
                        <>
                          <Boton variante="fantasma" tamano="sm" icono={FileText} etiqueta={`PDF ${p.comprobante.serieNumero}`} onClick={() => p.comprobante && void abrirArchivo(p.comprobante.urlPdf)} />
                          <Boton variante="fantasma" tamano="sm" icono={FileCode} etiqueta={`XML ${p.comprobante.serieNumero}`} onClick={() => p.comprobante && void descargarArchivo(p.comprobante.urlXml, `${p.comprobante.serieNumero}.xml`)} />
                        </>
                      )}
                    </li>
                  );
                })}
              </ul>
            </section>
          )}
        </aside>
      </div>

      <ModalValidar idPago={modal === 'validar' ? idPago : null} alCerrar={() => setModal(null)} alHecho={recargar} />
      <ModalRechazar idPago={modal === 'rechazar' ? idPago : null} alCerrar={() => setModal(null)} alHecho={recargar} />
      <ModalCobrar reserva={modal === 'cobrar' ? res : null} alCerrar={() => setModal(null)} alHecho={() => { setVersion((v) => v + 1); refrescarContador(); }} />
      <ModalCancelar reserva={modal === 'cancelar' ? res : null} devolucionHoras={complejo.datos?.reglas.devolucionHoras} alCerrar={() => setModal(null)} alHecho={recargar} />
      <ModalConfirmar
        abierto={modal === 'ASISTIO' || modal === 'NO_ASISTIO'}
        alCerrar={() => setModal(null)}
        titulo={modal === 'ASISTIO' ? 'Marcar asistencia' : 'Marcar no asistió'}
        subtitulo={`${res.codigoReserva} · ${nombre}`}
        franja={[['Turno', horarioDeReserva(res, true)], ['Cancha', res.cancha.nombre.split(' · ')[0] ?? ''], ['Saldo', fmtSoles(res.saldo)]]}
        textoConfirmar={modal === 'ASISTIO' ? 'Asistió' : 'No asistió'}
        iconoConfirmar={modal === 'ASISTIO' ? UserCheck : UserX}
        peligro={modal === 'NO_ASISTIO'}
        alConfirmar={() => marcarAsistencia(modal === 'ASISTIO' ? 'ASISTIO' : 'NO_ASISTIO')}
      >
        <div className="caja-aviso">
          {modal === 'ASISTIO' ? (
            <>
              <div>
                <Send className="ico" aria-hidden />
                Le llega la invitación para calificar la cancha
              </div>
              {res.saldo > 0 && (
                <div>
                  <HandCoins className="ico" aria-hidden />
                  Recuerda cobrar el saldo de {fmtSoles(res.saldo)}
                </div>
              )}
            </>
          ) : (
            <div>
              <UserX className="ico" aria-hidden />
              Cuenta como no-show en su historial
            </div>
          )}
        </div>
      </ModalConfirmar>
    </>
  );
}

function DatosPago({ pago, esperado }: { pago: FilaPago; esperado: number }) {
  const toast = useToast();
  const I = ICONO_METODO[pago.metodo] ?? Wallet;
  const coincide = Math.abs(esperado - pago.monto) < 0.01 || pago.tipoPago === 'SALDO';
  return (
    <div className="filas-dato">
      <div>
        <span className="td-ico">
          <I className="ico" aria-hidden />
        </span>
        {TEXTO_METODO[pago.metodo] ?? pago.metodo} · {TEXTO_TIPO_PAGO[pago.tipoPago]}
      </div>
      <div>
        <span className="td-ico">
          <Wallet className="ico" aria-hidden />
        </span>
        <b className="tnum">{fmtSoles(pago.monto)}</b>
        <span className="der">
          {coincide ? (
            <span className="badge b-menta badge-sm">
              <Check className="ico" aria-hidden />
              <span>Coincide</span>
            </span>
          ) : (
            <span className="badge b-rojo badge-sm">
              <TriangleAlert className="ico" aria-hidden />
              <span>Esperado {fmtSoles(esperado)}</span>
            </span>
          )}
        </span>
      </div>
      {pago.codigoOperacion && (
        <div>
          <span className="td-ico">
            <Hash className="ico" aria-hidden />
          </span>
          <span className="tnum">{pago.codigoOperacion}</span>
          <button
            type="button"
            className="td-mas ml-auto"
            aria-label="Copiar número de operación"
            onClick={() => {
              void navigator.clipboard?.writeText(pago.codigoOperacion ?? '').then(() => toast.exito('Copiado'), () => undefined);
            }}
          >
            <Copy className="ico" aria-hidden />
          </button>
        </div>
      )}
      <div>
        <span className="td-ico">
          <Clock className="ico" aria-hidden />
        </span>
        {fmtFechaHora(pago.fechaEnvio, { h24: true })}
        <span className="der">{fmtHace(pago.fechaEnvio)}</span>
      </div>
      {pago.validadoPor && (
        <div>
          <span className="td-ico">
            <UserCheck className="ico" aria-hidden />
          </span>
          {pago.validadoPor}
          <span className="der">{pago.fechaValidacion ? fmtFechaHora(pago.fechaValidacion, { h24: true, sinAnio: true }) : ''}</span>
        </div>
      )}
    </div>
  );
}

