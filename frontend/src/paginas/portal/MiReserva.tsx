import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  ArrowLeft,
  CalendarDays,
  CalendarX,
  CircleX,
  CreditCard,
  Download,
  Eye,
  FileCode2,
  FileText,
  Fingerprint,
  HandCoins,
  Hash,
  History,
  Info,
  Receipt,
  Search,
  Smartphone,
  Star,
  Ticket,
  TimerReset,
  Upload,
  UserRound,
  Banknote,
  Building2,
  MapPin,
  Store,
  Wallet,
} from 'lucide-react';
import { documentosApi, pagosApi, reservasApi } from '@/api/servicios';
import { esErrorApi, urlApi } from '@/api/cliente';
import type { ComprobanteEnlace, DatosComprobante, PagoCliente, ReservaDetalle } from '@/api/tipos';
import { guardarFacturacion, guardarUltimaReserva, leerFacturacion } from '@/contexto/ReservaContext';
import { useToast } from '@/contexto/ToastContext';
import { useCarga } from '@/hooks/useCarga';
import { useComplejo, useMetodosPago } from '@/hooks/useDatosPublicos';
import { useCuentaRegresiva } from '@/hooks/useCuentaRegresiva';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { Boton, BotonEnlace, BotonExterno } from '@/componentes/ui/Boton';
import { AreaTexto, CampoTexto } from '@/componentes/ui/Campo';
import { Alerta, InsigniaEstado } from '@/componentes/ui/Estados';
import { Cargando, Segmentado } from '@/componentes/ui/Datos';
import { Ilustracion, IconoDeporte } from '@/componentes/ui/Marca';
import { Modal } from '@/componentes/ui/Modal';
import { CodigoReserva, LineaTiempo } from '@/componentes/ui/Reserva';
import { SubidaImagen } from '@/componentes/ui/SubidaImagen';
import { HeroEstado, estadoCliente, hitosDeReserva, horarioDeReserva } from '@/componentes/portal/EstadoReserva';
import { esquemaCancelacion, esquemaConsulta, type ConsultaEntrada, type DatosCancelacion } from '@/esquemas/otros';
import { esquemaReenvio, type DatosReenvio, type ReenvioEntrada } from '@/esquemas/reserva';
import { rucValido } from '@/utilidades/documentos';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { ahoraLima, deLima, fmtDuracion, fmtFecha, fmtFechaHora } from '@/utilidades/fecha';
import { enmascararCelular, fmtSoles, nombreConDeporte, normalizarCodigo } from '@/utilidades/formato';

const CLAVE = 'alc-mi-reserva';
interface Consulta {
  codigo: string;
  dni: string;
}
function leerConsulta(): Consulta | null {
  try {
    return JSON.parse(sessionStorage.getItem(CLAVE) ?? 'null') as Consulta | null;
  } catch {
    return null;
  }
}

// ------------------------------ Consulta ------------------------------
function FormConsulta({ codigoInicial, alEncontrar }: { codigoInicial: string; alEncontrar: (c: Consulta, r: ReservaDetalle) => void }) {
  const [error, setError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError: setErrorCampo,
    formState: { errors, isSubmitting },
  } = useForm<ConsultaEntrada>({ resolver: zodResolver(esquemaConsulta), mode: 'onTouched', defaultValues: { codigo: codigoInicial, dni: '' } });

  const buscar = handleSubmit(async (d) => {
    setError(null);
    try {
      const r = await reservasApi.detalle(d.codigo, d.dni);
      alEncontrar({ codigo: d.codigo, dni: d.dni }, r);
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setErrorCampo, ['codigo', 'dni'], { codigoReserva: 'codigo' });
      if (!aplicados) setError(esErrorApi(e) && e.status === 404 ? 'No encontramos una reserva con ese código y DNI.' : esErrorApi(e) ? e.message : 'No se pudo consultar. Intenta de nuevo.');
    }
  });

  return (
    <div className="contenedor py-8 lg:py-14">
      <div className="grid gap-8 lg:grid-cols-[1fr_440px] lg:items-center lg:gap-16 max-w-[1040px] mx-auto">
        <div className="grid gap-3 justify-items-center text-center lg:justify-items-start lg:text-left">
          <Ilustracion id={error ? 'IL-17-no-encontrada' : 'IL-16-consulta'} carga="eager" className="w-[180px] h-[180px] lg:w-[320px] lg:h-[320px]" />
          <span className="kicker kicker-raya">Mi reserva</span>
          <h1 className="t-h1">Consulta tu reserva</h1>
          <p className="t-sec">Estado, pagos, comprobantes y cancelación.</p>
        </div>
        <form onSubmit={buscar} noValidate className="card card-pad grid gap-4">
          <CampoTexto
            label="Código"
            requerido
            icono={Ticket}
            placeholder="ALC-XXXXXX"
            autoComplete="off"
            maxLength={10}
            className="uppercase-input"
            ayuda="Llegó a tu correo y WhatsApp"
            error={errors.codigo?.message}
            {...register('codigo', { setValueAs: (v: string) => normalizarCodigo(v ?? '') })}
          />
          <CampoTexto label="DNI" requerido icono={Fingerprint} inputMode="numeric" maxLength={8} autoComplete="off" error={errors.dni?.message} {...register('dni')} />
          {error && <Alerta tipo="error">{error}</Alerta>}
          <Boton type="submit" icono={Search} tamano="lg" bloque cargando={isSubmitting} textoCargando="Buscando…">
            Consultar
          </Boton>
        </form>
      </div>
    </div>
  );
}

