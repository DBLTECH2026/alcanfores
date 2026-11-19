import { useEffect, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { EVENTO_REINTENTO } from '@/api/cliente';

/** Píldora fija «Actualizando el sistema, un momento…» mientras el cliente HTTP reintenta un GET (503 / sin red). */
export function AvisoReintento() {
  const [activo, setActivo] = useState(false);
  useEffect(() => {
    const f = (e: Event) => setActivo(Boolean((e as CustomEvent<boolean>).detail));
    window.addEventListener(EVENTO_REINTENTO, f);
    return () => window.removeEventListener(EVENTO_REINTENTO, f);
  }, []);
  if (!activo) return null;
  return (
    <div className="aviso-reintento" role="status" aria-live="polite">
      <RefreshCw className="ico girar" aria-hidden />
      Actualizando el sistema, un momento…
    </div>
  );
}
