import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { z } from 'zod';
import {
  CalendarClock,
  ChevronRight,
  CircleCheck,
  Link2Off,
  MessageCircle,
  QrCode,
  RefreshCw,
  RotateCcw,
  Save,
  Send,
  Server,
  Smartphone,
  TriangleAlert,
} from 'lucide-react';
import { configuracionApi, whatsappApi } from '@/api/admin';
import type { ConexionWhatsapp, DetalleNotificacion } from '@/api/admin/tipos';
import { esErrorApi } from '@/api/cliente';
import { useToast } from '@/contexto/ToastContext';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { ErrorCarga, Estado, ModalConfirmar } from '@/componentes/panel/Herramientas';
import { Boton } from '@/componentes/ui/Boton';
import { AreaTexto, CampoTexto } from '@/componentes/ui/Campo';
import { Cargando, Spinner } from '@/componentes/ui/Datos';
import { Alerta } from '@/componentes/ui/Estados';
import { Ilustracion } from '@/componentes/ui/Marca';
import { Modal } from '@/componentes/ui/Modal';
import { fmtCelular } from '@/utilidades/formato';
import { normalizarCelular } from '@/utilidades/documentos';
import type { IdIlustracion } from '@/utilidades/recursos';
import { ChipsRecordatorios } from './ChipsRecordatorios';
import { GRUPOS_CONFIG, esquemaConectarWa, esquemaGrupoConfig, esquemaPruebaWa, type DefClave } from './esquemasPanel';
import { TextoWhatsapp } from './Notificaciones';
import Feriados from './Feriados';
import ConfigCorreo from './ConfigCorreo';
import { TarjetaCanchita } from './TarjetaCanchita';

/** Tarjetas de Configuración (SPEC_V2 §F): ilustración + una línea; cada una abre su pantalla. */
interface Tarjeta {
  id: string;
  titulo: string;
  texto: string;
  ilustracion: IdIlustracion;
}
const TARJETAS: Tarjeta[] = [
  { id: 'complejo', titulo: 'Datos del complejo', texto: 'Nombre, dirección y mapa del portal', ilustracion: 'ARQ-03-canchas-techadas' },
  { id: 'contacto', titulo: 'Contacto y pagos', texto: 'WhatsApp, correo, Yape y Plin', ilustracion: 'IL-21-compartir' },
  { id: 'reglas', titulo: 'Reglas de reserva', texto: 'Adelanto, plazo para pagar y devoluciones', ilustracion: 'ARQ-02-planificando' },
  { id: 'feriados', titulo: 'Feriados y días no laborables', texto: 'Calendario del año y días cerrados', ilustracion: 'IL-26-sin-turnos' },
  { id: 'comprobantes', titulo: 'Comprobantes', texto: 'Emisor de boletas y facturas', ilustracion: 'ARQ-06-datos-ordenados' },
  { id: 'whatsapp', titulo: 'WhatsApp', texto: 'Conecta el número con el QR', ilustracion: 'IL-04-paso3-comprobante' },
  { id: 'correo', titulo: 'Correo', texto: 'Envío de correos a los clientes', ilustracion: 'IL-23-todo-al-dia' },
];

/** /admin/configuracion[/{tarjeta}] — solo ADMIN. */
export default function Configuracion() {
  const sub = useParams()['*'] ?? '';
  const [sp] = useSearchParams();
  // Enlace antiguo: ?pestana=whatsapp
  if (!sub && sp.get('pestana') === 'whatsapp') return <Navigate to="/admin/configuracion/whatsapp" replace />;
  const tarjeta = TARJETAS.find((t) => t.id === sub);
  if (!tarjeta) return <Tarjetas />;
  if (tarjeta.id === 'feriados') return <Feriados />;
  return <PantallaTarjeta tarjeta={tarjeta} />;
}