// ------------------------------ Pagos ------------------------------
const TIPO_COMPROBANTE: Record<ComprobanteEnlace['tipo'], string> = { BOLETA: 'Boleta', FACTURA: 'Factura', NOTA_CREDITO: 'Nota de crédito' };

/** Boleta, factura o nota de crédito con sus descargas (el back ya manda las URL con código y DNI). */
function FilaComprobante({ c, codigo, dni }: { c: ComprobanteEnlace; codigo: string; dni: string }) {
  return (
    <div className="flex flex-wrap items-center gap-2 pl-[52px]">
      <span className="inline-flex items-center gap-1.5 text-[13px] font-semibold">
        <Receipt className={`ico ico-16 ${c.estado === 'ANULADO' ? 'text-gris' : 'text-verde'}`} aria-hidden />
        {`${TIPO_COMPROBANTE[c.tipo] ?? 'Comprobante'} ${c.serieNumero}`}
      </span>
      {c.estado === 'ANULADO' && <span className="t-mini">(anulada)</span>}
      <BotonExterno href={urlApi(c.urlPdf || pagosApi.urlComprobante(c.serieNumero, codigo, dni, 'pdf'))} target="_blank" variante="secundario" tamano="sm" icono={FileText}>
        PDF
      </BotonExterno>
      <BotonExterno href={urlApi(c.urlXml || pagosApi.urlComprobante(c.serieNumero, codigo, dni, 'xml'))} target="_blank" variante="fantasma" tamano="sm" icono={FileCode2}>
        XML
      </BotonExterno>
    </div>
  );
}

function ListaPagos({ pagos, codigo, dni }: { pagos: PagoCliente[]; codigo: string; dni: string }) {
  if (!pagos.length) {
    return <p className="t-sec px-[18px] pb-[18px]">Aún no hay pagos registrados.</p>;
  }
  return (
    <ul className="grid">
      {pagos.map((p, i) => (
        <li key={p.idPago ?? `${p.fecha}-${i}`} className="grid gap-2 px-[18px] py-3.5 border-t border-borde first:border-t-0">
          <div className="flex items-start gap-3">
            <span className="grid place-items-center w-10 h-10 rounded-12 bg-superficie-suave text-noche shrink-0">
              {p.metodo === 'TARJETA' ? <CreditCard className="ico ico-18" aria-hidden /> : p.metodo === 'EFECTIVO' ? <Banknote className="ico ico-18" aria-hidden /> : <Smartphone className="ico ico-18" aria-hidden />}
            </span>
            <div className="grid gap-1 min-w-0 flex-1">
              <span className="flex flex-wrap items-center gap-2">
                <b className="text-[14.5px] tnum">{`${p.metodo.replace('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())} · ${fmtSoles(p.monto)}`}</b>
                <InsigniaEstado dominio="pago" estado={p.estado} tamano="sm" />
              </span>
              <span className="t-mini tnum">{`${p.tipoPago === 'ADELANTO' ? 'Adelanto' : p.tipoPago === 'TOTAL' ? 'Total' : 'Saldo'} · ${fmtFechaHora(p.fecha, { sinAnio: true })}`}</span>
            </div>
          </div>
          {p.observacion && p.estado === 'RECHAZADO' && (
            <section className="observacion" role="note">
              <div className="observacion-cab">
                <span className="tarjeta-roja" aria-hidden />
                <span>Motivo</span>
              </div>
              <div className="observacion-texto">{`«${p.observacion}»`}</div>
            </section>
          )}
          {p.observacion && p.estado === 'REEMBOLSADO' && <p className="t-mini pl-[52px]">{p.observacion}</p>}
          {[p.comprobante, p.notaCredito].map((c) => c && <FilaComprobante key={c.serieNumero} c={c} codigo={codigo} dni={dni} />)}
        </li>
      ))}
    </ul>
  );
}

