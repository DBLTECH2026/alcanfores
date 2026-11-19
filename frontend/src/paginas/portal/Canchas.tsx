import { CalendarPlus, Lightbulb, Moon, RefreshCw, Star, Sun } from 'lucide-react';
import { Boton, BotonEnlace } from '@/componentes/ui/Boton';
import { IconoDeporte } from '@/componentes/ui/Marca';
import { Tabla, Vacio } from '@/componentes/ui/Datos';
import { TarjetaCancha, TarjetaCanchaEsqueleto } from '@/componentes/portal/Canchas';
import { CondicionesIconos } from '@/componentes/portal/Asistente';
import { useCanchas, useComplejo } from '@/hooks/useDatosPublicos';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { fmtHora12 } from '@/utilidades/fecha';
import { fmtSoles } from '@/utilidades/formato';
import type { CanchaResumen } from '@/api/tipos';

export default function Canchas() {
  useTituloPagina('Canchas y tarifas');
  const canchas = useCanchas();
  const { datos: complejo } = useComplejo();
  const lista = canchas.datos ?? [];
  const h = complejo?.horario;

  return (
    <div className="contenedor grid gap-6 py-6 lg:gap-8 lg:py-10">
      <header className="grid gap-2">
        <span className="kicker kicker-raya">Fútbol 5 · 6 · 7 · Vóley · Básquet</span>
        <h1 className="t-h1">Canchas y tarifas</h1>
        <p className="t-sec flex flex-wrap gap-x-4 gap-y-1">
          <span className="inline-flex items-center gap-1.5">
            <Sun className="ico ico-14 text-amarillo-ocre" aria-hidden />
            {h ? `Día ${fmtHora12(h.primerTurno)} – ${fmtHora12(h.inicioNoche)}` : 'Día'}
          </span>
          <span className="inline-flex items-center gap-1.5">
            <Moon className="ico ico-14" aria-hidden />
            {h ? `Noche ${fmtHora12(h.inicioNoche)} – ${fmtHora12(h.ultimoTurno)}` : 'Noche'}
          </span>
          <span>Precios por hora · en soles</span>
        </p>
      </header>

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
      ) : canchas.cargando ? (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          {[1, 2, 3, 4].map((n) => (
            <TarjetaCanchaEsqueleto key={n} />
          ))}
        </div>
      ) : lista.length === 0 ? (
        <div className="card">
          <Vacio ilustracion="IL-25-sin-canchas" titulo="Sin canchas por ahora" compacto />
        </div>
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4 lg:gap-5">
            {lista.map((c) => (
              <TarjetaCancha key={c.id} cancha={c} />
            ))}
          </div>

          <section className="grid gap-4 lg:grid-cols-[1.5fr_1fr] lg:gap-8 lg:items-start" aria-labelledby="h-tarifas">
            <div className="grid gap-3">
              <h2 id="h-tarifas" className="t-h2">
                Tarifas por hora
              </h2>
              <Tabla<CanchaResumen>
                etiqueta="Tarifas por cancha"
                filas={lista}
                claveFila={(c) => c.id}
                columnas={[
                  {
                    clave: 'cancha',
                    titulo: 'Cancha',
                    celda: (c) => (
                      <span className="inline-flex items-center gap-2.5 font-semibold">
                        <span className="chip-cancha-ico !w-8 !h-8">
                          <IconoDeporte deporte={c.deporte} className="ico-18" />
                        </span>
                        <span>
                          {c.nombre}
                          <span className="sub">{c.deporteTexto}</span>
                        </span>
                      </span>
                    ),
                  },
                  { clave: 'cap', titulo: 'Jugadores', alinear: 'centro', celda: (c) => <span className="tnum">{c.capacidad}</span> },
                  { clave: 'dia', titulo: 'Día', alinear: 'der', celda: (c) => <b className="tnum">{fmtSoles(c.precioDia)}</b> },
                  { clave: 'noche', titulo: 'Noche', alinear: 'der', celda: (c) => <b className="tnum">{fmtSoles(c.precioNoche)}</b> },
                  {
                    clave: 'calif',
                    titulo: 'Calificación',
                    alinear: 'der',
                    celda: (c) =>
                      c.calificacion.total ? (
                        <span className="inline-flex items-center gap-1 tnum">
                          <Star className="ico ico-14 text-amarillo fill-amarillo" aria-hidden />
                          {c.calificacion.promedio.toFixed(1)}
                        </span>
                      ) : (
                        '—'
                      ),
                  },
                ]}
              />
              <p className="t-mini inline-flex items-center gap-1.5">
                <Lightbulb className="ico ico-14" aria-hidden />
                Todas con iluminación LED, camerinos y estacionamiento.
              </p>
            </div>
            <div className="grid gap-4">
              <CondicionesIconos adelantoPct={complejo?.reglas.adelantoPct} devolucionHoras={complejo?.reglas.devolucionHoras} />
              <BotonEnlace to="/reservar" icono={CalendarPlus} tamano="lg" bloque>
                Reservar
              </BotonEnlace>
            </div>
          </section>
        </>
      )}
    </div>
  );
}
