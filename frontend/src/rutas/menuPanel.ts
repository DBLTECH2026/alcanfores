import {
  Activity,
  CalendarDays,
  ChartColumn,
  CirclePlus,
  ClipboardList,
  LayoutDashboard,
  LayoutGrid,
  MessagesSquare,
  Receipt,
  ScrollText,
  Settings,
  UsersRound,
  UserCog,
  type LucideIcon,
} from 'lucide-react';
import type { Rol } from '@/api/tipos';
import type { IdBusto } from '@/utilidades/recursos';

/**
 * Menú del panel (SPEC §6 y matriz de permisos §2). v2: sin «Reseñas» (ahora viven en la ficha de cada cancha). ÚNICA fuente: la barra lateral, el menú móvil,
 * las migas, el busto de la cabecera y las rutas protegidas salen de aquí.
 * Para agregar una pantalla: crea la página y pon su `elemento` en src/rutas/router.tsx (mismo `ruta`).
 */
export interface ItemMenu {
  clave: string;
  texto: string;
  ruta: string;
  icono: LucideIcon;
  roles: Rol[];
  seccion: 'Operación' | 'Caja y clientes' | 'Gestión' | 'Sistema';
  busto: IdBusto;
  /** Muestra el contador de comprobantes por validar. */
  contador?: 'porValidar';
}

const TODOS: Rol[] = ['ADMIN', 'RECEPCION'];
const ADMIN: Rol[] = ['ADMIN'];

export const MENU_PANEL: ItemMenu[] = [
  { clave: 'hoy', texto: 'Hoy', ruta: '/admin/hoy', icono: LayoutDashboard, roles: TODOS, seccion: 'Operación', busto: 'BU-rosa-cafe' },
  { clave: 'reservas', texto: 'Reservas', ruta: '/admin/reservas', icono: ClipboardList, roles: TODOS, seccion: 'Operación', busto: 'BU-rosa-panel', contador: 'porValidar' },
  { clave: 'agenda', texto: 'Agenda', ruta: '/admin/agenda', icono: CalendarDays, roles: TODOS, seccion: 'Operación', busto: 'BU-rosa-agenda' },
  { clave: 'nueva', texto: 'Nueva reserva', ruta: '/admin/nueva-reserva', icono: CirclePlus, roles: TODOS, seccion: 'Operación', busto: 'BU-rosa-laptop' },
  { clave: 'pagos', texto: 'Pagos y comprobantes', ruta: '/admin/pagos', icono: Receipt, roles: TODOS, seccion: 'Caja y clientes', busto: 'BU-lucho-tarjeta' },
  { clave: 'clientes', texto: 'Clientes', ruta: '/admin/clientes', icono: UsersRound, roles: TODOS, seccion: 'Caja y clientes', busto: 'BU-rosa-panel' },
  { clave: 'notificaciones', texto: 'Notificaciones', ruta: '/admin/notificaciones', icono: MessagesSquare, roles: TODOS, seccion: 'Caja y clientes', busto: 'BU-rosa-laptop' },
  { clave: 'canchas', texto: 'Canchas', ruta: '/admin/canchas', icono: LayoutGrid, roles: ADMIN, seccion: 'Gestión', busto: 'BU-rosa-gorra' },
  { clave: 'reportes', texto: 'Reportes', ruta: '/admin/reportes', icono: ChartColumn, roles: ADMIN, seccion: 'Gestión', busto: 'BU-rosa-pizarra' },
  { clave: 'usuarios', texto: 'Usuarios', ruta: '/admin/usuarios', icono: UserCog, roles: ADMIN, seccion: 'Sistema', busto: 'BU-rosa-panel' },
  { clave: 'bitacora', texto: 'Bitácora', ruta: '/admin/bitacora', icono: ScrollText, roles: ADMIN, seccion: 'Sistema', busto: 'BU-rosa-pizarra' },
  { clave: 'configuracion', texto: 'Configuración', ruta: '/admin/configuracion', icono: Settings, roles: ADMIN, seccion: 'Sistema', busto: 'BU-rosa-agenda' },
  { clave: 'estado', texto: 'Estado del sistema', ruta: '/admin/estado', icono: Activity, roles: ADMIN, seccion: 'Sistema', busto: 'BU-lucho-silbato' },
];

export function menuPara(rol: Rol | undefined): ItemMenu[] {
  return rol ? MENU_PANEL.filter((i) => i.roles.includes(rol)) : [];
}

/** Ítem del menú que corresponde a una ruta (el prefijo más largo). */
export function itemDeRuta(pathname: string): ItemMenu | undefined {
  return [...MENU_PANEL].sort((a, b) => b.ruta.length - a.ruta.length).find((i) => pathname === i.ruta || pathname.startsWith(`${i.ruta}/`));
}
