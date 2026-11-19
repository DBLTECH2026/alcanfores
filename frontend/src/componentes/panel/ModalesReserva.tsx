import { Fragment, useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { z } from 'zod';
import {
  ArrowRight,
  Banknote,
  CalendarX,
  Check,
  CircleCheck,
  CreditCard,
  Eye,
  FileText,
  Globe,
  HandCoins,
  Hash,
  ImageOff,
  LockOpen,
  MessageSquareWarning,
  PenLine,
  Phone,
  Smartphone,
  Store,
  TimerOff,
  Wrench,
  X,
  type LucideIcon,
} from 'lucide-react';
import { pagosAdminApi, reservasAdminApi } from '@/api/admin';
import type { DetallePago, ReservaAdmin, ResultadoCobro } from '@/api/admin/tipos';
import { esErrorApi } from '@/api/cliente';
import { useAuth } from '@/contexto/AuthContext';
import { useToast } from '@/contexto/ToastContext';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { Boton } from '@/componentes/ui/Boton';
import { AreaTexto, Casilla, CampoTexto, Interruptor } from '@/componentes/ui/Campo';
import { Cargando, Segmentado } from '@/componentes/ui/Datos';
import { Alerta, InsigniaEstado } from '@/componentes/ui/Estados';
import { Modal } from '@/componentes/ui/Modal';
import { comprobanteDe, crearEsquemaCaja, esquemaCancelarAdmin, esquemaRechazo } from '@/paginas/panel/esquemasPanel';
import { ahoraLima, deLima, fmtFecha, fmtFechaHora, fmtRango24, textoHorario } from '@/utilidades/fecha';
import { fmtSoles, plural } from '@/utilidades/formato';
import { cx } from '@/utilidades/cx';
import { abrirArchivo } from './archivos';
import { BloqueComprobante } from './BloqueComprobante';
import { SubidaImagen } from '@/componentes/ui/SubidaImagen';
import { VisorComprobante } from './VisorComprobante';

export const TEXTO_METODO: Record<string, string> = {
  YAPE: 'Yape',
  PLIN: 'Plin',
  TARJETA: 'Tarjeta',
  MERCADO_PAGO: 'Mercado Pago',
  EFECTIVO: 'Efectivo',
  TARJETA_POS: 'Tarjeta (POS)',
};
export const ICONO_METODO: Record<string, LucideIcon> = {
  YAPE: Smartphone,
  PLIN: Smartphone,
  TARJETA: CreditCard,
  MERCADO_PAGO: CreditCard,
  EFECTIVO: Banknote,
  TARJETA_POS: CreditCard,
};
export const ICONO_ORIGEN: Record<string, LucideIcon> = { WEB: Globe, PRESENCIAL: Store, TELEFONICA: Phone };
export const TEXTO_ORIGEN: Record<string, string> = { WEB: 'Web', PRESENCIAL: 'Presencial', TELEFONICA: 'Teléfono' };
export const TEXTO_TIPO_PAGO: Record<string, string> = { ADELANTO: 'Adelanto', TOTAL: 'Pago total', SALDO: 'Saldo' };
const nombre = (c: { nombres: string; apellidos: string }) => `${c.nombres} ${c.apellidos}`.trim();

// ------------------------------ horario en 24 h (el panel nunca usa 12 h) ------------------------------
const RE_TRAMO = /^(\d{1,2}):(\d{2})(?:\s*([ap])\.?\s*m\.?)?\s*[–-]\s*(\d{1,2}):(\d{2})\s*([ap])\.?\s*m\.?$/i;
const a24 = (h: string, m: string, s: string, fin: boolean) => {
  let H = Number(h) % 12;
  if (s.toLowerCase() === 'p') H += 12;
  if (fin && H === 0 && m === '00') return '24:00';
  return `${String(H).padStart(2, '0')}:${m}`;
};
/**
 * `horarioTexto` del back (12 h, turnos seguidos ya unidos): «9:00–10:00 a. m. y 7:00–8:00 p. m.» →
 * [['09:00','10:00'], ['19:00','20:00']]. null si no se entiende.
 */
export function tramosDeTexto(texto: string | null | undefined): Array<[string, string]> | null {
  if (!texto) return null;
  const tramos: Array<[string, string]> = [];
  for (const parte of texto.trim().split(/\s*,\s*|\s+y\s+/)) {
    const x = RE_TRAMO.exec(parte.trim());
    if (!x) return null;
    const [, h1 = '', m1 = '', s1, h2 = '', m2 = '', s2 = ''] = x;
    tramos.push([a24(h1, m1, s1 ?? s2, false), a24(h2, m2, s2, true)]);
  }
  return tramos.length ? tramos : null;
}
/**
 * Texto del back para el panel: horas de 12 h («antes de las 3:37 p. m.», «8:00–9:00 p. m.») → 24 h
 * («antes de las 15:37», «20:00–21:00») y el monto pegado a «S/» con espacio duro, para que no se parta.
 */
export function horasA24(texto: string): string {
  return texto
    .replace(/S\/\s+(?=\d)/g, 'S/ ')
    .replace(/(\d{1,2}):(\d{2})(?:[\s⁠]*([ap])\.[\s⁠]*m\.)?[\s⁠]*[–-][\s⁠]*(\d{1,2}):(\d{2})[\s⁠]*([ap])\.[\s⁠]*m\./gi, (_t, h1: string, m1: string, s1: string | undefined, h2: string, m2: string, s2: string) =>
      fmtRango24(a24(h1, m1, s1 ?? s2, false), a24(h2, m2, s2, true)),
    )
    .replace(/(\d{1,2}):(\d{2})[\s⁠]*([ap])\.[\s⁠]*m\./gi, (_t, h: string, m: string, s: string) => a24(h, m, s, false));
}
/** Texto plano del horario en 24 h («08:00–09:00, 10:00–11:00»), para títulos y lectores de pantalla. */
export function horario24(r: { horarioTexto?: string | null; horaInicio: string; horaFin: string; cantidadTurnos?: number | null }): string {
  const tramos = (r.cantidadTurnos ?? 1) > 1 ? tramosDeTexto(r.horarioTexto) : null;
  if (tramos) return tramos.map(([a, b]) => fmtRango24(a, b)).join(', ');
  return (r.cantidadTurnos ?? 1) > 1 && r.horarioTexto ? r.horarioTexto : fmtRango24(r.horaInicio, r.horaFin);
}
/**
 * Horario de una reserva en el panel, siempre en 24 h. Varios turnos = un tramo por bloque seguido
 * («08:00–09:00, 10:00–11:00»), cada tramo sin partirse; con más de 2 tramos, el primero y «+N».
 * Nunca «inicio del primero–fin del último»: los turnos pueden no ser seguidos.
 */
export function HorarioTramos({ reserva: r, className }: { reserva: { horarioTexto?: string | null; horaInicio: string; horaFin: string; cantidadTurnos?: number | null }; className?: string }) {
  const n = r.cantidadTurnos ?? 1;
  const tramos = n > 1 ? tramosDeTexto(r.horarioTexto) : null;
  if (n > 1 && !tramos && r.horarioTexto) return <span className={className}>{r.horarioTexto}</span>;
  const textos = (tramos ?? [[r.horaInicio, r.horaFin] as [string, string]]).map(([a, b]) => fmtRango24(a, b));
  const vistos = textos.length > 2 ? textos.slice(0, 1) : textos;
  const resto = textos.length - vistos.length;
  return (
    <span className={cx('tnum', className)} title={n > 1 ? `${textos.join(', ')} · ${plural(n, 'turno', 'turnos')}` : undefined}>
      {/* La coma va pegada a su tramo y los espacios son nodos sueltos: se ve igual dentro de un contenedor flex (avisos) o en línea (tabla). */}
      {vistos.map((t, i) => (
        <Fragment key={t}>
          {i > 0 && ' '}
          <span className="whitespace-nowrap">{i < vistos.length - 1 ? `${t},` : t}</span>
        </Fragment>
      ))}
      {resto > 0 && (
        <>
          {' '}
          <span className="whitespace-nowrap">{`+${resto}`}</span>
        </>
      )}
      {textos.length === 1 && n > 1 && (
        <>
          {' '}
          <span className="whitespace-nowrap">{`· ${plural(n, 'turno', 'turnos')}`}</span>
        </>
      )}
    </span>
  );
}

/** Carga el pago y su reserva (para los montos de la franja). */
function usePagoYReserva(idPago: number | null) {
  const [datos, setDatos] = useState<{ pago: DetallePago; reserva: ReservaAdmin | null } | null>(null);
  const [error, setError] = useState(false);
  useEffect(() => {
    if (idPago === null) return undefined;
    let vigente = true;
    setDatos(null);
    setError(false);
    pagosAdminApi
      .detalle(idPago)
      .then(async (pago) => {
        const reserva = await reservasAdminApi.detalle(pago.codigoReserva).catch(() => null);
        if (vigente) setDatos({ pago, reserva });
      })
      .catch(() => vigente && setError(true));
    return () => {
      vigente = false;
    };
  }, [idPago]);
  return { datos, error };
}

const franjaReserva = (r: ReservaAdmin | null, pago?: DetallePago): Array<[string, string, string?]> =>
  r
    ? [
        ['Total', fmtSoles(r.montoTotal)],
        ['Adelanto', fmtSoles(r.montoAdelanto), 'verde'],
        ['Saldo en caja', fmtSoles(Math.max(0, r.montoTotal - r.montoPagado - (pago?.monto ?? 0)))],
      ]
    : pago
      ? [['Monto', fmtSoles(pago.monto), 'verde']]
      : [];

// ------------------------------ validar comprobante (adm-m1) ------------------------------
export function ModalValidar({ idPago, alCerrar, alHecho }: { idPago: number | null; alCerrar: () => void; alHecho: (r: ResultadoCobro) => void }) {
  const { datos, error } = usePagoYReserva(idPago);
  const toast = useToast();
  const [verificado, setVerificado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  useEffect(() => setVerificado(false), [idPago]);
  const pago = datos?.pago;
  const r = datos?.reserva ?? null;
  const esperado = r ? (pago?.tipoPago === 'TOTAL' ? r.montoTotal : pago?.tipoPago === 'SALDO' ? r.saldo : r.montoAdelanto) : null;
  const coincide = esperado === null || !pago || Math.abs(esperado - pago.monto) < 0.01;

  const validar = async () => {
    if (!pago) return;
    setEnviando(true);
    try {
      const res = await pagosAdminApi.validar(pago.id);
      toast.exito(res.comprobante ? `Pago validado · ${res.comprobante.serieNumero}` : 'Pago validado');
      alHecho(res);
    } catch {
      /* el interceptor ya mostró el mensaje del back */
    } finally {
      setEnviando(false);
    }
  };

  return (
    <Modal
      abierto={idPago !== null}
      alCerrar={() => !enviando && alCerrar()}
      titulo="Validar comprobante"
      tamano={pago?.canal === 'COMPROBANTE' ? 'xl' : 'md'}
      subtitulo={pago ? `${pago.codigoReserva} · ${nombre(pago.cliente)}` : 'Cargando…'}
      busto="BU-rosa-panel"
      franja={franjaReserva(r, pago)}
      acciones={
        <>
          <Boton variante="fantasma" onClick={alCerrar} disabled={enviando}>
            Cancelar
          </Boton>
          <Boton icono={Check} disabled={!pago || !verificado || !pago.puedeValidar} cargando={enviando} textoCargando="Validando…" onClick={validar}>
            Validar
          </Boton>
        </>
      }
    >
      {error ? (
        <Alerta tipo="error">No se pudo cargar el pago.</Alerta>
      ) : !pago ? (
        <Cargando texto="Cargando pago…" className="!py-8" />
      ) : (
        <div className={pago.canal === 'COMPROBANTE' ? 'validar-con-foto' : undefined}>
        {pago.canal === 'COMPROBANTE' && (
          <div className="validar-foto">
            <VisorComprobante idPago={pago.id} leyenda={`Comprobante de ${pago.codigoReserva}`} />
          </div>
        )}
        <div className="grid gap-3 content-start">
          <div className={coincide ? 'compara' : 'compara distinto'}>
            <div>
              <span>
                <FileText className="ico ico-14" aria-hidden />
                Comprobante
              </span>
              <b className="tnum">{fmtSoles(pago.monto)}</b>
            </div>
            <i aria-label={coincide ? 'igual' : 'distinto'}>{coincide ? '=' : '≠'}</i>
            <div>
              <span>
                <HandCoins className="ico ico-14" aria-hidden />
                {TEXTO_TIPO_PAGO[pago.tipoPago] ?? pago.tipoPago}
              </span>
              <b className="tnum">{esperado !== null ? fmtSoles(esperado) : '—'}</b>
            </div>
          </div>
          <div className="cajas2">
            <div className="caja-osc">
              <Smartphone className="ico" aria-hidden />
              {TEXTO_METODO[pago.metodo] ?? pago.metodo}
            </div>
            <div className="caja-osc">
              <Hash className="ico" aria-hidden />
              {pago.codigoOperacion ?? '—'}
            </div>
          </div>
          <div className="flecha-estados">
            <InsigniaEstado dominio="reserva" estado="PENDIENTE" />
            <ArrowRight className="ico ico-16 text-[#6B7C74]" aria-hidden />
            <InsigniaEstado dominio="reserva" estado="CONFIRMADA" />
          </div>
          {!pago.puedeValidar && <Alerta tipo="advertencia">Este pago ya no está pendiente.</Alerta>}
          {!coincide && <Alerta tipo="advertencia">El monto no coincide con lo esperado. Revisa antes de validar.</Alerta>}
          <Casilla checked={verificado} onChange={(e) => setVerificado(e.target.checked)}>
            Verifiqué en {TEXTO_METODO[pago.metodo] ?? pago.metodo} la operación <b>{pago.codigoOperacion}</b> por <b>{fmtSoles(pago.monto)}</b>
          </Casilla>
          <span className="t-mini text-[#6B7C74]">Al validar, el cliente recibe por WhatsApp y correo la confirmación y su boleta o factura.</span>
        </div>
        </div>
      )}
    </Modal>
  );
}

// ------------------------------ rechazar comprobante (adm-m2) ------------------------------
const MOTIVOS_RECHAZO: Array<[string, LucideIcon]> = [
  ['El monto no coincide con el adelanto', HandCoins],
  ['No encontramos la operación en Yape o Plin', Hash],
  ['La imagen no se ve o no es un comprobante', ImageOff],
];
export function ModalRechazar({ idPago, alCerrar, alHecho }: { idPago: number | null; alCerrar: () => void; alHecho: () => void }) {
  const { datos, error } = usePagoYReserva(idPago);
  const toast = useToast();
  const pago = datos?.pago;
  const {
    register,
    handleSubmit,
    setValue,
    setError,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof esquemaRechazo>>({ resolver: zodResolver(esquemaRechazo), mode: 'onTouched', defaultValues: { observacion: '' } });
  useEffect(() => reset({ observacion: '' }), [idPago, reset]);
  const obs = watch('observacion') ?? '';

  const enviar = handleSubmit(async ({ observacion }) => {
    if (!pago) return;
    try {
      await pagosAdminApi.rechazar(pago.id, observacion);
      toast.exito('Comprobante observado');
      alHecho();
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['observacion']);
      if (!aplicados) toast.error(esErrorApi(e) ? e.message : 'No se pudo rechazar.');
    }
  });

  return (
    <Modal
      abierto={idPago !== null}
      alCerrar={() => !isSubmitting && alCerrar()}
      titulo="Rechazar comprobante"
      subtitulo={pago ? `${pago.codigoReserva} · ${nombre(pago.cliente)}` : 'Cargando…'}
      busto="BU-rosa-panel"
      franja={pago ? [['Comprobante', fmtSoles(pago.monto)], ['Método', TEXTO_METODO[pago.metodo] ?? pago.metodo], ['Operación', pago.codigoOperacion ?? '—']] : undefined}
      acciones={
        <>
          <Boton variante="fantasma" onClick={alCerrar} disabled={isSubmitting}>
            Volver
          </Boton>
          <Boton variante="peligro" icono={X} cargando={isSubmitting} disabled={!pago} onClick={enviar}>
            Rechazar
          </Boton>
        </>
      }
    >
      {error ? (
        <Alerta tipo="error">No se pudo cargar el pago.</Alerta>
      ) : (
        <form className="grid gap-3" onSubmit={enviar} noValidate>
          <span className="etiqueta-osc">Motivo</span>
          <div className="chips-motivo">
            {MOTIVOS_RECHAZO.map(([m, I]) => (
              <button key={m} type="button" className="chip-motivo" aria-pressed={obs === m} onClick={() => setValue('observacion', m, { shouldValidate: true })}>
                <I className="ico" aria-hidden />
                {m}
              </button>
            ))}
          </div>
          <AreaTexto label="Observación para el cliente" requerido rows={3} maxLength={255} contador={{ actual: obs.length, max: 255 }} error={errors.observacion?.message} {...register('observacion')} />
          <div className="caja-aviso">
            <div>
              <MessageSquareWarning className="ico" aria-hidden />
              El cliente recibe la observación por correo y WhatsApp
            </div>
            <div>
              <TimerOff className="ico" aria-hidden />
              Tiene un nuevo plazo para reenviar
            </div>
          </div>
        </form>
      )}
    </Modal>
  );
}

// ------------------------------ cobrar saldo en caja ------------------------------
type EntradaCaja = z.input<ReturnType<typeof crearEsquemaCaja>>;
type SalidaCaja = z.output<ReturnType<typeof crearEsquemaCaja>>;
export function ModalCobrar({ reserva, alCerrar, alHecho }: { reserva: ReservaAdmin | null; alCerrar: () => void; alHecho: (r: ResultadoCobro) => void }) {
  const toast = useToast();
  const [resultado, setResultado] = useState<ResultadoCobro | null>(null);
  const saldo = reserva?.saldo ?? 0;
  const esquema = useMemo(() => crearEsquemaCaja(saldo), [saldo]);
  const tipoInicial: 'SALDO' | 'TOTAL' = reserva && reserva.montoPagado > 0 ? 'SALDO' : 'TOTAL';
  const {
    register,
    handleSubmit,
    setValue,
    setError,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<EntradaCaja>({
    resolver: zodResolver(esquema),
    mode: 'onTouched',
    defaultValues: { tipoPago: tipoInicial, metodo: 'EFECTIVO', monto: saldo.toFixed(2), codigoOperacion: '', tipoComprobante: 'BOLETA', ruc: '', razonSocial: '', direccionFiscal: '' },
  });
  useEffect(() => {
    setResultado(null);
    reset({ tipoPago: tipoInicial, metodo: 'EFECTIVO', monto: saldo.toFixed(2), codigoOperacion: '', tipoComprobante: 'BOLETA', ruc: '', razonSocial: '', direccionFiscal: '' });
  }, [reserva?.codigoReserva, saldo, tipoInicial, reset]);
  const metodo = watch('metodo');
  const tipoComp = watch('tipoComprobante');
  const [foto, setFoto] = useState<File | null>(null);
  const [errorFoto, setErrorFoto] = useState<string | null>(null);
  const conFoto = metodo === 'YAPE' || metodo === 'PLIN' || metodo === 'TARJETA_POS';
  useEffect(() => {
    setFoto(null);
    setErrorFoto(null);
  }, [reserva]);

  const enviar = handleSubmit(async (entrada) => {
    if (!reserva) return;
    // zodResolver entrega los datos ya transformados (monto como número)
    const d = entrada as unknown as SalidaCaja;
    try {
      const r = await pagosAdminApi.caja({
        codigoReserva: reserva.codigoReserva,
        tipoPago: d.tipoPago,
        metodo: d.metodo,
        monto: d.monto,
        codigoOperacion: d.codigoOperacion || undefined,
        comprobante: comprobanteDe(d),
      });
      if (conFoto && foto && r.idPago) {
        try {
          await pagosAdminApi.adjuntarFoto(r.idPago, foto);
        } catch {
          toast.error('El cobro se registró, pero no se pudo guardar la foto. Súbela desde el detalle de la reserva.');
        }
      }
      setResultado(r);
      toast.exito(r.comprobante ? `Cobro registrado · ${r.comprobante.serieNumero}` : 'Cobro registrado');
      alHecho(r);
    } catch (e) {
      const { aplicados, sinUbicar } = aplicarErroresServidor(e, setError, ['monto', 'metodo', 'codigoOperacion', 'tipoPago', 'ruc', 'razonSocial', 'direccionFiscal'], {
        'comprobante.ruc': 'ruc',
        'comprobante.razonSocial': 'razonSocial',
        'comprobante.direccionFiscal': 'direccionFiscal',
      });
      if (!aplicados || sinUbicar.length) toast.error(esErrorApi(e) ? e.message : 'No se pudo cobrar.');
    }
  });

  return (
    <Modal
      abierto={reserva !== null}
      alCerrar={() => !isSubmitting && alCerrar()}
      titulo={resultado ? 'Cobro registrado' : 'Cobrar en caja'}
      subtitulo={reserva ? `${reserva.codigoReserva} · ${nombre(reserva.cliente)}` : ''}
      busto="BU-lucho-tarjeta"
      franja={reserva ? [['Total', fmtSoles(reserva.montoTotal)], ['Pagado', fmtSoles(reserva.montoPagado), 'verde'], ['Saldo', fmtSoles(reserva.saldo)]] : undefined}
      acciones={
        resultado ? (
          <>
            {resultado.comprobante && (
              <Boton variante="secundario" icono={FileText} onClick={() => resultado.comprobante && void abrirArchivo(resultado.comprobante.urlPdf)}>
                {resultado.comprobante.serieNumero}
              </Boton>
            )}
            <Boton icono={Check} onClick={alCerrar}>
              Listo
            </Boton>
          </>
        ) : (
          <>
            <Boton variante="fantasma" onClick={alCerrar} disabled={isSubmitting}>
              Volver
            </Boton>
            <Boton icono={HandCoins} cargando={isSubmitting} textoCargando="Cobrando…" onClick={enviar}>
              Cobrar
            </Boton>
          </>
        )
      }
    >
      {resultado ? (
        <div className="caja-aviso">
          <div>
            <CircleCheck className="ico" aria-hidden />
            Pago de {fmtSoles(resultado.monto)} validado
          </div>
          {resultado.comprobante && (
            <div>
              <FileText className="ico" aria-hidden />
              {resultado.comprobante.tipo === 'FACTURA' ? 'Factura' : 'Boleta'} {resultado.comprobante.serieNumero} enviada al cliente
            </div>
          )}
        </div>
      ) : (
        <form className="grid gap-3" onSubmit={enviar} noValidate>
          {reserva && reserva.montoPagado > 0 ? (
            // Con adelanto ya pagado solo queda el saldo: una línea, no un segmentado de una sola opción.
            <span className="dato-ico">
              <HandCoins className="ico" aria-hidden />
              <span>
                Cobras el saldo: <b className="tnum whitespace-nowrap">{fmtSoles(saldo)}</b>
              </span>
            </span>
          ) : (
            <Segmentado
              etiqueta="Tipo de cobro"
              bloque
              pequeno
              valor={watch('tipoPago') ?? null}
              alCambiar={(v) => {
                setValue('tipoPago', v);
                setValue('monto', (v === 'ADELANTO' && reserva ? Math.min(reserva.montoAdelanto, saldo) : saldo).toFixed(2), { shouldValidate: true });
              }}
              opciones={[
                { valor: 'TOTAL', texto: 'Total', icono: HandCoins },
                { valor: 'ADELANTO', texto: 'Adelanto', icono: HandCoins },
              ]}
            />
          )}
          <Segmentado
            etiqueta="Método"
            bloque
            pequeno
            valor={metodo}
            alCambiar={(v) => setValue('metodo', v, { shouldValidate: true })}
            opciones={[
              { valor: 'EFECTIVO', texto: 'Efectivo', icono: Banknote },
              { valor: 'YAPE', texto: 'Yape', icono: Smartphone },
              { valor: 'PLIN', texto: 'Plin', icono: Smartphone },
              { valor: 'TARJETA_POS', texto: 'POS', icono: CreditCard },
            ]}
          />
          <div className="cajas2">
            <CampoTexto label="Monto" requerido prefijo="S/" inputMode="decimal" error={errors.monto?.message} {...register('monto')} />
            {(metodo === 'YAPE' || metodo === 'PLIN') && (
              <CampoTexto label="N.° de operación" requerido inputMode="numeric" maxLength={12} icono={Hash} error={errors.codigoOperacion?.message} {...register('codigoOperacion')} />
            )}
          </div>
          {conFoto && (
            <SubidaImagen
              valor={foto}
              alCambiar={setFoto}
              error={errorFoto ?? undefined}
              alError={setErrorFoto}
              titulo="Foto del comprobante (opcional)"
              ayuda={metodo === 'TARJETA_POS' ? 'Voucher del POS · JPG o PNG, máx. 5 MB' : 'Captura del Yape o Plin · JPG o PNG, máx. 5 MB'}
              nombreCampo="foto-caja"
            />
          )}
          <BloqueComprobante
            tipo={tipoComp}
            alTipo={(t) => setValue('tipoComprobante', t)}
            registrar={(c) => register(c)}
            errores={{ ruc: errors.ruc?.message, razonSocial: errors.razonSocial?.message, direccionFiscal: errors.direccionFiscal?.message }}
            ruc={watch('ruc') ?? ''}
            alLlenar={(x) => {
              setValue('razonSocial', x.razonSocial, { shouldValidate: true });
              setValue('direccionFiscal', x.direccionFiscal, { shouldValidate: true });
            }}
          />
        </form>
      )}
    </Modal>
  );
}

// ------------------------------ cancelar reserva (adm-m3) ------------------------------
const MOTIVOS_CANCELAR: Array<[string, LucideIcon]> = [
  ['El cliente lo pidió por teléfono', Phone],
  ['Mantenimiento de la cancha', Wrench],
  ['No envió el comprobante a tiempo', TimerOff],
];
export function ModalCancelar({
  reserva,
  devolucionHoras = 24,
  alCerrar,
  alHecho,
}: {
  reserva: ReservaAdmin | null;
  devolucionHoras?: number;
  alCerrar: () => void;
  alHecho: (r: ReservaAdmin) => void;
}) {
  const toast = useToast();
  const { usuario } = useAuth();
  const horasFaltan = reserva ? deLima(`${reserva.fecha}T${reserva.horaInicio}:00`).diff(ahoraLima(), 'hour', true) : 0;
  const devolucionSugerida = Boolean(reserva && reserva.montoPagado > 0 && horasFaltan >= devolucionHoras);
  const {
    register,
    handleSubmit,
    setValue,
    setError,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof esquemaCancelarAdmin>>({ resolver: zodResolver(esquemaCancelarAdmin), mode: 'onTouched', defaultValues: { motivo: '', devolucion: devolucionSugerida } });
  useEffect(() => reset({ motivo: '', devolucion: devolucionSugerida }), [reserva?.codigoReserva, devolucionSugerida, reset]);
  const motivo = watch('motivo') ?? '';
  const devolucion = watch('devolucion');
  const [otro, setOtro] = useState(false);

  const enviar = handleSubmit(async (d) => {
    if (!reserva) return;
    try {
      const r = await reservasAdminApi.cancelar(reserva.codigoReserva, d.motivo, reserva.montoPagado > 0 ? d.devolucion : false);
      toast.exito('Reserva cancelada');
      alHecho(r);
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['motivo']);
      if (!aplicados) toast.error(esErrorApi(e) ? e.message : 'No se pudo cancelar.');
    }
  });

  return (
    <Modal
      abierto={reserva !== null}
      alCerrar={() => !isSubmitting && alCerrar()}
      titulo="Cancelar reserva"
      subtitulo={reserva ? `${reserva.codigoReserva} · ${nombre(reserva.cliente)}` : ''}
      busto="BU-rosa-panel"
      tamano="lg"
      franja={reserva ? [['Total', fmtSoles(reserva.montoTotal)], ['Pagado', fmtSoles(reserva.montoPagado), 'verde'], ['Saldo', fmtSoles(reserva.saldo)]] : undefined}
      acciones={
        <>
          <Boton variante="fantasma" onClick={alCerrar} disabled={isSubmitting}>
            Volver
          </Boton>
          <Boton variante="peligro" icono={CalendarX} cargando={isSubmitting} textoCargando="Cancelando…" onClick={enviar}>
            Cancelar reserva
          </Boton>
        </>
      }
    >
      {reserva && (
        <form className="grid gap-3" onSubmit={enviar} noValidate>
          <span className="etiqueta-osc">Motivo</span>
          <div className="chips-motivo">
            {MOTIVOS_CANCELAR.map(([m, I]) => (
              <button
                key={m}
                type="button"
                className="chip-motivo"
                aria-pressed={!otro && motivo === m}
                onClick={() => {
                  setOtro(false);
                  setValue('motivo', m, { shouldValidate: true });
                }}
              >
                <I className="ico" aria-hidden />
                {m}
              </button>
            ))}
            <button
              type="button"
              className="chip-motivo"
              aria-pressed={otro}
              onClick={() => {
                setOtro(true);
                setValue('motivo', '');
              }}
            >
              <PenLine className="ico" aria-hidden />
              Otro
            </button>
          </div>
          <AreaTexto label="Detalle" requerido rows={3} maxLength={255} contador={{ actual: motivo.length, max: 255 }} error={errors.motivo?.message} {...register('motivo')} />
          {reserva.montoPagado > 0 && (
            <div className="grid gap-1">
              <Interruptor activo={devolucion} alCambiar={(v) => setValue('devolucion', v)}>
                Devolver {fmtSoles(reserva.montoPagado)} (nota de crédito)
              </Interruptor>
              <span className="text-[12px] text-[#6B7C74]">
                {devolucionSugerida ? `Faltan más de ${devolucionHoras} h: corresponde devolución.` : `Faltan menos de ${devolucionHoras} h: por regla no hay devolución.`}
              </span>
            </div>
          )}
          <div className="cajas2">
            <div className="caja-osc">
              <span className="avatar avatar-sm admin">{usuario ? usuario.nombres.charAt(0) + usuario.apellidos.charAt(0) : ''}</span>
              {usuario ? `${usuario.nombres} ${usuario.apellidos}` : ''}
            </div>
            <div className="caja-osc">
              <CalendarX className="ico" aria-hidden />
              {fmtFechaHora(ahoraLima().format('YYYY-MM-DDTHH:mm:ss'), { h24: true, sinAnio: true })}
            </div>
          </div>
          <div className="caja-aviso">
            <div>
              <LockOpen className="ico" aria-hidden />
              {fmtFecha(reserva.fecha, 'corta')} · {reserva.turnos && reserva.turnos.length > 1 ? textoHorario(reserva.turnos, true) : fmtRango24(reserva.horaInicio, reserva.horaFin)} queda libre
            </div>
            <div>
              <Eye className="ico" aria-hidden />
              El cliente verá el motivo
            </div>
            {reserva.montoPagado > 0 && (
              <div>
                <HandCoins className="ico" aria-hidden />
                {devolucion ? `Le corresponde ${fmtSoles(reserva.montoPagado)}` : 'Sin devolución'}
              </div>
            )}
          </div>
        </form>
      )}
    </Modal>
  );
}
