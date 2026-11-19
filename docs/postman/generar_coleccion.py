"""Genera la colección Postman de Alcanfores (y sus entornos) con pruebas pm.test en cada petición.
Ejecutar:  python docs/postman/generar_coleccion.py   →  docs/postman/*.json
Correr:    npx newman run docs/postman/Alcanfores.postman_collection.json -e docs/postman/Alcanfores-local.postman_environment.json
"""
import json
import os
import uuid

AQUI = os.path.dirname(os.path.abspath(__file__))


def prueba(*lineas):
    return [{'listen': 'test', 'script': {'type': 'text/javascript', 'exec': list(lineas)}}]


def pre(*lineas):
    return [{'listen': 'prerequest', 'script': {'type': 'text/javascript', 'exec': list(lineas)}}]


def peticion(nombre, metodo, ruta, cuerpo=None, token=False, pruebas=(), previas=(), formulario=None, descripcion='', crudo=None):
    """token: False (sin sesión), True (administradora, {{token}}) o el nombre de la variable del token (p. ej. 'tokenCliente')."""
    partes = ruta.split('?')
    url = {'raw': '{{base}}' + ruta, 'host': ['{{base}}'], 'path': [p for p in partes[0].split('/') if p]}
    if len(partes) > 1:
        url['query'] = [{'key': kv.split('=')[0], 'value': kv.split('=', 1)[1]} for kv in partes[1].split('&')]
    req = {'method': metodo, 'header': [], 'url': url, 'description': descripcion}
    if token:
        variable = token if isinstance(token, str) else 'token'
        req['auth'] = {'type': 'bearer', 'bearer': [{'key': 'token', 'value': '{{' + variable + '}}', 'type': 'string'}]}
    else:
        req['auth'] = {'type': 'noauth'}
    if cuerpo is not None:
        req['header'].append({'key': 'Content-Type', 'value': 'application/json'})
        req['body'] = {'mode': 'raw', 'raw': json.dumps(cuerpo, ensure_ascii=False, indent=2)}
    if crudo is not None:
        req['header'].append({'key': 'Content-Type', 'value': 'application/json'})
        req['body'] = {'mode': 'raw', 'raw': crudo}
    if formulario is not None:
        req['body'] = {'mode': 'formdata', 'formdata': formulario}
    eventos = []
    if previas:
        eventos += pre(*previas)
    if pruebas:
        eventos += prueba(*pruebas)
    return {'name': nombre, 'request': req, 'event': eventos}


def estado(codigo):
    return f"pm.test('Responde {codigo}', () => pm.response.to.have.status({codigo}));"


ES_ERROR = "pm.test('Cuerpo de error estándar', () => { const j = pm.response.json(); pm.expect(j).to.have.keys('timestamp','status','codigo','mensaje','ruta','errores'); });"

