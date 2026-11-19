import { useCallback, useEffect, useRef, useState, type ClipboardEvent, type KeyboardEvent, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  ArrowLeft,
  ArrowRight,
  AtSign,
  Check,
  ChevronRight,
  CircleCheck,
  Eye,
  EyeOff,
  Fingerprint,
  FlaskConical,
  KeyRound,
  LogIn,
  Mail,
  MessageCircle,
  RotateCw,
  Save,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react';
import { esErrorApi } from '@/api/cliente';
import { recuperarApi } from '@/api/servicios';
import type { CanalRecuperacion, CodigoEnviado, OpcionCanal, TipoCuenta } from '@/api/tipos';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { Boton, BotonEnlace } from '@/componentes/ui/Boton';
import { CampoTexto } from '@/componentes/ui/Campo';
import { Spinner } from '@/componentes/ui/Datos';
import { Alerta } from '@/componentes/ui/Estados';
import { Ilustracion } from '@/componentes/ui/Marca';
import { ReglasClave, errorClave } from '@/componentes/cuenta/ReglasClave';
import {
  esquemaNuevaClave,
  esquemaRecuperarAdmin,
  esquemaRecuperarCliente,
  type NuevaClaveEntrada,
  type RecuperarEntrada,
} from '@/esquemas/otros';
import { deLima } from '@/utilidades/fecha';
import { cx } from '@/utilidades/cx';
import type { IdIlustracion } from '@/utilidades/recursos';

/** 1 cuenta · 2 canal · 3 código · 4 contraseña nueva · 5 listo. */
export type PasoRecuperar = 1 | 2 | 3 | 4 | 5;
export const ILUSTRACION_PASO: Record<PasoRecuperar, IdIlustracion> = {
  1: 'IL-16-consulta',
  2: 'IL-21-compartir',
  3: 'IL-11-en-revision',
  4: 'IL-20-privacidad',
  5: 'IL-12-confirmada',
};

const CANALES: Record<CanalRecuperacion, { titulo: string; icono: LucideIcon; sinDato: string }> = {
  CORREO: { titulo: 'Correo', icono: Mail, sinDato: 'Sin correo registrado' },
  WHATSAPP: { titulo: 'WhatsApp', icono: MessageCircle, sinDato: 'Sin celular registrado' },
};
const LARGO_CODIGO = 6;

/** `reenviarEn` / `expiraEn` llegan como fecha-hora de Lima (o con zona) o como segundos. */
function aMs(v: string | number | null | undefined, recibido: number): number | null {
  if (v === null || v === undefined || v === '') return null;
  if (typeof v === 'number') return recibido + v * 1000;
  if (/^\d+$/.test(v)) return recibido + Number(v) * 1000;
  if (/(Z|[+-]\d\d:\d\d)$/.test(v)) return new Date(v).getTime();
  return deLima(v).valueOf();
}
function mmss(ms: number): string {
  const s = Math.max(0, Math.ceil(ms / 1000));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}
/** Reloj de un segundo mientras haga falta (contadores de reenvío y vencimiento). */
function useAhora(activo: boolean): number {
  const [ahora, setAhora] = useState(() => Date.now());
  useEffect(() => {
    if (!activo) return undefined;
    const t = window.setInterval(() => setAhora(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, [activo]);
  return ahora;
}

interface Envio extends CodigoEnviado {
  canal: CanalRecuperacion;
  reenviarMs: number | null;
  expiraMs: number | null;
}

/**
 * Asistente de 4 pasos para recuperar la contraseña (SPEC_V2 · «Recuperar contraseña y correo»).
 * Lo usan /recuperar (clientes) y /admin/recuperar (panel). El contenedor pinta la ilustración grande con `alCambiarPaso`.
 */
export function AsistenteRecuperar({
  tipoCuenta,
  rutaIngresar,
  alCambiarPaso,
  claseIlustracionMovil = 'solo-telefono',
}: {
  tipoCuenta: TipoCuenta;
  rutaIngresar: string;
  alCambiarPaso?: (p: PasoRecuperar) => void;
  claseIlustracionMovil?: string;
}) {
  const esCliente = tipoCuenta === 'CLIENTE';
  const [paso, setPasoInterno] = useState<PasoRecuperar>(1);
  const [usuario, setUsuario] = useState('');
  const [canales, setCanales] = useState<OpcionCanal[]>([]);
  const [envio, setEnvio] = useState<Envio | null>(null);
  const [enviando, setEnviando] = useState<CanalRecuperacion | null>(null);
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const titulo = useRef<HTMLHeadingElement>(null);

  const setPaso = useCallback(
    (p: PasoRecuperar) => {
      setPasoInterno(p);
      alCambiarPaso?.(p);
    },
    [alCambiarPaso],
  );
  // Al cambiar de paso, el foco va al título (lectores de pantalla) y la vista arriba.
  useEffect(() => {
    if (paso === 1) return;
    titulo.current?.focus({ preventScroll: true });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [paso]);

  const enviarCodigo = async (canal: CanalRecuperacion) => {
    setEnviando(canal);
    setErrorEnvio(null);
    try {
      const r = await recuperarApi.enviar(tipoCuenta, usuario, canal);
      const ahora = Date.now();
      setEnvio({ ...r, canal, reenviarMs: aMs(r.reenviarEnSegundos ?? r.reenviarEn, ahora), expiraMs: aMs(r.expiraEn, ahora) });
      setPaso(3);
    } catch (e) {
      setErrorEnvio(esErrorApi(e) ? e.message : 'No pudimos enviar el código. Intenta de nuevo.');
    } finally {
      setEnviando(null);
    }
  };

  const reiniciar = () => {
    setEnvio(null);
    setToken(null);
    setErrorEnvio(null);
    setPaso(1);
  };

  const cabecera = (t: string, sub?: ReactNode) => (
    <div className="grid gap-1">
      <Ilustracion id={ILUSTRACION_PASO[paso]} carga="eager" className={cx('w-[120px] h-[120px] justify-self-center -mt-1 mb-1', claseIlustracionMovil)} />
      {paso <= 4 && <PuntosPaso paso={paso} />}
      <h1 className="t-h1 outline-none" ref={titulo} tabIndex={-1}>
        {t}
      </h1>
      {sub && <p className="t-sec">{sub}</p>}
    </div>
  );

  return (
    <div className="grid gap-5">
      {paso === 1 && (
        <PasoCuenta
          esCliente={esCliente}
          cabecera={cabecera}
          rutaIngresar={rutaIngresar}
          inicial={usuario}
          alEncontrar={(u, c) => {
            setUsuario(u);
            setCanales(c);
            setErrorEnvio(null);
            setPaso(2);
          }}
          tipoCuenta={tipoCuenta}
        />
      )}

      {paso === 2 && (
        <>
          {cabecera('¿Dónde recibes el código?')}
          {canales.length === 0 ? (
            <Alerta tipo="advertencia" titulo="Tu cuenta no tiene correo ni celular">
              {esCliente ? 'Escríbenos por WhatsApp o acércate al complejo para ayudarte.' : 'Pide a otro administrador que la restablezca desde «Usuarios».'}
            </Alerta>
          ) : (
            <div className="grid gap-3" role="list">
              {(['CORREO', 'WHATSAPP'] as const).map((c) => {
                const op = canales.find((x) => x.canal === c);
                const def = CANALES[c];
                const Icono = def.icono;
                return (
                  <button
                    key={c}
                    type="button"
                    role="listitem"
                    className={cx('rec-canal', c === 'WHATSAPP' && 'wa')}
                    disabled={!op || enviando !== null}
                    aria-label={op ? `Recibir el código por ${def.titulo} en ${op.destino}` : `${def.titulo}: ${def.sinDato}`}
                    onClick={() => op && enviarCodigo(c)}
                  >
                    <span className="rec-canal-ico">
                      <Icono className="ico ico-24" aria-hidden />
                    </span>
                    <span className="grid gap-0.5 text-left min-w-0">
                      <b>{def.titulo}</b>
                      <span className="tnum truncate">{op ? op.destino : def.sinDato}</span>
                    </span>
                    {enviando === c ? <Spinner className="ml-auto" /> : op && <ChevronRight className="ico ml-auto text-gris" aria-hidden />}
                  </button>
                );
              })}
            </div>
          )}
          {errorEnvio && <Alerta tipo="error">{errorEnvio}</Alerta>}
          <Boton variante="fantasma" icono={ArrowLeft} className="justify-self-start" onClick={() => setPaso(1)} disabled={enviando !== null}>
            Volver
          </Boton>
        </>
      )}

      {paso === 3 && envio && (
        <PasoCodigo
          key={`${envio.canal}-${envio.expiraEn}`}
          cabecera={cabecera}
          envio={envio}
          canales={canales}
          enviando={enviando}
          errorEnvio={errorEnvio}
          alReenviar={enviarCodigo}
          alVerificar={async (codigo) => {
            const r = await recuperarApi.verificar(tipoCuenta, usuario, codigo);
            setToken(r.tokenRestablecer);
            setPaso(4);
          }}
          alVolver={() => setPaso(2)}
        />
      )}

      {paso === 4 && token && <PasoClave cabecera={cabecera} token={token} alListo={() => setPaso(5)} alReiniciar={reiniciar} />}

      {paso === 5 && (
        <>
          {cabecera('Contraseña actualizada', 'Ya puedes ingresar con tu nueva contraseña.')}
          <div className="rec-listo" aria-hidden>
            <CircleCheck className="ico ico-24" />
          </div>
          <BotonEnlace to={rutaIngresar} tamano="lg" bloque icono={LogIn}>
            Ingresar
          </BotonEnlace>
        </>
      )}
    </div>
  );
}

/** Indicador «Paso n de 4» con 4 barras. */
function PuntosPaso({ paso }: { paso: number }) {
  return (
    <div className="rec-pasos" aria-label={`Paso ${paso} de 4`}>
      {[1, 2, 3, 4].map((n) => (
        <span key={n} className={cx(n < paso && 'hecho', n === paso && 'actual')} />
      ))}
      <small className="tnum">Paso {paso} de 4</small>
    </div>
  );
}

type Cabecera = (t: string, sub?: ReactNode) => ReactNode;

// ------------------------------ paso 1: la cuenta ------------------------------
function PasoCuenta({
  esCliente,
  cabecera,
  rutaIngresar,
  inicial,
  tipoCuenta,
  alEncontrar,
}: {
  esCliente: boolean;
  cabecera: Cabecera;
  rutaIngresar: string;
  inicial: string;
  tipoCuenta: TipoCuenta;
  alEncontrar: (usuario: string, canales: OpcionCanal[]) => void;
}) {
  const [aviso, setAviso] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RecuperarEntrada>({
    resolver: zodResolver(esCliente ? esquemaRecuperarCliente : esquemaRecuperarAdmin),
    mode: 'onTouched',
    defaultValues: { usuario: inicial },
  });
  const enviar = handleSubmit(async (d) => {
    setAviso(null);
    const usuario = d.usuario.trim().toLowerCase();
    try {
      const r = await recuperarApi.opciones(tipoCuenta, usuario);
      alEncontrar(usuario, r.canales ?? []);
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['usuario']);
      if (aplicados) return;
      if (esErrorApi(e) && (e.status === 404 || e.codigo === 'CUENTA_NO_ENCONTRADA'))
        setError('usuario', { type: 'servidor', message: e.message || (esCliente ? 'No encontramos una cuenta con ese DNI o correo.' : 'No encontramos ese usuario.') }, { shouldFocus: true });
      else setAviso(esErrorApi(e) ? e.message : 'No pudimos buscar tu cuenta. Intenta de nuevo.');
    }
  });
  return (
    <form className="grid gap-5" onSubmit={enviar} noValidate>
      {cabecera('¿Olvidaste tu contraseña?', esCliente ? 'Escribe tu DNI o correo y te enviamos un código.' : 'Escribe tu usuario o correo y te enviamos un código.')}
      <CampoTexto
        label={esCliente ? 'DNI o correo' : 'Usuario o correo'}
        requerido
        icono={esCliente ? Fingerprint : AtSign}
        autoComplete="username"
        autoCapitalize="none"
        spellCheck={false}
        maxLength={100}
        autoFocus
        error={errors.usuario?.message}
        {...register('usuario')}
      />
      {aviso && <Alerta tipo="error">{aviso}</Alerta>}
      <Boton type="submit" tamano="lg" bloque icono={ArrowRight} cargando={isSubmitting} textoCargando="Buscando…">
        Continuar
      </Boton>
      <p className="t-mini text-center">
        ¿La recordaste?{' '}
        <Link to={rutaIngresar} className="enlace">
          Volver a ingresar
        </Link>
      </p>
    </form>
  );
}

// ------------------------------ paso 3: el código ------------------------------
function PasoCodigo({
  cabecera,
  envio,
  canales,
  enviando,
  errorEnvio,
  alReenviar,
  alVerificar,
  alVolver,
}: {
  cabecera: Cabecera;
  envio: Envio;
  canales: OpcionCanal[];
  enviando: CanalRecuperacion | null;
  errorEnvio: string | null;
  alReenviar: (c: CanalRecuperacion) => void;
  alVerificar: (codigo: string) => Promise<void>;
  alVolver: () => void;
}) {
  const [codigo, setCodigo] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [bloqueado, setBloqueado] = useState(false);
  const [verificando, setVerificando] = useState(false);
  const ahora = useAhora(true);
  const faltaReenvio = envio.reenviarMs ? envio.reenviarMs - ahora : 0;
  const faltaVence = envio.expiraMs ? envio.expiraMs - ahora : null;
  const vencido = faltaVence !== null && faltaVence <= 0;
  const otro = canales.find((c) => c.canal !== envio.canal);
  const def = CANALES[envio.canal];
  const IconoCanal = def.icono;

  const verificar = async (valor: string) => {
    if (valor.length !== LARGO_CODIGO || verificando || bloqueado) return;
    setVerificando(true);
    setError(null);
    try {
      await alVerificar(valor);
    } catch (e) {
      const cod = esErrorApi(e) ? e.codigo : '';
      const campo = esErrorApi(e) ? e.errores.find((x) => x.campo === 'codigo')?.mensaje : undefined;
      const msj =
        campo ??
        (esErrorApi(e) && e.message
          ? e.message
          : cod === 'CODIGO_VENCIDO'
            ? 'El código venció. Pide otro.'
            : cod === 'DEMASIADOS_INTENTOS'
              ? 'Muchos intentos. Pide un código nuevo.'
              : 'El código no es correcto.');
      setError(msj);
      if (cod === 'DEMASIADOS_INTENTOS' || cod === 'CODIGO_VENCIDO') setBloqueado(true);
      setCodigo('');
    } finally {
      setVerificando(false);
    }
  };

  return (
    <>
      {cabecera(
        'Escribe el código',
        <span className="inline-flex items-center gap-1.5 flex-wrap">
          <IconoCanal className="ico ico-16" aria-hidden />
          Lo enviamos a <b className="tnum text-noche">{envio.destino}</b>
        </span>,
      )}

      {envio.simulado && (
        <Alerta tipo="advertencia" titulo={`${def.titulo} aún no configurado`} icono={TriangleAlert}>
          El complejo todavía no envía por {def.titulo === 'Correo' ? 'correo' : 'WhatsApp'}: el código no salió. {otro ? `Prueba por ${CANALES[otro.canal].titulo === 'Correo' ? 'correo' : 'WhatsApp'}.` : 'Pide ayuda en el complejo.'}
        </Alerta>
      )}
      {envio.codigoPrueba && (
        <div className="rec-prueba">
          <span className="inline-flex items-center gap-2 font-semibold text-[13px]">
            <FlaskConical className="ico ico-16 text-amarillo-ocre" aria-hidden />
            Modo prueba
          </span>
          <b className="tnum">{envio.codigoPrueba}</b>
          <Boton
            variante="secundario"
            tamano="sm"
            disabled={bloqueado || vencido}
            onClick={() => {
              const c = envio.codigoPrueba ?? '';
              setCodigo(c);
              void verificar(c);
            }}
          >
            Usar
          </Boton>
        </div>
      )}

      <div className="grid gap-2">
        <CodigoOtp
          valor={codigo}
          alCambiar={(v) => {
            setCodigo(v);
            setError(null);
            if (v.length === LARGO_CODIGO) void verificar(v);
          }}
          error={Boolean(error)}
          deshabilitado={verificando || bloqueado || vencido}
        />
        {error ? (
          <div className="campo-error justify-center" role="alert">
            <TriangleAlert className="ico" aria-hidden />
            <span>{error}</span>
          </div>
        ) : vencido ? (
          <div className="campo-error justify-center" role="alert">
            <TriangleAlert className="ico" aria-hidden />
            <span>El código venció. Pide otro.</span>
          </div>
        ) : (
          faltaVence !== null && (
            <small className="t-mini text-center tnum" aria-live="off">
              Vence en {mmss(faltaVence)}
            </small>
          )
        )}
      </div>

      <Boton tamano="lg" bloque icono={Check} cargando={verificando} textoCargando="Verificando…" disabled={codigo.length !== LARGO_CODIGO || bloqueado || vencido} onClick={() => void verificar(codigo)}>
        Verificar
      </Boton>

      {errorEnvio && <Alerta tipo="error">{errorEnvio}</Alerta>}

      <div className="rec-acciones">
        <Boton variante="fantasma" tamano="sm" icono={RotateCw} disabled={faltaReenvio > 0 || enviando !== null} cargando={enviando === envio.canal} textoCargando="Enviando…" onClick={() => alReenviar(envio.canal)}>
          {faltaReenvio > 0 ? <span className="tnum">Reenviar en {mmss(faltaReenvio)}</span> : 'Reenviar código'}
        </Boton>
        {otro && (
          <Boton variante="fantasma" tamano="sm" icono={CANALES[otro.canal].icono} disabled={enviando !== null} cargando={enviando === otro.canal} textoCargando="Enviando…" onClick={() => alReenviar(otro.canal)}>
            {otro.canal === 'WHATSAPP' ? 'Mejor por WhatsApp' : 'Mejor por correo'}
          </Boton>
        )}
      </div>
      <Boton variante="fantasma" icono={ArrowLeft} className="justify-self-start" onClick={alVolver}>
        Volver
      </Boton>
    </>
  );
}

/** 6 casillas: solo dígitos, avance automático, retroceso, flechas y pegar el código completo. */
function CodigoOtp({ valor, alCambiar, error, deshabilitado }: { valor: string; alCambiar: (v: string) => void; error: boolean; deshabilitado: boolean }) {
  const refs = useRef<Array<HTMLInputElement | null>>([]);
  const enfocar = (i: number) => refs.current[Math.max(0, Math.min(LARGO_CODIGO - 1, i))]?.focus();
  // Al vaciarse (código incorrecto) vuelve a la primera casilla.
  useEffect(() => {
    if (valor === '' && !deshabilitado) enfocar(0);
  }, [valor, deshabilitado]);

  const poner = (i: number, digitos: string) => {
    const arr = valor.padEnd(LARGO_CODIGO, ' ').split('');
    let j = i;
    for (const d of digitos) {
      if (j >= LARGO_CODIGO) break;
      arr[j] = d;
      j += 1;
    }
    const nuevo = arr.join('').replace(/\s+$/, '');
    alCambiar(nuevo);
    enfocar(j);
  };
  const alTeclear = (i: number, e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace') {
      e.preventDefault();
      const arr = valor.split('');
      if (arr[i] !== undefined && arr[i] !== ' ') {
        arr[i] = ' ';
        alCambiar(arr.join('').replace(/\s+$/, ''));
      } else if (i > 0) {
        arr[i - 1] = ' ';
        alCambiar(arr.join('').replace(/\s+$/, ''));
        enfocar(i - 1);
      }
    } else if (e.key === 'ArrowLeft') enfocar(i - 1);
    else if (e.key === 'ArrowRight') enfocar(i + 1);
  };
  const alPegar = (i: number, e: ClipboardEvent<HTMLInputElement>) => {
    const d = e.clipboardData.getData('text').replace(/\D/g, '');
    if (!d) return;
    e.preventDefault();
    poner(d.length >= LARGO_CODIGO ? 0 : i, d.slice(0, LARGO_CODIGO));
  };

  return (
    <div className={cx('otp', error && 'is-error')} role="group" aria-label="Código de 6 dígitos">
      {Array.from({ length: LARGO_CODIGO }, (_, i) => {
        const c = valor[i];
        return (
          <input
            key={i}
            ref={(el) => {
              refs.current[i] = el;
            }}
            inputMode="numeric"
            autoComplete={i === 0 ? 'one-time-code' : 'off'}
            pattern="[0-9]*"
            maxLength={LARGO_CODIGO}
            aria-label={`Dígito ${i + 1}`}
            aria-invalid={error || undefined}
            disabled={deshabilitado}
            value={c && c !== ' ' ? c : ''}
            autoFocus={i === 0}
            onFocus={(e) => e.currentTarget.select()}
            onKeyDown={(e) => alTeclear(i, e)}
            onPaste={(e) => alPegar(i, e)}
            onChange={(e) => {
              const d = e.target.value.replace(/\D/g, '');
              if (d) poner(d.length >= LARGO_CODIGO ? 0 : i, d.length > 1 && d.length < LARGO_CODIGO ? d.slice(-1) : d);
            }}
          />
        );
      })}
    </div>
  );
}

// ------------------------------ paso 4: contraseña nueva ------------------------------
function PasoClave({ cabecera, token, alListo, alReiniciar }: { cabecera: Cabecera; token: string; alListo: () => void; alReiniciar: () => void }) {
  const [ver, setVer] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<NuevaClaveEntrada>({ resolver: zodResolver(esquemaNuevaClave), mode: 'onTouched', defaultValues: { nueva: '', confirmacion: '' } });
  const nueva = watch('nueva') ?? '';
  // El mensaje largo de Zod repite las reglas: se muestra corto y la lista marca en rojo lo que falta.
  const { error: errorNueva, conError: reglaError } = errorClave(errors.nueva?.message);
  const guardar = handleSubmit(async (d) => {
    setAviso(null);
    try {
      await recuperarApi.restablecer(token, d.nueva, d.confirmacion);
      alListo();
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['nueva', 'confirmacion']);
      if (!aplicados) setAviso(esErrorApi(e) ? e.message : 'No pudimos cambiar la contraseña. Intenta de nuevo.');
    }
  });
  const ojo = (
    <button type="button" className="control-boton" aria-label={ver ? 'Ocultar contraseña' : 'Ver contraseña'} aria-pressed={ver} onClick={() => setVer((v) => !v)}>
      {ver ? <EyeOff className="ico ico-18" aria-hidden /> : <Eye className="ico ico-18" aria-hidden />}
    </button>
  );
  return (
    <form className="grid gap-5" onSubmit={guardar} noValidate>
      {cabecera('Crea tu nueva contraseña')}
      <div className="grid gap-2">
        <CampoTexto label="Nueva contraseña" requerido icono={KeyRound} type={ver ? 'text' : 'password'} autoComplete="new-password" maxLength={64} autoFocus accion={ojo} error={errorNueva} {...register('nueva')} />
        <ReglasClave valor={nueva} conError={reglaError} />
      </div>
      <CampoTexto label="Repite la contraseña" requerido icono={KeyRound} type={ver ? 'text' : 'password'} autoComplete="new-password" maxLength={64} error={errors.confirmacion?.message} {...register('confirmacion')} />
      {aviso && (
        <Alerta
          tipo="error"
          acciones={
            <Boton variante="secundario" tamano="sm" icono={RotateCw} onClick={alReiniciar}>
              Empezar de nuevo
            </Boton>
          }
        >
          {aviso}
        </Alerta>
      )}
      <Boton type="submit" tamano="lg" bloque icono={Save} cargando={isSubmitting} textoCargando="Guardando…">
        Guardar contraseña
      </Boton>
    </form>
  );
}
