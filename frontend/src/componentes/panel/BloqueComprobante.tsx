import { useState } from 'react';
import type { UseFormRegisterReturn } from 'react-hook-form';
import { Building2, FileText, MapPin, Receipt, Search } from 'lucide-react';
import { documentosApi } from '@/api/servicios';
import { esErrorApi } from '@/api/cliente';
import { CampoTexto } from '@/componentes/ui/Campo';
import { Segmentado } from '@/componentes/ui/Datos';
import { Spinner } from '@/componentes/ui/Datos';
import { MSJ } from '@/esquemas/comunes';
import { rucValido } from '@/utilidades/documentos';

type CampoFactura = 'ruc' | 'razonSocial' | 'direccionFiscal';

/**
 * Boleta o factura (SPEC §3): para factura, RUC con lupa SUNAT (llena razón social y dirección) o escritos a mano.
 * Se usa en «Cobrar saldo» y en la reserva manual con pago inicial.
 */
export function BloqueComprobante({
  tipo,
  alTipo,
  registrar,
  errores,
  ruc,
  alLlenar,
}: {
  tipo: 'BOLETA' | 'FACTURA';
  alTipo: (t: 'BOLETA' | 'FACTURA') => void;
  registrar: (c: CampoFactura) => UseFormRegisterReturn;
  errores: Partial<Record<CampoFactura, string | undefined>>;
  ruc: string;
  alLlenar: (d: { razonSocial: string; direccionFiscal: string }) => void;
}) {
  const [buscando, setBuscando] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);
  const [verificado, setVerificado] = useState(false);

  const buscar = async () => {
    setAviso(null);
    if (!rucValido(ruc)) {
      setAviso(MSJ.ruc);
      return;
    }
    setBuscando(true);
    try {
      const d = await documentosApi.ruc(ruc);
      alLlenar({ razonSocial: d.razonSocial, direccionFiscal: d.direccion ?? '' });
      setVerificado(true);
    } catch (e) {
      setVerificado(false);
      setAviso(esErrorApi(e) && e.status === 404 ? 'SUNAT no encontró ese RUC. Escríbelo a mano.' : 'SUNAT no responde. Escribe la razón social a mano.');
    } finally {
      setBuscando(false);
    }
  };

  return (
    <div className="grid gap-3">
      <Segmentado
        etiqueta="Comprobante"
        bloque
        pequeno
        valor={tipo}
        alCambiar={alTipo}
        opciones={[
          { valor: 'BOLETA', texto: 'Boleta', icono: Receipt },
          { valor: 'FACTURA', texto: 'Factura', icono: FileText },
        ]}
      />
      {tipo === 'FACTURA' && (
        <div className="grid gap-3">
          <CampoTexto
            label="RUC"
            requerido
            inputMode="numeric"
            maxLength={11}
            icono={Building2}
            valido={verificado}
            error={errores.ruc ?? aviso ?? undefined}
            accion={
              <button type="button" className="control-boton" aria-label="Buscar RUC en SUNAT" onClick={buscar} disabled={buscando}>
                {buscando ? <Spinner /> : <Search className="ico ico-18" aria-hidden />}
              </button>
            }
            {...registrar('ruc')}
          />
          <CampoTexto label="Razón social" requerido error={errores.razonSocial} {...registrar('razonSocial')} />
          <CampoTexto label="Dirección fiscal" requerido icono={MapPin} error={errores.direccionFiscal} {...registrar('direccionFiscal')} />
        </div>
      )}
    </div>
  );
}
