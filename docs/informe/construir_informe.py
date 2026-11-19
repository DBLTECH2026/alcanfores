# -*- coding: utf-8 -*-
"""
Generador reproducible del informe final de «Desarrollo Web Integrado» (UTP) · Sistema de reservas Alcanfores.

Parte del documento vigente (v4, que NO se modifica), escribe debajo de los títulos vacíos de los capítulos 6 a 12,
agrega las referencias nuevas (APA 7, orden alfabético) y las entradas del índice de tablas y figuras.
Después se ejecuta actualizar_indices.ps1 (Word) para recalcular el índice general, las páginas y exportar el PDF.

Uso:
    python construir_informe.py                 # genera el .docx
    python construir_informe.py --diagramas     # además vuelve a exportar los diagramas SVG a PNG (Playwright + Chrome)

Todas las cifras están en DATOS (abajo): si cambian las pruebas, la cobertura o la medición del servidor,
se editan ahí y se vuelve a generar.
"""
from __future__ import annotations

import copy
import re
import sys
import unicodedata
from pathlib import Path

import docx
from docx.oxml.ns import qn
from docx.shared import Cm
from lxml import etree
from PIL import Image

# =====================================================================================================================
# RUTAS
# =====================================================================================================================
RAIZ_REPO = Path(__file__).resolve().parents[2]                       # C:\xampp\htdocs\alcanfores-app
DOCS = RAIZ_REPO / "docs"
RECURSOS = Path(__file__).resolve().parent / "recursos"
CONSTRUCCION = Path(__file__).resolve().parent / "construccion"       # imágenes recortadas (se regeneran)
CARPETA_CURSO = Path(r"C:\Users\Luis Lopez Lapa\Desktop\ALCANFORES\02 DESARROLLO WEB INTEGRADO (Spring Boot + React)")
BASE = CARPETA_CURSO / "03 Informe vigente" / "INFORME_DESARROLLO_WEB_v4.docx"
SALIDA = CARPETA_CURSO / "04 Entrega final" / "01 Informe final" / "INFORME_DESARROLLO_WEB_FINAL.docx"
CAP_PORTAL = DOCS / "capturas-reales" / "portal"
CAP_PANEL = DOCS / "capturas-reales" / "panel"
CAP_DESPLIEGUE = CARPETA_CURSO / "04 Entrega final" / "04 Capturas del sistema" / "Despliegue"
PROTOTIPO = Path(r"C:\xampp\htdocs\alcanfores-diseno\arquitectura\capturas")

# =====================================================================================================================
# DATOS (todas las cifras del informe)
# =====================================================================================================================
DATOS = {
    "fecha_portada": "Noviembre de 2026",
    "sitio": "https://alcanfores.dbltech.tech",
    "panel": "https://alcanfores.dbltech.tech/admin/login",
    "repositorio": "https://github.com/DBLTECH2026/alcanfores",
    # Pruebas JUnit 5 / Mockito por módulo (surefire) y cobertura JaCoCo (instrucciones, ramas, líneas) en %
    "pruebas": [
        # módulo, pruebas, fallos, instrucciones, ramas, líneas
        ("comun", 6, 0, 29.6, 29.0, 24.4),
        ("discovery-server", 3, 0, 27.3, None, 25.0),
        ("api-gateway", 41, 0, 89.1, 70.0, 92.1),
        ("auth-service", 70, 0, 95.7, 81.3, 95.2),
        ("catalogo-service", 94, 0, 93.8, 73.3, 92.2),
        ("reservas-service", 61, 0, 94.0, 77.2, 92.3),
        ("pagos-service", 105, 0, 92.9, 71.2, 92.9),
        ("notificaciones-service", 97, 0, 95.5, 71.9, 94.6),
    ],
    # Prueba de punta a punta (docs/pruebas/prueba_e2e.py)
    "e2e_local": {"correctas": 36, "total": 36, "fecha": "18/11/2026 22:14"},
    "e2e_produccion": {"correctas": 36, "total": 37, "fecha": "18/11/2026 22:31",
                       "fallo": "La foto de la cancha se descarga"},
    # Nueva corrida en producción tras la corrección de nginx (commit e18be07). Completar cuando se mida, p. ej.:
    # {"correctas": 37, "total": 37, "fecha": "19/11/2026 10:00"}
    "e2e_produccion_final": None,
    # Colección Postman ejecutada con Newman
    "postman": {"peticiones": 40, "aserciones": 60, "fallos": 0, "promedio_ms": 44,
                "carpetas": [("00 · Plataforma (gateway + Eureka)", 2), ("01 · auth-service", 6),
                             ("02 · catalogo-service", 5), ("03 · reservas-service", 12),
                             ("04 · pagos-service", 11), ("05 · notificaciones-service", 4)]},
    # GitHub Actions (duración de cada trabajo en la última ejecución en verde)
    "ci": {"backend": "3 min 50 s", "frontend": "25 s", "despliegue": "1 min 45 s"},
    # Servidor de producción (registro de terminal del 18/11/2026)
    "vps": {"plan": "Hostinger KVM 2", "vcpu": 2, "ram": "8 GB (7,7 GiB utilizables)", "so": "Ubuntu 26.04.1 LTS",
            "disco": "96 GB (15 GB en uso)", "docker": "29.1.3", "compose": "2.40.3"},
    # docker stats --no-stream (MiB usados / límite) · 18/11/2026 22:29
    "memoria": [
        ("mysql", "mysql:8.4", 491.7, 640), ("rabbitmq", "rabbitmq:4-management-alpine", 89.94, 384),
        ("discovery-server", "alcanfores/discovery-server", 175.1, 320),
        ("api-gateway", "alcanfores/api-gateway", 178.2, 384),
        ("auth-service", "alcanfores/auth-service", 248.4, 384),
        ("catalogo-service", "alcanfores/catalogo-service", 285.9, 384),
        ("reservas-service", "alcanfores/reservas-service", 317.3, 448),
        ("pagos-service", "alcanfores/pagos-service", 307.6, 448),
        ("notificaciones-service", "alcanfores/notificaciones-service", 325.3, 384),
        ("alcanfores-web", "alcanfores/web (nginx)", 6.801, 64),
    ],
    "rabbit_dev_mib": 98.72,          # contenedor auxiliar alcanfores-rabbit-dev (no forma parte del compose)
    "tablas_por_bd": [("alcanfores_auth", 4), ("alcanfores_catalogo", 6), ("alcanfores_reservas", 8),
                      ("alcanfores_pagos", 9), ("alcanfores_notificaciones", 4)],
    "colas": 13,
    "latencias_estado": "7 a 54 ms",   # GET /api/estado en producción, 18/11/2026 23:20
}

# =====================================================================================================================
# MOTOR DE ESCRITURA (formato APA del documento base)
# =====================================================================================================================
W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
NS = {"w": W}
LANG = "es-PE"
MAX_COLUMNAS_CODIGO = 87     # medido en el PDF: caben 88 caracteres de Consolas 9 pt
ANCHO_TEXTO_TW = 9025          # ancho útil de la página en twips (A4 con márgenes de 2,54 cm)
MARCA = re.compile(r"(\^\^.+?\^\^|~.+?~)")      # ^^negrita^^  ~cursiva~
REF = re.compile(r"\{([TF]):([a-z0-9_\-]+)\}")


def w(tag: str) -> str:
    return "{%s}%s" % (W, tag)


def el(tag: str, **attrs) -> etree._Element:
    e = etree.Element(w(tag))
    for k, v in attrs.items():
        e.set(w(k), str(v))
    return e


def sub(padre, tag: str, **attrs):
    e = el(tag, **attrs)
    padre.append(e)
    return e


def texto_plano(p) -> str:
    return "".join(t.text or "" for t in p.iter(w("t")))


class Informe:
    def __init__(self, base: Path):
        self.doc = docx.Document(str(base))
        self.body = self.doc.element.body
        self.cursor = None
        ids = [int(b.get(w("id"))) for b in self.body.iter(w("bookmarkStart"))]
        self.sig_id = max(ids) + 100
        self.n_tabla, self.n_figura = 16, 4
        self.etiquetas: dict[str, int] = {}          # "T:clave" -> número
        self.nuevas_tablas: list[tuple[int, str]] = []
        self.nuevas_figuras: list[tuple[int, str]] = []
        self.orden = 0                                # orden de aparición (para comprobar citas)
        self.primera_cita: dict[str, int] = {}
        self.aparicion: dict[str, int] = {}
        self.num_listas = 0
        CONSTRUCCION.mkdir(parents=True, exist_ok=True)

    # ------------------------------------------------------------------ navegación
    def parrafos(self):
        return [e for e in self.body.iterchildren() if e.tag == w("p")]

    def buscar(self, texto: str):
        for e in self.body.iterchildren():
            if e.tag == w("p") and texto_plano(e).strip() == texto:
                return e
        raise KeyError(f"No se encontró el párrafo «{texto}»")

    def es_titulo(self, e) -> bool:
        if e.tag != w("p"):
            return False
        ps = e.find("w:pPr/w:pStyle", NS)
        return ps is not None and ps.get(w("val")) in ("Ttulo1", "Ttulo2", "Ttulo3")

    def en(self, titulo: str):
        """Coloca el cursor después del título (y de los párrafos que ya tuviera); borra el texto provisional."""
        h = self.buscar(titulo)
        self.cursor = h
        e = h.getnext()
        while e is not None and not self.es_titulo(e) and e.tag != w("sectPr"):
            sig = e.getnext()
            if e.tag == w("p") and texto_plano(e).startswith("Sección en desarrollo"):
                self.body.remove(e)
            elif e.tag == w("p") and texto_plano(e).strip() == "":
                pass
            else:
                self.cursor = e
            e = sig
        return self

    def _poner(self, e):
        self.cursor.addnext(e)
        self.cursor = e
        self.orden += 1
        for t in e.iter(w("t")):
            for m in REF.finditer(t.text or ""):
                clave = f"{m.group(1)}:{m.group(2)}"
                self.primera_cita.setdefault(clave, self.orden)
        return e

    # ------------------------------------------------------------------ runs
    def _rpr(self, r, negrita=False, cursiva=False, tam=None, fuente=None, color=None, sin_ortografia=False):
        rpr = sub(r, "rPr")
        if fuente:
            sub(rpr, "rFonts", ascii=fuente, hAnsi=fuente, cs=fuente, eastAsia=fuente)
        if negrita:
            sub(rpr, "b"); sub(rpr, "bCs")
        if cursiva:
            sub(rpr, "i"); sub(rpr, "iCs")
        if sin_ortografia:
            sub(rpr, "noProof")
        if color:
            sub(rpr, "color", val=color)
        if tam:
            sub(rpr, "sz", val=tam); sub(rpr, "szCs", val=tam)
        sub(rpr, "lang", val=LANG, eastAsia="es-ES", bidi="ar-SA")
        return rpr

    def runs(self, p, texto: str, negrita=False, cursiva=False, tam=None, color=None):
        """Agrega el texto con marcas ^^negrita^^ y ~cursiva~ al párrafo."""
        for trozo in MARCA.split(texto):
            if not trozo:
                continue
            b, i, t = negrita, cursiva, trozo
            if trozo.startswith("^^") and trozo.endswith("^^"):
                b, t = True, trozo[2:-2]
            elif trozo.startswith("~") and trozo.endswith("~") and len(trozo) > 1:
                i, t = True, trozo[1:-1]
            r = sub(p, "r")
            self._rpr(r, negrita=b, cursiva=i, tam=tam, color=color)
            te = sub(r, "t")
            te.text = t
            te.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
        return p

    def _p(self, ppr_hijos=()):
        p = el("p")
        ppr = sub(p, "pPr")
        for h in ppr_hijos:
            ppr.append(h)
        return p, ppr

    # ------------------------------------------------------------------ párrafos
    def p(self, texto: str, sangria=True, keep_next=False):
        p, ppr = self._p()
        if keep_next:
            sub(ppr, "keepNext")
        if sangria:
            sub(ppr, "ind", firstLine="720")
        self.runs(p, texto)
        return self._poner(p)

    def sub4(self, titulo: str, texto: str = ""):
        """Encabezado APA de nivel 4 en línea: sangrado, negrita y terminado en punto, seguido del texto."""
        p, ppr = self._p()
        sub(ppr, "keepNext")
        sub(ppr, "ind", firstLine="720")
        self.runs(p, f"^^{titulo}.^^ " + texto)
        return self._poner(p)

    def vinetas(self, items):
        for it in items:
            p, ppr = self._p()
            num = sub(ppr, "numPr")
            sub(num, "ilvl", val=0)
            sub(num, "numId", val=1)
            self.runs(p, it)
            self._poner(p)

    def numerada(self, items):
        num_id = self._nueva_lista()
        for it in items:
            p, ppr = self._p()
            num = sub(ppr, "numPr")
            sub(num, "ilvl", val=0)
            sub(num, "numId", val=num_id)
            self.runs(p, it)
            self._poner(p)

    def _nueva_lista(self) -> int:
        numbering = self.doc.part.numbering_part.element
        ids = [int(n.get(w("numId"))) for n in numbering.findall("w:num", NS)]
        nuevo = max(ids) + 1
        n = el("num", numId=nuevo)
        sub(n, "abstractNumId", val=0)
        lo = sub(n, "lvlOverride", ilvl=0)
        sub(lo, "startOverride", val=1)
        numbering.append(n)
        return nuevo

    # ------------------------------------------------------------------ rótulos APA
    def _marcador(self, p, nombre: str):
        i = self.sig_id
        self.sig_id += 1
        ppr = p.find("w:pPr", NS)
        bs = el("bookmarkStart", id=i, name=nombre)
        be = el("bookmarkEnd", id=i)
        ppr.addnext(bs)
        bs.addnext(be)

    def _rotulo(self, tipo: str, n: int, titulo: str):
        p, ppr = self._p()
        sub(ppr, "keepNext")
        sub(ppr, "spacing", before=240)
        self.runs(p, f"{tipo} {n}", negrita=True)
        self._marcador(p, f"_Idx{tipo}{n}")
        self._poner(p)
        p, ppr = self._p()
        sub(ppr, "keepNext")
        sub(ppr, "spacing", after=120)
        self.runs(p, titulo, cursiva=True)
        self._poner(p)

    def _nota(self, texto: str, tam: int, before: int, line=None):
        p, ppr = self._p()
        sp = sub(ppr, "spacing", before=before, after=240)
        if line:
            sp.set(w("line"), str(line)); sp.set(w("lineRule"), "auto")
        r = sub(p, "r")
        self._rpr(r, cursiva=True, tam=tam)
        t = sub(r, "t"); t.text = "Nota. "
        t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
        self.runs(p, texto, tam=tam)
        self._poner(p)

    def _registrar(self, clave: str, tipo: str, n: int, titulo: str):
        if clave in self.etiquetas:
            raise ValueError(f"Etiqueta repetida: {clave}")
        self.etiquetas[clave] = n
        self.aparicion[clave] = self.orden + 1
        (self.nuevas_tablas if tipo == "T" else self.nuevas_figuras).append((n, titulo))

    # ------------------------------------------------------------------ tablas
    def tabla(self, clave: str, titulo: str, encabezados, filas, anchos, nota: str, tam=20):
        """filas: lista de listas de textos; una fila ("§", texto) es un subtítulo sombreado que ocupa todo el ancho."""
        self.n_tabla += 1
        n = self.n_tabla
        self._registrar("T:" + clave, "T", n, titulo)
        self._rotulo("Tabla", n, titulo)
        total = sum(anchos)
        tw = [int(ANCHO_TEXTO_TW * a / total) for a in anchos]
        tbl = el("tbl")
        tblpr = sub(tbl, "tblPr")
        sub(tblpr, "tblStyle", val="a5")
        sub(tblpr, "tblW", w=sum(tw), type="dxa")
        sub(tblpr, "tblInd", w=0, type="dxa")
        bd = sub(tblpr, "tblBorders")
        for lado in ("top", "left", "bottom", "right", "insideH", "insideV"):
            sub(bd, lado, val="nil")
        sub(tblpr, "tblLayout", type="fixed")
        sub(tblpr, "tblLook", val="0600", firstRow=0, lastRow=0, firstColumn=0, lastColumn=0, noHBand=1, noVBand=1)
        grid = sub(tbl, "tblGrid")
        for x in tw:
            sub(grid, "gridCol", w=x)
        todas = [("H", encabezados)] + [("§", f[1]) if isinstance(f, tuple) and f[0] == "§" else ("F", f) for f in filas]
        for idx, (clase, contenido) in enumerate(todas):
            ultima = idx == len(todas) - 1
            tr = sub(tbl, "tr")
            trpr = sub(tr, "trPr")
            sub(trpr, "cantSplit")
            if clase == "H":
                sub(trpr, "tblHeader")
            celdas = [contenido] if clase == "§" else contenido
            for j, txt in enumerate(celdas):
                tc = sub(tr, "tc")
                tcpr = sub(tc, "tcPr")
                sub(tcpr, "tcW", w=(sum(tw) if clase == "§" else tw[j]), type="dxa")
                if clase == "§":
                    sub(tcpr, "gridSpan", val=len(tw))
                b = sub(tcpr, "tcBorders")
                arriba = "single" if clase == "H" else "nil"
                abajo = "single" if (clase == "H" or ultima) else "nil"
                for lado, val in (("top", arriba), ("left", "nil"), ("bottom", abajo), ("right", "nil")):
                    if val == "single":
                        sub(b, lado, val="single", sz=5, space=0, color="000000")
                    else:
                        sub(b, lado, val="nil")
                if clase == "§":
                    sub(tcpr, "shd", val="clear", color="auto", fill="F2F2F2")
                mar = sub(tcpr, "tcMar")
                for lado, v in (("top", 60), ("left", 100), ("bottom", 60), ("right", 100)):
                    sub(mar, lado, w=v, type="dxa")
                for linea in str(txt).split("\n"):
                    p, ppr = self._p()
                    if clase == "H" or len(todas) <= 12:
                        sub(ppr, "keepNext")
                    sub(ppr, "spacing", after=40, line=252, lineRule="auto")
                    self.runs(p, linea, negrita=(clase in ("H", "§")), tam=tam)
                    tc.append(p)
        self._poner(tbl)
        self._nota(nota, 22, 60)
        return n

    # ------------------------------------------------------------------ imágenes
    def preparar(self, origen: Path, recorte=None, nombre=None, max_px=1500) -> Path:
        im = Image.open(origen)
        if im.mode in ("RGBA", "P", "LA"):
            fondo = Image.new("RGB", im.size, "white")
            fondo.paste(im.convert("RGBA"), mask=im.convert("RGBA").split()[-1])
            im = fondo
        else:
            im = im.convert("RGB")
        if recorte:
            x0, y0, x1, y1 = recorte
            im = im.crop((x0, y0, min(x1, im.width), min(y1, im.height)))
        if im.width > max_px:
            im = im.resize((max_px, int(im.height * max_px / im.width)), Image.LANCZOS)
        destino = CONSTRUCCION / ((nombre or origen.stem) + ".jpg")
        im.save(destino, "JPEG", quality=88, optimize=True)
        return destino

    def _parrafo_imagen(self, ruta: Path, ancho_cm: float, alto_max_cm: float):
        with Image.open(ruta) as im:
            ancho_px, alto_px = im.size
        ancho = ancho_cm
        if ancho * alto_px / ancho_px > alto_max_cm:
            ancho = alto_max_cm * ancho_px / alto_px
        par = self.doc.add_paragraph()          # se crea al final y luego se mueve
        run = par.add_run()
        run.add_picture(str(ruta), width=Cm(ancho))
        p = par._p
        p.getparent().remove(p)
        ppr = p.get_or_add_pPr()
        sub(ppr, "keepNext")
        sub(ppr, "spacing", line=240, lineRule="auto")
        sub(ppr, "jc", val="center")
        # orden correcto de pPr: keepNext, spacing, jc (ya están en ese orden)
        for d in p.iter(qn("wp:docPr")):
            d.set("descr", ruta.stem.replace("-", " "))
        return p

    def figura(self, clave: str, titulo: str, imagen: Path, nota: str, ancho_cm=15.5, alto_max_cm=17.5):
        self.n_figura += 1
        n = self.n_figura
        self._registrar("F:" + clave, "F", n, titulo)
        self._rotulo("Figura", n, titulo)
        self._poner(self._parrafo_imagen(imagen, min(ancho_cm, 15.5), alto_max_cm))
        self._nota(nota, 20, 80, line=276)
        return n

    def figura_par(self, clave: str, titulo: str, imagenes, nota: str, ancho_cm=7.4, alto_max_cm=15.5, rotulos=None):
        """Dos imágenes lado a lado en una tabla sin bordes."""
        self.n_figura += 1
        n = self.n_figura
        self._registrar("F:" + clave, "F", n, titulo)
        self._rotulo("Figura", n, titulo)
        tbl = el("tbl")
        tblpr = sub(tbl, "tblPr")
        sub(tblpr, "tblW", w=ANCHO_TEXTO_TW, type="dxa")
        sub(tblpr, "jc", val="center")
        bd = sub(tblpr, "tblBorders")
        for lado in ("top", "left", "bottom", "right", "insideH", "insideV"):
            sub(bd, lado, val="nil")
        sub(tblpr, "tblLayout", type="fixed")
        grid = sub(tbl, "tblGrid")
        mitad = ANCHO_TEXTO_TW // 2
        for _ in imagenes:
            sub(grid, "gridCol", w=mitad)
        tr = sub(tbl, "tr")
        sub(sub(tr, "trPr"), "cantSplit")
        for img in imagenes:
            tc = sub(tr, "tc")
            sub(sub(tc, "tcPr"), "tcW", w=mitad, type="dxa")
            tc.append(self._parrafo_imagen(img, ancho_cm, alto_max_cm))
        if rotulos:
            tr2 = sub(tbl, "tr")
            for r in rotulos:
                tc = sub(tr2, "tc")
                sub(sub(tc, "tcPr"), "tcW", w=mitad, type="dxa")
                p, ppr = self._p()
                sub(ppr, "spacing", before=40, after=0, line=240, lineRule="auto")
                sub(ppr, "jc", val="center")
                self.runs(p, r, cursiva=True, tam=20)
                tc.append(p)
        self._poner(tbl)
        self._nota(nota, 20, 80, line=276)
        return n

    # ------------------------------------------------------------------ código fuente
    def codigo(self, clave: str, titulo: str, codigo: str, nota: str):
        lineas = codigo.strip("\n").split("\n")
        if not 3 <= len(lineas) <= 20:
            raise ValueError(f"El bloque {clave} tiene {len(lineas)} líneas")
        largas = [l for l in lineas if len(l) > MAX_COLUMNAS_CODIGO]
        if largas:
            raise ValueError(f"El bloque {clave} tiene líneas de más de {MAX_COLUMNAS_CODIGO} caracteres: {largas}")
        self.n_figura += 1
        n = self.n_figura
        self._registrar("F:" + clave, "F", n, titulo)
        self._rotulo("Figura", n, titulo)
        for k, linea in enumerate(lineas):
            p, ppr = self._p()
            if k < len(lineas) - 1:
                sub(ppr, "keepNext")
            sub(ppr, "keepLines")
            bdr = sub(ppr, "pBdr")
            for lado in (("top",) if k == 0 else ()) + (("bottom",) if k == len(lineas) - 1 else ()) + ("left", "right"):
                sub(bdr, lado, val="single", sz=4, space=4, color="D0D7D3")
            sub(ppr, "shd", val="clear", color="auto", fill="F2F2F2")
            sub(ppr, "spacing", before=0, after=0, line=240, lineRule="auto")
            sub(ppr, "ind", left=120, right=120)
            r = sub(p, "r")
            self._rpr(r, tam=18, fuente="Consolas", sin_ortografia=True)
            t = sub(r, "t")
            t.text = linea if linea else " "
            t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
            self._poner(p)
        self._nota(nota, 20, 80, line=276)
        return n

    # ------------------------------------------------------------------ cierre
    def resolver_referencias(self):
        faltan = set()
        for t in self.body.iter(w("t")):
            if t.text and "{" in t.text:
                def cambio(m):
                    clave = f"{m.group(1)}:{m.group(2)}"
                    if clave not in self.etiquetas:
                        faltan.add(clave)
                        return m.group(0)
                    return str(self.etiquetas[clave])
                t.text = REF.sub(cambio, t.text)
        if faltan:
            raise KeyError(f"Citas a tablas/figuras inexistentes: {sorted(faltan)}")
        avisos = []
        for clave, pos in self.aparicion.items():
            cita = self.primera_cita.get(clave)
            if cita is None:
                avisos.append(f"{clave} no se cita en el texto")
            elif cita > pos:
                avisos.append(f"{clave} se cita después de aparecer")
        return avisos

    def indice(self):
        """Agrega las tablas y figuras nuevas al «Índice de Tablas y Figuras» (hipervínculo + PAGEREF)."""
        def entrada(tipo, n, titulo):
            p, ppr = self._p()
            tabs = sub(ppr, "tabs")
            sub(tabs, "tab", val="right", leader="dot", pos=9026)
            sub(ppr, "spacing", after=60, line=300, lineRule="auto")
            sub(ppr, "ind", left=1080, hanging=1080)
            h = sub(p, "hyperlink", anchor=f"_Idx{tipo}{n}", history=1)
            for texto, b, i in ((f"{tipo} {n}. ", True, False), (titulo, False, True)):
                r = sub(h, "r"); self._rpr(r, negrita=b, cursiva=i)
                t = sub(r, "t"); t.text = texto
                t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
            r = sub(h, "r"); sub(r, "tab")
            r = sub(h, "r"); sub(r, "fldChar", fldCharType="begin")
            r = sub(h, "r"); t = sub(r, "instrText"); t.text = f" PAGEREF _Idx{tipo}{n} \\h "
            t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
            r = sub(h, "r"); sub(r, "fldChar", fldCharType="separate")
            r = sub(h, "r"); sub(sub(r, "rPr"), "noProof"); t = sub(r, "t"); t.text = "0"
            r = sub(h, "r"); sub(r, "fldChar", fldCharType="end")
            return p
        anclas = [e for e in self.body.iterchildren() if e.tag == w("p") and texto_plano(e).startswith("Tabla 16. ")]
        a = anclas[0]
        for n, titulo in self.nuevas_tablas:
            nuevo = entrada("Tabla", n, titulo); a.addnext(nuevo); a = nuevo
        anclas = [e for e in self.body.iterchildren() if e.tag == w("p") and texto_plano(e).startswith("Figura 4. ")]
        a = anclas[0]
        for n, titulo in self.nuevas_figuras:
            nuevo = entrada("Figura", n, titulo); a.addnext(nuevo); a = nuevo

    def reemplazar_texto(self, viejo: str, nuevo: str):
        for p in self.parrafos():
            if viejo in texto_plano(p):
                ts = list(p.iter(w("t")))
                completo = "".join(t.text or "" for t in ts)
                ts[0].text = completo.replace(viejo, nuevo)
                for t in ts[1:]:
                    t.text = ""
                return
        raise KeyError(viejo)

    def guardar(self, destino: Path):
        destino.parent.mkdir(parents=True, exist_ok=True)
        self.doc.save(str(destino))


