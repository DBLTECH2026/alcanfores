import { ErrorApi, http, MENSAJE_SIN_CONEXION } from '@/api/cliente';
import type { CuerpoError } from '@/api/tipos';

/**
 * Canchita, la IA del complejo (asistente-service, docs/servicios/asistente-service.md).
 *  - Portal: estado del chat web, historial por sesión y envío de mensajes con respuesta en streaming (SSE).
 *  - Panel: estado de la credencial, interruptores, webhook de WhatsApp, números del día y últimas conversaciones.
 * Los interruptores se guardan como cualquier clave de configuración (`configuracionApi.guardar`).
 */

// ------------------------------ tipos ------------------------------
export interface EstadoChatWeb {
  activa: boolean;
  nombre: string;
  avatar: string;
}

export interface EnlaceCanchita {
  url: string;
  texto: string;
}

/** Un mensaje que devuelve Canchita (SSE `mensaje` o JSON `mensajes[]`). */
export interface MensajeCanchita {
  texto: string;
  opciones?: string[];
  enlace?: EnlaceCanchita;
}

export type RolMensaje = 'CLIENTE' | 'CANCHITA' | 'EQUIPO';

export interface MensajeHistorial {
  id: number;
  rol: RolMensaje;
  texto: string;
  /** El mensaje llevaba la foto de un comprobante (no se devuelve la imagen). */
  imagen: boolean;
  opciones?: string[];
  enlace?: EnlaceCanchita;
  creadoEn: string;
}

export interface HistorialChat {
  sesion: string;
  codigoReserva?: string | null;
  mensajes: MensajeHistorial[];
}

export interface SolicitudMensaje {
  sesion: string;
  texto?: string;
  /** data URL o base64 de una JPG/PNG (≤ 5 MB en el back; el front la comprime a ≤ 1,5 MB). */
  imagen?: string;
}

/** Lo que va pasando mientras Canchita responde. */
export interface EventosChat {
  escribiendo?: () => void;
  herramienta?: (texto: string, nombre: string) => void;
  mensaje?: (m: MensajeCanchita) => void;
}

export interface EstadoCanchitaAdmin {
  credencial: { estado: 'CONECTADA' | 'FALTA'; tipo: 'TOKEN_SUSCRIPCION' | 'API_KEY' | 'SESION_LOCAL' | 'FALTA' | string };
  modelo: string;
  esfuerzo: string;
  interruptores: { whatsapp: boolean; web: boolean; validaPagos: boolean };
  whatsapp: { evolutionConfigurado: boolean; webhookActivo: boolean | null; ultimoError: string | null; revisadoEn: string | null };
  conversaciones: { activas: number; pausadas: number; hoy: number };
  latenciaPromedioMs: number | null;
}

export interface ConversacionAdmin {
  id: number;
  canal: 'WHATSAPP' | 'WEB';
  cliente: string;
  clave: string;
  estado: 'ACTIVA' | 'PAUSADA';
  pausadaHasta: string | null;
  codigoReserva: string | null;
  ultimaActividad: string;
  ultimoMensaje: { rol: RolMensaje; texto: string; creadoEn: string } | null;
}

/** Claves de configuración (catálogo V6) de los tres interruptores del panel. */
export const CLAVES_CANCHITA = {
  whatsapp: 'ia_whatsapp_activa',
  web: 'ia_web_activa',
  validaPagos: 'ia_valida_pagos',
} as const;

// ------------------------------ sesión del chat web ------------------------------
const CLAVE_SESION = 'alc-canchita-sesion';
const PATRON_SESION = /^[A-Za-z0-9_-]{16,64}$/;

/** Id aleatorio de 32 caracteres que identifica la conversación web de este navegador (se guarda en localStorage). */
export function sesionChat(): string {
  try {
    const guardada = localStorage.getItem(CLAVE_SESION);
    if (guardada && PATRON_SESION.test(guardada)) return guardada;
  } catch {
    /* modo privado sin localStorage: sesión solo en memoria */
  }
  const letras = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  const azar = new Uint8Array(32);
  crypto.getRandomValues(azar);
  const nueva = Array.from(azar, (b) => letras[b % letras.length]).join('');
  try {
    localStorage.setItem(CLAVE_SESION, nueva);
  } catch {
    /* sin localStorage */
  }
  return nueva;
}

// ------------------------------ portal ------------------------------
const S = { silenciarToast: true } as const;
const BASE = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

/** Una sola consulta del estado por carga de la página (lo comparten los dos marcos del portal). */
let estadoWeb: Promise<EstadoChatWeb> | null = null;

