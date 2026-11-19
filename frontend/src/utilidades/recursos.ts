/** Rutas de los recursos gráficos (copiados del prototipo aprobado a /public). */
export type IdIlustracion =
  | 'IL-01-hero'
  | 'IL-02-paso1-horario'
  | 'IL-03-paso2-datos'
  | 'IL-04-paso3-comprobante'
  | 'IL-05-futbol'
  | 'IL-06-voley'
  | 'IL-07-basquet'
  | 'IL-08-sin-horarios'
  | 'IL-09-mantenimiento'
  | 'IL-10-conflicto'
  | 'IL-11-en-revision'
  | 'IL-12-confirmada'
  | 'IL-13-rechazado'
  | 'IL-14-cancelada'
  | 'IL-15-partido-jugado'
  | 'IL-16-consulta'
  | 'IL-17-no-encontrada'
  | 'IL-18-404-fuera-de-juego'
  | 'IL-19-sin-conexion'
  | 'IL-20-privacidad'
  | 'IL-21-compartir'
  | 'IL-22-ingreso-admin'
  | 'IL-23-todo-al-dia'
  | 'IL-24-sin-resultados'
  | 'IL-25-sin-canchas'
  | 'IL-26-sin-turnos'
  | 'IL-27-reporte-vacio'
  | 'IL-28-sesion-expirada'
  | 'IL-29-sin-permiso'
  | 'IL-30-hoy-sin-reservas';

export type IdBusto =
  | 'BU-rosa-agenda'
  | 'BU-rosa-cafe'
  | 'BU-rosa-gorra'
  | 'BU-rosa-laptop'
  | 'BU-rosa-panel'
  | 'BU-rosa-pizarra'
  | 'BU-lucho-silbato'
  | 'BU-lucho-tarjeta';

export const rutaIlustracion = (id: IdIlustracion) => `/ilustraciones/${id}.webp`;
export const rutaBusto = (id: IdBusto) => `/bustos/${id}.webp`;

export const LOGOS = {
  horizontal: '/marca/logo-alcanfores-horizontal-sin-lema.webp',
  'horizontal-oscuro': '/marca/logo-alcanfores-horizontal-sin-lema-sobre-oscuro.webp',
  completo: '/marca/logo-alcanfores.webp',
  'completo-oscuro': '/marca/logo-alcanfores-sobre-oscuro.webp',
  isotipo: '/marca/isotipo-alcanfores.webp',
} as const;
export type VarianteLogo = keyof typeof LOGOS;

/** true cuando la app corre con MSW (VITE_MOCK=true). */
export const MODO_MOCK = import.meta.env.VITE_MOCK === 'true';
