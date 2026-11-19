import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  ArrowLeft,
  BadgeCheck,
  CircleCheck,
  CircleX,
  Clock,
  CreditCard,
  FlaskConical,
  KeyRound,
  Lock,
  Mail,
  ShieldCheck,
  Smartphone,
  Store,
  TimerOff,
  UserRound,
  Wallet,
} from 'lucide-react';
import { pagosApi, pasarelaApi } from '@/api/servicios';
import { esErrorApi } from '@/api/cliente';
import type { IntentoPasarela, ResultadoPasarela, TarjetaPrueba } from '@/api/tipos';
import { leerUltimaReserva } from '@/contexto/ReservaContext';
import { useCuenta } from '@/contexto/CuentaContext';
import { useCarga } from '@/hooks/useCarga';
import { useCuentaRegresiva } from '@/hooks/useCuentaRegresiva';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { Boton } from '@/componentes/ui/Boton';
import { CampoTexto } from '@/componentes/ui/Campo';
import { Alerta } from '@/componentes/ui/Estados';
import { Cargando } from '@/componentes/ui/Datos';
import { Modal } from '@/componentes/ui/Modal';
import { esquemaTarjeta, esquemaYapeBeta, type TarjetaEntrada, type YapeBetaEntrada } from '@/esquemas/otros';
import { detectarMarca, formatearTarjeta, type MarcaTarjeta } from '@/utilidades/documentos';
import { fmtSoles } from '@/utilidades/formato';
import { cx } from '@/utilidades/cx';

/** Tarjetas de prueba del SPEC (se usan si /api/pagos/metodos no las trae). */
const PRUEBAS_SPEC: TarjetaPrueba[] = [
  { numero: '4111 1111 1111 1111', marca: 'Visa', resultado: 'Aprobada' },
  { numero: '5555 5555 5555 4444', marca: 'Mastercard', resultado: 'Aprobada' },
  { numero: '3782 822463 10005', marca: 'Amex', resultado: 'Aprobada' },
  { numero: '4000 0027 6000 3184', marca: 'Visa', resultado: 'Pide 3-D Secure (123456)' },
  { numero: '4000 0000 0000 0002', marca: 'Visa', resultado: 'Rechazada: fondos insuficientes' },
  { numero: '4000 0000 0000 9995', marca: 'Visa', resultado: 'Rechazada: tarjeta bloqueada' },
  { numero: '4000 0000 0000 0069', marca: 'Visa', resultado: 'Rechazada: tarjeta vencida' },
];

const NOMBRE_MARCA: Record<MarcaTarjeta, string> = { VISA: 'Visa', MASTERCARD: 'Mastercard', AMEX: 'Amex', DINERS: 'Diners Club' };

function LogoMarca({ marca }: { marca: MarcaTarjeta | null }) {
  if (marca === 'MASTERCARD')
    return (
      <span className="tarjeta-marca mc" aria-label="Mastercard">
        <i />
        <i />
      </span>
    );
  if (!marca) return <CreditCard className="ico ico-24 opacity-70" aria-hidden />;
  return <span className="tarjeta-marca">{marca === 'VISA' ? 'VISA' : marca === 'AMEX' ? 'AMEX' : 'Diners'}</span>;
}

