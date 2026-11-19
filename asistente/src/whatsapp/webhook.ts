// Webhook de Evolution: decide qué hacer con cada mensaje y, si toca, responde por WhatsApp.
// - Grupos, estados, canales y reacciones: se ignoran.
// - Evolution 2.3.x crea Baileys con emitOwnEvents: false: lo que se ENVÍA por la API (avisos de notificaciones-service
//   y respuestas de Canchita) NO llega como MESSAGES_UPSERT, solo como SEND_MESSAGE. Lo que el equipo escribe desde el
//   celular, WhatsApp Web o Desktop SÍ llega como MESSAGES_UPSERT con fromMe. El campo «source» no sirve para separarlos
//   (la API y WhatsApp Web generan ids 3EB0… y ambos salen «web»).
// - Regla: un fromMe en MESSAGES_UPSERT es del EQUIPO (se guarda y la conversación se pausa N horas), salvo que su id
//   esté registrado como enviado por la API (SEND_MESSAGE o respuesta de sendText de Canchita), por si una versión de
//   Evolution emitiera los propios.
import type { Ajustes } from "../ajustes.js";
import type { Canchita, ImagenEntrante } from "../canchita.js";
import type { Configuracion } from "../config.js";
import { leerImagenBase64 } from "../imagenes.js";
import type { Repositorio } from "../modelo.js";
import { ahora } from "../tiempo.js";
import type { ClienteWhatsapp } from "./evolution.js";

export interface MensajeEntrante {
  tipo: "CLIENTE" | "EQUIPO";
  clave: string;
  destino: string;
  idMensaje: string;
  nombre: string | null;
  texto: string;
  imagen: boolean;
}

export type EventoEntrante =
  | { tipo: "IGNORAR"; motivo: string }
  | { tipo: "ENVIADO_POR_API"; idMensaje: string }
  | MensajeEntrante;

type Dato = {
  key?: { remoteJid?: string; remoteJidAlt?: string; senderPn?: string; fromMe?: boolean; id?: string };
  pushName?: string;
  message?: Record<string, Record<string, unknown> | string | undefined>;
  messageType?: string;
  source?: string;
};

function textoDe(msg: Dato["message"]): { texto: string; imagen: boolean; ignorar: boolean } {
  if (!msg) return { texto: "", imagen: false, ignorar: true };
  const m = msg as Record<string, Record<string, unknown> | string | undefined>;
  const campo = (k: string, sub: string) => (typeof m[k] === "object" ? String((m[k] as Record<string, unknown>)[sub] ?? "") : "");
  if (typeof m.conversation === "string") return { texto: m.conversation, imagen: false, ignorar: false };
  if (m.extendedTextMessage) return { texto: campo("extendedTextMessage", "text"), imagen: false, ignorar: false };
  if (m.imageMessage) return { texto: campo("imageMessage", "caption"), imagen: true, ignorar: false };
  if (m.buttonsResponseMessage) return { texto: campo("buttonsResponseMessage", "selectedDisplayText"), imagen: false, ignorar: false };
  if (m.listResponseMessage) return { texto: campo("listResponseMessage", "title"), imagen: false, ignorar: false };
  if (m.audioMessage) return { texto: "(envió una nota de voz; Canchita solo lee texto y fotos)", imagen: false, ignorar: false };
  if (m.documentMessage) return { texto: "(envió un documento; pídele una captura o foto del comprobante)", imagen: false, ignorar: false };
  if (m.videoMessage) return { texto: "(envió un video)", imagen: false, ignorar: false };
  if (m.locationMessage) return { texto: "(envió una ubicación)", imagen: false, ignorar: false };
  return { texto: "", imagen: false, ignorar: true };
}

/** Clave del chat (51XXXXXXXXX) y destino para responder. Soporta direcciones @lid con su número alterno. */
function direccion(key: NonNullable<Dato["key"]>): { clave: string; destino: string } | null {
  const candidatos = [key.remoteJid, key.remoteJidAlt, key.senderPn].filter((j): j is string => Boolean(j));
  const telefono = candidatos.find((j) => j.endsWith("@s.whatsapp.net"));
  if (telefono) {
    const numero = telefono.split("@")[0].split(":")[0];
    return { clave: numero, destino: numero };
  }
  const lid = candidatos.find((j) => j.endsWith("@lid"));
  return lid ? { clave: lid, destino: lid } : null;
}

