# Canchita · conversación web real: reserva de 2 turnos y lectura de un comprobante

Prueba manual del 19-nov-2026 (02:30–02:41, hora de Lima) contra el back local (gateway `http://localhost:8086`, todos los servicios v2 arriba) con la sesión de Claude Code de la PC (`IA_USAR_SESION_LOCAL=true`), modelo `claude-opus-5-5` con esfuerzo `low`. Canal web por `POST /api/asistente/web/mensaje` en modo SSE (sesión `prueba-real-canchita-001`). Datos 100 % ficticios: DNI del rango 99xxxxxx (RENIEC responde «no encontrado»), celular 987 650 007, correo @example.com; las notificaciones del entorno local son SIMULADAS. Al final la reserva ALC-XC6CG4 se canceló por la API admin (`rhuaman`) con el motivo «Reserva de prueba del asistente Canchita (datos ficticios)».

## 1. Cliente

> Hola! Quiero reservar una cancha de fútbol 6 para mañana en la noche, somos 12

Herramientas usadas: `listar_canchas` (1893 ms), `ver_disponibilidad` (1907 ms)

**Canchita** (6197 ms):

    ¡Hola! Soy Canchita. Para mañana, *viernes 20 de noviembre* en la noche, la Cancha 2 (Fútbol 6, para 12 jugadores) solo tiene libre este turno:
    - *7:00–8:00 p. m.*: *S/ 90.00*
    
    Si prefieren otra hora, la Cancha 3 (Fútbol 7) está libre de *6:00 a 7:00 p. m.*, de *8:00 a 9:00 p. m.* o de *10:00 a 11:00 p. m.*, a *S/ 120.00* cada turno. ¿Cuál reservo?

Botones: [Fútbol 6 7:00 p. m.] [Fútbol 7 6:00 p. m.] [Fútbol 7 8:00 p. m.] [Fútbol 7 10:00 p. m.]

*Nota: Los horarios y precios coinciden con `GET /api/disponibilidad?fecha=2026-11-20` (Cancha 2: solo 19:00; Cancha 3: 18:00, 20:00 y 22:00).*

## 2. Cliente

> Mejor la Cancha 3, de 8 a 9 y de 10 a 11 pm. Mi DNI es 99030007

Herramientas usadas: `ver_disponibilidad` (2065 ms), `consultar_dni` (2312 ms)

**Canchita** (13169 ms):

    Listo, separo para ti en la *Cancha 3 (Fútbol 7)*, el *viernes 20 de noviembre*:
    - *8:00–9:00 p. m.*: *S/ 120.00*
    - *10:00–11:00 p. m.*: *S/ 120.00*
    Total: *S/ 240.00* (adelanto del 50 %: *S/ 120.00*)
    
    No encontré tu DNI 99030007 en RENIEC. ¿Me das tus nombres y apellidos completos?

*Nota: El 85 % del tiempo es la consulta a RENIEC (Factiliza tarda ~11 s en responder «no encontrado»). «separo para ti» antes de reservar se corrigió luego en el prompt.*

## 3. Cliente

> Me llamo Carlos Alberto Prueba Ficticio. Celular 987 650 007, correo carlos.prueba@example.com. Boleta nomás, y pago con Yape

**Canchita** (4024 ms):

    Te dejo el resumen de tu reserva:
    - Cancha: *Cancha 3 (Fútbol 7)*
    - Día: *viernes 20 de noviembre*
    - Horario: *8:00–9:00 p. m.* y *10:00–11:00 p. m.*
    - Total: *S/ 240.00*. Adelanto (50 %): *S/ 120.00*
    - Nombre: Carlos Alberto Prueba Ficticio, DNI 99030007
    - Celular: 987 650 007. Correo: carlos.prueba@example.com
    - Comprobante: boleta. Pago: Yape
    
    Si confirmas, aceptas los términos y la política de privacidad: http://localhost:5186/terminos
    ¿Confirmo?

Botones: [Sí, confirmo] [Quiero cambiar algo]

## 4. Cliente

> Sí, confirmo

Herramientas usadas: `ver_disponibilidad` (2222 ms), `crear_reserva` (4415 ms)

