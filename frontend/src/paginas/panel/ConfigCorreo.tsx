import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { z } from 'zod';
import { AtSign, CircleCheck, KeyRound, Lock, Mail, RotateCcw, Save, Send, Server, ShieldCheck, UserRound, Wand2 } from 'lucide-react';
import { correoApi } from '@/api/admin';
import type { ConfigCorreo, ResultadoPruebaCorreo, SeguridadSmtp } from '@/api/admin/tipos';
import { esErrorApi } from '@/api/cliente';
import { useToast } from '@/contexto/ToastContext';
import { useCarga } from '@/hooks/useCarga';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { ErrorCarga } from '@/componentes/panel/Herramientas';
import { Boton } from '@/componentes/ui/Boton';
import { CampoTexto } from '@/componentes/ui/Campo';
import { Cargando, Segmentado } from '@/componentes/ui/Datos';
import { Alerta } from '@/componentes/ui/Estados';
import { fmtFechaHora } from '@/utilidades/fecha';
import { esquemaCorreoSmtp, esquemaPruebaCorreo } from './esquemasPanel';

/** De dónde vienen los datos del correo (lo dice el back en `origen`). */
function textoOrigen(origen: string | null | undefined): string {
  const o = (origen ?? '').toUpperCase();
  if (o.includes('PANEL') || o.includes('BD') || o.includes('BASE')) return 'Datos guardados en este panel';
  if (o.includes('ENTORNO') || o.includes('VARIABLE') || o.includes('SERVIDOR') || o.includes('ENV')) return 'Datos de las variables del servidor';
  if (!o || o.includes('NINGUN') || o.includes('SIMUL')) return 'Sin servidor de correo';
  return `Origen: ${origen}`;
}

type Entrada = z.input<ReturnType<typeof esquemaCorreoSmtp>>;
const valoresDe = (c: ConfigCorreo): Entrada => ({
  host: c.host ?? '',
  puerto: c.puerto ?? 465,
  seguridad: c.seguridad ?? 'SSL',
  usuario: c.usuario ?? '',
  clave: '',
  remitente: c.remitente ?? '',
  nombreRemitente: c.nombreRemitente ?? 'Complejo Deportivo Los Alcanfores',
});

