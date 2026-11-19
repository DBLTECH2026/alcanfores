import type { ReactNode } from 'react';
import {
  Building2,
  CalendarClock,
  CalendarX,
  Database,
  FileText,
  HandCoins,
  House,
  Image,
  Mail,
  MessageCircle,
  Receipt,
  Server,
  ShieldCheck,
  Target,
  TriangleAlert,
  UserRound,
  Wallet,
  CreditCard,
  Clock,
  type LucideIcon,
} from 'lucide-react';
import { BotonEnlace } from '@/componentes/ui/Boton';
import { Ilustracion } from '@/componentes/ui/Marca';
import { Vacio } from '@/componentes/ui/Datos';
import { useComplejo } from '@/hooks/useDatosPublicos';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import type { IdIlustracion } from '@/utilidades/recursos';

/** Emisor FICTICIO (SPEC §5.4): proyecto académico, no es una empresa real. */
const EMISOR = { ruc: '20612345679', razon: 'Los Alcanfores Sport E.I.R.L. (ficticia)' };

function Pagina({ kicker, titulo, ilustracion, actualizado, children }: { kicker: string; titulo: string; ilustracion: IdIlustracion; actualizado: string; children: ReactNode }) {
  return (
    <div className="contenedor py-8 lg:py-12">
      <div className="grid gap-8 lg:grid-cols-[280px_minmax(0,760px)] lg:gap-14 lg:justify-center">
        <div className="grid gap-3 content-start justify-items-center text-center lg:justify-items-start lg:text-left lg:sticky lg:top-[100px] self-start">
          <Ilustracion id={ilustracion} carga="eager" className="w-[160px] h-[160px] lg:w-[240px] lg:h-[240px]" />
          <span className="kicker kicker-raya">{kicker}</span>
          <h1 className="t-h1">{titulo}</h1>
          <span className="t-mini">{actualizado}</span>
        </div>
        <div className="grid gap-3 content-start">{children}</div>
      </div>
    </div>
  );
}

function Punto({ icono: I, titulo, children, alerta }: { icono: LucideIcon; titulo: string; children: ReactNode; alerta?: boolean }) {
  return (
    <section className="card p-4 md:p-5 flex gap-4 items-start">
      <span className={`grid place-items-center w-11 h-11 rounded-12 shrink-0 ${alerta ? 'bg-amarillo-tinte text-noche' : 'bg-menta text-profundo'}`}>
        <I className="ico ico-20" aria-hidden />
      </span>
      <div className="grid gap-1">
        <h2 className="t-h4">{titulo}</h2>
        <div className="t-sec !text-[14.5px] leading-relaxed">{children}</div>
      </div>
    </section>
  );
}

export function Terminos() {
  useTituloPagina('Términos y condiciones');
  const { datos: c } = useComplejo();
  const pct = c?.reglas.adelantoPct ?? 50;
  const horas = c?.reglas.devolucionHoras ?? 24;
  const minutos = c?.reglas.minutosParaPagar ?? 20;
  return (
    <Pagina kicker="Reservas web" titulo="Términos y condiciones" ilustracion="IL-21-compartir" actualizado="Vigentes desde noviembre de 2026">
      <Punto icono={Building2} titulo="Quién presta el servicio">
        {`${c?.nombre ?? 'Complejo Deportivo Los Alcanfores'} · ${c?.direccion ?? 'Carretera Central km 11.5, Santa Clara, Ate'}. Comprobantes emitidos por ${EMISOR.razon}, RUC ${EMISOR.ruc} (datos ficticios de un proyecto académico de la UTP).`}
      </Punto>
      <Punto icono={Wallet} titulo="Adelanto">
        {`Para separar un turno pagas un adelanto del ${pct} % por Yape, Plin o tarjeta (Alcanfores Pay). El saldo lo pagas en el complejo antes de jugar.`}
      </Punto>
      <Punto icono={Clock} titulo="Plazo para pagar">
        {`Tu horario queda separado ${minutos} minutos para que completes el pago. Si no llega a tiempo, la reserva se cancela sola y el turno se libera.`}
      </Punto>
      <Punto icono={ShieldCheck} titulo="Confirmación">
        Con tarjeta la reserva se confirma al instante. Con Yape o Plin, cuando el complejo valida tu comprobante. Si el comprobante tiene un problema, te avisamos y puedes reenviarlo.
      </Punto>
      <Punto icono={CalendarX} titulo="Cancelación">
        Puedes cancelar desde «Mi reserva» con tu código y tu DNI, antes de que empiece el turno, indicando un motivo.
      </Punto>
      <Punto icono={HandCoins} titulo="Devoluciones">
        {`Si cancelas con ${horas} horas o más de anticipación, te devolvemos lo pagado y emitimos una nota de crédito. Si el complejo cancela, siempre te devolvemos lo pagado.`}
      </Punto>
      <Punto icono={TriangleAlert} titulo="Sin devolución" alerta>
        {`Si cancelas con menos de ${horas} horas o no te presentas, el adelanto no se devuelve.`}
      </Punto>
      <Punto icono={Receipt} titulo="Comprobantes">
        Por cada pago validado emitimos boleta o factura electrónica (simulada en esta versión) y te la enviamos por correo y WhatsApp.
      </Punto>
      <Punto icono={CreditCard} titulo="Pagos con tarjeta">
        Alcanfores Pay es una pasarela de prueba (beta): no realiza cobros reales. Nunca guardamos el número completo de tu tarjeta ni el CVV.
      </Punto>
      <Punto icono={MessageCircle} titulo="Contacto">
        {`WhatsApp ${c?.whatsapp ?? '+51 914 995 187'} · ${c?.correo ?? 'informes.alcanfores@gmail.com'}`}
      </Punto>
    </Pagina>
  );
}

