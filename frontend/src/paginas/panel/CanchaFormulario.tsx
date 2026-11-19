import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { z } from 'zod';
import { ArrowDown, ArrowUp, ImagePlus, Images, Lightbulb, Moon, Plus, Save, Star, Sun, Trash2, Upload, Warehouse, X } from 'lucide-react';
import { canchasAdminApi } from '@/api/admin';
import type { CanchaAdmin, FotoAdmin } from '@/api/admin/tipos';
import { esErrorApi } from '@/api/cliente';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { useToast } from '@/contexto/ToastContext';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { ErrorCarga, ModalConfirmar } from '@/componentes/panel/Herramientas';
import { Boton } from '@/componentes/ui/Boton';
import { AreaTexto, CampoTexto, Interruptor, Selector } from '@/componentes/ui/Campo';
import { Cargando, Vacio } from '@/componentes/ui/Datos';
import { Alerta, Insignia } from '@/componentes/ui/Estados';
import { VisorImagen } from '@/componentes/ui/VisorImagen';
import { medirImagen, tipoPorBytes } from '@/utilidades/documentos';
import { esquemaCancha, esquemaCaracteristica, esquemaLeyenda, MSJ_PANEL } from './esquemasPanel';

export type Entrada = z.input<typeof esquemaCancha>;
export type Salida = z.output<typeof esquemaCancha>;
export const DEPORTES = [
  { valor: 'FUTBOL_5', texto: 'Fútbol 5' },
  { valor: 'FUTBOL_6', texto: 'Fútbol 6' },
  { valor: 'FUTBOL_7', texto: 'Fútbol 7' },
  { valor: 'VOLEY', texto: 'Vóley' },
  { valor: 'BASQUET', texto: 'Básquet' },
  { valor: 'MULTIUSO', texto: 'Multiusos (vóley y básquet)' },
];
export const CAMPOS = ['nombre', 'deporte', 'superficie', 'capacidad', 'descripcion', 'descripcionLarga', 'medidas', 'caracteristicas', 'precioDia', 'precioNoche', 'orden', 'estado', 'techada', 'iluminacionLed'];
const VACIA: Entrada = {
  nombre: '',
  deporte: 'FUTBOL_5',
  superficie: '',
  capacidad: '10',
  descripcion: '',
  descripcionLarga: '',
  techada: true,
  iluminacionLed: true,
  medidas: '',
  caracteristicas: [],
  precioDia: '',
  precioNoche: '',
  orden: '5',
  estado: 'ACTIVA',
};
export const deCancha = (c: CanchaAdmin): Entrada => ({
  nombre: c.nombre,
  deporte: c.deporte,
  superficie: c.superficie,
  capacidad: String(c.capacidad),
  descripcion: c.descripcion,
  descripcionLarga: c.descripcionLarga ?? '',
  techada: c.techada,
  iluminacionLed: c.iluminacionLed,
  medidas: c.medidas ?? '',
  caracteristicas: c.caracteristicas,
  precioDia: c.precioDia.toFixed(2),
  precioNoche: c.precioNoche.toFixed(2),
  orden: String(c.orden),
  estado: c.estado,
});