# Atajos para imágenes ------------------------------------------------------------------------------------------------
def portal(nombre, alto=1000, ancho=1440):
    return (CAP_PORTAL / f"{nombre}.png", (0, 0, ancho, alto))


def panel(nombre, alto=1000, ancho=1440):
    return (CAP_PANEL / f"{nombre}.png", (0, 0, ancho, alto))


def movil(carpeta, nombre, alto=1688):
    base = CAP_PORTAL if carpeta == "portal" else CAP_PANEL
    return (base / f"{nombre}.png", (0, 0, 780, alto))


# =====================================================================================================================
# CONTENIDO
# =====================================================================================================================
def seccion_6_5(i: Informe):
    i.en("6.5. Presentación del Prototipo")
    i.p("Antes de programar, el equipo construyó un prototipo navegable en HTML, CSS y JavaScript que reproducía las "
        "pantallas del cliente y del administrador con datos de ejemplo. El prototipo fue revisado y aprobado en el "
        "segundo avance del curso y se convirtió en la referencia visual y funcional del sistema: definió la "
        "paleta de colores (azul noche #091A2A, verde Alcanfores #27811F y sus variantes), las tipografías Barlow "
        "Semi Condensed para los títulos e Inter para el texto, las ilustraciones propias del complejo y el "
        "flujo de reserva en tres pasos derivado de las historias de usuario de la Tabla 1. Este enfoque responde "
        "a la recomendación de Sommerville (2016) de validar los requisitos con prototipos antes de invertir en la "
        "implementación, porque permite que los usuarios detecten omisiones cuando todavía es barato corregirlas.")
    i.p("En la etapa de implementación, cada pantalla del prototipo se transformó en componentes de React con "
        "TypeScript que consumen la API real; los datos ficticios se reemplazaron por las respuestas de los "
        "microservicios y se añadieron los estados que el prototipo no cubría, como los errores de validación del "
        "servidor, el conflicto por doble reserva, los pagos en línea y la vista de recepción. La Tabla {T:prototipo} "
        "resume esta correspondencia, y las Figuras {F:proto-paso1} y {F:proto-hoy} comparan dos pantallas "
        "representativas: el primer paso de la reserva en el celular y el panel «Hoy» del administrador.")
    i.tabla("prototipo", "Correspondencia entre las Pantallas del Prototipo y las del Sistema Implementado",
            ["Pantalla del prototipo", "Ruta en el sistema", "Cambios incorporados al implementarla"],
            [["Inicio", "/", "Canchas, fotos y calificaciones leídas del catálogo; mapa y preguntas frecuentes."],
             ["Paso 1: horario", "/reservar", "Calendario de 14 días con los turnos reales; aviso cuando otra persona toma el turno (409)."],
             ["Paso 2: datos", "/reservar", "Consulta de DNI (RENIEC) y RUC (SUNAT); elección de boleta o factura; consentimientos de la Ley N.° 29733."],
             ["Paso 3: comprobante", "/reservar", "Yape o Plin con imagen y número de operación; pago con tarjeta en la pasarela Alcanfores Pay (beta)."],
             ["Reserva registrada", "/reservar/listo/:codigo", "Constancia PDF con código QR y copia por correo y WhatsApp."],
             ["Mi reserva", "/mi-reserva", "Línea de tiempo, pagos, comprobantes PDF y XML, reenvío del comprobante, cancelación y reseña."],
             ["Panel «Hoy»", "/admin/hoy", "Indicadores calculados por reservas-service; alertas y agenda del día con la hora actual."],
             ["Validar comprobante", "/admin/reservas/:codigo", "Visor con zoom, emisión automática de boleta o factura simulada y auditoría."],
             ["Agenda y turnos", "/admin/agenda", "Bloqueo de turnos y rangos, y generación idempotente de turnos."],
             ["Canchas y reportes", "/admin/canchas, /admin/reportes", "Carga de fotos, reseñas moderadas, gráficos y exportación a Excel."]],
            [2.7, 4, 6.3], "Las rutas corresponden a React Router (apartado 6.10.3). Elaboración propia.")
    a = i.preparar(PROTOTIPO / "cli-10-paso1-horario.png", (0, 0, 360, 760), "proto-paso1-antes")
    b = i.preparar(*movil("portal", "10-paso1-horario-390", 1650), nombre="proto-paso1-ahora")
    i.figura_par("proto-paso1", "Paso 1 de la Reserva en el Celular: Prototipo Aprobado y Sistema Implementado", [a, b],
                 "A la izquierda, el prototipo HTML aprobado en el segundo avance (360 px de ancho); a la derecha, la "
                 "misma pantalla del sistema en funcionamiento (390 px), con los turnos reales de la base de datos. "
                 "Elaboración propia.", ancho_cm=6.2, alto_max_cm=13.2, rotulos=["Prototipo", "Sistema implementado"])
    a = i.preparar(PROTOTIPO / "adm-02-hoy.png", None, "proto-hoy-antes")
    b = i.preparar(*panel("01-hoy-1440", 900), nombre="proto-hoy-ahora")
    i.figura_par("proto-hoy", "Panel «Hoy» del Administrador: Prototipo Aprobado y Sistema Implementado", [a, b],
                 "Se conservó el esqueleto del prototipo (barra lateral, indicadores con ícono, columna «Por validar» y "
                 "agenda del día), pero los valores provienen del endpoint /api/admin/dashboard. Elaboración propia.",
                 ancho_cm=7.7, rotulos=["Prototipo", "Sistema implementado"])
    i.p("La comparación muestra que el sistema respetó la identidad visual aprobada, de modo que el esfuerzo de la "
        "implementación se concentró en las reglas de negocio, la seguridad y la integración entre servicios. "
        "Las diferencias visibles, como los estados de error o los indicadores calculados, son consecuencia directa "
        "de conectar la interfaz con datos reales.")


def seccion_6_6(i: Informe):
    i.en("6.6. Componentes de la Aplicación")
    i.p("La propuesta inicial planteó una arquitectura desacoplada con una sola API en Spring Boot (Figura 1). Durante "
        "el diseño detallado, el equipo dividió esa API en microservicios, es decir, en aplicaciones pequeñas y "
        "autónomas, cada una responsable de una capacidad del negocio, que se despliegan por separado y se "
        "comunican mediante mecanismos ligeros como HTTP y mensajería (Lewis y Fowler, 2014; Newman, 2021). "
        "La descomposición siguió los contextos delimitados del dominio (Evans, 2003): acceso y auditoría, catálogo "
        "de canchas, reservas, pagos y notificaciones. Abgaz et al. (2023) señalan que este tipo de descomposición "
        "favorece la escalabilidad y la resiliencia, aunque exige resolver la comunicación entre servicios y la "
        "consistencia de los datos; ambos aspectos se abordan en este apartado. La separación por capas de Fowler "
        "(2002) se mantiene dentro de cada microservicio.")
    i.p("La Figura {F:arquitectura} presenta la arquitectura desplegada. El navegador accede únicamente por HTTPS a "
        "Caddy, que delega en un contenedor nginx con la SPA compilada; nginx reenvía las rutas /api/ al API Gateway, "
        "única puerta de entrada a los servicios. El gateway descubre las instancias registradas en Eureka y "
        "enruta cada prefijo al microservicio correspondiente (Spring, s. f.-c). Cada servicio tiene su propia base "
        "de datos en MySQL 8.4 y publica sus eventos de dominio en RabbitMQ.")
    i.figura("arquitectura", "Arquitectura de Microservicios del Sistema de Reservas",
             RECURSOS / "diagrama-arquitectura.png",
             "Las flechas continuas representan llamadas HTTP síncronas y las discontinuas, eventos asíncronos de "
             "RabbitMQ. Los servicios externos se usan solo desde el microservicio indicado en cada caja. Elaboración "
             "propia a partir de docs/SPEC.md del repositorio.", ancho_cm=15.5)
    i.p("La Tabla {T:microservicios} detalla los módulos del back-end. Además de los cinco servicios de dominio, el "
        "sistema incluye el servidor de descubrimiento, el gateway y una librería compartida (comun) con el formato "
        "estándar de errores, el filtro JWT, los validadores de DNI, RUC y celular, los registros de los eventos y "
        "las utilidades de tiempo y dinero; así, las reglas transversales se escriben una sola vez.")
    i.tabla("microservicios", "Módulos del Back-end y sus Responsabilidades",
            ["Módulo", "Puerto", "Base de datos", "Responsabilidad"],
            [["discovery-server", "8761", "—", "Registro y descubrimiento de servicios (Eureka)."],
             ["api-gateway", "8086", "—", "Rutas, CORS, validación previa del JWT, límite de solicitudes por IP, circuit breaker y GET /api/estado."],
             ["auth-service", "8091", "alcanfores_auth (4 tablas)", "Usuarios del panel, inicio de sesión, roles ADMIN y RECEPCION, bloqueo por intentos y bitácora central."],
             ["catalogo-service", "8092", "alcanfores_catalogo (6 tablas)", "Canchas, fotos, reseñas verificadas y configuración del complejo (tarifas, horarios y reglas)."],
             ["reservas-service", "8093", "alcanfores_reservas (8 tablas)", "Clientes, turnos, disponibilidad, reservas, cancelaciones, asistencia, consulta RENIEC/SUNAT, constancia PDF y reportes."],
             ["pagos-service", "8094", "alcanfores_pagos (9 tablas)", "Comprobantes Yape/Plin, caja, pasarela beta, Mercado Pago, boletas, facturas y notas de crédito simuladas."],
             ["notificaciones-service", "8095", "alcanfores_notificaciones (4 tablas)", "Correo y WhatsApp con PDF adjunto, recordatorio dos horas antes y bandeja de envíos."],
             ["comun", "—", "—", "Librería compartida: errores, seguridad, validadores, eventos, auditoría y utilidades."]],
            [3.2, 1.3, 3.6, 7], "El número de tablas por base de datos se tomó del servidor de producción e incluye la "
            "tabla de historial de Flyway. Elaboración propia.")
    i.p("La comunicación entre servicios combina dos estilos. Las consultas que necesitan una respuesta inmediata, como "
        "verificar que una reserva existe antes de aceptar su comprobante, usan HTTP síncrono hacia rutas /interno "
        "protegidas con una cabecera secreta, con un tiempo máximo de 3 s y un circuit breaker de Resilience4j que "
        "evita que la caída de un servicio arrastre a los demás (Nygard, 2018; Resilience4j, s. f.). Los cambios de "
        "estado, en cambio, se comunican como eventos de dominio por RabbitMQ: el servicio que los origina publica en "
        "el exchange de tipo topic alcanfores.eventos y cada interesado los recibe en su propia cola durable "
        "(RabbitMQ, s. f.). La Tabla {T:eventos} lista los eventos implementados y la Figura {F:colas} muestra las "
        "colas creadas en producción.")
    i.tabla("eventos", "Eventos de Dominio Publicados en RabbitMQ",
            ["Evento (routing key)", "Lo publica", "Lo consumen", "Efecto principal"],
            [["reserva.registrada", "reservas", "notificaciones", "Aviso «completa tu pago» con la constancia PDF."],
             ["reserva.confirmada", "reservas", "notificaciones", "Confirmación con constancia y programación del recordatorio."],
             ["reserva.cancelada", "reservas", "pagos, notificaciones", "Anulación de pagos pendientes, reembolso simulado con nota de crédito y aviso al cliente."],
             ["reserva.asistencia", "reservas", "notificaciones", "Invitación a calificar la cancha cuando el cliente asistió."],
             ["pago.registrado", "pagos", "reservas, notificaciones", "La reserva pasa a EN_REVISION y se le quita el plazo de pago."],
             ["pago.validado", "pagos", "reservas", "Suma el monto pagado y confirma la reserva."],
             ["pago.rechazado", "pagos", "reservas, notificaciones", "Estado OBSERVADO, nuevo plazo y enlace para reenviar el comprobante."],
             ["comprobante.emitido", "pagos", "notificaciones", "Envío de la boleta, factura o nota de crédito en PDF."],
             ["cancha.actualizada", "catalogo", "reservas", "Actualiza precios de turnos futuros y bloquea los turnos de una cancha inactiva."],
             ["auditoria.accion", "todos", "auth", "Registro en la bitácora central."]],
            [3.4, 2.2, 3.2, 6.2], "Todos los eventos llevan un identificador único (UUID) y la fecha y hora en que "
            "ocurrieron. Elaboración propia a partir de docs/SPEC.md.")
    i.figura("colas", "Exchanges y Colas de RabbitMQ en el Servidor de Producción",
             i.preparar(CAP_DESPLIEGUE / "11 VPS - RabbitMQ exchanges y colas.png", None, "dep-11-rabbit"),
             "Salida de rabbitmqctl en el VPS: el exchange alcanfores.eventos, su exchange de mensajes muertos y las "
             f"{DATOS['colas']} colas de consumo, cada una con un consumidor activo y sin mensajes pendientes. Captura "
             "del 18 de noviembre de 2026.", ancho_cm=15.5)
    i.p("Como cada servicio es dueño de sus datos, una reserva no puede confirmarse con una única transacción ACID "
        "que abarque reservas y pagos. Para mantener la consistencia se aplicó el patrón Saga, propuesto por "
        "Garcia-Molina y Salem (1987) para transacciones de larga duración y adaptado a microservicios por "
        "Richardson (2018): la operación se divide en transacciones locales encadenadas por eventos y, si un paso "
        "no llega a completarse, se ejecutan acciones de compensación. En su variante coreografiada no existe un "
        "orquestador central; cada servicio reacciona a los eventos de los demás. La Figura {F:saga} muestra el "
        "recorrido de una reserva pagada con Yape y la compensación cuando el pago no llega dentro del plazo.")
    i.figura("saga", "Saga Coreografiada del Registro y la Confirmación de una Reserva",
             RECURSOS / "diagrama-saga.png",
             "Las flechas continuas son solicitudes HTTP y las discontinuas, eventos. El plazo de 20 minutos y la "
             "revisión cada minuto son valores de la configuración del complejo. Elaboración propia.",
             ancho_cm=15.5, alto_max_cm=13)
    i.p("La consistencia resultante es eventual: durante unos milisegundos la reserva puede seguir PENDIENTE aunque el "
        "pago ya esté validado (Kleppmann, 2017). Para que ese intervalo no produzca errores se adoptaron cuatro "
        "reglas: los eventos se publican solo después del commit de la transacción que los origina; cada "
        "consumidor registra el identificador del evento en una tabla evento_procesado y descarta los duplicados "
        "(receptor idempotente, según Hohpe y Woolf, 2003); los mensajes que fallan tres veces pasan a una cola de "
        "mensajes muertos para su revisión; y pagos-service guarda una copia de los datos de la reserva para emitir "
        "comprobantes aunque reservas-service no responda. Por último, la Tabla {T:integraciones} resume los "
        "servicios externos que complementan el sistema.")
    i.tabla("integraciones", "Integraciones Externas del Sistema",
            ["Integración", "Microservicio", "Uso en el sistema", "Si no está configurada"],
            [["Factiliza (RENIEC y SUNAT)", "reservas", "Completa nombres a partir del DNI y razón social y dirección a partir del RUC; caché de 30 días.", "Responde 503 y el formulario permite escribir los datos a mano."],
             ["Evolution API (WhatsApp, Baileys)", "notificaciones", "Envía cada aviso con el PDF como documento; el número del complejo se vincula con un código QR desde el panel (Evolution API, s. f.).", "Los avisos quedan en modo SIMULADO y se ven en la bandeja del panel."],
             ["Servidor SMTP", "notificaciones", "Correo HTML con el logo embebido y los PDF adjuntos.", "Modo SIMULADO con vista previa en el panel."],
             ["Alcanfores Pay (beta)", "pagos", "Pasarela simulada con tarjetas de prueba, 3-D Secure, Yape beta y webhook firmado con HMAC-SHA256.", "Siempre activa: no mueve dinero real."],
             ["Mercado Pago (Checkout Pro)", "pagos", "Preferencias de pago y webhook con firma en modo sandbox (Mercado Pago, s. f.).", "Opción oculta; en producción está desactivada."],
             ["Comprobantes electrónicos", "pagos", "Boleta, factura y nota de crédito en PDF y XML UBL 2.1 con emisor ficticio (RUC 20000000001).", "Simulados: sin firma digital ni envío a SUNAT."]],
            [3.2, 2.9, 5.6, 3.8], "Ningún secreto de estas integraciones se guarda en el repositorio: se leen de "
            "variables de entorno del servidor. Elaboración propia.")


def captura(i: Informe, clave, titulo, fuente, nota, ancho_cm=15, alto_max_cm=17.5):
    ruta, recorte = fuente
    img = i.preparar(ruta, recorte, clave)
    return i.figura(clave, titulo, img, nota, ancho_cm=ancho_cm, alto_max_cm=alto_max_cm)


NOTA_LOCAL = "Captura del sistema en funcionamiento con los siete microservicios y datos de la base de datos."


