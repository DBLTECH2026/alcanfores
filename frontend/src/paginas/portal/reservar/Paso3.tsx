import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  ArrowLeft,
  CalendarClock,
  CircleAlert,
  Copy,
  CreditCard,
  ExternalLink,
  HandCoins,
  Hash,
  ShieldCheck,
  Smartphone,
  TimerReset,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import { pagosApi, reservasApi } from '@/api/servicios';
import { esErrorApi } from '@/api/cliente';
import type { DatosComprobante, MetodoPagoWeb, ReservaCreada } from '@/api/tipos';
import { guardarFacturacion, guardarUltimaReserva, useReserva } from '@/contexto/ReservaContext';
import { useToast } from '@/contexto/ToastContext';
import { useComplejo, useMetodosPago } from '@/hooks/useDatosPublicos';
import { useCuentaRegresiva } from '@/hooks/useCuentaRegresiva';
import { CampoTexto } from '@/componentes/ui/Campo';
import { Boton } from '@/componentes/ui/Boton';
import { Alerta } from '@/componentes/ui/Estados';
import { Modal } from '@/componentes/ui/Modal';
import { Segmentado } from '@/componentes/ui/Datos';
import { SubidaImagen } from '@/componentes/ui/SubidaImagen';
import { calcularAdelanto } from '@/componentes/portal/Asistente';
import { esquemaPago, type DatosPago, type PagoEntrada } from '@/esquemas/reserva';
import { erroresPorCampo } from '@/hooks/useErroresServidor';
import { fmtSoles } from '@/utilidades/formato';
import { cx } from '@/utilidades/cx';
import { CabezaPaso } from './CabezaPaso';

interface OpcionMetodo {
  valor: MetodoPagoWeb;
  nombre: string;
  sub: string;
  icono: LucideIcon;
}

const CAMPOS_PASO2 = ['dni', 'nombres', 'apellidos', 'telefono', 'correo', 'ruc', 'razonSocial', 'direccionFiscal', 'aceptaTerminos', 'aceptaPrivacidad'];

/** Número de Yape / Plin del complejo + monto del adelanto (sin logos de terceros). */
function DatosPago({ metodo, numero, titular, monto }: { metodo: 'YAPE' | 'PLIN'; numero: string; titular: string; monto: number }) {
  const toast = useToast();
  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(numero.replace(/\s/g, ''));
      toast.exito('Número copiado');
    } catch {
      toast.info(numero);
    }
  };
  return (
    <div className="datos-pago">
      <div className="datos-pago-cab">
        <span className="grid place-items-center w-[72px] h-[72px] rounded-16 bg-menta text-profundo shrink-0" aria-hidden>
          <Smartphone className="ico ico-32" />
        </span>
        <div className="grid gap-1 min-w-0">
          <span className="kicker inline-flex items-center gap-1.5">{metodo === 'YAPE' ? 'Yape' : 'Plin'}</span>
          <span className="datos-pago-num">{numero}</span>
          <span className="t-sec">{titular}</span>
          <span className="mt-1">
            <Boton variante="secundario" tamano="sm" icono={Copy} onClick={copiar} etiqueta="Copiar número">
              Copiar
            </Boton>
          </span>
        </div>
      </div>
      <div className="datos-pago-monto">
        <span className="inline-flex items-center gap-2 text-[14px] font-semibold text-noche">
          <Wallet className="ico ico-18 text-profundo" aria-hidden />
          Adelanto
        </span>
        <b>{fmtSoles(monto)}</b>
      </div>
      <div className="datos-pago-paso">
        <Smartphone className="ico ico-18 text-verde" aria-hidden />
        <span>Paga en tu app y vuelve aquí con el número de operación y la captura.</span>
      </div>
    </div>
  );
}

function Plazo({ venceEn, minutos }: { venceEn: string | null; minutos: number }) {
  const c = useCuentaRegresiva(venceEn);
  if (!venceEn) {
    return (
      <p className="t-sec flex gap-2 items-center">
        <CalendarClock className="ico ico-16 text-profundo" aria-hidden />
        {`Al confirmar separamos tu horario ${minutos} min para que pagues.`}
      </p>
    );
  }
  return (
    <div className={cx('plazo', c.vencido && 'vencido')} role="timer" aria-live="off">
      <TimerReset className="ico ico-20" aria-hidden />
      <span className="flex-1">{c.vencido ? 'Venció el plazo para pagar. Empieza de nuevo.' : 'Tiempo para pagar'}</span>
      {!c.vencido && <b aria-label={`Quedan ${c.texto}`}>{c.texto}</b>}
    </div>
  );
}