export function Privacidad() {
  useTituloPagina('Política de privacidad');
  const { datos: c } = useComplejo();
  return (
    <Pagina kicker="Ley N.° 29733" titulo="Política de privacidad" ilustracion="IL-20-privacidad" actualizado="Ley de Protección de Datos Personales y su reglamento">
      <Punto icono={Building2} titulo="Responsable">
        {`${c?.nombre ?? 'Complejo Deportivo Los Alcanfores'}, ${c?.direccion ?? 'Carretera Central km 11.5, Santa Clara, Ate'}. Titular del banco de datos: ${EMISOR.razon}, RUC ${EMISOR.ruc} (ficticio).`}
      </Punto>
      <Punto icono={Database} titulo="Banco de datos">
        «Clientes de reservas». El código de inscripción ante la Autoridad Nacional de Protección de Datos Personales lo completa el complejo.
      </Punto>
      <Punto icono={Target} titulo="Para qué usamos tus datos">
        Solo para gestionar tu reserva, emitir tu comprobante y avisarte por correo y WhatsApp sobre ella (registro, confirmación, recordatorio y cancelación).
      </Punto>
      <Punto icono={UserRound} titulo="Qué datos pedimos">
        DNI, nombres, apellidos, celular, correo y, si pagas con Yape o Plin, la captura del comprobante. Si pides factura, el RUC y la razón social. Sin ellos no puedes reservar por la web.
      </Punto>
      <Punto icono={FileText} titulo="Consulta a RENIEC y SUNAT">
        Con tu DNI o RUC consultamos nombres y razón social en fuentes públicas para evitar errores. No guardamos más de lo que ves en el formulario.
      </Punto>
      <Punto icono={Image} titulo="Tu captura">
        Puede mostrar el nombre de quien hizo el pago. Solo la ve el personal que valida pagos.
      </Punto>
      <Punto icono={Server} titulo="Encargados">
        El proveedor de hosting y los servicios de mensajería (correo y WhatsApp). No vendemos ni cedemos tus datos.
      </Punto>
      <Punto icono={CalendarClock} titulo="Conservación">
        12 meses después de la fecha de tu turno; los comprobantes, el plazo que exige la ley tributaria.
      </Punto>
      <Punto icono={Mail} titulo="Tus derechos (ARCO)">
        {`Acceso, rectificación, cancelación y oposición: escríbenos a ${c?.correo ?? 'informes.alcanfores@gmail.com'} o al WhatsApp ${c?.whatsapp ?? '+51 914 995 187'}. Respondemos en el plazo de ley (máximo 20 días hábiles).`}
      </Punto>
    </Pagina>
  );
}

export function NoEncontrada() {
  useTituloPagina('Fuera de juego');
  return (
    <div className="contenedor py-10 lg:py-16">
      <Vacio
        ilustracion="IL-18-404-fuera-de-juego"
        titulo="Fuera de juego"
        texto="Esta página no existe."
        acciones={
          <BotonEnlace to="/" icono={House}>
            Volver al inicio
          </BotonEnlace>
        }
      />
    </div>
  );
}
