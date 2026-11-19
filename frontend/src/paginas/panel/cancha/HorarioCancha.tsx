import { useEffect, useMemo, useRef, useState } from 'react';
import { CalendarCheck, CalendarX, Clock, Copy, Eye, Flag, Moon, RotateCcw, Save, Sun } from 'lucide-react';
import { canchasAdminApi } from '@/api/admin';
import type { CanchaAdmin, HorarioAdmin } from '@/api/admin/tipos';
import type { DiaHorario } from '@/api/tipos';
import { esErrorApi } from '@/api/cliente';
import { useToast } from '@/contexto/ToastContext';
import { Boton } from '@/componentes/ui/Boton';
import { CampoTexto, Interruptor, Selector } from '@/componentes/ui/Campo';
import { Cargando, Segmentado } from '@/componentes/ui/Datos';
import { Alerta } from '@/componentes/ui/Estados';
import { fmtHora24 } from '@/utilidades/fecha';
import { fmtSoles, plural } from '@/utilidades/formato';
import { DIAS_CORTOS, DIAS_SEMANA, HORAS_MEDIA, errorDia, textoSemana, turnosDelDia, type TurnoCalculado } from '@/utilidades/horario';
import { cx } from '@/utilidades/cx';
import type { Entrada } from '../CanchaFormulario';
import { HORARIO_DEFECTO, erroresDe } from './utilCancha';

const DURACIONES = [30, 60, 90, 120] as const;
// El panel muestra las horas en 24 h (regla de coherencia); el portal, en 12 h.
const hora24 = (h: string) => fmtHora24(h);
const OPC_APERTURA = HORAS_MEDIA.filter((h) => h !== '24:00').map((h) => ({ valor: h, texto: hora24(h) }));
const OPC_CIERRE = HORAS_MEDIA.filter((h) => h !== '00:00').map((h) => ({ valor: h, texto: h === '24:00' ? '24:00 (medianoche)' : hora24(h) }));
const OPC_NOCHE = HORAS_MEDIA.filter((h) => h >= '12:00' && h <= '22:00').map((h) => ({ valor: h, texto: hora24(h) }));

/**
 * Apartado 2 · «Horario y precios» (SPEC_V2 §A): lunes a domingo (abierto/cerrado, apertura y cierre), duración del turno,
 * precios día/noche e inicio de la noche, «copiar a todos los días», vista previa de los turnos y comportamiento en feriado.
 */