/** Tarjeta que se arma mientras escribes (gira para el CVV y cambia de color según la marca). */
function TarjetaAnimada({ numero, titular, vencimiento, cvv, volteada }: { numero: string; titular: string; vencimiento: string; cvv: string; volteada: boolean }) {
  const marca = detectarMarca(numero);
  const digitos = numero.replace(/\D/g, '');
  const plantilla = marca === 'AMEX' ? '#### ###### #####' : '#### #### #### ####';
  let i = 0;
  const mostrado = plantilla.replace(/#/g, () => digitos[i++] ?? '•');
  return (
    <div className={cx('tarjeta-3d', volteada && 'volteada')} data-marca={marca ?? undefined} aria-hidden>
      <div className="tarjeta-giro">
        <div className="tarjeta-cara">
          <div className="flex items-start justify-between relative">
            <span className="tarjeta-chip" />
            <LogoMarca marca={marca} />
          </div>
          <span className="tarjeta-numero relative">{mostrado}</span>
          <div className="tarjeta-pie relative">
            <span className="min-w-0">
              <small>Titular</small>
              <b className="block truncar">{titular.trim() ? titular.toUpperCase() : 'NOMBRE APELLIDO'}</b>
            </span>
            <span className="shrink-0 text-right">
              <small>Vence</small>
              <b className="tnum">{vencimiento || 'MM/AA'}</b>
            </span>
          </div>
        </div>
        <div className="tarjeta-cara tarjeta-dorso">
          <span className="tarjeta-banda" />
          <span className="tarjeta-firma">{cvv ? cvv.replace(/./g, '•') : 'CVV'}</span>
          <span className="self-end px-[22px] pb-1 text-[10.5px] opacity-70 relative">Alcanfores Pay · beta · solo pruebas</span>
        </div>
      </div>
    </div>
  );
}

function CabeceraPay() {
  return (
    <header className="pay-cab">
      <div className="contenedor pay-cab-in">
        <span className="pay-marca">
          <span className="pay-marca-ico">
            <Wallet className="ico ico-18" aria-hidden />
          </span>
          Alcanfores Pay
          <span className="pay-beta">beta</span>
        </span>
        <span className="pay-seguro">
          <Lock className="ico ico-14" aria-hidden />
          <span className="solo-tablet-arriba">Pago seguro · TLS</span>
        </span>
      </div>
    </header>
  );
}

function Resultado({ tipo, titulo, texto, detalle, accion }: { tipo: 'ok' | 'error' | 'vencido'; titulo: string; texto?: string; detalle?: ReactNode; accion: ReactNode }) {
  const Icono = tipo === 'ok' ? CircleCheck : tipo === 'vencido' ? TimerOff : CircleX;
  return (
    <div className="pay-caja p-6 md:p-10 grid gap-4 justify-items-center text-center max-w-[520px] mx-auto animate-entrar" role={tipo === 'ok' ? 'status' : 'alert'}>
      <span className={cx('grid place-items-center w-20 h-20 rounded-full', tipo === 'ok' ? 'bg-menta text-verde' : tipo === 'vencido' ? 'bg-amarillo-tinte text-noche' : 'bg-rojo-tinte text-rojo')}>
        <Icono className="ico ico-32" aria-hidden />
      </span>
      <h1 className="t-h1">{titulo}</h1>
      {texto && <p className="t-sec !text-[15px]">{texto}</p>}
      {detalle}
      {accion}
    </div>
  );
}

function FormTarjeta({ intento, pruebas, alResultado, correoInicial }: { intento: IntentoPasarela; pruebas: TarjetaPrueba[]; alResultado: (r: ResultadoPasarela) => void; correoInicial: string }) {
  const [volteada, setVolteada] = useState(false);
  const [tresDs, setTresDs] = useState(false);
  const [codigo3ds, setCodigo3ds] = useState('');
  const [error3ds, setError3ds] = useState<string | null>(null);
  const [verificando, setVerificando] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    control,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<TarjetaEntrada>({ resolver: zodResolver(esquemaTarjeta), mode: 'onTouched', defaultValues: { numero: '', titular: '', vencimiento: '', cvv: '', correo: correoInicial } });
  const v = watch();
  const marca = detectarMarca(v.numero ?? '');

  const autocompletar = (t: TarjetaPrueba) => {
    const amex = detectarMarca(t.numero) === 'AMEX';
    setValue('numero', formatearTarjeta(t.numero), { shouldValidate: true });
    setValue('titular', 'Prueba Alcanfores', { shouldValidate: true });
    setValue('vencimiento', '12/30', { shouldValidate: true });
    setValue('cvv', amex ? '1234' : '123', { shouldValidate: true });
    if (!v.correo) setValue('correo', 'pruebas@alcanfores.pe', { shouldValidate: true });
    setAviso(null);
  };

  const pagar = handleSubmit(async (d) => {
    setAviso(null);
    const [mm = '', aa = ''] = d.vencimiento.split('/');
    try {
      const r = await pasarelaApi.tarjeta(intento.referencia, { numero: d.numero, titular: d.titular.trim(), mesVencimiento: mm, anioVencimiento: `20${aa}`, cvv: d.cvv, correo: d.correo.trim() });
      if (r.estado === 'REQUIERE_3DS') {
        setCodigo3ds('');
        setError3ds(null);
        setTresDs(true);
      } else alResultado(r);
    } catch (e) {
      setAviso(esErrorApi(e) ? e.message : 'No se pudo procesar el pago.');
    }
  });

  const verificar = async () => {
    if (!/^\d{6}$/.test(codigo3ds)) {
      setError3ds('El código tiene 6 dígitos.');
      return;
    }
    setVerificando(true);
    try {
      const r = await pasarelaApi.tresDs(intento.referencia, codigo3ds);
      setTresDs(false);
      alResultado(r);
    } catch (e) {
      setError3ds(esErrorApi(e) ? e.message : 'No se pudo verificar.');
    } finally {
      setVerificando(false);
    }
  };

  return (
    <>
      <TarjetaAnimada numero={v.numero ?? ''} titular={v.titular ?? ''} vencimiento={v.vencimiento ?? ''} cvv={v.cvv ?? ''} volteada={volteada} />
      <form onSubmit={pagar} noValidate className="grid gap-4">
        <Controller
          name="numero"
          control={control}
          render={({ field }) => (
            <CampoTexto
              label="Número de tarjeta"
              requerido
              icono={CreditCard}
              inputMode="numeric"
              autoComplete="cc-number"
              placeholder="0000 0000 0000 0000"
              sufijo={marca ? NOMBRE_MARCA[marca] : undefined}
              error={errors.numero?.message}
              name={field.name}
              ref={field.ref}
              value={field.value}
              onBlur={field.onBlur}
              onChange={(e) => field.onChange(formatearTarjeta(e.target.value))}
            />
          )}
        />
        <CampoTexto label="Titular" requerido icono={UserRound} autoComplete="cc-name" maxLength={60} placeholder="Como figura en la tarjeta" error={errors.titular?.message} {...register('titular')} />
        <div className="grid grid-cols-2 gap-4">
          <Controller
            name="vencimiento"
            control={control}
            render={({ field }) => (
              <CampoTexto
                label="Vence"
                requerido
                inputMode="numeric"
                autoComplete="cc-exp"
                placeholder="MM/AA"
                maxLength={5}
                error={errors.vencimiento?.message}
                name={field.name}
                ref={field.ref}
                value={field.value}
                onBlur={field.onBlur}
                onChange={(e) => {
                  const d = e.target.value.replace(/\D/g, '').slice(0, 4);
                  field.onChange(d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d);
                }}
              />
            )}
          />
          <CampoTexto
            label="CVV"
            requerido
            icono={KeyRound}
            inputMode="numeric"
            autoComplete="cc-csc"
            type="password"
            maxLength={marca === 'AMEX' ? 4 : 3}
            placeholder={marca === 'AMEX' ? '4 dígitos' : '3 dígitos'}
            error={errors.cvv?.message}
            {...register('cvv', {
              onBlur: () => setVolteada(false),
              setValueAs: (x: string) => String(x ?? '').replace(/\D/g, ''),
            })}
            onFocus={() => setVolteada(true)}
          />
        </div>
        <CampoTexto label="Correo" requerido icono={Mail} type="email" autoComplete="email" ayuda="Te enviamos el voucher" error={errors.correo?.message} {...register('correo')} />
        {aviso && <Alerta tipo="error">{aviso}</Alerta>}
        <Boton type="submit" tamano="lg" bloque icono={Lock} cargando={isSubmitting} textoCargando="Procesando…">
          {`Pagar ${fmtSoles(intento.monto)}`}
        </Boton>
      </form>

      <div className="pruebas">
        <span className="inline-flex items-center gap-2 font-semibold text-[13.5px]">
          <FlaskConical className="ico ico-16 text-amarillo-ocre" aria-hidden />
          Modo beta: tarjetas de prueba
        </span>
        <div className="grid gap-1.5">
          {pruebas.map((t) => (
            <button key={t.numero} type="button" className="prueba" onClick={() => autocompletar(t)} aria-label={`Usar tarjeta de prueba ${t.numero}: ${t.resultado}`}>
              <CreditCard className="ico ico-16 text-gris" aria-hidden />
              <code>{t.numero}</code>
              <span>{t.resultado}</span>
            </button>
          ))}
        </div>
        <span className="t-mini">Vence 12/30 · CVV 123 (Amex 1234). No se cobra nada.</span>
      </div>

      <Modal
        abierto={tresDs}
        alCerrar={() => setTresDs(false)}
        titulo="Verificación 3-D Secure"
        subtitulo="Tu banco pide confirmar esta compra"
        icono={ShieldCheck}
        tamano="sm"
        estatico
        acciones={
          <>
            <Boton variante="secundario" onClick={() => setTresDs(false)}>
              Cancelar
            </Boton>
            <Boton icono={BadgeCheck} cargando={verificando} onClick={verificar}>
              Verificar
            </Boton>
          </>
        }
      >
        <div className="grid gap-3">
          <p className="t-sec">{`Enviamos un código a tu celular para pagar ${fmtSoles(intento.monto)} a ${intento.comercio}.`}</p>
          <CampoTexto
            label="Código de verificación"
            requerido
            inputMode="numeric"
            maxLength={6}
            autoComplete="one-time-code"
            value={codigo3ds}
            data-autofoco
            error={error3ds ?? undefined}
            ayuda="Prueba: 123456 aprueba"
            onChange={(e) => {
              setCodigo3ds(e.target.value.replace(/\D/g, '').slice(0, 6));
              setError3ds(null);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                void verificar();
              }
            }}
          />
        </div>
      </Modal>
    </>
  );
}

