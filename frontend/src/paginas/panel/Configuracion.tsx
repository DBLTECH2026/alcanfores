import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { z } from 'zod';
import {
  ArrowRight,
  Building2,
  CalendarClock,
  CircleCheck,
  Clock,
  Link2Off,
  MessageCircle,
  QrCode,
  RefreshCw,
  RotateCcw,
  Save,
  ScrollText,
  Send,
  Server,
  Settings,
  Smartphone,
  TriangleAlert,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import { configuracionApi, whatsappApi } from '@/api/admin';
import type { Configuracion as Conf, ConexionWhatsapp, DetalleNotificacion } from '@/api/admin/tipos';
import { esErrorApi } from '@/api/cliente';
import { useToast } from '@/contexto/ToastContext';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { ErrorCarga, Estado, ModalConfirmar, useFiltrosUrl } from '@/componentes/panel/Herramientas';
import { Boton } from '@/componentes/ui/Boton';
import { AreaTexto, CampoTexto } from '@/componentes/ui/Campo';
import { Cargando, Pestanas, Spinner } from '@/componentes/ui/Datos';
import { Alerta } from '@/componentes/ui/Estados';
import { Modal } from '@/componentes/ui/Modal';
import { fmtCelular } from '@/utilidades/formato';
import { normalizarCelular } from '@/utilidades/documentos';
import { DEFS_CONFIG, GRUPOS_CONFIG, esquemaConectarWa, esquemaConfiguracion, esquemaPruebaWa, type DefClave } from './esquemasPanel';
import { TextoWhatsapp } from './Notificaciones';

const ICONO_GRUPO: Record<string, LucideIcon> = { complejo: Building2, contacto: Wallet, horario: Clock, reglas: ScrollText };

/** /admin/configuracion — datos del complejo y reglas (todas las claves) + conexión del WhatsApp (Evolution API). Solo ADMIN. */
export default function Configuracion() {
  useTituloPagina('Configuración');
  const f = useFiltrosUrl();
  const pestana = f.leer('pestana') === 'whatsapp' ? 'whatsapp' : 'datos';
  return (
    <>
      <CabeceraPagina titulo="Configuración" subtitulo="Datos del complejo, reglas de reserva y WhatsApp" busto="BU-rosa-agenda" />
      <Pestanas
        etiqueta="Sección"
        activa={pestana}
        alCambiar={(p) => f.poner({ pestana: p === 'datos' ? null : p })}
        items={[
          { valor: 'datos', texto: 'Complejo y reglas', icono: Settings },
          { valor: 'whatsapp', texto: 'WhatsApp', icono: MessageCircle },
        ]}
      />
      {pestana === 'datos' ? <DatosComplejo /> : <Whatsapp />}
    </>
  );
}

// ------------------------------ datos y reglas ------------------------------
function DatosComplejo() {
  const toast = useToast();
  const conf = useCarga(() => configuracionApi.obtener(), 'configuracion');
  const [confirmar, setConfirmar] = useState<Array<[string, string, string]> | null>(null);
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    getValues,
    formState: { errors, isDirty },
  } = useForm<z.infer<typeof esquemaConfiguracion>>({ resolver: zodResolver(esquemaConfiguracion), mode: 'onTouched' });
  useEffect(() => {
    if (conf.datos) reset(conf.datos);
  }, [conf.datos, reset]);

  const cambios = (valores: Conf) => DEFS_CONFIG.filter((d) => (valores[d.clave] ?? '').trim() !== (conf.datos?.[d.clave] ?? '')).map((d) => [d.clave, conf.datos?.[d.clave] ?? '', (valores[d.clave] ?? '').trim()] as [string, string, string]);
  const revisar = handleSubmit((v) => {
    const c = cambios(v);
    if (!c.length) return toast.info('Sin cambios');
    setConfirmar(c);
  });
  const guardar = async () => {
    if (!confirmar) return;
    setErrorGeneral(null);
    try {
      const r = await configuracionApi.guardar(Object.fromEntries(confirmar.map(([k, , n]) => [k, n])));
      conf.setDatos(r);
      reset(r);
      toast.exito('Configuración guardada');
      setConfirmar(null);
    } catch (e) {
      setConfirmar(null);
      const { aplicados } = aplicarErroresServidor(e, setError, DEFS_CONFIG.map((d) => d.clave));
      if (!aplicados) setErrorGeneral(esErrorApi(e) ? e.message : 'No se pudo guardar.');
    }
  };
  const etiqueta = (k: string) => DEFS_CONFIG.find((d) => d.clave === k)?.etiqueta ?? k;

  if (conf.error) return <ErrorCarga alReintentar={conf.recargar} />;
  if (!conf.datos) return <Cargando texto="Cargando configuración…" />;
  const campo = (d: DefClave) => {
    const err = errors[d.clave]?.message;
    const comun = { label: d.etiqueta, requerido: true, error: err, ayuda: d.ayuda, ...register(d.clave) };
    if (d.tipo === 'HORA') return <CampoTexto key={d.clave} type="time" step={900} icono={Clock} {...comun} />;
    if (d.tipo === 'CELULAR') return <CampoTexto key={d.clave} prefijo="+51" inputMode="tel" maxLength={15} {...comun} />;
    if (d.tipo === 'ENTERO' || d.tipo === 'PORCENTAJE') return <CampoTexto key={d.clave} inputMode="numeric" sufijo={d.sufijo} maxLength={5} {...comun} />;
    if (d.tipo === 'LATITUD' || d.tipo === 'LONGITUD') return <CampoTexto key={d.clave} inputMode="decimal" maxLength={13} {...comun} />;
    if (d.tipo === 'RUC') return <CampoTexto key={d.clave} inputMode="numeric" maxLength={11} {...comun} />;
    if (d.clave === 'direccion' || d.clave === 'referencia' || d.clave === 'lema') return <AreaTexto key={d.clave} rows={2} maxLength={d.max} {...comun} />;
    return <CampoTexto key={d.clave} type={d.tipo === 'CORREO' ? 'email' : d.tipo === 'URL' ? 'url' : 'text'} maxLength={d.max} {...comun} />;
  };

  return (
    <form className="grid gap-4" onSubmit={revisar} noValidate>
      <div className="grid gap-4 xl:grid-cols-2 xl:items-start">
        {GRUPOS_CONFIG.map((g) => {
          const I = ICONO_GRUPO[g.id] ?? Settings;
          return (
            <section key={g.id} className="pc">
              <div className="pc-cab">
                <h2>
                  <I className="ico" aria-hidden />
                  {g.titulo}
                </h2>
              </div>
              <div className="pc-cuerpo form-2">{g.claves.map(campo)}</div>
            </section>
          );
        })}
      </div>
      {errorGeneral && <Alerta tipo="error">{errorGeneral}</Alerta>}
      <div className="flex flex-wrap gap-2 justify-end sticky bottom-0 bg-[#F5F7F6] py-3 border-t border-borde-panel">
        <Boton variante="secundario" icono={RotateCcw} disabled={!isDirty} onClick={() => conf.datos && reset(conf.datos)}>
          Deshacer
        </Boton>
        <Boton type="submit" icono={Save} disabled={!isDirty}>
          Guardar cambios
        </Boton>
      </div>
      <ModalConfirmar
        abierto={confirmar !== null}
        alCerrar={() => setConfirmar(null)}
        titulo="Guardar configuración"
        subtitulo={`${confirmar?.length ?? 0} cambios · se aplican al portal y a las reservas nuevas`}
        busto="BU-rosa-agenda"
        textoConfirmar="Guardar"
        iconoConfirmar={Save}
        alConfirmar={guardar}
      >
        <ul className="grid gap-2">
          {(confirmar ?? []).map(([k, a, n]) => (
            <li key={k} className="caja-osc !min-h-0 py-2 flex-wrap">
              <b className="text-[13px]">{etiqueta(k)}</b>
              <span className="text-[#A9B9B1] text-[13px] line-through">{a || '—'}</span>
              <ArrowRight className="ico" aria-hidden />
              <span className="text-[13px] text-[#6FD06A]">{n || getValues(k)}</span>
            </li>
          ))}
        </ul>
      </ModalConfirmar>
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

      {/* QR */}
      <Modal abierto={qr !== null} alCerrar={() => setQr(null)} titulo="Vincular WhatsApp" subtitulo={`Instancia «${e?.instancia ?? 'alcanfores'}»`} busto="BU-rosa-laptop" acciones={<Boton variante="fantasma" onClick={() => setQr(null)}>Cerrar</Boton>}>
        {qr?.cargando ? (
          <div className="grid place-items-center gap-3 py-10">
            <Spinner grande />
            <span className="text-[#A9B9B1]">Pidiendo el QR a Evolution API…</span>
          </div>
        ) : qr?.error ? (
          <div className="grid gap-3">
            <div className="qr-caja !bg-transparent !border-white/10">
              <span className="punto-wa grid place-items-center w-[64px] h-[64px] rounded-[16px] bg-amarillo/15 text-amarillo">
                <TriangleAlert className="ico ico-32" aria-hidden />
              </span>
              <b className="text-white text-[16px] text-center">{qr.error.codigo === 'WHATSAPP_NO_CONFIGURADO' ? 'WhatsApp sin configurar' : 'Evolution API no responde'}</b>
              <span className="text-center text-[#C9D6CF] text-[13.5px]">{qr.error.mensaje}</span>
            </div>
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
            {qr.datos.mensaje && <span className="text-[#C9D6CF] text-[13px]">{qr.datos.mensaje}</span>}
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
