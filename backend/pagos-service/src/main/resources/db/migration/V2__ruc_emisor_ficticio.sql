-- El RUC «ficticio» del emisor (20612345679) existe en SUNAT: es una empresa real dada de baja de oficio.
-- Los comprobantes ya emitidos guardan los datos del emisor; se pasan al RUC ficticio 20000000001 (válido por
-- módulo 11). La cadena del QR empieza con el RUC del emisor («RUC|tipo|serie|…»), así que también se corrige.
-- El hash guardado no se recalcula: es la huella simulada del XML original (sin valor tributario).
UPDATE comprobante
   SET cadena_qr = CONCAT('20000000001', SUBSTRING(cadena_qr, 12))
 WHERE emisor_ruc = '20612345679'
   AND cadena_qr LIKE '20612345679|%';

UPDATE comprobante
   SET emisor_ruc = '20000000001'
 WHERE emisor_ruc = '20612345679';
