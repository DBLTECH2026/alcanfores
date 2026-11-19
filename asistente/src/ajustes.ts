// Interruptores de Canchita (claves de configuración del catálogo) y datos públicos del complejo, con caché corta.
// Si las claves nuevas aún no existen en el catálogo se usan los valores por defecto del contrato.
import type { ApiAlcanfores, Complejo } from "./api.js";

export interface Interruptores {
  /** ia_whatsapp_activa (por defecto false). */
  whatsapp: boolean;
  /** ia_web_activa (por defecto true). */
  web: boolean;
  /** ia_valida_pagos (por defecto true). */
  validaPagos: boolean;
}

export const INTERRUPTORES_POR_DEFECTO: Interruptores = { whatsapp: false, web: true, validaPagos: true };

function leerBooleano(valor: string | undefined, porDefecto: boolean): boolean {
  if (valor === undefined || valor === null || String(valor).trim() === "") return porDefecto;
  return String(valor).trim().toLowerCase() === "true";
}

export function interruptoresDesde(cfg: Record<string, string>): Interruptores {
  return {
    whatsapp: leerBooleano(cfg.ia_whatsapp_activa, INTERRUPTORES_POR_DEFECTO.whatsapp),
    web: leerBooleano(cfg.ia_web_activa, INTERRUPTORES_POR_DEFECTO.web),
    validaPagos: leerBooleano(cfg.ia_valida_pagos, INTERRUPTORES_POR_DEFECTO.validaPagos),
  };
}

export class Ajustes {
  private cacheInterruptores: { valor: Interruptores; en: number } | null = null;
  private cacheComplejo: { valor: Complejo; en: number } | null = null;

  constructor(
    private readonly api: ApiAlcanfores,
    private readonly ttlMs = 30_000,
  ) {}

  async interruptores(forzar = false): Promise<Interruptores> {
    const c = this.cacheInterruptores;
    if (!forzar && c && Date.now() - c.en < this.ttlMs) return c.valor;
    try {
      const valor = interruptoresDesde(await this.api.configuracionInterna());
      this.cacheInterruptores = { valor, en: Date.now() };
      return valor;
    } catch {
      // Catálogo caído: se mantiene lo último conocido (o los valores por defecto).
      return c?.valor ?? INTERRUPTORES_POR_DEFECTO;
    }
  }

  async complejo(): Promise<Complejo> {
    const c = this.cacheComplejo;
    if (c && Date.now() - c.en < this.ttlMs) return c.valor;
    try {
      const valor = await this.api.complejo();
      this.cacheComplejo = { valor, en: Date.now() };
      return valor;
    } catch (e) {
      if (c) return c.valor;
      throw e;
    }
  }

  /** Olvida la caché (tras cambiar un interruptor desde el panel). */
  olvidar(): void {
    this.cacheInterruptores = null;
    this.cacheComplejo = null;
  }
}
