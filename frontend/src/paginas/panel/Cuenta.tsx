import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { KeyRound, Lock, Save } from 'lucide-react';
import { authApi } from '@/api/servicios';
import { esErrorApi } from '@/api/cliente';
import { useToast } from '@/contexto/ToastContext';
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
      <CabeceraPagina titulo="Mi cuenta" subtitulo="Cambia tu contraseña" busto="BU-rosa-laptop" />
      <form onSubmit={guardar} noValidate className="card card-pad grid gap-4 max-w-[480px]">
        <CampoTexto label="Contraseña actual" requerido type="password" icono={Lock} autoComplete="current-password" error={errors.actual?.message} {...register('actual')} />
        <CampoTexto label="Nueva contraseña" requerido type="password" icono={KeyRound} autoComplete="new-password" ayuda="8+ caracteres, mayúscula, minúscula y número" error={errors.nueva?.message} {...register('nueva')} />
        <CampoTexto label="Repite la nueva" requerido type="password" icono={KeyRound} autoComplete="new-password" error={errors.confirmacion?.message} {...register('confirmacion')} />
        {aviso && <Alerta tipo="error">{aviso}</Alerta>}
        <div>
          <Boton type="submit" icono={Save} cargando={isSubmitting}>
            Guardar
          </Boton>
        </div>
      </form>
    </>
  );
}
