/** Solo rutas internas del portal para `?volver=` (evita redirecciones abiertas y no manda al panel). */
export function destinoSeguro(v: string | null, porDefecto = '/mi-cuenta'): string {
  return v && v.startsWith('/') && !v.startsWith('//') && !v.startsWith('/admin') ? v : porDefecto;
}
