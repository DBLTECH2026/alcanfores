import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CircleCheck, MessageCircle, RefreshCw, Ticket } from 'lucide-react';
import { pagosApi } from '@/api/servicios';
import { esErrorApi } from '@/api/cliente';
import type { EstadoIntentoRespuesta } from '@/api/tipos';
import { useComplejo } from '@/hooks/useDatosPublicos';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { Boton, BotonEnlace, BotonExterno } from '@/componentes/ui/Boton';
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
  // La referencia no existe (404 / NO_ENCONTRADO): no se sigue «verificando».
  const [noExiste, setNoExiste] = useState(false);
  const { datos: complejo } = useComplejo();
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
      if (esErrorApi(e) && (e.status === 404 || e.codigo === 'NO_ENCONTRADO')) setNoExiste(true);
      setError(esErrorApi(e) ? e.message : 'No se pudo consultar el pago.');
    } finally {
      setVerificando(false);
      setIntentos((n) => n + 1);
    }
  }, [ref]);

  const final = estado && ['APROBADO', 'RECHAZADO', 'EXPIRADO'].includes(estado.estado);

  useEffect(() => {
    if (!ref || final || noExiste || intentos >= MAX_INTENTOS) return undefined;
    temporizador.current = window.setTimeout(() => void consultar(), intentos === 0 ? 0 : INTERVALO);
    return () => {
      if (temporizador.current) window.clearTimeout(temporizador.current);
    };
  }, [ref, final, noExiste, intentos, consultar]);

  if (!ref) {
    return (
      <div className="contenedor py-10">
        <Vacio ilustracion="IL-18-404-fuera-de-juego" titulo="Falta la referencia del pago" acciones={<BotonEnlace to="/mi-reserva" icono={Ticket}>Mi reserva</BotonEnlace>} />
      </div>
    );
  }

  const codigo = estado?.codigoReserva;
  const whatsapp = String(complejo?.whatsapp ?? '').replace(/[^0-9]/g, '');
  // Sin tarjeta, igual que los otros vacíos (404, «Código no válido», «Falta la referencia»).
  return (
    <div className="contenedor py-10 lg:py-16" aria-live="polite">
      {noExiste && !estado ? (
        <Vacio
          ilustracion="IL-24-sin-resultados"
          titulo="No encontramos ese pago"
          texto={`Revisa el enlace o consulta tu reserva. Referencia ${ref}.`}
          acciones={
            <>
              <BotonEnlace to="/mi-reserva" icono={Ticket}>
                Mi reserva
              </BotonEnlace>
              {whatsapp && (
                <BotonExterno href={`https://wa.me/${whatsapp}`} target="_blank" rel="noopener" variante="secundario" icono={MessageCircle}>
                  WhatsApp
                </BotonExterno>
              )}
            </>
          }
        />
      ) : estado?.estado === 'APROBADO' ? (
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
          texto={estado.motivoRechazo ?? 'Puedes intentarlo de nuevo desde «Mi reserva».'}
          acciones={
            <BotonEnlace to={codigo ? `/mi-reserva?codigo=${codigo}` : '/mi-reserva'} icono={Ticket} tamano="lg">
              Mi reserva
            </BotonEnlace>
          }
        />
      ) : (
        // «Verificando» solo mientras el intento sigue PENDIENTE; si no se pudo consultar, se dice así.
        <Vacio
          ilustracion={error ? 'IL-19-sin-conexion' : 'IL-11-en-revision'}
          titulo={intentos >= MAX_INTENTOS ? 'Aún no recibimos la confirmación' : error ? 'No pudimos verificar tu pago' : 'Verificando tu pago…'}
          texto={error ?? `Referencia ${ref}. No cierres esta página.`}
          acciones={
            <Boton variante="secundario" icono={RefreshCw} cargando={verificando} onClick={() => void consultar()}>
              Ya pagué, verificar
            </Boton>
          }
        />
      )}
    </div>
  );
}
