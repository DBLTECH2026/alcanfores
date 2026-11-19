-- Línea de tiempo: el texto del cliente o del administrador ya terminaba en punto y se le agregaba otro («lluvia..»).
-- Se corrigen los hitos ya guardados (los nuevos se arman sin doble punto con ReglasReserva.frase).
UPDATE reserva_historial
   SET detalle = REPLACE(detalle, '.. Reenvíalo antes del', '. Reenvíalo antes del')
 WHERE evento = 'OBSERVADO'
   AND detalle LIKE '%.. Reenvíalo antes del%';

UPDATE reserva_historial
   SET detalle = REPLACE(detalle, '.. Devolución de', '. Devolución de')
 WHERE evento = 'CANCELADA'
   AND detalle LIKE '%.. Devolución de%';

-- Doble punto al final (cancelación sin devolución u observación sin plazo). Se respetan los «...» de un texto recortado.
UPDATE reserva_historial
   SET detalle = LEFT(detalle, CHAR_LENGTH(detalle) - 1)
 WHERE evento IN ('CANCELADA', 'OBSERVADO')
   AND detalle LIKE '%..'
   AND detalle NOT LIKE '%...';