carpetas = [
    {'name': '00 · Plataforma (gateway + Eureka)', 'item': [
        peticion('Estado de los microservicios', 'GET', '/api/estado', pruebas=[
            estado(200),
            "pm.test('Los 7 servicios están UP', () => { const s = pm.response.json().servicios; pm.expect(s.filter(x => x.estado === 'UP').length).to.eql(7); });"]),
        peticion('Ruta interna bloqueada en el gateway', 'GET', '/interno/configuracion', pruebas=[estado(404)]),
    ]},
    {'name': '01 · auth-service', 'item': [
        peticion('Login con credenciales incorrectas', 'POST', '/api/auth/login', {'usuario': 'rhuaman', 'contrasena': 'ClaveIncorrecta1'},
                 pruebas=[estado(401), ES_ERROR]),
        peticion('Login administradora (JWT)', 'POST', '/api/auth/login', {'usuario': 'rhuaman', 'contrasena': '{{claveAdmin}}'}, pruebas=[
            estado(200),
            "const j = pm.response.json(); pm.collectionVariables.set('token', j.token);",
            "pm.test('Entrega token Bearer y rol ADMIN', () => { pm.expect(j.tipo).to.eql('Bearer'); pm.expect(j.usuario.rol).to.eql('ADMIN'); });"]),
        peticion('Usuario actual', 'GET', '/api/auth/yo', token=True, pruebas=[estado(200)]),
        peticion('Panel sin token', 'GET', '/api/admin/usuarios', pruebas=[estado(401), ES_ERROR]),
        peticion('Listar usuarios (ADMIN)', 'GET', '/api/admin/usuarios', token=True, pruebas=[estado(200)]),
        peticion('Bitácora de auditoría', 'GET', '/api/admin/bitacora?size=10', token=True, pruebas=[estado(200)]),
    ]},
    {'name': '02 · catalogo-service', 'item': [
        peticion('Datos del complejo y reglas', 'GET', '/api/catalogo/complejo', pruebas=[estado(200),
            "pm.test('Adelanto configurado', () => pm.expect(pm.response.json().reglas.adelantoPct).to.be.above(0));",
            "pm.collectionVariables.set('adelantoPct', pm.response.json().reglas.adelantoPct);"]),
        peticion('Canchas activas', 'GET', '/api/catalogo/canchas', pruebas=[estado(200),
            "const c = pm.response.json(); pm.collectionVariables.set('idCancha', c[1].id); pm.collectionVariables.set('slug', c[1].slug);",
            "pm.test('Hay canchas con foto y calificación', () => { pm.expect(c.length).to.be.above(0); pm.expect(c[0].fotoPrincipal).to.be.a('string'); });"]),
        peticion('Detalle de cancha (fotos y reseñas)', 'GET', '/api/catalogo/canchas/{{slug}}', pruebas=[estado(200),
            "pm.test('Trae fotos y resumen de reseñas', () => { const j = pm.response.json(); pm.expect(j.fotos.length).to.be.above(0); pm.expect(j.resumenResenas).to.have.property('promedio'); });"]),
        peticion('Reseñas de la cancha', 'GET', '/api/catalogo/canchas/{{idCancha}}/resenas?orden=mejor&size=5', pruebas=[estado(200)]),
        peticion('Reseña sin iniciar sesión', 'POST', '/api/catalogo/canchas/{{idCancha}}/resenas',
                 {'codigoReserva': 'ALC-XXXXXX', 'puntuacion': 5, 'comentario': 'Muy buena cancha, volveremos pronto.'},
                 pruebas=[estado(401), ES_ERROR]),
        peticion('Horario y feriado en el detalle de la cancha', 'GET', '/api/catalogo/canchas/{{idCancha}}', pruebas=[estado(200),
            "const j = pm.response.json();",
            "pm.test('Trae horario de 7 días con texto y feriado', () => { pm.expect(j.horario.dias.length).to.eql(7); pm.expect(j.horario.texto).to.be.a('string'); pm.expect(j.feriado.modo).to.be.oneOf(['ABIERTA', 'CERRADA']); });"]),
        peticion('Calendario público (feriados y días cerrados)', 'GET', '/api/catalogo/calendario', pruebas=[estado(200),
            "pm.test('Es una lista con fecha y tipo', () => { const l = pm.response.json(); pm.expect(l).to.be.an('array'); l.forEach(d => pm.expect(d.tipo).to.be.oneOf(['FERIADO', 'NO_LABORABLE'])); });"]),
    ]},
    {'name': '03 · reservas-service', 'item': [
        peticion('Calendario de 14 días', 'GET', '/api/disponibilidad/calendario?dias=14', pruebas=[estado(200)]),
        peticion('Disponibilidad de mañana', 'GET', '/api/disponibilidad?fecha={{manana}}&idCancha={{idCancha}}', previas=[
            "const d = new Date(Date.now() + 86400000 - 5*3600000); pm.collectionVariables.set('manana', d.toISOString().slice(0,10));"],
            pruebas=[estado(200),
            "const t = pm.response.json().canchas[0].turnos.filter(x => x.estado === 'DISPONIBLE');",
            "pm.test('Hay turnos libres', () => pm.expect(t.length).to.be.above(0));",
            "pm.collectionVariables.set('idTurno', t[t.length - 1].idTurno);"]),
        peticion('Registrar reserva con datos inválidos', 'POST', '/api/reservas', {'idTurno': None, 'cliente': {
            'dni': '123', 'nombres': '1', 'apellidos': '', 'telefono': '812', 'correo': 'x'}, 'metodoPago': 'EFECTIVO',
            'aceptaTerminos': False, 'aceptaPrivacidad': False}, pruebas=[estado(400), ES_ERROR,
            "pm.test('Un error por cada campo', () => pm.expect(pm.response.json().errores.length).to.be.at.least(7));"]),
        peticion('Registrar reserva (Yape)', 'POST', '/api/reservas', {'idTurno': '{{idTurno}}', 'cliente': {
            'dni': '{{dni}}', 'nombres': 'Diego Alonso', 'apellidos': 'Ramos Quispe', 'telefono': '987654321',
            'correo': 'diego.prueba@example.com'}, 'metodoPago': 'YAPE', 'aceptaTerminos': True, 'aceptaPrivacidad': True},
            pruebas=[estado(201), "const r = pm.response.json(); pm.collectionVariables.set('codigo', r.codigoReserva);",
                     "pm.test('Queda PENDIENTE con plazo para pagar', () => { pm.expect(r.estado).to.eql('PENDIENTE'); pm.expect(r.venceEn).to.be.a('string'); });"]),
        peticion('Doble reserva del mismo turno', 'POST', '/api/reservas', {'idTurno': '{{idTurno}}', 'cliente': {
            'dni': '45678912', 'nombres': 'Camila', 'apellidos': 'Torres Vega', 'telefono': '912345678',
            'correo': 'camila.prueba@example.com'}, 'metodoPago': 'PLIN', 'aceptaTerminos': True, 'aceptaPrivacidad': True},
            pruebas=[estado(409), "pm.test('Código TURNO_OCUPADO', () => pm.expect(pm.response.json().codigo).to.eql('TURNO_OCUPADO'));"]),
        peticion('Consultar reserva (código + DNI)', 'GET', '/api/reservas/{{codigo}}?dni={{dni}}', pruebas=[estado(200)]),
        peticion('Consultar reserva con DNI ajeno', 'GET', '/api/reservas/{{codigo}}?dni=11111111', pruebas=[estado(404), ES_ERROR]),
        peticion('Constancia PDF', 'GET', '/api/reservas/{{codigo}}/constancia.pdf?dni={{dni}}', pruebas=[estado(200),
            "pm.test('Es un PDF', () => pm.expect(pm.response.headers.get('Content-Type')).to.include('pdf'));"]),
        peticion('RUC con dígito verificador inválido', 'GET', '/api/documentos/ruc/20612345678', pruebas=[estado(400)]),
        peticion('Dashboard del día', 'GET', '/api/admin/dashboard', token=True, pruebas=[estado(200)]),
        peticion('Listado de reservas con filtros', 'GET', '/api/admin/reservas?estado=PENDIENTE&size=10', token=True, pruebas=[estado(200)]),
        peticion('Reporte de reservas', 'GET', '/api/admin/reportes/reservas', token=True, pruebas=[estado(200)]),
    ]},
    {'name': '04 · pagos-service', 'item': [
        peticion('Métodos de pago y tarjetas de prueba', 'GET', '/api/pagos/metodos', pruebas=[estado(200),
            "pm.test('Pasarela beta activa', () => pm.expect(pm.response.json().tarjetaBeta).to.eql(true));"]),
        peticion('Crear intento en la pasarela beta', 'POST', '/api/pagos/pasarela/intentos', {'codigoReserva': '{{codigo}}', 'dni': '{{dni}}',
            'tipoPago': 'ADELANTO', 'comprobante': {'tipo': 'BOLETA'}}, pruebas=[
            "pm.test('Crea el intento', () => pm.expect(pm.response.code).to.be.oneOf([200, 201]));",
            "pm.collectionVariables.set('ref', pm.response.json().referencia);"]),
        peticion('Pagar con tarjeta sin fondos', 'POST', '/api/pasarela/intentos/{{ref}}/tarjeta', {'numero': '4000000000000002',
            'titular': 'DIEGO RAMOS', 'mesVencimiento': 12, 'anioVencimiento': 2029, 'cvv': '123', 'correo': 'diego.prueba@example.com'},
            pruebas=["pm.test('Rechazada', () => pm.expect(pm.response.json().estado).to.eql('RECHAZADO'));"]),
        peticion('Tarjeta con número inválido (Luhn)', 'POST', '/api/pasarela/intentos/{{ref}}/tarjeta', {'numero': '4111111111111112',
            'titular': 'DIEGO RAMOS', 'mesVencimiento': 12, 'anioVencimiento': 2029, 'cvv': '123', 'correo': 'diego.prueba@example.com'},
            pruebas=[estado(400)]),
        peticion('Nuevo intento', 'POST', '/api/pagos/pasarela/intentos', {'codigoReserva': '{{codigo}}', 'dni': '{{dni}}',
            'tipoPago': 'ADELANTO', 'comprobante': {'tipo': 'BOLETA'}}, pruebas=["pm.collectionVariables.set('ref', pm.response.json().referencia);"]),
        peticion('Pagar con Visa de prueba', 'POST', '/api/pasarela/intentos/{{ref}}/tarjeta', {'numero': '4111111111111111',
            'titular': 'DIEGO RAMOS', 'mesVencimiento': 12, 'anioVencimiento': 2029, 'cvv': '123', 'correo': 'diego.prueba@example.com'},
            pruebas=[estado(200), "pm.test('Aprobada con código de autorización', () => { const j = pm.response.json(); pm.expect(j.estado).to.eql('APROBADO'); pm.expect(j.codigoAutorizacion).to.match(/^\\d{6}$/); });"]),
        peticion('Pagos y comprobantes de la reserva', 'GET', '/api/pagos/reserva/{{codigo}}?dni={{dni}}', previas=[
            "setTimeout(() => {}, 2500);"], pruebas=[estado(200),
            "const c = pm.response.json().find(p => p.comprobante); pm.test('Boleta emitida', () => pm.expect(c.comprobante.serieNumero).to.match(/^B001-/)); pm.collectionVariables.set('serie', c.comprobante.serieNumero);"]),
        peticion('Boleta en PDF', 'GET', '/api/comprobantes/{{serie}}.pdf?codigo={{codigo}}&dni={{dni}}', pruebas=[estado(200)]),
        peticion('Boleta en XML UBL 2.1', 'GET', '/api/comprobantes/{{serie}}.xml?codigo={{codigo}}&dni={{dni}}', pruebas=[estado(200),
            "pm.test('Es UBL 2.1', () => pm.expect(pm.response.text()).to.include('UBLVersionID'));"]),
        peticion('Comprobantes emitidos (panel)', 'GET', '/api/admin/comprobantes?size=10', token=True, pruebas=[estado(200)]),
        peticion('Reporte de ingresos', 'GET', '/api/admin/reportes/ingresos', token=True, pruebas=[estado(200)]),
    ]},
    {'name': '05 · notificaciones-service', 'item': [
        peticion('Bandeja de notificaciones de la reserva', 'GET', '/api/admin/notificaciones?q={{codigo}}&size=50', token=True, pruebas=[estado(200),
            "pm.test('Avisos por correo y WhatsApp', () => { const c = pm.response.json().contenido.map(n => n.canal); pm.expect(c).to.include('CORREO'); pm.expect(c).to.include('WHATSAPP'); });"]),
        peticion('Recordatorios programados', 'GET', '/api/admin/recordatorios?size=20', token=True, pruebas=[estado(200)]),
        peticion('Estado de WhatsApp (Evolution API)', 'GET', '/api/admin/whatsapp/estado', token=True, pruebas=[estado(200)]),
        peticion('Mensaje de prueba inválido', 'POST', '/api/admin/whatsapp/prueba', {'telefono': '123', 'mensaje': ''}, token=True,
                 pruebas=[estado(400), ES_ERROR]),
    ]},
]


