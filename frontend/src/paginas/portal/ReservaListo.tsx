import { useParams } from 'react-router-dom';
import { CalendarPlus, CreditCard, Download, Mail, MessageCircle, Smartphone, Ticket, UserRound } from 'lucide-react';
import { reservasApi } from '@/api/servicios';
import { leerUltimaReserva } from '@/contexto/ReservaContext';
import { useCarga } from '@/hooks/useCarga';
import { useComplejo } from '@/hooks/useDatosPublicos';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { useCuentaRegresiva } from '@/hooks/useCuentaRegresiva';
import { BotonEnlace, BotonExterno } from '@/componentes/ui/Boton';
import { Alerta } from '@/componentes/ui/Estados';
import { CodigoReserva, LineaTiempo, type Hito } from '@/componentes/ui/Reserva';
import { Vacio } from '@/componentes/ui/Datos';
import { ResumenReserva } from '@/componentes/portal/Asistente';
import { HeroEstado, estadoCliente, turnoDeReserva } from '@/componentes/portal/EstadoReserva';
import { fmtHora12 } from '@/utilidades/fecha';
import { enmascararCelular, enmascararCorreo, fmtSoles } from '@/utilidades/formato';
import { RE_CODIGO_RESERVA } from '@/utilidades/documentos';
import type { ReservaDetalle } from '@/api/tipos';

const NOMBRE_METODO = { YAPE: 'Yape', PLIN: 'Plin', TARJETA: 'Tarjeta · Alcanfores Pay', MERCADO_PAGO: 'Mercado Pago' } as const;

