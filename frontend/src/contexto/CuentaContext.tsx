import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { registrarSesionCliente } from '@/api/cliente';
import { cuentaApi } from '@/api/servicios';
import type { CuentaCliente, RegistroCliente, RespuestaLoginCliente } from '@/api/tipos';
import { deLima } from '@/utilidades/fecha';

/**
 * Sesión del CLIENTE del portal (SPEC_V2 §E). Independiente de la del panel: token en localStorage («alc-cuenta»),
 * solo viaja a /auth/clientes/**, /cliente/**, POST /reservas y POST de reseñas (ver `rutaDeCliente` en api/cliente.ts).
 * Reservar SIN cuenta sigue igual: si no hay sesión, todo funciona como en la v1.
 */
interface SesionCliente {
  token: string;
  expiraEn: string;
  cliente: CuentaCliente;
}
interface ApiCuenta {
  cliente: CuentaCliente | null;
  conectado: boolean;
  ingresar: (usuario: string, contrasena: string) => Promise<CuentaCliente>;
  registrarse: (datos: RegistroCliente) => Promise<CuentaCliente>;
  actualizar: (c: CuentaCliente) => void;
  salir: () => void;
}

const CLAVE = 'alc-cuenta';
const Contexto = createContext<ApiCuenta | null>(null);

function vencida(expiraEn: string): boolean {
  const ms = expiraEn.endsWith('Z') || /[+-]\d\d:\d\d$/.test(expiraEn) ? new Date(expiraEn).getTime() : deLima(expiraEn).valueOf();
  return ms <= Date.now();
}
function leer(): SesionCliente | null {
  try {
    const s = JSON.parse(localStorage.getItem(CLAVE) ?? 'null') as SesionCliente | null;
    if (!s?.token || !s.cliente) return null;
    if (vencida(s.expiraEn)) {
      localStorage.removeItem(CLAVE);
      return null;
    }
    return s;
  } catch {
    return null;
  }
}

export function CuentaProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<SesionCliente | null>(leer);

  const guardar = useCallback((r: RespuestaLoginCliente) => {
    const s: SesionCliente = { token: r.token, expiraEn: r.expiraEn, cliente: r.cliente };
    localStorage.setItem(CLAVE, JSON.stringify(s));
    setSesion(s);
    return r.cliente;
  }, []);

  const salir = useCallback(() => {
    localStorage.removeItem(CLAVE);
    setSesion(null);
  }, []);

  useEffect(() => {
    registrarSesionCliente({ token: () => leer()?.token ?? null, alExpirar: salir });
    return () => registrarSesionCliente(null);
  }, [salir]);

  // Otra pestaña inició o cerró sesión.
  useEffect(() => {
    const alCambiar = (e: StorageEvent) => e.key === CLAVE && setSesion(leer());
    window.addEventListener('storage', alCambiar);
    return () => window.removeEventListener('storage', alCambiar);
  }, []);

  const api = useMemo<ApiCuenta>(
    () => ({
      cliente: sesion?.cliente ?? null,
      conectado: Boolean(sesion),
      ingresar: async (usuario, contrasena) => guardar(await cuentaApi.login(usuario, contrasena)),
      registrarse: async (datos) => guardar(await cuentaApi.registro(datos)),
      actualizar: (c) =>
        setSesion((s) => {
          if (!s) return s;
          const n = { ...s, cliente: c };
          localStorage.setItem(CLAVE, JSON.stringify(n));
          return n;
        }),
      salir,
    }),
    [sesion, guardar, salir],
  );
  return <Contexto.Provider value={api}>{children}</Contexto.Provider>;
}

export function useCuenta(): ApiCuenta {
  const c = useContext(Contexto);
  if (!c) throw new Error('useCuenta fuera de CuentaProvider');
  return c;
}
