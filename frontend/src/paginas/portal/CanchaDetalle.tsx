import { useEffect, useRef, useState } from 'react';
import { useLocation, useParams, useSearchParams } from 'react-router-dom';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, LogIn, MessageSquarePlus, RefreshCw, Send, Star, UserPlus } from 'lucide-react';
import { catalogoApi, cuentaApi } from '@/api/servicios';
import { esErrorApi } from '@/api/cliente';
import type { CanchaDetalle as TCancha, OrdenResenas, ReservaDeCliente } from '@/api/tipos';
import { Boton, BotonEnlace } from '@/componentes/ui/Boton';
import { AreaTexto } from '@/componentes/ui/Campo';
import { Alerta } from '@/componentes/ui/Estados';
import { Cargando, Paginacion, Segmentado, Vacio } from '@/componentes/ui/Datos';
import { EstrellasEntrada } from '@/componentes/ui/Estrellas';
import { ItemResena } from '@/componentes/portal/Canchas';
import { FichaCancha, MigasCancha } from '@/componentes/portal/FichaCancha';
import { useCarga } from '@/hooks/useCarga';
import { useComplejo } from '@/hooks/useDatosPublicos';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { useToast } from '@/contexto/ToastContext';
import { useCuenta } from '@/contexto/CuentaContext';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { esquemaResenaCuenta, type ResenaCuentaEntrada } from '@/esquemas/otros';
import { fmtFecha, textoHorario } from '@/utilidades/fecha';
import { normalizarCodigo } from '@/utilidades/formato';

const TAM_PAGINA = 5;