export const asistenteApi = {
  estadoWeb(): Promise<EstadoChatWeb> {
    if (!estadoWeb) {
      estadoWeb = http.get<EstadoChatWeb>('/asistente/web/estado', S).then((r) => r.data);
      estadoWeb.catch(() => {
        estadoWeb = null;
      });
    }
    return estadoWeb;
  },

  historial: (sesion: string) => http.get<HistorialChat>('/asistente/web/historial', { params: { sesion }, ...S }).then((r) => r.data),

  /**
   * Envía un mensaje y va avisando lo que llega por SSE (`escribiendo`, `herramienta`, `mensaje`, `fin`, `error`).
   * Si la respuesta llega como JSON (proxy sin streaming), entrega sus mensajes igual. Lanza `ErrorApi` si falla.
   */
  async enviar(datos: SolicitudMensaje, eventos: EventosChat, senal?: AbortSignal): Promise<{ latenciaMs: number | null }> {
    let r: Response;
    try {
      r = await fetch(`${BASE}/asistente/web/mensaje`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream, application/json', 'X-Request-Id': idPeticion() },
        body: JSON.stringify(datos),
        signal: senal,
      });
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') throw e;
      throw new ErrorApi({ status: 0, codigo: 'SIN_CONEXION', mensaje: MENSAJE_SIN_CONEXION });
    }
    if (!r.ok) throw await errorDe(r);

    const tipo = r.headers.get('content-type') ?? '';
    if (!tipo.includes('text/event-stream') || !r.body) {
      const cuerpo = (await r.json()) as { mensajes?: MensajeCanchita[]; latenciaMs?: number };
      for (const m of cuerpo.mensajes ?? []) eventos.mensaje?.(m);
      return { latenciaMs: cuerpo.latenciaMs ?? null };
    }

    const lector = r.body.getReader();
    const decodificador = new TextDecoder();
    let pendiente = '';
    let latenciaMs: number | null = null;
    let terminado = false;
    const procesar = (bloque: string) => {
      let evento = 'message';
      const datosEv: string[] = [];
      for (const linea of bloque.split(/\r?\n/)) {
        if (!linea || linea.startsWith(':')) continue;
        const i = linea.indexOf(':');
        const campo = i < 0 ? linea : linea.slice(0, i);
        const valor = i < 0 ? '' : linea.slice(i + 1).replace(/^ /, '');
        if (campo === 'event') evento = valor;
        else if (campo === 'data') datosEv.push(valor);
      }
      if (!datosEv.length && evento === 'message') return;
      let carga: Record<string, unknown> = {};
      try {
        carga = datosEv.length ? (JSON.parse(datosEv.join('\n')) as Record<string, unknown>) : {};
      } catch {
        return;
      }
      if (evento === 'escribiendo') eventos.escribiendo?.();
      else if (evento === 'herramienta') eventos.herramienta?.(String(carga.texto ?? ''), String(carga.nombre ?? ''));
      else if (evento === 'mensaje') eventos.mensaje?.(carga as unknown as MensajeCanchita);
      else if (evento === 'fin') {
        terminado = true;
        latenciaMs = typeof carga.latenciaMs === 'number' ? carga.latenciaMs : null;
      } else if (evento === 'error') {
        throw new ErrorApi({ status: 200, codigo: 'CANCHITA_ERROR', mensaje: String(carga.mensaje ?? 'No pude responder ahora. Intenta en unos segundos.') });
      }
    };
    try {
      for (;;) {
        const { value, done } = await lector.read();
        if (value) pendiente += decodificador.decode(value, { stream: true });
        let corte = pendiente.search(/\r?\n\r?\n/);
        while (corte >= 0) {
          const bloque = pendiente.slice(0, corte);
          pendiente = pendiente.slice(pendiente[corte] === '\r' ? corte + 4 : corte + 2);
          procesar(bloque);
          corte = pendiente.search(/\r?\n\r?\n/);
        }
        if (done) break;
      }
      if (pendiente.trim()) procesar(pendiente);
    } catch (e) {
      if (e instanceof ErrorApi || (e instanceof DOMException && e.name === 'AbortError')) throw e;
      throw new ErrorApi({ status: 0, codigo: 'SIN_CONEXION', mensaje: MENSAJE_SIN_CONEXION });
    } finally {
      lector.releaseLock();
    }
    if (!terminado) throw new ErrorApi({ status: 0, codigo: 'CORTADO', mensaje: 'Se cortó la conexión. Reintenta.' });
    return { latenciaMs };
  },
};

function idPeticion(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`;
}

/** Cuerpo estándar de error del sistema → ErrorApi (429 del gateway y 503 con mensaje propio). */
async function errorDe(r: Response): Promise<ErrorApi> {
  let cuerpo: Partial<CuerpoError> | null = null;
  try {
    cuerpo = (await r.json()) as Partial<CuerpoError>;
  } catch {
    cuerpo = null;
  }
  if (cuerpo && typeof cuerpo.mensaje === 'string') {
    return new ErrorApi({ status: r.status, codigo: cuerpo.codigo ?? 'ERROR', mensaje: cuerpo.mensaje, errores: cuerpo.errores ?? [] });
  }
  if (r.status === 429) return new ErrorApi({ status: 429, codigo: 'DEMASIADAS_SOLICITUDES', mensaje: 'Vas muy rápido. Espera unos segundos y vuelve a intentar.' });
  if (r.status >= 502) return new ErrorApi({ status: r.status, codigo: 'SERVICIO_NO_DISPONIBLE', mensaje: 'Canchita no responde ahora. Intenta en unos segundos.' });
  return new ErrorApi({ status: r.status, codigo: 'ERROR', mensaje: 'Algo falló. Intenta en unos minutos.' });
}

// ------------------------------ panel ------------------------------
export const asistenteAdminApi = {
  estado: () => http.get<EstadoCanchitaAdmin>('/asistente/admin/estado', S).then((r) => r.data),
  conversaciones: (limite = 20) => http.get<{ contenido: ConversacionAdmin[] }>('/asistente/admin/conversaciones', { params: { limite }, ...S }).then((r) => r.data.contenido),
  pausar: (id: number, horas?: number) => http.post<ConversacionAdmin>(`/asistente/admin/conversaciones/${id}/pausar`, horas ? { horas } : {}).then((r) => r.data),
  reanudar: (id: number) => http.post<ConversacionAdmin>(`/asistente/admin/conversaciones/${id}/reanudar`, {}).then((r) => r.data),
};