// ------------------------------ Modales ------------------------------
function ModalCancelar({ abierto, alCerrar, reserva, dni, devolucionHoras, alCancelar }: { abierto: boolean; alCerrar: () => void; reserva: ReservaDetalle; dni: string; devolucionHoras: number; alCancelar: (r: ReservaDetalle) => void }) {
  const toast = useToast();
  const [error, setErrorGeneral] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    watch,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<DatosCancelacion>({ resolver: zodResolver(esquemaCancelacion), mode: 'onTouched', defaultValues: { motivo: '' } });
  const motivo = watch('motivo') ?? '';
  const horas = deLima(`${reserva.fecha}T${reserva.horaInicio}:00`).diff(ahoraLima(), 'minute');
  const corresponde = horas >= devolucionHoras * 60 && reserva.montoPagado > 0;

  const cancelar = handleSubmit(async (d) => {
    setErrorGeneral(null);
    try {
      const r = await reservasApi.cancelar(reserva.codigoReserva, dni, d.motivo.trim());
      toast.exito('Reserva cancelada. Horario libre.');
      reset();
      alCancelar(r);
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['motivo']);
      if (!aplicados) setErrorGeneral(esErrorApi(e) ? e.message : 'No se pudo cancelar.');
    }
  });

  return (
    <Modal
      abierto={abierto}
      alCerrar={alCerrar}
      titulo="¿Cancelar tu reserva?"
      subtitulo={`${reserva.cancha.nombre} · ${fmtFecha(reserva.fecha)} · ${horarioDeReserva(reserva)}`}
      icono={CalendarX}
      tono="peligro"
      variante="hoja"
      acciones={
        <>
          <Boton variante="secundario" onClick={alCerrar}>
            No, mantener
          </Boton>
          <Boton variante="peligro" icono={CircleX} type="submit" form="form-cancelar" cargando={isSubmitting} textoCargando="Cancelando…">
            Sí, cancelar
          </Boton>
        </>
      }
    >
      <form id="form-cancelar" onSubmit={cancelar} noValidate className="grid gap-4">
        <div className={`devolucion !m-0 ${corresponde ? 'corresponde' : 'no-corresponde'}`}>
          {corresponde ? <HandCoins className="ico" aria-hidden /> : <Info className="ico" aria-hidden />}
          <span>
            {reserva.montoPagado <= 0
              ? 'No hay pagos que devolver.'
              : corresponde
                ? `Faltan ${fmtDuracion(horas)}: te devolvemos ${fmtSoles(reserva.montoPagado)}.`
                : `Faltan menos de ${devolucionHoras} h: lo pagado no se devuelve.`}
          </span>
        </div>
        <AreaTexto label="Motivo" requerido rows={3} maxLength={255} placeholder="Ej.: se suspendió el partido por lluvia" contador={{ actual: motivo.length, max: 255 }} error={errors.motivo?.message} data-autofoco {...register('motivo')} />
        {error && <Alerta tipo="error">{error}</Alerta>}
      </form>
    </Modal>
  );
}

/** Última boleta o factura pedida para la reserva según pagos (sirve aunque la reserva se haya hecho en otro navegador). */
function facturacionPedida(pagos: PagoCliente[] | null | undefined): DatosComprobante | null {
  const f = [...(pagos ?? [])].reverse().find((p) => p.facturacion)?.facturacion;
  if (!f) return null;
  return f.tipo === 'FACTURA'
    ? { tipo: 'FACTURA', ruc: f.ruc ?? '', razonSocial: f.razonSocial ?? '', direccionFiscal: f.direccionFiscal ?? '' }
    : { tipo: 'BOLETA' };
}

