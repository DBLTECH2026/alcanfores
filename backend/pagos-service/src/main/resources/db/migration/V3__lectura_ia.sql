-- Canchita (IA): lectura de la foto del comprobante con la que se validó el pago automáticamente.
ALTER TABLE pago ADD COLUMN lectura_ia TEXT NULL;
