"""Prueba de punta a punta de Alcanfores contra el API Gateway (local o producción).

Recorre el sistema como lo haría la gente: un cliente reserva y paga (Yape con comprobante y tarjeta por la pasarela
beta), la administradora valida, se emiten boleta/factura, salen las notificaciones, se programa el recordatorio,
se cancela con nota de crédito y se publica una reseña verificada. También verifica seguridad y validaciones.
Versión 2: reserva de varios turnos, horario propio de cada cancha, feriados con recargo y días no laborables,
cuentas de clientes con «Mis reservas», reseña con sesión y reseñas del complejo.
Recuperar contraseña: el cliente pide un código por correo, lo verifica y elige otra contraseña. En local el servidor se
arranca con RECUPERAR_DEVOLVER_CODIGO=true y responde el código (codigoPrueba); en producción no lo devuelve y esa parte
se SALTA. En local además se prueban varios recordatorios: la lista pasa a «60,15» y se restaura al final.
Reprogramar: el cliente cambia de cancha y de día una sola vez, el panel cambia con motivo, sale el aviso «¡Cambiamos tu
horario!» y, en local, se consulta directo a reservas-service (RESERVAS_URL, INTERNO_TOKEN) la lista por DNI + celular.

  python docs/pruebas/prueba_e2e.py http://localhost:8086           (local)
  python docs/pruebas/prueba_e2e.py https://alcanfores.dbltech.tech  (producción)

Claves de las cuentas de demostración: variables CLAVE_ADMIN y CLAVE_RECEPCION (por defecto, las de desarrollo).
"""
import glob
import json
import os
import random
import sys
import time
from datetime import date, datetime, timedelta
from decimal import ROUND_HALF_UP, Decimal

import requests

sys.stdout.reconfigure(encoding='utf-8')
BASE = (sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8086').rstrip('/')
API = BASE + '/api'
LOCAL = 'localhost' in BASE or '127.0.0.1' in BASE
CLAVE_ADMIN = os.environ.get('CLAVE_ADMIN', 'Alcanfores2026')
CLAVE_RECEPCION = os.environ.get('CLAVE_RECEPCION', 'Recepcion2026')
AQUI = os.path.dirname(os.path.abspath(__file__))
COMPROBANTE_IMG = (glob.glob(os.path.join(AQUI, '..', '..', 'backend', 'pagos-service', 'src', 'main', 'resources',
                                          'semilla', 'comprobantes', '*.png')) or [None])[0]
resultados = []

# Registro de todas las reservas que crea la prueba (para cancelarlas al final con E2E_LIMPIAR=1)
RESERVAS_CREADAS = []
_post_original = requests.post


def _post_registrando(url, *args, **kwargs):
    r = _post_original(url, *args, **kwargs)
    if url.rstrip('/').endswith(('/api/reservas', '/api/admin/reservas')) and r.status_code == 201:
        try:
            RESERVAS_CREADAS.append(r.json()['codigoReserva'])
        except (ValueError, KeyError):
            pass
    return r


requests.post = _post_registrando


def paso(nombre, ok, detalle=''):
    resultados.append((nombre, ok))
    print(f"[{'OK' if ok else 'FALLO'}] {nombre}" + (f' · {detalle}' if detalle else ''))
    return ok


def esperar(condicion, segundos=20):
    fin = time.time() + segundos
    while time.time() < fin:
        valor = condicion()
        if valor:
            return valor
        time.sleep(1)
    return None


def ruc_valido(diez):
    pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2]
    d = 11 - sum(int(c) * p for c, p in zip(diez, pesos)) % 11
    return diez + str(0 if d == 10 else 1 if d == 11 else d)


def turno_libre(fecha, id_cancha, noche=True):
    r = requests.get(f'{API}/disponibilidad', params={'fecha': fecha, 'idCancha': id_cancha}, timeout=15).json()
    libres = [t for c in r['canchas'] for t in c['turnos'] if t['estado'] == 'DISPONIBLE']
    libres = [t for t in libres if (t['franja'] == 'NOCHE') == noche] or libres
    return random.choice(libres) if libres else None


def cliente(dni, nombres, apellidos, celular):
    return {'dni': dni, 'nombres': nombres, 'apellidos': apellidos, 'telefono': celular,
            'correo': f'{nombres.split()[0].lower()}.prueba@example.com'}


def login(usuario, clave):
    r = requests.post(f'{API}/auth/login', json={'usuario': usuario, 'contrasena': clave}, timeout=15)
    return r.json().get('token') if r.status_code == 200 else None


CLAVE_CLIENTE = 'ClienteE2e2026'


def cuenta_cliente(dni, nombres='Cliente', apellidos='Prueba Automatica', telefono='987000111'):
    """Inicia sesión con la cuenta del DNI o, si no existe, la crea; devuelve su token o None. El gateway limita el
    registro a 5/min y el login a 10/min por IP: ante un 429 espera el minuto y reintenta una vez."""
    def enviar(ruta, cuerpo):
        r = requests.post(f'{API}/auth/clientes/{ruta}', json=cuerpo, timeout=15)
        if r.status_code == 429:
            time.sleep(61)
            r = requests.post(f'{API}/auth/clientes/{ruta}', json=cuerpo, timeout=15)
        return r
    r = enviar('login', {'usuario': dni, 'contrasena': CLAVE_CLIENTE})
    if r.status_code == 200:
        return r.json()['token']
    r = enviar('registro', {'dni': dni, 'nombres': nombres, 'apellidos': apellidos, 'correo': f'e2e.{dni}@example.com',
                            'telefono': telefono, 'contrasena': CLAVE_CLIENTE, 'confirmacion': CLAVE_CLIENTE,
                            'aceptaTerminos': True, 'aceptaPrivacidad': True})
    return r.json().get('token') if r.status_code == 201 else None


def turnos_de(fecha, id_cancha):
    r = requests.get(f'{API}/disponibilidad', params={'fecha': fecha, 'idCancha': id_cancha}, timeout=15).json()
    return [t for c in r['canchas'] for t in c['turnos']]


def fechas_libres(desde, hasta):
    """Fechas sin día especial entre hoy + desde y hoy + hasta (para no chocar con feriados reales)."""
    especiales = {d['fecha'] for d in requests.get(f'{API}/catalogo/calendario', params={
        'desde': date.today().isoformat(), 'hasta': (date.today() + timedelta(days=hasta)).isoformat()}, timeout=15).json()}
    return [f for f in ((date.today() + timedelta(days=n)).isoformat() for n in range(desde, hasta + 1)) if f not in especiales]