function ModalReenviar({ abierto, alCerrar, reserva, dni, alEnviar, pedida }: { abierto: boolean; alCerrar: () => void; reserva: ReservaDetalle; dni: string; alEnviar: () => void; pedida: DatosComprobante | null }) {
  const toast = useToast();
  const metodos = useMetodosPago();
  const { datos: complejo } = useComplejo();
  const [progreso, setProgreso] = useState<number | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    control,
    watch,
    setError,
    clearErrors,
    formState: { errors, isSubmitting },
    setValue,
  } = useForm<ReenvioEntrada>({
    resolver: zodResolver(esquemaReenvio),
    mode: 'onTouched',
    defaultValues: (() => {
      const f = leerFacturacion(reserva.codigoReserva) ?? pedida;
      return {
        metodo: reserva.metodoPagoPrevisto === 'PLIN' ? 'PLIN' : 'YAPE',
        codigoOperacion: '',
        archivo: null,
        tipoComprobante: f?.tipo ?? 'BOLETA',
        ruc: f?.ruc ?? '',
        razonSocial: f?.razonSocial ?? '',
        direccionFiscal: f?.direccionFiscal ?? '',
      };
    })(),
  });
  const metodo = watch('metodo');
  const tipoComprobante = watch('tipoComprobante');
  const [rucConsultado, setRucConsultado] = useState('');
  const ruc = watch('ruc') ?? '';
  const rucPedido = pedida?.ruc;
  // Con 11 dígitos válidos trae razón social y dirección de SUNAT (si no responde, se escriben a mano).
  useEffect(() => {
    if (tipoComprobante !== 'FACTURA' || ruc === rucConsultado || !/^\d{11}$/.test(ruc) || !rucValido(ruc)) return;
    if ((leerFacturacion(reserva.codigoReserva)?.ruc ?? rucPedido) === ruc && rucConsultado === '') {
      setRucConsultado(ruc);
      return;
    }
    setRucConsultado(ruc);
    documentosApi
      .ruc(ruc)
      .then((d) => {
        setValue('razonSocial', d.razonSocial, { shouldValidate: true });
        setValue('direccionFiscal', d.direccion, { shouldValidate: true });
      })
      .catch(() => undefined);
  }, [ruc, rucConsultado, tipoComprobante, reserva.codigoReserva, rucPedido, setValue]);
  const numero = metodo === 'PLIN' ? metodos.datos?.plin.numero ?? complejo?.pagos.plinNumero : metodos.datos?.yape.numero ?? complejo?.pagos.yapeNumero;
  const monto = Math.max(0, reserva.montoAdelanto - reserva.montoPagado) || reserva.montoAdelanto;

  const enviar = handleSubmit(async (entrada) => {
    // zodResolver entrega los datos ya transformados (valores por defecto aplicados).
    const d = entrada as DatosReenvio;
    setAviso(null);
    try {
      setProgreso(0);
      const comprobante: DatosComprobante =
        d.tipoComprobante === 'FACTURA' ? { tipo: 'FACTURA', ruc: d.ruc, razonSocial: d.razonSocial, direccionFiscal: d.direccionFiscal } : { tipo: 'BOLETA' };
      await pagosApi.enviarComprobante({ codigoReserva: reserva.codigoReserva, dni, metodo: d.metodo, codigoOperacion: d.codigoOperacion, archivo: d.archivo as File, comprobante }, setProgreso);
      guardarFacturacion(reserva.codigoReserva, comprobante);
      toast.exito('Comprobante enviado');
      alEnviar();
    } catch (e) {
      setProgreso(null);
      if (esErrorApi(e) && e.codigo === 'OPERACION_REPETIDA') {
        setError('codigoOperacion', { type: 'servidor', message: e.message }, { shouldFocus: true });
        return;
      }
      const { aplicados } = aplicarErroresServidor(e, setError, ['metodo', 'codigoOperacion', 'archivo', 'ruc', 'razonSocial', 'direccionFiscal']);
      if (!aplicados) setAviso(esErrorApi(e) ? e.message : 'No se pudo enviar.');
    }
  });

  return (
    <Modal
      abierto={abierto}
      alCerrar={alCerrar}
      titulo={reserva.estadoPago === 'OBSERVADO' ? 'Reenvía tu comprobante' : 'Envía tu comprobante'}
      subtitulo={`Monto: ${fmtSoles(monto)} · ${reserva.codigoReserva}`}
      icono={Upload}
      variante="hoja"
      tamano="lg"
      acciones={
        <>
          <Boton variante="secundario" onClick={alCerrar}>
            Cancelar
          </Boton>
          <Boton type="submit" form="form-reenvio" icono={Upload} cargando={isSubmitting} textoCargando="Enviando…">
            Enviar
          </Boton>
        </>
      }
    >
      <form id="form-reenvio" onSubmit={enviar} noValidate className="grid gap-4">
        <Controller
          name="metodo"
          control={control}
          render={({ field }) => (
            <Segmentado
              etiqueta="Pagaste con"
              bloque
              valor={field.value}
              alCambiar={field.onChange}
              opciones={[
                { valor: 'YAPE', texto: 'Yape', icono: Smartphone },
                { valor: 'PLIN', texto: 'Plin', icono: Smartphone },
              ]}
            />
          )}
        />
        <div className="datos-pago-monto rounded-12 border border-[#CFE5D7]">
          <span className="grid">
            <span className="text-[12.5px] text-texto-suave">{metodo === 'PLIN' ? 'Plin' : 'Yape'} del complejo</span>
            <b className="!text-[22px] !text-noche tnum">{numero ?? '—'}</b>
          </span>
          <b>{fmtSoles(monto)}</b>
        </div>
        <CampoTexto label="N.º de operación" requerido icono={Hash} inputMode="numeric" maxLength={12} autoComplete="off" error={errors.codigoOperacion?.message} {...register('codigoOperacion', { setValueAs: (v: string) => String(v ?? '').replace(/\D/g, '') })} />
        <Controller
          name="archivo"
          control={control}
          render={({ field }) => (
            <SubidaImagen
              valor={(field.value as File | null) ?? null}
              progreso={progreso}
              error={errors.archivo?.message as string | undefined}
              alError={(m) => (m ? setError('archivo', { type: 'manual', message: m }) : clearErrors('archivo'))}
              alCambiar={(f) => field.onChange(f)}
            />
          )}
        />
        <Controller
          name="tipoComprobante"
          control={control}
          render={({ field }) => (
            <Segmentado
              etiqueta="Comprobante"
              bloque
              valor={field.value ?? 'BOLETA'}
              alCambiar={field.onChange}
              opciones={[
                { valor: 'BOLETA', texto: 'Boleta', icono: FileText },
                { valor: 'FACTURA', texto: 'Factura', icono: Building2 },
              ]}
            />
          )}
        />
        {tipoComprobante === 'FACTURA' && (
          <div className="form-grid dos">
            <CampoTexto label="RUC" requerido icono={Building2} inputMode="numeric" maxLength={11} autoComplete="off" error={errors.ruc?.message} {...register('ruc', { setValueAs: (v: string) => String(v ?? '').replace(/\D/g, '').slice(0, 11) })} />
            <CampoTexto label="Razón social" requerido icono={Building2} maxLength={150} error={errors.razonSocial?.message} {...register('razonSocial')} />
            <CampoTexto label="Dirección fiscal" requerido icono={MapPin} maxLength={200} className="md:col-span-2" error={errors.direccionFiscal?.message} {...register('direccionFiscal')} />
          </div>
        )}
        {aviso && <Alerta tipo="error">{aviso}</Alerta>}
      </form>
    </Modal>
  );
}