def seccion_6_7(i: Informe):
    i.en("6.7. Interfaces, Pantallas de Ejecución y Código")
    i.p("Este apartado describe el funcionamiento completo del sistema mediante pantallas reales de ejecución. Todas "
        "las capturas se tomaron el 18 de noviembre de 2026 con el front-end conectado a los siete microservicios, sin "
        "datos simulados en el navegador: cada reserva, pago, comprobante y reseña que aparece existe en la base de "
        "datos, ya sea porque proviene de la semilla de demostración o porque se creó durante la prueba. Los clientes de "
        "prueba usan DNI ficticios de la serie 9902xxxx. Primero se recorre el portal del cliente, desde que conoce "
        "las canchas hasta que califica su experiencia, y después el panel del personal del complejo. Las capturas "
        "se muestran en su versión de escritorio (1440 px de ancho); la versión para celular se presenta en el manual "
        "de usuario del apartado 12.4.")

    # ------------------------------------------------------------------ cliente
    i.sub4("Recorrido del cliente: conocer las canchas",
           "El cliente entra a la página de inicio sin crear una cuenta (RF-02 y RNF-02). La Figura {F:c-inicio} muestra "
           "la portada con el mensaje principal, el botón «Reservar», los tres pasos del proceso y las cuatro canchas "
           "del complejo con su foto, su precio de día y de noche y su calificación promedio, datos que el front-end "
           "obtiene de catalogo-service.")
    captura(i, "c-inicio", "Página de Inicio del Portal de Reservas", portal("01-inicio-1440", 1000),
            "La sección inferior de la página, que no se muestra, incluye las tarifas, el mapa de ubicación y las "
            "preguntas frecuentes. " + NOTA_LOCAL)
    i.p("Al elegir una cancha se abre su ficha (Figura {F:c-detalle}), con una galería de fotos que se puede ampliar a "
        "pantalla completa, la descripción, las medidas, las características (camerinos, estacionamiento, "
        "iluminación LED) y los precios por franja. El botón «Reservar esta cancha» lleva al primer paso con la cancha "
        "ya elegida, de modo que el cliente no repite la selección.")
    captura(i, "c-detalle", "Ficha de una Cancha con Galería de Fotos", portal("03-cancha-detalle-1440", 1000),
            "Las fotos se sirven desde catalogo-service con caché de siete días; el administrador las gestiona desde el "
            "panel (Figura {F:a-fotos}). " + NOTA_LOCAL)
    i.p("Debajo de la ficha se encuentran las reseñas (Figura {F:c-resenas}). Para evitar opiniones falsas, solo puede "
        "calificar quien jugó: el formulario «Califica tu experiencia» pide el código de reserva y el DNI, y "
        "catalogo-service consulta a reservas-service si esa reserva corresponde a la cancha, está confirmada y su "
        "turno ya empezó. Cada reserva admite una sola reseña, y el complejo puede responderla u ocultarla.")
    captura(i, "c-resenas", "Reseñas Verificadas con Promedio y Distribución por Estrellas",
            portal("05-cancha-resenas-1440", 900),
            "Se ordenan por recientes, mejor o peor calificadas; la respuesta del complejo aparece debajo de la reseña. "
            + NOTA_LOCAL)

    i.sub4("Recorrido del cliente: reservar en tres pasos",
           "El asistente de reserva implementa el caso de uso CU-03 (Tabla 12). En el paso 1 (Figura {F:c-paso1}) el "
           "cliente elige la cancha, el día en una tira de catorce días y un turno libre. Los turnos se pintan según su "
           "estado: disponible, ocupado, en mantenimiento o pasado; los que empiezan en menos de 30 minutos ya no se "
           "ofrecen. El resumen lateral muestra la tarifa, el adelanto del 50 % y la condición de devolución (RF-01, "
           "RF-03 y RF-04).")
    captura(i, "c-paso1", "Paso 1: Elección de Cancha, Día y Turno", portal("10-paso1-horario-1440", 900),
            "Los turnos de noche (desde las 18:00) tienen otra tarifa. " + NOTA_LOCAL)
    i.p("En el paso 2 se registran los datos del cliente (RF-05). Todos los campos se validan mientras se escriben y "
        "otra vez en el servidor, con los mismos mensajes en español (RNF-03). La Figura {F:c-paso2err} muestra lo que "
        "ocurre al pulsar «Continuar» con el formulario vacío: cada campo indica qué falta. El DNI tiene un botón de "
        "búsqueda que consulta a RENIEC mediante Factiliza y completa nombres y apellidos; si el servicio no responde, "
        "aparece un aviso y el cliente los escribe a mano, de modo que la reserva nunca depende de un tercero.")
    captura(i, "c-paso2err", "Paso 2: Validaciones por Campo de los Datos del Cliente",
            portal("11-paso2-errores-1440", 1017),
            "Los mensajes son los definidos en la especificación del sistema; por ejemplo, «El DNI debe tener 8 "
            "dígitos.» o «Ingresa un celular peruano de 9 dígitos que empiece con 9.». " + NOTA_LOCAL)
    i.p("Si el cliente necesita factura, ingresa su RUC; el sistema comprueba el dígito verificador (módulo 11) antes "
        "de consultar a SUNAT y completa la razón social y la dirección fiscal. La Figura {F:c-sunat} muestra el caso "
        "de un RUC que SUNAT reporta en estado «BAJA DE OFICIO»: el sistema advierte que no está activo para que el "
        "cliente lo corrija. Al final del paso se aceptan los términos y la política de privacidad, exigidos por la "
        "Ley N.° 29733 (RNF-09).")
    captura(i, "c-sunat", "Paso 2: Consulta del RUC en SUNAT para Emitir Factura",
            portal("14-paso2-sunat-aviso-1440", 1100),
            "La respuesta de SUNAT se guarda 30 días en caché para no repetir consultas. " + NOTA_LOCAL)
    i.p("En el paso 3 el cliente elige cómo pagar el adelanto (RF-06). Con Yape o Plin, la pantalla muestra el número "
        "del complejo y el monto exacto, y pide el número de operación (6 a 12 dígitos) y la captura del comprobante "
        "(JPG o PNG de hasta 5 MB) con vista previa (Figura {F:c-yape}). Al confirmar, reservas-service registra la "
        "reserva en estado PENDIENTE y bloquea el turno durante el plazo de pago, y pagos-service guarda el "
        "comprobante para que el personal lo valide.")
    captura(i, "c-yape", "Paso 3: Pago del Adelanto con Yape y Carga del Comprobante",
            portal("17-paso3-yape-1440", 916),
            "La imagen del comprobante se valida en el navegador y, de nuevo, en el servidor por sus bytes iniciales, "
            "de modo que un archivo con la extensión cambiada se rechaza. " + NOTA_LOCAL)
    i.p("Si dos personas intentan reservar el mismo turno al mismo tiempo, solo una lo consigue (RF-09). La otra "
        "recibe la respuesta 409 TURNO_OCUPADO y el sistema muestra el aviso «Alguien se adelantó» (Figura "
        "{F:c-409}); al cerrarlo vuelve al paso 1 con el turno marcado como ocupado y conserva los datos ya "
        "escritos. La Figura {F:cod-409} muestra cómo el front-end distingue este caso de los errores de validación; "
        "el mecanismo del servidor que lo garantiza se explica en el apartado 6.9.4.")
    captura(i, "c-409", "Aviso de Doble Reserva tras la Respuesta 409 del Servidor",
            portal("33-conflicto-409-1440", 900),
            "El conflicto se provocó registrando el mismo turno por la API mientras el cliente completaba el paso 3. "
            + NOTA_LOCAL)
    i.codigo("cod-409", "Tratamiento del Conflicto por Doble Reserva en el Paso 3", """
try {
  reserva = await reservasApi.crear({
  idTurno: r.turno.idTurno,
  cliente: { dni, nombres: r.cliente.nombres, apellidos: r.cliente.apellidos,
       telefono: String(r.cliente.telefono), correo: r.cliente.correo },
  metodoPago,
  aceptaTerminos: Boolean(r.cliente.aceptaTerminos),
  aceptaPrivacidad: Boolean(r.cliente.aceptaPrivacidad),
  });
  r.registrarReserva(reserva);
} catch (e) {
  if (esErrorApi(e) && (e.codigo === 'TURNO_OCUPADO' || e.status === 409)) {
  setConflicto(true);  // modal «Alguien se adelantó» y vuelta al paso 1
  return;
  }
  const porCampo = erroresPorCampo(e);   // 400: cada error se pinta en su campo
  // ...
}""", "Fragmento de frontend/src/paginas/portal/reservar/Paso3.tsx. Los puntos suspensivos indican líneas omitidas.")
    i.p("Cuando el registro termina, el cliente ve su código de reserva con el formato ALC-XXXXXX, el estado «En "
        "revisión», el resumen de la reserva y el botón para descargar la constancia en PDF (Figura {F:c-listo}). En "
        "paralelo, notificaciones-service le envía una copia por correo y por WhatsApp (RF-07).")
    captura(i, "c-listo", "Reserva Registrada con su Código y Constancia", portal("18-listo-yape-1440", 1000),
            "El código usa un alfabeto sin caracteres confundibles (sin I, O, 0 ni 1). " + NOTA_LOCAL)

    i.sub4("Recorrido del cliente: pagar con tarjeta",
           "Como alternativa a Yape y Plin, el cliente puede pagar con tarjeta en Alcanfores Pay (beta), una pasarela "
           "simulada que se comporta como un proveedor externo: tiene su propia página de pago, valida la tarjeta con "
           "el algoritmo de Luhn, la fecha de vencimiento y el CVV, y al aprobar el cobro avisa al comercio mediante un "
           "webhook firmado. La pantalla de pago (Figura {F:c-pasarela}) indica el comercio, el monto, el tiempo "
           "restante y un recuadro «Modo beta» con las tarjetas de prueba, porque no se procesa dinero real.")
    captura(i, "c-pasarela", "Página de Pago de la Pasarela Alcanfores Pay (beta)", portal("20-pasarela-1440", 1000),
            "Las tarjetas de prueba se listan en la Tabla {T:tarjetas} del manual de usuario. " + NOTA_LOCAL)
    i.p("Algunas tarjetas de prueba reproducen situaciones reales: fondos insuficientes, tarjeta bloqueada o vencida, "
        "y una tarjeta que exige autenticación 3-D Secure. En este último caso se abre una ventana que solicita el "
        "código de verificación (Figura {F:c-3ds}); con el código correcto el pago se aprueba y se muestra el "
        "número de autorización y la marca con los últimos cuatro dígitos (Figura {F:c-aprobado}). El sistema nunca "
        "guarda el número completo de la tarjeta ni el CVV.")
    captura(i, "c-3ds", "Verificación 3-D Secure en la Pasarela Beta", portal("26-pasarela-3ds-1440", 900),
            "Tarjeta de prueba Visa 4000 0027 6000 3184; el código 123456 aprueba y cualquier otro rechaza. " + NOTA_LOCAL)
    captura(i, "c-aprobado", "Pago Aprobado por la Pasarela", portal("29-pasarela-aprobado-1440", 900),
            "Al volver al portal, la reserva ya figura como confirmada, porque el webhook firmado registró el pago y "
            "emitió el comprobante. " + NOTA_LOCAL)

    i.sub4("Recorrido del cliente: seguir, corregir y cancelar la reserva",
           "En «Mi reserva» el cliente ingresa su código y su DNI para ver el estado (RF-10). Por privacidad, el sistema "
           "responde el mismo mensaje cuando el código no existe y cuando el DNI no coincide. La Figura {F:c-revision} "
           "muestra una reserva en revisión con su línea de tiempo, el detalle de montos y el pago pendiente de "
           "validación.")
    captura(i, "c-revision", "Consulta de una Reserva en Revisión", portal("43-mi-reserva-en-revision-1440", 1000),
            "La línea de tiempo se guarda en la tabla reserva_historial de reservas-service. " + NOTA_LOCAL)
    i.p("Si el personal rechaza el comprobante, la reserva pasa a «Observado», el cliente ve el motivo y recibe un nuevo "
        "plazo para reenviarlo desde la misma pantalla (Figura {F:c-observada}). Una vez validado el pago, la reserva "
        "aparece confirmada con su boleta o factura en PDF y XML (Figura {F:c-confirmada}).")
    captura(i, "c-observada", "Reserva Observada con Opción para Reenviar el Comprobante",
            portal("44-mi-reserva-observada-1440", 1000),
            "El rechazo se hizo desde el panel con la observación «La captura no muestra el número de operación "
            "completo». " + NOTA_LOCAL)
    captura(i, "c-confirmada", "Reserva Confirmada con su Comprobante Electrónico Simulado",
            portal("47-mi-reserva-confirmada-1440", 1000),
            "Los comprobantes son simulados y llevan la marca «sin validez tributaria». " + NOTA_LOCAL)
    i.p("El cliente también puede cancelar su reserva antes de que empiece el turno, indicando un motivo de al menos "
        "diez caracteres (RF-11). La ventana de la Figura {F:c-cancelar} le informa si le corresponde devolución, lo "
        "que ocurre cuando hubo pago y faltan al menos 24 horas. Al confirmar, la reserva se cancela, el turno vuelve "
        "a estar disponible y pagos-service registra el reembolso simulado y emite una nota de crédito que anula la "
        "boleta (Figura {F:c-cancelada}), con lo que se cumple RF-12.")
    captura(i, "c-cancelar", "Cancelación de la Reserva por el Cliente", portal("48-mi-reserva-cancelar-1440", 900),
            "La regla de 24 horas se lee de la configuración del complejo. " + NOTA_LOCAL)
    captura(i, "c-cancelada", "Reserva Cancelada con Devolución y Nota de Crédito",
            portal("49-mi-reserva-cancelada-1440", 1000),
            "Boleta B001-00000115 anulada por la nota de crédito BC01-00000001. " + NOTA_LOCAL)
    i.p("Finalmente, la constancia de reserva (Figura {F:c-constancia}) es un PDF A4 generado en el servidor con el "
        "código en grande, un código QR que abre «Mi reserva», los datos del turno, los montos y las condiciones. El "
        "cliente puede presentarla impresa o desde su celular al llegar al complejo.")
    captura(i, "c-constancia", "Constancia de Reserva en PDF con Código QR", portal("60-constancia-pdf-1440", 900),
            "Generada con Thymeleaf y openhtmltopdf; el QR se dibuja con ZXing. " + NOTA_LOCAL)

    # ------------------------------------------------------------------ administrador
    i.sub4("Recorrido del administrador: acceso y vista del día",
           "El personal ingresa por /admin/login con su usuario y contraseña (Figura {F:a-login}). Si las credenciales "
           "son correctas, auth-service entrega un token JWT válido por ocho horas; tras cinco intentos fallidos la "
           "cuenta se bloquea 15 minutos (RF-13). El sistema distingue dos roles: ADMIN, con acceso total, y "
           "RECEPCION, que atiende reservas y pagos pero no gestiona canchas, reportes ni usuarios.")
    captura(i, "a-login", "Inicio de Sesión del Panel de Administración",
            (RECURSOS / "panel-login-produccion-1440.png", None),
            "Captura del sitio en producción (https://alcanfores.dbltech.tech/admin/login) tomada el 18 de noviembre "
            "de 2026.")
    i.p("La pantalla «Hoy» (Figura {F:a-hoy}) concentra el trabajo diario: indicadores de reservas del día, comprobantes "
        "por validar, ocupación y tasa de inasistencia del mes; la lista de pagos pendientes de revisión; la agenda "
        "por cancha con una línea que marca la hora actual; y alertas como turnos próximos sin pago.")
    captura(i, "a-hoy", "Pantalla «Hoy» del Panel con Indicadores y Agenda del Día", panel("01-hoy-1440", 1000),
            "Los indicadores se calculan sobre los datos de demostración de la semilla, que simulan 45 días de "
            "operación; no corresponden a la operación real del complejo. " + NOTA_LOCAL)

    i.sub4("Recorrido del administrador: validar pagos y atender reservas",
           "La lista de reservas (Figura {F:a-reservas}) muestra contadores por estado que funcionan como filtros, un "
           "buscador por código, DNI, nombre o celular, y un panel lateral de filtros por estado de pago, cancha, "
           "origen y fechas (RF-14).")
    captura(i, "a-reservas", "Lista de Reservas con Contadores y Filtros", panel("02-reservas-1440", 900),
            "Cada fila tiene un menú de acciones (ver, validar, cancelar, registrar asistencia). " + NOTA_LOCAL)
    i.p("El detalle de una reserva en revisión (Figura {F:a-detalle}) presenta el turno, los montos, el cliente, el "
        "historial y, a la derecha, la imagen del comprobante con zoom (RF-15). Al pulsar «Validar», una ventana "
        "resume el monto esperado, el método y el número de operación, y pide confirmar que el dinero llegó a la "
        "cuenta del complejo (Figura {F:a-validar}).")
    captura(i, "a-detalle", "Detalle de una Reserva con el Visor del Comprobante", panel("04-reserva-detalle-1440", 1000),
            "Corresponde al caso de uso CU-09 (Tabla 13). " + NOTA_LOCAL)
    captura(i, "a-validar", "Validación del Comprobante de Pago", panel("06-modal-validar-1440", 900),
            "Si el comprobante no coincide, el botón «Rechazar» exige una observación de 10 a 255 caracteres. "
            + NOTA_LOCAL)
    i.p("Al validar, pagos-service marca el pago como VALIDADO, emite una boleta o factura simulada y publica los "
        "eventos que confirman la reserva y avisan al cliente (RF-16). La Figura {F:a-validada} muestra la reserva ya "
        "confirmada, con el adelanto pagado, el comprobante emitido y la línea de tiempo actualizada. Cuando el cliente "
        "llega al complejo, el personal cobra el saldo en caja (Figura {F:a-saldo}); el sistema impide cobrar más que "
        "el saldo y emite un comprobante por cada cobro.")
    captura(i, "a-validada", "Reserva Confirmada tras la Validación", panel("07-reserva-validada-1440", 1000),
            "Boleta B001-00000118 emitida automáticamente. " + NOTA_LOCAL)
    captura(i, "a-saldo", "Cobro del Saldo en Caja con Factura", panel("12-modal-cobrar-saldo-1440", 900),
            "El RUC del cliente se consulta en SUNAT desde la misma ventana. " + NOTA_LOCAL)
    i.p("Las reservas que llegan por teléfono o en persona se registran con «Nueva reserva» (RF-18), que reutiliza la "
        "consulta de DNI, la grilla de turnos libres y, opcionalmente, un pago inicial en caja (Figura "
        "{F:a-nueva}). Así el complejo mantiene un único registro de turnos y desaparecen los cuadernos que "
        "originaban los cruces de horario.")
    captura(i, "a-nueva", "Registro de una Reserva Telefónica desde el Panel", panel("17-nueva-reserva-1440", 1000),
            "Las reservas manuales no tienen plazo de pago porque el personal las registra directamente. " + NOTA_LOCAL)
    i.p("La agenda (Figura {F:a-agenda}) presenta una grilla de canchas por horas con el estado de cada turno. Desde ella "
        "se bloquea un turno o un rango de horas por mantenimiento, con motivo obligatorio, y el administrador puede "
        "generar los turnos de un periodo; esta operación es idempotente, por lo que repetirla no duplica turnos (RF-21).")
    captura(i, "a-agenda", "Agenda de Turnos por Cancha y Hora", panel("22-agenda-1440", 1000),
            "No se puede bloquear un turno que ya tiene una reserva vigente. " + NOTA_LOCAL)

    i.sub4("Recorrido del administrador: canchas, comprobantes y reportes",
           "En «Canchas» (Figura {F:a-canchas}) el administrador crea y edita canchas, define sus precios de día y de "
           "noche (la tarifa nocturna no puede ser menor que la diurna) y las activa o desactiva (RF-20). Al desactivar "
           "una cancha, reservas-service bloquea sus turnos futuros libres al recibir el evento cancha.actualizada. Cada "
           "cancha admite hasta diez fotos con leyenda, orden y una foto principal (Figura {F:a-fotos}).")
    captura(i, "a-canchas", "Gestión de Canchas con Foto, Estado y Precios", panel("30-canchas-1440", 900),
            "Pantalla disponible solo para el rol ADMIN. " + NOTA_LOCAL)
    captura(i, "a-fotos", "Gestión de las Fotos de una Cancha", panel("33-cancha-fotos-1440", 1000),
            "Se aceptan imágenes JPG, PNG o WebP de hasta 5 MB y al menos 200 px por lado, verificadas por su "
            "contenido. " + NOTA_LOCAL)
    i.p("La sección «Pagos y comprobantes» lista las boletas, facturas y notas de crédito emitidas, con su PDF y su XML "
        "(Figura {F:a-comprobantes}). Anular un comprobante no lo borra: emite una nota de crédito que lo referencia, "
        "tal como exige la normativa de comprobantes electrónicos (Superintendencia Nacional de Aduanas y de "
        "Administración Tributaria [SUNAT], s. f.). La Figura {F:a-boleta} muestra la representación impresa de una "
        "boleta, que incluye el código QR, el valor resumen y la marca de agua de documento de prueba.")
    captura(i, "a-comprobantes", "Lista de Comprobantes Electrónicos Simulados", panel("44-comprobantes-1440", 900),
            "La numeración por serie (B001, F001, BC01, FC01) no tiene huecos porque se asigna dentro de la misma "
            "transacción del comprobante. " + NOTA_LOCAL)
    captura(i, "a-boleta", "Representación Impresa de una Boleta de Venta Simulada", portal("61-boleta-pdf-1440", 900),
            "El emisor es ficticio y el documento declara que no tiene validez tributaria. " + NOTA_LOCAL)
    i.p("Los reportes (Figura {F:a-reportes}) responden a los indicadores de la Tabla 5: reservas por día, ingresos por "
        "método de pago, ocupación por cancha, reservas por hora, origen de las reservas y tasa de inasistencia, con "
        "exportación a Excel (RF-22).")
    captura(i, "a-reportes", "Reportes de Reservas e Ingresos", panel("50-reportes-1440", 1150),
            "Por defecto se muestran los últimos 30 días; los valores provienen de la semilla de demostración. "
            + NOTA_LOCAL)

    i.sub4("Recorrido del administrador: notificaciones y WhatsApp",
           "Cada aviso que el sistema envía queda en una bandeja con su estado (enviado, simulado o fallido) y una vista "
           "previa fiel del correo HTML (Figura {F:a-correo}) o del mensaje de WhatsApp con su PDF adjunto (Figura "
           "{F:a-whatsapp}). El administrador puede reintentar un envío fallido y revisar los recordatorios "
           "programados dos horas antes de cada partido.")
    captura(i, "a-correo", "Bandeja de Notificaciones con la Vista Previa del Correo",
            panel("51-notificacion-correo-1440", 900),
            "La vista previa se muestra en un iframe aislado (sandbox). " + NOTA_LOCAL)
    captura(i, "a-whatsapp", "Vista Previa de un Aviso de WhatsApp con la Constancia en PDF",
            panel("52-notificacion-whatsapp-1440", 900), NOTA_LOCAL)
    i.p("La conexión del número de WhatsApp del complejo se realiza desde Configuración: el botón «Conectar» solicita a "
        "Evolution API un código QR que se escanea con el teléfono, igual que en WhatsApp Web. La Figura {F:a-qr} se "
        "tomó en el entorno local, donde Evolution API no está configurado, y muestra cómo el panel informa qué "
        "variables faltan en el servidor en lugar de fallar sin explicación.")
    captura(i, "a-qr", "Ventana de Conexión de WhatsApp por Código QR", panel("59-whatsapp-qr-1440", 900),
            "En el entorno local el servidor respondió 409 WHATSAPP_NO_CONFIGURADO; en producción las variables "
            "EVOLUTION_* están definidas en el servidor. " + NOTA_LOCAL)

    i.sub4("Recorrido del administrador: usuarios, bitácora y estado del sistema",
           "El administrador gestiona las cuentas del personal (Figura {F:a-usuarios}): crea usuarios con rol ADMIN o "
           "RECEPCION, los desactiva y restablece contraseñas, sin poder quitarse a sí mismo el rol ni dejar el sistema "
           "sin administradores. Toda acción que cambia datos queda en la bitácora (Figura {F:a-bitacora}) con fecha, "
           "actor, rol, acción, entidad e IP, lo que cumple el requisito de trazabilidad RNF-19.")
    captura(i, "a-usuarios", "Gestión de Usuarios del Panel", panel("61-usuarios-1440", 900), NOTA_LOCAL)
    captura(i, "a-bitacora", "Bitácora de Auditoría de Todos los Microservicios", panel("67-bitacora-1440", 900),
            "Los registros llegan a auth-service por el evento auditoria.accion. " + NOTA_LOCAL)
    i.p("Por último, «Estado del sistema» (Figura {F:a-estado}) consulta el endpoint /api/estado del gateway, que verifica "
        "la salud de cada microservicio registrado en Eureka y su latencia. Esta pantalla permite al personal saber si "
        "un problema se debe a un servicio caído antes de llamar al soporte.")
    captura(i, "a-estado", "Estado de los Microservicios", panel("69-estado-sistema-1440", 900),
            "Un servicio sin instancias aparece como DOWN y el gateway responde 503 en sus rutas. " + NOTA_LOCAL)


def total_pruebas():
    return sum(x[1] for x in DATOS["pruebas"])


def pct(v):
    return "—" if v is None else f"{v:.1f} %".replace(".", ",")


