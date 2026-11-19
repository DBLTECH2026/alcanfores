-- El RUC «ficticio» del emisor (20612345679) existe en SUNAT: es una empresa real dada de baja de oficio.
-- Se reemplaza por 20000000001 (válido por módulo 11 y claramente ficticio). Solo si sigue con el valor viejo:
-- si el administrador ya lo cambió desde el panel, se respeta.
UPDATE configuracion
   SET valor = '20000000001'
 WHERE clave = 'ruc_emisor'
   AND valor = '20612345679';
