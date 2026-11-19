import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { CalendarOff, CalendarPlus, ChevronLeft, ChevronRight, Download, Flag, Lock, Moon, Sun, Trash2, Wrench } from 'lucide-react';
import { calendarioApi, canchasAdminApi } from '@/api/admin';
import type { CanchaAdmin, DiaEspecialAdmin } from '@/api/admin/tipos';
import type { TipoDiaEspecial } from '@/api/tipos';
import { esErrorApi } from '@/api/cliente';
import { useToast } from '@/contexto/ToastContext';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { ErrorCarga } from '@/componentes/panel/Herramientas';
import { Boton } from '@/componentes/ui/Boton';
import { CampoTexto, Casilla, Interruptor } from '@/componentes/ui/Campo';
import { Esqueleto, Segmentado, Vacio } from '@/componentes/ui/Datos';
import { Alerta, Insignia } from '@/componentes/ui/Estados';
import { Modal } from '@/componentes/ui/Modal';
import { fmtFecha, hoyLima } from '@/utilidades/fecha';
import { fmtSoles } from '@/utilidades/formato';
import { cx } from '@/utilidades/cx';

const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Setiembre', 'Octubre', 'Noviembre', 'Diciembre'];
const SEMANA = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

/** Nombre corto de una cancha: «Cancha 1 · Fútbol 5» → «Cancha 1». */
const corto = (n: string) => n.split(' · ')[0] ?? n;

/** Texto «qué canchas abren y cuáles cierran» para un día especial. */
function efecto(d: Pick<DiaEspecialAdmin, 'tipo' | 'canchas'>, canchas: CanchaAdmin[]) {
  const activas = canchas.filter((c) => c.estado === 'ACTIVA');
  if (d.tipo === 'NO_LABORABLE') {
    const cierran = d.canchas?.length ? activas.filter((c) => d.canchas?.includes(c.id)) : activas;
    return { cierran, abren: activas.filter((c) => !cierran.includes(c)) };
  }
  return { cierran: activas.filter((c) => c.feriado?.modo === 'CERRADA'), abren: activas.filter((c) => c.feriado?.modo !== 'CERRADA') };
}

