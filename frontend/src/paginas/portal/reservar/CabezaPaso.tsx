import type { ReactNode } from 'react';
import { Globe } from 'lucide-react';
import { useReserva } from '@/contexto/ReservaContext';
import { Ilustracion } from '@/componentes/ui/Marca';
import { Stepper } from '@/componentes/portal/Asistente';
import type { IdIlustracion } from '@/utilidades/recursos';

/** Cabecera de cada paso: stepper + título + ilustración pequeña (≥ 768) + línea de apoyo. */
export function CabezaPaso({ titulo, ilustracion, apoyo, volver }: { titulo: string; ilustracion: IdIlustracion; apoyo?: ReactNode; volver?: ReactNode }) {
  const r = useReserva();
  const completados = [r.turno ? 1 : 0, r.cliente ? 2 : 0].filter(Boolean);
  return (
    <div className="grid gap-4 md:gap-5">
      {volver}
      <Stepper actual={r.paso} completados={completados} alIr={r.reserva ? undefined : (p) => r.irAPaso(p)} />
      <div className="flex items-center justify-between gap-4">
        <div className="grid gap-1.5 min-w-0">
          <h1 className="t-h1">{titulo}</h1>
          {apoyo && <div className="t-sec flex flex-wrap items-center gap-x-4 gap-y-1">{apoyo}</div>}
        </div>
        <Ilustracion id={ilustracion} className="hidden md:block w-[132px] h-[132px] shrink-0 -my-3" />
      </div>
    </div>
  );
}

export function HoraLimaCorta() {
  return (
    <span className="inline-flex items-center gap-1.5">
      <Globe className="ico ico-14" aria-hidden />
      Hora de Lima
    </span>
  );
}