def seccion_6_8(i: Informe):
    i.en("6.8.1. Configuración e Inicialización del Proyecto")
    i.p("El back-end se organizó como un proyecto Maven de varios módulos: un pom.xml padre fija las versiones de Java 17, "
        "Spring Boot 3.5.16 y Spring Cloud 2025.0.3 para los ocho módulos (los siete servicios y la librería comun), de "
        "modo que todos compilan con las mismas dependencias. Spring Boot aporta la configuración automática, el "
        "servidor web embebido y los Actuator de salud que luego usan Docker y el gateway (Spring, s. f.-a). Cada "
        "servicio tiene su clase principal con @SpringBootApplication, fija la zona horaria America/Lima al iniciar "
        "(RNF-13) y crea su esquema de base de datos con migraciones de Flyway versionadas (Redgate, s. f.), en lugar "
        "de dejar que Hibernate modifique las tablas.")
    i.p("La configuración se escribió en application.yml con variables de entorno y valores por defecto para el "
        "desarrollo local, de modo que el mismo artefacto se ejecuta en la computadora del desarrollador y en "
        "producción cambiando solo el entorno (Figura {F:cod-yml}). El repositorio es público, por lo que ninguna "
        "contraseña ni token figura en el código. La Tabla {T:versiones} contrasta las tecnologías propuestas en la "
        "Tabla 2 con las versiones que finalmente se implementaron.")
    i.codigo("cod-yml", "Configuración de reservas-service con Variables de Entorno", """
spring:
  application:
    name: reservas-service
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/alcanfores_reservas
    username: ${DB_USUARIO:root}
    password: ${DB_CLAVE:}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate  # Flyway crea las tablas; Hibernate solo las verifica
  rabbitmq:
    host: ${RABBIT_HOST:localhost}
    listener:
      simple:
        default-requeue-rejected: false
        retry: { enabled: true, max-attempts: 3 }
eureka:
  client:
    service-url: { defaultZone: "${EUREKA_URL:http://localhost:8761/eureka}" }""",
             "Fragmento abreviado de backend/reservas-service/src/main/resources/application.yml; la URL JDBC real "
             "toma el nombre de la base de la variable DB_NOMBRE e incluye parámetros de zona horaria y codificación.")
    i.tabla("versiones", "Tecnologías Propuestas frente a las Implementadas",
            ["Capa", "Propuesta (Tabla 2)", "Implementación final"],
            [["Back-end", "Java 17 y Spring Boot 3", "Java 17, Spring Boot 3.5.16, Spring Cloud 2025.0.3 (Gateway, Eureka, LoadBalancer, Resilience4j)"],
             ["Persistencia", "MySQL, Spring Data JPA e Hibernate", "MySQL 8.4, Spring Data JPA, Hibernate 6 y Flyway; una base de datos por servicio"],
             ["Seguridad", "Spring Security y JWT", "Spring Security 6 con filtro JWT (HS256, jjwt 0.12) y BCrypt de fuerza 12"],
             ["Mensajería", "No prevista", "RabbitMQ 4 con Spring AMQP: exchange topic, colas durables y cola de mensajes muertos"],
             ["Front-end", "React 18 con TypeScript", "React 18.3, TypeScript 5.6, Vite 6 y React Router 6.28"],
             ["Formularios y HTTP", "Tailwind CSS y Axios", "Tailwind CSS 3.4, react-hook-form 7, Zod 3 y Axios 1.7 con interceptores"],
             ["Pruebas", "JUnit 5 y Mockito", "JUnit 5, Mockito, AssertJ, MockMvc, GreenMail, JaCoCo y Postman con Newman"],
             ["Despliegue", "Docker y servicio en la nube", "Docker Compose en un VPS, Caddy con Let's Encrypt y GitHub Actions"]],
            [2.6, 4.2, 8.2], "Las versiones se tomaron de los archivos pom.xml y package.json del repositorio. "
            "Elaboración propia.")

    i.en("6.8.2. Diseño de Arquitectura por Capas (Controller, Service, Repository)")
    i.p("Dentro de cada microservicio se respetó la arquitectura por capas descrita por Fowler (2002). Los paquetes se "
        "nombraron en español y siguen la misma estructura en los cinco servicios de dominio: controlador (recibe la "
        "solicitud HTTP y valida su forma), servicio (interfaces y sus implementaciones con las reglas de negocio y las "
        "transacciones), repositorio (acceso a datos con Spring Data JPA) y modelo (entidades). Se agregaron paquetes "
        "de apoyo: dto con registros (records) inmutables y anotaciones de Bean Validation, mapeador para convertir "
        "entidades en respuestas, evento para publicar y consumir mensajes, cliente para las llamadas a otros "
        "servicios y config para la seguridad y la mensajería. Ningún controlador accede a un repositorio y ninguna "
        "entidad sale de la API, lo que evita exponer columnas internas.")
    i.p("Esta organización materializa los principios SOLID planteados en el segundo objetivo específico. La Tabla "
        "{T:solid} resume cómo se aplicó cada principio con ejemplos del código.")
    i.tabla("solid", "Aplicación de los Principios SOLID en el Back-end",
            ["Principio", "Aplicación en el código"],
            [["Responsabilidad única", "ReservaServicioImpl orquesta el caso de uso, OperacionesReserva aplica las reglas del registro, ConstanciaServicio genera el PDF y ReservaMapeador arma las respuestas."],
             ["Abierto/cerrado", "La interfaz ProveedorDocumentos permite agregar otro proveedor de RENIEC/SUNAT sin modificar DocumentoServicioImpl, que hoy usa FactilizaProveedor."],
             ["Sustitución de Liskov", "Los clientes HTTP (por ejemplo, CatalogoCliente y CatalogoClienteHttp) se reemplazan por dobles de prueba con Mockito sin cambiar el comportamiento esperado."],
             ["Segregación de interfaces", "Cada capacidad tiene su interfaz pequeña: ReservaServicio (portal), ReservaAdminServicio (panel), PanelServicio (indicadores) y ReporteServicio."],
             ["Inversión de dependencias", "Los servicios dependen de interfaces y reciben sus colaboradores por constructor (@RequiredArgsConstructor); Spring inyecta la implementación concreta."]],
            [3.5, 11.5], "Elaboración propia a partir del código de reservas-service.")

    i.en("6.8.3. Creación de Endpoints y Controladores REST")
    i.p("Los endpoints siguen el estilo REST de Fielding (2000): recursos identificados por URI, métodos HTTP según la "
        "operación y respuestas en JSON con códigos de estado estándar. Bogner et al. (2023) muestran que respetar "
        "estas reglas hace más comprensibles las API; por ello se adoptaron convenciones uniformes: rutas en plural y en "
        "español, prefijo /api/admin para el panel, paginación con los parámetros page, size y sort, y el mismo cuerpo "
        "de error en todos los servicios. La Tabla {T:endpoints} presenta una selección de los endpoints; la lista "
        "completa se documenta automáticamente con OpenAPI en la interfaz Swagger de cada servicio (Figura "
        "{F:swagger}).")
    i.tabla("endpoints", "Endpoints Principales de la API REST",
            ["Método", "Ruta", "Acceso", "Descripción"],
            [["GET", "/api/catalogo/canchas", "Público", "Canchas activas con foto principal y calificación."],
             ["GET", "/api/disponibilidad?fecha=", "Público", "Turnos de cada cancha con su estado para una fecha."],
             ["POST", "/api/reservas", "Público", "Registra la reserva PENDIENTE (201) o responde 409 TURNO_OCUPADO."],
             ["GET", "/api/reservas/{codigo}?dni=", "Público", "Estado, montos y línea de tiempo de la reserva."],
             ["POST", "/api/reservas/{codigo}/cancelar", "Público", "Cancela con motivo y calcula la devolución."],
             ["GET", "/api/documentos/dni/{dni}", "Público", "Consulta RENIEC mediante Factiliza (con caché)."],
             ["POST", "/api/pagos/comprobante", "Público", "Recibe la imagen del comprobante Yape o Plin (multipart)."],
             ["POST", "/api/pasarela/intentos/{ref}/tarjeta", "Público", "Procesa la tarjeta en la pasarela beta."],
             ["POST", "/api/webhooks/pasarela-beta", "Firma HMAC", "Aviso de pago aprobado; idempotente."],
             ["POST", "/api/auth/login", "Público", "Devuelve el token JWT y los datos del usuario."],
             ["GET", "/api/admin/dashboard", "ADMIN, RECEPCION", "Indicadores, agenda y alertas del día."],
             ["POST", "/api/admin/pagos/{id}/validar", "ADMIN, RECEPCION", "Valida el pago y emite el comprobante."],
             ["POST", "/api/admin/reservas", "ADMIN, RECEPCION", "Reserva manual con pago inicial opcional."],
             ["POST", "/api/admin/turnos/generar", "ADMIN", "Genera turnos de un periodo sin duplicarlos."],
             ["POST", "/api/admin/comprobantes/{id}/anular", "ADMIN", "Emite la nota de crédito del comprobante."],
             ["GET", "/api/admin/reportes/reservas.xlsx", "ADMIN", "Exporta el reporte de reservas a Excel."],
             ["GET", "/api/estado", "Público", "Salud y latencia de cada microservicio."]],
            [1.4, 5.4, 2.6, 6.2], "Los endpoints públicos que reciben datos tienen límite de solicitudes por IP en el "
            "gateway. Elaboración propia a partir de docs/SPEC.md.", tam=19)
    i.p("La Figura {F:cod-controlador} muestra el controlador público de reservas. La anotación @Valid activa Bean "
        "Validation sobre el cuerpo de la solicitud antes de llegar al servicio, @ResponseStatus devuelve el código "
        "201 al crear y las anotaciones @Tag y @Operation generan la documentación OpenAPI en español.")
    i.codigo("cod-controlador", "Controlador REST Público de Reservas", """
@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas (portal)",
  description = "Registrar, consultar y cancelar una reserva con código + DNI")
public class ReservaPublicaControlador {

  private final ReservaServicio servicio;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Registrar reserva",
       description = "409 TURNO_OCUPADO si otra persona tomó el turno.")
  public ReservaCreadaRespuesta registrar(
      @Valid @RequestBody RegistroReservaSolicitud solicitud) {
    return servicio.registrar(solicitud);
  }
  // ... consultar (GET /{codigo}), cancelar (POST /{codigo}/cancelar) y constancia PDF
}""", "Fragmento de backend/reservas-service/src/main/java/pe/alcanfores/reservas/controlador/"
       "ReservaPublicaControlador.java; se omitieron los mensajes de las anotaciones de validación.")
    captura(i, "swagger", "Documentación OpenAPI de reservas-service en Swagger UI",
            (CAP_DESPLIEGUE / "17 Swagger - API de reservas-service (OpenAPI).png", None),
            "Cada microservicio publica su contrato en /swagger-ui.html y /v3/api-docs (springdoc-openapi 2.8). "
            "Captura del 18 de noviembre de 2026.")

    i.en("6.8.4. Inyección de Dependencias y Manejo Global de Excepciones")
    i.p("Todas las dependencias se inyectan por constructor: las clases declaran sus colaboradores como campos final y "
        "Lombok genera el constructor con @RequiredArgsConstructor. Con este estilo, recomendado por Spring, las clases "
        "no dependen del contenedor para probarse, ya que una prueba unitaria puede construirlas con dobles de "
        "Mockito, y una dependencia faltante se detecta al iniciar la aplicación.")
    i.p("Los errores se manejan en un único lugar. Las reglas de negocio lanzan NegocioExcepcion con un código y un "
        "mensaje (por ejemplo, conflicto TURNO_OCUPADO o regla RESERVA_YA_EMPEZO), y la clase ManejadorGlobalErrores de "
        "la librería comun, anotada con @RestControllerAdvice, convierte esa excepción y las de validación en una "
        "respuesta con el mismo formato en los siete servicios (Figura {F:cod-errores}). La Tabla {T:http} resume los "
        "códigos de estado utilizados. Gracias a este contrato, el front-end puede mostrar cada error en su campo sin "
        "conocer qué servicio respondió.")
    i.codigo("cod-errores", "Manejador Global de Errores de la Librería Compartida", """
@RestControllerAdvice
public class ManejadorGlobalErrores {

  @ExceptionHandler(NegocioExcepcion.class)
  public ResponseEntity<ErrorApi> negocio(NegocioExcepcion e, HttpServletRequest req) {
    return responder(e.getStatus(), e.getCodigo(), e.getMessage(), req,
            e.getErrores());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorApi> cuerpoInvalido(MethodArgumentNotValidException e,
                         HttpServletRequest req) {
    List<ErrorCampo> errores = new ArrayList<>();
    for (FieldError fe : e.getBindingResult().getFieldErrors()) {
      errores.add(new ErrorCampo(fe.getField(), fe.getDefaultMessage()));
    }
    return validacion(errores, req);  // 400 VALIDACION con un error por campo
  }
  // ... JSON ilegible, parámetros faltantes, archivo muy grande, ruta inexistente
}""", "Fragmento de backend/comun/src/main/java/pe/alcanfores/comun/error/ManejadorGlobalErrores.java.")
    i.tabla("http", "Códigos de Estado HTTP y Códigos de Error de la API",
            ["Estado", "Código en el cuerpo", "Situación"],
            [["400", "VALIDACION", "Uno o más campos no cumplen las reglas; el arreglo errores indica cada campo y su mensaje."],
             ["401", "NO_AUTENTICADO", "Falta el token, venció o su firma no es válida."],
             ["403", "SIN_PERMISO", "El rol del usuario no puede usar la función (por ejemplo, RECEPCION en reportes)."],
             ["404", "NO_ENCONTRADO", "El recurso no existe; también se usa para rutas /interno solicitadas desde fuera."],
             ["409", "TURNO_OCUPADO, RESENA_DUPLICADA…", "Conflicto con el estado actual de los datos."],
             ["422", "RESERVA_YA_EMPEZO, ULTIMO_ADMIN…", "Regla de negocio incumplida."],
             ["423", "USUARIO_BLOQUEADO", "Cinco intentos fallidos de inicio de sesión."],
             ["429", "DEMASIADAS_SOLICITUDES", "Se superó el límite por IP del gateway."],
             ["503", "SERVICIO_NO_DISPONIBLE", "Un microservicio o un proveedor externo no responde."]],
            [1.4, 5, 8.6], "Cuerpo de error común: timestamp, status, codigo, mensaje, ruta y errores. "
            "Elaboración propia.")

    i.en("6.8.5. Pruebas de API REST con Postman y TDD (JUnit 5 / Mockito)")
    i.p("Las reglas críticas se desarrollaron con un enfoque guiado por pruebas: primero se escribía el caso esperado "
        "(por ejemplo, «el segundo cliente que reserva el mismo turno recibe 409») y luego el código que lo cumple, "
        "siguiendo el ciclo propuesto por Beck (2002). Se combinaron pruebas unitarias con JUnit 5 y Mockito, que aíslan "
        "un servicio de sus dependencias (The JUnit Team, s. f.; Mockito, s. f.), pruebas de controlador con MockMvc y "
        "pruebas de integración con @SpringBootTest contra una base de datos real, en las que Flyway recrea el esquema "
        "al iniciar y el reloj del sistema se fija con la utilidad Tiempo para probar plazos y vencimientos. La "
        f"cobertura se midió con JaCoCo (Mountainminds GmbH & Co. KG, s. f.). En total se ejecutan {total_pruebas()} "
        "pruebas automatizadas sin fallos, distribuidas como indica la Tabla {T:junit}.")
    filas = [[m, str(n), str(f), pct(a), pct(b), pct(c)] for m, n, f, a, b, c in DATOS["pruebas"]]
    filas.append(["^^Total^^", f"^^{total_pruebas()}^^", f"^^{sum(x[2] for x in DATOS['pruebas'])}^^", "", "", ""])
    i.tabla("junit", "Pruebas Automatizadas del Back-end y Cobertura por Módulo",
            ["Módulo", "Pruebas", "Fallos", "Instrucciones", "Ramas", "Líneas"],
            filas, [4.6, 1.8, 1.6, 2.4, 2, 2],
            "Cobertura de JaCoCo (target/site/jacoco). La librería comun y el servidor Eureka tienen una cobertura "
            "propia baja porque su código se ejerce desde las pruebas de los servicios que los usan; la meta de 70 % "
            "de RNF-15 se aplicó a los cinco servicios de dominio y al gateway. Elaboración propia.")
    i.p("Entre los casos cubiertos están las validaciones de cada campo (400), los accesos sin token o con rol "
        "insuficiente (401 y 403), la doble reserva concurrente, la expiración de reservas sin pago, la numeración "
        "concurrente de comprobantes, el cálculo del IGV y del monto en letras, la firma HMAC del webhook, las "
        "tarjetas de prueba de la pasarela y el envío real de correo contra un servidor SMTP de pruebas (GreenMail). "
        "La Figura {F:ci-pruebas} muestra el resumen que GitHub Actions publica después de cada ejecución.")
    captura(i, "ci-pruebas", "Resumen de Pruebas por Microservicio en GitHub Actions",
            (CAP_DESPLIEGUE / "20 GitHub Actions - resumen de pruebas por microservicio.jpg", None),
            "El flujo de integración continua ejecuta mvn verify contra MySQL 8.4 en cada cambio de la rama main. "
            "Captura del 18 de noviembre de 2026.")
    pm = DATOS["postman"]
    i.p("Para probar la API desde fuera, como lo haría un cliente, se construyó una colección de Postman con "
        f"{pm['peticiones']} peticiones organizadas por microservicio y {pm['aserciones']} aserciones escritas con "
        "pm.test, que verifican el código de estado, la forma del JSON y las reglas de negocio. La colección "
        "encadena variables (el token del inicio de sesión, el código de la reserva creada) y tiene entornos para el "
        "servidor local y para producción. Ejecutada con Newman, la herramienta de línea de comandos de Postman "
        f"(Postman, s. f.), terminó con {pm['fallos']} fallos y un tiempo medio de respuesta de {pm['promedio_ms']} ms "
        "(Tabla {T:postman}).")
    i.tabla("postman", "Colección de Postman por Carpeta",
            ["Carpeta", "Peticiones"],
            [[c, str(n)] for c, n in pm["carpetas"]] +
            [["^^Total^^", f"^^{pm['peticiones']} peticiones · {pm['aserciones']} aserciones · {pm['fallos']} fallos^^"]],
            [8, 7], "Archivos docs/postman/Alcanfores.postman_collection.json y sus entornos local y de producción. "
            "Elaboración propia.")


def seccion_6_9(i: Informe):
    i.en("6.9.1. Modelado Entidad-Relación y Diagrama de Base de Datos MySQL")
    i.p("El modelo entidad-relación del apartado 6.4 (Figura 4) se diseñó con siete tablas en una sola base de datos, "
        "siguiendo a Chen (1976) y a Elmasri y Navathe (2016). Al adoptar microservicios, ese modelo se repartió según "
        "el patrón de una base de datos por servicio (Richardson, 2018): cada servicio es dueño exclusivo de sus "
        "tablas, tiene su propio usuario de MySQL y ningún otro servicio puede leerlas, de modo que un cambio de "
        "esquema no rompe a los demás. Las relaciones que cruzan servicios ya no son claves foráneas, sino referencias "
        "por un identificador de negocio, como el código de reserva, que se verifican por HTTP o se mantienen al día "
        "mediante eventos. La Tabla {T:distribucion} muestra cómo quedó repartido el modelo y la Figura {F:mysql} las "
        "bases de datos creadas en producción.")
    i.tabla("distribucion", "Distribución del Modelo de Datos entre los Microservicios",
            ["Tabla del diseño (6.4)", "Base de datos", "Implementación"],
            [["cliente", "alcanfores_reservas", "Se identifica por DNI (único) en lugar del teléfono; registra si el DNI se verificó con RENIEC."],
             ["turno", "alcanfores_reservas", "Restricción única (cancha, fecha, hora de inicio); copia el nombre de la cancha y su franja de día o noche."],
             ["reserva", "alcanfores_reservas", "Se añadieron estado de pago, plazo de pago (vence_en), versión para bloqueo optimista y la columna generada turno_activo."],
             ["cancelacion", "alcanfores_reservas", "Se agregó SISTEMA como responsable (vencimiento) y los datos de devolución."],
             ["cancha", "alcanfores_catalogo", "Precios de día y de noche, características en JSON, fotos y reseñas en tablas propias."],
             ["pago", "alcanfores_pagos", "Referencia la reserva por su código; incluye los canales comprobante, pasarela y caja, y una copia de los datos del cliente."],
             ["administrador", "alcanfores_auth", "Se añadieron roles, bloqueo por intentos y la tabla bitacora."],
             ["— (nuevas)", "varias", "reserva_historial, consulta_documento, comprobante, serie, intento_pago, webhook_evento, notificacion, recordatorio y evento_procesado."]],
            [3.2, 3.6, 8.2], "Elaboración propia a partir de los scripts V1__esquema.sql de cada servicio.")
    captura(i, "mysql", "Bases de Datos y Usuarios por Microservicio en MySQL 8.4 de Producción",
            (CAP_DESPLIEGUE / "10 VPS - MySQL base de datos por servicio.png", None),
            "Cada servicio se conecta con su propio usuario, que solo tiene permisos sobre su base de datos. Captura "
            "del 18 de noviembre de 2026.", ancho_cm=12.5)

    i.en("6.9.2. Mapeo Objeto-Relacional (ORM) con JPA y Hibernate")
    i.p("Las tablas se mapearon a entidades de JPA, con Hibernate como implementación (Hibernate, s. f.). La Figura "
        "{F:cod-entidad} muestra la entidad Reserva: las relaciones con Cliente y Turno son @ManyToOne con carga diferida "
        "(LAZY) para no traer datos innecesarios, los estados se guardan como texto con @Enumerated(EnumType.STRING) "
        "para que la base de datos sea legible, y el campo version activa el bloqueo optimista. Hibernate se configuró "
        "con ddl-auto: validate: no crea ni altera tablas, solo comprueba al iniciar que las entidades coinciden con "
        "el esquema creado por Flyway, lo que detecta a tiempo cualquier diferencia entre código y base de datos.")
    i.codigo("cod-entidad", "Entidad JPA Reserva", """
/** La columna generada turno_activo (no mapeada) + su índice UNIQUE impiden
 *  dos reservas vigentes del mismo turno. */
@Entity
@Table(name = "reserva")
@Getter @Setter @NoArgsConstructor
public class Reserva {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "id_turno", nullable = false)
  private Turno turno;

  @Enumerated(EnumType.STRING) @Column(nullable = false)
  private EstadoReserva estado;

  @Version
  private Long version;
  // ... cliente, estadoPago, montos, venceEn, origen, nota
}""", "Fragmento de backend/reservas-service/src/main/java/pe/alcanfores/reservas/modelo/Reserva.java.")
    i.p("La columna turno_activo, que garantiza la regla de no doble reserva propuesta en el apartado 6.4, no se mapea "
        "en la entidad porque la calcula MySQL. Se definió como columna generada y almacenada con una restricción de "
        "unicidad en la migración de Flyway (Figura {F:cod-turno-activo}): toma el valor de id_turno mientras la reserva "
        "está PENDIENTE o CONFIRMADA y NULL cuando se cancela. Como MySQL admite varios NULL en un índice único, un "
        "turno conserva el historial de sus cancelaciones, pero nunca puede tener dos reservas vigentes (Oracle, s. f.).")
    i.codigo("cod-turno-activo", "Columna Generada turno_activo con Restricción de Unicidad", """
CREATE TABLE reserva (
  id_reserva      BIGINT        NOT NULL AUTO_INCREMENT,
  codigo_reserva  VARCHAR(12)   NOT NULL,
  id_turno        BIGINT        NOT NULL,
  estado          ENUM('PENDIENTE','CONFIRMADA','CANCELADA') NOT NULL,
  -- ... cliente, estado de pago, montos y fechas
  version         BIGINT        NOT NULL DEFAULT 0,
  -- Garantía de NO doble reserva (RNF-11): solo una reserva vigente por turno.
  turno_activo    BIGINT GENERATED ALWAYS AS (
   CASE WHEN estado IN ('PENDIENTE','CONFIRMADA') THEN id_turno ELSE NULL END) STORED,
  PRIMARY KEY (id_reserva),
  CONSTRAINT uq_reserva_codigo UNIQUE (codigo_reserva),
  CONSTRAINT uq_reserva_turno_activo UNIQUE (turno_activo),
  CONSTRAINT fk_reserva_turno FOREIGN KEY (id_turno) REFERENCES turno (id_turno)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;""",
             "Fragmento de backend/reservas-service/src/main/resources/db/migration/V1__esquema.sql.")

    i.en("6.9.3. Implementación de Operaciones CRUD y Consultas JPQL Personalizadas")
    i.p("Las operaciones básicas de creación, lectura, actualización y borrado se obtienen al extender JpaRepository, "
        "que Spring Data JPA implementa en tiempo de ejecución. Las búsquedas simples se declaran por el nombre del "
        "método (por ejemplo, findByFechaOrderByIdCanchaAscHoraInicioAsc) y las más específicas se escriben en JPQL, "
        "el lenguaje de consultas orientado a entidades de JPA (Figura {F:cod-jpql}). Para evitar el problema de las N+1 "
        "consultas, las búsquedas que alimentan listas usan @EntityGraph y traen el turno y el cliente en una sola "
        "consulta. Los filtros combinables del panel (estado, pago, fecha, cancha, origen y texto libre) se construyen "
        "con especificaciones de JPA, y las consultas que modifican datos concurrentes usan bloqueos explícitos, como "
        "se explica en el siguiente apartado.")
    i.codigo("cod-jpql", "Consultas JPQL del Repositorio de Reservas", """
public interface ReservaRepositorio
    extends JpaRepository<Reserva, Long>, JpaSpecificationExecutor<Reserva> {

  @Query("select r from Reserva r "
       + "where r.turno.id = :idTurno and r.estado in :estados")
  List<Reserva> deTurnoEnEstados(@Param("idTurno") Long idTurno,
                 @Param("estados") Collection<EstadoReserva> estados);

  /** Reservas cuyo plazo para pagar ya venció (las expira la tarea programada). */
  @Query("select r.id from Reserva r where r.estado = :estado "
    + "and r.estadoPago in :estadosPago and r.venceEn < :ahora")
  List<Long> idsVencidas(@Param("estado") EstadoReserva estado,
             @Param("estadosPago") Collection<EstadoPago> estadosPago,
             @Param("ahora") LocalDateTime ahora);

  @EntityGraph(attributePaths = {"turno", "cliente"})
  @Query("select r from Reserva r where r.turno.fecha between :desde and :hasta")
  List<Reserva> conTurnoEntre(@Param("desde") LocalDate desde,
                @Param("hasta") LocalDate hasta);
}""", "Fragmento de backend/reservas-service/src/main/java/pe/alcanfores/reservas/repositorio/ReservaRepositorio.java.")

    i.en("6.9.4. Gestión Transaccional y Control de Concurrencia (@Transactional)")
    i.p("El registro de una reserva es la operación más delicada del sistema, porque dos clientes pueden elegir el mismo "
        "turno al mismo tiempo. Siguiendo la estrategia de tres defensas descrita en el apartado 5.3.6, la validación de "
        "la interfaz solo orienta al usuario y la garantía real está en el servidor. El método del servicio se ejecuta "
        "dentro de una transacción (@Transactional) que cumple las propiedades ACID (Härder y Reuter, 1983) y aplica "
        "dos candados (Figura {F:cod-transaccion}). El primero es un bloqueo pesimista: la consulta bloquearPorId, "
        "anotada con @Lock(PESSIMISTIC_WRITE), ejecuta SELECT … FOR UPDATE sobre el turno, de modo que la segunda "
        "solicitud espera a que la primera termine y luego encuentra el turno RESERVADO. El segundo es la restricción "
        "única de turno_activo: si por cualquier motivo dos inserciones llegaran a la base de datos, MySQL rechaza la "
        "segunda y el servicio convierte esa violación de integridad en la respuesta 409 TURNO_OCUPADO.")
    i.codigo("cod-transaccion", "Registro Transaccional de una Reserva con Bloqueo Pesimista", """
@Transactional(propagation = Propagation.MANDATORY)  // se une a la de registrar()
public Reserva crear(NuevaReserva n) {
  Turno t = turnos.bloquearPorId(n.idTurno()).orElseThrow(...);  // SELECT … FOR UPDATE
  // ... valida fecha (hoy + 14 días), anticipación de 30 min y turno no bloqueado
  if (t.getEstado() == EstadoTurno.RESERVADO
      || !reservas.deTurnoEnEstados(t.getId(), VIGENTES).isEmpty()) {
    throw ocupado();                                  // 409 TURNO_OCUPADO
  }
  Reserva r = new Reserva();
  r.setCodigoReserva(nuevoCodigo());  // ALC-XXXXXX con SecureRandom
  r.setVenceEn(web ? min(ahora.plusMinutes(p.minutosParaPagar()), t.inicio()) : null);
  t.setEstado(EstadoTurno.RESERVADO);
  try {
    reservas.saveAndFlush(r);
  } catch (DataIntegrityViolationException e) {  // índice UNIQUE de turno_activo
    throw ocupado();
  }
  publicar(Eventos.RESERVA_REGISTRADA, evento);  // a RabbitMQ después del commit
  return r;
}""", "Fragmento abreviado de backend/reservas-service/src/main/java/pe/alcanfores/reservas/servicio/impl/"
       "OperacionesReserva.java.")
    i.p("Esta solución se verificó con la prueba de integración DobleReservaIntegracionTest, que lanza dos y luego cinco "
        "hilos que intentan reservar el mismo turno en el mismo instante contra la base de datos real, sincronizados con "
        "un CountDownLatch (Figura {F:cod-prueba-concurrencia}). En ambos casos se registra exactamente una reserva, las "
        "demás solicitudes reciben 409 y se publica un solo evento. Una cuarta prueba inserta directamente en la tabla "
        "una segunda reserva vigente, saltándose el bloqueo, y comprueba que la restricción única la rechaza. El "
        "resultado coincide con el de Hermawan y Suharso (2025), quienes redujeron las reservas duplicadas de 100 % a 0 % "
        "con control de concurrencia, y con la advertencia de Clark et al. (2024) sobre la importancia de verificar el "
        "comportamiento real de los niveles de aislamiento en lugar de suponerlo.")
    i.codigo("cod-prueba-concurrencia", "Prueba de Integración de la Doble Reserva Concurrente", """
@Test
void dosHilosReservanElMismoTurnoALaVezYSoloUnoGana() throws Exception {
  Turno t = turno(1, HOY.plusDays(1), 19);
  CountDownLatch largada = new CountDownLatch(1);
  ExecutorService pool = Executors.newFixedThreadPool(2);
  List<Future<String>> resultados = new ArrayList<>();
  for (int i = 0; i < 2; i++) {
    String dni = "4000000" + i;
    resultados.add(pool.submit(() -> {
      largada.await();  // ambos hilos salen a la vez
      try { return servicio.registrar(solicitud(t.getId(), dni)).codigoReserva(); }
      catch (NegocioExcepcion e) { return e.getStatus() + " " + e.getCodigo(); }
    }));
  }
  largada.countDown();
  // ... se recogen las salidas
  assertThat(salidas).filteredOn(s -> s.startsWith("ALC-")).hasSize(1);
  assertThat(salidas).filteredOn(s -> s.equals("409 TURNO_OCUPADO")).hasSize(1);
  assertThat(reservas.count()).isEqualTo(1);
}""", "Fragmento abreviado de backend/reservas-service/src/test/java/pe/alcanfores/reservas/"
       "DobleReservaIntegracionTest.java.")
    i.p("El mismo criterio se aplicó en otros puntos sensibles: la numeración de comprobantes bloquea la fila de su serie "
        "dentro de la transacción del comprobante (probado con 100 emisiones simultáneas sin huecos ni duplicados), los "
        "consumidores de eventos registran el evento procesado en la misma transacción que su efecto, y los eventos se "
        "publican con @TransactionalEventListener en la fase AFTER_COMMIT, para que ningún servicio reciba la noticia "
        "de un cambio que luego se revirtió (RNF-10).")

    i.en("6.9.5. Configuración de Spring Security y Autenticación Stateless con JWT")
    i.p("La seguridad combina Spring Security en cada servicio con la validación previa en el gateway, una defensa en "
        "profundidad: aunque alguien llegara a un servicio sin pasar por el gateway, el servicio vuelve a verificar el "
        "token y el rol (Spring, s. f.-b). La configuración común (Figura {F:cod-seguridad}) desactiva las sesiones, el "
        "formulario de inicio de sesión y CSRF, porque la API no usa cookies, e incorpora dos filtros: uno para las "
        "llamadas internas entre servicios y otro para el token JWT. Cada servicio declara luego qué rutas son "
        "públicas y qué roles exige el resto; las funciones exclusivas del administrador se marcan además con "
        "@PreAuthorize(\"hasRole('ADMIN')\"). La Tabla {T:permisos} resume la matriz de permisos.")
    i.codigo("cod-seguridad", "Configuración de Seguridad Stateless de reservas-service", """
public static HttpSecurity configurarBase(HttpSecurity http, ComponentesSeguridad seg)
    throws Exception {
  return http
    .csrf(AbstractHttpConfigurer::disable)
    .cors(AbstractHttpConfigurer::disable)  // CORS lo resuelve el gateway
    .httpBasic(AbstractHttpConfigurer::disable)
    .formLogin(AbstractHttpConfigurer::disable)
    .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
    .addFilterBefore(seg.internoFiltro(), UsernamePasswordAuthenticationFilter.class)
    .addFilterBefore(seg.jwtFiltro(), UsernamePasswordAuthenticationFilter.class);
}

// SeguridadConfig de reservas-service
http.authorizeHttpRequests(a -> a
    .requestMatchers("/interno/**").hasRole("INTERNO")
    .requestMatchers(HttpMethod.GET, "/api/disponibilidad/**").permitAll()
    .requestMatchers("/api/reservas", "/api/reservas/**").permitAll()
    .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "RECEPCION")
    .anyRequest().denyAll());""",
             "Fragmentos abreviados de backend/comun/.../seguridad/SeguridadComun.java y backend/reservas-service/.../config/"
             "SeguridadConfig.java.")
    i.p("El token es un JWT firmado con HMAC-SHA256 (Jones et al., 2015) que emite auth-service tras verificar la "
        "contraseña con BCrypt de fuerza 12. Contiene el identificador, el usuario, el nombre, el rol y la fecha de "
        "expiración (ocho horas). El filtro de la Figura {F:cod-jwt} lee la cabecera Authorization, valida la firma y la "
        "vigencia y, si todo es correcto, registra al usuario con su rol en el contexto de seguridad de esa petición; "
        "no se guarda ninguna sesión en el servidor, por lo que cualquier instancia de un servicio puede atender la "
        "siguiente solicitud. Siguiendo las recomendaciones de Bucko et al. (2023), el sistema complementa el token con "
        "controles de comportamiento: bloqueo de la cuenta tras cinco intentos fallidos, el mismo mensaje para usuario "
        "inexistente y contraseña errónea, y un límite de diez intentos de inicio de sesión por minuto e IP en el gateway.")
    i.codigo("cod-jwt", "Filtro de Autenticación JWT", """
@Override
protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                FilterChain chain) throws ServletException, IOException {
  String cabecera = req.getHeader("Authorization");
  if (cabecera != null && cabecera.startsWith("Bearer ")
      && SecurityContextHolder.getContext().getAuthentication() == null) {
    try {
      UsuarioToken u = jwt.validar(cabecera.substring(7).trim());
      var auth = new UsernamePasswordAuthenticationToken(u, null,
          List.of(new SimpleGrantedAuthority("ROLE_" + u.rol())));
      SecurityContextHolder.getContext().setAuthentication(auth);
    } catch (ExpiredJwtException e) {
      req.setAttribute(ATRIBUTO_ERROR,
          "Tu sesión venció. Vuelve a iniciar sesión.");
    } catch (JwtException | IllegalArgumentException e) {
      req.setAttribute(ATRIBUTO_ERROR, "El token de sesión no es válido.");
    }
  }
  chain.doFilter(req, res);
}""", "Fragmento de backend/comun/src/main/java/pe/alcanfores/comun/seguridad/JwtFiltroAutenticacion.java.")
    i.tabla("permisos", "Matriz de Permisos por Rol",
            ["Función del panel", "ADMIN", "RECEPCION"],
            [["Hoy, reservas (listar, ver, registrar manual, cancelar, asistencia) y agenda", "Sí", "Sí"],
             ["Validar o rechazar comprobantes, cobrar saldo y descargar comprobantes", "Sí", "Sí"],
             ["Ver notificaciones", "Sí", "Sí"],
             ["Canchas y fotos, reseñas, configuración y generación de turnos", "Sí", "No"],
             ["Reportes, usuarios, bitácora y conexión de WhatsApp", "Sí", "No"],
             ["Anular comprobantes y reintentar notificaciones", "Sí", "No"]],
            [11, 2, 2], "Un usuario RECEPCION que intenta usar una función restringida recibe 403 y el panel le "
            "muestra la pantalla «Sin permiso». Elaboración propia a partir de docs/SPEC.md.")