function FormYape({ intento, aprueba, rechaza, alResultado }: { intento: IntentoPasarela; aprueba: string; rechaza: string; alResultado: (r: ResultadoPasarela) => void }) {
  const [aviso, setAviso] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<YapeBetaEntrada>({ resolver: zodResolver(esquemaYapeBeta), mode: 'onTouched', defaultValues: { celular: '', codigoAprobacion: '' } });
  const pagar = handleSubmit(async (d) => {
    setAviso(null);
    try {
      alResultado(await pasarelaApi.yape(intento.referencia, { celular: d.celular, codigoAprobacion: d.codigoAprobacion }));
    } catch (e) {
      setAviso(esErrorApi(e) ? e.message : 'No se pudo procesar el pago.');
    }
  });
  return (
    <form onSubmit={pagar} noValidate className="grid gap-4">
      <div className="flex items-center gap-3 p-4 rounded-14 bg-menta">
        <Smartphone className="ico ico-24 text-profundo" aria-hidden />
        <p className="text-[14px]">Abre tu app, busca «Código de aprobación» y escríbelo aquí.</p>
      </div>
      <CampoTexto label="Celular Yape" requerido prefijo="+51" type="tel" inputMode="tel" autoComplete="tel-national" placeholder="987 654 321" error={errors.celular?.message} {...register('celular')} />
      <CampoTexto label="Código de aprobación" requerido icono={KeyRound} inputMode="numeric" maxLength={6} autoComplete="one-time-code" placeholder="6 dígitos" error={errors.codigoAprobacion?.message} {...register('codigoAprobacion')} />
      {aviso && <Alerta tipo="error">{aviso}</Alerta>}
      <Boton type="submit" tamano="lg" bloque icono={Lock} cargando={isSubmitting} textoCargando="Procesando…">
        {`Pagar ${fmtSoles(intento.monto)}`}
      </Boton>
      <div className="pruebas">
        <span className="inline-flex items-center gap-2 font-semibold text-[13.5px]">
          <FlaskConical className="ico ico-16 text-amarillo-ocre" aria-hidden />
          Modo beta: códigos de prueba
        </span>
        <span className="text-[13px]">
          <code className="font-bold">{aprueba}</code> aprueba · <code className="font-bold">{rechaza}</code> rechaza (saldo insuficiente)
        </span>
      </div>
    </form>
  );
}

