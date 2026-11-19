import { z } from 'zod';
import {
  RE_CELULAR,
  RE_CODIGO_RESERVA,
  RE_DNI,
  RE_NOMBRE,
  RE_OPERACION,
  RE_USUARIO,
  normalizarCelular,
  rucValido,
} from '@/utilidades/documentos';

/**
 * Mensajes EXACTOS del SPEC §3 (los mismos que devuelve Bean Validation en el back).
 * Si cambias uno aquí, cámbialo también en `comun` (back) y en el SPEC.
 */
export const MSJ = {
  dni: 'El DNI debe tener 8 dígitos.',
  ruc: 'El RUC no es válido.',
  factura: 'Para factura necesitas un RUC válido y la razón social.',
  nombre: 'Usa solo letras (2 a 60).',
  celular: 'Ingresa un celular peruano de 9 dígitos que empiece con 9.',
  correo: 'Ingresa un correo válido; ahí te enviamos la copia de tu reserva.',
  fecha: 'Elige una fecha entre hoy y los próximos 14 días.',
  turno: 'Ese turno ya no está disponible.',
  metodo: 'Elige un método de pago.',
  operacion: 'El número de operación tiene de 6 a 12 dígitos.',
  imagen: 'Sube una imagen JPG o PNG de hasta 5 MB.',
  motivo: 'Cuéntanos el motivo (mínimo 10 caracteres).',
  resena: 'Escribe entre 10 y 500 caracteres, sin enlaces.',
  codigo: 'El código tiene el formato ALC-XXXXXX.',
  terminos: 'Debes aceptar los términos y la política de privacidad.',
  tarjeta: 'Revisa el número de tarjeta.',
  usuario: 'Usuario de 4 a 30 caracteres en minúsculas.',
  // Complementos del front (el SPEC dice «etc.» para la tarjeta; ver README · Decisiones).
  vencimiento: 'Revisa la fecha de vencimiento (MM/AA).',
  cvv: 'Revisa el CVV (3 dígitos; 4 en Amex).',
  titular: 'Escribe el nombre como figura en la tarjeta.',
  puntuacion: 'Elige de 1 a 5 estrellas.',
  contrasena: 'Ingresa tu contraseña.',
  codigoAprobacion: 'El código de aprobación tiene 6 dígitos.',
} as const;

export const TAMANIO_MAX_IMAGEN = 5 * 1024 * 1024;
export const LADO_MIN_IMAGEN = 200;

export const esquemaDni = z.string().trim().regex(RE_DNI, MSJ.dni);

export const esquemaRuc = z.string().trim().refine(rucValido, MSJ.ruc);

/** Nombres / apellidos: 2–60, solo letras, sin espacios dobles. */
export const esquemaNombre = z
  .string()
  .trim()
  .min(2, MSJ.nombre)
  .max(60, MSJ.nombre)
  .regex(RE_NOMBRE, MSJ.nombre)
  .refine((v) => !/\s{2,}/.test(v), MSJ.nombre);

/** Celular: se acepta con espacios o +51 y se normaliza a 9 dígitos. */
export const esquemaCelular = z
  .string()
  .transform(normalizarCelular)
  .pipe(z.string().regex(RE_CELULAR, MSJ.celular));

/** Correo: RFC, ≤ 100, dominio con punto, obligatorio. */
export const esquemaCorreo = z
  .string()
  .trim()
  .min(1, MSJ.correo)
  .max(100, MSJ.correo)
  .email(MSJ.correo)
  .refine((v) => (v.split('@')[1] ?? '').includes('.'), MSJ.correo);

export const esquemaOperacion = z.string().trim().regex(RE_OPERACION, MSJ.operacion);

export const esquemaMotivo = z.string().trim().min(10, MSJ.motivo).max(255, MSJ.motivo);

export const esquemaCodigoReserva = z
  .string()
  .trim()
  .transform((v) => v.toUpperCase())
  .pipe(z.string().regex(RE_CODIGO_RESERVA, MSJ.codigo));

export const esquemaUsuario = z.string().trim().regex(RE_USUARIO, MSJ.usuario);

/** Comentario de reseña: 10–500, sin enlaces (http, www.), sin solo mayúsculas. */
export const esquemaComentarioResena = z
  .string()
  .trim()
  .min(10, MSJ.resena)
  .max(500, MSJ.resena)
  .refine((v) => !/(https?:|www\.)/i.test(v), MSJ.resena)
  .refine((v) => {
    const letras = v.replace(/[^A-Za-zÁÉÍÓÚÜÑáéíóúüñ]/g, '');
    return letras.length < 4 || letras !== letras.toUpperCase();
  }, MSJ.resena);

/** Casilla obligatoria (términos / privacidad). */
export const esquemaAcepta = z.boolean().refine((v) => v === true, MSJ.terminos);