/** Crear una cancha (v2: editarla es la ficha de 3 apartados en ./cancha/FichaCanchaAdmin). Sirve también para editar por id. */
export default function CanchaFormulario({ id }: { id?: number }) {
  useTituloPagina(id ? 'Editar cancha' : 'Nueva cancha');
  const navigate = useNavigate();
  const toast = useToast();
  const [version, setVersion] = useState(0);
  const cancha = useCarga(() => canchasAdminApi.obtener(id ?? 0), id ? `cancha-${id}-${version}` : null);
  const [nuevaCaract, setNuevaCaract] = useState('');
  const [errorCaract, setErrorCaract] = useState<string | null>(null);
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setValue,
    setError,
    reset,
    watch,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<Entrada>({ resolver: zodResolver(esquemaCancha), mode: 'onTouched', defaultValues: VACIA });
  useEffect(() => {
    if (cancha.datos) reset(deCancha(cancha.datos));
  }, [cancha.datos, reset]);
  useEffect(() => {
    if (window.location.hash === '#fotos' && cancha.datos) document.getElementById('fotos')?.scrollIntoView({ behavior: 'smooth' });
  }, [cancha.datos]);
  const v = watch();
  const caracts = v.caracteristicas ?? [];

  const agregar = () => {
    const r = esquemaCaracteristica.safeParse(nuevaCaract);
    if (!r.success) return setErrorCaract(MSJ_PANEL.canchaCaracteristica);
    if (caracts.length >= 12) return setErrorCaract(MSJ_PANEL.canchaCaracteristicas);
    setErrorCaract(null);
    setValue('caracteristicas', [...caracts, r.data], { shouldDirty: true, shouldValidate: true });
    setNuevaCaract('');
  };

  const guardar = handleSubmit(async (entrada) => {
    const d = entrada as unknown as Salida;
    setErrorGeneral(null);
    const cuerpo = { ...d, medidas: d.medidas || undefined, descripcionLarga: d.descripcionLarga || undefined };
    try {
      const r = id ? await canchasAdminApi.editar(id, cuerpo) : await canchasAdminApi.crear(cuerpo);
      toast.exito(id ? 'Cancha guardada' : 'Cancha creada');
      if (id) {
        cancha.setDatos(r);
        reset(deCancha(r));
      } else navigate(`/admin/canchas/${r.id}#fotos`, { replace: true });
    } catch (e) {
      const { aplicados, sinUbicar } = aplicarErroresServidor(e, setError, CAMPOS, Object.fromEntries(Array.from({ length: 12 }, (_, i) => [`caracteristicas[${i}]`, 'caracteristicas'])));
      if (!aplicados || sinUbicar.length) setErrorGeneral(esErrorApi(e) ? e.message : 'No se pudo guardar.');
    }
  });

  if (id && cancha.error) return <ErrorCarga alReintentar={cancha.recargar} />;
  if (id && !cancha.datos) return <Cargando texto="Cargando cancha…" />;

  return (
    <>
      <CabeceraPagina
        titulo={id ? cancha.datos?.nombre ?? 'Cancha' : 'Nueva cancha'}
        subtitulo={id ? 'Datos, precios y fotos' : 'Datos y precios; luego agregas las fotos'}
        volver="/admin/canchas"
        acciones={
          <Boton icono={Save} cargando={isSubmitting} textoCargando="Guardando…" disabled={Boolean(id) && !isDirty} onClick={guardar}>
            {id ? 'Guardar cambios' : 'Crear cancha'}
          </Boton>
        }
      />
      <form className="grid-form" onSubmit={guardar} noValidate>
        <div className="grid gap-4 min-w-0">
          <section className="pc">
            <div className="pc-cab">
              <h2>
                <Warehouse className="ico" aria-hidden />
                Datos
              </h2>
            </div>
            <div className="pc-cuerpo grid gap-4">
              <div className="form-2">
                <CampoTexto label="Nombre" requerido maxLength={60} placeholder="Cancha 1 · Fútbol 5" error={errors.nombre?.message} {...register('nombre')} />
                <Selector label="Deporte" requerido opciones={DEPORTES} error={errors.deporte?.message} {...register('deporte')} />
                <CampoTexto label="Superficie" requerido maxLength={40} placeholder="Grass sintético" error={errors.superficie?.message} {...register('superficie')} />
                <CampoTexto label="Medidas" opcional maxLength={40} placeholder="30 x 18 m" error={errors.medidas?.message} {...register('medidas')} />
              </div>
              <div className="form-2">
                <CampoTexto label="Capacidad" requerido inputMode="numeric" sufijo="jugadores" maxLength={2} error={errors.capacidad?.message} {...register('capacidad')} />
                <CampoTexto label="Orden en el portal" requerido inputMode="numeric" maxLength={2} ayuda="1 = primera" error={errors.orden?.message} {...register('orden')} />
              </div>
              <div className="flex flex-wrap gap-6">
                <Interruptor activo={Boolean(v.techada)} alCambiar={(x) => setValue('techada', x, { shouldDirty: true })}>
                  Techada
                </Interruptor>
                <Interruptor activo={Boolean(v.iluminacionLed)} alCambiar={(x) => setValue('iluminacionLed', x, { shouldDirty: true })}>
                  <span className="inline-flex items-center gap-1">
                    <Lightbulb className="ico ico-16" aria-hidden />
                    Luz LED
                  </span>
                </Interruptor>
              </div>
              {/* Mismo switch que en «Configuración» de la cancha (antes era un select «Estado»). */}
              <div className="flex flex-wrap items-center gap-3 border-t border-borde-panel pt-4">
                <div className="grid gap-0.5 flex-1 min-w-[200px]">
                  <b className="text-[15px]">{v.estado === 'ACTIVA' ? 'Visible en el portal' : 'Oculta en el portal'}</b>
                  <span className="t-sec text-[13px]">{v.estado === 'ACTIVA' ? 'Se puede reservar apenas la crees.' : 'No se puede reservar hasta activarla.'}</span>
                </div>
                <Interruptor activo={v.estado === 'ACTIVA'} alCambiar={(x) => setValue('estado', x ? 'ACTIVA' : 'INACTIVA', { shouldDirty: true })}>
                  {v.estado === 'ACTIVA' ? 'Activa' : 'Inactiva'}
                </Interruptor>
              </div>
            </div>
          </section>

          <section className="pc">
            <div className="pc-cab">
              <h2>Descripción</h2>
            </div>
            <div className="pc-cuerpo grid gap-4">
              <AreaTexto label="Descripción corta" requerido rows={2} maxLength={255} contador={{ actual: (v.descripcion ?? '').length, max: 255 }} error={errors.descripcion?.message} {...register('descripcion')} />
              <AreaTexto label="Descripción larga" opcional rows={6} maxLength={2000} contador={{ actual: (v.descripcionLarga ?? '').length, max: 2000 }} error={errors.descripcionLarga?.message} {...register('descripcionLarga')} />
              <div className="grid gap-2">
                <span className="campo-label">
                  Características <span className="opc">({caracts.length}/12)</span>
                </span>
                <div className="caracts">
                  {caracts.map((c, i) => (
                    <span key={`${c}-${i}`} className="caract">
                      {c}
                      <button type="button" aria-label={`Quitar ${c}`} onClick={() => setValue('caracteristicas', caracts.filter((_, j) => j !== i), { shouldDirty: true, shouldValidate: true })}>
                        <X className="ico ico-14" aria-hidden />
                      </button>
                    </span>
                  ))}
                </div>
                <CampoTexto
                  aria-label="Nueva característica"
                  value={nuevaCaract}
                  maxLength={60}
                  placeholder="Camerinos con duchas"
                  onChange={(e) => setNuevaCaract(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      agregar();
                    }
                  }}
                  error={errorCaract ?? (errors.caracteristicas as { message?: string } | undefined)?.message}
                  accion={
                    <button type="button" className="control-boton" aria-label="Agregar característica" onClick={agregar}>
                      <Plus className="ico ico-18" aria-hidden />
                    </button>
                  }
                />
              </div>
            </div>
          </section>
        </div>

        <aside className="grid gap-4 content-start">
          <section className="pc">
            <div className="pc-cab">
              <h2>Precios por turno</h2>
            </div>
            <div className="pc-cuerpo grid gap-4">
              <CampoTexto label="Día" requerido prefijo="S/" icono={Sun} inputMode="decimal" error={errors.precioDia?.message} {...register('precioDia')} />
              <CampoTexto label="Noche" requerido prefijo="S/" inputMode="decimal" ayuda="Igual o mayor que el de día" error={errors.precioNoche?.message} {...register('precioNoche')} />
              <span className="dato-ico">
                <Moon className="ico" aria-hidden />
                Los turnos futuros libres toman el precio nuevo
              </span>
            </div>
          </section>
          {errorGeneral && <Alerta tipo="error">{errorGeneral}</Alerta>}
          <Boton type="submit" tamano="lg" bloque icono={Save} cargando={isSubmitting} textoCargando="Guardando…" disabled={Boolean(id) && !isDirty}>
            {id ? 'Guardar cambios' : 'Crear cancha'}
          </Boton>
        </aside>
      </form>

      {id && cancha.datos && <FotosCancha cancha={cancha.datos} alCambiar={() => setVersion((x) => x + 1)} />}
    </>
  );
}