// ------------------------------ Detalle ------------------------------
function Plazo({ venceEn }: { venceEn: string | null }) {
  const c = useCuentaRegresiva(venceEn);
  if (!c.activo) return null;
  return (
    <div className={`plazo ${c.vencido ? 'vencido' : ''}`} role="timer">
      <TimerReset className="ico ico-20" aria-hidden />
      <span className="flex-1">{c.vencido ? 'Venció el plazo para pagar.' : 'Tiempo para pagar'}</span>
      {!c.vencido && <b>{c.texto}</b>}
    </div>
  );
}

function Detalle({ consulta, inicial, alSalir }: { consulta: Consulta; inicial: ReservaDetalle; alSalir: () => void }) {
  const navigate = useNavigate();
  const toast = useToast();
  const { datos: complejo } = useComplejo();
  const [reserva, setReserva] = useState<ReservaDetalle>(inicial);
  const [cancelar, setCancelar] = useState(false);
  const [reenviar, setReenviar] = useState(false);
  const [pagando, setPagando] = useState(false);
  const [version, setVersion] = useState(0);
  const pagos = useCarga(() => pagosApi.pagosDeReserva(consulta.codigo, consulta.dni), `${consulta.codigo}-${version}`);
  const pedida = facturacionPedida(pagos.datos);
  useTituloPagina(`Reserva ${reserva.codigoReserva}`);

  // Los cambios de pago llegan a la reserva por eventos (RabbitMQ): se vuelve a pedir unos segundos hasta verlos.
  const recargar = async (listo: (r: ReservaDetalle) => boolean = () => true) => {
    for (let i = 0; i < 8; i += 1) {
      try {
        const r = await reservasApi.detalle(consulta.codigo, consulta.dni);
        setReserva(r);
        setVersion((v) => v + 1);
        if (listo(r)) return;
      } catch {
        return; /* el interceptor ya avisó */
      }
      await new Promise((ok) => window.setTimeout(ok, 1500));
    }
  };

  const clave = estadoCliente(reserva);
  const pendientePago = reserva.estado === 'PENDIENTE' && (reserva.estadoPago === 'SIN_PAGO' || reserva.estadoPago === 'OBSERVADO');
  const devolucionHoras = complejo?.reglas.devolucionHoras ?? 24;

  const pagarConTarjeta = async () => {
    setPagando(true);
    try {
      const i = await pagosApi.crearIntento({ codigoReserva: reserva.codigoReserva, dni: consulta.dni, tipoPago: 'ADELANTO', comprobante: leerFacturacion(reserva.codigoReserva) ?? pedida ?? undefined });
      guardarUltimaReserva({ reserva, dni: consulta.dni, metodo: 'TARJETA' });
      navigate(i.urlCheckout.startsWith('http') ? new URL(i.urlCheckout).pathname : i.urlCheckout);
    } catch (e) {
      toast.error(esErrorApi(e) ? e.message : 'No se pudo iniciar el pago.');
      setPagando(false);
    }
  };

  return (
    <div className="contenedor grid gap-6 py-6 lg:grid-cols-[minmax(0,1fr)_380px] lg:gap-8 lg:py-10">
      <div className="grid gap-5 content-start min-w-0">
        <button type="button" className="btn btn-fantasma btn-sm justify-self-start -ml-3" onClick={alSalir}>
          <ArrowLeft className="ico" aria-hidden />
          <span>Consultar otra</span>
        </button>
        <HeroEstado
          clave={clave}
          extra={
            reserva.puedeResenar ? (
              <BotonEnlace to={`/canchas/${reserva.cancha.id}?resena=${reserva.codigoReserva}`} variante="oscuro" icono={Star}>
                Calificar la cancha
              </BotonEnlace>
            ) : undefined
          }
        />
        {reserva.estado === 'PENDIENTE' && <Plazo venceEn={reserva.venceEn} />}

        {reserva.cancelacion && (
          <section className="cancelacion">
            <div className="cancelacion-cab">
              <CircleX className="ico" aria-hidden />
              <span>Reserva cancelada</span>
            </div>
            <ul className="grid gap-2 px-4 pt-1.5 pb-3.5 text-[14px] font-semibold">
              <li className="flex gap-2.5 items-start">
                <UserRound className="ico ico-18 text-gris mt-px" aria-hidden />
                <span>{reserva.cancelacion.canceladoPor === 'CLIENTE' ? 'Por ti' : reserva.cancelacion.canceladoPor === 'SISTEMA' ? 'Por el sistema' : 'Por el complejo'}</span>
              </li>
              <li className="flex gap-2.5 items-start">
                <History className="ico ico-18 text-gris mt-px" aria-hidden />
                <span className="tnum">{fmtFechaHora(reserva.cancelacion.fecha)}</span>
              </li>
              <li className="flex gap-2.5 items-start">
                <Info className="ico ico-18 text-gris mt-px" aria-hidden />
                <span>{`«${reserva.cancelacion.motivo}»`}</span>
              </li>
            </ul>
            <div className={`devolucion ${reserva.cancelacion.devolucion ? 'corresponde' : 'no-corresponde'}`}>
              {reserva.cancelacion.devolucion ? <HandCoins className="ico" aria-hidden /> : <Info className="ico" aria-hidden />}
              <span>
                {reserva.cancelacion.devolucion ? `Te devolvemos ${fmtSoles(reserva.cancelacion.montoDevolucion ?? 0)}. Te llegará la nota de crédito.` : 'Sin devolución.'}
              </span>
            </div>
          </section>
        )}

        <div className="grid gap-5 md:grid-cols-2 md:items-start">
          <section className="card card-pad grid gap-4" aria-labelledby="h-historial">
            <h2 id="h-historial" className="inline-flex items-center gap-2 t-h4">
              <History className="ico ico-18 text-verde" aria-hidden />
              Historial
            </h2>
            <LineaTiempo hitos={hitosDeReserva(reserva)} />
          </section>
          <section className="card overflow-hidden" aria-labelledby="h-pagos">
            <h2 id="h-pagos" className="inline-flex items-center gap-2 t-h4 px-[18px] pt-[18px] pb-3">
              <Receipt className="ico ico-18 text-verde" aria-hidden />
              Pagos y comprobantes
            </h2>
            {pagos.cargando && !pagos.datos ? <Cargando className="py-6" /> : <ListaPagos pagos={pagos.datos ?? []} codigo={consulta.codigo} dni={consulta.dni} />}
          </section>
        </div>
      </div>

      <aside className="grid gap-4 content-start">
        <CodigoReserva
          codigo={reserva.codigoReserva}
          claro
          ayuda=""
          acciones={
            <BotonExterno href={reservasApi.urlConstancia(reserva.codigoReserva, consulta.dni)} target="_blank" variante="fantasma" tamano="sm" icono={Download}>
              Constancia
            </BotonExterno>
          }
        />
        <p className="t-sec flex flex-wrap gap-x-4 gap-y-1 -mt-1 px-1">
          <span className="inline-flex items-center gap-1.5">
            <UserRound className="ico ico-14" aria-hidden />
            {`${reserva.cliente.nombres.split(' ')[0]} ${reserva.cliente.apellidos.split(' ')[0]}`}
          </span>
          <span className="inline-flex items-center gap-1.5 tnum">
            <Fingerprint className="ico ico-14" aria-hidden />
            {reserva.cliente.dniEnmascarado}
          </span>
          <span className="inline-flex items-center gap-1.5 tnum">
            <Smartphone className="ico ico-14" aria-hidden />
            {enmascararCelular(reserva.cliente.telefono)}
          </span>
        </p>

        <aside className="resumen" aria-label="Resumen de la reserva">
          <div className="resumen-cab">
            <span className="kicker">Tu reserva</span>
            {reserva.estado === 'CANCELADA' ? <InsigniaEstado dominio="reserva" estado="CANCELADA" tamano="sm" /> : <InsigniaEstado dominio="estadoPago" estado={reserva.estadoPago} tamano="sm" />}
          </div>
          <div className="resumen-cuerpo">
            <div className="resumen-item !items-center">
              <span className="resumen-ico">
                <IconoDeporte deporte={reserva.cancha.deporte} />
              </span>
              <b>{nombreConDeporte(reserva.cancha.nombre, reserva.cancha.deporte)}</b>
            </div>
            <div className="resumen-item !items-center">
              <span className="resumen-ico">
                <CalendarDays className="ico" aria-hidden />
              </span>
              <b className="tnum">{`${fmtFecha(reserva.fecha)} · ${horarioDeReserva(reserva)}`}</b>
            </div>
          </div>
          <div className="resumen-montos">
            <div className="fila-monto">
              <span className="inline-flex items-center gap-2">
                <Banknote className="ico ico-16" aria-hidden />
                Total
              </span>
              <b>{fmtSoles(reserva.montoTotal)}</b>
            </div>
            <div className="fila-monto">
              <span className="inline-flex items-center gap-2">
                <Wallet className="ico ico-16" aria-hidden />
                Pagado
              </span>
              <b>{fmtSoles(reserva.montoPagado)}</b>
            </div>
            {reserva.estado === 'CANCELADA' ? (
              // Cancelada: ya no hay saldo que pagar (el back conserva `saldo`); se muestra lo devuelto.
              <div className="fila-monto destacada">
                <span className="inline-flex items-center gap-2">
                  <HandCoins className="ico ico-16" aria-hidden />
                  Devuelto
                </span>
                <b>{fmtSoles(reserva.cancelacion?.devolucion ? (reserva.cancelacion.montoDevolucion ?? 0) : 0)}</b>
              </div>
            ) : (
              <div className="fila-monto destacada">
                <span className="inline-flex items-center gap-2">
                  <Store className="ico ico-16" aria-hidden />
                  Saldo al llegar
                </span>
                <b>{fmtSoles(reserva.saldo)}</b>
              </div>
            )}
          </div>
        </aside>

        {pendientePago && (
          <div className="grid gap-2">
            <Boton icono={Upload} tamano="lg" bloque onClick={() => setReenviar(true)}>
              {reserva.estadoPago === 'OBSERVADO' ? 'Reenviar comprobante' : 'Enviar comprobante'}
            </Boton>
            <Boton variante="secundario" icono={CreditCard} bloque cargando={pagando} onClick={pagarConTarjeta}>
              Pagar con tarjeta
            </Boton>
          </div>
        )}
        {!pendientePago && reserva.puedeReenviarComprobante && (
          <Boton icono={Upload} bloque onClick={() => setReenviar(true)}>
            Reenviar comprobante
          </Boton>
        )}

        {reserva.puedeCancelar && (
          <div className="grid gap-2">
            <p className="t-sec flex gap-2 items-start">
              <HandCoins className="ico ico-16 text-profundo mt-0.5" aria-hidden />
              <span>{`Cancela con ${devolucionHoras} h o más y te devolvemos lo pagado.`}</span>
            </p>
            <Boton variante="peligro-contorno" icono={CalendarX} bloque onClick={() => setCancelar(true)}>
              Cancelar reserva
            </Boton>
          </div>
        )}
        <BotonEnlace to={`/canchas/${reserva.cancha.id}`} variante="fantasma" icono={Eye} bloque>
          Ver la cancha
        </BotonEnlace>
      </aside>

      <ModalCancelar
        abierto={cancelar}
        alCerrar={() => setCancelar(false)}
        reserva={reserva}
        dni={consulta.dni}
        devolucionHoras={devolucionHoras}
        alCancelar={(r) => {
          setCancelar(false);
          if (r?.estado) setReserva(r);
          // La nota de crédito (si hubo devolución) la emite pagos al recibir `reserva.cancelada`.
          void recargar().then(() => window.setTimeout(() => setVersion((v) => v + 1), 4000));
        }}
      />
      <ModalReenviar
        key={JSON.stringify(pedida)}
        pedida={pedida}
        abierto={reenviar}
        alCerrar={() => setReenviar(false)}
        reserva={reserva}
        dni={consulta.dni}
        alEnviar={() => {
          setReenviar(false);
          setReserva((r) => ({ ...r, estadoPago: 'EN_REVISION', venceEn: null, puedeReenviarComprobante: false }));
          void recargar((r) => r.estadoPago !== 'OBSERVADO' && r.estadoPago !== 'SIN_PAGO');
        }}
      />
    </div>
  );
}