/** /admin/configuracion/correo — servidor SMTP para los correos a los clientes y los códigos de recuperación. */
export default function ConfigCorreoPantalla() {
  const toast = useToast();
  const conf = useCarga(() => correoApi.obtener(), 'correo');
  const datos = conf.datos;
  const tieneClave = datos?.tieneClave ?? false;
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    getValues,
    setError,
    watch,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<Entrada>({ resolver: zodResolver(esquemaCorreoSmtp(tieneClave)), mode: 'onTouched' });
  useEffect(() => {
    if (datos) reset(valoresDe(datos));
  }, [datos, reset]);

  const guardar = handleSubmit(async (v) => {
    setErrorGeneral(null);
    try {
      const r = await correoApi.guardar({
        host: v.host.trim().toLowerCase(),
        puerto: Number(v.puerto),
        seguridad: v.seguridad as SeguridadSmtp,
        usuario: v.usuario.trim(),
        clave: v.clave,
        remitente: v.remitente.trim().toLowerCase(),
        nombreRemitente: v.nombreRemitente.trim(),
      });
      conf.setDatos(r);
      reset(valoresDe(r));
      toast.exito('Correo guardado');
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['host', 'puerto', 'seguridad', 'usuario', 'clave', 'remitente', 'nombreRemitente']);
      if (!aplicados) setErrorGeneral(esErrorApi(e) ? e.message : 'No se pudo guardar.');
    }
  });

  /** Hostinger: smtp.hostinger.com · 465 · SSL; el usuario es el mismo correo. */
  const usarHostinger = () => {
    const o = { shouldDirty: true, shouldValidate: false };
    setValue('host', 'smtp.hostinger.com', o);
    setValue('puerto', 465, o);
    setValue('seguridad', 'SSL', o);
    const { usuario, remitente } = getValues();
    if (!usuario && remitente) setValue('usuario', remitente, o);
    if (!remitente && usuario.includes('@')) setValue('remitente', usuario, o);
    toast.info('Datos de Hostinger listos: revisa y guarda');
  };

  if (conf.error) return <ErrorCarga alReintentar={conf.recargar} />;
  if (!datos) return <Cargando texto="Cargando…" />;

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)] xl:items-start">
      <div className="grid gap-4 min-w-0">
        <section className="pc">
          <div className="estado-wa">
            <span className={`punto-wa ${datos.configurado ? 'ok' : 'no'}`}>{datos.configurado ? <CircleCheck className="ico ico-24" aria-hidden /> : <Server className="ico ico-24" aria-hidden />}</span>
            <div className="grid gap-0.5 min-w-0">
              <b className="text-[17px]">{datos.configurado ? 'Configurado' : 'Simulado'}</b>
              <span className="t-sec">{datos.configurado ? `Los correos salen desde ${datos.remitente ?? datos.usuario ?? '—'}` : 'Los correos no salen: quedan registrados en Notificaciones.'}</span>
              {datos.actualizadoEn && (
                <span className="t-mini tnum">
                  Guardado {fmtFechaHora(datos.actualizadoEn, { h24: true })}
                  {datos.actualizadoPor ? ` por ${datos.actualizadoPor}` : ''}
                </span>
              )}
            </div>
            <span className="ml-auto">
              <span className="badge b-neutro badge-sm">
                <span>{textoOrigen(datos.origen)}</span>
              </span>
            </span>
          </div>
        </section>

        <form className="pc" onSubmit={guardar} noValidate>
          {/* En celular el botón baja a su propia línea para que el título no se parta. */}
          <div className="pc-cab flex-wrap gap-y-2.5">
            <h2>
              <Server className="ico" aria-hidden />
              Servidor de correo
            </h2>
            <div className="pc-der max-sm:ml-0 max-sm:w-full">
              <Boton variante="secundario" tamano="sm" icono={Wand2} onClick={usarHostinger}>
                Usar datos de Hostinger
              </Boton>
            </div>
          </div>
          <div className="pc-cuerpo grid gap-4">
            <div className="form-2">
              <CampoTexto label="Servidor (SMTP)" requerido icono={Server} autoCapitalize="none" spellCheck={false} maxLength={255} placeholder="smtp.hostinger.com" error={errors.host?.message} {...register('host')} />
              <CampoTexto label="Puerto" requerido inputMode="numeric" maxLength={5} ayuda="465 con SSL · 587 con STARTTLS" error={errors.puerto?.message} {...register('puerto')} />
            </div>
            <div className="grid gap-1.5">
              <span className="campo-label">Seguridad</span>
              <Segmentado<SeguridadSmtp>
                etiqueta="Seguridad"
                bloque
                valor={(watch('seguridad') as SeguridadSmtp) ?? 'SSL'}
                alCambiar={(s) => {
                  setValue('seguridad', s, { shouldDirty: true });
                  const p = Number(getValues('puerto'));
                  if (s === 'SSL' && p === 587) setValue('puerto', 465, { shouldDirty: true });
                  if (s === 'STARTTLS' && p === 465) setValue('puerto', 587, { shouldDirty: true });
                }}
                opciones={[
                  { valor: 'SSL', texto: 'SSL', icono: Lock },
                  { valor: 'STARTTLS', texto: 'STARTTLS', icono: ShieldCheck },
                ]}
              />
            </div>
            <div className="form-2">
              <CampoTexto label="Usuario" requerido icono={AtSign} autoComplete="off" autoCapitalize="none" spellCheck={false} maxLength={150} placeholder="reservas@tudominio.com" error={errors.usuario?.message} {...register('usuario')} />
              <CampoTexto
                label="Contraseña"
                requerido={!tieneClave}
                icono={KeyRound}
                type="password"
                autoComplete="new-password"
                maxLength={200}
                placeholder={tieneClave ? '••••••••' : ''}
                ayuda={tieneClave ? 'Déjala en blanco para conservar la guardada' : 'La del buzón de correo'}
                error={errors.clave?.message}
                {...register('clave')}
              />
              <CampoTexto label="Correo remitente" requerido type="email" icono={Mail} autoCapitalize="none" maxLength={150} ayuda="El que ven los clientes" error={errors.remitente?.message} {...register('remitente')} />
              <CampoTexto label="Nombre del remitente" opcional icono={UserRound} maxLength={80} error={errors.nombreRemitente?.message} {...register('nombreRemitente')} />
            </div>
            {errorGeneral && <Alerta tipo="error">{errorGeneral}</Alerta>}
            <div className="flex flex-wrap gap-2 justify-end">
              <Boton variante="fantasma" icono={RotateCcw} disabled={!isDirty} onClick={() => reset(valoresDe(datos))}>
                Deshacer
              </Boton>
              <Boton type="submit" icono={Save} disabled={!isDirty} cargando={isSubmitting} textoCargando="Guardando…">
                Guardar
              </Boton>
            </div>
          </div>
        </form>
      </div>

      <PruebaCorreo configurado={datos.configurado} sinGuardar={isDirty} />
    </div>
  );
}

