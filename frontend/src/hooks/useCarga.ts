import { useCallback, useEffect, useRef, useState } from 'react';

export interface Carga<T> {
  datos: T | null;
  error: unknown;
  cargando: boolean;
  recargar: () => void;
  setDatos: (d: T | null) => void;
}

/**
 * Carga asíncrona simple: vuelve a pedir cuando cambia `clave` (o con `recargar`).
 * `clave = null` no pide nada. Ignora respuestas de pedidos viejos.
 */
export function useCarga<T>(fn: () => Promise<T>, clave: string | null): Carga<T> {
  const [datos, setDatos] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [cargando, setCargando] = useState<boolean>(clave !== null);
  const [intento, setIntento] = useState(0);
  const fnRef = useRef(fn);
  fnRef.current = fn;

  useEffect(() => {
    if (clave === null) {
      setCargando(false);
      return undefined;
    }
    let vigente = true;
    setCargando(true);
    setError(null);
    fnRef
      .current()
      .then((d) => {
        if (vigente) setDatos(d);
      })
      .catch((e: unknown) => {
        if (vigente) setError(e);
      })
      .finally(() => {
        if (vigente) setCargando(false);
      });
    return () => {
      vigente = false;
    };
  }, [clave, intento]);

  const recargar = useCallback(() => setIntento((i) => i + 1), []);
  return { datos, error, cargando, recargar, setDatos };
}