// ------------------------------ Reseña (v2: exige sesión de cliente) ------------------------------
function FormResena({ cancha, codigoInicial, alPublicar }: { cancha: TCancha; codigoInicial: string; alPublicar: () => void }) {
  const toast = useToast();
  const { conectado } = useCuenta();
  const { pathname, search } = useLocation();
  const [aviso, setAviso] = useState<{ tipo: 'error' | 'exito' | 'advertencia'; texto: string } | null>(null);
  const [publicada, setPublicada] = useState(false);
  // Partidos jugados en ESTA cancha que aún no tienen reseña.
  const elegibles = useCarga(
    async () => (await cuentaApi.reservas({ size: 50 })).contenido.filter((r: ReservaDeCliente) => r.cancha.id === cancha.id && r.puedeResenar && !r.resenada),
    conectado ? `elegibles-${cancha.id}` : null,
  );
  const {
    handleSubmit,
    control,
    setError,
    setValue,
    reset,
    watch,
    register,
    formState: { errors, isSubmitting },
  } = useForm<ResenaCuentaEntrada>({ resolver: zodResolver(esquemaResenaCuenta), mode: 'onTouched', defaultValues: { codigoReserva: codigoInicial, puntuacion: 0, comentario: '' } });
  const comentario = watch('comentario') ?? '';
  const codigo = watch('codigoReserva');
  useEffect(() => {
    const primera = elegibles.datos?.[0];
    if (!codigo && primera) setValue('codigoReserva', primera.codigoReserva);
  }, [elegibles.datos, codigo, setValue]);

  if (!conectado) {
    const volver = encodeURIComponent(pathname + search);
    return (
      <div className="grid gap-3 justify-items-start">
        <p className="t-sec">Ingresa con tu cuenta para calificar: así sabemos que jugaste aquí.</p>
        <div className="flex flex-wrap gap-2">
          <BotonEnlace to={`/ingresar?volver=${volver}`} icono={LogIn}>
            Ingresar
          </BotonEnlace>
          <BotonEnlace to={`/crear-cuenta?volver=${volver}`} variante="secundario" icono={UserPlus}>
            Crear cuenta
          </BotonEnlace>
        </div>
      </div>
    );
  }

  const enviar = handleSubmit(async (d) => {
    setAviso(null);
    try {
      await catalogoApi.publicarResena(cancha.id, { codigoReserva: d.codigoReserva.toUpperCase(), puntuacion: d.puntuacion, comentario: d.comentario.trim() });
      toast.exito('¡Gracias por tu reseña!');
      setPublicada(true);
      reset({ codigoReserva: '', puntuacion: 0, comentario: '' });
      elegibles.recargar();
      alPublicar();
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['codigoReserva', 'puntuacion', 'comentario']);
      if (!aplicados) setAviso({ tipo: esErrorApi(e) && e.codigo === 'RESENA_DUPLICADA' ? 'advertencia' : 'error', texto: esErrorApi(e) ? e.message : 'No se pudo enviar. Intenta de nuevo.' });
    }
  });

  if (publicada) {
    return (
      <Alerta tipo="exito" titulo="¡Gracias por tu reseña!" acciones={(elegibles.datos?.length ?? 0) > 0 ? <Boton variante="secundario" tamano="sm" onClick={() => setPublicada(false)}>Calificar otro partido</Boton> : undefined}>
        Ya está publicada en esta cancha.
      </Alerta>
    );
  }
  if (elegibles.cargando && !elegibles.datos) return <Cargando texto="Buscando tus partidos…" className="py-6" />;
  const lista = elegibles.datos ?? [];
  if (!lista.length && !codigoInicial) {
    return <Vacio ilustracion="IL-15-partido-jugado" titulo="Nada por calificar aquí" texto="Cuando juegues en esta cancha, podrás calificarla." compacto />;
  }
  return (
    <form onSubmit={enviar} noValidate className="grid gap-4">
      {lista.length > 1 ? (
        <Segmentado
          etiqueta="Partido"
          valor={codigo}
          alCambiar={(v) => setValue('codigoReserva', v, { shouldValidate: true })}
          opciones={lista.map((r) => ({ valor: r.codigoReserva, texto: `${fmtFecha(r.fecha, 'corta')} · ${r.horarioTexto ?? textoHorario([r])}` }))}
        />
      ) : lista[0] ? (
        <p className="t-sec">{`Tu partido del ${fmtFecha(lista[0].fecha, 'larga')} · ${lista[0].horarioTexto ?? textoHorario([lista[0]])}`}</p>
      ) : (
        <p className="t-sec tnum">{`Reserva ${codigo}`}</p>
      )}
      <input type="hidden" {...register('codigoReserva', { setValueAs: (v: string) => normalizarCodigo(v ?? '') })} />
      {errors.codigoReserva?.message && <Alerta tipo="error">{errors.codigoReserva.message}</Alerta>}
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
  const [form, setForm] = useState(Boolean(codigoResena) || params.get('calificar') === '1');
  const resenas = useCarga(() => catalogoApi.resenas(cancha?.id ?? 0, { page: pagina, size: TAM_PAGINA, orden }), cancha ? `${cancha.id}-${pagina}-${orden}` : null);
  const bloqueForm = useRef<HTMLElement>(null);

  useEffect(() => {
    if (form && cancha) window.setTimeout(() => bloqueForm.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 150);
  }, [form, cancha]);

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

  const recargarTodo = () => {
    det.recargar();
    setPagina(0);
    resenas.recargar();
  };

  const listaResenas =
    resenas.cargando && !resenas.datos ? (
      <Cargando texto="Cargando reseñas…" className="py-8" />
    ) : resenas.datos && resenas.datos.contenido.length > 0 ? (
      <>
        <div className="flex items-center justify-between gap-3">
          <span className="t-sec tnum">{`${resenas.datos.totalElementos} reseñas`}</span>
          <label className="inline-flex items-center gap-2 text-[13.5px] font-semibold">
            Ordenar
            <span className="control control-chevron !min-h-[40px] !rounded-10">
              <select
                value={orden}
                onChange={(e) => {
                  setOrden(e.target.value as OrdenResenas);
                  setPagina(0);
                }}
                className="text-[16px] md:!text-[14px]"
              >
                <option value="recientes">Más recientes</option>
                <option value="mejor">Mejor calificadas</option>
                <option value="peor">Peor calificadas</option>
              </select>
            </span>
          </label>
        </div>
        <div aria-busy={resenas.cargando || undefined}>
          {resenas.datos.contenido.map((r) => (
            <ItemResena key={r.id} resena={r} />
          ))}
        </div>
        <Paginacion pagina={resenas.datos.pagina} totalPaginas={resenas.datos.totalPaginas} totalElementos={resenas.datos.totalElementos} tamanio={resenas.datos.tamanio} etiqueta="reseñas" alCambiar={setPagina} />
      </>
    ) : (
      <Vacio ilustracion="IL-24-sin-resultados" titulo="Aún sin reseñas" texto="Juega y sé el primero en calificarla." compacto />
    );

  return (
    <div className="contenedor grid gap-6 py-5 lg:py-8">
      <MigasCancha nombre={cancha.nombre} />
      <FichaCancha
        cancha={cancha}
        complejo={complejo}
        resenas={listaResenas}
        accionResenas={
          !form ? (
            <Boton variante="secundario" icono={MessageSquarePlus} onClick={() => setForm(true)}>
              Calificar
            </Boton>
          ) : undefined
        }
        formResena={
          form ? (
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
          ) : undefined
        }
      />
    </div>
  );
}
