import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { House } from 'lucide-react';
import type { Rol } from '@/api/tipos';
import { useAuth } from '@/contexto/AuthContext';
import { Vacio } from '@/componentes/ui/Datos';
import { BotonEnlace } from '@/componentes/ui/Boton';

/** Pantalla de acceso denegado (IL-29). */
export function SinPermiso() {
  return (
    <div className="card">
      <Vacio
        ilustracion="IL-29-sin-permiso"
        titulo="Sin permiso"
        texto="Tu rol no puede usar esta sección."
        acciones={
          <BotonEnlace to="/admin/hoy" icono={House} variante="secundario">
            Ir a Hoy
          </BotonEnlace>
        }
      />
    </div>
  );
}

/**
 * Protege rutas del panel: sin sesión → /admin/login (recuerda a dónde iba);
 * con sesión pero sin el rol → «Sin permiso». Sin `roles` basta con estar autenticado.
 */
export function RutaProtegida({ roles, children }: { roles?: Rol[]; children?: ReactNode }) {
  const { autenticado, usuario } = useAuth();
  const location = useLocation();
  if (!autenticado || !usuario) return <Navigate to="/admin/login" replace state={{ desde: location.pathname + location.search }} />;
  if (roles && !roles.includes(usuario.rol)) return <SinPermiso />;
  return <>{children ?? <Outlet />}</>;
}