function Tarjetas() {
  useTituloPagina('Configuración');
  return (
    <>
      <CabeceraPagina titulo="Configuración" subtitulo="Elige qué quieres cambiar" busto="BU-rosa-agenda" />
      <div className="conf-tarjetas">
        {TARJETAS.map((t) => (
          <Link key={t.id} to={`/admin/configuracion/${t.id}`} className="conf-tarjeta">
            <span className="conf-tarjeta-il">
              <Ilustracion id={t.ilustracion} />
            </span>
            <span className="conf-tarjeta-txt">
              <b>{t.titulo}</b>
              <span>{t.texto}</span>
            </span>
            <ChevronRight className="ico conf-tarjeta-ir" aria-hidden />
          </Link>
        ))}
      </div>
    </>
  );
}

function PantallaTarjeta({ tarjeta }: { tarjeta: Tarjeta }) {
  useTituloPagina(tarjeta.titulo);
  return (
    <>
      <CabeceraPagina titulo={tarjeta.titulo} subtitulo={tarjeta.texto} busto="BU-rosa-agenda" volver="/admin/configuracion" />
      {tarjeta.id === 'whatsapp' ? <Whatsapp /> : tarjeta.id === 'correo' ? <ConfigCorreo /> : <FormGrupo id={tarjeta.id} ilustracion={tarjeta.ilustracion} />}
    </>
  );
}