/** /admin/configuracion/feriados — calendario anual con feriados de Perú y días no laborables (SPEC_V2 §B). */
export default function Feriados() {
  useTituloPagina('Feriados y días no laborables');
  const toast = useToast();
  const [anio, setAnio] = useState(Number(hoyLima().slice(0, 4)));
  const [version, setVersion] = useState(0);
  const dias = useCarga(() => calendarioApi.listar(anio), `cal-${anio}-${version}`);
  const canchas = useCarga(() => canchasAdminApi.listar(), 'canchas-feriados');
  const [editar, setEditar] = useState<{ dia: DiaEspecialAdmin | null; fecha: string } | null>(null);
  const [cargando, setCargando] = useState(false);
  const porFecha = useMemo(() => new Map((dias.datos ?? []).map((d) => [d.fecha, d])), [dias.datos]);
  const lista = useMemo(() => [...(dias.datos ?? [])].sort((a, b) => a.fecha.localeCompare(b.fecha)), [dias.datos]);
  const hoy = hoyLima();
  const proximos = lista.filter((d) => d.fecha >= hoy);
  const feriados = lista.filter((d) => d.tipo === 'FERIADO').length;

  const cargarPeru = async () => {
    setCargando(true);
    try {
      await calendarioApi.cargarFeriadosPeru(anio);
      toast.exito(`Feriados de Perú ${anio} cargados`);
      setVersion((v) => v + 1);
    } catch {
      /* toast del interceptor */
    } finally {
      setCargando(false);
    }
  };

  return (
    <>
      <CabeceraPagina
        titulo="Feriados y días no laborables"
        subtitulo="Cierra o cobra adicional en feriados; cierra días puntuales"
        busto="BU-rosa-agenda"
        volver="/admin/configuracion"
        acciones={
          <Boton icono={CalendarPlus} onClick={() => setEditar({ dia: null, fecha: anio === Number(hoy.slice(0, 4)) ? hoy : `${anio}-01-01` })}>
            Día no laborable
          </Boton>
        }
      />

      <div className="flex flex-wrap items-center gap-3 justify-between">
        <div className="anio-sel" role="group" aria-label="Año">
          <Boton variante="secundario" tamano="sm" icono={ChevronLeft} etiqueta="Año anterior" onClick={() => setAnio((a) => a - 1)} />
          <b className="tnum">{anio}</b>
          <Boton variante="secundario" tamano="sm" icono={ChevronRight} etiqueta="Año siguiente" onClick={() => setAnio((a) => a + 1)} />
        </div>
        <div className="leyenda-panel" aria-label="Leyenda">
          <span>
            <i className="cal-mue feriado" />
            Feriado
          </span>
          <span>
            <i className="cal-mue no-laborable" />
            No laborable
          </span>
          <span>
            <i className="cal-mue hoy" />
            Hoy
          </span>
        </div>
      </div>

      {dias.error ? (
        <ErrorCarga alReintentar={dias.recargar} />
      ) : !dias.datos ? (
        <Esqueleto alto={420} radio={14} />
      ) : (
        <>
          {feriados === 0 && (
            <Alerta
              tipo="info"
              titulo={`Sin feriados en ${anio}`}
              acciones={
                <Boton tamano="sm" variante="oscuro" icono={Download} cargando={cargando} textoCargando="Cargando…" onClick={cargarPeru}>
                  Cargar feriados de Perú
                </Boton>
              }
            >
              Carga los feriados nacionales del año en un clic.
            </Alerta>
          )}
          <div className="feriados-grid">
            <section className="pc pc-cuerpo">
              <div className="cal-anio">
                {MESES.map((m, i) => (
                  <MesMini key={m} anio={anio} mes={i} nombre={m} porFecha={porFecha} hoy={hoy} alElegir={(f) => setEditar({ dia: porFecha.get(f) ?? null, fecha: f })} />
                ))}
              </div>
            </section>
            <section className="pc">
              <div className="pc-cab">
                <h2>
                  <Flag className="ico" aria-hidden />
                  {anio === Number(hoy.slice(0, 4)) ? 'Próximos' : `Días de ${anio}`}
                </h2>
                <span className="pc-der dato-ico tnum">{(anio === Number(hoy.slice(0, 4)) ? proximos : lista).length}</span>
              </div>
              {(anio === Number(hoy.slice(0, 4)) ? proximos : lista).length === 0 ? (
                <Vacio ilustracion="IL-26-sin-turnos" titulo="Sin días especiales" compacto />
              ) : (
                <ul className="dias-esp">
                  {(anio === Number(hoy.slice(0, 4)) ? proximos : lista).map((d) => {
                    const ef = efecto(d, canchas.datos ?? []);
                    return (
                      <li key={d.id}>
                        <button type="button" className="dia-esp" onClick={() => setEditar({ dia: d, fecha: d.fecha })}>
                          <span className={cx('dia-esp-fecha', d.tipo === 'FERIADO' ? 'feriado' : 'no-laborable')}>
                            <small>{fmtFecha(d.fecha, 'corta').split(' ')[0]}</small>
                            <b className="tnum">{Number(d.fecha.slice(8))}</b>
                            <small>{fmtFecha(d.fecha, 'dia-mes').split(' ')[1]}</small>
                          </span>
                          <span className="grid gap-1 min-w-0 text-left">
                            <b className="truncar">{d.nombre}</b>
                            <span className="flex flex-wrap gap-x-3 gap-y-1">
                              {ef.abren.length > 0 && (
                                <span className="dato-ico">
                                  <Sun className="ico" aria-hidden />
                                  {`Abren: ${ef.abren.map((c) => corto(c.nombre)).join(', ')}`}
                                </span>
                              )}
                              {ef.cierran.length > 0 && (
                                <span className="dato-ico">
                                  <Lock className="ico" aria-hidden />
                                  {`Cierran: ${ef.cierran.map((c) => corto(c.nombre)).join(', ')}`}
                                </span>
                              )}
                            </span>
                          </span>
                          <Insignia tono={d.tipo === 'FERIADO' ? 'amarillo' : 'noche'} tamano="sm" className="ml-auto shrink-0">
                            {d.tipo === 'FERIADO' ? 'Feriado' : 'No laborable'}
                          </Insignia>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
              <details className="mas-opciones">
                <summary>Más opciones</summary>
                <div className="grid gap-2">
                  <span className="t-sec text-[13px]">Vuelve a cargar los feriados oficiales del año (no duplica los que ya están).</span>
                  <Boton variante="secundario" tamano="sm" icono={Download} cargando={cargando} textoCargando="Cargando…" onClick={cargarPeru}>
                    {`Cargar feriados de Perú ${anio}`}
                  </Boton>
                </div>
              </details>
            </section>
          </div>
        </>
      )}

      <ModalDia
        abierto={editar !== null}
        dia={editar?.dia ?? null}
        fecha={editar?.fecha ?? hoy}
        canchas={canchas.datos ?? []}
        alCerrar={() => setEditar(null)}
        alHecho={() => {
          setEditar(null);
          setVersion((v) => v + 1);
        }}
      />
    </>
  );
}

function MesMini({ anio, mes, nombre, porFecha, hoy, alElegir }: { anio: number; mes: number; nombre: string; porFecha: Map<string, DiaEspecialAdmin>; hoy: string; alElegir: (f: string) => void }) {
  const primero = new Date(Date.UTC(anio, mes, 1));
  const total = new Date(Date.UTC(anio, mes + 1, 0)).getUTCDate();
  const vacios = (primero.getUTCDay() + 6) % 7; // lunes primero
  return (
    <div className="cal-mes">
      <b className="cal-mes-nom">{nombre}</b>
      <div className="cal-mes-grid" role="grid" aria-label={`${nombre} ${anio}`}>
        {SEMANA.map((d, i) => (
          <span key={i} className="cal-sem" aria-hidden>
            {d}
          </span>
        ))}
        {Array.from({ length: vacios }, (_, i) => (
          <span key={`v${i}`} />
        ))}
        {Array.from({ length: total }, (_, i) => {
          const f = `${anio}-${String(mes + 1).padStart(2, '0')}-${String(i + 1).padStart(2, '0')}`;
          const d = porFecha.get(f);
          return (
            <button
              key={f}
              type="button"
              className={cx('cal-dia', d?.tipo === 'FERIADO' && 'feriado', d?.tipo === 'NO_LABORABLE' && 'no-laborable', f === hoy && 'hoy', f < hoy && 'pasado')}
              title={d ? d.nombre : undefined}
              aria-label={`${fmtFecha(f, 'larga')}${d ? `: ${d.nombre}` : ''}`}
              onClick={() => alElegir(f)}
            >
              {i + 1}
            </button>
          );
        })}
      </div>
    </div>
  );
}

// ------------------------------ agregar / editar un día ------------------------------
const esquemaDia = z.object({
  fecha: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Elige la fecha.'),
  tipo: z.enum(['FERIADO', 'NO_LABORABLE']),
  nombre: z.string().trim().min(3, 'Escribe el motivo (3 a 120 caracteres).').max(120, 'Escribe el motivo (3 a 120 caracteres).'),
  todas: z.boolean(),
  canchas: z.array(z.number()),
}).refine((d) => d.tipo === 'FERIADO' || d.todas || d.canchas.length > 0, { path: ['canchas'], message: 'Elige al menos una cancha.' });
type EntradaDia = z.infer<typeof esquemaDia>;

function ModalDia({ abierto, dia, fecha, canchas, alCerrar, alHecho }: { abierto: boolean; dia: DiaEspecialAdmin | null; fecha: string; canchas: CanchaAdmin[]; alCerrar: () => void; alHecho: () => void }) {
  const toast = useToast();
  const [conReservas, setConReservas] = useState<string | null>(null);
  const [borrando, setBorrando] = useState(false);
  const { register, handleSubmit, reset, setValue, watch, setError, formState: { errors, isSubmitting } } = useForm<EntradaDia>({ resolver: zodResolver(esquemaDia), mode: 'onTouched' });
  useEffect(() => {
    if (!abierto) return;
    setConReservas(null);
    reset({ fecha: dia?.fecha ?? fecha, tipo: dia?.tipo ?? 'NO_LABORABLE', nombre: dia?.nombre ?? '', todas: !dia?.canchas?.length, canchas: dia?.canchas ?? [] });
  }, [abierto, dia, fecha, reset]);
  const v = watch();
  const activas = canchas.filter((c) => c.estado === 'ACTIVA');
  const ef = efecto({ tipo: (v.tipo ?? 'NO_LABORABLE') as TipoDiaEspecial, canchas: v.todas ? null : v.canchas ?? [] }, canchas);

  const guardar = handleSubmit(async (d) => {
    setConReservas(null);
    const cuerpo = { fecha: d.fecha, tipo: d.tipo, nombre: d.nombre, canchas: d.tipo === 'NO_LABORABLE' && !d.todas ? d.canchas : null };
    try {
      if (dia) await calendarioApi.editar(dia.id, cuerpo);
      else await calendarioApi.crear(cuerpo);
      toast.exito(dia ? 'Día actualizado' : 'Día agregado');
      alHecho();
    } catch (e) {
      if (esErrorApi(e) && e.codigo === 'DIA_CON_RESERVAS') {
        // El mensaje del back ya trae los códigos; si no, se arman con `errores`.
        const codigos = e.errores.map((x) => x.mensaje).filter(Boolean).join(', ');
        setConReservas(codigos && !e.message.includes(codigos.slice(0, 10)) ? `${e.message} ${codigos}` : e.message);
        return;
      }
      const { aplicados } = aplicarErroresServidor(e, setError, ['fecha', 'tipo', 'nombre', 'canchas']);
      if (!aplicados) toast.error(esErrorApi(e) ? e.message : 'No se pudo guardar.');
    }
  });
  const borrar = async () => {
    if (!dia) return;
    setBorrando(true);
    try {
      await calendarioApi.borrar(dia.id);
      toast.exito('Día quitado: vuelve a ser normal');
      alHecho();
    } catch {
      /* toast del interceptor */
    } finally {
      setBorrando(false);
    }
  };

  return (
    <Modal
      abierto={abierto}
      alCerrar={alCerrar}
      titulo={dia ? (dia.tipo === 'FERIADO' ? 'Feriado' : 'Día no laborable') : 'Nuevo día especial'}
      subtitulo={fmtFecha(v.fecha ?? fecha, 'larga-anio')}
      busto="BU-rosa-agenda"
      acciones={
        <>
          {dia ? (
            <Boton variante="peligro-contorno" icono={Trash2} cargando={borrando} onClick={borrar}>
              Quitar
            </Boton>
          ) : (
            <Boton variante="fantasma" onClick={alCerrar}>
              Volver
            </Boton>
          )}
          <Boton icono={CalendarOff} cargando={isSubmitting} textoCargando="Guardando…" onClick={guardar}>
            Guardar
          </Boton>
        </>
      }
    >
      <form className="grid gap-3" onSubmit={guardar} noValidate>
        <Segmentado
          etiqueta="Tipo"
          bloque
          valor={v.tipo ?? 'NO_LABORABLE'}
          alCambiar={(t) => setValue('tipo', t, { shouldValidate: true })}
          opciones={[
            { valor: 'NO_LABORABLE', texto: 'No laborable', icono: Wrench },
            { valor: 'FERIADO', texto: 'Feriado', icono: Flag },
          ]}
        />
        <div className="cajas2">
          <CampoTexto label="Fecha" requerido type="date" error={errors.fecha?.message} {...register('fecha')} />
          <CampoTexto label={v.tipo === 'FERIADO' ? 'Nombre' : 'Motivo'} requerido maxLength={120} placeholder={v.tipo === 'FERIADO' ? 'Santa Rosa de Lima' : 'Mantenimiento general'} error={errors.nombre?.message} {...register('nombre')} />
        </div>
        {v.tipo === 'NO_LABORABLE' ? (
          <div className="grid gap-2">
            <Interruptor activo={Boolean(v.todas)} alCambiar={(x) => setValue('todas', x, { shouldValidate: true })}>
              Todas las canchas
            </Interruptor>
            {!v.todas && (
              <div className="grid gap-2 sm:grid-cols-2">
                {activas.map((c) => (
                  <Casilla
                    key={c.id}
                    checked={(v.canchas ?? []).includes(c.id)}
                    onChange={(e) => setValue('canchas', e.target.checked ? [...(v.canchas ?? []), c.id] : (v.canchas ?? []).filter((x) => x !== c.id), { shouldValidate: true })}
                  >
                    {c.nombre}
                  </Casilla>
                ))}
              </div>
            )}
            {errors.canchas?.message && <Alerta tipo="error">{errors.canchas.message}</Alerta>}
          </div>
        ) : null}
        <div className="caja-aviso">
          {ef.abren.length > 0 && (
            <div>
              <Sun className="ico" aria-hidden />
              {v.tipo === 'FERIADO'
                ? `Abren con adicional: ${ef.abren.map((c) => `${corto(c.nombre)}${c.feriado?.recargo ? ` (+${fmtSoles(c.feriado.recargo, { decimales: 0 })})` : ''}`).join(', ')}`
                : `Siguen abiertas: ${ef.abren.map((c) => corto(c.nombre)).join(', ')}`}
            </div>
          )}
          {ef.cierran.length > 0 && (
            <div>
              <Lock className="ico" aria-hidden />
              {`Cierran: ${ef.cierran.map((c) => corto(c.nombre)).join(', ')}`}
            </div>
          )}
          {ef.cierran.length > 0 && (
            <div>
              <Moon className="ico" aria-hidden />
              Si ya hay reservas en las que cierran, primero cancélalas
            </div>
          )}
        </div>
        {conReservas && <Alerta tipo="advertencia" titulo="Ese día ya tiene reservas">{conReservas}</Alerta>}
      </form>
    </Modal>
  );
}
