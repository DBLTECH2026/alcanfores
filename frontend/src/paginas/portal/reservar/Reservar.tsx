import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ArrowRight, Check, RotateCcw } from 'lucide-react';
import { ReservaProvider, useReserva } from '@/contexto/ReservaContext';
import { useCanchas, useComplejo } from '@/hooks/useDatosPublicos';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { Boton } from '@/componentes/ui/Boton';
import { Alerta } from '@/componentes/ui/Estados';
import { BarraResumen, ResumenReserva, type DatosResumen } from '@/componentes/portal/Asistente';
import Paso1 from './Paso1';
import Paso2 from './Paso2';
import Paso3 from './Paso3';

function Contenido() {
  useTituloPagina('Reservar');
  const r = useReserva();
  const [params, setParams] = useSearchParams();
  const canchas = useCanchas();
  const { datos: complejo } = useComplejo();
  const [enviando, setEnviando] = useState(false);
  const pct = complejo?.reglas.adelantoPct ?? 50;

  // ?cancha=<id o slug> llega desde el detalle de la cancha o el inicio.
  const deParam = params.get('cancha');
  useEffect(() => {
    if (!deParam || !canchas.datos || r.reserva) return;
    const c = canchas.datos.find((x) => String(x.id) === deParam || x.slug === deParam);
    if (c) {
      r.elegirCancha(c.id);
      r.irAPaso(1);
    }
    params.delete('cancha');
    setParams(params, { replace: true });
    // solo al llegar con el parámetro
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deParam, canchas.datos]);

  // Con la reserva ya creada (pago pendiente) solo queda el paso 3.
  useEffect(() => {
    if (r.reserva && r.paso !== 3) r.irAPaso(3);
  }, [r]);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [r.paso]);

  const datos: DatosResumen = {
    turno: r.turno,
    adelantoPct: pct,
    montoAdelanto: r.reserva?.montoAdelanto,
    cliente: r.paso === 3 && r.cliente ? { nombres: r.cliente.nombres, apellidos: r.cliente.apellidos, telefono: r.cliente.telefono, correo: r.cliente.correo } : null,
    codigo: r.reserva?.codigoReserva,
  };

  const cta =
    r.paso === 1 ? (
      <Boton bloque iconoDer={ArrowRight} disabled={!r.turno} onClick={() => r.irAPaso(2)}>
        Continuar
      </Boton>
    ) : r.paso === 2 ? (
      <Boton bloque iconoDer={ArrowRight} type="submit" form="form-paso2">
        Continuar
      </Boton>
    ) : (
      <Boton bloque tamano="lg" icono={Check} type="submit" form="form-paso3" cargando={enviando} textoCargando="Confirmando…">
        {r.reserva ? 'Completar pago' : 'Confirmar reserva'}
      </Boton>
    );

  const nota =
    r.paso === 3 && r.reserva
      ? 'Tu horario está separado mientras completas el pago.'
      : `Al confirmar separamos tu horario ${complejo?.reglas.minutosParaPagar ?? 20} min para que pagues.`;

  return (
    <>
      <div className="contenedor grid gap-6 pt-5 pb-8 lg:grid-cols-[minmax(0,1fr)_360px] lg:gap-10 lg:pt-8 lg:pb-14">
        <div className="grid gap-6 content-start min-w-0">
          {r.reserva && r.paso === 3 && (
            <Alerta
              tipo="info"
              titulo={`Reserva ${r.reserva.codigoReserva} separada`}
              acciones={
                <Boton variante="fantasma" tamano="sm" icono={RotateCcw} onClick={r.reiniciar}>
                  Empezar de nuevo
                </Boton>
              }
            >
              Completa el pago antes de que venza el plazo.
            </Alerta>
          )}
          {r.paso === 1 && <Paso1 />}
          {r.paso === 2 && <Paso2 />}
          {r.paso === 3 && <Paso3 alCambiarEnvio={setEnviando} />}
        </div>
        <div className="solo-escritorio">
          <div className="lg:sticky lg:top-[96px] grid gap-4">
            <ResumenReserva
              datos={datos}
              nota={nota}
              cta={cta}
              accionCab={
                r.paso > 1 && r.turno && !r.reserva ? (
                  <button type="button" className="enlace bg-transparent border-0 p-0 text-[14px]" onClick={() => r.irAPaso(1)}>
                    Cambiar
                  </button>
                ) : undefined
              }
            />
          </div>
        </div>
      </div>
      <BarraResumen datos={datos} cta={cta} completo={<ResumenReserva datos={datos} nota={nota} />} />
    </>
  );
}

/** /reservar — asistente de 3 pasos con su propio estado (ReservaContext). */
export default function Reservar() {
  return (
    <ReservaProvider>
      <Contenido />
    </ReservaProvider>
  );
}
