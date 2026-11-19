import { useLocation } from 'react-router-dom';
import { House } from 'lucide-react';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { BotonEnlace } from '@/componentes/ui/Boton';
import { Vacio } from '@/componentes/ui/Datos';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { itemDeRuta } from '@/rutas/menuPanel';

/** Marcador de las pantallas del panel que construye el siguiente agente (se reemplaza en src/rutas/router.tsx). */
export default function EnConstruccion() {
  const { pathname } = useLocation();
  const item = itemDeRuta(pathname);
  useTituloPagina(item?.texto ?? 'Panel');
  return (
    <>
      <CabeceraPagina titulo={item?.texto ?? 'Panel'} subtitulo="Pantalla en construcción" />
      <div className="card">
        <Vacio
          ilustracion="IL-26-sin-turnos"
          titulo="Muy pronto"
          texto="Esta sección se está construyendo."
          acciones={
            <BotonEnlace to="/admin/hoy" icono={House} variante="secundario">
              Ir a Hoy
            </BotonEnlace>
          }
        />
      </div>
    </>
  );
}