export function HorarioCancha({
  cancha,
  horario,
  cargando,
  entrada,
  cambiar,
  guardarCancha,
  alGuardado,
}: {
  cancha: CanchaAdmin;
  horario: HorarioAdmin | null;
  cargando: boolean;
  entrada: Entrada;
  cambiar: (p: Partial<Entrada>) => void;
  guardarCancha: () => Promise<Record<string, string> | null>;
  alGuardado: (h: HorarioAdmin) => void;
}) {
  const toast = useToast();
  const base = horario ?? HORARIO_DEFECTO;
  const [h, setH] = useState<HorarioAdmin>(base);
  const [dia, setDia] = useState(1);
  const [guardando, setGuardando] = useState(false);
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [erroresPrecio, setErroresPrecio] = useState<Record<string, string>>({});
  useEffect(() => setH(horario ?? HORARIO_DEFECTO), [horario]);

  const preciosOriginales = `${cancha.precioDia.toFixed(2)}|${cancha.precioNoche.toFixed(2)}`;
  const preciosCambiaron = `${entrada.precioDia}|${entrada.precioNoche}` !== preciosOriginales;
  const sucio = JSON.stringify(h) !== JSON.stringify(base) || preciosCambiaron;
  const dias = [...h.dias].sort((a, b) => a.diaSemana - b.diaSemana);
  const errores = dias.map((d) => errorDia(d, h.duracionTurnoMin));
  const hayError = errores.some(Boolean) || !dias.some((d) => d.abierto);

  const ponerDia = (n: number, p: Partial<DiaHorario>) => setH((x) => ({ ...x, dias: x.dias.map((d) => (d.diaSemana === n ? { ...d, ...p } : d)) }));
  const copiarATodos = (n: number) => {
    const o = h.dias.find((d) => d.diaSemana === n);
    if (!o) return;
    setH((x) => ({ ...x, dias: x.dias.map((d) => ({ ...d, abierto: o.abierto, apertura: o.apertura, cierre: o.cierre })) }));
    toast.info(`${DIAS_SEMANA[n - 1]} copiado a toda la semana`);
  };

  // Vista previa: la calcula el back (POST …/vista-previa) y, si no responde, se calcula aquí con la misma regla.
  const pd = Number(entrada.precioDia) || 0;
  const pn = Number(entrada.precioNoche) || 0;
  const local = useMemo(() => turnosDelDia(h.dias.find((d) => d.diaSemana === dia), h.duracionTurnoMin, h.inicioNoche, pd, pn), [h, dia, pd, pn]);
  const [remota, setRemota] = useState<TurnoCalculado[] | null>(null);
  const pedido = useRef(0);
  useEffect(() => {
    setRemota(null);
    if (errores[dia - 1]) return undefined;
    const n = ++pedido.current;
    const t = window.setTimeout(() => {
      canchasAdminApi
        .vistaPrevia(cancha.id, dia, h)
        .then((r) => n === pedido.current && Array.isArray(r) && setRemota(r))
        .catch(() => undefined);
    }, 450);
    return () => window.clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [h, dia, cancha.id]);
  // El back usa los precios GUARDADOS: si se están cambiando, manda la vista local.
  const turnos = remota && !preciosCambiaron ? remota : local;

  const guardar = async () => {
    setErrorGeneral(null);
    if (hayError) {
      setErrorGeneral(!dias.some((d) => d.abierto) ? 'Abre al menos un día.' : 'Corrige los días marcados.');
      return;
    }
    if (h.feriado.modo === 'ABIERTA' && !(h.feriado.recargo >= 0 && h.feriado.recargo <= 999)) {
      setErrorGeneral('El adicional de feriado va de S/ 0 a S/ 999.');
      return;
    }
    if (preciosCambiaron) {
      const e = erroresDe(entrada);
      if (e.precioDia || e.precioNoche) {
        setErroresPrecio(e);
        return;
      }
    }
    setGuardando(true);
    try {
      if (preciosCambiaron) {
        const e = await guardarCancha();
        if (e) {
          setErroresPrecio(e);
          return;
        }
      }
      if (JSON.stringify(h) !== JSON.stringify(base) || !horario) {
        const r = await canchasAdminApi.guardarHorario(cancha.id, h);
        alGuardado(r ?? h);
        toast.exito('Horario guardado: los turnos futuros ya se actualizaron');
      }
      setErroresPrecio({});
    } catch (e) {
      const porDia = esErrorApi(e) ? e.errores.map((x) => x.mensaje).join(' ') : '';
      setErrorGeneral(esErrorApi(e) ? porDia || e.message : 'No se pudo guardar.');
    } finally {
      setGuardando(false);
    }
  };

  if (cargando && !horario) return <Cargando texto="Cargando horario…" />;
  const suma = turnos.reduce((a, t) => a + t.precio, 0);

  return (
    <div className="horario-grid">
      <div className="grid gap-4 min-w-0">
        {/* turnos y precios */}
        <section className="pc">
          <div className="pc-cab">
            <h2>
              <Clock className="ico" aria-hidden />
              Turnos y precios
            </h2>
          </div>
          <div className="pc-cuerpo grid gap-4">
            <Segmentado
              etiqueta="Duración del turno"
              bloque
              valor={String(h.duracionTurnoMin)}
              alCambiar={(v) => setH((x) => ({ ...x, duracionTurnoMin: Number(v) }))}
              opciones={DURACIONES.map((d) => ({ valor: String(d), texto: d < 60 ? `${d} min` : d % 60 ? `${Math.floor(d / 60)} h ${d % 60}` : `${d / 60} h` }))}
            />
            <div className="form-3">
              <CampoTexto label="Precio de día" requerido prefijo="S/" icono={Sun} inputMode="decimal" value={entrada.precioDia ?? ''} onChange={(e) => cambiar({ precioDia: e.target.value })} error={erroresPrecio.precioDia} />
              <CampoTexto label="Precio de noche" requerido prefijo="S/" icono={Moon} inputMode="decimal" value={entrada.precioNoche ?? ''} onChange={(e) => cambiar({ precioNoche: e.target.value })} error={erroresPrecio.precioNoche} />
              <Selector label="La noche empieza" requerido opciones={OPC_NOCHE} value={h.inicioNoche} onChange={(e) => setH((x) => ({ ...x, inicioNoche: e.target.value }))} />
            </div>
          </div>
        </section>

        {/* semana */}
        <section className="pc">
          <div className="pc-cab">
            <h2>
              <CalendarCheck className="ico" aria-hidden />
              Días y horas
            </h2>
            <span className="pc-der dato-ico">{textoSemana(dias, true)}</span>
          </div>
          <ul className="semana">
            {dias.map((d, i) => {
              const n = turnosDelDia(d, h.duracionTurnoMin, h.inicioNoche, pd, pn).length;
              return (
                <li key={d.diaSemana} className={cx(!d.abierto && 'cerrado', errores[i] && 'con-error')}>
                  <button type="button" className={cx('semana-dia', dia === d.diaSemana && 'activo')} onClick={() => setDia(d.diaSemana)} aria-label={`Ver turnos del ${DIAS_SEMANA[i]}`}>
                    {DIAS_SEMANA[i]}
                  </button>
                  <Interruptor activo={d.abierto} alCambiar={(x) => ponerDia(d.diaSemana, { abierto: x })}>
                    <span className="sr-only">{`${DIAS_SEMANA[i]} abierto`}</span>
                  </Interruptor>
                  {d.abierto ? (
                    <>
                      <select className="semana-hora" aria-label={`Apertura del ${DIAS_SEMANA[i]}`} value={d.apertura.slice(0, 5)} onChange={(e) => ponerDia(d.diaSemana, { apertura: e.target.value })}>
                        {OPC_APERTURA.map((o) => (
                          <option key={o.valor} value={o.valor}>
                            {o.texto}
                          </option>
                        ))}
                      </select>
                      <span className="semana-a" aria-hidden>
                        a
                      </span>
                      <select className="semana-hora" aria-label={`Cierre del ${DIAS_SEMANA[i]}`} value={d.cierre.startsWith('23:59') ? '24:00' : d.cierre.slice(0, 5)} onChange={(e) => ponerDia(d.diaSemana, { cierre: e.target.value })}>
                        {OPC_CIERRE.map((o) => (
                          <option key={o.valor} value={o.valor}>
                            {o.texto}
                          </option>
                        ))}
                      </select>
                      <span className="semana-n tnum">{plural(n, 'turno', 'turnos')}</span>
                    </>
                  ) : (
                    <span className="semana-cerrado">
                      <CalendarX className="ico" aria-hidden />
                      Cerrado
                    </span>
                  )}
                  <Boton variante="fantasma" tamano="sm" icono={Copy} etiqueta={`Copiar ${DIAS_SEMANA[i]} a todos los días`} className="semana-copiar" onClick={() => copiarATodos(d.diaSemana)} />
                  {errores[i] && <span className="semana-error">{errores[i]}</span>}
                </li>
              );
            })}
          </ul>
        </section>

        {/* feriados */}
        <section className="pc">
          <div className="pc-cab">
            <h2>
              <Flag className="ico" aria-hidden />
              En feriados
            </h2>
          </div>
          <div className="pc-cuerpo grid gap-3">
            <Segmentado
              etiqueta="En feriados"
              bloque
              valor={h.feriado.modo}
              alCambiar={(m) => setH((x) => ({ ...x, feriado: { ...x.feriado, modo: m } }))}
              opciones={[
                { valor: 'ABIERTA', texto: 'Abre con adicional', icono: Sun },
                { valor: 'CERRADA', texto: 'Cierra', icono: CalendarX },
              ]}
            />
            {h.feriado.modo === 'ABIERTA' ? (
              <div className="form-3">
                <CampoTexto
                  label="Adicional por turno"
                  prefijo="S/"
                  inputMode="decimal"
                  maxLength={6}
                  value={String(h.feriado.recargo)}
                  onChange={(e) => setH((x) => ({ ...x, feriado: { ...x.feriado, recargo: Number(e.target.value.replace(',', '.')) || 0 } }))}
                  ayuda="0 = mismo precio"
                />
              </div>
            ) : (
              <span className="t-sec">Los feriados la cancha no se puede reservar.</span>
            )}
          </div>
        </section>
      </div>

      {/* vista previa */}
      <aside className="grid gap-4 content-start horario-lado">
        <section className="pc">
          <div className="pc-cab">
            <h2>
              <Eye className="ico" aria-hidden />
              Así saldrán los turnos
            </h2>
          </div>
          <div className="pc-cuerpo grid gap-3">
            <div className="dias-chips" role="tablist" aria-label="Día">
              {DIAS_CORTOS.map((t, i) => (
                <button key={t} type="button" role="tab" aria-selected={dia === i + 1} className={cx(!dias[i]?.abierto && 'cerrado')} onClick={() => setDia(i + 1)}>
                  {t}
                </button>
              ))}
            </div>
            {errores[dia - 1] ? (
              <Alerta tipo="advertencia">{errores[dia - 1]}</Alerta>
            ) : turnos.length === 0 ? (
              <span className="t-sec">Cerrado este día.</span>
            ) : (
              <>
                {(['DIA', 'NOCHE'] as const).map((franja) => {
                  const grupo = turnos.filter((t) => (t.franja === 'NOCHE' ? 'NOCHE' : 'DIA') === franja);
                  if (grupo.length === 0) return null;
                  return (
                    <div key={franja} className="previa-grupo">
                      <span className="previa-titulo">{franja === 'DIA' ? 'Día' : 'Noche'}</span>
                      <ul className="previa-turnos">
                        {grupo.map((t) => (
                          <li key={t.horaInicio} className={franja === 'NOCHE' ? 'previa-noche' : 'previa-dia'}>
                            <b className="tnum">{hora24(t.horaInicio)}</b>
                            <span className="tnum">{fmtSoles(t.precio, { decimales: 0 })}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  );
                })}
                <span className="dato-ico tnum">{`${plural(turnos.length, 'turno', 'turnos')} · hasta ${fmtSoles(suma, { decimales: 0 })} al día`}</span>
              </>
            )}
          </div>
        </section>
        {errorGeneral && <Alerta tipo="error">{errorGeneral}</Alerta>}
        <div className="grid gap-2">
          <Boton tamano="lg" bloque icono={Save} disabled={!sucio && Boolean(horario)} cargando={guardando} textoCargando="Guardando…" onClick={guardar}>
            Guardar horario
          </Boton>
          {sucio && (
            <Boton
              variante="fantasma"
              bloque
              icono={RotateCcw}
              onClick={() => {
                setH(base);
                cambiar({ precioDia: cancha.precioDia.toFixed(2), precioNoche: cancha.precioNoche.toFixed(2) });
              }}
            >
              Deshacer
            </Boton>
          )}
          <span className="t-sec text-[12.5px] text-center">Las reservas ya hechas se conservan.</span>
        </div>
      </aside>
    </div>
  );
}
