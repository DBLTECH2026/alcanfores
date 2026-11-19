// Mantiene el webhook de Evolution alineado con el interruptor «Responde por WhatsApp» (ia_whatsapp_activa):
// encendido → registra el webhook MESSAGES_UPSERT en la instancia; apagado → lo desactiva.
import type { Ajustes } from "../ajustes.js";
import type { Configuracion } from "../config.js";
import type { ClienteWhatsapp } from "./evolution.js";

export interface EstadoWebhook {
  evolutionConfigurado: boolean;
  /** null = todavía no se sincronizó. */
  webhookActivo: boolean | null;
  ultimoError: string | null;
  revisadoEn: string | null;
}

export class SincronizadorWebhook {
  private activo: boolean | null = null;
  private ultimoError: string | null = null;
  private revisadoEn: string | null = null;
  private temporizador: NodeJS.Timeout | null = null;

  constructor(
    private readonly cfg: Configuracion,
    private readonly ajustes: Ajustes,
    private readonly wa: ClienteWhatsapp,
  ) {}

  url(): string {
    return `${this.cfg.urlInterna}/webhook/evolution/${encodeURIComponent(this.cfg.webhookToken)}`;
  }

  async sincronizar(forzar = false): Promise<EstadoWebhook> {
    if (this.wa.configurado()) {
      const deseado = (await this.ajustes.interruptores(forzar)).whatsapp;
      if (forzar || deseado !== this.activo) {
        try {
          await this.wa.configurarWebhook(deseado, this.url());
          this.activo = deseado;
          this.ultimoError = null;
        } catch (e) {
          this.ultimoError = e instanceof Error ? e.message.slice(0, 200) : "Error desconocido";
        }
      }
      this.revisadoEn = new Date().toISOString();
    }
    return this.estado();
  }

  estado(): EstadoWebhook {
    return {
      evolutionConfigurado: this.wa.configurado(), webhookActivo: this.activo, ultimoError: this.ultimoError, revisadoEn: this.revisadoEn,
    };
  }

  iniciar(cadaMs = 30_000): void {
    void this.sincronizar(true);
    this.temporizador = setInterval(() => void this.sincronizar(), cadaMs);
    this.temporizador.unref();
  }

  detener(): void {
    if (this.temporizador) clearInterval(this.temporizador);
  }
}