export default function Paso3({ alCambiarEnvio }: { alCambiarEnvio: (v: boolean) => void }) {
  const r = useReserva();
  const navigate = useNavigate();
  const toast = useToast();
  const { datos: complejo } = useComplejo();
  const metodos = useMetodosPago();
  const [conflicto, setConflicto] = useState(false);
  const [progreso, setProgreso] = useState<number | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    control,
    watch,
    setValue,
    setError,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<PagoEntrada>({
    resolver: zodResolver(esquemaPago),
    mode: 'onTouched',
    defaultValues: { metodoPago: r.pago.metodoPago ?? undefined, codigoOperacion: r.pago.codigoOperacion, archivo: r.archivo, tipoPago: r.pago.tipoPago },
  });

  useEffect(() => alCambiarEnvio(isSubmitting), [isSubmitting, alCambiarEnvio]);

  const metodo = watch('metodoPago');
  const tipoPago = watch('tipoPago') ?? 'ADELANTO';
  const pct = complejo?.reglas.adelantoPct ?? 50;
  const total = r.turno?.precio ?? r.reserva?.montoTotal ?? 0;
  const adelanto = r.reserva?.montoAdelanto ?? calcularAdelanto(total, pct);

  useEffect(() => {
    if (r.erroresServidor?.paso !== 3) return;
    for (const [campo, mensaje] of Object.entries(r.erroresServidor.errores)) {
      if (campo === 'codigoOperacion' || campo === 'archivo' || campo === 'metodoPago') setError(campo, { type: 'servidor', message: mensaje });
    }
    r.limpiarErroresServidor();
  }, [r, setError]);

  const opciones: OpcionMetodo[] = [
    { valor: 'YAPE', nombre: 'Yape', sub: 'Número + captura', icono: Smartphone },
    { valor: 'PLIN', nombre: 'Plin', sub: 'Número + captura', icono: Smartphone },
  ];
  if (metodos.datos?.tarjetaBeta !== false) opciones.push({ valor: 'TARJETA', nombre: 'Alcanfores Pay', sub: 'Tarjeta o Yape · beta', icono: CreditCard });
  if (metodos.datos?.mercadoPago) opciones.push({ valor: 'MERCADO_PAGO', nombre: 'Mercado Pago', sub: 'Tarjeta, Yape y más', icono: ExternalLink });

  const comprobante = (): DatosComprobante =>
    r.cliente?.tipoComprobante === 'FACTURA'
      ? { tipo: 'FACTURA', ruc: r.cliente.ruc, razonSocial: r.cliente.razonSocial, direccionFiscal: r.cliente.direccionFiscal }
      : { tipo: 'BOLETA' };

  const confirmar = handleSubmit(async (entrada) => {
    // zodResolver entrega los datos ya transformados (valores por defecto aplicados).
    const d = entrada as DatosPago;
    setAviso(null);
    if (!r.turno || !r.cliente) {
      r.irAPaso(r.turno ? 2 : 1);
      return;
    }
    const metodoPago = d.metodoPago as MetodoPagoWeb;
    r.guardarPago({ metodoPago, tipoPago: d.tipoPago, codigoOperacion: d.codigoOperacion });
    const dni = r.cliente.dni;

    // 1) Reserva PENDIENTE (si aún no existe).
    let reserva: ReservaCreada | null = r.reserva;
    if (!reserva) {
      try {
        reserva = await reservasApi.crear({
          // v2: varios turnos → idsTurno; uno solo se sigue mandando como idTurno.
          ...(r.turno.idsTurno.length > 1 ? { idsTurno: r.turno.idsTurno } : { idTurno: r.turno.idTurno }),
          cliente: { dni, nombres: r.cliente.nombres, apellidos: r.cliente.apellidos, telefono: String(r.cliente.telefono), correo: r.cliente.correo },
          metodoPago,
          aceptaTerminos: Boolean(r.cliente.aceptaTerminos),
          aceptaPrivacidad: Boolean(r.cliente.aceptaPrivacidad),
        });
        r.registrarReserva(reserva);
        guardarFacturacion(reserva.codigoReserva, comprobante());
      } catch (e) {
        if (esErrorApi(e) && (e.codigo === 'TURNO_OCUPADO' || e.status === 409)) {
          setConflicto(true);
          return;
        }
        const porCampo = erroresPorCampo(e);
        const delPaso2 = Object.fromEntries(Object.entries(porCampo).filter(([k]) => CAMPOS_PASO2.includes(k)));
        if (Object.keys(delPaso2).length) {
          toast.error('Revisa tus datos');
          r.ponerErroresServidor(2, delPaso2);
          return;
        }
        if (porCampo.metodoPago) setError('metodoPago', { type: 'servidor', message: porCampo.metodoPago });
        const errTurno = porCampo.idTurno ?? porCampo.idsTurno ?? Object.entries(porCampo).find(([k]) => k.startsWith('idsTurno'))?.[1];
        if (errTurno) {
          toast.error(errTurno);
          r.marcarConflicto();
          return;
        }
        setAviso(esErrorApi(e) ? e.message : 'No se pudo registrar. Intenta de nuevo.');
        return;
      }
    }

    // 2) Pago según el método.
    try {
      if (metodoPago === 'YAPE' || metodoPago === 'PLIN') {
        setProgreso(0);
        await pagosApi.enviarComprobante(
          { codigoReserva: reserva.codigoReserva, dni, metodo: metodoPago, codigoOperacion: d.codigoOperacion.trim(), archivo: d.archivo as File, comprobante: comprobante() },
          setProgreso,
        );
        guardarUltimaReserva({ reserva: { ...reserva, estadoPago: 'EN_REVISION', venceEn: null }, dni, metodo: metodoPago });
        r.reiniciar();
        navigate(`/reservar/listo/${reserva.codigoReserva}`);
      } else if (metodoPago === 'TARJETA') {
        const intento = await pagosApi.crearIntento({ codigoReserva: reserva.codigoReserva, dni, tipoPago: d.tipoPago, comprobante: comprobante() });
        guardarUltimaReserva({ reserva, dni, metodo: metodoPago });
        r.reiniciar();
        navigate(intento.urlCheckout.startsWith('http') ? new URL(intento.urlCheckout).pathname : intento.urlCheckout);
      } else {
        const pref = await pagosApi.preferenciaMp({ codigoReserva: reserva.codigoReserva, dni, tipoPago: d.tipoPago, comprobante: comprobante() });
        guardarUltimaReserva({ reserva, dni, metodo: metodoPago });
        r.reiniciar();
        window.location.assign(pref.initPoint);
      }
    } catch (e) {
      setProgreso(null);
      const porCampo = erroresPorCampo(e);
      // 409 sin campo del back: el número de operación ya sustenta otro pago.
      if (esErrorApi(e) && e.codigo === 'OPERACION_REPETIDA') porCampo.codigoOperacion = e.message;
      let ubicados = 0;
      for (const campo of ['codigoOperacion', 'archivo'] as const) {
        if (porCampo[campo]) {
          setError(campo, { type: 'servidor', message: porCampo[campo] });
          ubicados += 1;
        }
      }
      if (!ubicados) setAviso(`${esErrorApi(e) ? e.message : 'No se pudo completar el pago.'} Tu horario sigue separado: vuelve a intentar.`);
    }
  });

  const yape = metodos.datos?.yape ?? (complejo ? { numero: complejo.pagos.yapeNumero, titular: complejo.pagos.yapeTitular } : null);
  const plin = metodos.datos?.plin ?? (complejo ? { numero: complejo.pagos.plinNumero } : null);

  return (
    <div className="grid gap-5">
      <CabezaPaso
        titulo="Paga y confirma"
        ilustracion="IL-04-paso3-comprobante"
        volver={
          !r.reserva ? (
            <button type="button" className="btn btn-fantasma btn-sm justify-self-start -ml-3" onClick={() => r.irAPaso(2)}>
              <ArrowLeft className="ico" aria-hidden />
              <span>Volver</span>
            </button>
          ) : undefined
        }
        apoyo={
          <span className="inline-flex items-center gap-1.5">
            <ShieldCheck className="ico ico-14" aria-hidden />
            Se confirma cuando validamos tu adelanto
          </span>
        }
      />

      <form id="form-paso3" onSubmit={confirmar} noValidate className="grid gap-5">
        <Plazo venceEn={r.reserva?.venceEn ?? null} minutos={complejo?.reglas.minutosParaPagar ?? 20} />

        <section className="grid gap-3" aria-labelledby="h-metodo">
          <h2 id="h-metodo" className="inline-flex items-center gap-2 text-[14px] font-semibold">
            <Wallet className="ico ico-16 text-verde" aria-hidden />
            ¿Cómo pagas?
          </h2>
          <Controller
            name="metodoPago"
            control={control}
            render={({ field }) => (
              <div className={cx('metodos', errors.metodoPago && 'con-error')} style={{ ['--n-metodos' as string]: opciones.length }} role="radiogroup" aria-labelledby="h-metodo" aria-describedby={errors.metodoPago ? 'err-metodo' : undefined}>
                {opciones.map((o) => {
                  const I = o.icono;
                  return (
                    <button
                      key={o.valor}
                      type="button"
                      role="radio"
                      aria-checked={field.value === o.valor}
                      className="metodo"
                      onClick={() => {
                        field.onChange(o.valor);
                        clearErrors('metodoPago');
                        r.guardarPago({ metodoPago: o.valor });
                      }}
                    >
                      <span className="metodo-marca" aria-hidden />
                      <span className="metodo-ico">
                        <I className="ico ico-20" aria-hidden />
                      </span>
                      <span className="metodo-nom">{o.nombre}</span>
                      <span className="metodo-sub">{o.sub}</span>
                    </button>
                  );
                })}
              </div>
            )}
          />
          {errors.metodoPago && (
            <div className="campo-error" id="err-metodo" role="alert">
              <CircleAlert className="ico" aria-hidden />
              <span>{errors.metodoPago.message}</span>
            </div>
          )}
        </section>

        {(metodo === 'YAPE' || metodo === 'PLIN') && (
          <div className="grid gap-5 lg:grid-cols-2 animate-entrar">
            <DatosPago
              metodo={metodo}
              numero={(metodo === 'YAPE' ? yape?.numero : plin?.numero) ?? '—'}
              titular={yape?.titular ?? complejo?.nombre ?? 'Complejo Deportivo Los Alcanfores'}
              monto={adelanto}
            />
            <div className="grid gap-4 content-start">
              <CampoTexto
                label="N.º de operación"
                requerido
                icono={Hash}
                inputMode="numeric"
                maxLength={12}
                autoComplete="off"
                placeholder="6 a 12 dígitos"
                error={errors.codigoOperacion?.message}
                {...register('codigoOperacion', { setValueAs: (v: string) => String(v ?? '').replace(/\D/g, '') })}
              />
              <Controller
                name="archivo"
                control={control}
                render={({ field }) => (
                  <SubidaImagen
                    valor={(field.value as File | null) ?? null}
                    progreso={progreso}
                    error={errors.archivo?.message as string | undefined}
                    alError={(m) => (m ? setError('archivo', { type: 'manual', message: m }) : clearErrors('archivo'))}
                    alCambiar={(f) => {
                      field.onChange(f);
                      r.setArchivo(f);
                      if (f) setValue('archivo', f, { shouldValidate: true });
                    }}
                  />
                )}
              />
            </div>
          </div>
        )}

        {(metodo === 'TARJETA' || metodo === 'MERCADO_PAGO') && (
          <div className="card card-pad grid gap-4 animate-entrar">
            <Controller
              name="tipoPago"
              control={control}
              render={({ field }) => (
                <Segmentado
                  etiqueta="¿Cuánto pagas ahora?"
                  bloque
                  valor={field.value ?? 'ADELANTO'}
                  alCambiar={(v) => field.onChange(v)}
                  opciones={[
                    { valor: 'ADELANTO', texto: `Adelanto ${fmtSoles(adelanto)}`, icono: HandCoins },
                    { valor: 'TOTAL', texto: `Total ${fmtSoles(total)}`, icono: Wallet },
                  ]}
                />
              )}
            />
            <p className="t-sec flex gap-2 items-start">
              <ShieldCheck className="ico ico-16 text-profundo mt-0.5" aria-hidden />
              <span>
                {metodo === 'TARJETA'
                  ? `Te llevamos a Alcanfores Pay (beta) para pagar ${fmtSoles(tipoPago === 'TOTAL' ? total : adelanto)} con tarjeta o Yape. Tu reserva se confirma al instante.`
                  : `Te llevamos a Mercado Pago para pagar ${fmtSoles(tipoPago === 'TOTAL' ? total : adelanto)}. Al volver, verificamos tu pago.`}
              </span>
            </p>
          </div>
        )}

        {aviso && <Alerta tipo="error">{aviso}</Alerta>}

        <p className="t-sec flex gap-2 items-start">
          <HandCoins className="ico ico-16 text-profundo mt-0.5" aria-hidden />
          <span>
            {`Cancela con ${complejo?.reglas.devolucionHoras ?? 24} h o más y te devolvemos lo pagado. `}
            <a className="enlace" href="/terminos" target="_blank" rel="noopener">
              Ver términos
            </a>
          </span>
        </p>
        <button type="submit" className="sr-only" tabIndex={-1}>
          Confirmar reserva
        </button>
      </form>

      <Modal
        abierto={conflicto}
        alCerrar={() => {
          setConflicto(false);
          r.marcarConflicto();
        }}
        ilustracion="IL-10-conflicto"
        titulo="Alguien se adelantó"
        estatico
        acciones={
          <Boton
            bloque
            onClick={() => {
              setConflicto(false);
              r.marcarConflicto();
            }}
          >
            Elige otro horario
          </Boton>
        }
      >
        <p className="t-sec">Ese turno acaba de ser reservado por otra persona. Tus datos siguen guardados.</p>
      </Modal>
    </div>
  );
}