// ------------------------------ formulario de una tarjeta ------------------------------
function FormGrupo({ id, ilustracion }: { id: string; ilustracion: IdIlustracion }) {
  const toast = useToast();
  const defs = GRUPOS_CONFIG.find((g) => g.id === id)?.claves ?? [];
  const conf = useCarga(() => configuracionApi.obtener(), 'configuracion');
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  // v1 → v2: si el back aún no tiene `recordatorio_minutos`, se parte de `recordatorio_horas`; `asistencia_desde_min` por defecto 30.
  const respaldo = (c: Record<string, string>, clave: string) =>
    clave === 'recordatorio_minutos' && c.recordatorio_horas ? String(Number(c.recordatorio_horas) * 60) : clave === 'asistencia_desde_min' ? '30' : '';
  const valoresDe = (c: Record<string, string>) => Object.fromEntries(defs.map((d) => [d.clave, c[d.clave] ?? respaldo(c, d.clave)]));
  const {
    register,
    handleSubmit,
    reset,
    setError,
    setValue,
    watch,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<Record<string, string>>({ resolver: zodResolver(esquemaGrupoConfig(defs)), mode: 'onTouched' });
  useEffect(() => {
    if (conf.datos) reset(valoresDe(conf.datos));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conf.datos, reset]);

  const guardar = handleSubmit(async (v) => {
    setErrorGeneral(null);
    const cambios = Object.fromEntries(defs.filter((d) => (v[d.clave] ?? '').trim() !== (conf.datos?.[d.clave] ?? '')).map((d) => [d.clave, (v[d.clave] ?? '').trim()]));
    if (!Object.keys(cambios).length) return toast.info('Sin cambios');
    try {
      const r = await configuracionApi.guardar(cambios);
      conf.setDatos(r);
      reset(valoresDe(r));
      toast.exito('Guardado');
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, defs.map((d) => d.clave));
      if (!aplicados) setErrorGeneral(esErrorApi(e) ? e.message : 'No se pudo guardar.');
    }
  });

  if (conf.error) return <ErrorCarga alReintentar={conf.recargar} />;
  if (!conf.datos) return <Cargando texto="Cargando…" />;
  /** Un campo de texto según su tipo. */
  const campoSolo = (d: DefClave) => {
    const comun = { label: d.etiqueta, requerido: true, error: errors[d.clave]?.message, ayuda: d.ayuda, ...register(d.clave) };
    if (d.tipo === 'CELULAR') return <CampoTexto key={d.clave} prefijo="+51" inputMode="tel" maxLength={15} {...comun} />;
    if (d.tipo === 'ENTERO' || d.tipo === 'PORCENTAJE') return <CampoTexto key={d.clave} inputMode="numeric" sufijo={d.sufijo} maxLength={5} {...comun} />;
    if (d.tipo === 'LATITUD' || d.tipo === 'LONGITUD') return <CampoTexto key={d.clave} inputMode="decimal" maxLength={13} {...comun} />;
    if (d.tipo === 'RUC') return <CampoTexto key={d.clave} inputMode="numeric" maxLength={11} {...comun} />;
    if (d.clave === 'direccion' || d.clave === 'referencia') return <AreaTexto key={d.clave} rows={2} maxLength={d.max} {...comun} />;
    return <CampoTexto key={d.clave} type={d.tipo === 'CORREO' ? 'email' : d.tipo === 'URL' ? 'url' : 'text'} maxLength={d.max} {...comun} />;
  };
  /** `completo`: ocupa toda la fila (p. ej. el enlace de Maps, para que Latitud y Longitud queden juntas). */
  const campo = (d: DefClave) => {
    if (d.tipo === 'MINUTOS_LISTA')
      return (
        <ChipsRecordatorios
          key={d.clave}
          etiqueta={d.etiqueta}
          ayuda={d.ayuda}
          min={d.min ?? 5}
          max={d.max ?? 2880}
          valor={watch(d.clave) ?? ''}
          error={errors[d.clave]?.message}
          alCambiar={(v) => setValue(d.clave, v, { shouldDirty: true, shouldValidate: true })}
        />
      );
    if (d.completo) return <div key={d.clave} className="sm:col-span-2 min-w-0">{campoSolo(d)}</div>;
    return campoSolo(d);
  };

  return (
    <form className="pc conf-form" onSubmit={guardar} noValidate>
      <div className="conf-form-il solo-tablet-arriba" aria-hidden>
        <Ilustracion id={ilustracion} />
      </div>
      <div className="pc-cuerpo grid gap-4 min-w-0">
        <div className="form-2">{defs.map(campo)}</div>
        {errorGeneral && <Alerta tipo="error">{errorGeneral}</Alerta>}
        <div className="flex flex-wrap gap-2 justify-end">
          <Boton variante="fantasma" icono={RotateCcw} disabled={!isDirty} onClick={() => conf.datos && reset(valoresDe(conf.datos))}>
            Deshacer
          </Boton>
          <Boton type="submit" icono={Save} disabled={!isDirty} cargando={isSubmitting} textoCargando="Guardando…">
            Guardar
          </Boton>
        </div>
      </div>
    </form>
  );
}

// ------------------------------ WhatsApp (Evolution API) ------------------------------
const TEXTO_ESTADO_WA: Record<string, { titulo: string; texto: string; tono: 'ok' | 'no' | '' }> = {
  open: { titulo: 'Conectado', texto: 'Los avisos salen por este número.', tono: 'ok' },
  connecting: { titulo: 'Esperando escaneo', texto: 'Escanea el QR con el celular del complejo.', tono: 'no' },
  close: { titulo: 'Desconectado', texto: 'Conecta el número del complejo con el QR.', tono: 'no' },
  NO_CONFIGURADO: { titulo: 'Sin configurar', texto: 'El servidor no tiene Evolution API: los avisos quedan SIMULADOS (se ven en Notificaciones).', tono: '' },
};

