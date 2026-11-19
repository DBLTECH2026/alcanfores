import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CalendarDays, CircleCheck, Clock, Eye, Smartphone } from 'lucide-react';
import { pagosAdminApi, reservasAdminApi } from '@/api/admin';
import type { FilaPago, FilaReserva } from '@/api/admin/tipos';
import { Boton } from '@/componentes/ui/Boton';
import { Avatar, Cargando, Vacio } from '@/componentes/ui/Datos';
import { Alerta } from '@/componentes/ui/Estados';
import { Modal } from '@/componentes/ui/Modal';
import { fmtFecha, fmtHace, fmtRango24 } from '@/utilidades/fecha';
import { fmtSoles } from '@/utilidades/formato';
import { ModalValidar, TEXTO_METODO } from './ModalesReserva';

interface Aviso {
  pago: FilaPago;
  reserva: FilaReserva | null;
}

/**
 * Campana de la barra superior (SPEC_V2 §F): «Te falta revisar estos comprobantes».
 * Lista los pagos PENDIENTES (más antiguos primero) con cancha y horario de su reserva, y abre «Validar» directo.
 */
export function ModalAvisos({ abierto, alCerrar, alCambiar }: { abierto: boolean; alCerrar: () => void; alCambiar: () => void }) {
  const navigate = useNavigate();
  const [avisos, setAvisos] = useState<Aviso[] | null>(null);
  const [error, setError] = useState(false);
  const [validar, setValidar] = useState<number | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    if (!abierto) return undefined;
    let vigente = true;
    setError(false);
    Promise.all([
      pagosAdminApi.listar({ estado: 'PENDIENTE', size: 50, sort: 'fechaEnvio,asc' }),
      reservasAdminApi.listar({ estadoPago: 'EN_REVISION', size: 100 }).catch(() => null),
    ])
      .then(([pagos, reservas]) => {
        if (!vigente) return;
        const porCodigo = new Map((reservas?.contenido ?? []).map((r) => [r.codigoReserva, r]));
        const lista = [...pagos.contenido].sort((a, b) => a.fechaEnvio.localeCompare(b.fechaEnvio));
        setAvisos(lista.map((p) => ({ pago: p, reserva: porCodigo.get(p.codigoReserva) ?? null })));
      })
      .catch(() => vigente && setError(true));
    return () => {
      vigente = false;
    };
  }, [abierto, version]);

  const n = avisos?.length ?? 0;
  return (
    <>
      <Modal
        abierto={abierto && validar === null}
        alCerrar={alCerrar}
        titulo="Te falta revisar estos comprobantes"
        subtitulo={avisos ? (n ? `${n} por validar · los más antiguos primero` : 'Todo al día') : 'Cargando…'}
        busto="BU-lucho-tarjeta"
        tamano="lg"
        acciones={
          <>
            <Boton variante="fantasma" onClick={alCerrar}>
              Cerrar
            </Boton>
            <Boton
              variante="secundario"
              icono={Eye}
              onClick={() => {
                alCerrar();
                navigate('/admin/pagos?estado=PENDIENTE');
              }}
            >
              Ver en Pagos
            </Boton>
          </>
        }
      >
        {error ? (
          <Alerta tipo="error">No se pudo cargar la lista. Intenta de nuevo.</Alerta>
        ) : !avisos ? (
          <Cargando texto="Cargando comprobantes…" className="!py-8" />
        ) : n === 0 ? (
          <Vacio ilustracion="IL-23-todo-al-dia" titulo="Todo al día" texto="No hay comprobantes por revisar." compacto />
        ) : (
          <ul className="avisos-lista">
            {avisos.map(({ pago: p, reserva: r }) => {
              const nombre = `${p.cliente.nombres} ${p.cliente.apellidos}`;
              return (
                <li key={p.id} className="aviso">
                  <Avatar nombre={nombre} />
                  <div className="aviso-txt">
                    <b className="truncar">{nombre}</b>
                    <span className="aviso-datos">
                      {r ? (
                        <>
                          <span>
                            <CalendarDays className="ico" aria-hidden />
                            {`${r.cancha.split(' · ')[0]} · ${fmtFecha(r.fecha, 'corta')}`}
                          </span>
                          <span className="tnum">
                            <Clock className="ico" aria-hidden />
                            {r.horarioTexto ?? fmtRango24(r.horaInicio, r.horaFin)}
                          </span>
                        </>
                      ) : (
                        <span className="tnum">{p.codigoReserva}</span>
                      )}
                      <span>
                        <Smartphone className="ico" aria-hidden />
                        {TEXTO_METODO[p.metodo] ?? p.metodo}
                      </span>
                    </span>
                  </div>
                  <div className="aviso-monto">
                    <b className="tnum">{fmtSoles(p.monto)}</b>
                    <small>{fmtHace(p.fechaEnvio)}</small>
                  </div>
                  <Boton tamano="sm" icono={CircleCheck} onClick={() => setValidar(p.id)}>
                    Validar
                  </Boton>
                </li>
              );
            })}
          </ul>
        )}
      </Modal>
      <ModalValidar
        idPago={validar}
        alCerrar={() => setValidar(null)}
        alHecho={() => {
          setValidar(null);
          setVersion((v) => v + 1);
          alCambiar();
        }}
      />
    </>
  );
}