// ------------------------------ fotos ------------------------------
async function validarFoto(f: File): Promise<string | null> {
  const msj = 'Sube una imagen JPG, PNG o WebP de hasta 5 MB.';
  if (f.size > 5 * 1024 * 1024) return msj;
  const tipo = await tipoPorBytes(f);
  const cab = new Uint8Array(await f.slice(0, 12).arrayBuffer());
  const webp = String.fromCharCode(...cab.slice(0, 4)) === 'RIFF' && String.fromCharCode(...cab.slice(8, 12)) === 'WEBP';
  if (!tipo && !webp) return msj;
  if (tipo) {
    const url = URL.createObjectURL(f);
    try {
      const { ancho, alto } = await medirImagen(url);
      if (Math.min(ancho, alto) < 200) return 'La foto debe medir al menos 200 px por lado.';
    } finally {
      URL.revokeObjectURL(url);
    }
  }
  return null;
}

/** Fotos de la cancha: subir con vista previa, leyenda, orden, principal y borrar. `compacto` = panel lateral del editor. */
export function FotosCancha({ cancha, alCambiar, compacto }: { cancha: CanchaAdmin; alCambiar: () => void; compacto?: boolean }) {
  const toast = useToast();
  const entrada = useRef<HTMLInputElement>(null);
  const [archivo, setArchivo] = useState<File | null>(null);
  const [vista, setVista] = useState<string | null>(null);
  const [leyenda, setLeyenda] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [progreso, setProgreso] = useState<number | null>(null);
  const [borrar, setBorrar] = useState<FotoAdmin | null>(null);
  const [visor, setVisor] = useState<number | null>(null);
  const [leyendas, setLeyendas] = useState<Record<number, string>>({});
  const fotos = [...cancha.fotos].sort((a, b) => a.orden - b.orden);

  useEffect(() => {
    if (!archivo) return setVista(null);
    const u = URL.createObjectURL(archivo);
    setVista(u);
    return () => URL.revokeObjectURL(u);
  }, [archivo]);

  const elegir = async (f?: File | null) => {
    if (!f) return;
    const p = await validarFoto(f);
    setError(p);
    setArchivo(p ? null : f);
  };
  const subir = async () => {
    if (!archivo) return setError('Elige una foto.');
    const l = esquemaLeyenda.safeParse(leyenda);
    if (!l.success) return setError(MSJ_PANEL.leyenda);
    setProgreso(0);
    try {
      await canchasAdminApi.subirFoto(cancha.id, archivo, l.data, setProgreso);
      toast.exito('Foto subida');
      setArchivo(null);
      setLeyenda('');
      alCambiar();
    } catch (e) {
      setError(esErrorApi(e) ? (e.errores[0]?.mensaje ?? e.message) : 'No se pudo subir.');
    } finally {
      setProgreso(null);
    }
  };
  const editar = async (f: FotoAdmin, datos: { leyenda?: string; orden?: number; principal?: boolean }, aviso: string) => {
    if (datos.leyenda !== undefined && !esquemaLeyenda.safeParse(datos.leyenda).success) return toast.error(MSJ_PANEL.leyenda);
    try {
      await canchasAdminApi.editarFoto(cancha.id, f.id, datos);
      toast.exito(aviso);
      alCambiar();
    } catch (e) {
      toast.error(esErrorApi(e) ? (e.errores[0]?.mensaje ?? e.message) : 'No se pudo guardar.');
    }
  };
  /** Intercambia el orden con la vecina (si tenían el mismo número, usa la posición en la lista). */
  const mover = async (i: number, d: -1 | 1) => {
    const f = fotos[i];
    const otra = fotos[i + d];
    if (!f || !otra) return;
    const ordenF = otra.orden !== f.orden ? otra.orden : i + d + 1;
    const ordenOtra = otra.orden !== f.orden ? f.orden : i + 1;
    try {
      await canchasAdminApi.editarFoto(cancha.id, f.id, { orden: ordenF });
      await canchasAdminApi.editarFoto(cancha.id, otra.id, { orden: ordenOtra });
      toast.exito('Orden actualizado');
    } catch (e) {
      toast.error(esErrorApi(e) ? (e.errores[0]?.mensaje ?? e.message) : 'No se pudo ordenar.');
    } finally {
      alCambiar();
    }
  };

  return (
    <section className={compacto ? 'grid gap-3' : 'pc'} id="fotos" aria-labelledby="h-fotos">
      <div className={compacto ? 'sr-only' : 'pc-cab'}>
        <h2 id="h-fotos">
          <Images className="ico" aria-hidden />
          Fotos
        </h2>
        <span className="pc-der dato-ico">{fotos.length}/10</span>
      </div>
      <div className={compacto ? 'grid gap-4' : 'pc-cuerpo grid gap-4'}>
        {/* subir */}
        <div className={compacto ? 'grid gap-3' : 'grid gap-3 md:grid-cols-[260px_1fr] items-start'}>
          <button
            type="button"
            className="uploader !min-h-[170px] grid place-items-center gap-2 cursor-pointer overflow-hidden p-0"
            onClick={() => entrada.current?.click()}
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
              e.preventDefault();
              void elegir(e.dataTransfer.files?.[0]);
            }}
            aria-label="Elegir foto"
          >
            {vista ? (
              <img src={vista} alt="Vista previa" className="w-full h-[170px] object-cover" />
            ) : (
              <span className="grid justify-items-center gap-1 p-4 text-texto-suave">
                <ImagePlus className="ico ico-32 text-verde" aria-hidden />
                <b className="text-noche">Elegir o arrastrar</b>
                <small>JPG, PNG o WebP · máx. 5 MB</small>
              </span>
            )}
          </button>
          <input
            ref={entrada}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="sr-only"
            onChange={(e) => {
              void elegir(e.target.files?.[0]);
              e.target.value = '';
            }}
          />
          <div className="grid gap-3">
            <CampoTexto label="Leyenda" opcional maxLength={120} value={leyenda} onChange={(e) => setLeyenda(e.target.value)} contador={{ actual: leyenda.length, max: 120 }} placeholder="De noche, con iluminación LED" />
            {error && <Alerta tipo="error">{error}</Alerta>}
            <div className="flex flex-wrap items-center gap-2">
              <Boton icono={Upload} disabled={!archivo || fotos.length >= 10} cargando={progreso !== null} textoCargando={`Subiendo ${progreso ?? 0} %`} onClick={subir}>
                Subir foto
              </Boton>
              {archivo && (
                <Boton variante="fantasma" icono={X} onClick={() => setArchivo(null)}>
                  Quitar
                </Boton>
              )}
              {archivo && <span className="t-sec text-[12.5px]">{archivo.name} · {Math.round(archivo.size / 1024)} KB</span>}
            </div>
          </div>
        </div>

        {fotos.length === 0 ? (
          <Vacio ilustracion="IL-25-sin-canchas" titulo="Sin fotos" texto="La primera que subas será la principal." compacto />
        ) : (
          <div className={compacto ? 'fotos-panel compacto' : 'fotos-panel'}>
            {fotos.map((f, i) => (
              <figure key={f.id} className="foto-panel m-0">
                <div className="img">
                  <img src={f.url} alt={f.leyenda ?? `Foto ${i + 1}`} loading="lazy" onClick={() => setVisor(i)} />
                  {f.principal && (
                    <span className="principal">
                      <Insignia tono="noche" icono={Star} tamano="sm">
                        Principal
                      </Insignia>
                    </span>
                  )}
                </div>
                <div className="px-2.5 pt-2">
                  <CampoTexto
                    aria-label={`Leyenda de la foto ${i + 1}`}
                    maxLength={120}
                    value={leyendas[f.id] ?? f.leyenda ?? ''}
                    onChange={(e) => setLeyendas((l) => ({ ...l, [f.id]: e.target.value }))}
                    onBlur={() => {
                      const nueva = leyendas[f.id];
                      if (nueva !== undefined && nueva !== (f.leyenda ?? '')) void editar(f, { leyenda: nueva }, 'Leyenda guardada');
                    }}
                    placeholder="Leyenda"
                  />
                </div>
                <div className="acc">
                  <Boton variante="fantasma" tamano="sm" icono={ArrowUp} etiqueta="Subir en el orden" disabled={i === 0} onClick={() => void mover(i, -1)} />
                  <Boton variante="fantasma" tamano="sm" icono={ArrowDown} etiqueta="Bajar en el orden" disabled={i === fotos.length - 1} onClick={() => void mover(i, 1)} />
                  <span className="t-sec text-[12px] tnum">#{f.orden}</span>
                  {!f.principal && (
                    <Boton variante="fantasma" tamano="sm" icono={Star} onClick={() => void editar(f, { principal: true }, 'Foto principal cambiada')}>
                      Principal
                    </Boton>
                  )}
                  <Boton variante="fantasma" tamano="sm" icono={Trash2} etiqueta="Borrar foto" className="ml-auto !text-rojo" onClick={() => setBorrar(f)} />
                </div>
              </figure>
            ))}
          </div>
        )}
      </div>
      {visor !== null && <VisorImagen imagenes={fotos.map((f) => ({ url: f.url, leyenda: f.leyenda }))} indice={visor} alCambiar={setVisor} alCerrar={() => setVisor(null)} titulo={cancha.nombre} />}
      <ModalConfirmar
        abierto={borrar !== null}
        alCerrar={() => setBorrar(null)}
        titulo="Borrar foto"
        subtitulo={cancha.nombre}
        busto="BU-rosa-gorra"
        peligro
        textoConfirmar="Borrar"
        iconoConfirmar={Trash2}
        alConfirmar={async () => {
          if (!borrar) return;
          try {
            await canchasAdminApi.borrarFoto(cancha.id, borrar.id);
            toast.exito('Foto borrada');
            setBorrar(null);
            alCambiar();
          } catch {
            /* toast del interceptor */
          }
        }}
      >
        {borrar && (
          <div className="grid gap-3">
            <img src={borrar.url} alt="" className="w-full max-h-[200px] object-cover rounded-[10px]" />
            {borrar.principal && (
              <div className="caja-aviso">
                <div>
                  <Star className="ico" aria-hidden />
                  La siguiente foto pasa a ser la principal
                </div>
              </div>
            )}
          </div>
        )}
      </ModalConfirmar>
    </section>
  );
}
