import { useState } from 'react';
import { CalendarClock, Eye, EyeOff, MessageSquareReply, Moon, Pencil, Plus, Star, Sun, Trash2, X } from 'lucide-react';
import { canchasAdminApi, resenasAdminApi } from '@/api/admin';
import type { CanchaAdmin, ResenaAdmin } from '@/api/admin/tipos';
import { esErrorApi } from '@/api/cliente';
import { useToast } from '@/contexto/ToastContext';
import { Boton } from '@/componentes/ui/Boton';
import { AreaTexto, CampoTexto } from '@/componentes/ui/Campo';
import { Avatar } from '@/componentes/ui/Datos';
import { ModalConfirmar } from '@/componentes/panel/Herramientas';
import { Insignia } from '@/componentes/ui/Estados';
import { Estrellas, EstrellasEntrada } from '@/componentes/ui/Estrellas';
import { fmtFecha, hoyLima } from '@/utilidades/fecha';
import { esquemaCaracteristica, MSJ_PANEL } from '../esquemasPanel';
import { FotosCancha, type Entrada } from '../CanchaFormulario';

interface PropsBorrador {
  entrada: Entrada;
  cambiar: (p: Partial<Entrada>) => void;
  errores: Record<string, string>;
}

export function PanelNombre({ entrada, cambiar, errores }: PropsBorrador) {
  const d = entrada.descripcion ?? '';
  return (
    <div className="grid gap-4">
      <CampoTexto label="Nombre" requerido maxLength={60} value={entrada.nombre ?? ''} onChange={(e) => cambiar({ nombre: e.target.value })} error={errores.nombre} placeholder="Cancha 1 · Fútbol 5" />
      <AreaTexto label="Resumen" requerido rows={3} maxLength={255} ayuda="Una o dos líneas bajo el nombre" value={d} onChange={(e) => cambiar({ descripcion: e.target.value })} contador={{ actual: d.length, max: 255 }} error={errores.descripcion} />
    </div>
  );
}

export function PanelFotos({ cancha, alCambiar }: { cancha: CanchaAdmin; alCambiar: () => void }) {
  return <FotosCancha cancha={cancha} alCambiar={alCambiar} compacto />;
}

export function PanelDescripcion({ entrada, cambiar, errores }: PropsBorrador) {
  const d = entrada.descripcionLarga ?? '';
  return (
    <div className="grid gap-3">
      <AreaTexto label="Sobre la cancha" opcional rows={9} maxLength={2000} value={d} onChange={(e) => cambiar({ descripcionLarga: e.target.value })} contador={{ actual: d.length, max: 2000 }} error={errores.descripcionLarga} />
      <span className="t-sec text-[12.5px]">Superficie, medidas, capacidad, techo y luz se cambian en «Configuración».</span>
    </div>
  );
}

export function PanelCaracteristicas({ entrada, cambiar, errores }: PropsBorrador) {
  const [nueva, setNueva] = useState('');
  const [error, setError] = useState<string | null>(null);
  const lista = entrada.caracteristicas ?? [];
  const agregar = () => {
    const r = esquemaCaracteristica.safeParse(nueva);
    if (!r.success) return setError(MSJ_PANEL.canchaCaracteristica);
    if (lista.length >= 12) return setError(MSJ_PANEL.canchaCaracteristicas);
    setError(null);
    cambiar({ caracteristicas: [...lista, r.data] });
    setNueva('');
  };
  return (
    <div className="grid gap-3">
      <div className="caracts">
        {lista.map((c, i) => (
          <span key={`${c}-${i}`} className="caract">
            {c}
            <button type="button" aria-label={`Quitar ${c}`} onClick={() => cambiar({ caracteristicas: lista.filter((_, j) => j !== i) })}>
              <X className="ico ico-14" aria-hidden />
            </button>
          </span>
        ))}
      </div>
      <CampoTexto
        label={`Agregar (${lista.length}/12)`}
        value={nueva}
        maxLength={60}
        placeholder="Camerinos con duchas"
        onChange={(e) => setNueva(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.preventDefault();
            agregar();
          }
        }}
        error={error ?? errores.caracteristicas}
        accion={
          <button type="button" className="control-boton" aria-label="Agregar característica" onClick={agregar}>
            <Plus className="ico ico-18" aria-hidden />
          </button>
        }
      />
    </div>
  );
}

