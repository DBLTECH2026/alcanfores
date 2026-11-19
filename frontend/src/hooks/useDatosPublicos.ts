import { useEffect, useState } from 'react';
import { catalogoApi, pagosApi } from '@/api/servicios';
import type { CanchaResumen, Complejo, MetodosPago } from '@/api/tipos';

/** Datos públicos que casi no cambian: se piden una vez por sesión y se comparten entre pantallas. */
function cacheado<T>(pedir: () => Promise<T>) {
  let promesa: Promise<T> | null = null;
  let valor: T | null = null;
  return {
    obtener(): Promise<T> {
      if (!promesa) {
        promesa = pedir().then((v) => {
          valor = v;
          return v;
        });
        promesa.catch(() => {
          promesa = null;
        });
      }
      return promesa;
    },
    actual: () => valor,
  };
}

const complejo = cacheado(catalogoApi.complejo);
const canchas = cacheado(catalogoApi.canchas);
const metodos = cacheado(pagosApi.metodos);

function useCache<T>(c: ReturnType<typeof cacheado<T>>) {
  const [valor, setValor] = useState<T | null>(c.actual());
  const [error, setError] = useState<unknown>(null);
  const [intento, setIntento] = useState(0);
  useEffect(() => {
    let vigente = true;
    if (c.actual()) return undefined;
    c.obtener()
      .then((v) => vigente && setValor(v))
      .catch((e: unknown) => vigente && setError(e));
    return () => {
      vigente = false;
    };
  }, [c, intento]);
  return {
    datos: valor,
    error,
    cargando: !valor && !error,
    recargar: () => {
      setError(null);
      setIntento((i) => i + 1);
    },
  };
}

export const useComplejo = () => useCache<Complejo>(complejo);
export const useCanchas = () => useCache<CanchaResumen[]>(canchas);
export const useMetodosPago = () => useCache<MetodosPago>(metodos);
