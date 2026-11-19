-- catalogo-service · datos de demostración
-- Complejo real (Carretera Central km 11.5, Santa Clara, Ate); precios, Yape/Plin, RUC y razón social FICTICIOS.
-- Reseñas y autores ficticios. Las fotos las copia el componente SemillaFotos al arrancar.

INSERT INTO cancha (id_cancha, slug, nombre, deporte, superficie, capacidad, descripcion, descripcion_larga, techada,
                    iluminacion_led, medidas, caracteristicas, precio_dia, precio_noche, estado, orden, creado_en, actualizado_en)
VALUES
(1, 'cancha-1-futbol-5', 'Cancha 1 · Fútbol 5', 'FUTBOL_5', 'Grass sintético', 10,
 'Cancha techada de fútbol 5 con grass sintético e iluminación LED. Ideal para la pichanga de la semana con tus amigos o compañeros de trabajo.',
 'La Cancha 1 es la favorita para la pichanga de cinco contra cinco. Está completamente techada con estructura metálica, así que se juega igual con el sol fuerte de la tarde o con la garúa de invierno. El grass sintético de última generación tiene relleno de caucho que amortigua las caídas y no resbala, y las mallas perimetrales evitan que la pelota salga a cada rato.\n\nDe noche la iluminación LED cubre toda la cancha de manera pareja, sin sombras ni reflejos, para que veas cada pase como de día. Al lado tienes camerinos con duchas, estacionamiento dentro del complejo y la explanada para quedarte después del partido.\n\nEstamos en la Carretera Central km 11.5, Santa Clara (Ate), bajando en el paradero Caseta y a dos cuadras de la UTP Santa Clara. Reserva en línea, paga el adelanto con Yape o Plin y recibe la confirmación por WhatsApp y correo.',
 TRUE, TRUE, '30 x 18 m',
 '["Techada con estructura metálica", "Grass sintético con caucho", "Iluminación LED", "Camerinos con duchas", "Estacionamiento", "Explanada para eventos", "Mallas perimetrales"]',
 60.00, 80.00, 'ACTIVA', 1, '2026-09-01 09:00:00', '2026-09-01 09:00:00'),
(2, 'cancha-2-futbol-6', 'Cancha 2 · Fútbol 6', 'FUTBOL_6', 'Grass sintético', 12,
 'Cancha techada de fútbol 6, un poco más amplia, con grass sintético, iluminación LED y camerinos. Perfecta para equipos de seis y campeonatos internos.',
 'La Cancha 2 está pensada para equipos de seis jugadores que buscan más espacio para tocar y desmarcarse. Es techada, con grass sintético de pelo largo y relleno de caucho, arcos reglamentarios con redes en buen estado y mallas perimetrales altas.\n\nLa iluminación LED de alto rendimiento hace que los turnos de noche se vean tan claros como los de día. Muchas empresas y promociones de la UTP la eligen para sus campeonatos internos porque, terminado el partido, pueden pasar a la explanada del complejo para la premiación o un compartir.\n\nCuenta con camerinos con duchas, estacionamiento propio y fácil acceso desde la Carretera Central (paradero Caseta, Santa Clara, Ate). Reserva tu turno en minutos y recibe la constancia con código QR en tu correo y WhatsApp.',
 TRUE, TRUE, '40 x 20 m',
 '["Techada con estructura metálica", "Grass sintético con caucho", "Iluminación LED", "Camerinos con duchas", "Estacionamiento", "Explanada para eventos", "Arcos reglamentarios"]',
 70.00, 90.00, 'ACTIVA', 2, '2026-09-01 09:00:00', '2026-09-01 09:00:00'),
