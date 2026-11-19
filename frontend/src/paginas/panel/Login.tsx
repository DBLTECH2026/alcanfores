import { useState } from 'react';
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, CalendarDays, ChartColumn, CircleCheck, Eye, EyeOff, Lock, LogIn, UserRound, FlaskConical } from 'lucide-react';
import { esErrorApi } from '@/api/cliente';
import { useAuth } from '@/contexto/AuthContext';
import { useToast } from '@/contexto/ToastContext';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { Boton, BotonEnlace } from '@/componentes/ui/Boton';
import { CampoTexto } from '@/componentes/ui/Campo';
import { Alerta } from '@/componentes/ui/Estados';
import { Ilustracion, Logo, PatronCancha } from '@/componentes/ui/Marca';
import { esquemaLogin, type DatosLogin } from '@/esquemas/otros';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { MODO_MOCK } from '@/utilidades/recursos';

/** /admin/login — ingreso al panel (prototipo adm-01): validación, error de credenciales o bloqueo, mostrar/ocultar contraseña. */
export default function Login() {
  useTituloPagina('Ingresa al panel');
  const { autenticado, ingresar, expirada } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const [ver, setVer] = useState(false);
  const [aviso, setAviso] = useState<{ tipo: 'error' | 'advertencia'; titulo: string; texto?: string } | null>(null);
  const [ayuda, setAyuda] = useState(false);
  const desde = (location.state as { desde?: string } | null)?.desde ?? '/admin/hoy';
  const sesionVencida = expirada || params.get('expirada') === '1';

  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<DatosLogin>({ resolver: zodResolver(esquemaLogin), mode: 'onTouched', defaultValues: { usuario: '', contrasena: '' } });

  if (autenticado) return <Navigate to={desde} replace />;

  const entrar = handleSubmit(async (d) => {
    setAviso(null);
    try {
      const u = await ingresar(d.usuario.trim(), d.contrasena);
      toast.exito(`Hola, ${u.nombres.split(' ')[0]}`);
      navigate(desde, { replace: true });
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['usuario', 'contrasena']);
      if (aplicados) return;
      if (esErrorApi(e) && e.status === 423) setAviso({ tipo: 'advertencia', titulo: 'Cuenta bloqueada', texto: e.message });
      else if (esErrorApi(e) && e.status === 401) {
        setAviso({ tipo: 'error', titulo: 'Usuario o contraseña incorrectos', texto: e.message !== 'Usuario o contraseña incorrectos.' ? e.message : undefined });
        setFocus('contrasena');
      } else setAviso({ tipo: 'error', titulo: 'No se pudo ingresar', texto: esErrorApi(e) ? e.message : undefined });
    }
  });

  return (
    <div className="pantalla grid min-h-[100dvh] lg:grid-cols-2">
      <section className="relative overflow-hidden bg-noche text-white sobre-oscuro grid content-between gap-6 px-6 py-6 lg:px-12 lg:py-12">
        <PatronCancha className="absolute -left-24 -bottom-24 w-[520px] text-white opacity-[.06] pointer-events-none lg:left-auto lg:-right-24" />
        <div className="relative flex items-center justify-between gap-3">
          <Logo variante="horizontal-oscuro" className="h-[26px] lg:h-[34px]" />
          {MODO_MOCK && <span className="sello-demo">Datos simulados</span>}
        </div>
        <Ilustracion id="IL-22-ingreso-admin" carga="eager" className="relative hidden lg:block w-[440px] h-[440px] justify-self-center" />
        <ul className="relative hidden lg:flex justify-center gap-8 text-[14.5px] font-semibold">
          <li className="inline-flex items-center gap-2">
            <CircleCheck className="ico ico-18 text-grass" aria-hidden />
            Validar
          </li>
          <li className="inline-flex items-center gap-2">
            <CalendarDays className="ico ico-18 text-grass" aria-hidden />
            Agenda
          </li>
          <li className="inline-flex items-center gap-2">
            <ChartColumn className="ico ico-18 text-grass" aria-hidden />
            Reportes
          </li>
        </ul>
      </section>

      <main id="contenido" className="grid place-items-center px-5 py-8 lg:px-12">
        <div className="w-full max-w-[400px] grid gap-5">
          <Ilustracion id="IL-22-ingreso-admin" carga="eager" className="lg:hidden w-[150px] h-[150px] justify-self-center -mt-2" />
          <h1 className="t-h1">Ingresa al panel</h1>
          {sesionVencida && !aviso && <Alerta tipo="info" titulo="Tu sesión terminó">Ingresa de nuevo para continuar.</Alerta>}
          {aviso && (
            <Alerta tipo={aviso.tipo} titulo={aviso.titulo}>
              {aviso.texto}
            </Alerta>
          )}
          <form onSubmit={entrar} noValidate className="grid gap-4">
            <CampoTexto label="Usuario" requerido icono={UserRound} autoComplete="username" autoCapitalize="none" spellCheck={false} maxLength={30} error={errors.usuario?.message} {...register('usuario', { setValueAs: (v: string) => String(v ?? '').trim().toLowerCase() })} />
            <CampoTexto
              label="Contraseña"
              requerido
              icono={Lock}
              type={ver ? 'text' : 'password'}
              autoComplete="current-password"
              maxLength={64}
              error={errors.contrasena?.message}
              accion={
                <button type="button" className="control-boton" aria-label={ver ? 'Ocultar contraseña' : 'Mostrar contraseña'} aria-pressed={ver} onClick={() => setVer((v) => !v)}>
                  {ver ? <EyeOff className="ico" aria-hidden /> : <Eye className="ico" aria-hidden />}
                </button>
              }
              {...register('contrasena')}
            />
            <div className="flex justify-end -mt-1">
              <button type="button" className="enlace bg-transparent border-0 p-0 text-[13.5px]" aria-expanded={ayuda} onClick={() => setAyuda((a) => !a)}>
                ¿Olvidaste tu contraseña?
              </button>
            </div>
            {ayuda && <Alerta tipo="info">Pide al administrador que la restablezca desde «Usuarios».</Alerta>}
            <Boton type="submit" tamano="lg" bloque icono={LogIn} cargando={isSubmitting} textoCargando="Ingresando…">
              Ingresar
            </Boton>
          </form>
          {MODO_MOCK && (
            <div className="pruebas !gap-1.5 text-[13px]">
              <span className="inline-flex items-center gap-2 font-semibold">
                <FlaskConical className="ico ico-16 text-amarillo-ocre" aria-hidden />
                Cuentas de prueba
              </span>
              <span>
                <code className="font-bold">rhuaman</code> · Alcanfores2026 (ADMIN)
              </span>
              <span>
                <code className="font-bold">mtorres</code> · Recepcion2026 (RECEPCION)
              </span>
            </div>
          )}
          <BotonEnlace to="/" variante="fantasma" icono={ArrowLeft} className="justify-self-center">
            Volver al portal
          </BotonEnlace>
        </div>
      </main>
    </div>
  );
}
