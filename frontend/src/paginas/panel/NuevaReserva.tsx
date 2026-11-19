import { useEffect, useMemo, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { z } from 'zod';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Banknote,
  BadgeCheck,
  CalendarDays,
  Check,
  Clock,
  CreditCard,
  Eye,
  Hash,
  IdCard,
  Mail,
  NotebookPen,
  Phone,
  Plus,
  Search,
  Smartphone,
  Star,
  Store,
  UserRound,
  UserX,
  Wallet,
} from 'lucide-react';
import { clientesApi, reservasAdminApi, turnosApi } from '@/api/admin';
import type { RespuestaManual } from '@/api/admin/tipos';
import { documentosApi } from '@/api/servicios';
import { esErrorApi } from '@/api/cliente';
import { useCarga } from '@/hooks/useCarga';
import { useCanchas, useComplejo } from '@/hooks/useDatosPublicos';
import { SelectorCancha } from '@/componentes/portal/SelectorCancha';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { useToast } from '@/contexto/ToastContext';
import { CabeceraPagina, usePanel } from '@/componentes/panel/PanelLayout';
import { BloqueComprobante } from '@/componentes/panel/BloqueComprobante';
import { Boton } from '@/componentes/ui/Boton';
import { AreaTexto, Casilla, CampoTexto, Interruptor } from '@/componentes/ui/Campo';
import { Avatar, Esqueleto, Segmentado, Spinner, Vacio } from '@/componentes/ui/Datos';
import { Alerta } from '@/componentes/ui/Estados';
import { IconoDeporte } from '@/componentes/ui/Marca';
import { Modal } from '@/componentes/ui/Modal';
import { ahoraLima, diaSemanaCorto, fmtFecha, fmtHora24, hoyLima, numeroDia, sumarDias, textoHorario } from '@/utilidades/fecha';
import { aTitulo, fmtSoles, plural } from '@/utilidades/formato';
import { normalizarCelular, RE_DNI } from '@/utilidades/documentos';
import { comprobanteDe, crearEsquemaManual } from './esquemasPanel';

type Entrada = z.input<ReturnType<typeof crearEsquemaManual>>;
type Salida = z.output<ReturnType<typeof crearEsquemaManual>>;
const CAMPOS = ['idsTurno', 'dni', 'nombres', 'apellidos', 'telefono', 'correo', 'origen', 'nota', 'monto', 'metodo', 'tipoPago', 'codigoOperacion', 'ruc', 'razonSocial', 'direccionFiscal'];

