import { useState } from 'react';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { Ilustracion } from '@/componentes/ui/Marca';
import { AsistenteRecuperar, ILUSTRACION_PASO, type PasoRecuperar } from '@/componentes/cuenta/AsistenteRecuperar';

/** /recuperar — el cliente recupera su contraseña con un código por correo o WhatsApp. */
export default function Recuperar() {
  useTituloPagina('Recuperar contraseña');
  const [paso, setPaso] = useState<PasoRecuperar>(1);
  return (
    <div className="contenedor py-8 lg:py-14">
      <div className="cuenta-caja">
        <div className="cuenta-il solo-tablet-arriba" aria-hidden>
          <Ilustracion id={ILUSTRACION_PASO[paso]} carga="eager" />
        </div>
        <div className="cuenta-form">
          <AsistenteRecuperar tipoCuenta="CLIENTE" rutaIngresar="/ingresar" alCambiarPaso={setPaso} />
        </div>
      </div>
    </div>
  );
}