export function interpretarEvento(cuerpo: unknown): EventoEntrante[] {
  const c = (cuerpo ?? {}) as { event?: string; data?: Dato | Dato[] };
  const evento = String(c.event ?? "").toLowerCase().replace(/_/g, ".");
  const datos = Array.isArray(c.data) ? c.data : c.data ? [c.data] : [];
  if (evento === "send.message") {
    return datos.map((d): EventoEntrante => (d.key?.id ? { tipo: "ENVIADO_POR_API", idMensaje: d.key.id } : { tipo: "IGNORAR", motivo: "sin id" }));
  }
  if (evento !== "messages.upsert") return [{ tipo: "IGNORAR", motivo: `evento ${c.event ?? "desconocido"}` }];
  return datos.map((d): EventoEntrante => {
    const key = d.key ?? {};
    const jid = key.remoteJid ?? "";
    if (!jid || jid.endsWith("@g.us") || jid === "status@broadcast" || jid.endsWith("@newsletter") || jid.endsWith("@broadcast")) {
      return { tipo: "IGNORAR", motivo: "grupo, estado o canal" };
    }
    if (!key.id) return { tipo: "IGNORAR", motivo: "sin id" };
    const contenido = textoDe(d.message);
    if (contenido.ignorar) return { tipo: "IGNORAR", motivo: "tipo de mensaje sin texto" };
    const dir = direccion(key);
    if (!dir) return { tipo: "IGNORAR", motivo: "remitente sin número" };
    if (key.fromMe) {
      return { tipo: "EQUIPO", ...dir, idMensaje: key.id, nombre: null, texto: contenido.texto, imagen: contenido.imagen };
    }
    return { tipo: "CLIENTE", ...dir, idMensaje: key.id, nombre: d.pushName?.trim() || null, texto: contenido.texto, imagen: contenido.imagen };
  });
}

export class ProcesadorWhatsapp {
  /** Ids enviados por la API (Canchita y notificaciones-service) en los últimos minutos; la BD respalda los de Canchita. */
  private readonly enviados = new Set<string>();

  constructor(
    private readonly cfg: Configuracion,
    private readonly repo: Repositorio,
    private readonly ajustes: Ajustes,
    private readonly canchita: Canchita,
    private readonly wa: ClienteWhatsapp,
    private readonly registro: (nivel: "info" | "warn" | "error", mensaje: string) => void = () => {},
  ) {}

  private recordarEnvio(id: string): void {
    this.enviados.add(id);
    if (this.enviados.size > 2000) this.enviados.delete(this.enviados.values().next().value!);
  }

  async procesar(cuerpo: unknown): Promise<void> {
    for (const ev of interpretarEvento(cuerpo)) {
      if (ev.tipo === "IGNORAR") continue;
      if (ev.tipo === "ENVIADO_POR_API") {
        this.recordarEnvio(ev.idMensaje);
        continue;
      }
      if (this.enviados.has(ev.idMensaje) || await this.repo.existeMensajeExterno(ev.idMensaje)) continue;
      try {
        if (ev.tipo === "EQUIPO") await this.mensajeDelEquipo(ev);
        else await this.mensajeDelCliente(ev);
      } catch (e) {
        this.registro("error", `WhatsApp: no se pudo procesar el mensaje ${ev.idMensaje}: ${e instanceof Error ? e.message : String(e)}`);
      }
    }
  }

  private async mensajeDelEquipo(ev: MensajeEntrante): Promise<void> {
    const c = await this.repo.obtenerOCrearConversacion("WHATSAPP", ev.clave);
    await this.repo.agregarMensaje({
      idConversacion: c.id, rol: "EQUIPO", texto: ev.texto || "(envió un archivo)", idExterno: ev.idMensaje,
    });
    // Lo que el cliente escribió antes ya lo atendió la persona del equipo.
    await this.repo.marcarAtendidos((await this.repo.pendientes(c.id)).map((m) => m.id));
    const indefinida = c.estado === "PAUSADA" && c.pausadaHasta === null;
    if (!indefinida) {
      await this.repo.actualizarConversacion(c.id, {
        estado: "PAUSADA", pausadaHasta: new Date(ahora().getTime() + this.cfg.pausaEquipoHoras * 3_600_000),
      });
    }
    this.registro("info", `WhatsApp: el equipo escribió en ${ev.clave}; Canchita en pausa.`);
  }

  private async mensajeDelCliente(ev: MensajeEntrante): Promise<void> {
    const interruptores = await this.ajustes.interruptores();
    if (!interruptores.whatsapp) return;
    const c = await this.repo.obtenerOCrearConversacion("WHATSAPP", ev.clave, ev.nombre);

    let imagen: ImagenEntrante | undefined;
    let texto = ev.texto;
    if (ev.imagen) {
      try {
        const media = await this.wa.descargarMedia(ev.idMensaje);
        const leida = media ? leerImagenBase64(media.base64) : { error: "sin datos" };
        if ("error" in leida) texto = `${texto} (envió una imagen que no se pudo abrir)`.trim();
        else imagen = leida;
      } catch (e) {
        this.registro("warn", `WhatsApp: no se pudo descargar la foto ${ev.idMensaje}: ${e instanceof Error ? e.message : String(e)}`);
        texto = `${texto} (envió una foto que no se pudo descargar; pídele que la mande de nuevo)`.trim();
      }
    }
    await this.canchita.guardarEntrada(c, texto, imagen, ev.idMensaje);

    const resultado = await this.canchita.responder(c.id, {
      escribiendo: () => void this.wa.escribiendo(ev.destino, 12_000).catch(() => undefined),
    });
    for (const [i, m] of resultado.mensajes.entries()) {
      if (i > 0) await new Promise((r) => setTimeout(r, 800));
      const envio = await this.wa.enviarTexto(ev.destino, m.texto);
      const id = envio.key?.id;
      if (id) {
        this.recordarEnvio(id);
        await this.repo.anotarIdExterno(resultado.idsGuardados[i], id).catch(() => undefined);
      }
    }
  }
}
