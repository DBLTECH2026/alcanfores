-- Reserva cancelada con devolución: el estado del pago pasa a REEMBOLSADO y monto_pagado queda neto (lo cobrado menos
-- lo devuelto). Antes quedaba ADELANTO_PAGADO con el monto cobrado y el «saldo» calculado aunque ya no se cobraba nada.
ALTER TABLE reserva
    MODIFY estado_pago ENUM('SIN_PAGO','EN_REVISION','OBSERVADO','ADELANTO_PAGADO','PAGADO_TOTAL','REEMBOLSADO') NOT NULL;

-- Reservas ya canceladas con devolución (portal y panel): se corrigen igual que las nuevas.
UPDATE reserva r
    JOIN cancelacion c ON c.id_reserva = r.id_reserva
   SET r.monto_pagado = GREATEST(r.monto_pagado - c.monto_devolucion, 0.00),
       r.estado_pago  = 'REEMBOLSADO'
 WHERE r.estado = 'CANCELADA'
   AND c.devolucion = TRUE
   AND c.monto_devolucion > 0;