def crear_dia(h, fecha_candidatas, tipo, nombre, canchas=None):
    """Registra el día especial en la primera fecha que acepte (salta las que ya tienen reservas). Devuelve (fecha, id)."""
    for f in fecha_candidatas:
        r = requests.post(f'{API}/admin/calendario', json={'fecha': f, 'tipo': tipo, 'nombre': nombre, 'canchas': canchas},
                          headers=h, timeout=15)
        if r.status_code == 201:
            return f, r.json()['id']
    return None, None


def seccion_v2(h, canchas):
    """Versión 2: varios turnos, horario por cancha, feriado con recargo, no laborable y cuentas de clientes."""
    pct = requests.get(f'{API}/catalogo/complejo', timeout=15).json()['reglas']['adelantoPct']

    # 13. Reserva de 2 turnos NO seguidos, sin cuenta y con un DNI nuevo (luego ese DNI crea su cuenta)
    fecha_v = (date.today() + timedelta(days=4)).isoformat()
    cancha_v = canchas[0]['id']
    libres = [t for t in turnos_de(fecha_v, cancha_v) if t['estado'] == 'DISPONIBLE']
    par = next(((a, b) for i, a in enumerate(libres) for b in libres[i + 1:] if b['horaInicio'] != a['horaFin']), None)
    dni_n = '98' + f'{random.randint(0, 999999):06d}'
    cli_n = {'dni': dni_n, 'nombres': 'Lucía', 'apellidos': 'Mendoza Paredes', 'telefono': '987111222',
             'correo': f'e2e.{dni_n}@example.com'}
    rv = requests.post(f'{API}/reservas', json={'idsTurno': [par[0]['idTurno'], par[1]['idTurno']], 'cliente': cli_n,
                       'metodoPago': 'YAPE', 'aceptaTerminos': True, 'aceptaPrivacidad': True}, timeout=20)
    j = rv.json()
    total = Decimal(str(par[0]['precio'])) + Decimal(str(par[1]['precio']))
    adelanto = (total * Decimal(pct) / 100).quantize(Decimal('0.01'), ROUND_HALF_UP)
    paso('Reserva de 2 turnos no seguidos: total = suma y adelanto = total × %', rv.status_code == 201
         and Decimal(str(j.get('montoTotal'))) == total and Decimal(str(j.get('montoAdelanto'))) == adelanto
         and j.get('cantidadTurnos') == 2 and len(j.get('turnos', [])) == 2,
         f"{j.get('codigoReserva')} · total S/ {j.get('montoTotal')} (esperado {total}) · adelanto S/ {j.get('montoAdelanto')} (esperado {adelanto})")
    paso('Horario en texto de los turnos no seguidos', ' y ' in (j.get('horarioTexto') or ''), j.get('horarioTexto'))
    cod_v = j.get('codigoReserva')
    ocupados = {t['idTurno']: t['estado'] for t in turnos_de(fecha_v, cancha_v)}
    paso('Los 2 turnos quedan OCUPADOS', ocupados.get(par[0]['idTurno']) == 'OCUPADO' and ocupados.get(par[1]['idTurno']) == 'OCUPADO')
    choque = requests.post(f'{API}/admin/calendario', json={'fecha': fecha_v, 'tipo': 'NO_LABORABLE',
                           'nombre': 'Prueba automática con reservas', 'canchas': [cancha_v]}, headers=h, timeout=15)
    paso('No laborable en un día con reservas: 409 DIA_CON_RESERVAS con el código', choque.status_code == 409
         and choque.json().get('codigo') == 'DIA_CON_RESERVAS' and cod_v in choque.text, choque.json().get('mensaje'))
    if choque.status_code == 201:
        requests.delete(f"{API}/admin/calendario/{choque.json()['id']}", headers=h, timeout=15)

    # 14. Cambio de horario de una cancha → regenera sus turnos (y se restaura)
    losa = canchas[3]['id']
    fecha_h = (date.today() + timedelta(days=8))
    horario = requests.get(f'{API}/admin/canchas/{losa}/horario', headers=h, timeout=15).json()
    original = {k: horario[k] for k in ('duracionTurnoMin', 'inicioNoche', 'dias', 'feriado')}
    cambiado = json.loads(json.dumps(original))
    dia = next(d for d in cambiado['dias'] if d['diaSemana'] == fecha_h.isoweekday())
    apertura_original = dia['apertura']
    dia.update({'abierto': True, 'apertura': '10:00'})
    put = requests.put(f'{API}/admin/canchas/{losa}/horario', json=cambiado, headers=h, timeout=15)
    temprano = lambda: [t for t in turnos_de(fecha_h.isoformat(), losa) if t['horaInicio'] < '10:00' and t['estado'] != 'OCUPADO']
    sin_temprano = put.status_code == 200 and esperar(lambda: not temprano() and bool(turnos_de(fecha_h.isoformat(), losa)), 30)
    paso('Cambiar el horario (abre 10:00 a. m.) regenera los turnos de la cancha', bool(sin_temprano),
         f"{fecha_h} · primer turno {next((t['horaInicio'] for t in turnos_de(fecha_h.isoformat(), losa)), '-')}")
    rest = requests.put(f'{API}/admin/canchas/{losa}/horario', json=original, headers=h, timeout=15)
    vuelve = rest.status_code == 200 and esperar(lambda: bool(temprano()) or apertura_original >= '10:00', 30)
    paso('Restaurar el horario devuelve los turnos tempranos', bool(vuelve), f'abre otra vez {apertura_original}')

    # 15. Feriado con recargo (cancha ABIERTA) y día no laborable que bloquea
    candidatas = fechas_libres(6, 13)
    cancha_f = canchas[1]['id']
    recargo = requests.get(f"{API}/catalogo/canchas/{cancha_f}", timeout=15).json()['feriado']
    antes = {}
    for f in candidatas:
        antes[f] = {t['idTurno']: t['precio'] for t in turnos_de(f, cancha_f) if t['estado'] == 'DISPONIBLE'}
    fecha_f, id_f = crear_dia(h, candidatas, 'FERIADO', 'Feriado de prueba automática')
    if fecha_f:
        def con_recargo():
            ts = [t for t in turnos_de(fecha_f, cancha_f) if t['idTurno'] in antes[fecha_f] and t['estado'] == 'DISPONIBLE']
            return ts if ts and all(t.get('feriado') and abs(t['precio'] - antes[fecha_f][t['idTurno']] - t['recargo']) < 0.001
                                    and t['recargo'] > 0 for t in ts) else None
        ts = esperar(con_recargo, 30)
        paso('Feriado: la cancha abre con recargo por turno', bool(ts) and recargo['modo'] == 'ABIERTA',
             f"{fecha_f} · recargo S/ {ts[0]['recargo'] if ts else '?'} · {ts[0]['horaInicio'] if ts else ''} S/ {ts[0]['precio'] if ts else ''}")
        requests.delete(f'{API}/admin/calendario/{id_f}', headers=h, timeout=15)
        sin = esperar(lambda: all(not t.get('feriado') for t in turnos_de(fecha_f, cancha_f)), 30)
        paso('Al quitar el feriado vuelve el precio normal', bool(sin))
    else:
        paso('Feriado: la cancha abre con recargo por turno', False, 'no se pudo registrar el feriado de prueba')
    cancha_n = canchas[3]['id']
    fecha_n, id_n = crear_dia(h, [f for f in candidatas if f != fecha_f][::-1], 'NO_LABORABLE', 'Mantenimiento de prueba automática', [cancha_n])
    if fecha_n:
        bloqueada = esperar(lambda: (lambda ts: ts and all(t['estado'] in ('BLOQUEADO', 'PASADO') for t in ts))(turnos_de(fecha_n, cancha_n)), 30)
        otra = [t for t in turnos_de(fecha_n, canchas[2]['id']) if t['estado'] == 'DISPONIBLE']
        paso('Día no laborable: bloquea los turnos solo de la cancha indicada', bool(bloqueada) and bool(otra), fecha_n)
        requests.delete(f'{API}/admin/calendario/{id_n}', headers=h, timeout=15)
        libre = esperar(lambda: any(t['estado'] == 'DISPONIBLE' for t in turnos_de(fecha_n, cancha_n)), 30)
        paso('Al quitar el día no laborable los turnos vuelven a estar libres', bool(libre))
    else:
        paso('Día no laborable: bloquea los turnos solo de la cancha indicada', False, 'no se pudo registrar el día de prueba')

    # 16. Cuenta del cliente y «Mis reservas» (incluye la reserva hecha ANTES de crear la cuenta)
    reg = requests.post(f'{API}/auth/clientes/registro', json={**cli_n, 'contrasena': CLAVE_CLIENTE, 'confirmacion': CLAVE_CLIENTE,
                        'aceptaTerminos': True, 'aceptaPrivacidad': True}, timeout=15)
    paso('Registro de cliente: 201 con sesión iniciada', reg.status_code == 201 and reg.json().get('cliente', {}).get('dni') == dni_n,
         reg.text[:120] if reg.status_code != 201 else dni_n)
    dup = requests.post(f'{API}/auth/clientes/registro', json={**cli_n, 'correo': f'otro.{dni_n}@example.com', 'contrasena': CLAVE_CLIENTE,
                        'confirmacion': CLAVE_CLIENTE, 'aceptaTerminos': True, 'aceptaPrivacidad': True}, timeout=15)
    paso('Mismo DNI otra vez: 409 DNI_REGISTRADO', dup.status_code == 409 and dup.json().get('codigo') == 'DNI_REGISTRADO')
    por_dni = requests.post(f'{API}/auth/clientes/login', json={'usuario': dni_n, 'contrasena': CLAVE_CLIENTE}, timeout=15)
    por_correo = requests.post(f'{API}/auth/clientes/login', json={'usuario': cli_n['correo'], 'contrasena': CLAVE_CLIENTE}, timeout=15)
    paso('Login del cliente con DNI y con correo', por_dni.status_code == 200 and por_correo.status_code == 200)
    tc = {'Authorization': f"Bearer {por_dni.json().get('token')}"} if por_dni.status_code == 200 else {}
    mias = requests.get(f'{API}/cliente/reservas', headers=tc, timeout=15)
    codigos = [x['codigoReserva'] for x in mias.json().get('contenido', [])] if mias.status_code == 200 else []
    paso('Mis reservas incluye la reserva hecha ANTES de crear la cuenta', cod_v in codigos, ', '.join(codigos[:5]))
    paso('Mis reservas sin sesión: 401', requests.get(f'{API}/cliente/reservas', timeout=10).status_code == 401)
    paso('Mis reservas con token de administradora: 403', requests.get(f'{API}/cliente/reservas', headers=h, timeout=10).status_code == 403)
    paso('Panel con token de cliente: 403', requests.get(f'{API}/admin/reservas', headers=tc, timeout=10).status_code == 403)
    can = requests.post(f'{API}/reservas/{cod_v}/cancelar', json={'dni': dni_n, 'motivo': 'Prueba automática de varios turnos'}, timeout=20)
    libres_otra_vez = {t['idTurno']: t['estado'] for t in turnos_de(fecha_v, cancha_v)}
    paso('Cancelar la reserva libera sus 2 turnos', can.status_code == 200 and libres_otra_vez.get(par[0]['idTurno']) == 'DISPONIBLE'
         and libres_otra_vez.get(par[1]['idTurno']) == 'DISPONIBLE')
    recuperar_contrasena(dni_n)