def seccion_6_10(i: Informe):
    i.en("6.10.1. Configuración de la SPA con Vite y TypeScript")
    i.p("El front-end es una aplicación de página única creada con Vite (Vite, s. f.), React 18 y TypeScript en modo "
        "estricto (Microsoft, s. f.). Vite sirve el proyecto en desarrollo con recarga instantánea en el puerto 5186 y "
        "reenvía las rutas /api al gateway, de modo que el navegador trabaja como si front-end y API compartieran "
        "origen (Figura {F:cod-vite}). Para producción, vite build genera archivos estáticos con nombres que incluyen "
        "un hash del contenido, lo que permite que nginx los guarde en caché por un año sin riesgo de servir versiones "
        "viejas. Cada página se carga de forma diferida (lazy loading) y las librerías se separan en bloques (React, "
        "formularios), por lo que el celular del cliente descarga solo lo que necesita para la pantalla que abre, "
        "criterio que justificó la elección de React en el apartado 4.3.2.")
    i.codigo("cod-vite", "Configuración de Vite con Proxy hacia el API Gateway", """
// Puerto 5186 y proxy /api → api-gateway (8086).
export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
  port: 5186,
  strictPort: true,
  proxy: { '/api': { target: 'http://localhost:8086', changeOrigin: true } },
  },
  build: {
  rollupOptions: {
   output: {
    manualChunks: { react: ['react', 'react-dom', 'react-router-dom'],
            formularios: ['react-hook-form', 'zod', '@hookform/resolvers'] },
   },
  },
  },
});""", "Fragmento de frontend/vite.config.ts.")
    i.p("El código se organizó por responsabilidad: src/api contiene un cliente por microservicio con los tipos de sus "
        "respuestas; src/componentes, los componentes reutilizables; src/paginas, las pantallas del portal y del panel; "
        "src/rutas, el enrutador y la protección por rol; src/contexto, los estados globales; src/esquemas, las "
        "validaciones de Zod; y src/hooks y src/utilidades, la lógica compartida. ESLint revisa el código con cero "
        "advertencias permitidas y forma parte de la integración continua.")

    i.en("6.10.2. Estructura de Componentes Modular y Estilos con Tailwind CSS")
    i.p("La interfaz se construyó con componentes pequeños y reutilizables (Meta Open Source, s. f.). En "
        "componentes/ui están las piezas básicas usadas en todo el sistema (Boton, Campo, Modal, Estrellas, "
        "SubidaImagen, VisorImagen, Estados); en componentes/portal, las del cliente, como el asistente de pasos y la "
        "tarjeta de estado de la reserva; y en componentes/panel, el marco del panel, el visor de comprobantes y las "
        "ventanas de acciones sobre una reserva. Cada página compone estas piezas, de manera que un cambio de diseño "
        "en un botón se refleja en todas las pantallas.")
    i.p("Los estilos se escribieron con Tailwind CSS (Tailwind Labs, s. f.). Su archivo de configuración define como "
        "tokens los mismos colores, tipografías, radios y sombras del prototipo aprobado, por lo que las clases como "
        "bg-verde o text-noche producen exactamente la identidad visual de la Figura {F:proto-hoy}. El diseño es "
        "mobile-first: los estilos base se escriben para el celular y los prefijos md, lg y xl agregan la "
        "distribución de tableta y escritorio. La Figura {F:movil-portal} muestra el portal en un ancho de 390 px "
        "(RNF-01).")
    a = i.preparar(*movil("portal", "01-inicio-390", 1688), nombre="m-inicio")
    b = i.preparar(*movil("portal", "03-cancha-detalle-390", 1688), nombre="m-detalle")
    i.figura_par("movil-portal", "Portal en un Celular de 390 px: Inicio y Ficha de Cancha", [a, b],
                 "Las mismas pantallas de las Figuras {F:c-inicio} y {F:c-detalle} con el diseño adaptable para "
                 "celular. " + NOTA_LOCAL, ancho_cm=6.2, alto_max_cm=13.5, rotulos=["Inicio", "Ficha de cancha"])

    i.en("6.10.3. Enrutamiento con React Router v6 y Protección de Rutas Administrativas")
    i.p("La navegación se resolvió con React Router 6 y su función createBrowserRouter (React Router, s. f.). El portal "
        "agrupa las rutas públicas (/, /canchas, /canchas/:idOSlug, /reservar, /mi-reserva, /pasarela/:ref, "
        "/terminos y /privacidad) y el panel cuelga de /admin. Todas las rutas del panel están envueltas por el "
        "componente RutaProtegida (Figura {F:cod-ruta}): si no hay sesión, redirige al inicio de sesión recordando la "
        "página que se quería abrir; si hay sesión pero el rol no tiene permiso, muestra la pantalla «Sin permiso» "
        "(Figura {F:sin-permiso}). El menú lateral se genera desde la misma lista de rutas y roles, así que la "
        "recepcionista no ve opciones que no puede usar.")
    i.codigo("cod-ruta", "Componente RutaProtegida del Panel", """
/**
 * Protege rutas del panel: sin sesión → /admin/login (recuerda a dónde iba);
 * con sesión pero sin el rol → «Sin permiso». Sin `roles` basta con estar autenticado.
 */
export function RutaProtegida({ roles, children }:
               { roles?: Rol[]; children?: ReactNode }) {
  const { autenticado, usuario } = useAuth();
  const location = useLocation();
  if (!autenticado || !usuario)
  return <Navigate to="/admin/login" replace
          state={{ desde: location.pathname + location.search }} />;
  if (roles && !roles.includes(usuario.rol)) return <SinPermiso />;
  return <>{children ?? <Outlet />}</>;
}""", "Fragmento de frontend/src/rutas/RutaProtegida.tsx.")
    captura(i, "sin-permiso", "Usuario con Rol RECEPCION ante una Sección Exclusiva del Administrador",
            panel("73-recepcion-sin-permiso-1440", 900),
            "Ocultar una opción en la interfaz no basta como seguridad: aunque se escriba la ruta a mano, el servidor "
            "responde 403. " + NOTA_LOCAL)

    i.en("6.10.4. Formulario de Reserva en Tres Pasos y Validación de Datos (react-hook-form / Zod)")
    i.p("Cada paso del asistente es un formulario de react-hook-form (React Hook Form, s. f.), que registra los campos "
        "sin volver a dibujar toda la pantalla en cada tecla. Las reglas se declaran una sola vez en esquemas de Zod "
        "(Zod, s. f.) y se conectan al formulario con zodResolver; de esos esquemas TypeScript deduce además los tipos "
        "de los datos. La Figura {F:cod-zod} muestra el esquema del paso 2: los campos simples reutilizan los esquemas "
        "comunes de DNI, nombre, celular y correo, y superRefine agrega la regla condicional de la factura. Los "
        "mensajes son textualmente los mismos que devuelve Bean Validation en el servidor, por lo que el cliente ve "
        "igual texto si un error se detecta en el navegador o en la API, como se observó en la Figura {F:c-paso2err}.")
    i.codigo("cod-zod", "Esquema de Zod del Paso 2 del Asistente de Reserva", """
export const esquemaDatosCliente = z
  .object({
  dni: esquemaDni,  // /^\\d{8}$/ → «El DNI debe tener 8 dígitos.»
  nombres: esquemaNombre,
  apellidos: esquemaNombre,
  telefono: esquemaCelular,  // acepta +51 y espacios; normaliza a 9 dígitos
  correo: esquemaCorreo,
  tipoComprobante: z.enum(['BOLETA', 'FACTURA']),
  ruc: z.string().trim().optional().default(''),  // y razonSocial, direccionFiscal
  aceptaTerminos: esquemaAcepta,
  aceptaPrivacidad: esquemaAcepta,
  })
  .superRefine((d, ctx) => {
  if (d.tipoComprobante !== 'FACTURA') return;
  if (!rucValido(d.ruc))
   ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['ruc'], message: MSJ.ruc });
  if (d.razonSocial.length < 3 || d.razonSocial.length > 150)
   ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['razonSocial'],
          message: MSJ.factura });
  });""", "Fragmento de frontend/src/esquemas/reserva.ts; rucValido aplica el dígito verificador módulo 11.")
    i.p("El estado del asistente (paso actual, turno elegido, datos del cliente, método de pago y reserva ya creada) "
        "se guarda en sessionStorage, de modo que una recarga accidental no obliga a empezar de nuevo; la única "
        "excepción es la imagen del comprobante, que no se almacena por privacidad. Si el servidor rechaza un dato, el "
        "error se pinta en el campo correspondiente aunque pertenezca a un paso anterior.")

    i.en("6.10.5. Consumo de la API REST con Axios (Interceptores y Gestión de Tokens)")
    i.p("Todas las llamadas a la API pasan por una única instancia de Axios (Axios, s. f.) con dos interceptores "
        "(Figura {F:cod-axios}). El interceptor de petición agrega el token JWT cuando hay sesión y una cabecera "
        "X-Request-Id con un UUID; ese identificador viaja por el gateway y los microservicios y aparece en sus "
        "registros, lo que permite seguir una misma operación en todos los servicios. El interceptor de respuesta "
        "convierte cualquier error en un objeto ErrorApi con el estado, el código y los errores por campo del cuerpo "
        "estándar; si la respuesta es 401 en una petición autenticada, cierra la sesión porque el token venció, y en "
        "los demás casos muestra un aviso con el mensaje del servidor, salvo que la pantalla prefiera manejarlo.")
    i.codigo("cod-axios", "Cliente Axios con Interceptores de Petición y Respuesta", """
export const http = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  timeout: 20000,
  headers: { Accept: 'application/json' },
});

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = leerToken();
  if (token) config.headers.set('Authorization', `Bearer ${token}`);
  config.headers.set('X-Request-Id', uuid());
  return config;
});

http.interceptors.response.use((r) => r, (error: AxiosError) => {
  const cuerpo = aCuerpo(error);  // normaliza al cuerpo de error estándar
  const conToken = Boolean(error.config?.headers?.Authorization);
  if (cuerpo.status === 401 && conToken) alExpirarSesion?.();  // token vencido → login
  else if (!error.config?.silenciarToast) notificarError?.(cuerpo.mensaje);
  return Promise.reject(new ErrorApi(cuerpo));
});""", "Fragmento simplificado de frontend/src/api/cliente.ts.")

    i.en("6.10.6. Manejo de Estado Global (Context API) y Carga de Vouchers de Pago")
    i.p("El estado compartido entre pantallas se resolvió con Context API de React, sin librerías adicionales, porque "
        "solo hay tres estados globales. AuthContext guarda la sesión del panel en localStorage, programa el cierre "
        "automático cuando vence el token y sincroniza el cierre de sesión entre pestañas del navegador; "
        "ReservaContext mantiene el avance del asistente de tres pasos; y ToastContext muestra los avisos breves. El "
        "contexto de autenticación se conecta con el interceptor de Axios mediante una función de registro, para que "
        "el cliente HTTP lea siempre el token vigente sin depender de React.")
    i.p("La carga del comprobante de pago se implementó en el componente SubidaImagen, que permite elegir un archivo, "
        "tomar una foto con la cámara del celular o arrastrar la imagen, y muestra una vista previa ampliable. Antes "
        "de enviarlo valida en el navegador lo mismo que el servidor: tipo JPG o PNG comprobado por los primeros bytes "
        "del archivo y no por su extensión, tamaño máximo de 5 MB y lado mínimo de 200 px (RNF-08). La Figura "
        "{F:c-yape-err} muestra el rechazo de una imagen falsa y de un número de operación incompleto. El archivo se "
        "envía como multipart/form-data junto con el código de reserva, el DNI, el método y los datos de facturación, "
        "y Axios informa el progreso de la subida.")
    captura(i, "c-yape-err", "Validación del Número de Operación y de la Imagen del Comprobante",
            portal("16-paso3-errores-yape-1440", 1018),
            "La imagen rechazada tenía extensión .png pero su contenido no correspondía a una imagen. " + NOTA_LOCAL)


def seccion_6_11(i: Informe):
    i.en("6.11.1. Configuración de Políticas de Acceso CORS en Spring Boot")
    i.p("El navegador bloquea por defecto las peticiones de una página hacia un origen distinto, salvo que el servidor "
        "lo autorice con las cabeceras CORS. En una arquitectura de microservicios, configurar CORS en cada servicio "
        "duplicaría la política y podría generar cabeceras repetidas; por eso se centralizó en el API Gateway y se "
        "desactivó en los servicios (Figura {F:cod-seguridad}). La configuración (Figura {F:cod-cors}) acepta solo los "
        "orígenes listados en la variable FRONT_URL, los métodos y cabeceras que usa el front-end, expone X-Request-Id y "
        "Content-Disposition (necesaria para el nombre de los PDF descargados) y no admite credenciales, porque la "
        "autenticación viaja en la cabecera Authorization y no en cookies.")
    i.codigo("cod-cors", "Política CORS Centralizada en el API Gateway", """
@Configuration
public class CorsConfig {
  static final List<String> METODOS =
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
  static final List<String> CABECERAS =
      List.of("Authorization", "Content-Type", "X-Request-Id");

  @Bean
  FiltroCors filtroCors(AlcanforesPropiedades propiedades) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(origenes(propiedades.getFrontUrl()));  // FRONT_URL (lista)
    c.setAllowedMethods(METODOS);
    c.setAllowedHeaders(CABECERAS);
    c.setExposedHeaders(List.of("X-Request-Id", "Content-Disposition"));
    c.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource origen = new UrlBasedCorsConfigurationSource();
    origen.registerCorsConfiguration("/**", c);
    return new FiltroCors(origen);
  }
}""", "Fragmento de backend/api-gateway/src/main/java/pe/alcanfores/gateway/config/CorsConfig.java.")
    i.p("El filtro CORS se ejecuta antes que los rechazos de seguridad, para que el navegador pueda leer también las "
        "respuestas 401, 404 o 429 y el front-end muestre el mensaje correcto. En producción el portal y la API se "
        "publican bajo el mismo dominio (la SPA en la raíz y la API en /api/), por lo que el navegador no necesita "
        "consultas previas (preflight) y la política CORS actúa como barrera frente a páginas de otros orígenes. Las "
        "pruebas del gateway verifican que un origen permitido recibe las cabeceras y que uno ajeno es rechazado.")

    i.en("6.11.2. Pruebas de Integración y Sincronización de Datos")
    i.p("Las pruebas unitarias y de integración de cada servicio simulan a los demás; para comprobar que los siete "
        "funcionan juntos se escribió una prueba de punta a punta en Python (docs/pruebas/prueba_e2e.py) que recorre, "
        "contra el API Gateway, el ciclo completo de una reserva tal como lo haría un cliente y un administrador. La "
        "prueba verifica también la sincronización asíncrona: después de subir el comprobante espera a que el evento "
        "pago.registrado cambie la reserva a EN_REVISION, después de validar espera la confirmación por "
        "pago.validado, y después de cancelar comprueba que reserva.cancelada generó el reembolso y la nota de crédito "
        "en pagos-service y liberó el turno. La Tabla {T:e2e} resume sus comprobaciones.")
    loc, pro = DATOS["e2e_local"], DATOS["e2e_produccion"]
    i.tabla("e2e", "Comprobaciones de la Prueba de Punta a Punta",
            ["Grupo", "Qué se comprueba"],
            [["Plataforma", "Los 7 microservicios están UP en Eureka; catálogo con 4 canchas; detalle con fotos y reseñas; descarga de la foto."],
             ["Validaciones", "400 con un error por cada campo inválido; RUC con dígito verificador incorrecto; consulta real de RUC en SUNAT."],
             ["Reserva y concurrencia", "Reserva web PENDIENTE con plazo; segunda reserva del mismo turno: 409 TURNO_OCUPADO."],
             ["Pago con comprobante", "Comprobante Yape aceptado; imagen falsa rechazada; evento pago.registrado → EN_REVISION."],
             ["Seguridad", "Panel sin token: 401; RECEPCION en reportes: 403; inicio de sesión del administrador con JWT."],
             ["Validación y documentos", "Validación del pago; evento pago.validado → CONFIRMADA; boleta emitida; PDF de la boleta y de la constancia."],
             ["Notificaciones", "Ocho avisos (correo y WhatsApp) de registro, comprobante, confirmación y boleta; recordatorio programado dos horas antes."],
             ["Pasarela beta", "Tarjeta sin fondos rechazada; Visa aprobada; webhook firmado → reserva CONFIRMADA y PAGADO_TOTAL; factura al RUC."],
             ["Cancelación (saga)", "Cancelación con devolución; evento reserva.cancelada → reembolso y nota de crédito; turno DISPONIBLE otra vez."],
             ["Reseñas", "Reseña verificada publicada; reseña repetida 409; reseña de una reserva no jugada 422."],
             ["Panel", "Indicadores del día, reporte de ingresos, Excel de reservas y bitácora con las acciones de la prueba."],
             ["^^Resultado^^", f"^^Local: {loc['correctas']} de {loc['total']} ({loc['fecha']}). Producción: {pro['correctas']} de {pro['total']} ({pro['fecha']}).^^"]],
            [3.6, 11.4], "En producción la prueba agrega una comprobación final de limpieza (cancela la reserva de "
            "prueba). El único fallo en producción fue la descarga de la foto de la cancha, explicado en el apartado "
            "8.2 y corregido en el commit e18be07. Archivos docs/pruebas/resultado_e2e_local.txt y "
            "resultado_e2e_produccion.txt. Elaboración propia.")