function Whatsapp() {
  const toast = useToast();
  const [version, setVersion] = useState(0);
  const est = useCarga(() => whatsappApi.estado(), `wa-${version}`);
  const fijarEstado = est.setDatos;
  const [qr, setQr] = useState<{ cargando: boolean; datos: ConexionWhatsapp | null; error: { codigo: string; mensaje: string } | null } | null>(null);
  const [desconectar, setDesconectar] = useState(false);
  const [prueba, setPrueba] = useState<DetalleNotificacion | null>(null);
  const sondeo = useRef<number | null>(null);
  const conectarForm = useForm<z.input<typeof esquemaConectarWa>>({ resolver: zodResolver(esquemaConectarWa), mode: 'onTouched', defaultValues: { telefono: '' } });
  const pruebaForm = useForm<z.input<typeof esquemaPruebaWa>>({
    resolver: zodResolver(esquemaPruebaWa),
    mode: 'onTouched',
    defaultValues: { telefono: '', mensaje: 'Hola, este es un mensaje de prueba del Complejo Deportivo Los Alcanfores.' },
  });

  // Mientras el QR está abierto, consulta el estado cada 3 s hasta «open».
  useEffect(() => {
    if (!qr?.datos) return undefined;
    sondeo.current = window.setInterval(async () => {
      try {
        const e = await whatsappApi.estado();
        if (e.estado === 'open') {
          toast.exito(`WhatsApp conectado${e.numero ? ` · ${fmtCelular(e.numero.slice(-9))}` : ''}`);
          setQr(null);
          fijarEstado(e);
        }
      } catch {
        /* se reintenta en el siguiente ciclo */
      }
    }, 3000);
    return () => {
      if (sondeo.current) window.clearInterval(sondeo.current);
    };
  }, [qr?.datos, toast, fijarEstado]);

  const conectar = conectarForm.handleSubmit(async (d) => {
    setQr({ cargando: true, datos: null, error: null });
    try {
      const r = await whatsappApi.conectar(d.telefono ? normalizarCelular(d.telefono) : undefined);
      setQr({ cargando: false, datos: r, error: null });
    } catch (e) {
      setQr({ cargando: false, datos: null, error: esErrorApi(e) ? { codigo: e.codigo, mensaje: e.message } : { codigo: 'ERROR', mensaje: 'No se pudo conectar.' } });
    }
  });
  const enviarPrueba = pruebaForm.handleSubmit(async (d) => {
    try {
      const r = await whatsappApi.prueba(normalizarCelular(d.telefono), d.mensaje);
      setPrueba(r);
      toast.exito(r.estado === 'SIMULADO' ? 'Prueba registrada (simulada)' : r.estado === 'FALLIDO' ? 'La prueba falló' : 'Mensaje enviado');
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, pruebaForm.setError, ['telefono', 'mensaje']);
      if (!aplicados) toast.error(esErrorApi(e) ? e.message : 'No se pudo enviar.');
    }
  });
  const hacerDesconexion = async () => {
    try {
      await whatsappApi.desconectar();
      toast.exito('WhatsApp desconectado');
      setDesconectar(false);
      setVersion((v) => v + 1);
    } catch {
      setDesconectar(false);
    }
  };

  const e = est.datos;
  const info = TEXTO_ESTADO_WA[e?.estado ?? ''] ?? { titulo: e?.estado ?? '—', texto: '', tono: '' as const };

  return (
    <div className="grid gap-4 xl:grid-cols-2 xl:items-start">
      <div className="grid gap-4">
        <section className="pc">
          <div className="pc-cab">
            <h2>
              <MessageCircle className="ico" aria-hidden />
              Conexión
            </h2>
            <div className="pc-der">
              <Boton variante="fantasma" tamano="sm" icono={RefreshCw} etiqueta="Actualizar estado" onClick={() => setVersion((v) => v + 1)} />
            </div>
          </div>
          {est.error ? (
            <div className="pc-cuerpo">
              <Alerta tipo="error">{esErrorApi(est.error) ? est.error.message : 'No se pudo leer el estado.'}</Alerta>
            </div>
          ) : !e ? (
            <Cargando className="!py-8" />
          ) : (
            <>
              <div className="estado-wa">
                <span className={`punto-wa ${info.tono}`}>{e.estado === 'open' ? <CircleCheck className="ico ico-24" aria-hidden /> : e.estado === 'NO_CONFIGURADO' ? <Server className="ico ico-24" aria-hidden /> : <Smartphone className="ico ico-24" aria-hidden />}</span>
                <div className="grid gap-0.5">
                  <b className="text-[17px]">{info.titulo}</b>
                  <span className="t-sec">{info.texto}</span>
                  {e.numero && <b className="tnum text-verde">+{e.numero}</b>}
                </div>
                <span className="ml-auto">
                  <span className="badge b-neutro badge-sm">
                    <span>Instancia «{e.instancia}»</span>
                  </span>
                </span>
              </div>
              {!e.configurado && (
                <div className="pc-cuerpo border-t border-borde-panel grid gap-3">
                  <Alerta tipo="advertencia" titulo="Qué falta en el servidor">
                    Definir en notificaciones-service <b>EVOLUTION_URL</b> (la URL de Evolution API v2), <b>EVOLUTION_APIKEY</b> (cabecera apikey) y, si quieres otro nombre, <b>EVOLUTION_INSTANCIA</b>. Luego reinicia el servicio y vuelve a esta pestaña para escanear el QR.
                  </Alerta>
                </div>
              )}
              <form className="pc-cuerpo border-t border-borde-panel grid gap-3" onSubmit={conectar} noValidate>
                <CampoTexto
                  label="Celular del complejo"
                  opcional
                  prefijo="+51"
                  inputMode="tel"
                  maxLength={15}
                  ayuda="Para recibir también un código de emparejamiento"
                  error={conectarForm.formState.errors.telefono?.message}
                  {...conectarForm.register('telefono')}
                />
                <div className="flex flex-wrap gap-2">
                  <Boton type="submit" icono={QrCode} disabled={e.estado === 'open'}>
                    Conectar
                  </Boton>
                  {e.configurado && e.estado !== 'NO_CONFIGURADO' && (
                    <Boton variante="peligro-contorno" icono={Link2Off} onClick={() => setDesconectar(true)}>
                      Desconectar
                    </Boton>
                  )}
                </div>
              </form>
            </>
          )}
        </section>
        <section className="pc">
          <div className="pc-cab">
            <h2>
              <CalendarClock className="ico" aria-hidden />
              Cómo vincular
            </h2>
          </div>
          <ol className="pasos-wa pc-cuerpo m-0">
            <li>Pulsa «Conectar»: aparece el QR (y el código si escribiste el celular).</li>
            <li>En el celular del complejo abre WhatsApp › Dispositivos vinculados › Vincular un dispositivo.</li>
            <li>Escanea el QR. Esta pantalla pasa sola a «Conectado» con el número vinculado.</li>
            <li>Envía un mensaje de prueba para comprobarlo.</li>
          </ol>
        </section>
      </div>

      <section className="pc">
        <div className="pc-cab">
          <h2>
            <Send className="ico" aria-hidden />
            Mensaje de prueba
          </h2>
        </div>
        <form className="pc-cuerpo grid gap-3" onSubmit={enviarPrueba} noValidate>
          <CampoTexto label="Celular" requerido prefijo="+51" inputMode="tel" maxLength={15} error={pruebaForm.formState.errors.telefono?.message} {...pruebaForm.register('telefono')} />
          <AreaTexto label="Mensaje" requerido rows={3} maxLength={1000} contador={{ actual: (pruebaForm.watch('mensaje') ?? '').length, max: 1000 }} error={pruebaForm.formState.errors.mensaje?.message} {...pruebaForm.register('mensaje')} />
          <div>
            <Boton type="submit" variante="oscuro" icono={Send} cargando={pruebaForm.formState.isSubmitting} textoCargando="Enviando…">
              Enviar prueba
            </Boton>
          </div>
          {prueba && (
            <div className="grid gap-2">
              <div className="flex items-center gap-2">
                <Estado dominio="notificacion" estado={prueba.estado} />
                <span className="t-sec text-[12.5px] tnum">a {prueba.destino}</span>
              </div>
              {prueba.ultimoError && <Alerta tipo="error">{prueba.ultimoError}</Alerta>}
              <div className="prev-wa !min-h-0">
                <div className="burbuja">
                  <TextoWhatsapp texto={prueba.cuerpo} />
                  <small>{(prueba.enviadoEn ?? prueba.creadoEn).slice(11, 16)}</small>
                </div>
              </div>
            </div>
          )}
        </form>
      </section>

      {/* Canchita (IA): debajo de la conexión, a todo el ancho. */}
      <TarjetaCanchita className="xl:col-span-2" />

      {/* QR */}
      <Modal abierto={qr !== null} alCerrar={() => setQr(null)} titulo="Vincular WhatsApp" subtitulo={`Instancia «${e?.instancia ?? 'alcanfores'}»`} busto="BU-rosa-laptop" acciones={<Boton variante="fantasma" onClick={() => setQr(null)}>Cerrar</Boton>}>
        {qr?.cargando ? (
          <div className="grid place-items-center gap-3 py-10">
            <Spinner grande />
            <span className="t-sec">Pidiendo el QR a Evolution API…</span>
          </div>
        ) : qr?.error ? (
          <div className="grid gap-3">
            <div className="qr-caja">
              <span className="punto-wa grid place-items-center w-[64px] h-[64px] rounded-[16px] bg-amarillo/15 text-amarillo">
                <TriangleAlert className="ico ico-32" aria-hidden />
              </span>
              <b className="text-noche text-[16px] text-center">{qr.error.codigo === 'WHATSAPP_NO_CONFIGURADO' ? 'WhatsApp sin configurar' : 'Evolution API no responde'}</b>
              <span className="text-center t-sec text-[13.5px]">{qr.error.mensaje}</span>
            </div>
            {qr.error.codigo === 'WHATSAPP_NO_CONFIGURADO' ? (
              <div className="caja-aviso">
                <div>
                  <Server className="ico" aria-hidden />
                  Falta EVOLUTION_URL y EVOLUTION_APIKEY en notificaciones-service
                </div>
                <div>
                  <MessageCircle className="ico" aria-hidden />
                  Mientras tanto, los avisos quedan SIMULADOS
                </div>
              </div>
            ) : (
              <span className="t-sec text-[13px] text-center">Revisa que Evolution API esté encendido y vuelve a intentar.</span>
            )}
          </div>
        ) : qr?.datos ? (
          <div className="grid gap-3">
            <div className="qr-caja">
              {qr.datos.qrBase64 ? <img src={qr.datos.qrBase64.startsWith('data:') ? qr.datos.qrBase64 : `data:image/png;base64,${qr.datos.qrBase64}`} alt="Código QR para vincular WhatsApp" /> : <span className="t-sec">Sin QR</span>}
              {qr.datos.codigoEmparejamiento && <span className="codigo-par">{qr.datos.codigoEmparejamiento}</span>}
              <span className="dato-ico">
                <Spinner />
                Esperando que escanees…
              </span>
            </div>
            {qr.datos.mensaje && <span className="t-sec text-[13px] text-center">{qr.datos.mensaje}</span>}
          </div>
        ) : null}
      </Modal>
      <ModalConfirmar abierto={desconectar} alCerrar={() => setDesconectar(false)} titulo="Desconectar WhatsApp" subtitulo={e?.numero ? `+${e.numero}` : e?.instancia} busto="BU-rosa-laptop" peligro textoConfirmar="Desconectar" iconoConfirmar={Link2Off} alConfirmar={hacerDesconexion}>
        <div className="caja-aviso">
          <div>
            <Link2Off className="ico" aria-hidden />
            Los avisos dejarán de salir por WhatsApp hasta volver a vincular
          </div>
        </div>
      </ModalConfirmar>
    </div>
  );
}
