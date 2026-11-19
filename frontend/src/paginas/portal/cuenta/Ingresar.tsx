import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Eye, EyeOff, Fingerprint, KeyRound, LogIn, UserPlus } from 'lucide-react';
import { esErrorApi } from '@/api/cliente';
import { useCuenta } from '@/contexto/CuentaContext';
import { useToast } from '@/contexto/ToastContext';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { Boton, BotonEnlace } from '@/componentes/ui/Boton';
import { CampoTexto } from '@/componentes/ui/Campo';
import { Alerta } from '@/componentes/ui/Estados';
import { Ilustracion } from '@/componentes/ui/Marca';
import { esquemaIngresoCliente, type IngresoClienteEntrada } from '@/esquemas/otros';
import { destinoSeguro } from '@/utilidades/navegacion';

/** /ingresar — inicio de sesión del cliente (DNI o correo + contraseña). SPEC_V2 §E. */
export default function Ingresar() {
  useTituloPagina('Ingresar');
  const { ingresar, conectado } = useCuenta();
  const toast = useToast();
  const navigate = useNavigate();
  const [sp] = useSearchParams();
  const volver = destinoSeguro(sp.get('volver'));
  const [ver, setVer] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<IngresoClienteEntrada>({ resolver: zodResolver(esquemaIngresoCliente), mode: 'onTouched', defaultValues: { usuario: '', contrasena: '' } });

  if (conectado) return <Navigate to={volver} replace />;

  const enviar = handleSubmit(async (d) => {
    setError(null);
    try {
      const c = await ingresar(d.usuario.trim().toLowerCase(), d.contrasena);
      toast.exito(`Hola, ${c.nombres.split(' ')[0]}`);
      navigate(volver, { replace: true });
    } catch (e) {
      setError(esErrorApi(e) ? e.message : 'No pudimos ingresar. Intenta de nuevo.');
    }
  });

  return (
    <div className="contenedor py-8 lg:py-14">
      <div className="cuenta-caja">
        <div className="cuenta-il" aria-hidden>
          <Ilustracion id="IL-03-paso2-datos" carga="eager" />
        </div>
        <form className="cuenta-form" onSubmit={enviar} noValidate>
          <div className="grid gap-1">
            <h1 className="t-h1">Ingresa a tu cuenta</h1>
            <p className="t-sec">Mira tus reservas y califica tus partidos.</p>
          </div>
          <CampoTexto label="DNI o correo" requerido icono={Fingerprint} autoComplete="username" maxLength={100} error={errors.usuario?.message} {...register('usuario')} />
          <CampoTexto
            label="Contraseña"
            requerido
            icono={KeyRound}
            type={ver ? 'text' : 'password'}
            autoComplete="current-password"
            maxLength={64}
            error={errors.contrasena?.message}
            accion={
              <button type="button" className="control-boton" aria-label={ver ? 'Ocultar contraseña' : 'Ver contraseña'} onClick={() => setVer((v) => !v)}>
                {ver ? <EyeOff className="ico ico-18" aria-hidden /> : <Eye className="ico ico-18" aria-hidden />}
              </button>
            }
            {...register('contrasena')}
          />
          <div className="flex justify-end -mt-2">
            <Link to="/recuperar" className="enlace text-[13.5px] inline-flex items-center min-h-[36px]">
              ¿Olvidaste tu contraseña?
            </Link>
          </div>
          {error && <Alerta tipo="error">{error}</Alerta>}
          <Boton type="submit" tamano="lg" bloque icono={LogIn} cargando={isSubmitting} textoCargando="Ingresando…">
            Ingresar
          </Boton>
          <div className="cuenta-sep">
            <span>¿Aún no tienes cuenta?</span>
          </div>
          <BotonEnlace to={`/crear-cuenta${sp.get('volver') ? `?volver=${encodeURIComponent(volver)}` : ''}`} variante="secundario" bloque icono={UserPlus}>
            Crear cuenta
          </BotonEnlace>
          <p className="t-mini text-center">
            También puedes{' '}
            <Link to="/reservar" className="enlace">
              reservar sin cuenta
            </Link>
            .
          </p>
        </form>
      </div>
    </div>
  );
}