def seccion_6_12(i: Informe):
    vps = DATOS["vps"]
    i.en("6.12.1. Contenerización de Servicios con Docker (Spring Boot + MySQL)")
    i.p("Cada componente se empaqueta en una imagen de Docker, de modo que se ejecuta igual en cualquier equipo con "
        "Docker instalado (Docker Inc., s. f.), como exige RNF-18. Para los siete servicios Java se escribió un solo Dockerfile "
        "de dos etapas (Figura {F:cod-docker}): la primera compila el proyecto Maven completo con una caché de "
        "dependencias y la segunda copia únicamente el JAR del módulo indicado sobre una imagen ligera con el entorno "
        "de ejecución de Java 17. El contenedor corre con un usuario sin privilegios y con opciones de la JVM "
        "ajustadas a un servidor pequeño: el recolector de basura serial, un máximo del 70 % de la memoria asignada y "
        "la compilación optimizada solo en su primer nivel, que reduce el consumo durante el arranque.")
    i.codigo("cod-docker", "Dockerfile de Dos Etapas Común a los Microservicios", """
FROM maven:3.9-eclipse-temurin-17 AS construccion
WORKDIR /fuente
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
ARG MODULO
RUN apt-get update \\
  && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl \\
  && rm -rf /var/lib/apt/lists/* \\
  && useradd --system --uid 1001 --home /app alcanfores \\
  && mkdir -p /app/uploads && chown -R alcanfores /app
WORKDIR /app
COPY --from=construccion /fuente/${MODULO}/target/${MODULO}-1.0.0.jar /app/app.jar
USER alcanfores
ENV TZ=America/Lima \\
  JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]""",
             "Fragmento de backend/Dockerfile; se omitieron las etiquetas y algunas opciones de la JVM.")
    i.p("El archivo deploy/docker-compose.yml describe los diez contenedores del sistema: MySQL 8.4, RabbitMQ 4, los "
        "siete servicios Java y la web nginx con la SPA. Todos comparten una red interna; solo el contenedor web se "
        "une además a la red del proxy Caddy, que ya corría en el VPS como servicio compartido y no forma parte del "
        "sistema. Cada servicio declara una verificación de salud con su Actuator, espera a que MySQL, RabbitMQ y "
        "Eureka estén sanos antes de iniciar, se reinicia solo si falla y tiene un límite de memoria. MySQL, RabbitMQ y "
        "las fotos de las canchas guardan sus datos en volúmenes persistentes, y un script de inicio crea las cinco "
        "bases de datos con un usuario propio para cada servicio. La Tabla {T:memoria} muestra el consumo medido en "
        "producción y la Figura {F:compose-ps}, el estado de los contenedores.")
    filas = []
    usado = limite = 0.0
    for nombre, imagen, mib, lim in DATOS["memoria"]:
        filas.append([nombre, imagen, f"{mib:.0f} MiB".replace(".", ","), f"{lim} MiB"])
        usado += mib
        limite += lim
    filas.append(["^^Total^^", "^^10 contenedores^^", f"^^{usado:,.0f} MiB^^".replace(",", " "), f"^^{limite:,.0f} MiB^^".replace(",", " ")])
    i.tabla("memoria", "Contenedores del Sistema en Producción y su Consumo de Memoria",
            ["Contenedor", "Imagen", "Memoria en uso", "Límite"], filas, [3.8, 6, 2.8, 2.4],
            f"Medición con docker stats el 18 de noviembre de 2026 en el VPS {vps['plan']} ({vps['vcpu']} vCPU, "
            f"{vps['ram']}). El sistema usa cerca de {usado / 1024:.1f} GiB, menos de un tercio de la memoria del "
            "servidor. En la misma medición aparece un contenedor auxiliar de RabbitMQ para desarrollo "
            f"({DATOS['rabbit_dev_mib']:.0f} MiB), que no pertenece al docker-compose del sistema. Elaboración propia.")
    captura(i, "compose-ps", "Contenedores en Ejecución y Consumo de Recursos en el VPS",
            (CAP_DESPLIEGUE / "08 VPS - docker compose ps y consumo de memoria.png", None),
            "Todos los contenedores aparecen en estado healthy, con las imágenes etiquetadas con el commit "
            "desplegado. Captura del 18 de noviembre de 2026.")

    i.en("6.12.2. Despliegue en Entorno Nube / PaaS (Railway / Render / Vercel)")
    i.p("El plan inicial consideraba una plataforma como servicio (PaaS) como Railway, Render o Vercel. Según el NIST, "
        "en una PaaS el proveedor administra el servidor y el usuario solo despliega su aplicación, mientras que en la "
        "infraestructura como servicio (IaaS) el usuario recibe una máquina virtual y controla el sistema operativo, la "
        "red y el almacenamiento (Mell y Grance, 2011). Al pasar a microservicios, el sistema dejó de ser una API y una "
        "base de datos para convertirse en diez contenedores que necesitan una red privada, un broker de mensajes "
        "siempre encendido y procesos Java que consumen memoria de forma permanente. Por ello el equipo comparó las "
        "alternativas (Tabla {T:despliegue}) y eligió un servidor virtual privado (VPS) con Docker Compose.")
    i.tabla("despliegue", "Comparación de Alternativas de Despliegue para el Sistema de Microservicios",
            ["Criterio", "VPS + Docker Compose (elegido)", "Railway", "Render", "Vercel"],
            [["Modelo", "IaaS: máquina virtual completa", "PaaS de contenedores", "PaaS de servicios web", "PaaS para front-end y funciones serverless"],
             ["Costo con 10 contenedores", "Costo fijo mensual de un VPS que el equipo ya tenía contratado", "Cobro por uso de CPU, memoria y red de cada servicio", "Cada servicio y la base de datos se cobran como instancias aparte; el plan gratuito suspende los servicios inactivos", "El front-end cabe en el plan gratuito, pero no ejecuta los servicios Java"],
             ["Control", "Total: sistema operativo, red interna, puertos, proxy y certificados", "Limitado a la configuración de la plataforma", "Limitado a la configuración de la plataforma", "Limitado; sin procesos persistentes"],
             ["Microservicios con RabbitMQ y Eureka", "Sí, en una red privada de Docker", "Posible, cada pieza como servicio facturable", "Posible con servicios privados de pago", "No admite brokers ni servidores de larga duración"],
             ["Memoria", "8 GB compartidos por todos los contenedores", "Según el consumo de cada servicio", "Memoria reducida por instancia en los planes de entrada", "No aplica"],
             ["Despliegue automático", "GitHub Actions por SSH con reversión automática", "Integrado con GitHub", "Integrado con GitHub", "Integrado con GitHub"]],
            [2.7, 3.6, 2.9, 3.1, 2.9], "Comparación cualitativa elaborada por el equipo con la información pública de "
            "cada plataforma en noviembre de 2026; no se consignan precios porque cambian con frecuencia. Elaboración "
            "propia.", tam=18)
    i.p(f"El sistema se publicó en un VPS {vps['plan']} con {vps['vcpu']} vCPU, {vps['ram']} de memoria, "
        f"disco de {vps['disco']} y {vps['so']}, con Docker {vps['docker']} y Docker Compose {vps['compose']}. El "
        "procedimiento fue el siguiente: se crearon los registros DNS A y AAAA del subdominio alcanfores.dbltech.tech "
        "en el panel de Hostinger; se clonó el repositorio en /opt/alcanfores; se creó el archivo deploy/.env con los "
        "secretos (contraseñas de las bases de datos, de RabbitMQ, clave del JWT, token interno y tokens de "
        "integraciones), que nunca sale del servidor; se ejecutó deploy/desplegar.sh, que construye las imágenes, "
        "levanta los contenedores y espera a que todos estén sanos; y, por último, se agregó el sitio a Caddy, que "
        "obtiene y renueva automáticamente el certificado de Let's Encrypt y fuerza HTTPS con la cabecera HSTS "
        "(Caddy, s. f.; Internet Security Research Group, s. f.). La Figura {F:caddy} muestra la configuración de "
        "Caddy y la primera respuesta HTTPS del sitio; las demás capturas del procedimiento se incluyen en el "
        "apartado 12.3.")
    captura(i, "caddy", "Publicación del Dominio con Caddy y Certificado SSL de Let's Encrypt",
            (CAP_DESPLIEGUE / "13 VPS - Caddy dominio y certificado SSL.png", None),
            "El certificado se emitió para alcanfores.dbltech.tech y la respuesta HTTP/2 200 confirma el acceso seguro. "
            "Captura del 18 de noviembre de 2026.")

    i.en("6.12.3. Integración Continua (CI/CD) con GitHub Actions")
    ci = DATOS["ci"]
    i.p("La integración y el despliegue continuos se automatizaron con GitHub Actions (GitHub, s. f.), siguiendo la "
        "idea de Humble y Farley (2010) de que cada cambio debe pasar por el mismo flujo verificable antes de llegar a "
        "producción. El archivo .github/workflows/ci-cd.yml define tres trabajos (Tabla {T:ci}). Los dos primeros se "
        "ejecutan en cada cambio o solicitud de integración hacia la rama main; el despliegue solo corre cuando ambos "
        "terminaron bien y el cambio llegó a main.")
    i.tabla("ci", "Trabajos del Flujo de Integración y Despliegue Continuos",
            ["Trabajo", "Pasos", "Duración"],
            [["Back-end · pruebas de los microservicios", f"Levanta MySQL 8.4 como servicio, crea las bases de prueba, ejecuta mvn verify (compilación, {total_pruebas()} pruebas y JaCoCo) y publica el resumen y los reportes.", ci["backend"]],
             ["Front-end · lint y compilación", "Instala dependencias con npm ci, ejecuta ESLint sin advertencias y compila la SPA con tsc y Vite.", ci["frontend"]],
             ["Despliegue al VPS (Docker)", "Se conecta por SSH con una llave guardada como secreto, ejecuta deploy/desplegar.sh y comprueba que /api/estado responde en el dominio público.", ci["despliegue"]]],
            [4.2, 8.6, 2.2], "Duraciones de la última ejecución exitosa registrada en GitHub Actions. Elaboración propia.")
    i.p("El script de despliegue (Figura {F:cod-workflow}) etiqueta las imágenes con el commit, espera hasta siete "
        "minutos a que todos los contenedores estén sanos y, si alguno falla, vuelve a la versión anterior y termina con "
        "error, de modo que una versión defectuosa no queda publicada. Los datos de acceso al servidor se guardan "
        "como secretos cifrados del repositorio y nunca aparecen en los registros. La Figura {F:ci-verde} muestra una "
        "ejecución completa en verde.")
    i.codigo("cod-workflow", "Trabajo de Despliegue del Flujo de GitHub Actions", """
  desplegar:
    name: Despliegue al VPS (Docker)
    needs: [backend, frontend]
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    environment:
      name: produccion
      url: https://alcanfores.dbltech.tech
    steps:
      - name: Desplegar por SSH
        uses: appleboy/ssh-action@v1.2.5
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ vars.VPS_USER }}
          key: ${{ secrets.VPS_SSH_KEY }}
          script: cd /opt/alcanfores && ./deploy/desplegar.sh
      - name: Verificar el sitio en línea
        run: curl -fsS https://alcanfores.dbltech.tech/api/estado""",
             "Fragmento simplificado de .github/workflows/ci-cd.yml; la verificación real reintenta hasta 12 veces.")
    captura(i, "ci-verde", "Ejecución Exitosa del Flujo de CI/CD en GitHub Actions",
            (CAP_DESPLIEGUE / "19 GitHub Actions - CI-CD en verde (pruebas, compilacion y despliegue).jpg", None),
            "Los tres trabajos terminaron correctamente y el entorno «produccion» enlaza al sitio publicado. Captura "
            "del 18 de noviembre de 2026.")


def texto_e2e_produccion():
    pro = DATOS["e2e_produccion"]
    base = (f"En producción, la primera ejecución ({pro['fecha']}) obtuvo {pro['correctas']} de {pro['total']} "
            f"comprobaciones correctas; la única fallida fue «{pro['fallo']}», causada por una regla de nginx que se "
            "corrigió en el commit e18be07 (apartado 8.2).")
    final = DATOS.get("e2e_produccion_final")
    if final:
        base += (f" Tras la corrección, la prueba se repitió el {final['fecha']} con {final['correctas']} de "
                 f"{final['total']} comprobaciones correctas.")
    return base


def capitulo_7(i: Informe):
    i.en("7. Resultados")
    i.p("Este capítulo presenta los resultados obtenidos al terminar la implementación: el grado de cumplimiento de "
        "los requerimientos funcionales y no funcionales definidos en los apartados 6.1 y 6.2, las funcionalidades que "
        "se agregaron durante el desarrollo, el resultado de las pruebas y el estado del despliegue en producción.")

    i.en("7.1. Funcionalidades Implementadas")
    i.p("Los 22 requerimientos funcionales de las Tablas 7 y 8 quedaron implementados. La Tabla {T:rf} indica, para cada "
        "uno, cómo se implementó y con qué evidencia se verificó; las figuras citadas corresponden al recorrido del "
        "apartado 6.7.")
    rf = [
        ["RF-01", "GET /api/disponibilidad y calendario de 14 días con estados disponible, ocupado, bloqueado y pasado.", "Pruebas de reservas-service; Figura {F:c-paso1}."],
        ["RF-02", "Catálogo de canchas activas con fotos, precios y calificación.", "Prueba E2E «Catálogo público con 4 canchas»; Figura {F:c-inicio}."],
        ["RF-03", "Precio por franja, adelanto del 50 %, regla de devolución de 24 h, términos y privacidad.", "Figuras {F:c-detalle} y {F:c-paso1}."],
        ["RF-04", "Paso 1 del asistente con cancha, día y turno.", "Figura {F:c-paso1}."],
        ["RF-05", "Paso 2 sin crear cuenta, con consulta de DNI (RENIEC) y RUC (SUNAT).", "Prueba E2E de validación (400 por campo); Figura {F:c-paso2err}."],
        ["RF-06", "Yape o Plin con número de operación e imagen; además, tarjeta en la pasarela beta.", "Pruebas de pagos-service; E2E «Comprobante de Yape subido»."],
        ["RF-07", "Código ALC-XXXXXX y constancia PDF con QR.", "E2E «Constancia de reserva en PDF»; Figura {F:c-constancia}."],
        ["RF-08", "Turno RESERVADO desde la reserva PENDIENTE, con plazo de pago de 20 minutos.", "Pruebas de expiración con reloj fijado."],
        ["RF-09", "Bloqueo pesimista y columna única turno_activo; respuesta 409.", "Prueba concurrente de 2 y 5 hilos; E2E «Doble reserva: 409»."],
        ["RF-10", "«Mi reserva» con código y DNI, línea de tiempo, pagos y comprobantes.", "Figuras {F:c-revision} y {F:c-confirmada}."],
        ["RF-11", "Cancelación con motivo antes del inicio; devolución si faltan 24 h o más.", "E2E «Cliente cancela con motivo»; Figura {F:c-cancelar}."],
        ["RF-12", "Responsable, fecha y motivo visibles en la consulta.", "Figura {F:c-cancelada}."],
        ["RF-13", "Inicio de sesión con JWT de 8 h, roles y bloqueo por intentos.", "70 pruebas de auth-service; E2E «Login del administrador»."],
        ["RF-14", "Lista con contadores, búsqueda y filtros combinables.", "Figura {F:a-reservas}."],
        ["RF-15", "Detalle con visor del comprobante y zoom.", "Figura {F:a-detalle}."],
        ["RF-16", "Validar (emite comprobante) o rechazar con observación (nuevo plazo).", "E2E «Administradora valida el comprobante»; Figuras {F:a-validar} y {F:c-observada}."],
        ["RF-17", "Cancelación desde el panel con devolución y nota de crédito.", "Pruebas de pagos-service (reembolso y nota de crédito)."],
        ["RF-18", "Reserva manual presencial o telefónica con pago inicial opcional.", "Figura {F:a-nueva}."],
        ["RF-19", "Asistencia ASISTIO o NO_ASISTIO desde 15 minutos antes del turno.", "Pruebas de reservas-service; indicador de inasistencia en «Hoy»."],
        ["RF-20", "CRUD de canchas, fotos y activación con efecto en los turnos.", "94 pruebas de catalogo-service; Figuras {F:a-canchas} y {F:a-fotos}."],
        ["RF-21", "Generación idempotente de turnos, precios por franja y bloqueos.", "Figura {F:a-agenda}."],
        ["RF-22", "Reportes de reservas e ingresos con gráficos y Excel.", "E2E «Reporte de ingresos» y «Excel»; Figura {F:a-reportes}."],
    ]
    i.tabla("rf", "Cumplimiento de los Requerimientos Funcionales",
            ["Código", "Implementación", "Verificación"], rf, [1.5, 7.5, 6],
            "Todos los requerimientos están implementados y en producción. Elaboración propia.", tam=19)
    i.p("Respecto de los requerimientos no funcionales, la Tabla {T:rnf} resume la evidencia reunida. Quince se cumplen "
        "con evidencia directa; tres se cumplen de forma parcial porque su criterio de verificación exige mediciones "
        "con usuarios reales, una prueba de carga o un periodo de monitoreo que excede el plazo del curso; y uno "
        "(RNF-05) queda pendiente. Estos casos se reconocen explícitamente y se retoman en las recomendaciones.")
    rnf = [
        ["RNF-01", "Cumple", "Diseño mobile-first; capturas a 390 px (Figura {F:movil-portal})."],
        ["RNF-02", "Parcial", "Flujo de tres pasos sin cuenta implementado; el tiempo promedio con usuarios reales no se midió."],
        ["RNF-03", "Cumple", "Mismos mensajes en español en Zod y Bean Validation; 400 con error por campo."],
        ["RNF-04", "Cumple", f"Latencias de {DATOS['latencias_estado']} en /api/estado y {DATOS['postman']['promedio_ms']} ms de promedio en la colección de Postman."],
        ["RNF-05", "Pendiente", "No se ejecutó una prueba de carga con 50 usuarios; solo pruebas de concurrencia de hasta 5 hilos y 100 emisiones."],
        ["RNF-06", "Cumple", "JWT con expiración de 8 h y roles; pruebas de 401 y 403 en todos los servicios."],
        ["RNF-07", "Cumple", "BCrypt de fuerza 12; HTTPS con Let's Encrypt y HSTS (Figura {F:caddy})."],
        ["RNF-08", "Cumple", "JPG o PNG por bytes iniciales, 5 MB y 200 px; E2E «Imagen falsa rechazada»."],
        ["RNF-09", "Cumple", "Consentimiento obligatorio, DNI enmascarado, política de privacidad y ningún dato de tarjeta almacenado."],
        ["RNF-10", "Cumple", "@Transactional y eventos publicados después del commit."],
        ["RNF-11", "Cumple", "Índice único de turno_activo y bloqueo pesimista; prueba concurrente."],
        ["RNF-12", "Parcial", "Verificaciones de salud, reinicio automático y reversión en el despliegue; la meta mensual de 99 % aún no se mide."],
        ["RNF-13", "Cumple", "Zona America/Lima en la JVM, MySQL y Jackson; pruebas con reloj fijado."],
        ["RNF-14", "Cumple", "Capas y principios SOLID (Tabla {T:solid})."],
        ["RNF-15", "Cumple", f"{total_pruebas()} pruebas; cobertura de 89 % a 96 % de instrucciones en los servicios (Tabla {{T:junit}})."],
        ["RNF-16", "Parcial", "Probado en Chrome de escritorio y con emulación de celular; Edge, Firefox, Safari e iOS no se probaron formalmente."],
        ["RNF-17", "Cumple", f"{DATOS['postman']['peticiones']} peticiones y {DATOS['postman']['aserciones']} aserciones de Postman sin fallos."],
        ["RNF-18", "Cumple", "Docker Compose ejecutado en el VPS, un entorno distinto al de desarrollo."],
        ["RNF-19", "Cumple", "Bitácora central e historial de cada reserva con fecha y responsable."],
    ]
    i.tabla("rnf", "Evidencia de Cumplimiento de los Requerimientos No Funcionales",
            ["Código", "Estado", "Evidencia"], rnf, [1.5, 1.8, 11.7],
            "Cumple = evidencia directa; Parcial = implementado, pero sin la medición completa del criterio de la "
            "Tabla 9; Pendiente = sin evidencia todavía. Elaboración propia.", tam=19)
    i.p("Además de lo planificado, durante el desarrollo se incorporaron funcionalidades que acercan el sistema a la "
        "operación real de un complejo deportivo y que no figuraban en los requerimientos originales. Se resumen en la "
        "Tabla {T:adicionales}.")
    i.tabla("adicionales", "Funcionalidades Adicionales a los Requerimientos Originales",
            ["Funcionalidad", "Descripción"],
            [["Consulta RENIEC y SUNAT", "Autocompletado de nombres por DNI y de razón social por RUC mediante Factiliza, con caché y llenado manual si el servicio falla."],
             ["Notificaciones por WhatsApp y correo", "Cada cambio de la reserva genera dos avisos con la constancia o el comprobante en PDF; bandeja con vista previa y reintentos."],
             ["Recordatorio automático", "Tarea programada que avisa dos horas antes del partido con el saldo y la ubicación; se cancela si la reserva se cancela."],
             ["Pasarela Alcanfores Pay (beta)", "Pago con tarjeta y Yape simulados, 3-D Secure, expiración a los 15 minutos y webhook firmado con HMAC-SHA256 e idempotente."],
             ["Comprobantes electrónicos simulados", "Boleta, factura y nota de crédito con IGV incluido, importe en letras, QR, PDF y XML UBL 2.1, sin validez tributaria."],
             ["Reseñas verificadas", "Solo califica quien jugó, una reseña por reserva, con respuesta y moderación del complejo."],
             ["Fotos de las canchas", "Galería con visor, hasta diez fotos por cancha y validación del contenido de cada imagen."],
             ["Rol de recepción", "Perfil con permisos reducidos para el personal que atiende reservas y pagos."],
             ["Bitácora y estado del sistema", "Auditoría central de todas las acciones y monitoreo de la salud de cada microservicio."],
             ["Mercado Pago (sandbox)", "Integración con Checkout Pro y webhook firmado, lista para activarse con credenciales de producción."]],
            [4.3, 10.7], "Elaboración propia.")

    i.en("7.2. Pruebas y Validaciones")
    loc = DATOS["e2e_local"]
    pm = DATOS["postman"]
    i.p("La calidad del sistema se verificó en cuatro niveles complementarios, resumidos en la Tabla {T:resumen-pruebas}. "
        f"En el nivel unitario y de integración se ejecutan {total_pruebas()} pruebas de JUnit 5 y Mockito en los ocho "
        "módulos, sin fallos, con una cobertura de instrucciones entre 89 % y 96 % en los servicios de dominio y el "
        "gateway, por encima de la meta de 70 % fijada en RNF-15 (Tabla {T:junit}). En el nivel de API, la colección de "
        f"Postman ejecutó {pm['peticiones']} peticiones y {pm['aserciones']} aserciones sin fallos. En el nivel de sistema, "
        f"la prueba de punta a punta superó {loc['correctas']} de {loc['total']} comprobaciones en el entorno local. "
        + texto_e2e_produccion())
    i.tabla("resumen-pruebas", "Resumen de las Pruebas y Validaciones Realizadas",
            ["Nivel", "Herramienta", "Alcance", "Resultado"],
            [["Unitario e integración", "JUnit 5, Mockito, AssertJ, MockMvc, JaCoCo", "8 módulos del back-end", f"{total_pruebas()} pruebas, 0 fallos"],
             ["Concurrencia", "JUnit 5 con hilos y MySQL/MariaDB real", "Doble reserva (2 y 5 hilos) y numeración de comprobantes (100 emisiones)", "Una sola reserva vigente; numeración sin huecos ni duplicados"],
             ["API", "Postman y Newman", "40 peticiones en 6 carpetas", f"{pm['aserciones']} aserciones, {pm['fallos']} fallos, {pm['promedio_ms']} ms de promedio"],
             ["Sistema (punta a punta)", "Script Python contra el gateway", "Ciclo completo de reserva, pago, saga y panel",
              f"Local {loc['correctas']}/{loc['total']}; producción {DATOS['e2e_produccion']['correctas']}/{DATOS['e2e_produccion']['total']}"],
             ["Front-end", "TypeScript estricto, ESLint y compilación", "Toda la SPA", "Compilación sin errores ni advertencias"],
             ["Interfaz", "Capturas automatizadas con Playwright (Chrome)", "120 pantallas del portal y 82 del panel", "Revisión visual de estados, errores y diseño móvil"]],
            [2.8, 3.8, 4.4, 4], "Elaboración propia a partir de los reportes del repositorio.", tam=19)
    i.p("La prueba más relevante para el problema del complejo es la de doble reserva. En la situación manual descrita "
        "en la historia de usuario HU-01, nada impedía anotar el mismo turno para dos equipos; en el sistema, cuando "
        "dos o cinco clientes intentan reservar el mismo turno en el mismo instante, siempre se registra una única "
        "reserva y los demás reciben un mensaje claro para elegir otro horario. Del mismo modo, las validaciones "
        "comprobaron que ningún dato inválido llega a la base de datos aunque se envíe directamente a la API sin pasar "
        "por el formulario.")

    i.en("7.3. Despliegue Exitoso")
    i.p(f"El sistema está publicado y en funcionamiento en {DATOS['sitio']} y su código se encuentra en "
        f"{DATOS['repositorio']}. Los diez contenedores corren en el VPS con estado saludable (Figura {{F:compose-ps}}), "
        "los siete servicios Java están registrados en Eureka (Figura {F:eureka}) y la consulta pública /api/estado "
        f"del 18 de noviembre de 2026 reportó todos los servicios en estado UP con latencias de {DATOS['latencias_estado']}. "
        "El sitio responde por HTTPS con certificado válido, y cada cambio en la rama main pasa por las pruebas y se "
        "despliega automáticamente con reversión en caso de falla.")
    captura(i, "eureka", "Panel de Eureka con los Microservicios Registrados en Producción",
            (CAP_DESPLIEGUE / "14 Eureka - panel de servicios registrados (produccion).png", (0, 0, 1440, 1150)),
            "El panel se consultó por un túnel SSH, porque el puerto 8761 solo escucha dentro del VPS. Captura del 18 "
            "de noviembre de 2026.")


