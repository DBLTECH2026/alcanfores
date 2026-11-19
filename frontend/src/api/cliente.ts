import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import type { CuerpoError, ErrorCampo } from './tipos';

/**
 * Cliente HTTP único. Interceptores:
 *  - petición: `Authorization: Bearer <token>` (si hay sesión) y `X-Request-Id` (UUID por petición).
 *  - respuesta: normaliza cualquier error a `ErrorApi` (cuerpo estándar del SPEC §0), muestra un toast con el
 *    `mensaje` del back (salvo `silenciarToast`) y ante 401 en rutas con token cierra la sesión.
 */

declare module 'axios' {
  interface AxiosRequestConfig {
    /** No mostrar el toast automático de error (la pantalla lo maneja: errores por campo, 409, DNI…). */
    silenciarToast?: boolean;
    /** Qué sesión puso el token (lo llena el interceptor). */
    tipoToken?: 'panel' | 'cliente' | null;
    /** Reintentos ya hechos de este GET (503 / sin red). */
    reintentos?: number;
  }
}

export class ErrorApi extends Error {
  readonly status: number;
  readonly codigo: string;
  readonly errores: ErrorCampo[];
  constructor(cuerpo: CuerpoError) {
    super(cuerpo.mensaje);
    this.name = 'ErrorApi';
    this.status = cuerpo.status;
    this.codigo = cuerpo.codigo;
    this.errores = cuerpo.errores ?? [];
  }
  /** Errores por campo como objeto { 'cliente.dni': 'El DNI debe tener 8 dígitos.' } */
  porCampo(): Record<string, string> {
    return Object.fromEntries(this.errores.map((e) => [e.campo, e.mensaje]));
  }
}

export const MENSAJE_SIN_CONEXION = 'Sin conexión con el servidor. Reintenta.';
export const MENSAJE_GENERICO = 'Algo falló. Intenta en unos minutos.';

// ------------------------------ puentes con los contextos ------------------------------
type Notificar = (mensaje: string) => void;
let notificarError: Notificar | null = null;
let alExpirarSesion: (() => void) | null = null;
let leerToken: () => string | null = () => null;
let alExpirarCliente: (() => void) | null = null;
let leerTokenCliente: () => string | null = () => null;

export function registrarNotificador(fn: Notificar | null) {
  notificarError = fn;
}
export function registrarSesion(opciones: { token: () => string | null; alExpirar: () => void } | null) {
  leerToken = opciones?.token ?? (() => null);
  alExpirarSesion = opciones?.alExpirar ?? null;
}
/** Sesión del CLIENTE del portal (v2): su token solo va a sus rutas, nunca al panel. */
export function registrarSesionCliente(opciones: { token: () => string | null; alExpirar: () => void } | null) {
  leerTokenCliente = opciones?.token ?? (() => null);
  alExpirarCliente = opciones?.alExpirar ?? null;
}

/** Rutas que usan el token del cliente: su cuenta, sus reservas, reservar y reseñar. */
function rutaDeCliente(url: string, metodo: string): boolean {
  if (/^\/(auth\/clientes|cliente)(\/|$)/.test(url)) return true;
  if (metodo !== 'post') return false;
  return /^\/reservas\/?$/.test(url) || /^\/catalogo\/canchas\/[^/]+\/resenas\/?$/.test(url);
}

function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`;
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  timeout: 20000,
  headers: { Accept: 'application/json' },
});

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const url = String(config.url ?? '');
  const deCliente = rutaDeCliente(url, String(config.method ?? 'get').toLowerCase());
  const esLogin = /\/login$|\/registro$/.test(url);
  const token = esLogin ? null : deCliente ? leerTokenCliente() : leerToken();
  config.tipoToken = token ? (deCliente ? 'cliente' : 'panel') : null;
  if (token) config.headers.set('Authorization', `Bearer ${token}`);
  config.headers.set('X-Request-Id', uuid());
  return config;
});

function aCuerpo(error: AxiosError): CuerpoError {
  const datos = error.response?.data as Partial<CuerpoError> | undefined;
  if (error.response) {
    const status = error.response.status;
    if (datos && typeof datos === 'object' && typeof datos.mensaje === 'string') {
      return { status, codigo: datos.codigo ?? 'ERROR', mensaje: datos.mensaje, errores: datos.errores ?? [], ruta: datos.ruta };
    }
    if (status === 503 || status === 502 || status === 504) return { status, codigo: 'SERVICIO_NO_DISPONIBLE', mensaje: 'El servicio no responde. Intenta en unos segundos.' };
    return { status, codigo: 'ERROR', mensaje: MENSAJE_GENERICO };
  }
  if (error.code === 'ECONNABORTED') return { status: 0, codigo: 'TIEMPO_AGOTADO', mensaje: 'El servidor tardó demasiado. Reintenta.' };
  return { status: 0, codigo: 'SIN_CONEXION', mensaje: MENSAJE_SIN_CONEXION };
}

// ------------------------------ reintentos (despliegues que reinician los servicios) ------------------------------
/** Un GET que falla con 502/503/504 o sin red se reintenta hasta 3 veces (0,8 s · 1,6 s · 3,2 s). */
const MAX_REINTENTOS = 3;
let enReintento = 0;
/** Evento global para el aviso «Actualizando el sistema, un momento…» (lo escucha `AvisoReintento`). */
export const EVENTO_REINTENTO = 'alc:reintento';
function avisarReintento(delta: number) {
  enReintento = Math.max(0, enReintento + delta);
  window.dispatchEvent(new CustomEvent(EVENTO_REINTENTO, { detail: enReintento > 0 }));
}
function reintentable(error: AxiosError, config: AxiosRequestConfig | undefined): boolean {
  if (!config || String(config.method ?? 'get').toLowerCase() !== 'get') return false;
  if ((config.reintentos ?? 0) >= MAX_REINTENTOS) return false;
  // RENIEC / SUNAT: su 503 es «el servicio externo no responde»; la pantalla ya ofrece escribir a mano.
  if (String(config.url ?? '').startsWith('/documentos/')) return false;
  const st = error.response?.status;
  if (st === 502 || st === 503 || st === 504) return true;
  return !error.response && error.code !== 'ECONNABORTED';
}

http.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    if (axios.isCancel(error)) return Promise.reject(error);
    const config = error.config as AxiosRequestConfig | undefined;
    if (config && reintentable(error, config)) {
      const n = (config.reintentos ?? 0) + 1;
      avisarReintento(1);
      try {
        await new Promise((ok) => setTimeout(ok, 800 * 2 ** (n - 1)));
        return await http({ ...config, reintentos: n });
      } finally {
        avisarReintento(-1);
      }
    }
    const cuerpo = aCuerpo(error);
    if (cuerpo.status === 401 && config?.tipoToken === 'cliente') {
      alExpirarCliente?.();
      if (!config.silenciarToast) notificarError?.('Tu sesión terminó. Ingresa de nuevo.');
    } else if (cuerpo.status === 401 && config?.tipoToken === 'panel') {
      alExpirarSesion?.();
    } else if (!config?.silenciarToast) {
      notificarError?.(cuerpo.mensaje);
    }
    return Promise.reject(new ErrorApi(cuerpo));
  },
);

export function esErrorApi(e: unknown): e is ErrorApi {
  return e instanceof ErrorApi;
}

/** URL absoluta de la API para enlaces de descarga (PDF, XML). */
export function urlApi(ruta: string): string {
  const base = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');
  if (/^https?:/.test(ruta)) return ruta;
  if (ruta.startsWith('/api/')) return ruta;
  return `${base}${ruta.startsWith('/') ? '' : '/'}${ruta}`;
}
