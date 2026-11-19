import { useEffect, useState } from 'react';
import { deLima } from '@/utilidades/fecha';

/** Cuenta regresiva hasta una fecha-hora ISO de Lima (`venceEn`, `expiraEn`). Se actualiza cada segundo. */
export function useCuentaRegresiva(fin: string | null | undefined) {
  const objetivo = fin ? deLima(fin).valueOf() : null;
  const [ahora, setAhora] = useState(() => Date.now());
  useEffect(() => {
    if (objetivo === null) return undefined;
    const t = window.setInterval(() => setAhora(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, [objetivo]);
  if (objetivo === null) return { activo: false, vencido: false, restanteMs: 0, texto: '' };
  const restanteMs = Math.max(0, objetivo - ahora);
  const s = Math.floor(restanteMs / 1000);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const seg = s % 60;
  const texto = h > 0 ? `${h} h ${String(m).padStart(2, '0')} min` : `${String(m).padStart(2, '0')}:${String(seg).padStart(2, '0')}`;
  return { activo: true, vencido: restanteMs === 0, restanteMs, texto };
}
