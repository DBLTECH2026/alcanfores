/** Espacio duro (U+00A0): une «S/» con el monto, la hora con «a. m.» y el número con su palabra para que nunca se partan. */
export const ESPACIO_DURO = '\u00a0';
/** fmtSoles(80) → «S/ 80.00» · fmtSoles(1284) → «S/ 1,284.00» · {decimales:0} → «S/ 80» (con espacio duro: no se parte). */
export function fmtSoles(n: number | string | null | undefined, { decimales = 2 } = {}): string {
  const v = Number(n ?? 0).toLocaleString('en-US', { minimumFractionDigits: decimales, maximumFractionDigits: decimales });
  return `S/${ESPACIO_DURO}${v}`;
}
/** '987654321' → «987 654 321» · {prefijo:true} → «+51 987 654 321» */
export function fmtCelular(t: string | null | undefined, { prefijo = false } = {}): string {
  const v = String(t ?? '').replace(/\D/g, '').slice(-9);
  const s = `${v.slice(0, 3)} ${v.slice(3, 6)} ${v.slice(6)}`.trim();
  return prefijo ? `+51 ${s}` : s;
}
/** «987 *** 321» para vistas públicas. */
export function enmascararCelular(t: string | null | undefined): string {
  const v = String(t ?? '').replace(/\D/g, '').slice(-9);
  return v.length === 9 ? `${v.slice(0, 3)} *** ${v.slice(6)}` : v;
}
/** «d***@correo.pe» */
export function enmascararCorreo(c: string | null | undefined): string {
  const [u = '', d = ''] = String(c ?? '').split('@');
  if (!d) return String(c ?? '');
  return `${u.slice(0, 1)}***@${d}`;
}
/** «7****321» (SPEC §2) */
export function enmascararDni(dni: string): string {
  return dni.length === 8 ? `${dni[0]}****${dni.slice(5)}` : dni;
}
export function iniciales(nombre: string | null | undefined): string {
  return String(nombre ?? '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join('')
    .toUpperCase();
}
/** «alc7k3q9p» / «alc-7k3q9p» → «ALC-7K3Q9P» */
export function normalizarCodigo(c: string): string {
  const v = c.toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (v.startsWith('ALC') && v.length > 3) return `ALC-${v.slice(3, 9)}`;
  return c.toUpperCase().trim();
}
/** Título: «rosa  HUAMÁN» → «Rosa Huamán» */
export function aTitulo(s: string): string {
  return s
    .trim()
    .replace(/\s+/g, ' ')
    .toLowerCase()
    .replace(/(^|[\s'-])(\p{L})/gu, (_m, a: string, b: string) => a + b.toUpperCase());
}
/** plural(3, 'turno', 'turnos') → «3 turnos» (con espacio duro: no se parte). */
export function plural(n: number, uno: string, varios: string): string {
  return `${n}${ESPACIO_DURO}${n === 1 ? uno : varios}`;
}
/**
 * Para textos que llegan armados del back («Paga el adelanto de S/ 135.00 antes de las 3:12 a. m.», «3 turnos»):
 * cambia por espacio duro los espacios que no deben partir la línea. Solo para pintar, no para enviar al back.
 */
export function sinCortes(texto: string | null | undefined): string {
  return String(texto ?? '')
    .replace(/S\/ (?=\d)/g, `S/${ESPACIO_DURO}`)
    .replace(/(\d) ([ap])\. m\./g, `$1${ESPACIO_DURO}$2.${ESPACIO_DURO}m.`)
    .replace(/(\d) (turnos?|h|min)\b/g, `$1${ESPACIO_DURO}$2`)
    .replace(/(\d)–(\d)/g, '$1\u2060–\u2060$2');
}

const DEPORTES: Record<string, string> = { FUTBOL_5: 'Fútbol 5', FUTBOL_6: 'Fútbol 6', FUTBOL_7: 'Fútbol 7', VOLEY: 'Vóley', BASQUET: 'Básquet', MULTIUSO: 'Vóley y básquet' };
/** 'FUTBOL_5' → «Fútbol 5» */
export function textoDeporte(d: string): string {
  return DEPORTES[d] ?? d;
}

/** «Cancha 2 · Fútbol 6» ya trae el deporte en el nombre (semilla real): no se repite. */
export function nombreConDeporte(nombre: string, deporte: string): string {
  const sinTildes = (t: string) => t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const dep = textoDeporte(deporte);
  const raiz = sinTildes(dep).split(/[ (]/)[0] ?? '';
  return sinTildes(nombre).includes(sinTildes(dep)) || (raiz.length > 3 && sinTildes(nombre).includes(raiz)) ? nombre : `${nombre} · ${dep}`;
}

/** IGV (18 %) incluido en el precio, como en las boletas: valor de venta = total / 1.18 (al céntimo) e IGV = total − valor. */
export function desgloseIgv(total: number): { base: number; igv: number } {
  const t = Math.round(Number(total || 0) * 100);
  const base = Math.round(t / 1.18);
  return { base: base / 100, igv: (t - base) / 100 };
}

/** Minutos antes del partido en texto corto: 15 → «15 min», 60 → «1 h», 90 → «1 h 30 min», 1440 → «1 día». */
export function fmtMinutosAntes(m: number): string {
  if (m >= 1440 && m % 1440 === 0) return m === 1440 ? '1 día' : `${m / 1440} días`;
  const h = Math.floor(m / 60);
  const r = m % 60;
  if (h && r) return `${h} h ${r} min`;
  return h ? `${h} h` : `${r} min`;
}