(3, 'cancha-3-futbol-7', 'Cancha 3 · Fútbol 7', 'FUTBOL_7', 'Grass sintético', 14,
 'Nuestra cancha más grande: fútbol 7 techado, grass sintético e iluminación LED. Ideal para partidos más tácticos, ligas de barrio y campeonatos.',
 'La Cancha 3 es la más amplia del complejo y la preferida para el fútbol 7: hay espacio para armar líneas, abrir la cancha por las bandas y jugar partidos más tácticos. Está techada, con grass sintético resistente y relleno de caucho, y tiene bancas para los suplentes a un costado.\n\nSu iluminación LED cubre toda la superficie de forma uniforme, por eso es la elegida para las ligas de barrio y los campeonatos nocturnos. Después del partido, el equipo puede usar los camerinos con duchas y reunirse en la explanada del complejo, que también se alquila para eventos.\n\nEstamos en la Carretera Central km 11.5, Santa Clara (Ate), a dos cuadras de la UTP Santa Clara, con estacionamiento dentro del local. Reserva en línea y asegura tu horario con solo el adelanto.',
 TRUE, TRUE, '50 x 30 m',
 '["Techada con estructura metálica", "Grass sintético con caucho", "Iluminación LED", "Camerinos con duchas", "Estacionamiento", "Explanada para eventos", "Bancas para suplentes"]',
 90.00, 120.00, 'ACTIVA', 3, '2026-09-01 09:00:00', '2026-09-01 09:00:00'),
(4, 'losa-multiusos', 'Losa multiusos', 'MULTIUSO', 'Losa de concreto', 12,
 'Losa de concreto para vóley y básquet, con malla, tableros e iluminación LED. La opción más económica para jugar en grupo.',
 'La Losa multiusos es el espacio para el vóley y el básquet del complejo. Tiene superficie de concreto pulido con líneas pintadas para ambos deportes, malla de vóley regulable en altura y tableros de básquet con aros firmes. Es la opción más económica para jugar en grupo, ideal para equipos mixtos, entrenamientos y torneos de vóley.\n\nCuenta con iluminación LED para los turnos de noche, y comparte con el resto del complejo los camerinos con duchas, el estacionamiento y la explanada para eventos, perfecta para celebrar después del partido.\n\nNos encuentras en la Carretera Central km 11.5, Santa Clara (Ate), paradero Caseta, a dos cuadras de la UTP Santa Clara. Elige tu turno, paga el adelanto y listo.',
 FALSE, TRUE, '28 x 15 m',
 '["Vóley y básquet", "Malla regulable", "Tableros de básquet", "Iluminación LED", "Camerinos con duchas", "Estacionamiento", "Explanada para eventos"]',
 40.00, 50.00, 'ACTIVA', 4, '2026-09-01 09:00:00', '2026-09-01 09:00:00');

INSERT INTO configuracion (clave, valor, descripcion) VALUES
('adelanto_pct', '50', 'Porcentaje del precio que se paga como adelanto (0 a 100).'),
('devolucion_horas', '24', 'Horas mínimas de anticipación para cancelar con devolución.'),
('primer_turno', '08:00', 'Hora de inicio del primer turno del día (HH:mm).'),
('ultimo_turno', '23:00', 'Hora de cierre: el último turno empieza una duración antes (HH:mm).'),
('duracion_min', '60', 'Duración de cada turno en minutos.'),
('inicio_noche', '18:00', 'Desde esta hora se cobra la tarifa de noche (HH:mm).'),
('dias_visibles', '14', 'Días hacia adelante que se pueden reservar.'),
('minutos_para_pagar', '20', 'Minutos que tiene el cliente para enviar el comprobante del adelanto.'),
('recordatorio_horas', '2', 'Horas antes del partido en que se envía el recordatorio.'),
('yape_numero', '900111222', 'Celular de Yape del complejo (ficticio).'),
('yape_titular', 'Complejo Deportivo Alcanfores', 'Titular de la cuenta de Yape.'),
('plin_numero', '900111222', 'Celular de Plin del complejo (ficticio).'),
('whatsapp', '914995187', 'WhatsApp de atención del complejo.'),
('correo', 'informes.alcanfores@gmail.com', 'Correo de informes del complejo.'),
('direccion', 'Carretera Central km 11.5, Santa Clara, Ate, Lima', 'Dirección del complejo.'),
('referencia', 'Paradero Caseta, a dos cuadras de la UTP Santa Clara', 'Referencia para llegar.'),
('mapa_url', 'https://www.google.com/maps/search/?api=1&query=Complejo+Deportivo+Los+Alcanfores+Santa+Clara+Ate', 'Enlace al mapa.'),
('latitud', '-12.0196', 'Latitud aproximada del complejo.'),
('longitud', '-76.8865', 'Longitud aproximada del complejo.'),
('nombre_complejo', 'Complejo Deportivo Los Alcanfores', 'Nombre comercial del complejo.'),
('lema', 'Reserva tu cancha. Juega cuando quieras.', 'Lema del portal.'),
('ruc_emisor', '20612345679', 'RUC del emisor de comprobantes (ficticio).'),
('razon_social_emisor', 'Los Alcanfores Sport E.I.R.L. (ficticia)', 'Razón social del emisor (ficticia).');

