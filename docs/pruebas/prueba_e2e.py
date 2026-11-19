"""Prueba de punta a punta de Alcanfores contra el API Gateway (local o producción).

Recorre el sistema como lo haría la gente: un cliente reserva y paga (Yape con comprobante y tarjeta por la pasarela
beta), la administradora valida, se emiten boleta/factura, salen las notificaciones, se programa el recordatorio,
se cancela con nota de crédito y se publica una reseña verificada. También verifica seguridad y validaciones.

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

import requests

sys.stdout.reconfigure(encoding='utf-8')
BASE = (sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8086').rstrip('/')
API = BASE + '/api'
CLAVE_ADMIN = os.environ.get('CLAVE_ADMIN', 'Alcanfores2026')
CLAVE_RECEPCION = os.environ.get('CLAVE_RECEPCION', 'Recepcion2026')
AQUI = os.path.dirname(os.path.abspath(__file__))
COMPROBANTE_IMG = (glob.glob(os.path.join(AQUI, '..', '..', 'backend', 'pagos-service', 'src', 'main', 'resources',
                                          'semilla', 'comprobantes', '*.png')) or [None])[0]
resultados = []


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
    rec = requests.get(f'{API}/admin/recordatorios', params={'size': 100}, headers=h, timeout=10).json()
    mio = [r for r in rec.get('contenido', []) if r.get('codigoReserva') == codigo]
    paso('Recordatorio programado 2 horas antes (cron)', bool(mio) and mio[0]['estado'] in ('PROGRAMADO', 'ENVIADO', 'OMITIDO'),
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
    nc = esperar(lambda: next((p['notaCredito'] for p in requests.get(f'{API}/pagos/reserva/{cod2}', params={'dni': c2['dni']}, timeout=10).json()
                               if p.get('notaCredito')), None), 30)
    paso('Evento reserva.cancelada → reembolso simulado y nota de crédito', bool(nc), nc and nc['serieNumero'])
    libre = requests.get(f'{API}/disponibilidad', params={'fecha': t2 and (date.today() + timedelta(days=3)).isoformat(),
                         'idCancha': canchas[2]['id']}, timeout=10).json()
    estado_t2 = next(t['estado'] for c in libre['canchas'] for t in c['turnos'] if t['idTurno'] == t2['idTurno'])
    paso('El turno cancelado vuelve a estar DISPONIBLE', estado_t2 == 'DISPONIBLE')

    # 10. Reseña verificada (reserva sembrada ya jugada)
    pasadas = requests.get(f'{API}/admin/reservas', params={'estado': 'CONFIRMADA', 'hasta': (date.today() - timedelta(days=1)).isoformat(),
                           'size': 50}, headers=h, timeout=15).json()['contenido']
    hecho = False
    for rv in pasadas:
        det_r = requests.get(f"{API}/admin/reservas/{rv['codigoReserva']}", headers=h, timeout=10).json()
        dni = det_r['cliente']['dni']
        id_cancha = det_r['cancha']['id'] if isinstance(det_r.get('cancha'), dict) else det_r.get('idCancha')
        res = requests.post(f'{API}/catalogo/canchas/{id_cancha}/resenas', json={'codigoReserva': rv['codigoReserva'], 'dni': dni,
                            'puntuacion': 5, 'comentario': 'Muy buena cancha, el grass está parejo y la iluminación es excelente.'}, timeout=15)
        if res.status_code == 201:
            dup = requests.post(f'{API}/catalogo/canchas/{id_cancha}/resenas', json={'codigoReserva': rv['codigoReserva'], 'dni': dni,
                                'puntuacion': 4, 'comentario': 'Intento repetir la reseña de la misma reserva.'}, timeout=15)
            paso('Reseña verificada publicada (código + DNI de una reserva jugada)', True, rv['codigoReserva'])
            paso('Reseña repetida: 409 RESENA_DUPLICADA', dup.status_code == 409)
            hecho = True
            break
    if not hecho:
        paso('Reseña verificada publicada', False, 'ninguna reserva pasada fue elegible')
    sin_reserva = requests.post(f"{API}/catalogo/canchas/{canchas[1]['id']}/resenas", json={'codigoReserva': codigo, 'dni': c1['dni'],
                                'puntuacion': 5, 'comentario': 'Todavía no juego pero ya quiero opinar.'}, timeout=15)
    paso('Reseña de una reserva aún no jugada: rechazada (422)', sin_reserva.status_code == 422, sin_reserva.json().get('mensaje'))

    # 11. Panel: dashboard, reportes y bitácora
    dash = requests.get(f'{API}/admin/dashboard', headers=h, timeout=15).json()
    paso('Dashboard del día', 'kpis' in dash, json.dumps(dash.get('kpis'), ensure_ascii=False)[:200])
    rep = requests.get(f'{API}/admin/reportes/ingresos', params={'desde': (date.today() - timedelta(days=45)).isoformat(),
                       'hasta': date.today().isoformat()}, headers=h, timeout=15).json()
    paso('Reporte de ingresos', rep.get('total') is not None, f"total S/ {rep.get('total')} en {rep.get('cantidadPagos')} pagos")
    xlsx = requests.get(f'{API}/admin/reportes/reservas.xlsx', params={'desde': (date.today() - timedelta(days=30)).isoformat(),
                        'hasta': date.today().isoformat()}, headers=h, timeout=30)
    paso('Reporte de reservas en Excel', xlsx.status_code == 200 and xlsx.content[:2] == b'PK', f'{len(xlsx.content) // 1024} KB')
    bit = requests.get(f'{API}/admin/bitacora', params={'size': 5}, headers=h, timeout=10).json()
    paso('Bitácora de auditoría registra las acciones', bit.get('totalElementos', 0) > 0,
         '; '.join(f"{b['actor']}:{b['accion']}" for b in bit.get('contenido', [])[:5]))

    total, buenos = len(resultados), sum(1 for _, ok in resultados if ok)
    print(f'\nResultado: {buenos} de {total} comprobaciones correctas.')
    sys.exit(0 if buenos == total else 1)


if __name__ == '__main__':
    main()
