/** Validadores de documentos peruanos y de tarjeta (mismas reglas que `comun` en el back, SPEC §3). */

export const RE_DNI = /^\d{8}$/;
export const RE_RUC = /^(10|15|16|17|20)\d{9}$/;
export const RE_CELULAR = /^9\d{8}$/;
export const RE_CODIGO_RESERVA = /^ALC-[A-HJ-NP-Z2-9]{6}$/;
export const RE_NOMBRE = /^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ' -]+$/;
export const RE_OPERACION = /^\d{6,12}$/;
export const RE_USUARIO = /^[a-z][a-z0-9._]{3,29}$/;

/** RUC: prefijo válido + dígito verificador módulo 11 (pesos 5,4,3,2,7,6,5,4,3,2; d = 11 − (suma % 11); 10→0, 11→1). */
export function rucValido(ruc: string): boolean {
  if (!RE_RUC.test(ruc)) return false;
  const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  const suma = pesos.reduce((acc, p, i) => acc + p * Number(ruc[i]), 0);
  let d = 11 - (suma % 11);
  if (d === 10) d = 0;
  if (d === 11) d = 1;
  return d === Number(ruc[10]);
}

/** Acepta «987 654 321», «+51 987654321», «51987654321» → «987654321». */
export function normalizarCelular(v: string): string {
  let d = String(v ?? '').replace(/[\s()-]/g, '');
  if (d.startsWith('+51')) d = d.slice(3);
  else if (d.startsWith('51') && d.length === 11) d = d.slice(2);
  return d;
}

export type MarcaTarjeta = 'VISA' | 'MASTERCARD' | 'AMEX' | 'DINERS';

/** Visa 4 · Mastercard 51–55 / 2221–2720 · Amex 34/37 · Diners 36/38 (SPEC §3). */
export function detectarMarca(numero: string): MarcaTarjeta | null {
  const n = numero.replace(/\D/g, '');
  if (/^4/.test(n)) return 'VISA';
  if (/^5[1-5]/.test(n)) return 'MASTERCARD';
  const p4 = Number(n.slice(0, 4));
  if (n.length >= 4 && p4 >= 2221 && p4 <= 2720) return 'MASTERCARD';
  if (/^3[47]/.test(n)) return 'AMEX';
  if (/^3[68]/.test(n)) return 'DINERS';
  return null;
}

export function luhnValido(numero: string): boolean {
  const n = numero.replace(/\D/g, '');
  if (n.length < 13 || n.length > 19) return false;
  let suma = 0;
  let doble = false;
  for (let i = n.length - 1; i >= 0; i -= 1) {
    let d = Number(n[i]);
    if (doble) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    suma += d;
    doble = !doble;
  }
  return suma % 10 === 0;
}

/** Agrupa el número para mostrarlo: Amex 4-6-5, el resto de 4 en 4. */
export function formatearTarjeta(numero: string): string {
  const n = numero.replace(/\D/g, '').slice(0, 19);
  if (detectarMarca(n) === 'AMEX') return [n.slice(0, 4), n.slice(4, 10), n.slice(10, 15)].filter(Boolean).join(' ');
  return n.replace(/(.{4})(?=.)/g, '$1 ');
}

/** «MM/AA» futuro (vence a fin de mes). */
export function vencimientoValido(mmaa: string, hoy: Date = new Date()): boolean {
  const m = /^(\d{2})\/(\d{2})$/.exec(mmaa);
  if (!m) return false;
  const mes = Number(m[1]);
  const anio = 2000 + Number(m[2]);
  if (mes < 1 || mes > 12) return false;
  const finDeMes = new Date(anio, mes, 1);
  return finDeMes > hoy;
}

/** Bytes mágicos: JPG (FF D8 FF) o PNG (89 50 4E 47). */
export async function tipoPorBytes(archivo: Blob): Promise<'image/jpeg' | 'image/png' | null> {
  const b = new Uint8Array(await archivo.slice(0, 8).arrayBuffer());
  if (b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff) return 'image/jpeg';
  if (b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47) return 'image/png';
  return null;
}

/** Tamaño real de una imagen (para exigir lado mínimo de 200 px). */
export function medirImagen(url: string): Promise<{ ancho: number; alto: number }> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve({ ancho: img.naturalWidth, alto: img.naturalHeight });
    img.onerror = () => reject(new Error('imagen'));
    img.src = url;
  });
}