/** /mi-reserva — código + DNI (acepta ?codigo=). */
export default function MiReserva() {
  useTituloPagina('Mi reserva');
  const [params, setParams] = useSearchParams();
  const delParam = params.get('codigo') ? normalizarCodigo(params.get('codigo') ?? '') : '';
  // v2: desde «Mi cuenta» llega el DNI en el estado de la navegación (no en la URL).
  const dniCuenta = (useLocation().state as { dni?: string } | null)?.dni;
  const guardada = delParam && dniCuenta ? { codigo: delParam, dni: dniCuenta } : leerConsulta();
  const [consulta, setConsulta] = useState<Consulta | null>(guardada && (!delParam || guardada.codigo === delParam) ? guardada : null);
  const [inicial, setInicial] = useState<ReservaDetalle | null>(null);
  const carga = useCarga(() => reservasApi.detalle(consulta?.codigo ?? '', consulta?.dni ?? ''), consulta && !inicial ? consulta.codigo : null);

  useEffect(() => {
    if (carga.datos) setInicial(carga.datos);
  }, [carga.datos]);
  useEffect(() => {
    if (carga.error) {
      sessionStorage.removeItem(CLAVE);
      setConsulta(null);
    }
  }, [carga.error]);

  if (consulta && !inicial) return <Cargando texto="Buscando tu reserva…" />;
  if (consulta && inicial) {
    return (
      <Detalle
        key={inicial.codigoReserva}
        consulta={consulta}
        inicial={inicial}
        alSalir={() => {
          sessionStorage.removeItem(CLAVE);
          setConsulta(null);
          setInicial(null);
          setParams({}, { replace: true });
        }}
      />
    );
  }
  return (
    <FormConsulta
      codigoInicial={delParam}
      alEncontrar={(c, r) => {
        sessionStorage.setItem(CLAVE, JSON.stringify(c));
        setInicial(r);
        setConsulta(c);
        setParams({ codigo: c.codigo }, { replace: true });
      }}
    />
  );
}
