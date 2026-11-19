import { useEffect, useRef, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  ArrowLeft,
  BadgeCheck,
  Building2,
  CircleAlert,
  FileText,
  Fingerprint,
  Lock,
  Mail,
  MapPin,
  MessageCircle,
  Receipt,
  Search,
  Smartphone,
  UserRound,
  UsersRound,
} from 'lucide-react';
import { documentosApi } from '@/api/servicios';
import { esErrorApi } from '@/api/cliente';
import { useReserva } from '@/contexto/ReservaContext';
import { useCuenta } from '@/contexto/CuentaContext';
import { Link } from 'react-router-dom';
import { CampoTexto, Casilla } from '@/componentes/ui/Campo';
import { Alerta, Insignia } from '@/componentes/ui/Estados';
import { Segmentado } from '@/componentes/ui/Datos';
import { esquemaDatosCliente, type DatosClienteEntrada } from '@/esquemas/reserva';
import { MSJ } from '@/esquemas/comunes';
import { RE_DNI, normalizarCelular, rucValido } from '@/utilidades/documentos';
import { aTitulo } from '@/utilidades/formato';
import { CabezaPaso } from './CabezaPaso';

const VACIO: DatosClienteEntrada = {
  dni: '',
  nombres: '',
  apellidos: '',
  telefono: '',
  correo: '',
  tipoComprobante: 'BOLETA',
  ruc: '',
  razonSocial: '',
  direccionFiscal: '',
  aceptaTerminos: false,
  aceptaPrivacidad: false,
};
type EstadoConsulta = 'inicial' | 'buscando' | 'verificado' | 'no-encontrado' | 'no-disponible';
const CAMPOS = ['dni', 'nombres', 'apellidos', 'telefono', 'correo', 'tipoComprobante', 'ruc', 'razonSocial', 'direccionFiscal', 'aceptaTerminos', 'aceptaPrivacidad'] as const;

function BotonLupa({ alPulsar, buscando, etiqueta, deshabilitado }: { alPulsar: () => void; buscando: boolean; etiqueta: string; deshabilitado?: boolean }) {
  return (
    <button type="button" className="control-boton" onClick={alPulsar} disabled={deshabilitado || buscando} aria-label={etiqueta} title={etiqueta}>
      {buscando ? <span className="spinner" aria-hidden /> : <Search className="ico ico-18" aria-hidden />}
    </button>
  );
}