/** /pasarela/:ref — checkout de «Alcanfores Pay · beta» con aspecto de proveedor externo. */
export default function Pasarela() {
  useTituloPagina('Alcanfores Pay · beta');
  const { ref = '' } = useParams();
  const navigate = useNavigate();
  const intento = useCarga(() => pasarelaApi.intento(ref), ref);
  const metodos = useCarga(() => pagosApi.metodos(), 'metodos');
  const [pestana, setPestana] = useState<'tarjeta' | 'yape'>('tarjeta');
  const [resultado, setResultado] = useState<ResultadoPasarela | null>(null);
  const reloj = useCuentaRegresiva(intento.datos?.expiraEn);
  const ultima = intento.datos ? leerUltimaReserva(intento.datos.codigoReserva) : null;
  const { cliente } = useCuenta();
  const pruebas = useMemo(() => (metodos.datos?.tarjetasPrueba?.length ? metodos.datos.tarjetasPrueba : PRUEBAS_SPEC), [metodos.datos]);

  useEffect(() => {
    document.documentElement.style.background = '#EEF2F0';
    return () => {
      document.documentElement.style.background = '';
    };
  }, []);

  const volver = () => navigate(intento.datos ? `/reservar/listo/${intento.datos.codigoReserva}` : '/');
  const i = intento.datos;
  const estado = resultado?.estado ?? i?.estado;

  let cuerpo: ReactNode;
  if (intento.cargando) cuerpo = <Cargando texto="Cargando pago…" />;
  else if (intento.error || !i) {
    cuerpo = (
      <Resultado
        tipo="error"
        titulo="Pago no encontrado"
        texto={esErrorApi(intento.error) && intento.error.status !== 404 ? intento.error.message : 'Este enlace de pago no existe o ya no está disponible.'}
        accion={
          <Boton tamano="lg" bloque variante="secundario" icono={ArrowLeft} onClick={() => navigate('/mi-reserva')}>
            Ir a Mi reserva
          </Boton>
        }
      />
    );
  } else if (estado === 'APROBADO') {
    cuerpo = (
      <Resultado
        tipo="ok"
        titulo="Pago aprobado"
        texto={`${fmtSoles(i.monto)} a ${i.comercio}`}
        detalle={
          <div className="grid gap-1 text-[14px] bg-superficie-suave rounded-12 px-4 py-3 w-full">
            {resultado?.codigoAutorizacion && (
              <span className="flex justify-between gap-3">
                <span className="t-sec">Autorización</span>
                <b className="tnum">{resultado.codigoAutorizacion}</b>
              </span>
            )}
            {resultado?.ultimos4 && (
              <span className="flex justify-between gap-3">
                <span className="t-sec">Medio</span>
                <b className="tnum">{`${resultado.marca ?? 'Tarjeta'} •••• ${resultado.ultimos4}`}</b>
              </span>
            )}
            <span className="flex justify-between gap-3">
              <span className="t-sec">Referencia</span>
              <b className="tnum">{i.referencia}</b>
            </span>
          </div>
        }
        accion={
          <Boton tamano="lg" bloque icono={Store} onClick={volver}>
            Volver al comercio
          </Boton>
        }
      />
    );
  } else if (estado === 'EXPIRADO' || reloj.vencido) {
    cuerpo = (
      <Resultado
        tipo="vencido"
        titulo="El tiempo para pagar terminó"
        texto="Vuelve al comercio para intentarlo de nuevo."
        accion={
          <Boton tamano="lg" bloque icono={Store} onClick={volver}>
            Volver al comercio
          </Boton>
        }
      />
    );
  } else if (resultado?.estado === 'RECHAZADO') {
    cuerpo = (
      <Resultado
        tipo="error"
        titulo="Pago rechazado"
        texto={resultado.motivo ?? 'El emisor rechazó la operación.'}
        accion={
          <div className="grid gap-2 w-full">
            <Boton tamano="lg" bloque icono={CreditCard} onClick={() => setResultado(null)}>
              Intentar de nuevo
            </Boton>
            <Boton variante="fantasma" bloque icono={ArrowLeft} onClick={volver}>
              Volver al comercio
            </Boton>
          </div>
        }
      />
    );
  } else {
    cuerpo = (
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.15fr)] lg:gap-8 lg:items-start max-w-[1040px] mx-auto">
        <section className="pay-caja p-5 md:p-7 grid gap-5" aria-label="Detalle del pago">
          <div className="flex items-center gap-3">
            <span className="grid place-items-center w-12 h-12 rounded-14 bg-noche text-white shrink-0">
              <Store className="ico ico-20" aria-hidden />
            </span>
            <div className="grid min-w-0">
              <span className="t-mini uppercase tracking-[.1em] font-bold">Comercio</span>
              <b className="text-[15.5px] leading-tight">{i.comercio}</b>
            </div>
          </div>
          <div className="grid gap-1">
            <span className="t-sec">{i.concepto}</span>
            <span className="pay-monto">{fmtSoles(i.monto)}</span>
            <span className="t-mini">{i.moneda === 'PEN' ? 'Soles (PEN)' : i.moneda}</span>
          </div>
          <dl className="grid gap-2 text-[13.5px] m-0">
            <div className="flex justify-between gap-3">
              <dt className="t-sec">Reserva</dt>
              <dd className="m-0 font-semibold tnum">{i.codigoReserva}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="t-sec">Referencia</dt>
              <dd className="m-0 font-semibold tnum">{i.referencia}</dd>
            </div>
          </dl>
          <div className={cx('plazo', reloj.restanteMs < 120000 && 'vencido')} role="timer">
            <Clock className="ico ico-20" aria-hidden />
            <span className="flex-1">Tiempo para pagar</span>
            <b>{reloj.texto}</b>
          </div>
          <button type="button" className="enlace bg-transparent border-0 p-0 min-h-[40px] justify-self-start inline-flex items-center gap-1.5 text-[14px]" onClick={volver}>
            <ArrowLeft className="ico ico-16" aria-hidden />
            Cancelar y volver al comercio
          </button>
        </section>

        <section className="pay-caja p-5 md:p-7 grid gap-5" aria-label="Medio de pago">
          <div className="pay-tabs" role="tablist" aria-label="Medio de pago">
            <button type="button" role="tab" aria-selected={pestana === 'tarjeta'} className="pay-tab" onClick={() => setPestana('tarjeta')}>
              <CreditCard className="ico ico-18" aria-hidden />
              Tarjeta
            </button>
            <button type="button" role="tab" aria-selected={pestana === 'yape'} className="pay-tab" onClick={() => setPestana('yape')}>
              <Smartphone className="ico ico-18" aria-hidden />
              Yape
            </button>
          </div>
          {pestana === 'tarjeta' ? (
            <FormTarjeta intento={i} pruebas={pruebas} alResultado={setResultado} correoInicial={ultima?.reserva.cliente.correo ?? cliente?.correo ?? ''} />
          ) : (
            <FormYape intento={i} aprueba={metodos.datos?.yapePrueba.codigoAprueba ?? '123456'} rechaza={metodos.datos?.yapePrueba.codigoRechaza ?? '000000'} alResultado={setResultado} />
          )}
        </section>
      </div>
    );
  }

  return (
    <div className="pay">
      <CabeceraPay />
      <main id="contenido" className="contenedor py-6 md:py-10 grid gap-6">
        {cuerpo}
        <p className="pay-pie">
          <span className="inline-flex items-center gap-1.5">
            <ShieldCheck className="ico ico-14" aria-hidden />
            No guardamos el número completo ni el CVV
          </span>
          <span>Pasarela de prueba del proyecto UTP · sin cobros reales</span>
        </p>
      </main>
    </div>
  );
}
