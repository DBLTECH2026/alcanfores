import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { KeyRound, Lock, Mail, Save, UserRound } from 'lucide-react';
import { authApi } from '@/api/servicios';
import { esErrorApi } from '@/api/cliente';
import { useToast } from '@/contexto/ToastContext';
import { useAuth } from '@/contexto/AuthContext';
import { useCarga } from '@/hooks/useCarga';
import { fmtCelular } from '@/utilidades/formato';
import { esquemaMisDatos } from './esquemasPanel';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { Boton } from '@/componentes/ui/Boton';
import { CampoTexto } from '@/componentes/ui/Campo';
import { Alerta } from '@/componentes/ui/Estados';

const MSJ_CLAVE = 'De 8 a 64 caracteres, con mayúscula, minúscula y número.';
/** SPEC §2: contraseña nueva 8–64, al menos una mayúscula, una minúscula y un número. */
const esquema = z
  .object({
    actual: z.string().min(1, 'Ingresa tu contraseña actual.'),
    nueva: z
      .string()
      .min(8, MSJ_CLAVE)
      .max(64, MSJ_CLAVE)
      .regex(/[A-Z]/, MSJ_CLAVE)
      .regex(/[a-z]/, MSJ_CLAVE)
      .regex(/\d/, MSJ_CLAVE),
    confirmacion: z.string(),
  })
  .refine((d) => d.nueva === d.confirmacion, { path: ['confirmacion'], message: 'Las contraseñas no coinciden.' })
  .refine((d) => d.nueva !== d.actual, { path: ['nueva'], message: 'Usa una contraseña distinta a la actual.' });
type Datos = z.infer<typeof esquema>;

/** /admin/cuenta — cambiar contraseña del usuario actual. */
export default function Cuenta() {
  useTituloPagina('Mi cuenta');
  const toast = useToast();
  const [aviso, setAviso] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<Datos>({ resolver: zodResolver(esquema), mode: 'onTouched', defaultValues: { actual: '', nueva: '', confirmacion: '' } });

  const guardar = handleSubmit(async (d) => {
    setAviso(null);
    try {
      await authApi.cambiarContrasena(d);
      toast.exito('Contraseña actualizada');
      reset();
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['actual', 'nueva', 'confirmacion']);
      if (!aplicados) setAviso(esErrorApi(e) ? e.message : 'No se pudo cambiar.');
    }
  });

  return (
    <>
      <CabeceraPagina titulo="Mi cuenta" subtitulo="Tus datos y tu contraseña" busto="BU-rosa-laptop" />
      <div className="grid gap-4 xl:grid-cols-2 xl:items-start max-w-[1000px]">
        <MisDatos />
        <form onSubmit={guardar} noValidate className="pc">
          <div className="pc-cab">
            <h2>
              <KeyRound className="ico" aria-hidden />
              Contraseña
            </h2>
          </div>
          <div className="pc-cuerpo grid gap-4">
            <CampoTexto label="Contraseña actual" requerido type="password" icono={Lock} autoComplete="current-password" error={errors.actual?.message} {...register('actual')} />
            <CampoTexto label="Nueva contraseña" requerido type="password" icono={KeyRound} autoComplete="new-password" ayuda="8+ caracteres, mayúscula, minúscula y número" error={errors.nueva?.message} {...register('nueva')} />
            <CampoTexto label="Repite la nueva" requerido type="password" icono={KeyRound} autoComplete="new-password" error={errors.confirmacion?.message} {...register('confirmacion')} />
            {aviso && <Alerta tipo="error">{aviso}</Alerta>}
            <div className="flex justify-end">
              <Boton type="submit" variante="primario" icono={KeyRound} cargando={isSubmitting}>
                Cambiar contraseña
              </Boton>
            </div>
          </div>
        </form>
      </div>
    </>
  );
}

type EntradaDatos = z.input<typeof esquemaMisDatos>;
/** Correo y celular del administrador: los usa «¿Olvidaste tu contraseña?». */
function MisDatos() {
  const toast = useToast();
  const { actualizarUsuario } = useAuth();
  const yo = useCarga(() => authApi.yo(), 'yo');
  const [aviso, setAviso] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<EntradaDatos>({ resolver: zodResolver(esquemaMisDatos), mode: 'onTouched', defaultValues: { correo: '', telefono: '' } });
  useEffect(() => {
    if (yo.datos) reset({ correo: yo.datos.correo ?? '', telefono: yo.datos.telefono ?? '' });
  }, [yo.datos, reset]);

  const guardar = handleSubmit(async (d) => {
    setAviso(null);
    const v = esquemaMisDatos.parse(d);
    try {
      const u = await authApi.editarYo({ correo: v.correo, telefono: v.telefono || null });
      actualizarUsuario(u);
      yo.setDatos(u);
      toast.exito('Datos guardados');
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['correo', 'telefono']);
      if (!aplicados) setAviso(esErrorApi(e) ? e.message : 'No se pudo guardar.');
    }
  });

  const u = yo.datos;
  return (
    <form onSubmit={guardar} noValidate className="pc">
      <div className="pc-cab">
        <h2>
          <UserRound className="ico" aria-hidden />
          Mis datos
        </h2>
      </div>
      <div className="pc-cuerpo grid gap-4">
        {u && (
          <div className="flex items-center gap-3">
            <span className="t-sec">
              {u.nombres} {u.apellidos} · <b className="text-noche">@{u.usuario}</b>
            </span>
          </div>
        )}
        <CampoTexto label="Correo" requerido type="email" icono={Mail} autoComplete="email" maxLength={100} ayuda="Para recibir el código si olvidas la contraseña" error={errors.correo?.message} {...register('correo')} />
        <CampoTexto label="Celular" opcional prefijo="+51" inputMode="tel" autoComplete="tel-national" maxLength={15} ayuda="Para recibir el código por WhatsApp" error={errors.telefono?.message} {...register('telefono')} />
        {u && !u.telefono && !isDirty && <Alerta tipo="info">Agrega tu celular para recuperar la contraseña también por WhatsApp.</Alerta>}
        {u?.telefono && !isDirty && <span className="t-mini tnum">WhatsApp: {fmtCelular(u.telefono)}</span>}
        {aviso && <Alerta tipo="error">{aviso}</Alerta>}
        <div className="flex justify-end">
          <Boton type="submit" icono={Save} disabled={!isDirty || !u} cargando={isSubmitting} textoCargando="Guardando…">
            Guardar datos
          </Boton>
        </div>
      </div>
    </form>
  );
}
