-- catalogo-service · Canchita (asistente con IA): tres interruptores simples del panel (MariaDB 10.4 y MySQL 8.4).
-- Responde por WhatsApp (apagado hasta que el dueño lo encienda), chat en la web y validar pagos automáticamente.
INSERT INTO configuracion (clave, valor, descripcion)
SELECT 'ia_whatsapp_activa', 'false', 'Canchita responde los chats de WhatsApp (true o false).'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM configuracion WHERE clave = 'ia_whatsapp_activa');

INSERT INTO configuracion (clave, valor, descripcion)
SELECT 'ia_web_activa', 'true', 'Canchita aparece como chat en la web (true o false).'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM configuracion WHERE clave = 'ia_web_activa');

INSERT INTO configuracion (clave, valor, descripcion)
SELECT 'ia_valida_pagos', 'true', 'Canchita valida sola los comprobantes que calzan con la reserva (true o false).'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM configuracion WHERE clave = 'ia_valida_pagos');
