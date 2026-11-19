import { useEffect } from 'react';

/** Título de la pestaña: «<título> · Alcanfores». */
export function useTituloPagina(titulo: string) {
  useEffect(() => {
    document.title = `${titulo} · Alcanfores`;
  }, [titulo]);
}
