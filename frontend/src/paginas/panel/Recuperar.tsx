import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '@/contexto/AuthContext';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { Ilustracion, Logo, PatronCancha } from '@/componentes/ui/Marca';
import { AsistenteRecuperar, ILUSTRACION_PASO, type PasoRecuperar } from '@/componentes/cuenta/AsistenteRecuperar';

/** /admin/recuperar — mismo armazón del login del panel (lado oscuro con la ilustración del paso). */
export default function RecuperarPanel() {
  useTituloPagina('Recuperar contraseña');
  const { autenticado } = useAuth();
  const [paso, setPaso] = useState<PasoRecuperar>(1);
  if (autenticado) return <Navigate to="/admin/cuenta" replace />;
  return (
    <div className="pantalla grid min-h-[100dvh] grid-rows-[auto_1fr] lg:grid-rows-1 lg:grid-cols-2">
      {/* En celular la franja mide 100 px fijos, igual que en Login. */}
      <section className="relative overflow-hidden bg-noche text-white sobre-oscuro grid content-center h-[100px] gap-6 px-6 lg:h-auto lg:content-between lg:px-12 lg:py-12">
        <PatronCancha className="absolute -left-24 -bottom-24 w-[520px] text-white opacity-[.06] pointer-events-none lg:left-auto lg:-right-24" />
        <div className="relative flex items-center justify-between gap-3">
          <Logo variante="horizontal-oscuro" className="h-[26px] lg:h-[34px]" />
        </div>
        <Ilustracion id={ILUSTRACION_PASO[paso]} carga="eager" className="relative hidden lg:block w-[440px] h-[440px] justify-self-center" />
        <span className="hidden lg:block" />
      </section>
      <main id="contenido" className="grid place-items-center px-5 py-8 lg:px-12">
        <div className="w-full max-w-[420px]">
          <AsistenteRecuperar tipoCuenta="ADMIN" rutaIngresar="/admin/login" alCambiarPaso={setPaso} claseIlustracionMovil="lg:hidden" />
        </div>
      </main>
    </div>
  );
}
