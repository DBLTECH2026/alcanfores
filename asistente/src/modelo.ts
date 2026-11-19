// Tipos del dominio de Canchita y contrato del repositorio (MySQL en producción, memoria en pruebas).

export type Canal = "WHATSAPP" | "WEB";
export type EstadoConversacion = "ACTIVA" | "PAUSADA";
export type RolMensaje = "CLIENTE" | "CANCHITA" | "EQUIPO";

export interface DatosComprobante {
  tipo: "BOLETA" | "FACTURA";
  ruc?: string;
  razonSocial?: string;
  direccionFiscal?: string;
}

/** Lo que Canchita recuerda de la conversación además del historial. */
export interface ContextoConversacion {
  comprobante?: DatosComprobante;
  metodoPago?: string;
  telefono?: string;
  correo?: string;
}

export interface Conversacion {
  id: number;
  canal: Canal;
  clave: string;
  nombre: string | null;
  estado: EstadoConversacion;
  pausadaHasta: Date | null;
  codigoReserva: string | null;
  dni: string | null;
  contexto: ContextoConversacion;
  creadaEn: Date;
  ultimaActividad: Date;
}

/** Botones y enlace que acompañan un mensaje de Canchita en la web. */
export interface ExtrasMensaje {
  opciones?: string[];
  enlace?: { url: string; texto: string };
}

export interface Mensaje {
  id: number;
  idConversacion: number;
  rol: RolMensaje;
  texto: string;
  adjunto: string | null;
  idExterno: string | null;
  latenciaMs: number | null;
  extras: ExtrasMensaje | null;
  atendido: boolean;
  creadoEn: Date;
}

/** Lectura de la foto del comprobante (la hace la IA con visión, en una llamada aparte y sin herramientas). */
export interface LecturaComprobante {
  esComprobante: boolean;
  app: "YAPE" | "PLIN" | "OTRO" | null;
  monto: number | null;
  codigoOperacion: string | null;
  /** YYYY-MM-DDTHH:mm en hora de Lima. */
  fechaHora: string | null;
  destinatario: string | null;
  /** Dígitos visibles del número de destino (Yape suele mostrar solo los últimos 3). */
  numeroDestino: string | null;
  /** La imagen dice «EJEMPLO», «MUESTRA», «PRUEBA» o parece una plantilla. */
  marcaDeAgua: boolean;
  pareceEditada: boolean;
  descripcion: string;
}

export interface Imagen {
  id: number;
  idConversacion: number;
  mime: string;
  datos: Buffer;
  lectura: LecturaComprobante | null;
  idPago: number | null;
  validacion: string | null;
  creadoEn: Date;
}

export interface NuevoMensaje {
  idConversacion: number;
  rol: RolMensaje;
  texto: string;
  adjunto?: string | null;
  idExterno?: string | null;
  latenciaMs?: number | null;
  extras?: ExtrasMensaje | null;
  atendido?: boolean;
}

export interface ResumenConversacion {
  conversacion: Conversacion;
  ultimoMensaje: Mensaje | null;
}

export interface Repositorio {
  obtenerOCrearConversacion(canal: Canal, clave: string, nombre?: string | null): Promise<Conversacion>;
  conversacion(id: number): Promise<Conversacion | null>;
  buscarConversacion(canal: Canal, clave: string): Promise<Conversacion | null>;
  actualizarConversacion(id: number, cambios: Partial<Pick<Conversacion,
    "nombre" | "estado" | "pausadaHasta" | "codigoReserva" | "dni" | "contexto">>): Promise<Conversacion>;
  agregarMensaje(m: NuevoMensaje): Promise<Mensaje>;
  existeMensajeExterno(idExterno: string): Promise<boolean>;
  /** Anota el id de WhatsApp de un mensaje ya guardado (para reconocer el eco de lo que envía Canchita). */
  anotarIdExterno(idMensaje: number, idExterno: string): Promise<void>;
  /** Últimos N mensajes en orden cronológico. */
  historial(idConversacion: number, limite: number): Promise<Mensaje[]>;
  pendientes(idConversacion: number): Promise<Mensaje[]>;
  marcarAtendidos(ids: number[]): Promise<void>;
  guardarImagen(idConversacion: number, mime: string, datos: Buffer): Promise<Imagen>;
  imagen(id: number): Promise<Imagen | null>;
  imagenPorPago(idPago: number): Promise<Imagen | null>;
  actualizarImagen(id: number, cambios: Partial<Pick<Imagen, "lectura" | "idPago" | "validacion">>): Promise<void>;
  ultimasConversaciones(limite: number): Promise<ResumenConversacion[]>;
  contarConversaciones(): Promise<{ activas: number; pausadas: number; hoy: number }>;
  latenciaPromedioMs(ultimos: number): Promise<number | null>;
  cerrar(): Promise<void>;
}
