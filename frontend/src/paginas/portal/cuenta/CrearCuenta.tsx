import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { BadgeCheck, CircleAlert, Eye, EyeOff, Fingerprint, KeyRound, Lock, Mail, Search, Smartphone, UserPlus, UserRound, UsersRound } from 'lucide-react';
import { documentosApi } from '@/api/servicios';
import { esErrorApi } from '@/api/cliente';
import { useCuenta } from '@/contexto/CuentaContext';
import { useToast } from '@/contexto/ToastContext';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { Boton } from '@/componentes/ui/Boton';
import { CampoTexto, Casilla } from '@/componentes/ui/Campo';
import { Alerta, Insignia } from '@/componentes/ui/Estados';
import { Ilustracion } from '@/componentes/ui/Marca';
import { ReglasClave, errorClave } from '@/componentes/cuenta/ReglasClave';
import { esquemaRegistroCliente, type RegistroClienteEntrada } from '@/esquemas/otros';
import { RE_DNI, normalizarCelular } from '@/utilidades/documentos';
import { aTitulo } from '@/utilidades/formato';
import { destinoSeguro } from '@/utilidades/navegacion';

type EstadoDni = 'inicial' | 'buscando' | 'verificado' | 'manual';
const CAMPOS = ['dni', 'nombres', 'apellidos', 'telefono', 'correo', 'contrasena', 'confirmacion', 'aceptaTerminos', 'aceptaPrivacidad'] as const;