export function PanelPrecios({ entrada, cambiar, errores, irAHorario }: PropsBorrador & { irAHorario: () => void }) {
  return (
    <div className="grid gap-4">
      <div className="cajas2-claro">
        <CampoTexto label="Día" requerido prefijo="S/" icono={Sun} inputMode="decimal" value={entrada.precioDia ?? ''} onChange={(e) => cambiar({ precioDia: e.target.value })} error={errores.precioDia} />
        <CampoTexto label="Noche" requerido prefijo="S/" icono={Moon} inputMode="decimal" value={entrada.precioNoche ?? ''} onChange={(e) => cambiar({ precioNoche: e.target.value })} error={errores.precioNoche} />
      </div>
      <span className="t-sec text-[12.5px]">Precio por turno. Los turnos libres futuros toman el precio nuevo.</span>
      <Boton variante="secundario" icono={CalendarClock} onClick={irAHorario}>
        Horario, duración y feriados
      </Boton>
    </div>
  );
}

// ------------------------------ reseñas de la cancha (SPEC_V2 §E) ------------------------------
interface FormResena {
  autor: string;
  puntuacion: number;
  comentario: string;
  fecha: string;
}
function validarResena(f: FormResena): Record<string, string> {
  const e: Record<string, string> = {};
  if (f.autor.trim().length < 2 || f.autor.trim().length > 60) e.autor = 'Escribe el nombre (2 a 60 caracteres).';
  if (f.puntuacion < 1) e.puntuacion = 'Elige de 1 a 5 estrellas.';
  const c = f.comentario.trim();
  if (c.length < 10 || c.length > 500 || /https?:\/\/|www\./i.test(c)) e.comentario = 'Escribe entre 10 y 500 caracteres, sin enlaces.';
  if (!/^\d{4}-\d{2}-\d{2}$/.test(f.fecha) || f.fecha > hoyLima()) e.fecha = 'Elige una fecha de hoy o antes.';
  return e;
}

function EditorResena({ inicial, alGuardar, alCancelar, textoBoton }: { inicial: FormResena; alGuardar: (f: FormResena) => Promise<Record<string, string> | null>; alCancelar: () => void; textoBoton: string }) {
  const [f, setF] = useState(inicial);
  const [err, setErr] = useState<Record<string, string>>({});
  const [enviando, setEnviando] = useState(false);
  const enviar = async () => {
    const e = validarResena(f);
    setErr(e);
    if (Object.keys(e).length) return;
    setEnviando(true);
    const r = await alGuardar({ ...f, autor: f.autor.trim(), comentario: f.comentario.trim() });
    setEnviando(false);
    if (r) setErr(r);
  };
  return (
    <div className="resena-form">
      <div className="cajas2-claro">
        <CampoTexto label="Autor" requerido maxLength={60} value={f.autor} onChange={(e) => setF({ ...f, autor: e.target.value })} error={err.autor} placeholder="Club Los Halcones" />
        <CampoTexto label="Fecha" requerido type="date" max={hoyLima()} value={f.fecha} onChange={(e) => setF({ ...f, fecha: e.target.value })} error={err.fecha} />
      </div>
      <EstrellasEntrada valor={f.puntuacion} alCambiar={(v) => setF({ ...f, puntuacion: v })} error={err.puntuacion} etiqueta="Calificación" />
      <AreaTexto label="Comentario" requerido rows={3} maxLength={500} value={f.comentario} onChange={(e) => setF({ ...f, comentario: e.target.value })} contador={{ actual: f.comentario.length, max: 500 }} error={err.comentario} />
      <div className="flex gap-2 justify-end">
        <Boton variante="fantasma" tamano="sm" onClick={alCancelar} disabled={enviando}>
          Cancelar
        </Boton>
        <Boton tamano="sm" cargando={enviando} onClick={enviar}>
          {textoBoton}
        </Boton>
      </div>
    </div>
  );
}

