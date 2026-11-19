import { createHash } from "node:crypto";

// Configuración de Canchita por variables de entorno, con valores por defecto para trabajar en local
// (los mismos que usan los servicios Java: gateway 8086, catálogo 8092, pagos 8094, token interno de desarrollo).

function texto(nombre: string, porDefecto = ""): string {
  const v = process.env[nombre];
  return v === undefined || v.trim() === "" ? porDefecto : v.trim();
}

function numero(nombre: string, porDefecto: number): number {
  const v = Number(process.env[nombre]);
  return Number.isFinite(v) && v > 0 ? v : porDefecto;
}

function booleano(nombre: string, porDefecto: boolean): boolean {
  const v = process.env[nombre]?.trim().toLowerCase();
  if (v === undefined || v === "") return porDefecto;
  return v === "true" || v === "1" || v === "si" || v === "sí";
}

export type Esfuerzo = "low" | "medium" | "high" | "xhigh" | "max";

export interface Configuracion {
  puerto: number;
  bd: { host: string; puerto: number; usuario: string; clave: string; nombre: string };
  /** API pública (gateway): las herramientas la usan como lo haría el cliente desde el portal. */
  apiUrl: string;
  /** Servicios internos (sin pasar por el gateway). */
  catalogoUrl: string;
  reservasUrl: string;
  pagosUrl: string;
  internoToken: string;
  frontUrl: string;
  jwtSecreto: string;
  ia: {
    modelo: string;
    esfuerzo: Esfuerzo;
    /** Permite usar la sesión de Claude Code de la PC (solo pruebas locales) cuando no hay token ni API key. */
    usarSesionLocal: boolean;
    historial: number;
    maxTurnos: number;
    tiempoMaximoMs: number;
  };
  evolution: { url: string; apikey: string; instancia: string };
  webhookToken: string;
  /** URL con la que Evolution alcanza a este servicio dentro de la red de Docker. */
  urlInterna: string;
  pausaEquipoHoras: number;
  whatsappHumano: string;
}

export function leerConfiguracion(): Configuracion {
  const esfuerzo = texto("IA_ESFUERZO", "low") as Esfuerzo;
  return {
    puerto: numero("PORT", 8096),
    bd: {
      host: texto("DB_HOST", "localhost"),
      puerto: numero("DB_PORT", 3306),
      usuario: texto("DB_USUARIO", "root"),
      clave: process.env.DB_CLAVE ?? "",
      nombre: texto("DB_NOMBRE", "alcanfores_asistente"),
    },
    apiUrl: texto("API_URL", "http://localhost:8086").replace(/\/$/, ""),
    catalogoUrl: texto("CATALOGO_URL", "http://localhost:8092").replace(/\/$/, ""),
    reservasUrl: texto("RESERVAS_URL", "http://localhost:8093").replace(/\/$/, ""),
    pagosUrl: texto("PAGOS_URL", "http://localhost:8094").replace(/\/$/, ""),
    internoToken: texto("INTERNO_TOKEN", "token-interno-solo-para-desarrollo-local"),
    frontUrl: texto("FRONT_URL", "http://localhost:5186").split(",")[0].trim().replace(/\/$/, ""),
    jwtSecreto: texto("JWT_SECRETO", "secreto-de-desarrollo-local-alcanfores-2026-cambiar-en-produccion"),
    ia: {
      modelo: texto("IA_MODELO", "claude-opus-5-5"),
      esfuerzo: ["low", "medium", "high", "xhigh", "max"].includes(esfuerzo) ? esfuerzo : "low",
      usarSesionLocal: booleano("IA_USAR_SESION_LOCAL", false),
      historial: numero("IA_HISTORIAL", 20),
      maxTurnos: numero("IA_MAX_TURNOS", 10),
      tiempoMaximoMs: numero("IA_TIEMPO_MAXIMO_MS", 60_000),
    },
    evolution: {
      url: texto("EVOLUTION_URL").replace(/\/$/, ""),
      apikey: texto("EVOLUTION_APIKEY"),
      instancia: texto("EVOLUTION_INSTANCIA", "alcanfores"),
    },
    // Sin ASISTENTE_WEBHOOK_TOKEN se deriva del token interno (el .env del servidor no necesita otra clave).
    webhookToken: texto("ASISTENTE_WEBHOOK_TOKEN",
      createHash("sha256").update(`canchita-webhook:${texto("INTERNO_TOKEN", "token-interno-solo-para-desarrollo-local")}`).digest("hex").slice(0, 40)),
    urlInterna: texto("ASISTENTE_URL_INTERNA", "http://alcanfores-asistente:8096").replace(/\/$/, ""),
    pausaEquipoHoras: numero("IA_PAUSA_EQUIPO_HORAS", 2),
    whatsappHumano: texto("WHATSAPP_HUMANO", "914995187"),
  };
}

export type TipoCredencial = "TOKEN_SUSCRIPCION" | "API_KEY" | "SESION_LOCAL" | "FALTA";

/** Qué credencial usará el SDK de agentes. Nunca devuelve ni registra el valor del token. */
export function tipoCredencial(cfg: Configuracion): TipoCredencial {
  if (texto("CLAUDE_CODE_OAUTH_TOKEN")) return "TOKEN_SUSCRIPCION";
  if (texto("ANTHROPIC_API_KEY")) return "API_KEY";
  return cfg.ia.usarSesionLocal ? "SESION_LOCAL" : "FALTA";
}