INSERT INTO resena (id_cancha, codigo_reserva, autor, puntuacion, comentario, estado, respuesta, respondido_en, creado_en) VALUES
-- Cancha 1 · Fútbol 5
(1, 'ALC-R8D4NV', 'Erick Q.', 5, 'Jugamos el martes en la noche y la cancha estaba impecable. El grass no resbala y la luz LED no deja sombras en ningún lado. Ya separamos el mismo turno para la próxima semana.', 'PUBLICADA', NULL, NULL, '2026-10-13 22:15:00'),
(1, 'ALC-D2Q6YF', 'Andrea T.', 4, 'Buena cancha y muy bien ubicada, a dos cuadras de la UTP. Le bajo una estrella porque el domingo los camerinos estaban bastante llenos y tocó esperar para la ducha.', 'PUBLICADA', 'Gracias por contarnos, Andrea. Los domingos en la noche hay más movimiento; ya sumamos un turno de limpieza extra en los camerinos para esos días.', '2026-10-12 09:10:00', '2026-10-11 22:30:00'),
(1, 'ALC-H7M2KX', 'Diego R.', 5, 'Reservar fue rapidísimo: elegí el turno, pagué el adelanto con Yape y en unos minutos me llegó la confirmación por WhatsApp. La cancha, tal cual las fotos.', 'PUBLICADA', NULL, NULL, '2026-09-28 20:05:00'),
(1, 'ALC-W3P9TB', 'Kevin V.', 3, 'La cancha está bien, pero llegamos puntuales y el grupo del turno anterior seguía jugando cinco minutos más. Ojalá controlen mejor el cambio de turno.', 'PUBLICADA', 'Kevin, tienes toda la razón. Desde esta semana avisamos por parlante cinco minutos antes de cada cambio de turno para que nadie pierda minutos de juego.', '2026-10-03 10:20:00', '2026-10-02 23:10:00'),
(1, 'ALC-F6N4QJ', 'Jhon M.', 5, 'Cayó garúa toda la noche y ni lo sentimos porque la cancha es techada. Además se puede dejar el auto dentro del complejo, eso es un plus.', 'PUBLICADA', NULL, NULL, '2026-09-08 21:40:00'),
-- Cancha 2 · Fútbol 6
(2, 'ALC-K5T8VZ', 'Piero G.', 5, 'Seis contra seis es ideal para nuestro grupo de la oficina: hay espacio para tocar sin que se vuelva un pelotero. Los arcos y las redes están como nuevos.', 'PUBLICADA', NULL, NULL, '2026-10-12 21:05:00'),
(2, 'ALC-B2X7NM', 'Rocío C.', 4, 'Celebramos el cumpleaños de mi hijo en la explanada y después los chicos jugaron en la Cancha 2. Todo muy ordenado y el personal fue muy amable con los niños.', 'PUBLICADA', NULL, NULL, '2026-10-04 18:45:00'),
(2, 'ALC-M9Q3HD', 'Brayan Q.', 3, 'El grass en general está bien, pero en una de las esquinas ya se nota gastado. Por el precio de la noche esperaba que estuviera parejo en toda la cancha.', 'PUBLICADA', 'Gracias por el detalle, Brayan. Esa esquina entra en mantenimiento esta semana; te esperamos para que veas el cambio.', '2026-10-09 11:00:00', '2026-10-08 22:20:00'),
(2, 'ALC-T4J6WE', 'Renzo S.', 5, 'La iluminación LED es de lo mejor que he visto en la zona, se juega de noche como si fuera de día. Volvemos todos los jueves.', 'PUBLICADA', NULL, NULL, '2026-09-24 22:50:00'),
(2, 'ALC-Z8C5RU', 'Yesenia C.', 4, 'Fuimos un grupo mixto y la pasamos genial. Llegar es muy fácil, bajas en el paradero Caseta de la Carretera Central y caminas un poco.', 'PUBLICADA', NULL, NULL, '2026-09-15 20:30:00'),
-- Cancha 3 · Fútbol 7
(3, 'ALC-P7V2GA', 'Luis D.', 5, 'La cancha de fútbol 7 es amplia de verdad, se puede jugar por las bandas. Hicimos la final de nuestro campeonato de promoción y todos quedaron contentos.', 'PUBLICADA', 'Gracias, Luis. Fue un gusto recibir la final de su campeonato; cuando quieran organizar el siguiente, la explanada también está disponible para la premiación.', '2026-10-11 10:05:00', '2026-10-10 23:00:00'),
(3, 'ALC-S3H9LC', 'Marta Q.', 5, 'Separé por teléfono y luego todo lo vi en la web con mi código. El partido empezó a la hora y la cancha estaba limpia. Muy recomendable.', 'PUBLICADA', NULL, NULL, '2026-10-06 21:35:00'),
(3, 'ALC-G4N7YF', 'Ángel P.', 4, 'Muy buena cancha y bien iluminada. Solo pediría un bebedero cerca de las bancas, porque a veces toca ir hasta la entrada por agua.', 'PUBLICADA', NULL, NULL, '2026-09-30 22:10:00'),
(3, 'ALC-X6D3MK', 'Fiorella C.', 3, 'La cancha está bien, pero el sábado el estacionamiento se llenó rápido y tuvimos que dejar el auto afuera. Por lo demás, buena experiencia.', 'PUBLICADA', 'Gracias por avisarnos, Fiorella. Los sábados recomendamos llegar quince minutos antes; estamos habilitando más espacios junto a la explanada.', '2026-09-21 09:30:00', '2026-09-20 20:40:00'),
(3, 'ALC-E9R5TP', 'Jorge C.', 5, 'Jugamos fútbol 7 cada quince días y ya es nuestra cancha fija. El grass está parejo y los camerinos tienen agua caliente.', 'PUBLICADA', NULL, NULL, '2026-09-12 21:55:00'),
-- Losa multiusos
(4, 'ALC-L8W3QS', 'Diana F.', 5, 'Entrenamos vóley con mi equipo y la losa está perfecta: la malla se regula a la altura que necesitamos y el piso está parejo. Excelente precio.', 'PUBLICADA', NULL, NULL, '2026-10-10 19:25:00'),
(4, 'ALC-U5K2BN', 'Milagros H.', 4, 'Jugamos básquet en la noche y la iluminación ayuda bastante. Los aros están firmes; solo uno de los tableros necesita una manito de pintura.', 'PUBLICADA', NULL, NULL, '2026-10-01 21:15:00'),
(4, 'ALC-C7Y4HG', 'Albert V.', 3, 'Para vóley está bien, aunque ese día la malla estaba algo floja y tuvimos que ajustarla nosotros. Eso sí, el precio es de lo más accesible de la zona.', 'PUBLICADA', 'Gracias por el comentario, Albert. Ya cambiamos los tensores de la malla y ahora el personal la revisa antes de cada turno.', '2026-09-27 10:15:00', '2026-09-26 20:50:00'),
(4, 'ALC-N2F8XR', 'Sebastián L.', 5, 'Hicimos un torneo relámpago de vóley mixto entre amigos y luego nos quedamos en la explanada. Todo ordenado, volveremos.', 'PUBLICADA', NULL, NULL, '2026-09-18 22:00:00');