function errores(e: unknown): Record<string, string> {
  if (!esErrorApi(e)) return { comentario: 'No se pudo guardar.' };
  const r = Object.fromEntries(e.errores.map((x) => [x.campo, x.mensaje]));
  return Object.keys(r).length ? r : { comentario: e.message };
}

export function PanelResenas({ idCancha, resenas, alCambiar }: { idCancha: number; resenas: ResenaAdmin[]; alCambiar: () => void }) {
  const toast = useToast();
  const [nueva, setNueva] = useState(false);
  const [editar, setEditar] = useState<number | null>(null);
  const [responder, setResponder] = useState<{ id: number; texto: string; error?: string } | null>(null);
  const [ocupado, setOcupado] = useState<number | null>(null);
  const [borrar, setBorrar] = useState<ResenaAdmin | null>(null);
  const orden = [...resenas].sort((a, b) => (b.fecha ?? b.creadoEn).localeCompare(a.fecha ?? a.creadoEn));

  const accion = async (id: number, fn: () => Promise<unknown>, aviso: string) => {
    setOcupado(id);
    try {
      await fn();
      toast.exito(aviso);
      alCambiar();
    } catch {
      /* toast del interceptor */
    } finally {
      setOcupado(null);
    }
  };
  const enviarRespuesta = async () => {
    if (!responder) return;
    const t = responder.texto.trim();
    if (t.length < 10 || t.length > 500) return setResponder({ ...responder, error: MSJ_PANEL.respuesta });
    setOcupado(responder.id);
    try {
      await resenasAdminApi.responder(responder.id, t);
      toast.exito('Respuesta publicada');
      setResponder(null);
      alCambiar();
    } catch (e) {
      setResponder({ ...responder, error: esErrorApi(e) ? (e.errores[0]?.mensaje ?? e.message) : 'No se pudo responder.' });
    } finally {
      setOcupado(null);
    }
  };

  return (
    <div className="grid gap-3">
      {nueva ? (
        <EditorResena
          inicial={{ autor: '', puntuacion: 0, comentario: '', fecha: hoyLima() }}
          textoBoton="Agregar"
          alCancelar={() => setNueva(false)}
          alGuardar={async (f) => {
            try {
              await canchasAdminApi.crearResena(idCancha, f);
              toast.exito('Reseña agregada');
              setNueva(false);
              alCambiar();
              return null;
            } catch (e) {
              return errores(e);
            }
          }}
        />
      ) : (
        <Boton variante="secundario" icono={Plus} onClick={() => setNueva(true)}>
          Agregar reseña del complejo
        </Boton>
      )}
      {orden.length === 0 && <span className="t-sec">Aún sin reseñas.</span>}
      <ul className="resenas-admin">
        {orden.map((r) => {
          const complejo = r.origen === 'COMPLEJO';
          if (editar === r.id)
            return (
              <li key={r.id}>
                <EditorResena
                  inicial={{ autor: r.autor, puntuacion: r.puntuacion, comentario: r.comentario, fecha: (r.fecha ?? r.creadoEn).slice(0, 10) }}
                  textoBoton="Guardar"
                  alCancelar={() => setEditar(null)}
                  alGuardar={async (f) => {
                    try {
                      await resenasAdminApi.editar(r.id, f);
                      toast.exito('Reseña guardada');
                      setEditar(null);
                      alCambiar();
                      return null;
                    } catch (e) {
                      return errores(e);
                    }
                  }}
                />
              </li>
            );
          return (
            <li key={r.id} className={r.estado === 'OCULTA' ? 'oculta' : undefined}>
              <div className="flex items-start gap-2">
                <Avatar nombre={r.autor} tamano="sm" />
                <div className="grid gap-0.5 min-w-0 flex-1">
                  <b className="truncar text-[13.5px]">{r.autor}</b>
                  <span className="flex flex-wrap items-center gap-2">
                    <Estrellas valor={r.puntuacion} tamano={13} />
                    <span className="t-mini tnum">{fmtFecha((r.fecha ?? r.creadoEn).slice(0, 10), 'dia-mes-anio')}</span>
                  </span>
                </div>
                <span className="grid gap-1 justify-items-end">
                  {r.estado === 'OCULTA' && (
                    <Insignia tono="gris" icono={EyeOff} tamano="sm">
                      Oculta
                    </Insignia>
                  )}
                  {complejo && (
                    <Insignia tono="neutro" tamano="sm">
                      Del complejo
                    </Insignia>
                  )}
                </span>
              </div>
              <p className="m-0 text-[13.5px] leading-relaxed">{r.comentario}</p>
              {r.respuesta && responder?.id !== r.id && (
                <p className="resena-resp">
                  <MessageSquareReply className="ico" aria-hidden />
                  {r.respuesta}
                </p>
              )}
              {responder?.id === r.id && (
                <div className="grid gap-2">
                  <AreaTexto
                    label="Respuesta pública"
                    rows={3}
                    maxLength={500}
                    value={responder.texto}
                    onChange={(e) => setResponder({ ...responder, texto: e.target.value, error: undefined })}
                    contador={{ actual: responder.texto.length, max: 500 }}
                    error={responder.error}
                  />
                  <div className="flex gap-2 justify-end">
                    <Boton variante="fantasma" tamano="sm" onClick={() => setResponder(null)}>
                      Cancelar
                    </Boton>
                    <Boton tamano="sm" cargando={ocupado === r.id} onClick={enviarRespuesta}>
                      Publicar
                    </Boton>
                  </div>
                </div>
              )}
              {responder?.id !== r.id && (
                <div className="resena-acc">
                  <Boton variante="fantasma" tamano="sm" icono={MessageSquareReply} onClick={() => setResponder({ id: r.id, texto: r.respuesta ?? '' })}>
                    {r.respuesta ? 'Editar respuesta' : 'Responder'}
                  </Boton>
                  <Boton
                    variante="fantasma"
                    tamano="sm"
                    icono={r.estado === 'PUBLICADA' ? EyeOff : Eye}
                    cargando={ocupado === r.id}
                    onClick={() => accion(r.id, () => resenasAdminApi.estado(r.id, r.estado === 'PUBLICADA' ? 'OCULTA' : 'PUBLICADA'), r.estado === 'PUBLICADA' ? 'Reseña oculta' : 'Reseña publicada')}
                  >
                    {r.estado === 'PUBLICADA' ? 'Ocultar' : 'Publicar'}
                  </Boton>
                  {complejo && <Boton variante="fantasma" tamano="sm" icono={Pencil} etiqueta="Editar reseña" onClick={() => setEditar(r.id)} />}
                  {complejo && <Boton variante="fantasma" tamano="sm" icono={Trash2} etiqueta="Borrar reseña" className="!text-rojo" onClick={() => setBorrar(r)} />}
                </div>
              )}
            </li>
          );
        })}
      </ul>
      <span className="t-sec text-[12px] inline-flex items-center gap-1.5">
        <Star className="ico ico-14" aria-hidden />
        Las ocultas no se ven ni cuentan en el promedio.
      </span>
      <ModalConfirmar
        abierto={borrar !== null}
        alCerrar={() => setBorrar(null)}
        titulo="Borrar reseña"
        subtitulo={borrar?.autor}
        busto="BU-lucho-silbato"
        peligro
        textoConfirmar="Borrar"
        iconoConfirmar={Trash2}
        alConfirmar={async () => {
          if (!borrar) return;
          await accion(borrar.id, () => resenasAdminApi.borrar(borrar.id), 'Reseña borrada');
          setBorrar(null);
        }}
      >
        {borrar && <p className="m-0 text-[14px]">«{borrar.comentario}»</p>}
      </ModalConfirmar>
    </div>
  );
}