RESERVAS_URL = os.environ.get('RESERVAS_URL', 'http://localhost:8093').rstrip('/')
INTERNO_TOKEN = os.environ.get('INTERNO_TOKEN', 'token-interno-solo-para-desarrollo-local')


def seccion_reprogramar(h, canchas):
    """18. Cambiar el horario conservando lo pagado: el cliente una vez (la 2.ª → 422), el panel con motivo y sin límite,
    el aviso «¡Cambiamos tu horario!», la bitácora y (solo local, directo a reservas-service con el token interno) las
    reservas por DNI + celular que usa Canchita. El celular es ficticio y fijo; la reserva se cancela con E2E_LIMPIAR=1."""
    fechas = fechas_libres(4, 9)
    if not paso('Reprogramar: hay dos días sin feriados para probar', len(fechas) >= 2, ', '.join(fechas)):
        return
    dni, celular = '7' + str(random.randint(1000000, 9999999)), '987000333'
    t1 = turno_libre(fechas[0], canchas[0]['id'])
    r = requests.post(f'{API}/reservas', json={'idsTurno': [t1['idTurno']], 'cliente': cliente(dni, 'Rocío', 'Salas Pinto', celular),
                      'metodoPago': 'YAPE', 'aceptaTerminos': True, 'aceptaPrivacidad': True}, timeout=20)
    if not paso('Reprogramar: reserva de prueba registrada', r.status_code == 201, r.text[:120]):
        return
    codigo = r.json()['codigoReserva']
    antes = requests.get(f'{API}/reservas/{codigo}', params={'dni': dni}, timeout=10).json()
    paso('Mi reserva ofrece cambiar el horario (puedeReprogramar, 0 cambios)',
         antes.get('puedeReprogramar') is True and antes.get('reprogramaciones') == 0)
    mismo = requests.post(f'{API}/reservas/{codigo}/reprogramar', json={'dni': dni, 'idsTurno': [t1['idTurno']]}, timeout=20)
    paso('Mismo horario: 422 REPROGRAMAR_MISMO_HORARIO', mismo.status_code == 422
         and mismo.json().get('codigo') == 'REPROGRAMAR_MISMO_HORARIO', mismo.json().get('mensaje'))

    t2 = turno_libre(fechas[1], canchas[1]['id'])
    cambio = requests.post(f'{API}/reservas/{codigo}/reprogramar', json={'dni': dni, 'idsTurno': [t2['idTurno']]}, timeout=20)
    cj = cambio.json() if cambio.status_code == 200 else {}
    paso('Cliente cambia de cancha y de día (lo pagado se conserva y el total es el del nuevo turno)',
         cambio.status_code == 200 and cj.get('fecha') == fechas[1] and cj['cancha']['id'] == canchas[1]['id']
         and float(cj['montoTotal']) == float(t2['precio']) and cj.get('reprogramaciones') == 1
         and cj.get('puedeReprogramar') is False and cj['linea'][-1]['evento'] == 'REPROGRAMADA',
         cj['linea'][-1]['detalle'] if cj else cambio.text[:160])
    viejo = next((t['estado'] for t in turnos_de(fechas[0], canchas[0]['id']) if t['idTurno'] == t1['idTurno']), None)
    paso('El turno anterior quedó libre para otros clientes', viejo == 'DISPONIBLE', viejo)
    t3 = turno_libre(fechas[1], canchas[1]['id'])
    otra = requests.post(f'{API}/reservas/{codigo}/reprogramar', json={'dni': dni, 'idsTurno': [t3['idTurno']]}, timeout=20)
    paso('Segundo cambio del cliente: 422 REPROGRAMACION_NO_PERMITIDA', otra.status_code == 422
         and otra.json().get('codigo') == 'REPROGRAMACION_NO_PERMITIDA', otra.json().get('mensaje'))

    corto = requests.post(f'{API}/admin/reservas/{codigo}/reprogramar', json={'idsTurno': [t3['idTurno']], 'motivo': 'ok'},
                          headers=h, timeout=20)
    paso('Panel con motivo inválido: 400 en el campo motivo', corto.status_code == 400
         and any(e['campo'] == 'motivo' for e in corto.json().get('errores', [])))
    panel = requests.post(f'{API}/admin/reservas/{codigo}/reprogramar', headers=h, timeout=20,
                          json={'idsTurno': [t3['idTurno']], 'motivo': 'El cliente pidió otra hora por teléfono'})
    pj = panel.json() if panel.status_code == 200 else {}
    historial = pj.get('reprogramaciones', [])
    paso('Panel cambia sin límite y guarda el historial (el más reciente primero)', panel.status_code == 200
         and [x['actor'] for x in historial] == ['PANEL', 'CLIENTE'] and historial[0]['motivo'].startswith('El cliente'),
         ' · '.join(f"{x['actor']}: {x['anterior']['horarioTexto']} → {x['nuevo']['horarioTexto']}" for x in historial)
         if historial else panel.text[:160])
    publica = requests.get(f'{API}/reservas/{codigo}', params={'dni': dni}, timeout=10)
    paso('El motivo del panel es interno: no sale en Mi reserva', publica.status_code == 200
         and 'pidió otra hora por teléfono' not in publica.text, publica.json()['linea'][-1]['detalle']
         if publica.status_code == 200 else publica.text[:120])
    avisos = esperar(lambda: (lambda n: n if n.get('totalElementos', 0) >= 4 else None)(requests.get(
        f'{API}/admin/notificaciones', params={'q': codigo, 'evento': 'reserva.reprogramada', 'size': 10}, headers=h,
        timeout=10).json()), 30)
    paso('Aviso «¡Cambiamos tu horario!» por correo y WhatsApp en cada cambio (reserva.reprogramada)', bool(avisos),
         ', '.join(sorted({f"{n['canal']}/{n['estado']}" for n in (avisos or {}).get('contenido', [])})))
    bit = esperar(lambda: (lambda l: l if len(l) >= 2 else None)([b for b in requests.get(
        f'{API}/admin/bitacora', params={'accion': 'REPROGRAMAR_RESERVA', 'size': 20}, headers=h, timeout=10).json()
        .get('contenido', []) if b.get('idEntidad') == codigo]), 20) or []
    paso('Bitácora: REPROGRAMAR_RESERVA del cliente y del panel', len(bit) >= 2, '; '.join(b['actor'] for b in bit))

    if LOCAL:
        interno = {'X-Interno-Token': INTERNO_TOKEN}
        lista = requests.get(f'{RESERVAS_URL}/interno/reservas/por-cliente', params={'dni': dni, 'telefono': '51' + celular},
                             headers=interno, timeout=10)
        paso('Canchita: reservas por DNI + celular registrado (con prefijo 51)', lista.status_code == 200
             and codigo in [x['codigo'] for x in lista.json()], lista.text[:160])
        ajeno = requests.get(f'{RESERVAS_URL}/interno/reservas/por-cliente', params={'dni': dni, 'telefono': '900000000'},
                             headers=interno, timeout=10)
        paso('Canchita: con otro celular la lista sale vacía', ajeno.status_code == 200 and ajeno.json() == [])
        # Otra reserva con el MISMO DNI y otro celular (como haría alguien que conoce el DNI): cada número ve solo las suyas.
        otro_celular = '987000444'
        t4 = turno_libre(fechas[0], canchas[0]['id'])
        segunda = requests.post(f'{API}/reservas', json={'idsTurno': [t4['idTurno']], 'cliente': cliente(
            dni, 'Rocío', 'Salas Pinto', otro_celular), 'metodoPago': 'YAPE', 'aceptaTerminos': True,
            'aceptaPrivacidad': True}, timeout=20)
        if paso('Canchita: segunda reserva del mismo DNI con otro celular', segunda.status_code == 201, segunda.text[:120]):
            codigo2 = segunda.json()['codigoReserva']
            del_otro = requests.get(f'{RESERVAS_URL}/interno/reservas/por-cliente',
                                    params={'dni': dni, 'telefono': otro_celular}, headers=interno, timeout=10)
            del_dueno = requests.get(f'{RESERVAS_URL}/interno/reservas/por-cliente',
                                     params={'dni': dni, 'telefono': '+51 ' + celular}, headers=interno, timeout=10)
            paso('Canchita: el otro celular solo ve su reserva (no las anteriores del DNI)', del_otro.status_code == 200
                 and [x['codigo'] for x in del_otro.json()] == [codigo2], del_otro.text[:160])
            paso('Canchita: el celular original sigue viendo las suyas y no la nueva', del_dueno.status_code == 200
                 and codigo in [x['codigo'] for x in del_dueno.json()]
                 and codigo2 not in [x['codigo'] for x in del_dueno.json()], del_dueno.text[:160])
        malo = requests.get(f'{RESERVAS_URL}/interno/reservas/por-cliente', params={'dni': '123', 'telefono': celular},
                            headers=interno, timeout=10)
        paso('Canchita: DNI inválido → 400', malo.status_code == 400)
    oculta = requests.get(f'{BASE}/interno/reservas/por-cliente', params={'dni': dni, 'telefono': celular}, timeout=10)
    paso('La ruta interna por-cliente no se publica en el gateway', oculta.status_code in (403, 404), str(oculta.status_code))


