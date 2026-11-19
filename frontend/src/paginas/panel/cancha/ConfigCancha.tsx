import { useState } from 'react';
import { CalendarDays, CircleCheck, CirclePause, EyeOff, Lightbulb, RotateCcw, Save, Warehouse } from 'lucide-react';
import { canchasAdminApi } from '@/api/admin';
import type { CanchaAdmin } from '@/api/admin/tipos';
import { useToast } from '@/contexto/ToastContext';
import { ModalConfirmar } from '@/componentes/panel/Herramientas';
import { Boton } from '@/componentes/ui/Boton';
import { CampoTexto, Interruptor, Selector } from '@/componentes/ui/Campo';
import { DEPORTES, type Entrada } from '../CanchaFormulario';

/** Apartado 3 · «Configuración»: deporte, capacidad, superficie, medidas, techada/LED, orden y estado. */
export function ConfigCancha({
  cancha,
  entrada,
  cambiar,
  sucio,
  guardando,
  guardar,
  descartar,
  alCambiarEstado,
}: {
  cancha: CanchaAdmin;
  entrada: Entrada;
  cambiar: (p: Partial<Entrada>) => void;
  sucio: boolean;
  guardando: boolean;
  guardar: () => Promise<Record<string, string> | null>;
  descartar: () => void;
  alCambiarEstado: () => void;
}) {
  const toast = useToast();
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [confirmar, setConfirmar] = useState(false);
  const activa = cancha.estado === 'ACTIVA';
  const enviar = async () => setErrores((await guardar()) ?? {});

  return (
    <div className="grid gap-4 max-w-[880px]">
      <section className="pc">
        <div className="pc-cab">
          <h2>
            <Warehouse className="ico" aria-hidden />
            Datos de la cancha
          </h2>
        </div>
        <div className="pc-cuerpo grid gap-4">
          <div className="form-2">
            <Selector label="Deporte" requerido opciones={DEPORTES} value={entrada.deporte} onChange={(e) => cambiar({ deporte: e.target.value as Entrada['deporte'] })} error={errores.deporte} />
            <CampoTexto label="Capacidad" requerido inputMode="numeric" sufijo="jugadores" maxLength={2} value={entrada.capacidad ?? ''} onChange={(e) => cambiar({ capacidad: e.target.value })} error={errores.capacidad} />
            <CampoTexto label="Superficie" requerido maxLength={40} placeholder="Grass sintético" value={entrada.superficie ?? ''} onChange={(e) => cambiar({ superficie: e.target.value })} error={errores.superficie} />
            <CampoTexto label="Medidas" opcional maxLength={40} placeholder="30 x 18 m" value={entrada.medidas ?? ''} onChange={(e) => cambiar({ medidas: e.target.value })} error={errores.medidas} />
            <CampoTexto label="Orden en el portal" requerido inputMode="numeric" maxLength={2} ayuda="1 = primera" value={entrada.orden ?? ''} onChange={(e) => cambiar({ orden: e.target.value })} error={errores.orden} />
          </div>
          <div className="flex flex-wrap gap-6">
            <Interruptor activo={Boolean(entrada.techada)} alCambiar={(x) => cambiar({ techada: x })}>
              Techada
            </Interruptor>
            <Interruptor activo={Boolean(entrada.iluminacionLed)} alCambiar={(x) => cambiar({ iluminacionLed: x })}>
              <span className="inline-flex items-center gap-1">
                <Lightbulb className="ico ico-16" aria-hidden />
                Luz LED
              </span>
            </Interruptor>
          </div>
          <div className="flex flex-wrap gap-2 justify-end">
            {sucio && (
              <Boton variante="fantasma" icono={RotateCcw} onClick={descartar} disabled={guardando}>
                Deshacer
              </Boton>
            )}
            <Boton icono={Save} disabled={!sucio} cargando={guardando} textoCargando="Guardando…" onClick={enviar}>
              Guardar
            </Boton>
          </div>
        </div>
      </section>

      <section className="pc">
        <div className="pc-cuerpo flex flex-wrap items-center gap-3">
          <div className="grid gap-0.5 flex-1 min-w-[220px]">
            <b className="text-[15px]">{activa ? 'Visible en el portal' : 'Oculta en el portal'}</b>
            <span className="t-sec text-[13px]">{activa ? 'Se puede reservar.' : 'No se puede reservar; sus turnos libres quedan bloqueados.'}</span>
          </div>
          <Interruptor activo={activa} alCambiar={() => setConfirmar(true)}>
            {activa ? 'Activa' : 'Inactiva'}
          </Interruptor>
        </div>
      </section>

      <ModalConfirmar
        abierto={confirmar}
        alCerrar={() => setConfirmar(false)}
        titulo={activa ? 'Desactivar cancha' : 'Activar cancha'}
        subtitulo={cancha.nombre}
        busto="BU-rosa-gorra"
        peligro={activa}
        textoConfirmar={activa ? 'Desactivar' : 'Activar'}
        iconoConfirmar={activa ? CirclePause : CircleCheck}
        alConfirmar={async () => {
          try {
            await canchasAdminApi.estado(cancha.id, activa ? 'INACTIVA' : 'ACTIVA');
            toast.exito(activa ? 'Cancha desactivada' : 'Cancha activada');
            setConfirmar(false);
            alCambiarEstado();
          } catch {
            /* toast del interceptor */
          }
        }}
      >
        <div className="caja-aviso">
          <div>
            {activa ? <EyeOff className="ico" aria-hidden /> : <CircleCheck className="ico" aria-hidden />}
            {activa ? 'Se oculta en el portal' : 'Vuelve a verse en el portal'}
          </div>
          <div>
            <CalendarDays className="ico" aria-hidden />
            {activa ? 'Sus turnos libres futuros quedan bloqueados; las reservas vigentes se mantienen' : 'Sus turnos futuros se liberan'}
          </div>
        </div>
      </ModalConfirmar>
    </div>
  );
}
