import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CircleCheck, RefreshCw, Ticket } from 'lucide-react';
import { pagosApi } from '@/api/servicios';
import { esErrorApi } from '@/api/cliente';
import type { EstadoIntentoRespuesta } from '@/api/tipos';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { Boton, BotonEnlace } from '@/componentes/ui/Boton';
import { Vacio } from '@/componentes/ui/Datos';

const INTERVALO = 3000;
const MAX_INTENTOS = 20;

/** /pago/resultado?ref= — retorno de Mercado Pago: sondea el estado del intento y ofrece «Ya pagué, verificar». */
export default function PagoResultado() {
  useTituloPagina('Resultado del pago');
  const [params] = useSearchParams();
  const ref = params.get('ref') ?? params.get('external_reference') ?? '';
  const [estado, setEstado] = useState<EstadoIntentoRespuesta | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [intentos, setIntentos] = useState(0);
  const [verificando, setVerificando] = useState(false);
  const temporizador = useRef<number | null>(null);

  const consultar = useCallback(async () => {
    if (!ref) return;
    setVerificando(true);
    try {
      const e = await pagosApi.estadoIntento(ref);
      setEstado(e);
      setError(null);
    } catch (e) {
      setError(esErrorApi(e) ? e.message : 'No se pudo consultar el pago.');
    } finally {
      setVerificando(false);
      setIntentos((n) => n + 1);
    }
  }, [ref]);

  const final = estado && ['APROBADO', 'RECHAZADO', 'EXPIRADO'].includes(estado.estado);

  useEffect(() => {
    if (!ref || final || intentos >= MAX_INTENTOS) return undefined;
    temporizador.current = window.setTimeout(() => void consultar(), intentos === 0 ? 0 : INTERVALO);
    return () => {
      if (temporizador.current) window.clearTimeout(temporizador.current);
    };
  }, [ref, final, intentos, consultar]);

  if (!ref) {
    return (
      <div className="contenedor py-10">
        <Vacio ilustracion="IL-18-404-fuera-de-juego" titulo="Falta la referencia del pago" acciones={<BotonEnlace to="/mi-reserva" icono={Ticket}>Mi reserva</BotonEnlace>} />
      </div>
    );
  }

  const codigo = estado?.codigoReserva;
  return (
    <div className="contenedor py-8 lg:py-14 max-w-[640px]">
      <div className="card card-pad" aria-live="polite">
        {estado?.estado === 'APROBADO' ? (
          <Vacio
            ilustracion="IL-12-confirmada"
            titulo="¡Pago aprobado!"
            texto="Tu reserva quedó confirmada. Te enviamos la constancia."
            acciones={
              <BotonEnlace to={codigo ? `/reservar/listo/${codigo}` : '/mi-reserva'} icono={CircleCheck} tamano="lg">
                Ver mi reserva
              </BotonEnlace>
            }
          />
        ) : estado?.estado === 'RECHAZADO' || estado?.estado === 'EXPIRADO' ? (
          <Vacio
            ilustracion="IL-13-rechazado"
            titulo={estado.estado === 'EXPIRADO' ? 'El pago venció' : 'Pago rechazado'}
            texto={estado.motivo ?? 'Puedes intentarlo de nuevo desde «Mi reserva».'}
            acciones={
              <BotonEnlace to={codigo ? `/mi-reserva?codigo=${codigo}` : '/mi-reserva'} icono={Ticket} tamano="lg">
                Mi reserva
              </BotonEnlace>
            }
          />
        ) : (
          <Vacio
            ilustracion="IL-11-en-revision"
            titulo={intentos >= MAX_INTENTOS ? 'Aún no recibimos la confirmación' : 'Verificando tu pago…'}
            texto={error ?? `Referencia ${ref}. No cierres esta página.`}
            acciones={
              <Boton variante="secundario" icono={RefreshCw} cargando={verificando} onClick={() => void consultar()}>
                Ya pagué, verificar
              </Boton>
            }
          />
        )}
      </div>
    </div>
  );
}