def recuperar_contrasena(dni):
    """17. ¿Olvidaste tu contraseña? (cliente, por correo). El gateway permite 5 llamadas por minuto por IP y aquí se
    usan 5 con una IP de prueba propia (X-Forwarded-For de la red 198.51.100.0/24; en producción nginx la reescribe), así
    no se comparte el cupo con otras pruebas de la PC. Si aun así llega un 429, espera lo que dice Retry-After."""
    ip = {'X-Forwarded-For': f'198.51.100.{random.randint(1, 254)}'}

    def post(paso, cuerpo):
        r = requests.post(f'{API}/auth/recuperar/{paso}', json=cuerpo, headers=ip, timeout=25)
        if r.status_code == 429:
            time.sleep(int(r.headers.get('Retry-After', '60')) + 1)
            r = requests.post(f'{API}/auth/recuperar/{paso}', json=cuerpo, headers=ip, timeout=25)
        return r
    base = {'tipoCuenta': 'CLIENTE', 'usuario': dni}
    ops = post('opciones', base)
    canales = {c['canal']: c['destino'] for c in ops.json().get('canales', [])} if ops.status_code == 200 else {}
    paso('Recuperar contraseña: canales con el destino enmascarado', canales.get('CORREO', '').startswith('e****@example.com')
         and canales.get('WHATSAPP') == '*** *** 222', str(canales))
    env = post('enviar', {**base, 'canal': 'CORREO'})
    ej = env.json() if env.status_code == 200 else {}
    paso('Recuperar contraseña: código enviado por correo (vence en 10 min, reenviar en 60 s)',
         env.status_code == 200 and ej.get('destino') == canales.get('CORREO') and ej.get('reenviarEnSegundos') == 60,
         f"simulado={ej.get('simulado')} · vence {ej.get('expiraEn')}" if ej else env.text[:120])
    codigo = ej.get('codigoPrueba')
    if not codigo:
        print('[SALTADO] Verificar el código y cambiar la contraseña: el servidor no devuelve codigoPrueba (producción).')
        return
    malo = '000000' if codigo != '000000' else '111111'
    mal = post('verificar', {**base, 'codigo': malo})
    paso('Código incorrecto: 422 CODIGO_INCORRECTO con los intentos que quedan',
         mal.status_code == 422 and mal.json().get('codigo') == 'CODIGO_INCORRECTO', mal.json().get('mensaje'))
    ver = post('verificar', {**base, 'codigo': codigo})
    token = ver.json().get('tokenRestablecer') if ver.status_code == 200 else None
    paso('Código correcto: permiso de un solo uso para cambiar la contraseña', bool(token), ver.text[:120] if not token else '')
    nueva = 'ClienteNueva2027'
    res = post('restablecer', {'tokenRestablecer': token, 'nueva': nueva, 'confirmacion': nueva})
    paso('Contraseña cambiada con el código', res.status_code == 200, res.json().get('mensaje'))
    nuevo = requests.post(f'{API}/auth/clientes/login', json={'usuario': dni, 'contrasena': nueva}, timeout=15)
    viejo = requests.post(f'{API}/auth/clientes/login', json={'usuario': dni, 'contrasena': CLAVE_CLIENTE}, timeout=15)
    paso('Login con la contraseña nueva; la anterior ya no sirve', nuevo.status_code == 200 and viejo.status_code == 401)


