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


def peticion(nombre, metodo, ruta, cuerpo=None, token=False, pruebas=(), previas=(), formulario=None, descripcion=''):
    partes = ruta.split('?')
    url = {'raw': '{{base}}' + ruta, 'host': ['{{base}}'], 'path': [p for p in partes[0].split('/') if p]}
    if len(partes) > 1:
        url['query'] = [{'key': kv.split('=')[0], 'value': kv.split('=', 1)[1]} for kv in partes[1].split('&')]
    req = {'method': metodo, 'header': [], 'url': url, 'description': descripcion}
    if token:
        req['auth'] = {'type': 'bearer', 'bearer': [{'key': 'token', 'value': '{{token}}', 'type': 'string'}]}
    else:
        req['auth'] = {'type': 'noauth'}
    if cuerpo is not None:
        req['header'].append({'key': 'Content-Type', 'value': 'application/json'})
        req['body'] = {'mode': 'raw', 'raw': json.dumps(cuerpo, ensure_ascii=False, indent=2)}
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
            "pm.test('Adelanto configurado', () => pm.expect(pm.response.json().reglas.adelantoPct).to.be.above(0));"]),
        peticion('Canchas activas', 'GET', '/api/catalogo/canchas', pruebas=[estado(200),
            "const c = pm.response.json(); pm.collectionVariables.set('idCancha', c[1].id); pm.collectionVariables.set('slug', c[1].slug);",
            "pm.test('Hay canchas con foto y calificación', () => { pm.expect(c.length).to.be.above(0); pm.expect(c[0].fotoPrincipal).to.be.a('string'); });"]),
        peticion('Detalle de cancha (fotos y reseñas)', 'GET', '/api/catalogo/canchas/{{slug}}', pruebas=[estado(200),
            "pm.test('Trae fotos y resumen de reseñas', () => { const j = pm.response.json(); pm.expect(j.fotos.length).to.be.above(0); pm.expect(j.resumenResenas).to.have.property('promedio'); });"]),
        peticion('Reseñas de la cancha', 'GET', '/api/catalogo/canchas/{{idCancha}}/resenas?orden=mejor&size=5', pruebas=[estado(200)]),
        peticion('Reseña con enlace (validación)', 'POST', '/api/catalogo/canchas/{{idCancha}}/resenas',
                 {'codigoReserva': 'ALC-XXXXXX', 'dni': '7012', 'puntuacion': 9, 'comentario': 'visita www.spam.com'}, pruebas=[estado(400), ES_ERROR]),
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

coleccion = {
    'info': {'_postman_id': str(uuid.uuid4()), 'name': 'Alcanfores · API de microservicios',
             'description': 'Sistema de reservas del Complejo Deportivo Los Alcanfores (UTP · Desarrollo Web Integrado). '
                            'Todas las peticiones pasan por el API Gateway. Ejecutar en orden: las variables se encadenan.',
             'schema': 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},
    'item': carpetas,
    'variable': [{'key': k, 'value': v} for k, v in [('token', ''), ('idCancha', ''), ('slug', ''), ('idTurno', ''), ('codigo', ''),
                                                      ('dni', '70123321'), ('manana', ''), ('ref', ''), ('serie', '')]],
}
json.dump(coleccion, open(os.path.join(AQUI, 'Alcanfores.postman_collection.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
for nombre, base in [('local', 'http://localhost:8086'), ('produccion', 'https://alcanfores.dbltech.tech')]:
    json.dump({'id': str(uuid.uuid4()), 'name': f'Alcanfores · {nombre}', 'values': [
        {'key': 'base', 'value': base, 'enabled': True},
        {'key': 'claveAdmin', 'value': 'Alcanfores2026' if nombre == 'local' else '', 'enabled': True}]},
        open(os.path.join(AQUI, f'Alcanfores-{nombre}.postman_environment.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
print('peticiones:', sum(len(c['item']) for c in carpetas))
