import { useState } from 'react';
import { BadgeCheck, CirclePause, Globe, MessageCircle, MessagesSquare, Pause, Play, RefreshCw, Timer, Webhook, type LucideIcon } from 'lucide-react';
import { asistenteAdminApi, CLAVES_CANCHITA, type ConversacionAdmin, type EstadoCanchitaAdmin } from '@/api/asistente';
import { configuracionApi } from '@/api/admin';
import { esErrorApi } from '@/api/cliente';
import { useToast } from '@/contexto/ToastContext';
import { useCarga } from '@/hooks/useCarga';
import { Boton } from '@/componentes/ui/Boton';
import { Interruptor } from '@/componentes/ui/Campo';
import { Cargando, Vacio } from '@/componentes/ui/Datos';
import { Alerta, Insignia } from '@/componentes/ui/Estados';
import { cx } from '@/utilidades/cx';
import { fmtFechaHora, fmtHace, hoyLima } from '@/utilidades/fecha';
import './tarjeta-canchita.css';

/**
 * Configuración › WhatsApp › tarjeta «Canchita» (docs/SPEC_IA.md §4): credencial, modelo, tres interruptores
 * (guardados como cualquier clave de configuración), webhook de WhatsApp, números del día y las últimas 20
 * conversaciones con «Pausar / Reanudar». Solo lectura salvo esos botones: nada de CRM.
 */

type Llave = keyof typeof CLAVES_CANCHITA;
const POR_DEFECTO: Record<Llave, boolean> = { whatsapp: false, web: true, validaPagos: true };

const INTERRUPTORES: Array<{ llave: Llave; titulo: string; texto: string; icono: LucideIcon; encendido: string; apagado: string }> = [
  { llave: 'whatsapp', titulo: 'Responde por WhatsApp', texto: 'Atiende todos los chats del número', icono: MessageCircle, encendido: 'Canchita responde por WhatsApp', apagado: 'Canchita ya no responde por WhatsApp' },
  { llave: 'web', titulo: 'Chat en la web', texto: 'Botón flotante del portal', icono: Globe, encendido: 'Chat de la web encendido', apagado: 'Chat de la web apagado' },
  { llave: 'validaPagos', titulo: 'Valida pagos automáticamente', texto: 'Solo si la foto calza en todo', icono: BadgeCheck, encendido: 'Canchita valida los pagos que calzan', apagado: 'Los pagos quedan para el equipo' },
];

const TIPO_CREDENCIAL: Record<string, string> = {
  TOKEN_SUSCRIPCION: 'Suscripción de Claude',
  API_KEY: 'Clave de API',
  SESION_LOCAL: 'Sesión local de esta PC',
  FALTA: 'Sin credencial: responde un texto fijo',
};

const ESFUERZO: Record<string, string> = { low: 'bajo', medium: 'medio', high: 'alto', xhigh: 'muy alto', max: 'máximo' };

/** Último mensaje en una línea, sin las *negritas* de WhatsApp. */
function resumen(c: ConversacionAdmin): string {
  const m = c.ultimoMensaje;
  if (!m) return 'Sin mensajes';
  const quien = m.rol === 'CANCHITA' ? 'Canchita: ' : m.rol === 'EQUIPO' ? 'Equipo: ' : '';
  const linea = m.texto
    .replace(/\*/g, '')
    .replace(/:?\s*\n\s*[-•]\s+/g, ' · ')
    .replace(/\s+/g, ' ')
    .trim();
  return `${quien}${linea}`;
}

function cuando(iso: string): string {
  if (!iso) return '';
  return iso.startsWith(hoyLima()) ? fmtHace(iso) : fmtFechaHora(iso, { sinAnio: true });
}

function EstadoWebhook({ e, whatsappEncendido }: { e: EstadoCanchitaAdmin['whatsapp']; whatsappEncendido: boolean }) {
  let insignia = <Insignia tono="gris" tamano="sm">Sin revisar</Insignia>;
  let texto = 'Se revisa cada 30 s.';
  if (!e.evolutionConfigurado) {
    insignia = <Insignia tono="gris" tamano="sm">Sin Evolution</Insignia>;
    texto = 'Falta EVOLUTION_URL en asistente-service.';
  } else if (e.webhookActivo) {
    insignia = <Insignia tono="verde" tamano="sm">Activo</Insignia>;
    texto = e.revisadoEn ? `Revisado ${cuando(e.revisadoEn)}` : 'Recibe los mensajes del número.';
  } else if (e.webhookActivo === false) {
    insignia = <Insignia tono={whatsappEncendido ? 'amarillo' : 'gris'} tamano="sm">{whatsappEncendido ? 'Inactivo' : 'Apagado'}</Insignia>;
    texto = whatsappEncendido ? 'Se activará en la próxima revisión.' : 'Enciende «Responde por WhatsApp».';
  }
  return (
    <div className="tc-webhook">
      <span className="tc-ico" aria-hidden>
        <Webhook className="ico ico-18" />
      </span>
      <span className="tc-webhook-txt">
        <b>Webhook de WhatsApp</b>
        <small>{texto}</small>
      </span>
      {insignia}
      {e.ultimoError && (
        <Alerta tipo="error" className="tc-webhook-error">
          {e.ultimoError}
        </Alerta>
      )}
    </div>
  );
}