def capitulo_8(i: Informe):
    i.en("8. Discusión")
    i.p("En este capítulo se interpretan los resultados a la luz del marco teórico y de los antecedentes, se analizan "
        "las dificultades que surgieron al implementar y desplegar el sistema, y se contrastan los logros con los "
        "objetivos planteados en el apartado 4.5.")
    i.en("8.1. Análisis e Interpretación de los Resultados")
    i.p("El resultado central es que el sistema elimina técnicamente la causa de los cruces de horario. Hermawan y "
        "Suharso (2025) mostraron que, sin control de concurrencia, todas las solicitudes simultáneas sobre un mismo "
        "recurso eran aceptadas; las pruebas del sistema reprodujeron ese escenario con dos y cinco solicitudes "
        "simultáneas y obtuvieron siempre una sola reserva vigente. La diferencia respecto de ese antecedente es que la "
        "garantía no depende solo de la transacción, sino también de una restricción de la base de datos, lo que "
        "concuerda con la estrategia de varias defensas del apartado 5.3.6 y con la recomendación de Silberschatz et "
        "al. (2020) de declarar las reglas de integridad en el propio esquema.")
    i.p("En cuanto a la gestión del proceso, el sistema digitaliza las tareas que Pariamachi Vega y Rubio Añasco (2024) y "
        "Quiroz Soriano y Quiroz Soriano (2023) identificaron como fuente de errores: el registro, la confirmación y el "
        "seguimiento de las reservas. La regla de negocio central, que solo confirma una reserva con pago validado, "
        "quedó reforzada con un plazo de pago y su compensación automática: una reserva sin pago se cancela sola y "
        "libera el turno, lo que ataca directamente las reservas no concretadas descritas en el apartado 4.1. Las "
        "notificaciones y el recordatorio previo responden a la falta de claridad que relata la historia HU-02, y la "
        "consulta pública de disponibilidad, a la lentitud de la atención de HU-03.")
    i.p("La adopción de microservicios merece una lectura crítica. Para un complejo con cuatro canchas, una sola "
        "aplicación por capas habría sido suficiente; Newman (2021) advierte que los microservicios agregan costos de "
        "operación, de red y de consistencia que solo se justifican cuando aportan independencia o escalabilidad. En "
        "este proyecto la decisión tuvo además un propósito formativo: aplicar en un caso real la comunicación por "
        "eventos, la consistencia eventual y el despliegue de varios servicios. Los resultados muestran que ese costo "
        "fue manejable, con cerca de 2,4 GiB de memoria en producción y un flujo de despliegue automatizado, y que la "
        "separación permitió desarrollar y probar cada servicio en paralelo. Por último, los indicadores de la Tabla 5 "
        "ya pueden calcularse con los reportes del sistema, pero los valores que se muestran provienen de datos de "
        "demostración; su medición con la operación real del complejo queda como trabajo posterior.")
    i.en("8.2. Dificultades Encontradas y Soluciones Técnicas Implementadas")
    i.p("La Tabla {T:dificultades} resume las principales dificultades técnicas y cómo se resolvieron. Varias de ellas "
        "solo aparecieron al integrar los servicios o al publicar el sistema, lo que confirma el valor de las pruebas "
        "de punta a punta y del despliegue temprano.")
    i.tabla("dificultades", "Dificultades Encontradas y Soluciones Aplicadas",
            ["Dificultad", "Causa", "Solución"],
            [["Diferencias entre MariaDB local y MySQL 8.4", "En la computadora de desarrollo se usó MariaDB 10.4 de XAMPP, que guarda las columnas JSON como LONGTEXT; la validación del esquema de Hibernate fallaba.", "SQL compatible con ambos motores, un convertidor de atributos y un resolutor de dialecto que solo actúa con MariaDB; la integración continua prueba contra MySQL 8.4 real."],
             ["Fotos de las canchas inaccesibles en producción", "En nginx, la regla de imágenes por extensión (.jpg, .png) tenía prioridad sobre el proxy /api/, y las fotos se buscaban como archivos estáticos.", "Se marcó la ubicación /api/ con el modificador ^~ para que gane a las expresiones regulares (commit e18be07)."],
             ["IP real del cliente detrás de varios proxies", "Entre el navegador y el servicio hay tres saltos (Caddy, nginx y gateway); Spring Cloud Gateway eliminaba las cabeceras X-Forwarded y la bitácora registraba la IP del gateway.", "Caddy escribe la IP real, nginx la reenvía sin agregar otra y el gateway la acepta como proxy confiable; un filtro deja un único valor que no puede falsearse."],
             ["Tiempos y fallas del proveedor de RENIEC", "Las consultas a Factiliza pueden demorar o fallar, y en las pruebas las consultas de DNI respondieron 503.", "Tiempo máximo de 5 s, caché de 30 días y un aviso que permite escribir los datos a mano: la reserva nunca depende del proveedor."],
             ["RUC ficticio del emisor existente en SUNAT", "El RUC 20612345679, elegido por ser válido por módulo 11, correspondía a una empresa real con baja de oficio.", "Se reemplazó por el RUC 20000000001, válido y no asignado, y los comprobantes declaran que son simulados."],
             ["Consistencia eventual entre servicios", "Los eventos de RabbitMQ llegan unos milisegundos después de la respuesta HTTP.", "Eventos después del commit, consumidores idempotentes, cola de mensajes muertos y estados intermedios visibles (por ejemplo, «En revisión»)."],
             ["Memoria limitada del servidor", "Siete JVM y MySQL en un VPS de 8 GB.", "Recolector serial, límite de memoria por contenedor y porcentaje máximo de la JVM; consumo medido de 2,4 GiB."],
             ["Rechazo CORS sin cuerpo en desarrollo", "El panel probado en otro puerto no estaba en la lista de orígenes y el gateway respondía 403 sin mensaje.", "Uso del proxy de Vite en desarrollo; en producción portal y API comparten origen."]],
            [3.4, 5.8, 5.8], "Elaboración propia a partir del registro de pendientes del proyecto (docs/PENDIENTES_BACK.md).",
            tam=19)
    i.p("Además de estos problemas, la integración del front-end con el back-end real dejó observaciones menores "
        "documentadas en el repositorio, como el estado de pago que se conserva en una reserva cancelada con devolución "
        "o la necesidad de repetir la elección de factura al reenviar un comprobante. Ninguna impide el funcionamiento "
        "del sistema y el portal ya las compensa en la interfaz, pero conviene corregirlas en el back-end.")
    i.en("8.3. Comparación con los Objetivos Planteados")
    i.p("La Tabla {T:objetivos} contrasta cada objetivo específico con su resultado. Los cinco se cumplieron; el quinto "
        "superó lo planteado, porque el sistema no solo se desplegó en la nube, sino que cuenta con despliegue "
        "automático, aunque sus pruebas de carga quedan pendientes.")
    i.tabla("objetivos", "Cumplimiento de los Objetivos Específicos",
            ["Objetivo específico", "Resultado obtenido", "Estado"],
            [["1. Analizar y modelar el proceso y la base de datos relacional", "Requerimientos, casos de uso y modelo de siete tablas (6.1 a 6.4), distribuido luego en cinco bases de datos por servicio.", "Cumplido"],
             ["2. Implementar la API RESTful con SOLID, manejo global de excepciones y seguridad JWT", "Siete servicios Spring Boot por capas, error estándar común, Spring Security con JWT y roles.", "Cumplido"],
             ["3. Desarrollar la SPA en React con TypeScript y reserva en tres pasos para móviles", "Portal y panel en React 18, asistente de tres pasos validado con Zod y diseño mobile-first.", "Cumplido"],
             ["4. Integrar front-end y back-end con HTTP asíncrono y CORS", "Axios con interceptores, gateway con CORS centralizado y prueba de punta a punta.", "Cumplido"],
             ["5. Validar y desplegar en la nube con pruebas que garanticen el bloqueo de turnos", f"{total_pruebas()} pruebas, prueba concurrente de doble reserva, despliegue en VPS con Docker y CI/CD.", "Cumplido"]],
            [5, 8, 2], "Elaboración propia.")


def capitulo_9_10(i: Informe):
    i.en("9. Conclusiones")
    i.p("Se desarrolló y puso en producción un sistema web para la gestión de reservas de canchas y la validación de "
        "pagos digitales del Complejo Deportivo Los Alcanfores, que permite consultar la disponibilidad en tiempo real, "
        "reservar en tres pasos desde el celular y confirmar el turno solo con un pago validado. Con ello se cumplió el "
        "objetivo general: el sistema impide la duplicidad de turnos y reduce las reservas no concretadas mediante el "
        "adelanto obligatorio y la cancelación automática de las reservas sin pago.")
    i.numerada([
        "Respecto del primer objetivo, el análisis de las historias de usuario permitió definir 22 requerimientos "
        "funcionales, 19 no funcionales y 15 casos de uso, y un modelo relacional cuya columna generada turno_activo "
        "con restricción de unicidad resultó ser la pieza clave para evitar la doble reserva desde la base de datos.",
        "Respecto del segundo objetivo, la API se implementó con Spring Boot en siete microservicios organizados por "
        "capas y principios SOLID, con un formato de error común en español y seguridad stateless con JWT, BCrypt y "
        "roles; la combinación de bloqueo pesimista y restricción única garantizó una sola reserva vigente por turno "
        "incluso con solicitudes simultáneas.",
        "Respecto del tercer objetivo, la SPA en React 18 con TypeScript y Tailwind CSS reprodujo el prototipo aprobado, "
        "ofrece un flujo de reserva de tres pasos sin registro y valida cada campo con los mismos mensajes que el "
        "servidor, tanto en escritorio como en celular.",
        "Respecto del cuarto objetivo, la integración mediante Axios, el API Gateway con CORS centralizado y la "
        "comunicación por eventos de RabbitMQ funcionaron de manera coordinada, como lo demostró la prueba de punta a "
        "punta que recorre el ciclo completo de una reserva y su compensación.",
        f"Respecto del quinto objetivo, {total_pruebas()} pruebas automatizadas sin fallos, con cobertura superior al 89 % "
        "en los servicios de dominio, validaron las reglas del sistema, y el despliegue en un VPS con Docker Compose, "
        "HTTPS y CI/CD con GitHub Actions dejó el sistema disponible públicamente con actualizaciones automáticas.",
    ])
    i.p("En conjunto, el proyecto muestra que los principios estudiados en el curso, como la arquitectura por capas, REST, "
        "las transacciones ACID y la autenticación stateless, siguen siendo la base de un sistema distribuido, y que su "
        "correcta aplicación permite resolver un problema concreto de un negocio local.")
    i.en("10. Recomendaciones")
    i.p("A partir de los resultados y de las limitaciones identificadas, se plantean las siguientes recomendaciones para "
        "la evolución del sistema:")
    i.vinetas([
        "^^Facturación electrónica real.^^ Antes de usar el sistema con fines tributarios, reemplazar los comprobantes "
        "simulados por la emisión a través de un proveedor de servicios electrónicos (PSE) o del sistema de emisión de "
        "SUNAT, con el RUC real del complejo, firma digital y envío de los XML (SUNAT, s. f.).",
        "^^Pagos en producción.^^ Activar Mercado Pago con credenciales de producción y retirar la pasarela beta, que hoy "
        "solo sirve para demostrar el flujo de pago con tarjeta.",
        "^^Pruebas de carga.^^ Ejecutar pruebas con herramientas como JMeter o k6 para comprobar RNF-05 (50 usuarios "
        "concurrentes) y ajustar los límites de memoria y conexiones según los resultados.",
        "^^Observabilidad.^^ Centralizar registros y métricas (por ejemplo, Prometheus y Grafana) y programar alertas, de "
        "modo que pueda medirse la disponibilidad mensual exigida en RNF-12.",
        "^^Medición con usuarios reales.^^ Aplicar los indicadores de la Tabla 5 durante un periodo de operación real, "
        "incluida una encuesta de satisfacción, para cuantificar la mejora respecto del proceso manual.",
        "^^Respaldo y recuperación.^^ Programar copias de seguridad automáticas de MySQL y de las fotos fuera del VPS, y "
        "documentar el procedimiento de restauración.",
        "^^Pruebas automatizadas de la interfaz.^^ Convertir las capturas de Playwright en pruebas de extremo a extremo que "
        "se ejecuten en la integración continua y cubran también Firefox y Safari (RNF-16).",
        "^^Correcciones menores.^^ Resolver las observaciones del registro de pendientes, como el estado de pago de las "
        "reservas canceladas con devolución y la conservación del tipo de comprobante al reenviar un pago.",
    ])


# Referencias nuevas (APA 7). Cada una: (lista de (texto, cursiva), idioma)
REFERENCIAS_NUEVAS = [
    ([("Axios. (s. f.). ", False), ("Axios documentation", True), (". https://axios-http.com/docs/intro", False)], "en-US"),
    ([("Beck, K. (2002). ", False), ("Test-driven development: By example", True), (". Addison-Wesley.", False)], "en-US"),
    ([("Caddy. (s. f.). ", False), ("Caddy documentation", True), (". https://caddyserver.com/docs/", False)], "en-US"),
    ([("Evans, E. (2003). ", False), ("Domain-driven design: Tackling complexity in the heart of software", True), (". Addison-Wesley.", False)], "en-US"),
    ([("Evolution API. (s. f.). ", False), ("Evolution API documentation", True), (". https://doc.evolution-api.com/", False)], "en-US"),
    ([("Garcia-Molina, H. y Salem, K. (1987). Sagas. ", False), ("ACM SIGMOD Record, 16", True), ("(3), 249–259. https://doi.org/10.1145/38714.38742", False)], "en-US"),
    ([("Hohpe, G. y Woolf, B. (2003). ", False), ("Enterprise integration patterns: Designing, building, and deploying messaging solutions", True), (". Addison-Wesley.", False)], "en-US"),
    ([("Humble, J. y Farley, D. (2010). ", False), ("Continuous delivery: Reliable software releases through build, test, and deployment automation", True), (". Addison-Wesley.", False)], "en-US"),
    ([("Internet Security Research Group. (s. f.). ", False), ("Let's Encrypt documentation", True), (". https://letsencrypt.org/docs/", False)], "en-US"),
    ([("Kleppmann, M. (2017). ", False), ("Designing data-intensive applications: The big ideas behind reliable, scalable, and maintainable systems", True), (". O'Reilly Media.", False)], "en-US"),
    ([("Lewis, J. y Fowler, M. (2014, 25 de marzo). ", False), ("Microservices", True), (". martinfowler.com. https://martinfowler.com/articles/microservices.html", False)], "en-US"),
    ([("Mell, P. y Grance, T. (2011). ", False), ("The NIST definition of cloud computing", True), (" (NIST Special Publication 800-145). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-145", False)], "en-US"),
    ([("Mercado Pago. (s. f.). ", False), ("Checkout Pro", True), (". Mercado Pago Developers. https://www.mercadopago.com.pe/developers/es/docs/checkout-pro/landing", False)], "es-PE"),
    ([("Mockito. (s. f.). ", False), ("Mockito framework site", True), (". https://site.mockito.org/", False)], "en-US"),
    ([("Mountainminds GmbH & Co. KG. (s. f.). ", False), ("JaCoCo Java code coverage library", True), (". https://www.jacoco.org/jacoco/", False)], "en-US"),
    ([("Newman, S. (2021). ", False), ("Building microservices: Designing fine-grained systems", True), (" (2.ª ed.). O'Reilly Media.", False)], "en-US"),
    ([("Nygard, M. T. (2018). ", False), ("Release it! Design and deploy production-ready software", True), (" (2.ª ed.). Pragmatic Bookshelf.", False)], "en-US"),
    ([("Postman. (s. f.). ", False), ("Run and test collections from the command line using Newman CLI", True), (". Postman Docs. https://learning.postman.com/docs/collections/using-newman-cli/command-line-integration-with-newman/", False)], "en-US"),
    ([("RabbitMQ. (s. f.). ", False), ("RabbitMQ documentation", True), (". https://www.rabbitmq.com/docs", False)], "en-US"),
    ([("React Hook Form. (s. f.). ", False), ("React Hook Form documentation", True), (". https://react-hook-form.com/docs", False)], "en-US"),
    ([("React Router. (s. f.). ", False), ("React Router documentation", True), (". https://reactrouter.com/", False)], "en-US"),
    ([("Redgate. (s. f.). ", False), ("Flyway documentation", True), (". https://documentation.red-gate.com/flyway", False)], "en-US"),
    ([("Resilience4j. (s. f.). ", False), ("Resilience4j documentation", True), (". https://resilience4j.readme.io/docs", False)], "en-US"),
    ([("Richardson, C. (2018). ", False), ("Microservices patterns: With examples in Java", True), (". Manning Publications.", False)], "en-US"),
    ([("Spring. (s. f.-c). ", False), ("Spring Cloud", True), (". https://spring.io/projects/spring-cloud", False)], "en-US"),
    ([("Superintendencia Nacional de Aduanas y de Administración Tributaria. (s. f.). ", False), ("Comprobantes de pago electrónicos", True), (". https://cpe.sunat.gob.pe/", False)], "es-PE"),
    ([("The JUnit Team. (s. f.). ", False), ("JUnit 5 user guide", True), (". https://junit.org/junit5/docs/current/user-guide/", False)], "en-US"),
    ([("Zod. (s. f.). ", False), ("Zod documentation", True), (". https://zod.dev/", False)], "en-US"),
]


def clave_orden(texto: str) -> str:
    t = unicodedata.normalize("NFD", texto.lower())
    return "".join(c for c in t if unicodedata.category(c) != "Mn")


def referencias(i: Informe):
    titulo = i.buscar("11. Referencias Bibliográficas")
    existentes = []
    e = titulo.getnext()
    while e is not None and not i.es_titulo(e):
        sig = e.getnext()
        if e.tag == w("p") and texto_plano(e).strip():
            existentes.append(e)
        e = sig
    nuevas = []
    for segmentos, idioma in REFERENCIAS_NUEVAS:
        p = el("p")
        ppr = sub(p, "pPr")
        sub(ppr, "ind", left=720, hanging=720)
        for texto, cursiva in segmentos:
            r = sub(p, "r")
            rpr = i._rpr(r, cursiva=cursiva)
            rpr.find("w:lang", NS).set(w("val"), idioma)
            t = sub(r, "t"); t.text = texto
            t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
        nuevas.append(p)
    todas = existentes + nuevas
    for p in existentes:
        p.getparent().remove(p)
    todas.sort(key=lambda p: clave_orden(texto_plano(p)))
    ancla = titulo
    for p in todas:
        ancla.addnext(p)
        ancla = p
    return len(existentes), len(nuevas)


