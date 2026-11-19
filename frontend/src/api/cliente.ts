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

export function registrarNotificador(fn: Notificar | null) {
  notificarError = fn;
}
export function registrarSesion(opciones: { token: () => string | null; alExpirar: () => void } | null) {
  leerToken = opciones?.token ?? (() => null);
  alExpirarSesion = opciones?.alExpirar ?? null;
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
  const token = leerToken();
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

http.interceptors.response.use(
  (r) => r,
  (error: AxiosError) => {
    if (axios.isCancel(error)) return Promise.reject(error);
    const cuerpo = aCuerpo(error);
    const config = error.config as AxiosRequestConfig | undefined;
    const conToken = Boolean(config?.headers && (config.headers as Record<string, unknown>).Authorization);
    if (cuerpo.status === 401 && conToken && !String(config?.url ?? '').includes('/auth/login')) {
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
