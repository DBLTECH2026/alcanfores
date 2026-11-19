// Validación de imágenes de comprobantes: JPG o PNG por bytes mágicos, hasta 5 MB (SPEC §3).

export const MAX_BYTES_IMAGEN = 5 * 1024 * 1024;

export function tipoPorBytes(datos: Buffer): "image/jpeg" | "image/png" | null {
  if (datos.length >= 3 && datos[0] === 0xff && datos[1] === 0xd8 && datos[2] === 0xff) return "image/jpeg";
  if (datos.length >= 8 && datos.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return "image/png";
  return null;
}

/** Decodifica un base64 (con o sin prefijo data:) y valida tipo y tamaño. */
export function leerImagenBase64(texto: string): { datos: Buffer; mime: "image/jpeg" | "image/png" } | { error: string } {
  const limpio = texto.replace(/^data:[^;]+;base64,/, "").replace(/\s/g, "");
  if (!/^[A-Za-z0-9+/]+=*$/.test(limpio)) return { error: "Sube una imagen JPG o PNG de hasta 5 MB." };
  const datos = Buffer.from(limpio, "base64");
  if (datos.length === 0 || datos.length > MAX_BYTES_IMAGEN) return { error: "Sube una imagen JPG o PNG de hasta 5 MB." };
  const mime = tipoPorBytes(datos);
  if (!mime) return { error: "Sube una imagen JPG o PNG de hasta 5 MB." };
  return { datos, mime };
}
