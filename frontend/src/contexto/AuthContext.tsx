import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { registrarSesion } from '@/api/cliente';
import { authApi } from '@/api/servicios';
import type { Rol, UsuarioSesion } from '@/api/tipos';
import { deLima } from '@/utilidades/fecha';

/**
 * Sesión del panel. Token JWT en localStorage («alc-sesion»), expiración por `expiraEn` (8 h),
 * cierre automático al vencer y ante cualquier 401 de una petición con token (interceptor de Axios).
 */

interface Sesion {
  token: string;
  expiraEn: string;
  usuario: UsuarioSesion;
}
interface ApiAuth {
  usuario: UsuarioSesion | null;
  token: string | null;
  autenticado: boolean;
  /** true si la última salida fue por sesión vencida (para el aviso del login). */
  expirada: boolean;
  ingresar: (usuario: string, contrasena: string) => Promise<UsuarioSesion>;
  salir: (motivo?: 'manual' | 'expirada') => void;
  tieneRol: (...roles: Rol[]) => boolean;
  /** Reemplaza los datos del usuario de la sesión (tras editar «Mi cuenta»). */
  actualizarUsuario: (u: UsuarioSesion) => void;
}

const CLAVE = 'alc-sesion';
const Contexto = createContext<ApiAuth | null>(null);

function msHasta(expiraEn: string): number {
  const iso = expiraEn.endsWith('Z') || /[+-]\d\d:\d\d$/.test(expiraEn) ? new Date(expiraEn).getTime() : deLima(expiraEn).valueOf();
  return iso - Date.now();
}

function leerSesion(): Sesion | null {
  try {
    const s = JSON.parse(localStorage.getItem(CLAVE) ?? 'null') as Sesion | null;
    if (!s?.token || !s.usuario) return null;
    if (msHasta(s.expiraEn) <= 0) {
      localStorage.removeItem(CLAVE);
      return null;
    }
    return s;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [sesion, setSesion] = useState<Sesion | null>(leerSesion);
  const [expirada, setExpirada] = useState(false);

  const salir = useCallback<ApiAuth['salir']>(
    (motivo = 'manual') => {
      localStorage.removeItem(CLAVE);
      setSesion(null);
      setExpirada(motivo === 'expirada');
      navigate(motivo === 'expirada' ? '/admin/login?expirada=1' : '/admin/login', { replace: true });
    },
    [navigate],
  );

  const ingresar = useCallback<ApiAuth['ingresar']>(async (usuario, contrasena) => {
    const r = await authApi.login(usuario, contrasena);
    const nueva: Sesion = { token: r.token, expiraEn: r.expiraEn, usuario: r.usuario };
    localStorage.setItem(CLAVE, JSON.stringify(nueva));
    setSesion(nueva);
    setExpirada(false);
    return r.usuario;
  }, []);

  const actualizarUsuario = useCallback<ApiAuth['actualizarUsuario']>((u) => {
    setSesion((s) => {
      if (!s) return s;
      const nueva = { ...s, usuario: { ...s.usuario, ...u } };
      localStorage.setItem(CLAVE, JSON.stringify(nueva));
      return nueva;
    });
  }, []);

  // El interceptor lee el token y avisa los 401.
  useEffect(() => {
    registrarSesion({ token: () => leerSesion()?.token ?? null, alExpirar: () => salir('expirada') });
    return () => registrarSesion(null);
  }, [salir]);

  // Cierre automático al vencer el token.
  useEffect(() => {
    if (!sesion) return undefined;
    const ms = msHasta(sesion.expiraEn);
    if (ms <= 0) {
      salir('expirada');
      return undefined;
    }
    const t = window.setTimeout(() => salir('expirada'), Math.min(ms, 2 ** 31 - 1));
    return () => window.clearTimeout(t);
  }, [sesion, salir]);

  // Otra pestaña cerró sesión.
  useEffect(() => {
    const alCambiar = (e: StorageEvent) => {
      if (e.key === CLAVE) setSesion(leerSesion());
    };
    window.addEventListener('storage', alCambiar);
    return () => window.removeEventListener('storage', alCambiar);
  }, []);

  const api = useMemo<ApiAuth>(
    () => ({
      usuario: sesion?.usuario ?? null,
      token: sesion?.token ?? null,
      autenticado: Boolean(sesion),
      expirada,
      ingresar,
      salir,
      tieneRol: (...roles) => Boolean(sesion && roles.includes(sesion.usuario.rol)),
      actualizarUsuario,
    }),
    [sesion, expirada, ingresar, salir, actualizarUsuario],
  );

  return <Contexto.Provider value={api}>{children}</Contexto.Provider>;
}

export function useAuth(): ApiAuth {
  const c = useContext(Contexto);
  if (!c) throw new Error('useAuth fuera de AuthProvider');
  return c;
}