/** /admin/nueva-reserva — reserva presencial o telefónica (prototipo adm-20). Acepta ?idCancha=&fecha=&hora= (o &idTurno=) desde la agenda. */
export default function NuevaReserva() {
  useTituloPagina('Nueva reserva');
  const navigate = useNavigate();
  const toast = useToast();
  const { refrescarContador } = usePanel();
  const [sp] = useSearchParams();
  const complejo = useComplejo();
  const dias = (complejo.datos?.horario.diasVisibles ?? 14) + 1;
  const [fecha, setFecha] = useState(sp.get('fecha') || hoyLima());
  const [idCancha, setIdCancha] = useState<number | null>(sp.get('idCancha') ? Number(sp.get('idCancha')) : null);
  const agenda = useCarga(() => turnosApi.agenda(fecha), `ag-${fecha}`);
  const canchas = (agenda.datos?.canchas ?? []).filter((c) => c.activa);
  const cancha = canchas.find((c) => c.idCancha === idCancha) ?? null;
  // Tarjetas con portada para el selector (los datos públicos de las canchas activas de la agenda).
  const catalogo = useCanchas();
  const publicas = (catalogo.datos ?? []).filter((c) => canchas.some((x) => x.idCancha === c.id));
  const [dniEstado, setDniEstado] = useState<'nada' | 'buscando' | 'verificado' | 'manual'>('nada');
  const [frecuente, setFrecuente] = useState<{ total: number; noShows: number } | null>(null);
  const [creada, setCreada] = useState<RespuestaManual | null>(null);
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);

  const [total, setTotal] = useState(0);
  const esquema = useMemo(() => crearEsquemaManual(total), [total]);
  const {
    register,
    handleSubmit,
    setValue,
    setError,
    watch,
    reset,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm<Entrada>({
    resolver: zodResolver(esquema),
    mode: 'onTouched',
    defaultValues: {
      idsTurno: sp.get('idTurno') ? [Number(sp.get('idTurno'))] : [],
      dni: '',
      nombres: '',
      apellidos: '',
      telefono: '',
      correo: '',
      origen: 'PRESENCIAL',
      nota: '',
      conPago: false,
      tipoPago: 'ADELANTO',
      metodo: 'EFECTIVO',
      monto: '',
      codigoOperacion: '',
      informado: false,
      tipoComprobante: 'BOLETA',
      ruc: '',
      razonSocial: '',
      direccionFiscal: '',
    },
  });
  const v = watch();
  // v2: varios turnos de la misma cancha y fecha; el total suma sus precios (con recargo de feriado).
  const ids = v.idsTurno ?? [];
  const elegidos = (cancha?.turnos ?? []).filter((t) => ids.includes(t.idTurno));
  const sumaTurnos = Math.round(elegidos.reduce((a, t) => a + t.precio, 0) * 100) / 100;
  const adelantoPct = complejo.datos?.reglas.adelantoPct ?? 50;
  const adelanto = Math.round(sumaTurnos * adelantoPct) / 100;
  useEffect(() => setTotal(sumaTurnos), [sumaTurnos]);
  const alternarTurno = (id: number) =>
    setValue('idsTurno', ids.includes(id) ? ids.filter((x) => x !== id) : ids.length >= 8 ? ids : [...ids, id], { shouldValidate: true });
  useEffect(() => {
    if (!idCancha && canchas[0]) setIdCancha(canchas[0].idCancha);
  }, [canchas, idCancha]);
  // Desde la agenda (?hora=HH:mm): deja marcado ese turno si sigue libre. Solo una vez, al cargar la cancha.
  const horaUrl = useRef(sp.get('hora'));
  useEffect(() => {
    const h = horaUrl.current;
    if (!h || !cancha) return;
    horaUrl.current = null;
    const t = cancha.turnos.find((x) => fmtHora24(x.horaInicio) === fmtHora24(h));
    if (t && t.estado === 'DISPONIBLE' && !(fecha === hoyLima() && t.horaFin <= ahoraLima().format('HH:mm')) && !getValues('idsTurno')?.length) {
      setValue('idsTurno', [t.idTurno], { shouldValidate: true });
    }
  }, [cancha, fecha, getValues, setValue]);
  useEffect(() => {
    if (v.conPago) setValue('monto', (v.tipoPago === 'TOTAL' ? total : adelanto).toFixed(2));
  }, [v.conPago, v.tipoPago, total, adelanto, setValue]);

  const ahora = ahoraLima().format('HH:mm');
  const terminado = (horaFin: string) => fecha === hoyLima() && horaFin <= ahora;

  const buscarDni = async () => {
    const dni = getValues('dni').trim();
    if (!RE_DNI.test(dni)) {
      setError('dni', { message: 'El DNI debe tener 8 dígitos.' });
      return;
    }
    setDniEstado('buscando');
    setFrecuente(null);
    const [reniec, historial] = await Promise.allSettled([documentosApi.dni(dni), clientesApi.historial(dni)]);
    if (historial.status === 'fulfilled') {
      const c = historial.value.cliente;
      setValue('nombres', c.nombres, { shouldValidate: true });
      setValue('apellidos', c.apellidos, { shouldValidate: true });
      setValue('telefono', c.telefono, { shouldValidate: true });
      setValue('correo', c.correo, { shouldValidate: true });
      setFrecuente({ total: c.totalReservas, noShows: c.noShows });
    }
    if (reniec.status === 'fulfilled') {
      setValue('nombres', aTitulo(reniec.value.nombres), { shouldValidate: true });
      setValue('apellidos', aTitulo(reniec.value.apellidos), { shouldValidate: true });
      setDniEstado('verificado');
    } else {
      setDniEstado(historial.status === 'fulfilled' ? 'verificado' : 'manual');
      if (historial.status !== 'fulfilled') {
        const e = reniec.reason;
        toast.info(esErrorApi(e) && e.status === 404 ? 'RENIEC no encontró el DNI. Escribe los datos.' : 'RENIEC no responde. Escribe los datos.');
      }
    }
  };

  const enviar = handleSubmit(async (entrada) => {
    const d = entrada as unknown as Salida;
    setErrorGeneral(null);
    try {
      const r = await reservasAdminApi.crearManual({
        idsTurno: d.idsTurno,
        cliente: { dni: d.dni, nombres: aTitulo(d.nombres), apellidos: aTitulo(d.apellidos), telefono: normalizarCelular(d.telefono), correo: d.correo },
        origen: d.origen,
        nota: d.nota || undefined,
        pagoInicial: d.conPago
          ? { tipoPago: d.tipoPago, metodo: d.metodo, monto: Number(d.monto), codigoOperacion: d.codigoOperacion || undefined, comprobante: comprobanteDe(d) }
          : undefined,
      });
      setCreada(r);
      refrescarContador();
      agenda.recargar();
    } catch (e) {
      const mapa: Record<string, string> = {
        'cliente.dni': 'dni',
        'cliente.nombres': 'nombres',
        'cliente.apellidos': 'apellidos',
        'cliente.telefono': 'telefono',
        'cliente.correo': 'correo',
        'pagoInicial.monto': 'monto',
        'pagoInicial.metodo': 'metodo',
        'pagoInicial.tipoPago': 'tipoPago',
        'pagoInicial.codigoOperacion': 'codigoOperacion',
        'pagoInicial.comprobante.ruc': 'ruc',
        'pagoInicial.comprobante.razonSocial': 'razonSocial',
        'pagoInicial.comprobante.direccionFiscal': 'direccionFiscal',
      };
      const { aplicados, sinUbicar } = aplicarErroresServidor(e, setError, CAMPOS, mapa);
      if (esErrorApi(e) && e.status === 409) agenda.recargar();
      if (!aplicados || sinUbicar.length) setErrorGeneral(esErrorApi(e) ? e.message : 'No se pudo registrar.');
    }
  });

  const otra = () => {
    setCreada(null);
    setDniEstado('nada');
    setFrecuente(null);
    reset();
  };

  return (
    <>
      <CabeceraPagina titulo="Nueva reserva" subtitulo="Presencial o por teléfono, en la misma agenda" volver="/admin/reservas" busto="BU-rosa-laptop" />
      <form className="grid-form" onSubmit={enviar} noValidate>
        <div className="grid gap-4 min-w-0">
          {/* 1. cliente */}
          <section className="pc">
            <div className="pc-cab">
              <span className="paso-num">1</span>
              <h2>
                <UserRound className="ico" aria-hidden />
                Cliente
              </h2>
            </div>
            <div className="pc-cuerpo grid gap-4">
              <Segmentado
                etiqueta="Origen"
                bloque
                valor={v.origen}
                alCambiar={(o) => setValue('origen', o)}
                opciones={[
                  { valor: 'PRESENCIAL', texto: 'Presencial', icono: Store },
                  { valor: 'TELEFONICA', texto: 'Teléfono', icono: Phone },
                ]}
              />
              <CampoTexto
                label="DNI"
                requerido
                inputMode="numeric"
                maxLength={8}
                icono={IdCard}
                valido={dniEstado === 'verificado'}
                ayuda={dniEstado === 'manual' ? 'Escribe nombres y apellidos' : 'Lupa: RENIEC y clientes'}
                error={errors.dni?.message}
                accion={
                  <button type="button" className="control-boton" aria-label="Buscar DNI" onClick={buscarDni} disabled={dniEstado === 'buscando'}>
                    {dniEstado === 'buscando' ? <Spinner /> : <Search className="ico ico-18" aria-hidden />}
                  </button>
                }
                {...register('dni', { onChange: () => dniEstado !== 'nada' && setDniEstado('nada') })}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    void buscarDni();
                  }
                }}
              />
              {frecuente && (
                <div className="cliente-encontrado">
                  <Avatar nombre={`${v.nombres} ${v.apellidos}`} />
                  <div className="grid gap-1 flex-1 min-w-0">
                    <b>{`${v.nombres} ${v.apellidos}`}</b>
                    <div className="flex flex-wrap gap-x-3 gap-y-1">
                      {frecuente.total >= 3 && (
                        <span className="dato-ico !text-verde font-bold text-[11.5px] tracking-[.08em]">
                          <Star className="ico" aria-hidden />
                          FRECUENTE
                        </span>
                      )}
                      <span className="dato-ico">
                        <CalendarDays className="ico" aria-hidden />
                        {plural(frecuente.total, 'reserva', 'reservas')}
                      </span>
                      <span className="dato-ico !text-rojo">
                        <UserX className="ico" aria-hidden />
                        {frecuente.noShows} no asistió
                      </span>
                    </div>
                  </div>
                  <BadgeCheck className="ico ico-24 text-verde" aria-hidden />
                </div>
              )}
              <div className="form-2">
                <CampoTexto label="Nombres" requerido autoComplete="off" error={errors.nombres?.message} {...register('nombres')} />
                <CampoTexto label="Apellidos" requerido autoComplete="off" error={errors.apellidos?.message} {...register('apellidos')} />
                <CampoTexto label="Celular" requerido prefijo="+51" inputMode="tel" maxLength={15} error={errors.telefono?.message} {...register('telefono')} />
                <CampoTexto label="Correo" requerido type="email" icono={Mail} ayuda="Le llega la constancia" error={errors.correo?.message} {...register('correo')} />
              </div>
            </div>
          </section>

          {/* 2. turno */}
          <section className="pc">
            <div className="pc-cab">
              <span className="paso-num">2</span>
              <h2>
                <Clock className="ico" aria-hidden />
                Turno
              </h2>
              <span className="pc-der dato-ico">Marca uno o varios</span>
            </div>
            <div className="pc-cuerpo grid gap-4">
              {!agenda.datos && agenda.cargando ? (
                <Esqueleto alto={68} radio={14} />
              ) : (
                <SelectorCancha
                  canchas={publicas}
                  activa={idCancha}
                  alElegir={(id) => {
                    setIdCancha(id);
                    setValue('idsTurno', []);
                  }}
                />
              )}
              <div className="tira-dias" role="radiogroup" aria-label="Fecha">
                {Array.from({ length: dias }, (_, i) => sumarDias(hoyLima(), i)).map((f) => (
                  <button
                    key={f}
                    type="button"
                    aria-pressed={f === fecha}
                    onClick={() => {
                      setFecha(f);
                      setValue('idsTurno', []);
                    }}
                  >
                    <small>{f === hoyLima() ? 'Hoy' : diaSemanaCorto(f)}</small>
                    <b>{numeroDia(f)}</b>
                  </button>
                ))}
              </div>
              {agenda.cargando ? (
                <Esqueleto alto={120} />
              ) : !cancha || cancha.turnos.length === 0 ? (
                <Vacio ilustracion="IL-26-sin-turnos" titulo="Sin turnos ese día" compacto />
              ) : (
                <div className="turnos-libres" role="group" aria-label="Turnos (puedes marcar varios)">
                  {cancha.turnos.map((t) => {
                    const libre = t.estado === 'DISPONIBLE' && !terminado(t.horaFin);
                    return (
                      <button
                        key={t.idTurno}
                        type="button"
                        className="turno-p"
                        disabled={!libre}
                        aria-pressed={ids.includes(t.idTurno)}
                        title={!libre ? (t.estado === 'BLOQUEADO' ? t.motivoBloqueo ?? 'Bloqueado' : t.estado === 'RESERVADO' ? 'Ocupado' : 'Ya pasó') : undefined}
                        onClick={() => alternarTurno(t.idTurno)}
                      >
                        {ids.includes(t.idTurno) && <Check className="ico turno-p-check" aria-hidden />}
                        <b>{t.horaInicio}</b>
                        <small>{libre ? `${fmtSoles(t.precio, { decimales: 0 })}${t.feriado ? ' · feriado' : ''}` : t.estado === 'RESERVADO' ? 'Ocupado' : t.estado === 'BLOQUEADO' ? 'Bloqueado' : 'Pasó'}</small>
                      </button>
                    );
                  })}
                </div>
              )}
              {ids.length > 0 && (
                <span className="dato-ico">
                  <Clock className="ico" aria-hidden />
                  {`${plural(ids.length, 'turno', 'turnos')} · ${textoHorario(elegidos, true)}`}
                </span>
              )}
              {errors.idsTurno?.message && (
                <Alerta tipo="error" className="!py-2">
                  {errors.idsTurno.message}
                </Alerta>
              )}
            </div>
          </section>

          {/* 3. pago inicial */}
          <section className="pc">
            <div className="pc-cab">
              <span className="paso-num">3</span>
              <h2>
                <Wallet className="ico" aria-hidden />
                Pago inicial
              </h2>
              <div className="pc-der">
                <Interruptor activo={Boolean(v.conPago)} alCambiar={(x) => setValue('conPago', x)}>
                  {v.conPago ? 'Cobrar ahora' : 'Sin pago'}
                </Interruptor>
              </div>
            </div>
            <div className="pc-cuerpo grid gap-4">
              {v.conPago ? (
                <>
                  <div className="form-2">
                    <Segmentado
                      etiqueta="Tipo de pago"
                      bloque
                      pequeno
                      valor={v.tipoPago}
                      alCambiar={(t) => setValue('tipoPago', t)}
                      opciones={[
                        { valor: 'ADELANTO', texto: `Adelanto ${adelantoPct} %` },
                        { valor: 'TOTAL', texto: 'Total' },
                      ]}
                    />
                    <Segmentado
                      etiqueta="Método"
                      bloque
                      pequeno
                      valor={v.metodo}
                      alCambiar={(m) => setValue('metodo', m, { shouldValidate: true })}
                      opciones={[
                        { valor: 'EFECTIVO', texto: 'Efectivo', icono: Banknote },
                        { valor: 'YAPE', texto: 'Yape', icono: Smartphone },
                        { valor: 'PLIN', texto: 'Plin', icono: Smartphone },
                        { valor: 'TARJETA_POS', texto: 'POS', icono: CreditCard },
                      ]}
                    />
                  </div>
                  <div className="form-2">
                    <CampoTexto label="Monto" requerido prefijo="S/" inputMode="decimal" error={errors.monto?.message} {...register('monto')} />
                    {(v.metodo === 'YAPE' || v.metodo === 'PLIN') && (
                      <CampoTexto label="N.° de operación" requerido inputMode="numeric" maxLength={12} icono={Hash} error={errors.codigoOperacion?.message} {...register('codigoOperacion')} />
                    )}
                  </div>
                  <BloqueComprobante
                    tipo={v.tipoComprobante ?? 'BOLETA'}
                    alTipo={(t) => setValue('tipoComprobante', t)}
                    registrar={(c) => register(c)}
                    errores={{ ruc: errors.ruc?.message, razonSocial: errors.razonSocial?.message, direccionFiscal: errors.direccionFiscal?.message }}
                    ruc={v.ruc ?? ''}
                    alLlenar={(x) => {
                      setValue('razonSocial', x.razonSocial, { shouldValidate: true });
                      setValue('direccionFiscal', x.direccionFiscal, { shouldValidate: true });
                    }}
                  />
                </>
              ) : (
                <span className="t-sec">La reserva queda pendiente, sin plazo. Cobra después desde su detalle.</span>
              )}
              <AreaTexto label="Nota" opcional rows={2} maxLength={255} contador={{ actual: (v.nota ?? '').length, max: 255 }} error={errors.nota?.message} {...register('nota')} />
            </div>
          </section>
        </div>

        {/* resumen */}
        <aside className="pc resumen-manual">
          <div className="pc-cab">
            <span className="sec-titulo !m-0 !text-verde">Reserva manual</span>
            <span className="pc-der badge b-neutro badge-sm">
              {v.origen === 'TELEFONICA' ? <Phone className="ico" aria-hidden /> : <Store className="ico" aria-hidden />}
              <span>{v.origen === 'TELEFONICA' ? 'Teléfono' : 'Presencial'}</span>
            </span>
          </div>
          <div className="pc-cuerpo grid gap-1">
            <div className="linea">
              <span className="td-ico">
                <UserRound className="ico" aria-hidden />
              </span>
              {v.nombres || v.apellidos ? `${v.nombres} ${v.apellidos}` : <span className="t-sec font-normal">Cliente</span>}
            </div>
            <div className="linea">
              <span className="td-ico">{cancha ? <IconoDeporte deporte={cancha.deporte} className="ico" /> : <CalendarDays className="ico" aria-hidden />}</span>
              {cancha?.nombre ?? <span className="t-sec font-normal">Cancha</span>}
            </div>
            <div className="linea">
              <span className="td-ico">
                <CalendarDays className="ico" aria-hidden />
              </span>
              {fmtFecha(fecha, 'relativa')} · {fmtFecha(fecha, 'dia-mes')}
            </div>
            <div className="linea">
              <span className="td-ico">
                <Clock className="ico" aria-hidden />
              </span>
              {elegidos.length ? `${textoHorario(elegidos, true)}${elegidos.length > 1 ? ` · ${elegidos.length} turnos` : ''}` : <span className="t-sec font-normal">Elige uno o más turnos</span>}
            </div>
          </div>
          <div className="pc-cuerpo border-t border-borde-panel grid gap-1">
            <div className="tot-fila">
              <span>Total</span>
              <b>{fmtSoles(total)}</b>
            </div>
            <div className="tot-fila">
              <span>Adelanto {adelantoPct} %</span>
              <b>{fmtSoles(adelanto)}</b>
            </div>
            <div className="tot-fila grande">
              <span className="font-semibold text-noche">{v.conPago ? 'Cobras ahora' : 'Sin pago'}</span>
              <b>{fmtSoles(v.conPago ? Number(v.monto) || 0 : 0)}</b>
            </div>
          </div>
          <div className="pc-cuerpo border-t border-borde-panel grid gap-3">
            <Casilla error={errors.informado?.message} {...register('informado')}>
              Informé al cliente del aviso de privacidad
            </Casilla>
            <a href="/privacidad" target="_blank" rel="noopener" className="enlace inline-flex items-center gap-1 text-[13px] w-fit">
              <Eye className="ico ico-14" aria-hidden />
              Ver aviso
            </a>
            {errorGeneral && <Alerta tipo="error">{errorGeneral}</Alerta>}
            <Boton type="submit" tamano="lg" bloque icono={Check} cargando={isSubmitting} textoCargando="Registrando…">
              Registrar reserva
            </Boton>
          </div>
        </aside>
      </form>

      <Modal
        abierto={creada !== null}
        alCerrar={otra}
        titulo="Reserva registrada"
        subtitulo={creada ? `${creada.codigoReserva} · ${fmtSoles(creada.montoTotal)}` : ''}
        ilustracion="IL-12-confirmada"
        acciones={
          <>
            <Boton variante="secundario" icono={Plus} onClick={otra}>
              Otra
            </Boton>
            <Boton icono={NotebookPen} onClick={() => creada && navigate(`/admin/reservas/${creada.codigoReserva}`)}>
              Ver reserva
            </Boton>
          </>
        }
      >
        {creada && (
          <div className="grid gap-2 justify-items-center text-center">
            <span className="ticket-codigo !text-[34px] tnum">{creada.codigoReserva}</span>
            {creada.pagoRegistrado === false && creada.aviso ? <Alerta tipo="advertencia">{creada.aviso}</Alerta> : <span className="t-sec">Le enviamos la constancia por correo y WhatsApp.</span>}
            {creada.pagoRegistrado && <span className="t-sec">El pago se registró y la reserva se confirma en segundos.</span>}
          </div>
        )}
      </Modal>
    </>
  );
}
