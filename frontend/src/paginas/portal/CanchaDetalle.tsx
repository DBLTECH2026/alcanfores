import { useEffect, useRef, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  ArrowLeft,
  CalendarPlus,
  ChevronRight,
  CloudRain,
  Fingerprint,
  Images,
  Layers,
  Lightbulb,
  MessageSquarePlus,
  Moon,
  RefreshCw,
  Ruler,
  Send,
  Star,
  Sun,
  Ticket,
  Users,
} from 'lucide-react';
import { catalogoApi } from '@/api/servicios';
import { esErrorApi } from '@/api/cliente';
import type { CanchaDetalle as TCancha, OrdenResenas } from '@/api/tipos';
import { Boton, BotonEnlace } from '@/componentes/ui/Boton';
import { AreaTexto, CampoTexto } from '@/componentes/ui/Campo';
import { Alerta } from '@/componentes/ui/Estados';
import { Cargando, Paginacion, Vacio } from '@/componentes/ui/Datos';
import { Estrellas, EstrellasEntrada } from '@/componentes/ui/Estrellas';
import { IconoDeporte, Ilustracion, ilustracionDeporte } from '@/componentes/ui/Marca';
import { VisorImagen } from '@/componentes/ui/VisorImagen';
import { ItemResena, ResumenCalificacion, iconoCaracteristica } from '@/componentes/portal/Canchas';
import { useCarga } from '@/hooks/useCarga';
import { useComplejo } from '@/hooks/useDatosPublicos';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { useToast } from '@/contexto/ToastContext';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { esquemaResena, type ResenaEntrada } from '@/esquemas/otros';
import { fmtHora12 } from '@/utilidades/fecha';
import { fmtSoles, normalizarCodigo } from '@/utilidades/formato';
import { cx } from '@/utilidades/cx';

const TAM_PAGINA = 5;

// ------------------------------ Galería ------------------------------
function Galeria({ cancha }: { cancha: TCancha }) {
  const fotos = cancha.fotos.length ? [...cancha.fotos].sort((a, b) => Number(b.principal) - Number(a.principal)) : [];
  const [activa, setActiva] = useState(0);
  const [visor, setVisor] = useState<number | null>(null);
  if (!fotos.length) {
    return (
      <div className="galeria-principal grid place-items-center cursor-default">
        <Ilustracion id={ilustracionDeporte(cancha.deporte)} className="w-[60%] h-auto" />
      </div>
    );
  }
  const actual = fotos[activa] ?? fotos[0];
  return (
    <div className="galeria">
      <button type="button" className="galeria-principal" onClick={() => setVisor(activa)} aria-label={`Ver fotos de ${cancha.nombre} en pantalla completa`}>
        <img src={actual?.url} alt={actual?.leyenda ?? cancha.nombre} />
        <span className="galeria-contador">
          <Images className="ico ico-16" aria-hidden />
          {`${activa + 1} / ${fotos.length}`}
        </span>
      </button>
      {fotos.length > 1 && (
        <div className="galeria-miniaturas" role="tablist" aria-label="Fotos de la cancha">
          {fotos.slice(0, 4).map((f, i) => (
            <button key={f.id} type="button" role="tab" aria-selected={i === activa} aria-label={f.leyenda ?? `Foto ${i + 1}`} className={cx('galeria-mini', i === activa && 'activa')} onClick={() => setActiva(i)}>
              <img src={f.url} alt="" loading="lazy" />
            </button>
          ))}
        </div>
      )}
      {visor !== null && (
        <VisorImagen
          imagenes={fotos.map((f) => ({ url: f.url, leyenda: f.leyenda }))}
          indice={visor}
          alCambiar={(i) => {
            setVisor(i);
            setActiva(i);
          }}
          alCerrar={() => setVisor(null)}
          titulo={`Fotos de ${cancha.nombre}`}
        />
      )}
    </div>
  );
}