def capitulo_12(i: Informe):
    i.en("12. Anexos")
    i.p("Los anexos reúnen la información complementaria del sistema: la ubicación del código fuente, las "
        "configuraciones principales, capturas adicionales del despliegue y del funcionamiento, y el manual de usuario "
        "para el cliente y para el personal del complejo.")

    i.en("12.1. Código Fuente")
    i.p(f"El código fuente completo está disponible en el repositorio público {DATOS['repositorio']}, sin contraseñas ni "
        "tokens, que se configuran como variables de entorno en el servidor. El sistema en funcionamiento se encuentra "
        f"en {DATOS['sitio']} y el panel de administración en {DATOS['panel']}. La Figura {{F:arbol}} muestra la "
        "estructura del repositorio y la Tabla {T:archivos} indica dónde se encuentran los archivos citados en este "
        "informe.")
    i.codigo("arbol", "Estructura del Repositorio del Sistema", """
alcanfores/
├── backend/                     Proyecto Maven multimódulo (Java 17, Spring Boot 3.5)
│   ├── pom.xml                  Padre: versiones de Spring Boot y Spring Cloud
│   ├── Dockerfile               Imagen de dos etapas para cualquier microservicio
│   ├── comun/                   Librería compartida: errores, JWT, validadores
│   ├── discovery-server/        Eureka (8761)
│   ├── api-gateway/             Spring Cloud Gateway (8086)
│   ├── auth-service/            Usuarios, login JWT y bitácora (8091)
│   ├── catalogo-service/        Canchas, fotos, reseñas y configuración (8092)
│   ├── reservas-service/        Turnos, reservas, clientes y reportes (8093)
│   ├── pagos-service/           Pagos, pasarela beta y comprobantes (8094)
│   └── notificaciones-service/  Correo, WhatsApp y recordatorios (8095)
├── frontend/                    SPA React 18 + TypeScript + Vite + Tailwind CSS
│   └── src/   api, componentes, contexto, esquemas, hooks, paginas, rutas
├── deploy/                      docker-compose.yml, desplegar.sh, nginx, Caddy y MySQL
├── docs/                        SPEC.md, servicios, Postman, pruebas E2E y capturas
└── .github/workflows/ci-cd.yml  Integración y despliegue continuos""", "Elaboración propia a partir del repositorio.")
    i.tabla("archivos", "Ubicación de los Archivos Clave en el Repositorio",
            ["Archivo", "Contenido"],
            [["docs/SPEC.md", "Especificación técnica: arquitectura, endpoints, eventos, validaciones y mensajes."],
             ["docs/servicios/*.md", "Documentación de cada microservicio: tablas, endpoints, decisiones y pruebas."],
             ["backend/*/src/main/resources/db/migration/", "Migraciones de Flyway con el esquema de cada base de datos."],
             ["backend/reservas-service/.../servicio/impl/OperacionesReserva.java", "Registro transaccional con bloqueo pesimista."],
             ["backend/comun/.../seguridad/", "Filtro JWT, configuración común de seguridad y filtro de llamadas internas."],
             ["backend/api-gateway/src/main/resources/application.yml", "Rutas, tiempos máximos y circuit breakers del gateway."],
             ["frontend/src/api/cliente.ts", "Cliente Axios con interceptores."],
             ["frontend/src/esquemas/", "Esquemas de validación de Zod."],
             ["deploy/docker-compose.yml y deploy/desplegar.sh", "Definición de los contenedores y script de despliegue con reversión."],
             ["docs/postman/ y docs/pruebas/", "Colección de Postman, prueba de punta a punta y sus resultados."]],
            [6.5, 8.5], "Elaboración propia.", tam=19)

    i.en("12.2. Configuraciones")
    i.p("Este apartado presenta las configuraciones que permiten reproducir el despliegue. La configuración de cada "
        "servicio Spring Boot sigue el modelo de la Figura {F:cod-yml}, y el flujo de integración continua, el de la "
        "Figura {F:cod-workflow}. La Figura {F:cod-compose} muestra la definición de un microservicio en "
        "docker-compose.yml; la Figura {F:cod-env}, la plantilla de variables de entorno que se copia como deploy/.env "
        "en el servidor; y la Figura {F:cod-nginx}, la configuración de nginx que sirve la SPA y reenvía la API.")
    i.codigo("cod-compose", "Definición de un Microservicio en docker-compose.yml", """
x-java: &java
  restart: unless-stopped
  networks: [interna]
  depends_on:
    discovery-server: { condition: service_healthy }
    mysql: { condition: service_healthy }
    rabbitmq: { condition: service_healthy }

services:
  reservas-service:
    <<: *java
    build: { context: ../backend, args: { MODULO: reservas-service } }
    image: alcanfores/reservas-service:${VERSION:-local}
    environment:
      <<: *entorno-comun  # TZ, DB_HOST, EUREKA_URL, RABBIT_*, JWT_SECRETO...
      SERVER_PORT: "8093"
      DB_USUARIO: reservas_svc
      DB_CLAVE: ${DB_CLAVE_RESERVAS}
    healthcheck: *salud                  # curl a /actuator/health cada 15 s
    deploy: { resources: { limits: { memory: 448m } } }""", "Fragmento de deploy/docker-compose.yml.")
    i.codigo("cod-env", "Plantilla de Variables de Entorno del Servidor", """
# Copiar como deploy/.env en el servidor y reemplazar TODOS los valores.
# Nunca subir .env al repositorio.
# Generar secretos con:  openssl rand -base64 48
FRONT_URL=https://alcanfores.dbltech.tech
PUBLIC_API_URL=https://alcanfores.dbltech.tech
MYSQL_ROOT_CLAVE=cambiar
DB_CLAVE_AUTH=cambiar          # y una clave por cada servicio
RABBIT_USUARIO=alcanfores
RABBIT_CLAVE=cambiar
JWT_SECRETO=cambiar-por-un-secreto-de-al-menos-32-bytes
INTERNO_TOKEN=cambiar
PASARELA_BETA_SECRETO=cambiar
# Integraciones opcionales (vacías = modo simulado)
FACTILIZA_TOKEN=
MP_ACCESS_TOKEN=
MAIL_HOST=
EVOLUTION_URL=
EVOLUTION_APIKEY=""", "Fragmento de deploy/.env.example; el archivo real del servidor no se incluye por contener secretos.")
    i.codigo("cod-nginx", "Configuración de nginx para la SPA y el Proxy de la API", """
server {
  listen 80;
  root /usr/share/nginx/html;

  # «^~» gana a la regla de imágenes estáticas (/api/catalogo/fotos/*.jpg).
  location ^~ /api/ {
    proxy_pass http://api-gateway:8086;
    proxy_set_header X-Forwarded-For $http_x_forwarded_for; # IP real (de Caddy)
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 6m;
  }
  location /assets/ {  # archivos con hash de Vite: caché de un año
    add_header Cache-Control "public, max-age=31536000, immutable";
  }
  location / {                              # SPA: React Router resuelve la ruta
    add_header Cache-Control "no-cache";
    try_files $uri $uri/ /index.html;
  }
}""", "Fragmento de deploy/web/nginx.conf.")

    i.en("12.3. Capturas de Pantalla")
    i.p("Se incluyen capturas complementarias del procedimiento de despliegue descrito en el apartado 6.12.2: los "
        "registros DNS del subdominio (Figura {F:x-dns}), la clonación del repositorio (Figura {F:x-clone}), las "
        "variables de entorno con sus valores ocultos (Figura {F:x-env}), la primera ejecución del script de "
        "despliegue (Figura {F:x-desplegar}), las imágenes construidas (Figura {F:x-imagenes}), el exchange y las colas "
        "de RabbitMQ en su consola web (Figuras {F:x-exchange} y {F:x-colas}), la documentación OpenAPI de "
        "pagos-service (Figura {F:x-swagger-pagos}) y los secretos cifrados de GitHub (Figura {F:x-secretos}).")
    i.p("También se agregan pantallas del sistema que no se mostraron en el apartado 6.7: una factura y una nota de "
        "crédito simuladas (Figuras {F:x-factura} y {F:x-nc}), la política de privacidad (Figura {F:x-privacidad}), la "
        "moderación de reseñas (Figura {F:x-resenas-admin}), la configuración del complejo (Figura {F:x-config}), los "
        "clientes con sus métricas (Figura {F:x-clientes}) y la vista del rol RECEPCION (Figura {F:x-recepcion}). El "
        "conjunto completo de 202 capturas del portal y del panel se encuentra en docs/capturas-reales del repositorio.")
    fecha = "Captura del 18 de noviembre de 2026."
    dep = [
        ("x-dns", "Registros DNS A y AAAA del Subdominio en el Panel de Hostinger", "03 hPanel - registros A y AAAA creados.jpg", "Los registros apuntan a la dirección IPv4 e IPv6 del VPS."),
        ("x-clone", "Clonación del Repositorio en el VPS", "05 VPS - git clone del repositorio.png", "El código se descarga en /opt/alcanfores."),
        ("x-env", "Variables de Entorno del Servidor con los Valores Ocultos", "06 VPS - variables de entorno con secretos ocultos.png", "Los valores se reemplazaron por asteriscos al mostrarlos; nunca se publican."),
        ("x-desplegar", "Primera Ejecución del Script de Despliegue", "07 VPS - primer despliegue (desplegar.sh).png", "Construcción de las imágenes y arranque ordenado de los contenedores hasta quedar saludables."),
        ("x-imagenes", "Imágenes Docker Construidas para Cada Microservicio", "09 VPS - imagenes Docker construidas.png", "Cada imagen se etiqueta con el commit desplegado."),
        ("x-exchange", "Exchange alcanfores.eventos en la Consola de RabbitMQ", "15 RabbitMQ - exchange alcanfores.eventos y sus enlaces (desarrollo).png", "Consola de administración en el entorno de desarrollo."),
        ("x-colas", "Colas de Cada Microservicio en la Consola de RabbitMQ", "16 RabbitMQ - colas de cada microservicio (desarrollo).png", "Colas durables con su consumidor; entorno de desarrollo."),
        ("x-swagger-pagos", "Documentación OpenAPI de pagos-service", "18 Swagger - API de pagos-service (OpenAPI).png", "Endpoints de pagos, pasarela, webhooks y comprobantes."),
        ("x-secretos", "Secretos Cifrados del Despliegue en GitHub", "21 GitHub - secretos del despliegue (cifrados).jpg", "GitHub muestra solo el nombre de cada secreto, nunca su valor."),
    ]
    for clave, titulo, archivo, nota in dep:
        recorte = (0, 0, 1539, 1150) if clave == "x-desplegar" else None
        captura(i, clave, titulo, (CAP_DESPLIEGUE / archivo, recorte), nota + " " + fecha, alto_max_cm=12.5)
    extra = [
        ("x-factura", "Factura Electrónica Simulada Emitida a un RUC", portal("62-factura-pdf-1440", 900), "Pago con tarjeta en la pasarela beta; RUC del cliente consultado en SUNAT."),
        ("x-nc", "Nota de Crédito Simulada por Cancelación con Devolución", portal("63-nota-credito-pdf-1440", 900), "Referencia al comprobante que anula."),
        ("x-privacidad", "Política de Privacidad conforme a la Ley N.° 29733", portal("71-privacidad-1440", 1000), "Se enlaza desde el paso 2 y desde el pie de cada correo."),
        ("x-resenas-admin", "Moderación de Reseñas en el Panel", panel("37-resenas-1440", 900), "Promedios por cancha, filtros, ocultar y responder."),
        ("x-config", "Configuración del Complejo", panel("55-configuracion-1440", 1000), "Tarifas, horarios, plazos, datos de pago y de contacto; cada cambio se audita."),
        ("x-clientes", "Clientes con Métricas de Asistencia", panel("48-clientes-1440", 900), "Reservas, asistencias e inasistencias por cliente."),
        ("x-recepcion", "Vista del Rol RECEPCION con el Menú Reducido", panel("72-recepcion-hoy-1440", 900), "Solo aparecen las funciones permitidas a su rol."),
    ]
    for clave, titulo, fuente, nota in extra:
        captura(i, clave, titulo, fuente, nota + " " + NOTA_LOCAL, alto_max_cm=12.5)

    capitulo_manual(i)


def capitulo_manual(i: Informe):
    i.en("12.4. Manual de Usuario")
    i.p("Este manual explica, paso a paso, cómo usar el sistema de reservas del Complejo Deportivo Los Alcanfores. La "
        "primera parte está dirigida a los clientes que reservan una cancha y la segunda, al personal del complejo que "
        "usa el panel de administración. Las pantallas de escritorio citadas se encuentran en el apartado 6.7; aquí se "
        "agregan las vistas desde el celular, que es el medio más usado por los clientes.")
    i.sub4("Requisitos y acceso",
           f"Basta un navegador actualizado (Chrome, Edge, Firefox o Safari) en una computadora o un celular con acceso a "
           f"Internet. El portal se abre en {DATOS['sitio']} y no requiere crear una cuenta. El panel del personal está "
           f"en {DATOS['panel']} y requiere usuario y contraseña.")

    i.sub4("Manual del cliente: reservar una cancha", "Para reservar, siga estos pasos:")
    i.numerada([
        "Ingrese al portal y pulse «Reservar», o abra la ficha de una cancha y pulse «Reservar esta cancha» (Figuras "
        "{F:c-inicio} y {F:c-detalle}).",
        "Paso 1: elija la cancha, el día (hasta 14 días adelante) y un turno libre. Los turnos ocupados, en mantenimiento o "
        "que empiezan en menos de 30 minutos no se pueden elegir. Pulse «Continuar».",
        "Paso 2: escriba su DNI y pulse la lupa para completar sus nombres; si aparece el aviso de que RENIEC no está "
        "disponible, escríbalos a mano. Ingrese su celular (9 dígitos, empieza con 9) y su correo. Si necesita factura, "
        "elija «Factura», ingrese el RUC y pulse la lupa. Acepte los términos y la política de privacidad y pulse "
        "«Continuar».",
        "Paso 3: elija cómo pagar el adelanto (50 % del precio). Para Yape o Plin, haga el pago al número que se muestra, "
        "escriba el número de operación y suba la captura del comprobante (JPG o PNG, hasta 5 MB). Para tarjeta, elija "
        "«Alcanfores Pay» y continúe en la página de pago.",
        "Pulse «Confirmar reserva». Anote su código ALC-XXXXXX y descargue la constancia en PDF; también la recibirá por "
        "correo y WhatsApp. Su reserva quedará «En revisión» hasta que el complejo valide el pago.",
    ])
    i.p("Si al confirmar aparece el aviso «Alguien se adelantó», otra persona tomó ese turno segundos antes: elija otro "
        "horario y sus datos se conservarán. El adelanto debe enviarse dentro del plazo que indica la cuenta regresiva "
        "(20 minutos); si vence, la reserva se cancela automáticamente y el turno se libera. La Figura {F:m-pasos} "
        "muestra los pasos 2 y 3 desde un celular.")
    a = i.preparar(*movil("portal", "11-paso2-errores-390", 1688), nombre="m-paso2")
    b = i.preparar(*movil("portal", "17-paso3-plin-390", 1688), nombre="m-paso3")
    i.figura_par("m-pasos", "Pasos 2 y 3 de la Reserva desde el Celular", [a, b],
                 "Izquierda: datos con los mensajes de validación. Derecha: pago con Plin con número de operación e "
                 "imagen del comprobante. " + NOTA_LOCAL, ancho_cm=6.2, alto_max_cm=13.5,
                 rotulos=["Paso 2: datos", "Paso 3: pago con Plin"])

    i.sub4("Manual del cliente: pagar con tarjeta en la pasarela beta",
           "La pasarela Alcanfores Pay es una versión de prueba que no cobra dinero real. Ingrese los datos de una de las "
           "tarjetas de prueba de la Tabla {T:tarjetas} (cualquier titular y una fecha de vencimiento futura), pulse "
           "«Pagar» y, si la tarjeta pide verificación 3-D Secure, escriba el código indicado. También puede elegir la "
           "pestaña «Yape» de la pasarela con cualquier celular y el código de aprobación de prueba. Al aprobarse el "
           "pago, vuelva al portal: la reserva aparecerá confirmada con su comprobante (Figura {F:m-pasarela}).")
    i.tabla("tarjetas", "Tarjetas y Códigos de Prueba de la Pasarela Alcanfores Pay (beta)",
            ["Medio de prueba", "Número o código", "Resultado"],
            [["Visa", "4111 1111 1111 1111", "Aprobada"],
             ["Mastercard", "5555 5555 5555 4444", "Aprobada"],
             ["American Express", "3782 822463 10005", "Aprobada"],
             ["Visa con 3-D Secure", "4000 0027 6000 3184 · código 123456", "Aprobada tras la verificación (otro código la rechaza)"],
             ["Visa sin fondos", "4000 0000 0000 0002", "Rechazada: fondos insuficientes"],
             ["Visa bloqueada", "4000 0000 0000 9995", "Rechazada: tarjeta bloqueada"],
             ["Visa vencida", "4000 0000 0000 0069", "Rechazada: tarjeta vencida"],
             ["Yape beta", "Cualquier celular · código 123456 (000000 rechaza)", "Aprobado o rechazado por saldo insuficiente"]],
            [3.5, 6.5, 5], "Para todas las tarjetas: CVV 123 (Amex: 1234) y cualquier fecha de vencimiento futura. Estos "
            "datos también se muestran en el recuadro «Modo beta» de la propia pasarela. Elaboración propia.")
    a = i.preparar(*movil("portal", "20-pasarela-390", 1688), nombre="m-pasarela")
    b = i.preparar(*movil("portal", "26-pasarela-3ds-390", 1688), nombre="m-3ds")
    i.figura_par("m-pasarela", "Pasarela Alcanfores Pay (beta) desde el Celular", [a, b],
                 "Izquierda: datos de la tarjeta. Derecha: verificación 3-D Secure. " + NOTA_LOCAL, ancho_cm=6.2,
                 alto_max_cm=13.5, rotulos=["Datos de la tarjeta", "3-D Secure"])

    i.sub4("Manual del cliente: consultar, corregir o cancelar la reserva", "Desde «Mi reserva» puede:")
    i.numerada([
        "Consultar el estado: escriba el código ALC-XXXXXX y su DNI y pulse «Consultar». Verá el estado, la línea de "
        "tiempo, los pagos y los comprobantes en PDF y XML (Figura {F:m-mireserva}).",
        "Reenviar el comprobante: si la reserva aparece «Observado», lea el motivo, pulse «Reenviar comprobante», suba una "
        "nueva captura con el número de operación correcto y confirme antes del nuevo plazo.",
        "Cancelar: pulse «Cancelar reserva», escriba el motivo (mínimo 10 caracteres) y confirme. Si pagó y faltan 24 horas "
        "o más, se registra la devolución y se emite una nota de crédito; si falta menos, no hay devolución.",
        "Calificar la cancha: después de jugar, pulse «Calificar la cancha» (o use el enlace del mensaje que recibe), "
        "elija de 1 a 5 estrellas y escriba un comentario de 10 a 500 caracteres sin enlaces.",
    ])
    a = i.preparar(*movil("portal", "40-mi-reserva-consulta-390", 1688), nombre="m-consulta")
    b = i.preparar(*movil("portal", "43-mi-reserva-en-revision-390", 1688), nombre="m-revision")
    i.figura_par("m-mireserva", "«Mi Reserva» desde el Celular", [a, b],
                 "Izquierda: consulta con código y DNI. Derecha: reserva en revisión con su línea de tiempo. "
                 + NOTA_LOCAL, ancho_cm=6.2, alto_max_cm=13.5, rotulos=["Consulta", "Reserva en revisión"])

    i.sub4("Manual del administrador: ingresar al panel",
           "Abra la dirección del panel, escriba su usuario y contraseña y pulse «Ingresar» (Figura {F:a-login}). La "
           "sesión dura ocho horas; al vencer, el sistema le pedirá ingresar de nuevo. Tras cinco intentos fallidos la "
           "cuenta se bloquea 15 minutos. Para cambiar su contraseña use «Mi cuenta» en el menú de usuario. Las cuentas "
           "de demostración se listan en la Tabla {T:cuentas}; por seguridad, sus contraseñas de producción no se "
           "incluyen en este informe y se entregan por separado al docente.")
    i.tabla("cuentas", "Cuentas de Demostración del Panel",
            ["Usuario", "Nombre", "Rol", "Uso previsto"],
            [["rhuaman", "Rosa Huamán", "ADMIN", "Administradora del complejo: acceso a todas las funciones."],
             ["mtorres", "Marco Torres", "RECEPCION", "Recepción: reservas, agenda, pagos y comprobantes."],
             ["docente", "Docente UTP", "ADMIN", "Cuenta para la revisión del docente del curso."]],
            [2.4, 3.2, 2.4, 7], "Las contraseñas se entregan por separado en el documento de accesos del sistema. "
            "Elaboración propia.")
    i.sub4("Manual del administrador: tareas diarias", "El trabajo habitual de recepción sigue esta secuencia:")
    i.numerada([
        "Revisar «Hoy» al iniciar el turno: comprobantes por validar, reservas del día y alertas (Figura {F:a-hoy}).",
        "Validar comprobantes: abra la reserva desde «Por validar» o desde «Reservas», compare la imagen con el pago "
        "recibido en la aplicación de Yape o Plin del complejo, marque la casilla de verificación y pulse «Validar». "
        "Si no coincide, pulse «Rechazar» y escriba el motivo para el cliente (Figuras {F:a-detalle} y {F:a-validar}).",
        "Registrar reservas telefónicas o presenciales en «Nueva reserva»: busque al cliente por DNI, elija cancha, "
        "día y turno y, si paga en el momento, registre el pago inicial (Figura {F:a-nueva}).",
        "Cobrar el saldo cuando el cliente llega: en el detalle de la reserva pulse «Cobrar saldo», elija el método y el "
        "tipo de comprobante (Figura {F:a-saldo}).",
        "Registrar la asistencia desde 15 minutos antes del turno con «Asistió» o «No asistió».",
        "Cancelar una reserva cuando corresponda: pulse «Cancelar», escriba el motivo e indique si hay devolución.",
        "Bloquear turnos por mantenimiento en «Agenda», seleccionando el turno o un rango de horas y escribiendo el motivo "
        "(Figura {F:a-agenda}).",
    ])
    i.p("La Figura {F:m-panel} muestra que estas tareas también pueden realizarse desde un celular.")
    a = i.preparar(*movil("panel", "01-hoy-390", 1688), nombre="m-hoy")
    b = i.preparar(*movil("panel", "22-agenda-390", 1688), nombre="m-agenda")
    i.figura_par("m-panel", "Panel de Administración desde el Celular", [a, b],
                 "El panel también se adapta al celular para consultas rápidas del personal. " + NOTA_LOCAL,
                 ancho_cm=6.2, alto_max_cm=13.5, rotulos=["Hoy", "Agenda"])
    i.sub4("Manual del administrador: funciones exclusivas del rol ADMIN",
           "Además de lo anterior, el administrador puede: crear y editar canchas, subir sus fotos y activarlas o "
           "desactivarlas (Figuras {F:a-canchas} y {F:a-fotos}); moderar y responder reseñas; modificar la configuración "
           "del complejo (tarifas, horarios, plazo de pago, horas para devolución, datos de Yape y Plin); generar turnos; "
           "consultar los reportes y descargarlos en Excel (Figura {F:a-reportes}); anular comprobantes, lo que emite una "
           "nota de crédito; revisar y reintentar notificaciones; conectar el WhatsApp del complejo escaneando el código "
           "QR con el teléfono desde Configuración (Figura {F:a-qr}); gestionar usuarios; consultar la bitácora; y "
           "revisar el estado de los microservicios (Figura {F:a-estado}).")
    i.sub4("Mensajes frecuentes y qué hacer",
           "La Tabla {T:mensajes} reúne los mensajes que el sistema muestra con más frecuencia y la acción recomendada.")
    i.tabla("mensajes", "Mensajes Frecuentes del Sistema y Acción Recomendada",
            ["Mensaje", "Significado", "Qué hacer"],
            [["«Ese turno acaba de ser reservado por otra persona…»", "Otro cliente tomó el turno primero.", "Elegir otro horario; los datos se conservan."],
             ["«RENIEC no disponible…»", "El servicio de consulta de DNI no respondió.", "Escribir nombres y apellidos a mano."],
             ["«Sube una imagen JPG o PNG de hasta 5 MB.»", "El archivo no es una imagen válida o es muy grande.", "Tomar otra captura del comprobante."],
             ["«No encontramos una reserva con ese código y DNI.»", "El código o el DNI no coinciden.", "Revisar el código ALC-XXXXXX y el DNI usado al reservar."],
             ["«Venció el plazo para pagar»", "No se envió el adelanto a tiempo.", "Hacer una nueva reserva."],
             ["«Usuario o contraseña incorrectos.»", "Credenciales erróneas en el panel.", "Revisarlas; tras cinco intentos se bloquea 15 minutos."],
             ["«Tu rol no puede usar esta sección.»", "Un usuario RECEPCION abrió una función del ADMIN.", "Solicitarla al administrador."],
             ["«El servicio de … no responde.»", "Un microservicio está reiniciándose o caído.", "Esperar unos segundos; revisar «Estado del sistema»."]],
            [5.2, 4.8, 5], "Elaboración propia.", tam=19)


# =====================================================================================================================
# PRINCIPAL
# =====================================================================================================================
def renderizar_diagramas():
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch(channel="chrome")
        pg = b.new_page(viewport={"width": 1700, "height": 1400}, device_scale_factor=2)
        for nombre in ("diagrama-arquitectura", "diagrama-saga"):
            pg.goto((RECURSOS / f"{nombre}.html").as_uri())
            pg.wait_for_timeout(400)
            pg.locator("#diagrama").screenshot(path=str(RECURSOS / f"{nombre}.png"))
        b.close()


def main():
    if "--diagramas" in sys.argv:
        renderizar_diagramas()
    i = Informe(BASE)
    i.reemplazar_texto("Setiembre de 2026", DATOS["fecha_portada"])
    i.reemplazar_texto("(apartados 6.8 a 6.12), que se completarán en el siguiente avance del proyecto.",
                       "(apartados 6.8 a 6.12).")
    seccion_6_5(i)
    seccion_6_6(i)
    seccion_6_7(i)
    seccion_6_8(i)
    seccion_6_9(i)
    seccion_6_10(i)
    seccion_6_11(i)
    seccion_6_12(i)
    capitulo_7(i)
    capitulo_8(i)
    capitulo_9_10(i)
    capitulo_12(i)
    n_ex, n_nu = referencias(i)
    avisos = i.resolver_referencias()
    i.indice()
    i.guardar(SALIDA)
    print(f"Informe generado: {SALIDA}")
    print(f"Tablas nuevas: {len(i.nuevas_tablas)} (17 a {i.n_tabla}); figuras nuevas: {len(i.nuevas_figuras)} (5 a {i.n_figura})")
    print(f"Referencias: {n_ex} existentes + {n_nu} nuevas")
    for a in avisos:
        print("AVISO:", a)


if __name__ == "__main__":
    main()