# Fecha (Lima) a N días de hoy en formato YYYY-MM-DD, para los scripts previos
def fecha_js(variable, dias):
    return f"const d = new Date(Date.now() + ({dias})*86400000 - 5*3600000); pm.collectionVariables.set('{variable}', d.toISOString().slice(0,10));"


CUERPO_HORARIO_MALO = {'duracionTurnoMin': 120, 'inicioNoche': '18:00', 'feriado': {'modo': 'ABIERTA', 'recargo': 10},
                       'dias': [{'diaSemana': d, 'abierto': True, 'apertura': '08:00', 'cierre': '23:00'} for d in range(1, 8)]}
CUERPO_HORARIO_90 = dict(CUERPO_HORARIO_MALO, duracionTurnoMin=90)
CLIENTE_LUCIA = {'dni': '{{dniCliente}}', 'nombres': 'Lucía', 'apellidos': 'Mendoza Paredes', 'telefono': '987111222',
                 'correo': '{{correoCliente}}'}

carpetas += [
    {'name': '06 · Horario por cancha, calendario y bloqueos', 'item': [
        peticion('Horario de la cancha (panel)', 'GET', '/api/admin/canchas/{{idCancha}}/horario', token=True, pruebas=[estado(200),
            "const j = pm.response.json(); pm.collectionVariables.set('horario', JSON.stringify(j));",
            "pm.test('7 días, duración y feriado', () => { pm.expect(j.dias.length).to.eql(7); pm.expect(j.duracionTurnoMin).to.be.at.least(30); pm.expect(j.feriado).to.have.property('recargo'); });"]),
        peticion('Guardar el mismo horario (regenera sin cambios)', 'PUT', '/api/admin/canchas/{{idCancha}}/horario', token=True,
                 crudo='{{horario}}', pruebas=[estado(200),
            "pm.test('Devuelve el horario guardado', () => pm.expect(pm.response.json().dias.length).to.eql(7));"]),
        peticion('Horario que no calza con la duración', 'PUT', '/api/admin/canchas/{{idCancha}}/horario', CUERPO_HORARIO_MALO, token=True,
                 pruebas=[estado(400), ES_ERROR,
            "pm.test('Dice cuánto sobra y cómo ajustarlo', () => pm.expect(JSON.stringify(pm.response.json())).to.include('sobra 1 hora'));"]),
        peticion('Vista previa del lunes (guardado)', 'GET', '/api/admin/canchas/{{idCancha}}/horario/vista-previa?dia=1', token=True,
                 pruebas=[estado(200),
            "pm.test('Lista de turnos con precio y franja', () => { const l = pm.response.json(); pm.expect(l.length).to.be.above(0); pm.expect(l[0]).to.have.keys('horaInicio','horaFin','precio','franja'); });"]),
        peticion('Vista previa sin guardar (turnos de 90 min)', 'POST', '/api/admin/canchas/{{idCancha}}/horario/vista-previa?dia=1',
                 CUERPO_HORARIO_90, token=True, pruebas=[estado(200),
            "pm.test('10 turnos de 8:00 a. m. a 11:00 p. m.', () => { const l = pm.response.json(); pm.expect(l.length).to.eql(10); pm.expect(l[0].horaFin).to.eql('09:30'); });"]),
        peticion('Calendario del año (panel)', 'GET', '/api/admin/calendario?anio=2026', token=True, pruebas=[estado(200),
            "pm.test('Feriados de Perú precargados', () => pm.expect(pm.response.json().filter(d => d.tipo === 'FERIADO').length).to.be.at.least(16));"]),
        peticion('Cargar feriados de Perú 2027 (sin duplicar)', 'POST', '/api/admin/calendario/feriados-peru?anio=2027', token=True,
                 pruebas=[estado(200),
            "pm.test('Son 16 feriados entre creados y existentes', () => { const j = pm.response.json(); pm.expect(j.creados + j.existentes).to.eql(16); });"]),
        peticion('Día no laborable inválido', 'POST', '/api/admin/calendario', {'fecha': None, 'tipo': None, 'nombre': 'x'},
                 token=True, pruebas=[estado(400), ES_ERROR,
            "pm.test('Error en fecha, tipo y nombre', () => { const c = pm.response.json().errores.map(e => e.campo); ['fecha','tipo','nombre'].forEach(x => pm.expect(c).to.include(x)); });"]),
        peticion('Registrar día no laborable', 'POST', '/api/admin/calendario', {'fecha': '{{fechaEspecial}}', 'tipo': 'NO_LABORABLE',
                 'nombre': 'Mantenimiento del grass (prueba Postman)', 'canchas': ['{{idCancha}}']}, token=True, previas=[
            "// Fecha lejana (sin turnos ni reservas) que no cae en un feriado nacional",
            "const fijos = ['01-01','05-01','06-07','06-29','07-23','07-28','07-29','08-06','08-30','10-08','11-01','12-08','12-09','12-25','04-01','04-02','04-03','03-25','03-26'];",
            "let d = new Date(Date.now() + (200 + Math.floor(Math.random()*150))*86400000 - 5*3600000);",
            "while (fijos.includes(d.toISOString().slice(5,10))) { d = new Date(d.getTime() + 86400000); }",
            "pm.collectionVariables.set('fechaEspecial', d.toISOString().slice(0,10));"], pruebas=[estado(201),
            "const j = pm.response.json(); pm.collectionVariables.set('idDiaEspecial', j.id);",
            "pm.test('Queda solo para esa cancha', () => { pm.expect(j.tipo).to.eql('NO_LABORABLE'); pm.expect(j.canchas.length).to.eql(1); });"]),
        peticion('Misma fecha otra vez', 'POST', '/api/admin/calendario', {'fecha': '{{fechaEspecial}}', 'tipo': 'FERIADO',
                 'nombre': 'Feriado repetido de prueba'}, token=True, pruebas=[estado(409), ES_ERROR,
            "pm.test('DIA_ESPECIAL_DUPLICADO', () => pm.expect(pm.response.json().codigo).to.eql('DIA_ESPECIAL_DUPLICADO'));"]),
        peticion('Quitar el día no laborable', 'DELETE', '/api/admin/calendario/{{idDiaEspecial}}', token=True, pruebas=[estado(204)]),
        peticion('Bloquear un rango de horas', 'POST', '/api/admin/turnos/bloquear-rango', {'idsCancha': ['{{idCancha}}'],
                 'fecha': '{{fechaBloqueo}}', 'horaDesde': '08:00', 'horaHasta': '10:00', 'motivo': 'Mantenimiento de prueba'},
                 token=True, previas=[fecha_js('fechaBloqueo', 10)], pruebas=[estado(200),
            "const j = pm.response.json(); pm.collectionVariables.set('bloqueados', j.bloqueados);",
            "pm.test('Cuenta bloqueados y omitidos', () => { pm.expect(j.bloqueados).to.be.a('number'); pm.expect(j.omitidosConReserva).to.be.an('array'); });"]),
        peticion('Desbloquear el mismo rango', 'POST', '/api/admin/turnos/desbloquear-rango', {'idsCancha': ['{{idCancha}}'],
                 'fecha': '{{fechaBloqueo}}', 'horaDesde': '08:00', 'horaHasta': '10:00'}, token=True, pruebas=[estado(200),
            "pm.test('Libera lo que se bloqueó', () => pm.expect(pm.response.json().desbloqueados).to.eql(Number(pm.collectionVariables.get('bloqueados'))));"]),
        peticion('Rango con horas al revés', 'POST', '/api/admin/turnos/bloquear-rango', {'idsCancha': ['{{idCancha}}'],
                 'fecha': '{{fechaBloqueo}}', 'horaDesde': '12:00', 'horaHasta': '10:00', 'motivo': 'Mantenimiento de prueba'},
                 token=True, pruebas=[estado(400), ES_ERROR]),
    ]},
    {'name': '07 · Cuentas de clientes y Mis reservas', 'item': [
        peticion('Turnos libres para la reserva de varios turnos', 'GET', '/api/disponibilidad?fecha={{fechaVarios}}&idCancha={{idCancha}}',
                 previas=[fecha_js('fechaVarios', 5),
                          "pm.collectionVariables.set('dniCliente', '98' + String(Math.floor(Math.random()*1e6)).padStart(6, '0'));",
                          "pm.collectionVariables.set('correoCliente', 'cliente' + Date.now() + '@example.com');"],
                 pruebas=[estado(200),
            "const t = pm.response.json().canchas[0].turnos.filter(x => x.estado === 'DISPONIBLE');",
            "let a = null, b = null; for (let i = 0; i < t.length && !b; i++) for (let j = i + 1; j < t.length; j++) if (t[j].horaInicio !== t[i].horaFin) { a = t[i]; b = t[j]; break; }",
            "pm.test('Hay dos turnos libres no seguidos y uno más', () => { pm.expect(b).to.not.be.null; pm.expect(t.length).to.be.at.least(3); });",
            "const c = t.find(x => x !== a && x !== b);",
            "pm.collectionVariables.set('idTurnoA', a.idTurno); pm.collectionVariables.set('idTurnoB', b.idTurno); pm.collectionVariables.set('idTurnoC', c.idTurno);",
            "pm.collectionVariables.set('totalVarios', (a.precio + b.precio).toFixed(2));"]),
        peticion('Reserva de 2 turnos no seguidos (sin cuenta)', 'POST', '/api/reservas', {'idsTurno': ['{{idTurnoA}}', '{{idTurnoB}}'],
                 'cliente': CLIENTE_LUCIA, 'metodoPago': 'YAPE', 'aceptaTerminos': True, 'aceptaPrivacidad': True},
                 pruebas=[estado(201), "const r = pm.response.json(); pm.collectionVariables.set('codigoVarios', r.codigoReserva);",
            "const total = Number(pm.collectionVariables.get('totalVarios')); const pct = Number(pm.collectionVariables.get('adelantoPct'));",
            "pm.test('Total = suma de los 2 turnos y adelanto = total × %', () => { pm.expect(r.montoTotal).to.be.closeTo(total, 0.001); pm.expect(r.montoAdelanto).to.be.closeTo(Math.round(total * pct) / 100, 0.001); });",
            "pm.test('2 turnos con horario en texto', () => { pm.expect(r.cantidadTurnos).to.eql(2); pm.expect(r.turnos.length).to.eql(2); pm.expect(r.horarioTexto).to.include(' y '); });"]),
        peticion('El mismo turno dos veces', 'POST', '/api/reservas', {'idsTurno': ['{{idTurnoC}}', '{{idTurnoC}}'],
                 'cliente': CLIENTE_LUCIA, 'metodoPago': 'YAPE', 'aceptaTerminos': True, 'aceptaPrivacidad': True},
                 pruebas=[estado(400), ES_ERROR,
            "pm.test('Error en idsTurno', () => pm.expect(pm.response.json().errores.map(e => e.campo)).to.include('idsTurno'));"]),
        peticion('Crear cuenta con datos inválidos', 'POST', '/api/auth/clientes/registro', {'dni': '12', 'nombres': '1', 'apellidos': '',
                 'correo': 'x', 'telefono': '812', 'contrasena': 'corta', 'confirmacion': 'otra', 'aceptaTerminos': False,
                 'aceptaPrivacidad': False}, pruebas=[estado(400), ES_ERROR,
            "pm.test('Un error por cada campo', () => pm.expect(pm.response.json().errores.length).to.be.at.least(7));"]),
        peticion('Crear cuenta (DNI de la reserva anterior)', 'POST', '/api/auth/clientes/registro', {'dni': '{{dniCliente}}',
                 'nombres': 'Lucía', 'apellidos': 'Mendoza Paredes', 'correo': '{{correoCliente}}', 'telefono': '987111222',
                 'contrasena': 'Cliente2026', 'confirmacion': 'Cliente2026', 'aceptaTerminos': True, 'aceptaPrivacidad': True},
                 pruebas=[estado(201), "const j = pm.response.json(); pm.collectionVariables.set('tokenCliente', j.token);",
            "pm.test('Sesión iniciada con la cuenta', () => { pm.expect(j.tipo).to.eql('Bearer'); pm.expect(j.cliente.dni).to.eql(pm.collectionVariables.get('dniCliente')); });"]),
        peticion('Crear cuenta con el mismo DNI', 'POST', '/api/auth/clientes/registro', {'dni': '{{dniCliente}}',
                 'nombres': 'Lucía', 'apellidos': 'Mendoza Paredes', 'correo': 'otro.{{correoCliente}}', 'telefono': '987111222',
                 'contrasena': 'Cliente2026', 'confirmacion': 'Cliente2026', 'aceptaTerminos': True, 'aceptaPrivacidad': True},
                 pruebas=[estado(409), "pm.test('DNI_REGISTRADO', () => pm.expect(pm.response.json().codigo).to.eql('DNI_REGISTRADO'));"]),
        peticion('Iniciar sesión con DNI', 'POST', '/api/auth/clientes/login', {'usuario': '{{dniCliente}}', 'contrasena': 'Cliente2026'},
                 pruebas=[estado(200), "pm.collectionVariables.set('tokenCliente', pm.response.json().token);"]),
        peticion('Iniciar sesión con correo', 'POST', '/api/auth/clientes/login', {'usuario': '{{correoCliente}}', 'contrasena': 'Cliente2026'},
                 pruebas=[estado(200)]),
        peticion('Contraseña incorrecta', 'POST', '/api/auth/clientes/login', {'usuario': '{{dniCliente}}', 'contrasena': 'Incorrecta2026'},
                 pruebas=[estado(401), ES_ERROR]),
        peticion('Mi cuenta', 'GET', '/api/auth/clientes/yo', token='tokenCliente', pruebas=[estado(200),
            "pm.test('Es la cuenta del DNI', () => pm.expect(pm.response.json().dni).to.eql(pm.collectionVariables.get('dniCliente')));"]),
        peticion('Editar mis datos', 'PUT', '/api/auth/clientes/yo', {'nombres': 'Lucía', 'apellidos': 'Mendoza Paredes',
                 'correo': '{{correoCliente}}', 'telefono': '987111333'}, token='tokenCliente', pruebas=[estado(200),
            "pm.test('Celular actualizado', () => pm.expect(pm.response.json().telefono).to.eql('987111333'));"]),
        peticion('Mis reservas (incluye la hecha antes de crear la cuenta)', 'GET', '/api/cliente/reservas', token='tokenCliente',
                 pruebas=[estado(200),
            "const l = pm.response.json().contenido;",
            "pm.test('Aparece la reserva de 2 turnos', () => pm.expect(l.map(x => x.codigoReserva)).to.include(pm.collectionVariables.get('codigoVarios')));",
            "pm.test('Cada ítem trae sus acciones', () => pm.expect(l[0]).to.include.keys('puedeCancelar','puedeReenviarComprobante','puedeResenar','resenada'));"]),
        peticion('Detalle en Mis reservas', 'GET', '/api/cliente/reservas/{{codigoVarios}}', token='tokenCliente', pruebas=[estado(200),
            "pm.test('Con sus 2 turnos y sin reseña', () => { const j = pm.response.json(); pm.expect(j.turnos.length).to.eql(2); pm.expect(j.resenada).to.eql(false); });"]),
        peticion('Mis reservas sin sesión', 'GET', '/api/cliente/reservas', pruebas=[estado(401), ES_ERROR]),
        peticion('Mis reservas con token de administradora', 'GET', '/api/cliente/reservas', token=True, pruebas=[estado(403), ES_ERROR]),
        peticion('Panel con token de cliente', 'GET', '/api/admin/reservas', token='tokenCliente', pruebas=[estado(403), ES_ERROR]),
        peticion('Reservar con sesión (el DNI sale de la cuenta)', 'POST', '/api/reservas', {'idsTurno': ['{{idTurnoC}}'],
                 'cliente': dict(CLIENTE_LUCIA, dni='{{dni}}'), 'metodoPago': 'PLIN', 'aceptaTerminos': True, 'aceptaPrivacidad': True},
                 token='tokenCliente', pruebas=[estado(201),
            "const r = pm.response.json(); pm.collectionVariables.set('codigoSesion', r.codigoReserva);",
            "const dni = pm.collectionVariables.get('dniCliente');",
            "pm.test('Quedó a nombre del DNI de la cuenta', () => pm.expect(r.cliente.dniEnmascarado).to.eql(dni[0] + '****' + dni.slice(5)));"]),
        peticion('Reseña con sesión: datos inválidos', 'POST', '/api/catalogo/canchas/{{idCancha}}/resenas',
                 {'codigoReserva': 'ALC-XXXXXX', 'puntuacion': 9, 'comentario': 'visita www.spam.com'}, token='tokenCliente',
                 pruebas=[estado(400), ES_ERROR]),
        peticion('Reseña con sesión de una reserva aún no jugada', 'POST', '/api/catalogo/canchas/{{idCancha}}/resenas',
                 {'codigoReserva': '{{codigoVarios}}', 'puntuacion': 5, 'comentario': 'Todavía no juego pero ya quiero opinar.'},
                 token='tokenCliente', pruebas=[estado(422), ES_ERROR]),
        peticion('Cancelar la reserva de 2 turnos', 'POST', '/api/reservas/{{codigoVarios}}/cancelar',
                 {'dni': '{{dniCliente}}', 'motivo': 'Prueba automática de la colección Postman'}, pruebas=[estado(200),
            "pm.test('CANCELADA', () => pm.expect(pm.response.json().estado).to.eql('CANCELADA'));"]),
        peticion('Cancelar la reserva hecha con sesión', 'POST', '/api/reservas/{{codigoSesion}}/cancelar',
                 {'dni': '{{dniCliente}}', 'motivo': 'Prueba automática de la colección Postman'}, pruebas=[estado(200)]),
        peticion('Cambiar mi contraseña', 'POST', '/api/auth/clientes/cambiar-contrasena', {'actual': 'Cliente2026',
                 'nueva': 'Cliente2027', 'confirmacion': 'Cliente2027'}, token='tokenCliente', pruebas=[estado(200)]),
    ]},
    {'name': '08 · Reseñas del complejo (en cada cancha)', 'item': [
        peticion('Reseñas de la cancha (panel, incluye ocultas)', 'GET', '/api/admin/canchas/{{idCancha}}/resenas', token=True,
                 pruebas=[estado(200),
            "const l = pm.response.json(); const c = l.find(r => r.origen === 'CLIENTE'); if (c) pm.collectionVariables.set('idResenaCliente', c.id);",
            "pm.test('Cada reseña dice su origen', () => l.forEach(r => pm.expect(r.origen).to.be.oneOf(['CLIENTE', 'COMPLEJO'])));"]),
        peticion('Crear reseña del complejo', 'POST', '/api/admin/canchas/{{idCancha}}/resenas', {'autor': 'Club Los Pumas',
                 'puntuacion': 5, 'comentario': 'Jugamos el campeonato interno y todo estuvo impecable.', 'fecha': '{{ayer}}'},
                 token=True, previas=[fecha_js('ayer', -1)], pruebas=[estado(201),
            "const j = pm.response.json(); pm.collectionVariables.set('idResenaComplejo', j.id);",
            "pm.test('Origen COMPLEJO y publicada', () => { pm.expect(j.origen).to.eql('COMPLEJO'); pm.expect(j.estado).to.eql('PUBLICADA'); });"]),
        peticion('Reseña del complejo inválida', 'POST', '/api/admin/canchas/{{idCancha}}/resenas', {'autor': '1', 'puntuacion': 0,
                 'comentario': 'corto', 'fecha': '2999-01-01'}, token=True, pruebas=[estado(400), ES_ERROR]),
        peticion('Editar la reseña del complejo', 'PUT', '/api/admin/resenas/{{idResenaComplejo}}', {'autor': 'Club Los Pumas',
                 'puntuacion': 4, 'comentario': 'Jugamos el campeonato interno; muy buena atención.', 'fecha': '{{ayer}}'}, token=True,
                 pruebas=[estado(200), "pm.test('Nueva puntuación', () => pm.expect(pm.response.json().puntuacion).to.eql(4));"]),
        peticion('Ocultarla', 'PATCH', '/api/admin/resenas/{{idResenaComplejo}}', {'estado': 'OCULTA'}, token=True, pruebas=[estado(200)]),
        peticion('Borrar una reseña de cliente (no se permite)', 'DELETE', '/api/admin/resenas/{{idResenaCliente}}', token=True,
                 pruebas=[estado(422), "pm.test('RESENA_DE_CLIENTE', () => pm.expect(pm.response.json().codigo).to.eql('RESENA_DE_CLIENTE'));"]),
        peticion('Borrar la reseña del complejo', 'DELETE', '/api/admin/resenas/{{idResenaComplejo}}', token=True, pruebas=[estado(204)]),
    ]},
]