/** /reservar/listo/:codigo — éxito: código grande, constancia PDF, aviso de copia y siguiente paso según el método. */
export default function ReservaListo() {
  useTituloPagina('Reserva registrada');
  const { codigo = '' } = useParams();
  const ultima = leerUltimaReserva(codigo);
  const { datos: complejo } = useComplejo();
  const det = useCarga<ReservaDetalle | null>(() => (ultima ? reservasApi.detalle(codigo, ultima.dni) : Promise.resolve(null)), ultima ? codigo : null);
  const base = det.datos ?? ultima?.reserva ?? null;
  const plazo = useCuentaRegresiva(base?.estado === 'PENDIENTE' && base.estadoPago === 'SIN_PAGO' ? base.venceEn : null);

  if (!RE_CODIGO_RESERVA.test(codigo)) {
    return (
      <div className="contenedor py-10">
        <Vacio ilustracion="IL-18-404-fuera-de-juego" titulo="Código no válido" acciones={<BotonEnlace to="/mi-reserva" icono={Ticket}>Buscar mi reserva</BotonEnlace>} />
      </div>
    );
  }

  // Desde otro dispositivo (sin datos en esta sesión): solo el código y el enlace a «Mi reserva».
  if (!ultima || !base) {
    return (
      <div className="contenedor max-w-[720px] grid gap-5 py-8">
        <CodigoReserva codigo={codigo} />
        <Alerta tipo="info" titulo="Consulta el estado con tu DNI">
          Te enviamos la constancia por correo y WhatsApp.
        </Alerta>
        <BotonEnlace to={`/mi-reserva?codigo=${codigo}`} icono={Ticket} tamano="lg">
          Ver mi reserva
        </BotonEnlace>
      </div>
    );
  }

  const clave = estadoCliente({ ...base, asistencia: 'PENDIENTE' });
  const pagado = 'montoPagado' in base ? (base as ReservaDetalle).montoPagado : 0;
  const metodo = ultima.metodo;
  const registrada = ('linea' in base ? (base as ReservaDetalle).linea[0]?.fecha : null) ?? null;

  const hitos: Hito[] = [
    { titulo: 'Registrada', estado: 'hecho', fecha: registrada ? fmtHora12(registrada.slice(11, 16)) : null, icono: Ticket },
    {
      titulo: clave === 'FALTA_PAGO' ? 'Pago' : clave === 'CONFIRMADA' ? 'Pagada' : 'En revisión',
      estado: clave === 'CONFIRMADA' ? 'hecho' : 'actual',
    },
    { titulo: 'Confirmada', estado: clave === 'CONFIRMADA' ? 'hecho' : 'pendiente' },
  ];

  const siguiente =
    clave === 'CONFIRMADA'
      ? metodo === 'TARJETA' || metodo === 'MERCADO_PAGO'
        ? `Pago aprobado (${fmtSoles(pagado || base.montoAdelanto)}). El saldo lo pagas al llegar.`
        : 'Validamos tu adelanto. El saldo lo pagas al llegar.'
      : clave === 'EN_REVISION'
        ? `Recibimos tu ${metodo === 'PLIN' ? 'Plin' : 'Yape'}. Lo validamos en minutos y te avisamos.`
        : clave === 'FALTA_PAGO'
          ? `Aún no recibimos tu pago. Complétalo antes de las ${base.venceEn ? fmtHora12(base.venceEn.slice(11, 16)) : '—'}.`
          : undefined;

  const constancia = reservasApi.urlConstancia(codigo, ultima.dni);

  return (
    <div className="contenedor grid gap-6 py-6 lg:grid-cols-[minmax(0,1fr)_380px] lg:gap-8 lg:py-10">
      <div className="grid gap-5 content-start min-w-0">
        <HeroEstado clave={clave} titulo={clave === 'EN_REVISION' ? '¡Listo! Tu horario está separado' : undefined} sub={siguiente} />

        <CodigoReserva
          codigo={codigo}
          acciones={
            <BotonExterno href={constancia} target="_blank" variante="contorno-claro" tamano="sm" icono={Download}>
              Constancia PDF
            </BotonExterno>
          }
        />

        <div className="card card-pad">
          <LineaTiempo hitos={hitos} horizontal />
        </div>

        {clave === 'FALTA_PAGO' && plazo.activo && !plazo.vencido && (
          <Alerta
            tipo="advertencia"
            titulo={`Tiempo para pagar: ${plazo.texto}`}
            acciones={
              <BotonEnlace to={`/mi-reserva?codigo=${codigo}`} variante="oscuro" tamano="sm" icono={CreditCard}>
                Pagar ahora
              </BotonEnlace>
            }
          >
            Si no pagas a tiempo, el horario se libera.
          </Alerta>
        )}

        <div className="card card-pad grid gap-3">
          <h2 className="t-h4">Te enviamos una copia</h2>
          <ul className="grid gap-2 text-[14px]">
            <li className="flex items-center gap-2.5">
              <span className="grid place-items-center w-9 h-9 rounded-10 bg-menta text-profundo shrink-0">
                <Mail className="ico ico-18" aria-hidden />
              </span>
              <span>
                Correo a <b>{enmascararCorreo(base.cliente.correo)}</b>
              </span>
            </li>
            <li className="flex items-center gap-2.5">
              <span className="grid place-items-center w-9 h-9 rounded-10 bg-menta text-profundo shrink-0">
                <MessageCircle className="ico ico-18" aria-hidden />
              </span>
              <span>
                WhatsApp al <b className="tnum">{enmascararCelular(base.cliente.telefono)}</b>
              </span>
            </li>
          </ul>
          <p className="t-mini">{`Te recordamos ${complejo?.reglas.recordatorioHoras ?? 2} h antes de tu partido (${fmtHora12(base.horaInicio)}).`}</p>
        </div>
      </div>

      <aside className="grid gap-4 content-start">
        <ResumenReserva datos={{ turno: turnoDeReserva(base), adelantoPct: complejo?.reglas.adelantoPct ?? 50, montoAdelanto: base.montoAdelanto }} />
        <div className="card overflow-hidden">
          <div className="flex items-center gap-3 p-4 border-b border-borde">
            <span className="grid place-items-center w-10 h-10 rounded-12 bg-menta text-profundo shrink-0">
              {metodo === 'TARJETA' ? <CreditCard className="ico ico-18" aria-hidden /> : <Smartphone className="ico ico-18" aria-hidden />}
            </span>
            <div className="grid">
              <b className="text-[14.5px]">{NOMBRE_METODO[metodo]}</b>
              <span className="t-mini">{pagado ? `Pagado ${fmtSoles(pagado)}` : `Adelanto ${fmtSoles(base.montoAdelanto)}`}</span>
            </div>
          </div>
          <div className="flex items-center gap-3 p-4">
            <span className="grid place-items-center w-10 h-10 rounded-12 bg-menta text-profundo shrink-0">
              <UserRound className="ico ico-18" aria-hidden />
            </span>
            <div className="grid">
              <b className="text-[14.5px]">{`${base.cliente.nombres} ${base.cliente.apellidos}`}</b>
              <span className="t-mini tnum">{`DNI ${base.cliente.dniEnmascarado} · ${enmascararCelular(base.cliente.telefono)}`}</span>
            </div>
          </div>
        </div>
        <BotonEnlace to={`/mi-reserva?codigo=${codigo}`} icono={Ticket} tamano="lg" bloque>
          Ver estado
        </BotonEnlace>
        <BotonEnlace to="/reservar" icono={CalendarPlus} variante="fantasma" bloque>
          Otra reserva
        </BotonEnlace>
      </aside>
    </div>
  );
}
