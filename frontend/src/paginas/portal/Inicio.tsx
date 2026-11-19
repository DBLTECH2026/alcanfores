import type { ReactNode } from 'react';
import {
  ArrowRight,
  CalendarDays,
  CalendarPlus,
  ChevronDown,
  Clock,
  CreditCard,
  Eye,
  MapPin,
  MessageCircle,
  Moon,
  Navigation,
  RefreshCw,
  ShieldCheck,
  Smartphone,
  Sun,
  Ticket,
  UserRound,
  Wallet,
  HelpCircle,
} from 'lucide-react';
import { BotonEnlace, BotonExterno, Boton } from '@/componentes/ui/Boton';
import { IconoDeporte, Ilustracion, PatronCancha } from '@/componentes/ui/Marca';
import { Vacio } from '@/componentes/ui/Datos';
import { TarjetaCancha, TarjetaCanchaEsqueleto } from '@/componentes/portal/Canchas';
import { CondicionesIconos } from '@/componentes/portal/Asistente';
import { enlaceWhatsapp } from '@/componentes/portal/MarcoPortal';
import { useCanchas, useComplejo } from '@/hooks/useDatosPublicos';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { fmtHora12 } from '@/utilidades/fecha';
import { fmtSoles } from '@/utilidades/formato';
import type { IdIlustracion } from '@/utilidades/recursos';

/** Encabezado de sección: kicker con raya amarilla + h2 (+ acción a la derecha). */
export function CabSeccion({ id, kicker, titulo, accion }: { id: string; kicker: string; titulo: string; accion?: ReactNode }) {
  return (
    <div className="grid gap-1.5">
      <span className="kicker kicker-raya">{kicker}</span>
      <div className="flex items-end justify-between gap-3">
        <h2 id={id} className="t-h2">
          {titulo}
        </h2>
        {accion}
      </div>
    </div>
  );
}

const PASOS: Array<{ il: IdIlustracion; icono: typeof Clock; titulo: string; apoyo: string }> = [
  { il: 'IL-02-paso1-horario', icono: Clock, titulo: 'Elige tu horario', apoyo: 'Cancha, día y hora' },
  { il: 'IL-03-paso2-datos', icono: UserRound, titulo: 'Tus datos', apoyo: 'DNI, celular y correo' },
  { il: 'IL-04-paso3-comprobante', icono: Wallet, titulo: 'Paga el adelanto', apoyo: 'Yape, Plin o tarjeta' },
];
const GARANTIAS = [
  { icono: Ticket, texto: 'Código de reserva' },
  { icono: Eye, texto: 'Estado en línea' },
  { icono: ShieldCheck, texto: 'Pago validado' },
];

const FAQ = [
  { p: '¿Necesito crear una cuenta?', r: 'No. Reservas con tu DNI, celular y correo. Con tu código y tu DNI consultas o cancelas cuando quieras.' },
  { p: '¿Cuánto pago para separar?', r: 'Un adelanto del 50 % del turno. El saldo lo pagas en el complejo antes de jugar.' },
  { p: '¿Cuándo queda confirmada?', r: 'Con tarjeta, al instante. Con Yape o Plin, cuando validamos tu comprobante (en minutos, en horario de atención).' },
  { p: '¿Puedo cancelar?', r: 'Sí, desde «Mi reserva». Si cancelas con 24 horas o más de anticipación, te devolvemos lo pagado.' },
  { p: '¿Me llega comprobante?', r: 'Sí. Te enviamos la constancia y tu boleta o factura por correo y por WhatsApp.' },
];