coleccion = {
    'info': {'_postman_id': str(uuid.uuid4()), 'name': 'Alcanfores · API de microservicios',
             'description': 'Sistema de reservas del Complejo Deportivo Los Alcanfores (UTP · Desarrollo Web Integrado). '
                            'Todas las peticiones pasan por el API Gateway. Ejecutar en orden: las variables se encadenan.',
             'schema': 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},
    'item': carpetas,
    'variable': [{'key': k, 'value': v} for k, v in [('token', ''), ('idCancha', ''), ('slug', ''), ('idTurno', ''), ('codigo', ''),
                                                      ('dni', '70123321'), ('manana', ''), ('ref', ''), ('serie', ''),
                                                      ('adelantoPct', ''), ('horario', ''), ('fechaEspecial', ''),
                                                      ('idDiaEspecial', ''), ('fechaBloqueo', ''), ('bloqueados', ''),
                                                      ('fechaVarios', ''), ('dniCliente', ''), ('correoCliente', ''),
                                                      ('idTurnoA', ''), ('idTurnoB', ''), ('idTurnoC', ''), ('totalVarios', ''),
                                                      ('codigoVarios', ''), ('codigoSesion', ''), ('tokenCliente', ''),
                                                      ('idResenaCliente', ''), ('idResenaComplejo', ''), ('ayer', '')]],
}
json.dump(coleccion, open(os.path.join(AQUI, 'Alcanfores.postman_collection.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
for nombre, base in [('local', 'http://localhost:8086'), ('produccion', 'https://alcanfores.dbltech.tech')]:
    json.dump({'id': str(uuid.uuid4()), 'name': f'Alcanfores · {nombre}', 'values': [
        {'key': 'base', 'value': base, 'enabled': True},
        {'key': 'claveAdmin', 'value': 'Alcanfores2026' if nombre == 'local' else '', 'enabled': True}]},
        open(os.path.join(AQUI, f'Alcanfores-{nombre}.postman_environment.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
print('peticiones:', sum(len(c['item']) for c in carpetas))