/** /crear-cuenta — registro del cliente (SPEC_V2 §E). Al terminar queda con la sesión iniciada. */
export default function CrearCuenta() {
  useTituloPagina('Crear cuenta');
  const { registrarse, conectado } = useCuenta();
  const toast = useToast();
  const navigate = useNavigate();
  const [sp] = useSearchParams();
  const volver = destinoSeguro(sp.get('volver'));
  const [ver, setVer] = useState(false);
  const [dniEstado, setDniEstado] = useState<EstadoDni>('inicial');
  const [aviso, setAviso] = useState<{ texto: string; ingresar?: boolean } | null>(null);
  const ultimo = useRef('');
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setError,
    getValues,
    trigger,
    formState: { errors, isSubmitting },
  } = useForm<RegistroClienteEntrada>({
    resolver: zodResolver(esquemaRegistroCliente),
    mode: 'onTouched',
    defaultValues: { dni: '', nombres: '', apellidos: '', telefono: '', correo: '', contrasena: '', confirmacion: '', aceptaTerminos: false, aceptaPrivacidad: false },
  });
  const dni = watch('dni') ?? '';
  const contrasena = watch('contrasena') ?? '';
  const reglas = errorClave(errors.contrasena?.message);

  const consultar = async (valor: string) => {
    if (!RE_DNI.test(valor)) {
      void trigger('dni');
      return;
    }
    ultimo.current = valor;
    setDniEstado('buscando');
    try {
      const d = await documentosApi.dni(valor);
      if (getValues('dni') !== valor) return;
      setValue('nombres', aTitulo(d.nombres), { shouldValidate: true });
      setValue('apellidos', aTitulo(d.apellidos || `${d.apellidoPaterno} ${d.apellidoMaterno}`), { shouldValidate: true });
      setDniEstado('verificado');
    } catch {
      setDniEstado('manual');
    }
  };
  // Al completar 8 dígitos se consulta solo; si cambia el DNI se libera lo que vino de RENIEC.
  useEffect(() => {
    if (dni === ultimo.current) return;
    if (dniEstado === 'verificado') {
      setValue('nombres', '');
      setValue('apellidos', '');
    }
    if (dniEstado !== 'buscando') setDniEstado('inicial');
    if (RE_DNI.test(dni)) void consultar(dni);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dni]);

  if (conectado) return <Navigate to={volver} replace />;

  const enviar = handleSubmit(async (d) => {
    setAviso(null);
    try {
      const c = await registrarse({
        dni: d.dni,
        nombres: aTitulo(d.nombres),
        apellidos: aTitulo(d.apellidos),
        telefono: normalizarCelular(String(d.telefono)),
        correo: d.correo.trim(),
        contrasena: d.contrasena,
        confirmacion: d.confirmacion,
        aceptaTerminos: Boolean(d.aceptaTerminos),
        aceptaPrivacidad: Boolean(d.aceptaPrivacidad),
      });
      toast.exito(`Cuenta creada. ¡Hola, ${c.nombres.split(' ')[0]}!`);
      navigate(volver, { replace: true });
    } catch (e) {
      if (esErrorApi(e) && e.codigo === 'DNI_REGISTRADO') {
        setError('dni', { message: e.message });
        setAviso({ texto: 'Ese DNI ya tiene cuenta.', ingresar: true });
        return;
      }
      if (esErrorApi(e) && e.codigo === 'CORREO_REGISTRADO') {
        setError('correo', { message: e.message });
        return;
      }
      let ubicados = 0;
      if (esErrorApi(e))
        for (const x of e.errores)
          if ((CAMPOS as readonly string[]).includes(x.campo)) {
            setError(x.campo as (typeof CAMPOS)[number], { message: x.mensaje });
            ubicados += 1;
          }
      if (!ubicados) setAviso({ texto: esErrorApi(e) ? e.message : 'No pudimos crear tu cuenta. Intenta de nuevo.' });
    }
  });

  const verificado = dniEstado === 'verificado';
  const errorAcepta = errors.aceptaTerminos?.message ?? errors.aceptaPrivacidad?.message;
  return (
    <div className="contenedor py-8 lg:py-14">
      <div className="cuenta-caja ancha">
        <div className="cuenta-il" aria-hidden>
          <Ilustracion id="IL-12-confirmada" carga="eager" />
        </div>
        <form className="cuenta-form" onSubmit={enviar} noValidate>
          <div className="grid gap-1">
            <h1 className="t-h1">Crea tu cuenta</h1>
            <p className="t-sec">Verás todas tus reservas, también las que hiciste antes.</p>
          </div>
          <CampoTexto
            label="DNI"
            requerido
            icono={Fingerprint}
            inputMode="numeric"
            maxLength={8}
            autoComplete="off"
            placeholder="8 dígitos"
            valido={verificado}
            error={errors.dni?.message}
            ayuda={
              verificado ? (
                <Insignia tono="menta" icono={BadgeCheck} tamano="sm">
                  Verificado con RENIEC
                </Insignia>
              ) : dniEstado === 'buscando' ? (
                'Consultando RENIEC…'
              ) : dniEstado === 'manual' ? (
                'RENIEC no respondió: escribe tus nombres'
              ) : (
                'Con él encontramos tus reservas'
              )
            }
            accion={
              <button type="button" className="control-boton" aria-label="Buscar DNI en RENIEC" disabled={dniEstado === 'buscando'} onClick={() => void consultar(getValues('dni') ?? '')}>
                {dniEstado === 'buscando' ? <span className="spinner" aria-hidden /> : <Search className="ico ico-18" aria-hidden />}
              </button>
            }
            {...register('dni', { setValueAs: (v: string) => String(v ?? '').replace(/\D/g, '').slice(0, 8) })}
          />
          {/* Celular: Contraseña, reglas y Repite. Desde 768: las dos contraseñas lado a lado y las reglas debajo (flujo dense). */}
          <div className="form-grid dos md:grid-flow-row-dense">
            <CampoTexto label="Nombres" requerido icono={UserRound} maxLength={60} lectura={verificado} valido={verificado} error={errors.nombres?.message} {...register('nombres')} />
            <CampoTexto label="Apellidos" requerido icono={UsersRound} maxLength={60} lectura={verificado} valido={verificado} error={errors.apellidos?.message} {...register('apellidos')} />
            <CampoTexto label="Celular" requerido prefijo={<><Smartphone className="ico ico-16" aria-hidden />+51</>} type="tel" inputMode="tel" autoComplete="tel-national" maxLength={15} placeholder="987 654 321" error={errors.telefono?.message} {...register('telefono')} />
            <CampoTexto label="Correo" requerido icono={Mail} type="email" autoComplete="email" maxLength={100} placeholder="nombre@correo.com" error={errors.correo?.message} {...register('correo')} />
            <CampoTexto
              label="Contraseña"
              requerido
              icono={KeyRound}
              type={ver ? 'text' : 'password'}
              autoComplete="new-password"
              maxLength={64}
              error={reglas.error}
              accion={
                <button type="button" className="control-boton" aria-label={ver ? 'Ocultar contraseña' : 'Ver contraseña'} onClick={() => setVer((v) => !v)}>
                  {ver ? <EyeOff className="ico ico-18" aria-hidden /> : <Eye className="ico ico-18" aria-hidden />}
                </button>
              }
              {...register('contrasena')}
            />
            <ReglasClave valor={contrasena} conError={reglas.conError} className="md:col-span-2" />
            <CampoTexto label="Repite la contraseña" requerido icono={KeyRound} type={ver ? 'text' : 'password'} autoComplete="new-password" maxLength={64} error={errors.confirmacion?.message} {...register('confirmacion')} />
          </div>
          <div className="grid gap-2">
            <p className="t-sec flex gap-2 items-start">
              <Lock className="ico ico-16 text-profundo mt-0.5" aria-hidden />
              <span>Usamos tus datos solo para tus reservas (Ley 29733).</span>
            </p>
            <Casilla {...register('aceptaTerminos')} aria-invalid={errors.aceptaTerminos ? true : undefined}>
              Acepto los{' '}
              <a href="/terminos" target="_blank" rel="noopener" className="enlace">
                términos y condiciones
              </a>
            </Casilla>
            <Casilla {...register('aceptaPrivacidad')} aria-invalid={errors.aceptaPrivacidad ? true : undefined}>
              Acepto la{' '}
              <a href="/privacidad" target="_blank" rel="noopener" className="enlace">
                política de privacidad
              </a>
            </Casilla>
            {errorAcepta && (
              <div className="campo-error pl-[34px]" role="alert">
                <CircleAlert className="ico" aria-hidden />
                <span>{errorAcepta}</span>
              </div>
            )}
          </div>
          {aviso && (
            <Alerta tipo={aviso.ingresar ? 'advertencia' : 'error'}>
              {aviso.texto}{' '}
              {aviso.ingresar && (
                <Link to={`/ingresar?volver=${encodeURIComponent(volver)}`} className="enlace">
                  Ingresa aquí
                </Link>
              )}
            </Alerta>
          )}
          <Boton type="submit" tamano="lg" bloque icono={UserPlus} cargando={isSubmitting} textoCargando="Creando…">
            Crear cuenta
          </Boton>
          <p className="t-mini text-center">
            ¿Ya tienes cuenta?{' '}
            <Link to={`/ingresar${sp.get('volver') ? `?volver=${encodeURIComponent(volver)}` : ''}`} className="enlace">
              Ingresa
            </Link>
          </p>
        </form>
      </div>
    </div>
  );
}
