import { z } from 'zod';
import {
  MSJ,
  esquemaAcepta,
  esquemaCelular,
  esquemaCorreo,
  esquemaApellidos,
  esquemaDni,
  esquemaNombres,
  esquemaOperacion,
} from './comunes';
import { rucValido } from '@/utilidades/documentos';

/** Paso 2 del asistente: datos del cliente + comprobante + consentimientos. */
export const esquemaDatosCliente = z
  .object({
    dni: esquemaDni,
    nombres: esquemaNombres,
    apellidos: esquemaApellidos,
    telefono: esquemaCelular,
    correo: esquemaCorreo,
    tipoComprobante: z.enum(['BOLETA', 'FACTURA']),
    ruc: z.string().trim().optional().default(''),
    razonSocial: z.string().trim().optional().default(''),
    direccionFiscal: z.string().trim().optional().default(''),
    aceptaTerminos: esquemaAcepta,
    aceptaPrivacidad: esquemaAcepta,
  })
  .superRefine((d, ctx) => {
    if (d.tipoComprobante !== 'FACTURA') return;
    // Factura: RUC que empiece con 10 o 20, razón social 3–150, dirección fiscal 5–200.
    if (!rucValido(d.ruc)) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['ruc'], message: MSJ.ruc });
    else if (!/^(10|20)/.test(d.ruc)) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['ruc'], message: MSJ.factura });
    if (d.razonSocial.length < 3 || d.razonSocial.length > 150) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['razonSocial'], message: MSJ.factura });
    if (d.direccionFiscal.length < 5 || d.direccionFiscal.length > 200) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['direccionFiscal'], message: MSJ.factura });
  });

export type DatosClienteEntrada = z.input<typeof esquemaDatosCliente>;
export type DatosCliente = z.output<typeof esquemaDatosCliente>;

/** Paso 3: método + (Yape/Plin) número de operación e imagen; (pasarela) tipo de pago. */
export const esquemaPago = z
  .object({
    metodoPago: z.enum(['YAPE', 'PLIN', 'TARJETA', 'MERCADO_PAGO'], { errorMap: () => ({ message: MSJ.metodo }) }),
    codigoOperacion: z.string().trim().optional().default(''),
    archivo: z.custom<File | null>().optional().nullable(),
    tipoPago: z.enum(['ADELANTO', 'TOTAL']).default('ADELANTO'),
  })
  .superRefine((d, ctx) => {
    if (d.metodoPago !== 'YAPE' && d.metodoPago !== 'PLIN') return;
    const op = esquemaOperacion.safeParse(d.codigoOperacion);
    if (!op.success) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['codigoOperacion'], message: MSJ.operacion });
    if (!(d.archivo instanceof File)) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['archivo'], message: MSJ.imagen });
  });

export type PagoEntrada = z.input<typeof esquemaPago>;
export type DatosPago = z.output<typeof esquemaPago>;

/**
 * Reenvío de comprobante desde «Mi reserva» (Yape/Plin obligatorio). Lleva otra vez boleta o factura: el back emite
 * el comprobante con los datos de ESTE envío (si no se mandan, emite boleta aunque al reservar se pidió factura).
 */
export const esquemaReenvio = z
  .object({
    metodo: z.enum(['YAPE', 'PLIN'], { errorMap: () => ({ message: MSJ.metodo }) }),
    codigoOperacion: esquemaOperacion,
    archivo: z.custom<File | null>((v) => v instanceof File, MSJ.imagen),
    tipoComprobante: z.enum(['BOLETA', 'FACTURA']).default('BOLETA'),
    ruc: z.string().trim().optional().default(''),
    razonSocial: z.string().trim().optional().default(''),
    direccionFiscal: z.string().trim().optional().default(''),
  })
  .superRefine((d, ctx) => {
    if (d.tipoComprobante !== 'FACTURA') return;
    if (!rucValido(d.ruc)) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['ruc'], message: MSJ.ruc });
    else if (!/^(10|20)/.test(d.ruc)) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['ruc'], message: MSJ.factura });
    if (d.razonSocial.length < 3 || d.razonSocial.length > 150) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['razonSocial'], message: MSJ.factura });
    if (d.direccionFiscal.length < 5 || d.direccionFiscal.length > 200) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['direccionFiscal'], message: MSJ.factura });
  });
export type ReenvioEntrada = z.input<typeof esquemaReenvio>;
export type DatosReenvio = z.output<typeof esquemaReenvio>;