export default function Inicio() {
  useTituloPagina('Reserva tu cancha');
  const { datos: complejo } = useComplejo();
  const canchas = useCanchas();
  const lista = canchas.datos ?? [];
  const sinCanchas = canchas.datos !== null && lista.length === 0;
  const desde = (clave: 'precioDia' | 'precioNoche') => (lista.length ? Math.min(...lista.map((c) => c[clave])) : null);
  const zona = complejo ? complejo.direccion.split(', ').slice(1).join(', ') : 'Santa Clara, Ate';
  const lat = complejo?.latitud ?? -12.0262;
  const lng = complejo?.longitud ?? -76.8918;
  const reglas = complejo?.reglas;

  return (
    <>
      {/* ------------------------------ héroe ------------------------------ */}
      <section className="relative overflow-hidden bg-menta" aria-labelledby="h-inicio">
        <PatronCancha className="absolute -right-28 -bottom-20 w-[520px] text-suave opacity-60 pointer-events-none lg:w-[820px] lg:-right-20 lg:-bottom-24" />
        <div className="contenedor relative grid gap-4 pt-4 pb-7 md:pt-8 lg:grid-cols-[1.12fr_1fr] lg:items-center lg:gap-10 lg:py-14">
          <div className="lg:order-2">
            <Ilustracion id="IL-01-hero" carga="eager" className="mx-auto h-[212px] w-auto md:h-[340px] lg:h-auto lg:w-full" alt="Jugador reserva su cancha desde el celular" />
          </div>
          <div className="grid gap-4 content-center lg:order-1 lg:gap-6">
            <span className="kicker kicker-raya">{zona}</span>
            <h1 id="h-inicio" className="t-display">
              Reserva tu cancha. <span className="text-verde">Juega cuando quieras.</span>
            </h1>
            <ul className="flex flex-wrap gap-2" aria-label="Así reservas">
              {[
                { i: CalendarDays, t: 'Todos los días' },
                { i: UserRound, t: 'Sin cuenta' },
                { i: Smartphone, t: 'Yape, Plin o tarjeta' },
              ].map(({ i: I, t }) => (
                <li key={t} className="inline-flex items-center gap-1.5 h-8 px-3 rounded-pill bg-white border border-borde text-[13px] font-semibold text-noche shadow-1">
                  <I className="ico ico-16 text-verde" aria-hidden />
                  {t}
                </li>
              ))}
            </ul>
            <div className="grid gap-2.5 mt-1 md:flex md:gap-3">
              <BotonEnlace to="/reservar" icono={CalendarPlus} tamano="lg">
                Reservar
              </BotonEnlace>
              <span className="solo-tablet-arriba">
                <BotonEnlace to="/mi-reserva" icono={Ticket} variante="secundario" tamano="lg">
                  Mi reserva
                </BotonEnlace>
              </span>
            </div>
          </div>
        </div>
      </section>

      {/* ------------------------------ cómo funciona ------------------------------ */}
      <section className="py-8 lg:py-14" aria-labelledby="h-pasos">
        <div className="contenedor grid gap-5 lg:gap-8">
          <CabSeccion id="h-pasos" kicker="Cómo funciona" titulo="Reserva en 3 pasos" />
          <ol className="grid gap-3 md:grid-cols-3 md:gap-4 lg:gap-6">
            {PASOS.map((p, i) => {
              const I = p.icono;
              return (
                <li key={p.il} className="card flex items-center gap-4 p-3 pr-4 md:flex-col md:text-center md:gap-3 md:px-4 md:pt-5 md:pb-6 lg:px-6 lg:pt-6 lg:pb-7">
                  <Ilustracion id={p.il} className="w-[104px] h-[104px] shrink-0 md:w-[144px] md:h-[144px] lg:w-[188px] lg:h-[188px]" />
                  <div className="grid gap-1.5 md:justify-items-center">
                    <span className="inline-flex items-center gap-2">
                      <span className="grid place-items-center w-7 h-7 rounded-pill bg-noche text-white font-titular font-extrabold text-[15px] leading-none" aria-hidden>
                        {i + 1}
                      </span>
                      <I className="ico ico-18 text-verde" aria-hidden />
                      <span className="sr-only">{`Paso ${i + 1}:`}</span>
                    </span>
                    <h3 className="t-h3">{p.titulo}</h3>
                    <p className="t-sec">{p.apoyo}</p>
                  </div>
                </li>
              );
            })}
          </ol>
          <ul className="grid grid-cols-3 gap-2 pt-1 md:flex md:justify-center md:gap-10" aria-label="Garantías">
            {GARANTIAS.map(({ icono: I, texto }) => (
              <li key={texto} className="grid justify-items-center text-center gap-2 md:flex md:items-center md:gap-2.5 md:text-left">
                <span className="grid place-items-center w-11 h-11 rounded-pill bg-menta text-verde shrink-0">
                  <I className="ico ico-20" aria-hidden />
                </span>
                <span className="text-[12.5px] font-semibold leading-tight text-noche md:text-[14px]">{texto}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* ------------------------------ canchas ------------------------------ */}
      <section className="pb-8 lg:pb-14" aria-labelledby="h-canchas">
        <div className="contenedor grid gap-5 lg:gap-7">
          <CabSeccion
            id="h-canchas"
            kicker="Fútbol 5 · 6 · 7 · Vóley · Básquet"
            titulo="Nuestras canchas"
            accion={
              <BotonEnlace to="/canchas" variante="fantasma" tamano="sm" iconoDer={ArrowRight} className="!text-verde solo-telefono">
                Ver todas
              </BotonEnlace>
            }
          />
          {canchas.error ? (
            <div className="card" role="alert">
              <Vacio
                ilustracion="IL-19-sin-conexion"
                titulo="Sin conexión con el servidor"
                compacto
                acciones={
                  <Boton variante="secundario" icono={RefreshCw} onClick={canchas.recargar}>
                    Reintentar
                  </Boton>
                }
              />
            </div>
          ) : sinCanchas ? (
            <div className="card">
              <Vacio
                ilustracion="IL-25-sin-canchas"
                titulo="Sin canchas por ahora"
                compacto
                acciones={
                  <BotonExterno href={enlaceWhatsapp(complejo?.whatsapp)} target="_blank" variante="whatsapp" icono={MessageCircle}>
                    WhatsApp
                  </BotonExterno>
                }
              />
            </div>
          ) : (
            <div
              className="flex gap-3 overflow-x-auto snap-x snap-mandatory mx-[calc(-1*var(--gutter))] px-[var(--gutter)] scroll-px-[var(--gutter)] pb-1 [scrollbar-width:none] md:grid md:grid-cols-2 md:overflow-visible md:mx-0 md:px-0 md:gap-5 lg:grid-cols-4"
              aria-label="Canchas"
              aria-busy={canchas.cargando || undefined}
            >
              {canchas.cargando
                ? [1, 2, 3, 4].map((n) => (
                    <div key={n} className="snap-start shrink-0 w-[296px] md:w-auto grid">
                      <TarjetaCanchaEsqueleto />
                    </div>
                  ))
                : lista.map((c) => (
                    <div key={c.id} className="snap-start shrink-0 w-[296px] md:w-auto grid">
                      <TarjetaCancha cancha={c} />
                    </div>
                  ))}
            </div>
          )}
        </div>
      </section>

      {/* ------------------------------ tarifas ------------------------------ */}
      {!canchas.error && !sinCanchas && (
        <section className="bg-superficie-suave border-y border-borde py-8 lg:py-14" aria-labelledby="h-tarifas">
          <div className="contenedor grid gap-5 lg:gap-7">
            <CabSeccion id="h-tarifas" kicker="Tarifas" titulo="Precio por hora" />
            <div className="grid gap-5 lg:grid-cols-2 lg:gap-10 lg:items-start">
              <div className="grid gap-4 lg:gap-5">
                <div className="grid gap-3 md:grid-cols-2">
                  {(
                    [
                      { clave: 'precioDia', icono: Sun, cls: 'text-amarillo-ocre', et: 'Día', txt: complejo ? `${fmtHora12(complejo.horario.primerTurno)} – ${fmtHora12(complejo.horario.inicioNoche)}` : '' },
                      { clave: 'precioNoche', icono: Moon, cls: 'text-noche', et: 'Noche', txt: complejo ? `${fmtHora12(complejo.horario.inicioNoche)} – ${fmtHora12(complejo.horario.ultimoTurno)}` : '' },
                    ] as const
                  ).map(({ clave, icono: I, cls, et, txt }) => {
                    const v = desde(clave);
                    return (
                      <div key={clave} className="card flex items-center justify-between gap-3 px-4 py-3.5 md:grid md:content-start md:gap-2.5 md:p-[18px]">
                        <div className="grid gap-1.5">
                          <span className="inline-flex items-center gap-2 font-titular font-bold text-[19px] leading-none">
                            <I className={`ico ico-24 ${cls}`} aria-hidden />
                            {et}
                          </span>
                          <span className="t-mini tnum">{txt}</span>
                        </div>
                        {v === null ? (
                          <span className="skel block w-[88px] h-[26px] !rounded-8" />
                        ) : (
                          <span className="desde">
                            <span className="desde-et">Desde</span>
                            <span className="desde-val">
                              {fmtSoles(v, { decimales: 0 })}
                              <small>/ hora</small>
                            </span>
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
                <div>
                  <BotonEnlace to="/canchas" variante="secundario" iconoDer={ArrowRight}>
                    Ver tarifas
                  </BotonEnlace>
                </div>
              </div>
              <CondicionesIconos adelantoPct={reglas?.adelantoPct} devolucionHoras={reglas?.devolucionHoras} />
            </div>
          </div>
        </section>
      )}

      {/* ------------------------------ ubicación ------------------------------ */}
      <section className="py-8 lg:py-14" aria-labelledby="h-ubicacion">
        <div className="contenedor grid gap-5 lg:grid-cols-[1fr_1.3fr] lg:gap-10 lg:items-center">
          <div className="grid gap-4">
            <CabSeccion id="h-ubicacion" kicker="Ubicación" titulo="Cómo llegar" />
            <ul className="grid gap-3 text-[15px]">
              <li className="flex gap-3 items-start">
                <span className="resumen-ico w-9 h-9 rounded-10 bg-menta text-profundo grid place-items-center shrink-0">
                  <MapPin className="ico ico-18" aria-hidden />
                </span>
                <span>
                  <b className="block">{complejo?.direccion ?? 'Carretera Central km 11.5, Santa Clara, Ate'}</b>
                  <span className="t-sec">{complejo?.referencia ?? 'Paradero Caseta, a dos cuadras de la UTP Santa Clara'}</span>
                </span>
              </li>
              <li className="flex gap-3 items-center">
                <span className="w-9 h-9 rounded-10 bg-menta text-profundo grid place-items-center shrink-0">
                  <Clock className="ico ico-18" aria-hidden />
                </span>
                <span className="tnum">
                  {complejo ? `Todos los días · ${fmtHora12(complejo.horario.primerTurno)} – ${fmtHora12(complejo.horario.ultimoTurno)}` : 'Todos los días'}
                </span>
              </li>
              <li className="flex gap-3 items-center">
                <span className="w-9 h-9 rounded-10 bg-menta text-profundo grid place-items-center shrink-0">
                  <CreditCard className="ico ico-18" aria-hidden />
                </span>
                <span>Yape · Plin · Tarjeta</span>
              </li>
            </ul>
            <div className="grupo-botones">
              <BotonExterno href={complejo?.mapaUrl || `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`} target="_blank" variante="secundario" icono={Navigation}>
                Abrir en Maps
              </BotonExterno>
              <BotonExterno href={enlaceWhatsapp(complejo?.whatsapp)} target="_blank" variante="whatsapp" icono={MessageCircle}>
                WhatsApp
              </BotonExterno>
            </div>
          </div>
          <div className="mapa">
            <span className="absolute inset-0 grid place-items-center text-verde" aria-hidden>
              <MapPin className="ico ico-32" />
            </span>
            <iframe title="Mapa del Complejo Deportivo Los Alcanfores" src={`https://www.google.com/maps?q=${lat},${lng}&z=16&output=embed`} loading="lazy" referrerPolicy="no-referrer-when-downgrade" />
          </div>
        </div>
      </section>

      {/* ------------------------------ preguntas frecuentes ------------------------------ */}
      <section className="bg-superficie-suave border-t border-borde py-8 lg:py-14" aria-labelledby="h-faq">
        <div className="contenedor grid gap-5 lg:grid-cols-[1fr_1.6fr] lg:gap-10">
          <div className="grid gap-4 content-start">
            <CabSeccion id="h-faq" kicker="Ayuda" titulo="Preguntas frecuentes" />
            <Ilustracion id="IL-16-consulta" className="hidden lg:block w-[220px] h-[220px]" />
          </div>
          <div className="faq">
            {FAQ.map((f) => (
              <details key={f.p}>
                <summary>
                  <HelpCircle className="ico ico-20 text-verde" aria-hidden />
                  {f.p}
                  <ChevronDown className="ico ico-18 ico-flecha" aria-hidden />
                </summary>
                <p className="faq-resp">{f.r}</p>
              </details>
            ))}
          </div>
        </div>
      </section>

      {/* ------------------------------ cierre ------------------------------ */}
      {!sinCanchas && (
        <section className="relative overflow-hidden bg-profundo sobre-oscuro" aria-labelledby="h-cierre">
          <PatronCancha className="absolute -left-16 -top-10 w-[380px] text-white opacity-10 pointer-events-none lg:w-[560px] lg:left-auto lg:-right-10" />
          <div className="contenedor relative flex flex-col items-start gap-4 py-8 md:flex-row md:items-center md:justify-between lg:py-10">
            <div className="flex items-center gap-3">
              <span className="grid place-items-center w-12 h-12 rounded-pill bg-white text-profundo shrink-0">
                <IconoDeporte deporte="FUTBOL_5" className="ico-24" />
              </span>
              <h2 id="h-cierre" className="t-h2 text-white">
                ¿Listo para jugar?
              </h2>
            </div>
            <div className="w-full md:w-auto">
              <BotonEnlace to="/reservar" icono={CalendarPlus} variante="claro" tamano="lg" bloque>
                Reservar
              </BotonEnlace>
            </div>
          </div>
        </section>
      )}
    </>
  );
}
