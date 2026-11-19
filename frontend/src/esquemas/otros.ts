import { z } from 'zod';
import {
  MSJ,
  esquemaAcepta,
  esquemaCorreo,
  esquemaNombre,
  esquemaCelular,
  esquemaCodigoReserva,
  esquemaComentarioResena,
  esquemaDni,
  esquemaMotivo,
  esquemaUsuario,
} from './comunes';
import { detectarMarca, luhnValido, vencimientoValido } from '@/utilidades/documentos';

/** Consulta de «Mi reserva»: código + DNI. */
export const esquemaConsulta = z.object({ codigo: esquemaCodigoReserva, dni: esquemaDni });
export type ConsultaEntrada = z.input<typeof esquemaConsulta>;

/** Cancelación por el cliente. */
export const esquemaCancelacion = z.object({ motivo: esquemaMotivo });
export type DatosCancelacion = z.infer<typeof esquemaCancelacion>;

/** «Califica tu experiencia». */
export const esquemaResena = z.object({
  codigoReserva: esquemaCodigoReserva,
  dni: esquemaDni,
  puntuacion: z.number({ invalid_type_error: MSJ.puntuacion }).int(MSJ.puntuacion).min(1, MSJ.puntuacion).max(5, MSJ.puntuacion),
  comentario: esquemaComentarioResena,
});
export type ResenaEntrada = z.input<typeof esquemaResena>;

/** v2: reseña con sesión de cliente (el DNI sale del token). */
export const esquemaResenaCuenta = z.object({
  codigoReserva: esquemaCodigoReserva,
  puntuacion: z.number({ invalid_type_error: MSJ.puntuacion }).int(MSJ.puntuacion).min(1, MSJ.puntuacion).max(5, MSJ.puntuacion),
  comentario: esquemaComentarioResena,
});
export type ResenaCuentaEntrada = z.input<typeof esquemaResenaCuenta>;

// ------------------------------ v2: cuentas de clientes ------------------------------
/** Contraseña del cliente: misma regla que la del personal (8 a 64, mayúscula, minúscula y número). */
export const MSJ_CLAVE_CLIENTE = 'Usa de 8 a 64 caracteres, con una mayúscula, una minúscula y un número.';
export const esquemaClaveCliente = z
  .string()
  .min(8, MSJ_CLAVE_CLIENTE)
  .max(64, MSJ_CLAVE_CLIENTE)
  .refine((v) => /[A-Z]/.test(v) && /[a-z]/.test(v) && /\d/.test(v), MSJ_CLAVE_CLIENTE);

/** Ingreso del cliente: DNI o correo + contraseña. */
export const esquemaIngresoCliente = z.object({
  usuario: z
    .string()
    .trim()
    .min(1, 'Escribe tu DNI o tu correo.')
    .refine((v) => /^\d{8}$/.test(v) || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v), 'Escribe tu DNI (8 dígitos) o tu correo.'),
  contrasena: z.string().min(1, MSJ.contrasena).max(64, MSJ.contrasena),
});
export type IngresoClienteEntrada = z.input<typeof esquemaIngresoCliente>;

/** Ingreso al panel. */
export const esquemaLogin = z.object({
  usuario: esquemaUsuario,
  contrasena: z.string().min(1, MSJ.contrasena).max(64, MSJ.contrasena),
});
export type DatosLogin = z.infer<typeof esquemaLogin>;

const SOLO_LETRAS = /^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ .'-]+$/;

/** Pasarela beta: tarjeta (Luhn, 13–19 dígitos, marca; MM/AA futuro; CVV 3 o 4 en Amex; titular 3–60 letras). */
export const esquemaTarjeta = z
  .object({
    numero: z
      .string()
      .transform((v) => v.replace(/\s/g, ''))
      .pipe(
        z
          .string()
          .regex(/^\d{13,19}$/, MSJ.tarjeta)
          .refine(luhnValido, MSJ.tarjeta)
          .refine((v) => detectarMarca(v) !== null, MSJ.tarjeta),
      ),
    titular: z.string().trim().min(3, MSJ.titular).max(60, MSJ.titular).regex(SOLO_LETRAS, MSJ.titular),
    vencimiento: z.string().trim().refine((v) => vencimientoValido(v), MSJ.vencimiento),
    cvv: z.string().trim().regex(/^\d{3,4}$/, MSJ.cvv),
    correo: z.string().trim().email('Ingresa un correo válido.').max(100, 'Ingresa un correo válido.'),
  })
  .superRefine((d, ctx) => {
    const largo = detectarMarca(d.numero) === 'AMEX' ? 4 : 3;
    if (d.cvv.length !== largo) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['cvv'], message: MSJ.cvv });
  });
export type TarjetaEntrada = z.input<typeof esquemaTarjeta>;

/** Pasarela beta: Yape. */
export const esquemaYapeBeta = z.object({
  celular: esquemaCelular,
  codigoAprobacion: z.string().trim().regex(/^\d{6}$/, MSJ.codigoAprobacion),
});
export type YapeBetaEntrada = z.input<typeof esquemaYapeBeta>;


/** Crear cuenta: DNI (RENIEC), nombres, celular, correo, contraseña + confirmación y aceptación. */
export const esquemaRegistroCliente = z
  .object({
    dni: esquemaDni,
    nombres: esquemaNombre,
    apellidos: esquemaNombre,
    telefono: esquemaCelular,
    correo: esquemaCorreo,
    contrasena: esquemaClaveCliente,
    confirmacion: z.string(),
    aceptaTerminos: esquemaAcepta,
    aceptaPrivacidad: esquemaAcepta,
  })
  .refine((d) => d.contrasena === d.confirmacion, { path: ['confirmacion'], message: 'Las contraseñas no coinciden.' });
export type RegistroClienteEntrada = z.input<typeof esquemaRegistroCliente>;

/** Mis datos: celular y correo (nombres y DNI vienen de RENIEC). */
export const esquemaDatosCuenta = z.object({ telefono: esquemaCelular, correo: esquemaCorreo });
export type DatosCuentaEntrada = z.input<typeof esquemaDatosCuenta>;

/** Cambiar contraseña del cliente. */
export const esquemaCambioClaveCliente = z
  .object({ actual: z.string().min(1, MSJ.contrasena), nueva: esquemaClaveCliente, confirmacion: z.string() })
  .refine((d) => d.nueva === d.confirmacion, { path: ['confirmacion'], message: 'Las contraseñas no coinciden.' });
export type CambioClaveClienteEntrada = z.input<typeof esquemaCambioClaveCliente>;
