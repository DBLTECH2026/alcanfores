"""Ejecuta comandos REALES en el VPS por SSH, guarda la salida y la dibuja como una captura de terminal (PNG).

Uso:
  python sesion_ssh.py --usuario sirac --cwd /opt/alcanfores --titulo "Construcción de imágenes" \
      --salida "04 ... .png" --comando "docker compose ps" --comando "docker stats --no-stream"

Cada comando se ejecuta tal cual en el servidor (nada se inventa); la imagen muestra el prompt, el comando y su
salida. --max-lineas recorta salidas largas mostrando el inicio y el final. Nunca usar con comandos que impriman secretos.
"""
import argparse
import os
import subprocess
import textwrap
from datetime import datetime

from PIL import Image, ImageDraw, ImageFont

HOST = '2.25.216.93'
LLAVES = {'root': '~/.ssh/sirac_vps', 'sirac': '~/.ssh/sirac_actions'}
FUENTE = r'C:\Windows\Fonts\CascadiaMono.ttf'
FONDO, TEXTO, PROMPT_U, PROMPT_D, GRIS, BARRA = '#0C0C0C', '#CCCCCC', '#16C60C', '#3B78FF', '#767676', '#1F1F1F'
ANSI = __import__('re').compile(r'\x1b\[[0-9;]*[A-Za-z]')


def ejecutar(usuario, cwd, comando):
    llave = os.path.expanduser(LLAVES[usuario])
    # La salida de error se une a la normal EN EL SERVIDOR para conservar el orden real de los mensajes
    remoto = (f'cd {cwd} || exit 1; ' if cwd else '') + f'{{ {comando} ; }} 2>&1'
    r = subprocess.run(['ssh', '-i', llave, '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=20', f'{usuario}@{HOST}', remoto],
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace',
                       timeout=3600)
    return r.stdout.rstrip('\n'), r.returncode


def recortar(lineas, maximo):
    if maximo and len(lineas) > maximo:
        mitad = maximo // 2
        return lineas[:mitad] + [f'… ({len(lineas) - maximo} líneas omitidas) …'] + lineas[-mitad:]
    return lineas


def dibujar(titulo, bloques, salida, ancho_col=150):
    f = ImageFont.truetype(FUENTE, 17)
    alto_linea, margen, barra = 24, 22, 40
    filas = []  # (texto, color, es_prompt)
    for prompt, comando, texto in bloques:
        filas.append(((prompt, comando), None, True))
        for linea in texto.splitlines() or ['']:
            linea = ANSI.sub('', linea).replace('\t', '    ')
            for trozo in textwrap.wrap(linea, ancho_col, replace_whitespace=False, drop_whitespace=False) or ['']:
                filas.append((trozo, TEXTO, False))
        filas.append(('', TEXTO, False))
    ancho = margen * 2 + int(f.getlength('M') * ancho_col)
    alto = barra + margen + alto_linea * len(filas) + margen
    img = Image.new('RGB', (ancho, alto), FONDO)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, ancho, barra], fill=BARRA)
    for i, c in enumerate(['#FF5F57', '#FEBC2E', '#28C840']):
        d.ellipse([16 + i * 22, 13, 30 + i * 22, 27], fill=c)
    d.text((90, 10), titulo, font=ImageFont.truetype(FUENTE, 16), fill='#E5E5E5')
    y = barra + margen
    for contenido, color, es_prompt in filas:
        x = margen
        if es_prompt:
            prompt, comando = contenido
            usuario_host, ruta, signo = prompt
            d.text((x, y), usuario_host, font=f, fill=PROMPT_U); x += f.getlength(usuario_host)
            d.text((x, y), ':', font=f, fill=TEXTO); x += f.getlength(':')
            d.text((x, y), ruta, font=f, fill=PROMPT_D); x += f.getlength(ruta)
            d.text((x, y), signo + ' ', font=f, fill=TEXTO); x += f.getlength(signo + ' ')
            d.text((x, y), comando, font=f, fill='#FFFFFF')
        else:
            d.text((x, y), contenido, font=f, fill=GRIS if contenido.startswith('… (') else color)
        y += alto_linea
    img.save(salida)


def redibujar(a):
    """Vuelve a dibujar una captura a partir de la salida REAL guardada en el registro (no ejecuta nada)."""
    texto = open(a.registro, encoding='utf-8').read()
    secciones = [s for s in texto.split('===== ') if s.startswith(a.redibujar)]
    if not secciones:
        raise SystemExit('No encuentro ese bloque en el registro')
    titulo, _, cuerpo = secciones[-1].partition('\n')
    signo = '#' if a.usuario == 'root' else '$'
    import re
    patron = re.compile(rf'^{re.escape(signo)} (.*?)\n(.*?)\n?\[código de salida -?\d+\]$', re.S | re.M)
    bloques = [((f'{a.usuario}@srv2164565', a.ruta_visible or '~', signo), comando,
                '\n'.join(recortar(salida.splitlines(), a.max_lineas))) for comando, salida in patron.findall(cuerpo)]
    dibujar(titulo.strip(), bloques, a.salida)
    print('redibujada:', a.salida, len(bloques), 'comandos')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--usuario', default='sirac', choices=list(LLAVES))
    ap.add_argument('--cwd', default='')
    ap.add_argument('--titulo', required=True)
    ap.add_argument('--salida', required=True)
    ap.add_argument('--comando', action='append', required=True)
    ap.add_argument('--max-lineas', type=int, default=40)
    ap.add_argument('--registro', default='', help='archivo .txt donde guardar la salida completa')
    ap.add_argument('--redibujar', default='', help='redibuja desde el registro el bloque cuyo título empieza así')
    ap.add_argument('--ruta-visible', default='', help='con --redibujar: ruta que mostrar en el prompt')
    ap.add_argument('--filtro', default='', help='con --redibujar: solo las líneas que cumplan esta expresión regular (extracto)')
    ap.add_argument('--nota', default='', help='con --redibujar: comentario que se agrega al comando mostrado')
    a = ap.parse_args()
    # Git Bash convierte «/opt/x» en «C:/Program Files/Git/opt/x»: se deshace la conversión
    for prefijo in ('C:/Program Files/Git', 'C:\\Program Files\\Git'):
        if a.cwd.startswith(prefijo):
            a.cwd = a.cwd[len(prefijo):].replace('\\', '/')
    if a.redibujar:
        redibujar(a)
        return
    bloques, completo = [], []
    ruta = a.cwd or ('~' if a.usuario != 'root' else '/root')
    ruta_corta = ruta.replace('/root', '~') if a.usuario == 'root' else ruta.replace('/home/sirac', '~')
    signo = '#' if a.usuario == 'root' else '$'
    for c in a.comando:
        texto, codigo = ejecutar(a.usuario, a.cwd, c)
        completo.append(f'{signo} {c}\n{texto}\n[código de salida {codigo}]\n')
        bloques.append(((f'{a.usuario}@srv2164565', ruta_corta, signo), c, '\n'.join(recortar(texto.splitlines(), a.max_lineas))))
        print(f'{signo} {c}\n{texto}\n[exit {codigo}]')
    titulo = f'{a.titulo}  ·  {a.usuario}@srv2164565 (VPS DBLTECH)  ·  {datetime.now():%d/%m/%Y %H:%M}'
    dibujar(titulo, bloques, a.salida)
    if a.registro:
        with open(a.registro, 'a', encoding='utf-8') as fh:
            fh.write(f'===== {titulo}\n' + '\n'.join(completo) + '\n')


if __name__ == '__main__':
    main()