// ------------------------------ Formulario de reseña ------------------------------
function FormResena({ cancha, codigoInicial, alPublicar }: { cancha: TCancha; codigoInicial: string; alPublicar: () => void }) {
  const toast = useToast();
  const [aviso, setAviso] = useState<{ tipo: 'error' | 'exito' | 'advertencia'; texto: string } | null>(null);
  const {
    register,
    handleSubmit,
    control,
    setError,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ResenaEntrada>({
    resolver: zodResolver(esquemaResena),
    mode: 'onTouched',
    defaultValues: { codigoReserva: codigoInicial, dni: '', puntuacion: 0, comentario: '' },
  });
  const comentario = watch('comentario') ?? '';

  const enviar = handleSubmit(async (d) => {
    setAviso(null);
    try {
      await catalogoApi.publicarResena(cancha.id, { codigoReserva: d.codigoReserva.toUpperCase(), dni: d.dni, puntuacion: d.puntuacion, comentario: d.comentario.trim() });
      toast.exito('¡Gracias por tu reseña!');
      setAviso({ tipo: 'exito', texto: 'Tu reseña ya está publicada.' });
      reset({ codigoReserva: '', dni: '', puntuacion: 0, comentario: '' });
      alPublicar();
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['codigoReserva', 'dni', 'puntuacion', 'comentario']);
      if (!aplicados) setAviso({ tipo: esErrorApi(e) && e.codigo === 'RESENA_DUPLICADA' ? 'advertencia' : 'error', texto: esErrorApi(e) ? e.message : 'No se pudo enviar. Intenta de nuevo.' });
    }
  });

  return (
    <form onSubmit={enviar} noValidate className="grid gap-4">
      <p className="t-sec">Solo con tu código de reserva y tu DNI, después de jugar.</p>
      <div className="form-grid dos">
        <CampoTexto
          label="Código de reserva"
          requerido
          icono={Ticket}
          placeholder="ALC-XXXXXX"
          autoComplete="off"
          maxLength={10}
          className="uppercase-input"
          error={errors.codigoReserva?.message}
          {...register('codigoReserva', { setValueAs: (v: string) => normalizarCodigo(v ?? '') })}
        />
        <CampoTexto label="DNI" requerido icono={Fingerprint} inputMode="numeric" maxLength={8} autoComplete="off" error={errors.dni?.message} {...register('dni')} />
      </div>
      <Controller name="puntuacion" control={control} render={({ field }) => <EstrellasEntrada valor={field.value ?? 0} alCambiar={(v) => field.onChange(v)} error={errors.puntuacion?.message} />} />
      <AreaTexto label="Comentario" requerido rows={4} maxLength={500} placeholder="¿Cómo estuvo la cancha, la iluminación, la atención?" contador={{ actual: comentario.length, max: 500 }} error={errors.comentario?.message} {...register('comentario')} />
      {aviso && <Alerta tipo={aviso.tipo}>{aviso.texto}</Alerta>}
      <div>
        <Boton type="submit" icono={Send} cargando={isSubmitting} textoCargando="Enviando…">
          Publicar reseña
        </Boton>
      </div>
    </form>
  );
}

