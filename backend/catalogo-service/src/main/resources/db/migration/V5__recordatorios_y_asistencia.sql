-- catalogo-service · v2.1: varios recordatorios y ventana de asistencia configurable (MariaDB 10.4 y MySQL 8.4).

-- recordatorio_horas (un número) pasa a recordatorio_minutos (lista «120,15»): se convierte el valor actual × 60,
-- con el máximo permitido de 1440 minutos. Si no había valor, queda el de siempre (2 horas = 120 minutos).
INSERT INTO configuracion (clave, valor, descripcion)
SELECT 'recordatorio_minutos',
       COALESCE((SELECT CAST(LEAST(CAST(valor AS UNSIGNED) * 60, 1440) AS CHAR)
                   FROM configuracion
                  WHERE clave = 'recordatorio_horas' AND valor REGEXP '^[0-9]{1,3}$' AND CAST(valor AS UNSIGNED) > 0), '120'),
       'Minutos antes del partido en que se envían los recordatorios (1 a 3, separados por comas).'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM configuracion WHERE clave = 'recordatorio_minutos');

DELETE FROM configuracion WHERE clave = 'recordatorio_horas';

-- Desde cuántos minutos antes del inicio se puede marcar la asistencia (antes eran 15 fijos en reservas-service).
INSERT INTO configuracion (clave, valor, descripcion)
SELECT 'asistencia_desde_min', '30', 'Minutos antes del inicio desde los que se puede marcar la asistencia.'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM configuracion WHERE clave = 'asistencia_desde_min');