def main():
    print(f'Prueba de punta a punta contra {BASE} · {datetime.now():%d/%m/%Y %H:%M}\n')

    # 1. Microservicios registrados y sanos
    estado = requests.get(f'{API}/estado', timeout=20).json()
    caidos = [s['nombre'] for s in estado['servicios'] if s['estado'] != 'UP']
    paso('Los 7 microservicios están UP en Eureka', not caidos,
         ', '.join(f"{s['nombre']}={s['estado']}" for s in estado['servicios']))

    # 2. Catálogo público y detalle de cancha con fotos y reseñas
    canchas = requests.get(f'{API}/catalogo/canchas', timeout=15).json()
    paso('Catálogo público con 4 canchas', len(canchas) == 4, ', '.join(c['nombre'] for c in canchas))
    det = requests.get(f"{API}/catalogo/canchas/{canchas[1]['slug']}", timeout=15).json()
    paso('Detalle de cancha con fotos y reseñas', len(det['fotos']) >= 3 and det['resumenResenas']['total'] > 0,
         f"{len(det['fotos'])} fotos · {det['resumenResenas']['total']} reseñas · promedio {det['resumenResenas']['promedio']}")
    foto = requests.get(BASE + det['fotos'][0]['url'] if det['fotos'][0]['url'].startswith('/') else det['fotos'][0]['url'], timeout=15)
    paso('La foto de la cancha se descarga', foto.status_code == 200 and foto.headers['Content-Type'].startswith('image/'))

    # 3. Validaciones del back-end (todos los campos)
    malo = requests.post(f'{API}/reservas', json={'idTurno': None, 'cliente': {'dni': '123', 'nombres': 'D1eg0', 'apellidos': '',
                         'telefono': '812345678', 'correo': 'no-es-correo'}, 'metodoPago': 'BITCOIN',
                         'aceptaTerminos': False, 'aceptaPrivacidad': False}, timeout=15)
    campos = sorted({e['campo'] for e in malo.json().get('errores', [])})
    paso('Validación: 400 con error por cada campo inválido', malo.status_code == 400 and len(campos) >= 7, ', '.join(campos))
    ruc_malo = requests.get(f'{API}/documentos/ruc/20612345678', timeout=15)
    paso('RUC con dígito verificador incorrecto se rechaza sin consultar', ruc_malo.status_code == 400)
    ruc_sunat = requests.get(f'{API}/documentos/ruc/20131312955', timeout=20)
    paso('Consulta SUNAT por RUC (Factiliza)', ruc_sunat.status_code in (200, 503),
         ruc_sunat.json().get('razonSocial') or ruc_sunat.json().get('codigo'))

    # 4. Reserva con Yape + comprobante
    manana = (date.today() + timedelta(days=1)).isoformat()
    t1 = turno_libre(manana, canchas[1]['id'])
    c1 = cliente('70123321', 'Diego Alonso', 'Ramos Quispe', '987 654 321')
    r1 = requests.post(f'{API}/reservas', json={'idTurno': t1['idTurno'], 'cliente': c1, 'metodoPago': 'YAPE',
                       'aceptaTerminos': True, 'aceptaPrivacidad': True}, timeout=20)
    ok = paso('Reserva web registrada (PENDIENTE, con plazo para pagar)', r1.status_code == 201, r1.text[:160])
    if not ok:
        return
    reserva = r1.json()
    codigo = reserva['codigoReserva']
    print(f"      código {codigo} · {reserva['cancha']['nombre']} · {reserva['fecha']} {reserva['horaInicio']} · adelanto S/ {reserva['montoAdelanto']}")

    doble = requests.post(f'{API}/reservas', json={'idTurno': t1['idTurno'], 'cliente': cliente('45678912', 'Camila', 'Torres Vega', '912345678'),
                          'metodoPago': 'PLIN', 'aceptaTerminos': True, 'aceptaPrivacidad': True}, timeout=20)
    paso('Doble reserva del mismo turno: 409 TURNO_OCUPADO', doble.status_code == 409 and doble.json().get('codigo') == 'TURNO_OCUPADO')

    with open(COMPROBANTE_IMG, 'rb') as fh:
        sub = requests.post(f'{API}/pagos/comprobante', data={'codigoReserva': codigo, 'dni': c1['dni'], 'metodo': 'YAPE',
                            'codigoOperacion': str(random.randint(10000000, 99999999)),
                            'comprobante': json.dumps({'tipo': 'BOLETA'})},
                            files={'archivo': ('yape.png', fh, 'image/png')}, timeout=30)
    paso('Comprobante de Yape subido (pago PENDIENTE)', sub.status_code == 201, sub.text[:120])
    falso = requests.post(f'{API}/pagos/comprobante', data={'codigoReserva': codigo, 'dni': c1['dni'], 'metodo': 'YAPE',
                          'codigoOperacion': '12345678'}, files={'archivo': ('foto.png', b'esto no es una imagen', 'image/png')}, timeout=20)
    paso('Imagen falsa (extensión cambiada) rechazada', falso.status_code == 400)
    en_revision = esperar(lambda: requests.get(f'{API}/reservas/{codigo}', params={'dni': c1['dni']}, timeout=10).json()
                          .get('estadoPago') == 'EN_REVISION')
    paso('Evento pago.registrado → reserva EN_REVISION (RabbitMQ)', bool(en_revision))

    # 5. Seguridad del panel
    paso('Panel sin token: 401', requests.get(f'{API}/admin/reservas', timeout=10).status_code == 401)
    token_rec = login('mtorres', CLAVE_RECEPCION)
    paso('RECEPCION no puede ver reportes: 403', token_rec and requests.get(
        f'{API}/admin/reportes/reservas', headers={'Authorization': f'Bearer {token_rec}'}, timeout=10).status_code == 403)
    token = login('rhuaman', CLAVE_ADMIN)
    h = {'Authorization': f'Bearer {token}'}
    paso('Login del administrador (JWT)', bool(token))

    # 5b. (solo local) Varios recordatorios: la lista pasa a «60,15» antes de confirmar; se restaura tras comprobarlo
    lista_original = None
    if LOCAL:
        lista_original = requests.get(f'{API}/admin/configuracion', headers=h, timeout=10).json().get('recordatorio_minutos')
        cambio = requests.put(f'{API}/admin/configuracion', json={'recordatorio_minutos': '15, 60'}, headers=h, timeout=15)
        paso('Configuración: recordatorios «60,15» (se guarda ordenada)', cambio.status_code == 200
             and cambio.json().get('recordatorio_minutos') == '60,15', f'antes «{lista_original}»')

    # 6. Validación del comprobante por la administradora
    pagos = requests.get(f'{API}/admin/pagos', params={'q': codigo}, headers=h, timeout=15).json()
    id_pago = next(p['id'] for p in pagos['contenido'] if p['codigoReserva'] == codigo)
    val = requests.post(f'{API}/admin/pagos/{id_pago}/validar', headers=h, timeout=20)
    paso('Administradora valida el comprobante', val.status_code == 200, val.text[:120])
    conf = esperar(lambda: (lambda d: d if d.get('estado') == 'CONFIRMADA' else None)(
        requests.get(f'{API}/reservas/{codigo}', params={'dni': c1['dni']}, timeout=10).json()))
    paso('Evento pago.validado → reserva CONFIRMADA', bool(conf), conf and f"pagado S/ {conf['montoPagado']} · saldo S/ {conf['saldo']}")
    pagos_cli = requests.get(f'{API}/pagos/reserva/{codigo}', params={'dni': c1['dni']}, timeout=10).json()
    boleta = next((p['comprobante'] for p in pagos_cli if p.get('comprobante')), None)
    paso('Boleta electrónica (simulada) emitida', bool(boleta), boleta and boleta['serieNumero'])
    if boleta:
        pdf = requests.get(f"{API}/comprobantes/{boleta['serieNumero']}.pdf", params={'codigo': codigo, 'dni': c1['dni']}, timeout=20)
        paso('PDF de la boleta descargable por el cliente', pdf.content[:4] == b'%PDF', f'{len(pdf.content) // 1024} KB')
    cons = requests.get(f'{API}/reservas/{codigo}/constancia.pdf', params={'dni': c1['dni']}, timeout=20)
    paso('Constancia de reserva en PDF', cons.content[:4] == b'%PDF', f'{len(cons.content) // 1024} KB')

    # 7. Notificaciones y recordatorio
    notifs = esperar(lambda: (lambda n: n if n.get('totalElementos', 0) >= 8 else None)(
        requests.get(f'{API}/admin/notificaciones', params={'q': codigo, 'size': 50}, headers=h, timeout=10).json()), 30)
    lista = (notifs or {}).get('contenido', [])
    paso('Notificaciones por correo y WhatsApp (registrada, comprobante, confirmada, boleta)', len(lista) >= 8,
         ', '.join(sorted({f"{n['evento']}/{n['canal']}/{n['estado']}" for n in lista}))[:300])
    minimo = 2 if LOCAL else 1
    mio = esperar(lambda: (lambda l: l if len(l) >= minimo else None)(requests.get(
        f'{API}/admin/recordatorios', params={'codigo': codigo, 'size': 10}, headers=h, timeout=10).json().get('contenido', [])), 20) or []
    if LOCAL:
        paso('Varios recordatorios: 2 programados, 60 y 15 minutos antes del partido',
             sorted(r['minutosAntes'] for r in mio) == [15, 60] and all(r['estado'] == 'PROGRAMADO' for r in mio),
             '; '.join(f"{r['minutosAntes']} min → {r['enviarEn']} {r['estado']}" for r in mio))
        if lista_original:
            restaurada = requests.put(f'{API}/admin/configuracion', json={'recordatorio_minutos': lista_original}, headers=h, timeout=15)
            paso('Configuración de recordatorios restaurada', restaurada.status_code == 200
                 and restaurada.json().get('recordatorio_minutos') == lista_original, lista_original)
    else:
        paso('Recordatorio programado antes del partido (cron)', bool(mio) and mio[0]['estado'] in ('PROGRAMADO', 'ENVIADO', 'OMITIDO'),
             mio and f"enviar en {mio[0].get('enviarEn')} · {mio[0]['estado']}")

    # 8. Pasarela Alcanfores Pay (beta) con factura
    t2 = turno_libre((date.today() + timedelta(days=3)).isoformat(), canchas[2]['id'])
    c2 = cliente('45678912', 'Camila', 'Torres Vega', '912345678')
    r2 = requests.post(f'{API}/reservas', json={'idTurno': t2['idTurno'], 'cliente': c2, 'metodoPago': 'TARJETA',
                       'aceptaTerminos': True, 'aceptaPrivacidad': True}, timeout=20).json()
    cod2 = r2['codigoReserva']
    ruc = ruc_valido('2060' + str(random.randint(100000, 999999)))
    intento = requests.post(f'{API}/pagos/pasarela/intentos', json={'codigoReserva': cod2, 'dni': c2['dni'], 'tipoPago': 'TOTAL',
                            'comprobante': {'tipo': 'FACTURA', 'ruc': ruc, 'razonSocial': 'Club Deportivo Los Pumas S.A.C. (prueba)',
                                            'direccionFiscal': 'Av. Nicolás Ayllón 1234, Ate, Lima'}}, timeout=20).json()
    ref = intento['referencia']
    malo = requests.post(f'{API}/pasarela/intentos/{ref}/tarjeta', json={'numero': '4000000000000002', 'titular': 'CAMILA TORRES',
                         'mesVencimiento': 12, 'anioVencimiento': date.today().year + 2, 'cvv': '123', 'correo': c2['correo']}, timeout=20).json()
    paso('Pasarela beta: tarjeta sin fondos rechazada', malo.get('estado') == 'RECHAZADO', malo.get('motivo'))
    bueno = requests.post(f'{API}/pasarela/intentos/{ref}/tarjeta', json={'numero': '4111111111111111', 'titular': 'CAMILA TORRES',
                          'mesVencimiento': 12, 'anioVencimiento': date.today().year + 2, 'cvv': '123', 'correo': c2['correo']}, timeout=20).json()
    if bueno.get('estado') != 'APROBADO':
        # Un intento rechazado puede cerrarse: se crea otro y se paga con la tarjeta aprobada
        ref = requests.post(f'{API}/pagos/pasarela/intentos', json={'codigoReserva': cod2, 'dni': c2['dni'], 'tipoPago': 'TOTAL',
                            'comprobante': {'tipo': 'FACTURA', 'ruc': ruc, 'razonSocial': 'Club Deportivo Los Pumas S.A.C. (prueba)',
                                            'direccionFiscal': 'Av. Nicolás Ayllón 1234, Ate, Lima'}}, timeout=20).json()['referencia']
        bueno = requests.post(f'{API}/pasarela/intentos/{ref}/tarjeta', json={'numero': '4111111111111111', 'titular': 'CAMILA TORRES',
                              'mesVencimiento': 12, 'anioVencimiento': date.today().year + 2, 'cvv': '123', 'correo': c2['correo']}, timeout=20).json()
    paso('Pasarela beta: Visa de prueba aprobada', bueno.get('estado') == 'APROBADO',
         f"autorización {bueno.get('codigoAutorizacion')} · {bueno.get('marca')} ****{bueno.get('ultimos4')}")
    conf2 = esperar(lambda: (lambda d: d if d.get('estado') == 'CONFIRMADA' else None)(
        requests.get(f'{API}/reservas/{cod2}', params={'dni': c2['dni']}, timeout=10).json()))
    paso('Webhook firmado → pago automático → reserva CONFIRMADA y PAGADO_TOTAL', bool(conf2) and conf2['estadoPago'] == 'PAGADO_TOTAL')
    fac = next((p['comprobante'] for p in requests.get(f'{API}/pagos/reserva/{cod2}', params={'dni': c2['dni']}, timeout=10).json()
                if p.get('comprobante')), None)
    paso('Factura electrónica (simulada) emitida al RUC', bool(fac) and fac['serieNumero'].startswith('F'), fac and fac['serieNumero'])

    # 9. Cancelación por el cliente con devolución → nota de crédito
    can = requests.post(f'{API}/reservas/{cod2}/cancelar', json={'dni': c2['dni'], 'motivo': 'Se suspendió el partido del equipo por lluvia'}, timeout=20)
    paso('Cliente cancela con motivo (≥ 24 h: con devolución)', can.status_code == 200 and can.json().get('estado') == 'CANCELADA')
    cj = can.json() if can.status_code == 200 else {}
    paso('Cancelada con devolución: estadoPago REEMBOLSADO y saldo 0', cj.get('estadoPago') == 'REEMBOLSADO' and cj.get('saldo') == 0,
         f"estadoPago {cj.get('estadoPago')} · pagado S/ {cj.get('montoPagado')} · saldo S/ {cj.get('saldo')}")
    paso('Línea de tiempo sin doble punto', all('..' not in (e.get('detalle') or '') for e in cj.get('linea', [])))
    nc = esperar(lambda: next((p['notaCredito'] for p in requests.get(f'{API}/pagos/reserva/{cod2}', params={'dni': c2['dni']}, timeout=10).json()
                               if p.get('notaCredito')), None), 30)
    paso('Evento reserva.cancelada → reembolso simulado y nota de crédito', bool(nc), nc and nc['serieNumero'])
    libre = requests.get(f'{API}/disponibilidad', params={'fecha': t2 and (date.today() + timedelta(days=3)).isoformat(),
                         'idCancha': canchas[2]['id']}, timeout=10).json()
    estado_t2 = next(t['estado'] for c in libre['canchas'] for t in c['turnos'] if t['idTurno'] == t2['idTurno'])
    paso('El turno cancelado vuelve a estar DISPONIBLE', estado_t2 == 'DISPONIBLE')

    seccion_v2(h, canchas)
    seccion_reprogramar(h, canchas)

    # 10. Reseña verificada (reserva sembrada ya jugada) · v2: con la sesión del cliente (cuenta de ese DNI)
    pasadas = requests.get(f'{API}/admin/reservas', params={'estado': 'CONFIRMADA', 'hasta': (date.today() - timedelta(days=1)).isoformat(),
                           'size': 50}, headers=h, timeout=15).json()['contenido']
    hecho = False
    for rv in pasadas:
        det_r = requests.get(f"{API}/admin/reservas/{rv['codigoReserva']}", headers=h, timeout=10).json()
        dni = det_r['cliente']['dni']
        id_cancha = det_r['cancha']['id'] if isinstance(det_r.get('cancha'), dict) else det_r.get('idCancha')
        publica = requests.get(f"{API}/reservas/{rv['codigoReserva']}", params={'dni': dni}, timeout=10).json()
        if not publica.get('puedeResenar'):
            continue
        token_cli = cuenta_cliente(dni)
        if not token_cli:
            continue
        hc = {'Authorization': f'Bearer {token_cli}'}
        res = requests.post(f'{API}/catalogo/canchas/{id_cancha}/resenas', json={'codigoReserva': rv['codigoReserva'],
                            'puntuacion': 5, 'comentario': 'Muy buena cancha, el grass está parejo y la iluminación es excelente.'},
                            headers=hc, timeout=15)
        if res.status_code == 201:
            dup = requests.post(f'{API}/catalogo/canchas/{id_cancha}/resenas', json={'codigoReserva': rv['codigoReserva'],
                                'puntuacion': 4, 'comentario': 'Intento repetir la reseña de la misma reserva.'}, headers=hc, timeout=15)
            paso('Reseña verificada publicada (con sesión del cliente de una reserva jugada)', True, rv['codigoReserva'])
            paso('Reseña repetida: 409 RESENA_DUPLICADA', dup.status_code == 409)
            hecho = True
            break
    if not hecho:
        paso('Reseña verificada publicada', False, 'ninguna reserva pasada fue elegible')
    sin_sesion = requests.post(f"{API}/catalogo/canchas/{canchas[1]['id']}/resenas", json={'codigoReserva': codigo,
                               'puntuacion': 5, 'comentario': 'Todavía no juego pero ya quiero opinar.'}, timeout=15)
    paso('Reseña sin iniciar sesión: 401', sin_sesion.status_code == 401, sin_sesion.json().get('mensaje'))
    token_c1 = cuenta_cliente(c1['dni'], 'Diego Alonso', 'Ramos Quispe', '987654321')
    sin_reserva = requests.post(f"{API}/catalogo/canchas/{canchas[1]['id']}/resenas", json={'codigoReserva': codigo,
                                'puntuacion': 5, 'comentario': 'Todavía no juego pero ya quiero opinar.'},
                                headers={'Authorization': f'Bearer {token_c1}'}, timeout=15)
    paso('Reseña de una reserva aún no jugada: rechazada (422)', sin_reserva.status_code == 422, sin_reserva.json().get('mensaje'))

    # 10b. Reseña del complejo (la registra la administradora desde la cancha)
    rc = requests.post(f"{API}/admin/canchas/{canchas[1]['id']}/resenas", json={'autor': 'Club Los Pumas', 'puntuacion': 5,
                       'comentario': 'Jugamos el campeonato interno y todo estuvo impecable.', 'fecha': date.today().isoformat()},
                       headers=h, timeout=15)
    publicas = requests.get(f"{API}/catalogo/canchas/{canchas[1]['id']}/resenas", params={'orden': 'recientes', 'size': 50}, timeout=15).json()
    id_rc = rc.json().get('id') if rc.status_code == 201 else None
    visible = any(x['id'] == id_rc and x.get('origen') == 'COMPLEJO' for x in publicas.get('contenido', []))
    paso('Reseña del complejo creada y visible en la cancha (origen COMPLEJO)', rc.status_code == 201 and visible)
    if id_rc:
        paso('Reseña del complejo borrada por la administradora', requests.delete(f'{API}/admin/resenas/{id_rc}', headers=h,
             timeout=15).status_code == 204)

    # 11. Panel: dashboard, reportes y bitácora
    dash = requests.get(f'{API}/admin/dashboard', headers=h, timeout=15).json()
    paso('Dashboard del día', 'kpis' in dash, json.dumps(dash.get('kpis'), ensure_ascii=False)[:200])
    rep = requests.get(f'{API}/admin/reportes/ingresos', params={'desde': (date.today() - timedelta(days=45)).isoformat(),
                       'hasta': date.today().isoformat()}, headers=h, timeout=15).json()
    paso('Reporte de ingresos', rep.get('total') is not None, f"total S/ {rep.get('total')} en {rep.get('cantidadPagos')} pagos")
    xlsx = requests.get(f'{API}/admin/reportes/reservas.xlsx', params={'desde': (date.today() - timedelta(days=30)).isoformat(),
                        'hasta': date.today().isoformat()}, headers=h, timeout=30)
    paso('Reporte de reservas en Excel', xlsx.status_code == 200 and xlsx.content[:2] == b'PK', f'{len(xlsx.content) // 1024} KB')
    cors_mal = requests.post(f'{API}/auth/login', json={}, headers={'Origin': 'https://sitio-ajeno.example'}, timeout=10)
    paso('CORS: origen no permitido → 403 ORIGEN_NO_PERMITIDO con cuerpo ErrorApi',
         cors_mal.status_code == 403 and cors_mal.headers.get('Content-Type', '').startswith('application/json')
         and cors_mal.json().get('codigo') == 'ORIGEN_NO_PERMITIDO', cors_mal.text[:90])
    if 'localhost' in BASE:
        panel = requests.options(f'{API}/admin/recordatorios/ejecutar', headers={'Origin': 'http://localhost:5187',
                                 'Access-Control-Request-Method': 'POST'}, timeout=10)
        paso('CORS local: el panel en 5187 está permitido', panel.status_code == 200
             and panel.headers.get('Access-Control-Allow-Origin') == 'http://localhost:5187')
    bit = requests.get(f'{API}/admin/bitacora', params={'size': 5}, headers=h, timeout=10).json()
    paso('Bitácora de auditoría registra las acciones', bit.get('totalElementos', 0) > 0,
         '; '.join(f"{b['actor']}:{b['accion']}" for b in bit.get('contenido', [])[:5]))

    # 12. Limpieza (producción): la reserva de prueba usa un celular ficticio; se cancela para que su recordatorio
    #     nunca llegue a un número real cuando se conecte el WhatsApp.
    if os.environ.get('E2E_LIMPIAR') == '1':
        canceladas = 0
        for cod in dict.fromkeys(RESERVAS_CREADAS):
            lim = requests.post(f'{API}/admin/reservas/{cod}/cancelar', headers=h,
                                json={'motivo': 'Reserva creada por la prueba automática de punta a punta', 'devolucion': False},
                                timeout=20)
            canceladas += lim.status_code == 200
        vigentes = [c for c in dict.fromkeys(RESERVAS_CREADAS)
                    if requests.get(f'{API}/admin/reservas/{c}', headers=h, timeout=15).json().get('estado') != 'CANCELADA']
        paso('Limpieza: todas las reservas de prueba quedan canceladas (sin recordatorios)', not vigentes,
             f'{canceladas} canceladas ahora de {len(set(RESERVAS_CREADAS))} creadas' + (f' · siguen vigentes: {vigentes}' if vigentes else ''))

    total, buenos = len(resultados), sum(1 for _, ok in resultados if ok)
    print(f'\nResultado: {buenos} de {total} comprobaciones correctas.')
    sys.exit(0 if buenos == total else 1)


if __name__ == '__main__':
    main()