// ------------------------------ Página ------------------------------
export default function CanchaDetalle() {
  const { idOSlug = '' } = useParams();
  const [params] = useSearchParams();
  const codigoResena = params.get('resena') ? normalizarCodigo(params.get('resena') ?? '') : '';
  const { datos: complejo } = useComplejo();
  const det = useCarga(() => catalogoApi.cancha(idOSlug), idOSlug);
  const cancha = det.datos;
  useTituloPagina(cancha?.nombre ?? 'Cancha');

  const [orden, setOrden] = useState<OrdenResenas>('recientes');
  const [pagina, setPagina] = useState(0);
  const [form, setForm] = useState(Boolean(codigoResena));
  const resenas = useCarga(() => catalogoApi.resenas(cancha?.id ?? 0, { page: pagina, size: TAM_PAGINA, orden }), cancha ? `${cancha.id}-${pagina}-${orden}` : null);
  const bloqueForm = useRef<HTMLElement>(null);

  useEffect(() => {
    if (codigoResena && cancha) {
      setForm(true);
      window.setTimeout(() => bloqueForm.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 150);
    }
  }, [codigoResena, cancha]);

  if (det.cargando && !det.datos) return <Cargando texto="Cargando cancha…" />;
  if (det.error || !cancha) {
    const noExiste = esErrorApi(det.error) && det.error.status === 404;
    return (
      <div className="contenedor py-10">
        <Vacio
          ilustracion={noExiste ? 'IL-18-404-fuera-de-juego' : 'IL-19-sin-conexion'}
          titulo={noExiste ? 'Esa cancha no existe' : 'Sin conexión con el servidor'}
          acciones={
            noExiste ? (
              <BotonEnlace to="/canchas" icono={ArrowLeft} variante="secundario">
                Ver canchas
              </BotonEnlace>
            ) : (
              <Boton variante="secundario" icono={RefreshCw} onClick={det.recargar}>
                Reintentar
              </Boton>
            )
          }
        />
      </div>
    );
  }

  const h = complejo?.horario;
  const recargarTodo = () => {
    det.recargar();
    setPagina(0);
    resenas.recargar();
  };

  return (
    <div className="contenedor grid gap-6 py-5 lg:py-8">
      <nav className="flex items-center gap-1.5 text-[13px] text-texto-suave" aria-label="Migas">
        <Link to="/canchas" className="hover:underline">
          Canchas
        </Link>
        <ChevronRight className="ico ico-14" aria-hidden />
        <span aria-current="page" className="text-noche font-semibold">
          {cancha.nombre}
        </span>
      </nav>

      <div className="grid gap-6 lg:grid-cols-[1.45fr_1fr] lg:gap-10 lg:items-start">
        {/* ---------- columna principal ---------- */}
        <div className="grid gap-7 min-w-0">
          <Galeria cancha={cancha} />

          <section className="grid gap-3" aria-labelledby="h-desc">
            <h2 id="h-desc" className="t-h2">
              Sobre la cancha
            </h2>
            <p className="text-[15.5px] leading-relaxed text-noche whitespace-pre-line">{cancha.descripcionLarga || cancha.descripcion}</p>
            <ul className="flex flex-wrap gap-x-5 gap-y-2 t-sec">
              <li className="inline-flex items-center gap-1.5">
                <Layers className="ico ico-16 text-verde" aria-hidden />
                {cancha.superficie}
              </li>
              <li className="inline-flex items-center gap-1.5">
                <Users className="ico ico-16 text-verde" aria-hidden />
                {`${cancha.capacidad} jugadores`}
              </li>
              {cancha.medidas && (
                <li className="inline-flex items-center gap-1.5">
                  <Ruler className="ico ico-16 text-verde" aria-hidden />
                  {cancha.medidas}
                </li>
              )}
              {cancha.techada && (
                <li className="inline-flex items-center gap-1.5">
                  <CloudRain className="ico ico-16 text-verde" aria-hidden />
                  Techada
                </li>
              )}
              {cancha.iluminacionLed && (
                <li className="inline-flex items-center gap-1.5">
                  <Lightbulb className="ico ico-16 text-verde" aria-hidden />
                  Luz LED
                </li>
              )}
            </ul>
          </section>

          {cancha.caracteristicas.length > 0 && (
            <section className="grid gap-3" aria-labelledby="h-carac">
              <h2 id="h-carac" className="t-h2">
                Características
              </h2>
              <ul className="caracteristicas">
                {cancha.caracteristicas.map((c) => {
                  const I = iconoCaracteristica(c);
                  return (
                    <li key={c} className="caracteristica">
                      <span className="caracteristica-ico">
                        <I className="ico ico-18" aria-hidden />
                      </span>
                      {c}
                    </li>
                  );
                })}
              </ul>
            </section>
          )}

          {/* ---------- reseñas ---------- */}
          <section className="grid gap-5" aria-labelledby="h-resenas">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div className="grid gap-1">
                <span className="kicker kicker-raya">Reseñas verificadas</span>
                <h2 id="h-resenas" className="t-h2">
                  Lo que dicen los jugadores
                </h2>
              </div>
              {!form && (
                <Boton variante="secundario" icono={MessageSquarePlus} onClick={() => setForm(true)}>
                  Calificar
                </Boton>
              )}
            </div>
            <div className="grid gap-6 md:grid-cols-[260px_1fr] md:gap-8">
              <div className="card card-pad self-start">
                <ResumenCalificacion promedio={cancha.resumenResenas.promedio} total={cancha.resumenResenas.total} distribucion={cancha.resumenResenas.distribucion} />
              </div>
              <div className="grid gap-2 content-start min-w-0">
                <div className="flex items-center justify-between gap-3">
                  <span className="t-sec tnum">{resenas.datos ? `${resenas.datos.totalElementos} reseñas` : ''}</span>
                  <label className="inline-flex items-center gap-2 text-[13.5px] font-semibold">
                    Ordenar
                    <span className="control control-chevron !min-h-[40px] !rounded-10">
                      <select
                        value={orden}
                        onChange={(e) => {
                          setOrden(e.target.value as OrdenResenas);
                          setPagina(0);
                        }}
                        className="!text-[14px]"
                      >
                        <option value="recientes">Más recientes</option>
                        <option value="mejor">Mejor calificadas</option>
                        <option value="peor">Peor calificadas</option>
                      </select>
                    </span>
                  </label>
                </div>
                {resenas.cargando && !resenas.datos ? (
                  <Cargando texto="Cargando reseñas…" className="py-8" />
                ) : resenas.datos && resenas.datos.contenido.length > 0 ? (
                  <>
                    <div aria-busy={resenas.cargando || undefined}>
                      {resenas.datos.contenido.map((r) => (
                        <ItemResena key={r.id} resena={r} />
                      ))}
                    </div>
                    <Paginacion pagina={resenas.datos.pagina} totalPaginas={resenas.datos.totalPaginas} totalElementos={resenas.datos.totalElementos} tamanio={resenas.datos.tamanio} etiqueta="reseñas" alCambiar={setPagina} />
                  </>
                ) : (
                  <Vacio ilustracion="IL-24-sin-resultados" titulo="Aún sin reseñas" texto="Juega y sé el primero en calificarla." compacto />
                )}
              </div>
            </div>

            {form && (
              <section ref={bloqueForm} className="card card-pad grid gap-3 scroll-mt-24" aria-labelledby="h-calificar">
                <div className="flex items-center gap-3">
                  <span className="grid place-items-center w-11 h-11 rounded-12 bg-amarillo-tinte text-noche shrink-0">
                    <Star className="ico ico-20" aria-hidden />
                  </span>
                  <h3 id="h-calificar" className="t-h3">
                    Califica tu experiencia
                  </h3>
                </div>
                <FormResena cancha={cancha} codigoInicial={codigoResena} alPublicar={recargarTodo} />
              </section>
            )}
          </section>
        </div>

        {/* ---------- columna lateral ---------- */}
        <aside className="order-first lg:order-none lg:sticky lg:top-[92px] grid gap-4">
          <div className="card overflow-hidden">
            <div className="p-[18px] grid gap-3">
              <span className="badge b-noche justify-self-start">
                <IconoDeporte deporte={cancha.deporte} />
                <span>{cancha.deporteTexto}</span>
              </span>
              <h1 className="t-h1">{cancha.nombre}</h1>
              <a href="#h-resenas" className="inline-flex items-center gap-2 text-[14px] hover:underline">
                <Estrellas valor={cancha.resumenResenas.promedio} tamano={18} />
                <b className="tnum">{cancha.resumenResenas.total ? cancha.resumenResenas.promedio.toFixed(1) : 'Nueva'}</b>
                <span className="t-sec tnum">{`(${cancha.resumenResenas.total} reseñas)`}</span>
              </a>
              <p className="t-sec">{cancha.descripcion}</p>
            </div>
            <div className="grid grid-cols-2 border-t border-borde">
              <div className="p-4 grid gap-1 border-r border-borde">
                <span className="inline-flex items-center gap-1.5 font-semibold text-[13.5px]">
                  <Sun className="ico ico-16 text-amarillo-ocre" aria-hidden />
                  Día
                </span>
                <span className="t-mini tnum">{h ? `${fmtHora12(h.primerTurno)} – ${fmtHora12(h.inicioNoche)}` : ''}</span>
                <span className="desde-val">
                  {fmtSoles(cancha.precioDia, { decimales: 0 })}
                  <small>/ hora</small>
                </span>
              </div>
              <div className="p-4 grid gap-1">
                <span className="inline-flex items-center gap-1.5 font-semibold text-[13.5px]">
                  <Moon className="ico ico-16" aria-hidden />
                  Noche
                </span>
                <span className="t-mini tnum">{h ? `${fmtHora12(h.inicioNoche)} – ${fmtHora12(h.ultimoTurno)}` : ''}</span>
                <span className="desde-val">
                  {fmtSoles(cancha.precioNoche, { decimales: 0 })}
                  <small>/ hora</small>
                </span>
              </div>
            </div>
            <div className="p-[18px] pt-4 border-t border-borde grid gap-2 bg-superficie-suave">
              <BotonEnlace to={`/reservar?cancha=${cancha.id}`} icono={CalendarPlus} tamano="lg" bloque>
                Reservar esta cancha
              </BotonEnlace>
              <span className="t-mini text-center">{`Adelanto ${complejo?.reglas.adelantoPct ?? 50} % · el saldo, al llegar`}</span>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