export default function Paso2() {
  const r = useReserva();
  // v2: con sesión de cliente el paso se llena con su cuenta y el DNI queda fijo (el back lo toma del token).
  const { cliente: cuenta, salir } = useCuenta();
  const deCuenta = Boolean(cuenta);
  const [dniEstado, setDniEstado] = useState<EstadoConsulta>(r.verificadoReniec || deCuenta ? 'verificado' : 'inicial');
  const [dniMensaje, setDniMensaje] = useState('');
  const [rucEstado, setRucEstado] = useState<EstadoConsulta>('inicial');
  const [rucMensaje, setRucMensaje] = useState('');
  const ultimoDni = useRef<string>(deCuenta ? cuenta?.dni ?? '' : r.verificadoReniec ? r.cliente?.dni ?? '' : '');
  const ultimoRuc = useRef<string>('');

  const {
    register,
    handleSubmit,
    control,
    watch,
    setValue,
    setError,
    getValues,
    trigger,
    formState: { errors },
  } = useForm<DatosClienteEntrada>({
    resolver: zodResolver(esquemaDatosCliente),
    mode: 'onTouched',
    defaultValues: {
      ...VACIO,
      ...(r.cliente ?? {}),
      ...(cuenta ? { dni: cuenta.dni, nombres: cuenta.nombres, apellidos: cuenta.apellidos, telefono: r.cliente?.telefono || cuenta.telefono, correo: r.cliente?.correo || cuenta.correo } : {}),
    },
  });

  // Errores por campo que mandó el back al confirmar (vuelven a este paso).
  useEffect(() => {
    if (r.erroresServidor?.paso !== 2) return;
    let primero = true;
    for (const [campo, mensaje] of Object.entries(r.erroresServidor.errores)) {
      if ((CAMPOS as readonly string[]).includes(campo)) {
        setError(campo as (typeof CAMPOS)[number], { type: 'servidor', message: mensaje }, { shouldFocus: primero });
        primero = false;
      }
    }
    r.limpiarErroresServidor();
  }, [r, setError]);

  const dni = watch('dni') ?? '';
  const tipo = watch('tipoComprobante');
  const ruc = watch('ruc') ?? '';

  // ---------- RENIEC ----------
  const consultarDni = async (valor: string) => {
    if (!RE_DNI.test(valor)) {
      void trigger('dni');
      return;
    }
    ultimoDni.current = valor;
    setDniEstado('buscando');
    try {
      const d = await documentosApi.dni(valor);
      if (getValues('dni') !== valor) return;
      setValue('nombres', aTitulo(d.nombres), { shouldValidate: true, shouldDirty: true });
      setValue('apellidos', aTitulo(d.apellidos || `${d.apellidoPaterno} ${d.apellidoMaterno}`), { shouldValidate: true, shouldDirty: true });
      setDniEstado('verificado');
    } catch (e) {
      if (esErrorApi(e) && e.status === 404) {
        setDniEstado('no-encontrado');
        setDniMensaje('No encontramos ese DNI. Escribe tus nombres y apellidos.');
      } else {
        setDniEstado('no-disponible');
        setDniMensaje(esErrorApi(e) && e.status === 429 ? e.message : 'La consulta a RENIEC no responde. Escribe tus datos a mano.');
      }
    }
  };

  // Al completar 8 dígitos se consulta solo; si cambia el DNI se libera lo que vino de RENIEC.
  useEffect(() => {
    if (dni === ultimoDni.current) return;
    if (dniEstado === 'verificado') {
      setValue('nombres', '');
      setValue('apellidos', '');
    }
    if (dniEstado !== 'inicial' && dniEstado !== 'buscando') setDniEstado('inicial');
    if (RE_DNI.test(dni)) void consultarDni(dni);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dni]);

  // ---------- SUNAT ----------
  const consultarRuc = async (valor: string) => {
    if (!rucValido(valor)) {
      setError('ruc', { type: 'manual', message: MSJ.ruc });
      return;
    }
    ultimoRuc.current = valor;
    setRucEstado('buscando');
    try {
      const d = await documentosApi.ruc(valor);
      if (getValues('ruc') !== valor) return;
      setValue('razonSocial', d.razonSocial, { shouldValidate: true });
      setValue('direccionFiscal', d.direccion, { shouldValidate: true });
      setRucEstado('verificado');
      setRucMensaje(d.estado && d.estado !== 'ACTIVO' ? `SUNAT: ${d.estado} · ${d.condicion}` : '');
    } catch (e) {
      setRucEstado(esErrorApi(e) && e.status === 404 ? 'no-encontrado' : 'no-disponible');
      setRucMensaje(esErrorApi(e) && e.status === 404 ? 'No encontramos ese RUC. Escribe la razón social y la dirección.' : 'La consulta a SUNAT no responde. Escribe los datos a mano.');
    }
  };
  useEffect(() => {
    if (ruc === ultimoRuc.current) return;
    if (rucEstado === 'verificado') {
      setValue('razonSocial', '');
      setValue('direccionFiscal', '');
    }
    if (rucEstado !== 'inicial' && rucEstado !== 'buscando') setRucEstado('inicial');
    if (/^\d{11}$/.test(ruc) && rucValido(ruc)) void consultarRuc(ruc);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ruc]);

  const enviar = handleSubmit((d) => {
    r.guardarCliente({ ...d, telefono: normalizarCelular(String(d.telefono)), correo: d.correo.trim(), nombres: aTitulo(d.nombres), apellidos: aTitulo(d.apellidos) }, dniEstado === 'verificado');
    r.irAPaso(3);
  });

  const verificado = dniEstado === 'verificado';
  const rucOk = rucEstado === 'verificado';
  const errorAcepta = errors.aceptaTerminos?.message ?? errors.aceptaPrivacidad?.message;

  return (
    <div className="grid gap-5">
      <CabezaPaso
        titulo="Tus datos"
        ilustracion="IL-03-paso2-datos"
        volver={
          <button type="button" className="btn btn-fantasma btn-sm justify-self-start -ml-3" onClick={() => r.irAPaso(1)}>
            <ArrowLeft className="ico" aria-hidden />
            <span>Volver</span>
          </button>
        }
        apoyo={
          deCuenta ? (
            <span className="inline-flex items-center gap-1.5">
              <BadgeCheck className="ico ico-14" aria-hidden />
              Con tu cuenta
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5">
              <UserRound className="ico ico-14" aria-hidden />
              Sin crear cuenta ·
              <Link className="enlace" to="/ingresar?volver=%2Freservar">
                Ingresar
              </Link>
            </span>
          )
        }
      />
      {cuenta && (
        <div className="cuenta-en-paso">
          <BadgeCheck className="ico ico-18 text-verde" aria-hidden />
          <span className="min-w-0">
            <b>{`${cuenta.nombres} ${cuenta.apellidos}`}</b>
            <small>Usamos los datos de tu cuenta. Puedes cambiar el celular o el correo.</small>
          </span>
          <button type="button" className="enlace bg-transparent border-0 p-0 text-[13px] ml-auto whitespace-nowrap shrink-0" onClick={salir}>
            No soy yo
          </button>
        </div>
      )}

      <form id="form-paso2" onSubmit={enviar} noValidate className="grid gap-5">
        <div className="form-grid dos">
          <CampoTexto
            label="DNI"
            requerido
            icono={Fingerprint}
            inputMode="numeric"
            autoComplete="off"
            maxLength={8}
            placeholder="8 dígitos"
            error={errors.dni?.message}
            valido={verificado}
            lectura={deCuenta}
            ayuda={
              deCuenta ? (
                <Insignia tono="menta" icono={BadgeCheck} tamano="sm">
                  DNI de tu cuenta
                </Insignia>
              ) : verificado ? (
                <Insignia tono="menta" icono={BadgeCheck} tamano="sm">
                  Verificado con RENIEC
                </Insignia>
              ) : dniEstado === 'buscando' ? (
                'Consultando RENIEC…'
              ) : (
                'Lo usamos para tu boleta'
              )
            }
            accion={deCuenta ? undefined : <BotonLupa etiqueta="Buscar DNI en RENIEC" buscando={dniEstado === 'buscando'} alPulsar={() => void consultarDni(getValues('dni') ?? '')} />}
            {...register('dni', { setValueAs: (v: string) => String(v ?? '').replace(/\D/g, '').slice(0, 8) })}
          />
          <div className="hidden md:block" aria-hidden />
          {(dniEstado === 'no-encontrado' || dniEstado === 'no-disponible') && (
            <Alerta tipo="advertencia" className="md:col-span-2">
              {dniMensaje}
            </Alerta>
          )}
          <CampoTexto label="Nombres" requerido icono={UserRound} autoComplete="given-name" maxLength={60} lectura={verificado} valido={verificado} error={errors.nombres?.message} {...register('nombres')} />
          <CampoTexto label="Apellidos" requerido icono={UsersRound} autoComplete="family-name" maxLength={60} lectura={verificado} valido={verificado} error={errors.apellidos?.message} {...register('apellidos')} />
          <CampoTexto
            label="Celular"
            requerido
            prefijo={
              <>
                <Smartphone className="ico ico-16" aria-hidden />
                +51
              </>
            }
            type="tel"
            inputMode="tel"
            autoComplete="tel-national"
            maxLength={15}
            placeholder="987 654 321"
            error={errors.telefono?.message}
            {...register('telefono')}
          />
          <CampoTexto label="Correo" requerido icono={Mail} type="email" inputMode="email" autoComplete="email" maxLength={100} placeholder="nombre@correo.com" error={errors.correo?.message} {...register('correo')} />
          <p className="md:col-span-2 t-sec flex items-center gap-2 -mt-1">
            <MessageCircle className="ico ico-16 text-verde" aria-hidden />
            <Mail className="ico ico-16 text-verde" aria-hidden />
            Te enviamos copia por WhatsApp y correo.
          </p>
        </div>

        <section className="grid gap-3" aria-labelledby="h-comprobante">
          <h2 id="h-comprobante" className="inline-flex items-center gap-2 text-[14px] font-semibold">
            <Receipt className="ico ico-16 text-verde" aria-hidden />
            Comprobante
          </h2>
          <Controller
            name="tipoComprobante"
            control={control}
            render={({ field }) => (
              <Segmentado
                etiqueta="Tipo de comprobante"
                className="md:max-w-[360px]"
                bloque
                valor={field.value ?? 'BOLETA'}
                alCambiar={(v) => field.onChange(v)}
                opciones={[
                  { valor: 'BOLETA', texto: 'Boleta', icono: FileText },
                  { valor: 'FACTURA', texto: 'Factura', icono: Building2 },
                ]}
              />
            )}
          />
          {tipo === 'FACTURA' && (
            <div className="form-grid dos animate-entrar">
              <CampoTexto
                label="RUC"
                requerido
                icono={Building2}
                inputMode="numeric"
                maxLength={11}
                autoComplete="off"
                placeholder="11 dígitos"
                error={errors.ruc?.message}
                valido={rucOk}
                ayuda={rucOk ? (rucMensaje ? 'Datos traídos de SUNAT' : <Insignia tono="menta" icono={BadgeCheck} tamano="sm">Verificado con SUNAT</Insignia>) : rucEstado === 'buscando' ? 'Consultando SUNAT…' : 'Empieza con 10 o 20'}
                accion={<BotonLupa etiqueta="Buscar RUC en SUNAT" buscando={rucEstado === 'buscando'} alPulsar={() => void consultarRuc(getValues('ruc') ?? '')} />}
                {...register('ruc', { setValueAs: (v: string) => String(v ?? '').replace(/\D/g, '').slice(0, 11) })}
              />
              <CampoTexto label="Razón social" requerido icono={Building2} maxLength={150} lectura={rucOk} valido={rucOk} error={errors.razonSocial?.message} {...register('razonSocial')} />
              <CampoTexto label="Dirección fiscal" requerido icono={MapPin} maxLength={200} lectura={rucOk} valido={rucOk} className="md:col-span-2" error={errors.direccionFiscal?.message} {...register('direccionFiscal')} />
              {rucOk && rucMensaje && (
                <Alerta tipo="advertencia" className="md:col-span-2">
                  {`${rucMensaje}. Revisa el RUC: SUNAT no lo tiene activo.`}
                </Alerta>
              )}
              {(rucEstado === 'no-encontrado' || rucEstado === 'no-disponible') && (
                <Alerta tipo="advertencia" className="md:col-span-2">
                  {rucMensaje}
                </Alerta>
              )}
            </div>
          )}
        </section>

        <section className="grid gap-3" aria-label="Consentimientos">
          <p className="t-sec flex gap-2 items-start">
            <Lock className="ico ico-16 text-profundo mt-0.5" aria-hidden />
            <span>Usamos tus datos solo para esta reserva (Ley 29733).</span>
          </p>
          <Casilla {...register('aceptaTerminos')} aria-invalid={errors.aceptaTerminos ? true : undefined} className={errors.aceptaTerminos ? '[&_.check-caja]:!border-rojo' : undefined}>
            Acepto los{' '}
            <a href="/terminos" target="_blank" rel="noopener">
              términos y condiciones
            </a>
          </Casilla>
          <Casilla {...register('aceptaPrivacidad')} aria-invalid={errors.aceptaPrivacidad ? true : undefined} className={errors.aceptaPrivacidad ? '[&_.check-caja]:!border-rojo' : undefined}>
            Acepto la{' '}
            <a href="/privacidad" target="_blank" rel="noopener">
              política de privacidad
            </a>
          </Casilla>
          {errorAcepta && (
            <div className="campo-error pl-[34px]" role="alert">
              <CircleAlert className="ico" aria-hidden />
              <span>{errorAcepta}</span>
            </div>
          )}
        </section>
        <button type="submit" className="sr-only" tabIndex={-1}>
          Continuar
        </button>
      </form>
    </div>
  );
}