export function TarjetaCanchita({ className }: { className?: string }) {
  const toast = useToast();
  const [version, setVersion] = useState(0);
  const est = useCarga(() => asistenteAdminApi.estado(), `canchita-${version}`);
  const convs = useCarga(() => asistenteAdminApi.conversaciones(20), `canchita-conv-${version}`);
  const conf = useCarga(() => configuracionApi.obtener(), 'canchita-conf');
  const [guardando, setGuardando] = useState<Llave | null>(null);
  const [cambiando, setCambiando] = useState<number | null>(null);

  const valor = (k: Llave): boolean => {
    const c = conf.datos?.[CLAVES_CANCHITA[k]];
    if (c !== undefined) return c === 'true';
    return est.datos?.interruptores[k] ?? POR_DEFECTO[k];
  };

  const cambiar = async (k: Llave, v: boolean) => {
    const def = INTERRUPTORES.find((i) => i.llave === k);
    const clave = CLAVES_CANCHITA[k];
    const previo = conf.datos;
    setGuardando(k);
    conf.setDatos({ ...(previo ?? {}), [clave]: String(v) });
    try {
      const r = await configuracionApi.guardar({ [clave]: String(v) });
      conf.setDatos(r);
      toast.exito(v ? def?.encendido ?? 'Guardado' : def?.apagado ?? 'Guardado');
      setVersion((x) => x + 1);
    } catch (e) {
      conf.setDatos(previo);
      toast.error(esErrorApi(e) ? e.message : 'No se pudo guardar.');
    } finally {
      setGuardando(null);
    }
  };

  const alternar = async (c: ConversacionAdmin) => {
    setCambiando(c.id);
    try {
      const r = c.estado === 'PAUSADA' ? await asistenteAdminApi.reanudar(c.id) : await asistenteAdminApi.pausar(c.id);
      convs.setDatos((convs.datos ?? []).map((x) => (x.id === r.id ? r : x)));
      toast.exito(r.estado === 'PAUSADA' ? 'Conversación en pausa: responde el equipo' : 'Canchita retoma la conversación');
      setVersion((x) => x + 1);
    } catch {
      /* el aviso de error ya lo muestra el cliente HTTP */
    } finally {
      setCambiando(null);
    }
  };

  const e = est.datos;
  const conectada = e?.credencial.estado === 'CONECTADA';
  const latencia = e?.latenciaPromedioMs ? `${(e.latenciaPromedioMs / 1000).toFixed(1).replace('.', ',')} s` : '—';
  const numeros: Array<{ etiqueta: string; valor: string | number; icono: LucideIcon }> = [
    { etiqueta: 'Activas', valor: e?.conversaciones.activas ?? '—', icono: MessagesSquare },
    { etiqueta: 'Pausadas', valor: e?.conversaciones.pausadas ?? '—', icono: CirclePause },
    { etiqueta: 'Hoy', valor: e?.conversaciones.hoy ?? '—', icono: MessageCircle },
    { etiqueta: 'Responde en', valor: latencia, icono: Timer },
  ];

  return (
    <section className={cx('pc tc', className)} aria-labelledby="tc-titulo">
      <div className="pc-cab">
        <h2 id="tc-titulo">
          <img className="tc-av-mini" src="/canchita/canchita-avatar.webp" alt="" width={22} height={22} />
          Canchita
        </h2>
        <div className="pc-der">
          {e && (
            <Insignia tono={conectada ? 'verde' : 'amarillo'} tamano="sm">
              {conectada ? 'Conectada' : 'Falta credencial'}
            </Insignia>
          )}
          <Boton variante="fantasma" tamano="sm" icono={RefreshCw} etiqueta="Actualizar Canchita" cargando={est.cargando && Boolean(e)} onClick={() => setVersion((v) => v + 1)} />
        </div>
      </div>

      {Boolean(est.error) && !e && (
        <div className="pc-cuerpo">
          <Alerta tipo="advertencia" titulo="Canchita no responde">
            Revisa que asistente-service esté encendido. Los interruptores igual se guardan.
          </Alerta>
        </div>
      )}

      <div className="tc-cuerpo">
        <div className="tc-izq">
          <div className="tc-perfil">
            <img src="/canchita/canchita-avatar.webp" alt="" width={64} height={64} />
            <span className="tc-perfil-txt">
              <b>Asistente de reservas con IA</b>
              {e ? (
                <>
                  <span>{TIPO_CREDENCIAL[e.credencial.tipo] ?? e.credencial.tipo}</span>
                  <span className="tc-modelo">
                    {e.modelo} · esfuerzo {ESFUERZO[e.esfuerzo] ?? e.esfuerzo}
                  </span>
                </>
              ) : (
                <span>{est.cargando ? 'Revisando…' : 'Sin datos del servicio'}</span>
              )}
            </span>
          </div>

          <div className="tc-ints" role="group" aria-label="Interruptores de Canchita">
            {INTERRUPTORES.map(({ llave, titulo, texto, icono: Icono }) => (
              <Interruptor key={llave} activo={valor(llave)} deshabilitado={guardando === llave || (!conf.datos && !e)} alCambiar={(v) => void cambiar(llave, v)}>
                <span className="tc-ico" aria-hidden>
                  <Icono className="ico ico-18" />
                </span>
                <span className="tc-int-txt">
                  <b>{titulo}</b>
                  <small>{texto}</small>
                </span>
              </Interruptor>
            ))}
          </div>

          {e && <EstadoWebhook e={e.whatsapp} whatsappEncendido={valor('whatsapp')} />}
        </div>

        <div className="tc-der">
          <ul className="tc-numeros" aria-label="Números de Canchita">
            {numeros.map(({ etiqueta, valor: v, icono: Icono }) => (
              <li key={etiqueta}>
                <Icono className="ico ico-16" aria-hidden />
                <b className="tnum">{v}</b>
                <span>{etiqueta}</span>
              </li>
            ))}
          </ul>

          <h3 className="tc-subtitulo">Últimas conversaciones</h3>
          {Boolean(convs.error) && !convs.datos ? (
            <Alerta tipo="error">No se pudo leer la lista.</Alerta>
          ) : !convs.datos ? (
            <Cargando className="!py-8" />
          ) : convs.datos.length === 0 ? (
            <Vacio compacto ilustracion="IL-23-todo-al-dia" titulo="Sin conversaciones aún" />
          ) : (
            <ul className="tc-convs">
              {convs.datos.map((c) => {
                const pausada = c.estado === 'PAUSADA';
                return (
                  <li key={c.id} className="tc-conv">
                    <span className={cx('tc-canal', c.canal === 'WHATSAPP' ? 'wa' : 'web')} title={c.canal === 'WHATSAPP' ? 'WhatsApp' : 'Chat de la web'}>
                      {c.canal === 'WHATSAPP' ? <MessageCircle className="ico ico-16" aria-hidden /> : <Globe className="ico ico-16" aria-hidden />}
                      <span className="sr-only">{c.canal === 'WHATSAPP' ? 'WhatsApp' : 'Web'}</span>
                    </span>
                    <div className="tc-conv-txt">
                      <div className="tc-conv-l1">
                        <b>{c.cliente}</b>
                        {c.codigoReserva && <span className="tc-codigo tnum">{c.codigoReserva}</span>}
                        <time className="tc-cuando" dateTime={c.ultimaActividad}>
                          {cuando(c.ultimaActividad)}
                        </time>
                      </div>
                      <p className="tc-ultimo">{resumen(c)}</p>
                    </div>
                    <div className="tc-conv-acc">
                      <Insignia tono={pausada ? 'amarillo' : 'menta'} tamano="sm">
                        {pausada ? (c.pausadaHasta ? `Pausada hasta ${fmtFechaHora(c.pausadaHasta, { sinAnio: true }).split(', ')[1] ?? ''}` : 'Pausada') : 'Activa'}
                      </Insignia>
                      <Boton variante="secundario" tamano="sm" icono={pausada ? Play : Pause} cargando={cambiando === c.id} disabled={cambiando !== null && cambiando !== c.id} onClick={() => void alternar(c)}>
                        {pausada ? 'Reanudar' : 'Pausar'}
                      </Boton>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </section>
  );
}
