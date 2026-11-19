// Cliente mínimo de Evolution API v2 (la instancia que ya usa notificaciones-service para enviar avisos).
import type { Configuracion } from "../config.js";

export interface EnvioWhatsapp {
  key?: { id?: string; remoteJid?: string; fromMe?: boolean };
}

export interface ClienteWhatsapp {
  configurado(): boolean;
  enviarTexto(numero: string, texto: string): Promise<EnvioWhatsapp>;
  escribiendo(numero: string, ms: number): Promise<void>;
  /** `crudo` = key + message tal como llegaron en el webhook: Evolution descarga con eso sin buscar el mensaje en su BD. */
  descargarMedia(idMensaje: string, crudo?: MensajeCrudo): Promise<{ base64: string; mimetype: string } | null>;
  configurarWebhook(activo: boolean, url: string): Promise<void>;
}

export interface MensajeCrudo {
  key: unknown;
  message: unknown;
}

export class EvolutionHttp implements ClienteWhatsapp {
  constructor(private readonly cfg: Configuracion) {}

  configurado(): boolean {
    return Boolean(this.cfg.evolution.url && this.cfg.evolution.apikey);
  }

  private async llamar<T>(ruta: string, cuerpo: unknown, tiempoMs = 20_000): Promise<T> {
    const e = this.cfg.evolution;
    const res = await fetch(`${e.url}${ruta.replace("{instancia}", encodeURIComponent(e.instancia))}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", apikey: e.apikey },
      body: JSON.stringify(cuerpo),
      signal: AbortSignal.timeout(tiempoMs),
    });
    const texto = await res.text();
    if (!res.ok) throw new Error(`Evolution respondió ${res.status} en ${ruta.split("/")[1]}: ${texto.slice(0, 200)}`);
    return (texto ? JSON.parse(texto) : {}) as T;
  }

  async enviarTexto(numero: string, texto: string): Promise<EnvioWhatsapp> {
    let ultimo: unknown;
    for (let intento = 1; intento <= 3; intento++) {
      try {
        return await this.llamar<EnvioWhatsapp>("/message/sendText/{instancia}", { number: numero, text: texto });
      } catch (e) {
        ultimo = e;
        await new Promise((r) => setTimeout(r, 1_000 * intento));
      }
    }
    throw ultimo;
  }

  async escribiendo(numero: string, ms: number): Promise<void> {
    await this.llamar("/chat/sendPresence/{instancia}", { number: numero, presence: "composing", delay: ms }, 5_000);
  }

  async descargarMedia(idMensaje: string, crudo?: MensajeCrudo): Promise<{ base64: string; mimetype: string } | null> {
    // Con solo el id, Evolution busca el mensaje en su BD y, si la instancia no guarda mensajes, responde
    // 400 «Message not found». Con key + message (lo que trajo el webhook) descarga directo de WhatsApp.
    const r = await this.llamar<{ base64?: string; mimetype?: string }>("/chat/getBase64FromMediaMessage/{instancia}",
      { message: crudo ?? { key: { id: idMensaje } }, convertToMp4: false }, 30_000);
    return r.base64 ? { base64: r.base64, mimetype: r.mimetype ?? "image/jpeg" } : null;
  }

  async configurarWebhook(activo: boolean, url: string): Promise<void> {
    await this.llamar("/webhook/set/{instancia}", {
      // SEND_MESSAGE: ids de lo enviado por la API (avisos y Canchita) para no confundirlos con mensajes del equipo.
      // base64: true → la foto del comprobante llega dentro del webhook (message.base64) y no hay que descargarla.
      webhook: { enabled: activo, url, events: ["MESSAGES_UPSERT", "SEND_MESSAGE"], byEvents: false, base64: true },
    });
  }
}
