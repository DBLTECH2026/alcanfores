import { z } from 'zod';
import { RE_USUARIO } from '@/utilidades/documentos';
import {
  MSJ,
  esquemaAcepta,
  esquemaCorreo,
  esquemaNombres,
  esquemaApellidos,
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
    nombres: esquemaNombres,
    apellidos: esquemaApellidos,
    telefono: esquemaCelular,
    correo: esquemaCorreo,
    contrasena: esquemaClaveCliente,
    confirmacion: z.string().min(1, 'Repite la contraseña.'),
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
  .object({ actual: z.string().min(1, MSJ.contrasena), nueva: esquemaClaveCliente, confirmacion: z.string().min(1, 'Repite la contraseña.') })
  .refine((d) => d.nueva === d.confirmacion, { path: ['confirmacion'], message: 'Las contraseñas no coinciden.' });
export type CambioClaveClienteEntrada = z.input<typeof esquemaCambioClaveCliente>;

// ------------------------------ v2: recuperar contraseña ------------------------------
const RE_CORREO_SIMPLE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
/** Paso 1 del cliente: DNI o correo. */
export const esquemaRecuperarCliente = z.object({
  usuario: z
    .string()
    .trim()
    .min(1, 'Escribe tu DNI o tu correo.')
    .max(100, 'Escribe tu DNI (8 dígitos) o tu correo.')
    .refine((v) => /^\d{8}$/.test(v) || RE_CORREO_SIMPLE.test(v), 'Escribe tu DNI (8 dígitos) o tu correo.'),
});
/** Paso 1 del panel: usuario o correo. */
export const esquemaRecuperarAdmin = z.object({
  usuario: z
    .string()
    .trim()
    .min(1, 'Escribe tu usuario o tu correo.')
    .max(100, 'Escribe tu usuario o tu correo.')
    .refine((v) => RE_USUARIO.test(v.toLowerCase()) || RE_CORREO_SIMPLE.test(v), 'Escribe tu usuario (4 a 30) o tu correo.'),
});
export type RecuperarEntrada = z.input<typeof esquemaRecuperarCliente>;

/** Reglas visibles de la contraseña nueva (las mismas del back). */
export const REGLAS_CLAVE: Array<{ texto: string; cumple: (v: string) => boolean }> = [
  { texto: '8 o más caracteres', cumple: (v) => v.length >= 8 && v.length <= 64 },
  { texto: 'Una mayúscula', cumple: (v) => /[A-Z]/.test(v) },
  { texto: 'Una minúscula', cumple: (v) => /[a-z]/.test(v) },
  { texto: 'Un número', cumple: (v) => /\d/.test(v) },
];
export const esquemaNuevaClave = z
  .object({ nueva: esquemaClaveCliente, confirmacion: z.string().min(1, 'Repite la contraseña.') })
  .refine((d) => d.nueva === d.confirmacion, { path: ['confirmacion'], message: 'Las contraseñas no coinciden.' });
export type NuevaClaveEntrada = z.input<typeof esquemaNuevaClave>;
