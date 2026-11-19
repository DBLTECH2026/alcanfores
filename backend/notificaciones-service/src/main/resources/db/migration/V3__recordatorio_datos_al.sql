-- notificaciones-service · v3 (válido en MariaDB 10.4 y MySQL 8.4)
-- Cambio de horario de una reserva (19/11/2026): reserva.confirmada y reserva.reprogramada llegan por colas distintas y
-- pueden atenderse en otro orden. datos_al = cuándo ocurrió el evento del que salen el inicio y los datos del
-- recordatorio; un evento más viejo no los reemplaza (así el recordatorio nunca vuelve al horario anterior).
-- NULL en los recordatorios que ya existían (cualquier evento los puede actualizar).
ALTER TABLE recordatorio ADD COLUMN datos_al DATETIME NULL AFTER datos;