function PruebaCorreo({ configurado, sinGuardar }: { configurado: boolean; sinGuardar: boolean }) {
  const [resultado, setResultado] = useState<{ tipo: 'exito' | 'advertencia' | 'error'; titulo: string; texto?: string } | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<z.input<typeof esquemaPruebaCorreo>>({ resolver: zodResolver(esquemaPruebaCorreo), mode: 'onTouched', defaultValues: { destino: '' } });

  const enviar = handleSubmit(async ({ destino }) => {
    setResultado(null);
    try {
      const r: ResultadoPruebaCorreo = (await correoApi.prueba(destino.trim().toLowerCase())) ?? {};
      const fallo = r.enviado === false || /FALL|ERROR/i.test(r.estado ?? '');
      if (fallo) setResultado({ tipo: 'error', titulo: 'No se pudo enviar', texto: r.ultimoError ?? r.error ?? r.mensaje ?? undefined });
      else if (r.simulado || /SIMUL/i.test(r.estado ?? ''))
        setResultado({ tipo: 'advertencia', titulo: 'Prueba simulada', texto: 'No hay servidor de correo: el correo no salió (queda en Notificaciones).' });
      else setResultado({ tipo: 'exito', titulo: 'Correo enviado', texto: r.mensaje ?? `Revisa la bandeja de ${r.destino ?? destino} (y la de spam).` });
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['destino']);
      if (!aplicados) setResultado({ tipo: 'error', titulo: 'No se pudo enviar', texto: esErrorApi(e) ? e.message : 'Revisa los datos del servidor.' });
    }
  });

  return (
    <section className="pc">
      <div className="pc-cab">
        <h2>
          <Send className="ico" aria-hidden />
          Correo de prueba
        </h2>
      </div>
      <form className="pc-cuerpo grid gap-3" onSubmit={enviar} noValidate>
        <CampoTexto label="Enviar a" requerido type="email" icono={Mail} autoCapitalize="none" maxLength={150} placeholder="tucorreo@gmail.com" error={errors.destino?.message} {...register('destino')} />
        {sinGuardar && <Alerta tipo="info">Guarda los cambios antes de probar.</Alerta>}
        {!configurado && !sinGuardar && <Alerta tipo="advertencia">Aún no hay servidor: la prueba quedará simulada.</Alerta>}
        <div>
          <Boton type="submit" variante="oscuro" icono={Send} disabled={sinGuardar} cargando={isSubmitting} textoCargando="Enviando…">
            Enviar correo de prueba
          </Boton>
        </div>
        {resultado && (
          <Alerta tipo={resultado.tipo} titulo={resultado.titulo}>
            {resultado.texto}
          </Alerta>
        )}
      </form>
    </section>
  );
}