**Canchita** (7588 ms):

    ¡Listo, Carlos! Tu reserva quedó registrada.
    - Código: *ALC-XC6CG4*
    - Cancha 3 (Fútbol 7), *viernes 20 de noviembre*, *8:00–9:00 p. m.* y *10:00–11:00 p. m.*
    - Adelanto: *S/ 120.00*. Págalo antes de las *2:59 a. m.* de hoy.
    
    Yapea *S/ 120.00* al *900111222* (Complejo Deportivo Alcanfores) y mándame aquí la FOTO (captura) del comprobante.
    Puedes ver tu reserva en http://localhost:5186/mi-reserva?codigo=ALC-XC6CG4

Enlace: [Ver mi reserva](http://localhost:5186/mi-reserva?codigo=ALC-XC6CG4)

*Nota: Reserva verificada en la API: PENDIENTE/SIN_PAGO, 2 turnos, total S/ 240.00, adelanto S/ 120.00, cliente Carlos Alberto / Prueba Ficticio, vence a los 20 min.*

## 5. Cliente

> Listo, ya yapeé. Te mando la captura
>
> *(adjunta la foto `ejemplo-1.png`)*

Herramientas usadas: `leer_comprobante` (70 ms)

**Canchita** (11058 ms):

    Carlos, revisé tu captura y no coincide con el pago de tu reserva *ALC-XC6CG4*, así que todavía no la puedo registrar:
    - El monto es *S/ 45.00* y el adelanto es *S/ 120.00*.
    - La fecha es el 18 de noviembre a las 7:05 p. m., antes de que hicieras la reserva.
    - El destinatario no es Complejo Deportivo Alcanfores y la imagen parece de ejemplo.
    
    Yapea *S/ 120.00* al *900111222* (Complejo Deportivo Alcanfores) y mándame aquí la captura de ese pago. Tienes plazo hasta las *2:59 a. m.* de hoy.
    Si crees que hay un error, escribe al WhatsApp del equipo: 914995187.

*Nota: Lectura REAL de la foto (visión, llamada aparte): monto 45.00, operación 04821937, fecha 2026-11-18T19:05, destinatario «Complejo Deportivo (cuenta de ejemplo)», marcaDeAgua=true. En esta versión del prompt el modelo juzgó la foto por su cuenta y NO la registró; el contrato dice que se registra siempre y que las reglas en código deciden. Se ajustó el prompt («llama SIEMPRE a registrar_comprobante… tú NO juzgas el comprobante») y se reinició el servicio.*

## 6. Cliente

> Ese es el comprobante que tengo, regístralo por favor

Herramientas usadas: `registrar_comprobante` (2585 ms)

**Canchita** (4630 ms):

    Listo, Carlos. Ya registré tu comprobante para la reserva *ALC-XC6CG4*.
    El equipo lo revisará en unos minutos y te avisará por correo y WhatsApp.
    Si tienes alguna duda, escribe al WhatsApp del equipo: 914995187.

*Nota: Con el prompt ajustado: `registrar_comprobante` subió la foto a `POST /api/pagos/comprobante` (pago 197, YAPE, S/ 120.00, PENDIENTE; la reserva pasó a EN_REVISION). Las reglas dieron PENDIENTE por: «La imagen dice EJEMPLO», «El monto S/ 45.00 no es el adelanto (S/ 120.00) ni el saldo pendiente (S/ 240.00)» y «El destinatario no es el titular ni el número del complejo»; por eso NO se llamó a `validar-ia`. Canchita no explicó las reglas al cliente.*

## Latencia medida por respuesta (cliente → último mensaje, SSE)

| Turno | ms | Herramientas |
|---|---|---|
| 1 | 6197 | `listar_canchas`, `ver_disponibilidad` |
| 2 | 13169 | `ver_disponibilidad`, `consultar_dni` |
| 3 | 4024 | — |
| 4 | 7588 | `ver_disponibilidad`, `crear_reserva` |
| 5 | 11058 | `leer_comprobante` |
| 6 | 4630 | `registrar_comprobante` |
