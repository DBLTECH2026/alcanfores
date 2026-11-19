package pe.alcanfores.reservas.servicio.impl;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.reservas.dto.AdminDtos.ReservaFila;
import pe.alcanfores.reservas.dto.PanelDtos.PorCancha;
import pe.alcanfores.reservas.dto.PanelDtos.PorDia;
import pe.alcanfores.reservas.dto.PanelDtos.ReporteReservas;
import pe.alcanfores.reservas.dto.PanelDtos.Totales;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

/** Excel del reporte de reservas (Apache POI): hojas Resumen, Por cancha, Por día y Reservas, con la marca. */
@Component
public class ReporteExcel {

    private static final byte[] VERDE = {(byte) 0x27, (byte) 0x81, (byte) 0x1F};
    private static final byte[] AZUL_NOCHE = {(byte) 0x09, (byte) 0x1A, (byte) 0x2A};
    private static final byte[] MENTA = {(byte) 0xE8, (byte) 0xF3, (byte) 0xEC};

    public byte[] generar(ReporteReservas rep, List<ReservaFila> filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos e = new Estilos(libro);
            resumen(libro.createSheet("Resumen"), rep, e);
            porCancha(libro.createSheet("Por cancha"), rep.porCancha(), e);
            porDia(libro.createSheet("Por día"), rep.porDia(), e);
            reservas(libro.createSheet("Reservas"), filas, e);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el Excel del reporte", ex);
        }
    }

    private void resumen(Sheet hoja, ReporteReservas rep, Estilos e) {
        titulo(hoja, "Reporte de reservas · Complejo Deportivo Los Alcanfores", e, 2);
        Row periodo = hoja.createRow(1);
        celda(periodo, 0, "Periodo", e.etiqueta);
        celda(periodo, 1, rep.desde().format(Tiempo.FECHA_CORTA) + " al " + rep.hasta().format(Tiempo.FECHA_CORTA), e.texto);
        encabezados(hoja.createRow(3), e, "Indicador", "Valor");
        Totales t = rep.totales();
        Object[][] datos = {
                {"Reservas registradas", t.registradas()}, {"Confirmadas", t.confirmadas()}, {"Pendientes", t.pendientes()},
                {"Canceladas", t.canceladas()}, {"Asistieron", t.asistidas()}, {"No asistieron", t.noAsistio()},
                {"Tasa de no-show (%)", t.tasaNoShowPct()}, {"Ocupación (%)", t.ocupacionPct()}};
        int fila = 4;
        for (Object[] d : datos) {
            Row r = hoja.createRow(fila++);
            celda(r, 0, (String) d[0], e.texto);
            numero(r, 1, d[1], e);
        }
        fila++;
        encabezados(hoja.createRow(fila++), e, "Cancelaciones por", "Cantidad");
        for (var actor : rep.cancelaciones().porActor().entrySet()) {
            Row r = hoja.createRow(fila++);
            celda(r, 0, actor.getKey(), e.texto);
            numero(r, 1, actor.getValue(), e);
        }
        fila++;
        encabezados(hoja.createRow(fila++), e, "Origen", "Reservas");
        for (var o : rep.porOrigen()) {
            Row r = hoja.createRow(fila++);
            celda(r, 0, o.origen(), e.texto);
            numero(r, 1, o.reservas(), e);
        }
        anchos(hoja, 34, 26);
    }

    private void porCancha(Sheet hoja, List<PorCancha> lista, Estilos e) {
        titulo(hoja, "Reservas y ocupación por cancha", e, 3);
        encabezados(hoja.createRow(2), e, "Id", "Cancha", "Reservas", "Ocupación (%)");
        int fila = 3;
        for (PorCancha c : lista) {
            Row r = hoja.createRow(fila++);
            numero(r, 0, c.idCancha(), e);
            celda(r, 1, c.cancha(), e.texto);
            numero(r, 2, c.reservas(), e);
            numero(r, 3, c.ocupacionPct(), e);
        }
        anchos(hoja, 8, 30, 14, 16);
    }

    private void porDia(Sheet hoja, List<PorDia> lista, Estilos e) {
        titulo(hoja, "Reservas por día", e, 3);
        encabezados(hoja.createRow(2), e, "Fecha", "Registradas", "Confirmadas", "Canceladas");
        int fila = 3;
        for (PorDia d : lista) {
            Row r = hoja.createRow(fila++);
            celda(r, 0, d.fecha().format(Tiempo.FECHA_CORTA), e.texto);
            numero(r, 1, d.registradas(), e);
            numero(r, 2, d.confirmadas(), e);
            numero(r, 3, d.canceladas(), e);
        }
        anchos(hoja, 14, 14, 14, 14);
    }

    private void reservas(Sheet hoja, List<ReservaFila> filas, Estilos e) {
        titulo(hoja, "Detalle de reservas", e, 12);
        encabezados(hoja.createRow(2), e, "Código", "Fecha", "Hora", "Cancha", "Cliente", "DNI", "Celular", "Estado",
                "Pago", "Asistencia", "Origen", "Total (S/)", "Pagado (S/)");
        int fila = 3;
        for (ReservaFila f : filas) {
            Row r = hoja.createRow(fila++);
            celda(r, 0, f.codigoReserva(), e.texto);
            celda(r, 1, f.fecha().format(Tiempo.FECHA_CORTA), e.texto);
            celda(r, 2, f.horaInicio().format(Tiempo.HORA) + " - " + f.horaFin().format(Tiempo.HORA), e.texto);
            celda(r, 3, f.cancha(), e.texto);
            celda(r, 4, f.cliente().nombres() + " " + f.cliente().apellidos(), e.texto);
            celda(r, 5, f.cliente().dni(), e.texto);
            celda(r, 6, f.cliente().telefono(), e.texto);
            celda(r, 7, f.estado().name(), e.texto);
            celda(r, 8, f.estadoPago().name(), e.texto);
            celda(r, 9, f.asistencia().name(), e.texto);
            celda(r, 10, f.origen().name(), e.texto);
            numero(r, 11, f.montoTotal(), e);
            numero(r, 12, f.montoPagado(), e);
        }
        hoja.createFreezePane(0, 3);
        if (!filas.isEmpty()) {
            hoja.setAutoFilter(new CellRangeAddress(2, fila - 1, 0, 12));
        }
        anchos(hoja, 13, 12, 14, 24, 30, 11, 12, 13, 17, 13, 13, 12, 12);
    }

    private static void titulo(Sheet hoja, String texto, Estilos e, int ultimaColumna) {
        Row r = hoja.createRow(0);
        r.setHeightInPoints(24);
        celda(r, 0, texto, e.titulo);
        hoja.addMergedRegion(new CellRangeAddress(0, 0, 0, ultimaColumna));
    }

    private static void encabezados(Row fila, Estilos e, String... textos) {
        for (int i = 0; i < textos.length; i++) {
            celda(fila, i, textos[i], e.encabezado);
        }
    }

    private static void celda(Row fila, int col, String valor, CellStyle estilo) {
        Cell c = fila.createCell(col);
        c.setCellValue(valor == null ? "" : valor);
        c.setCellStyle(estilo);
    }

    private static void numero(Row fila, int col, Object valor, Estilos e) {
        Cell c = fila.createCell(col);
        if (valor instanceof BigDecimal b) {
            c.setCellValue(b.doubleValue());
            c.setCellStyle(e.decimal);
        } else {
            c.setCellValue(((Number) valor).doubleValue());
            c.setCellStyle(e.entero);
        }
    }

    private static void anchos(Sheet hoja, int... caracteres) {
        for (int i = 0; i < caracteres.length; i++) {
            hoja.setColumnWidth(i, caracteres[i] * 256);
        }
    }

    /** Estilos de la marca: encabezado verde Alcanfores, título azul noche y bordes suaves. */
    private static final class Estilos {
        final CellStyle titulo;
        final CellStyle encabezado;
        final CellStyle etiqueta;
        final CellStyle texto;
        final CellStyle entero;
        final CellStyle decimal;

        Estilos(XSSFWorkbook libro) {
            XSSFFont fuenteTitulo = libro.createFont();
            fuenteTitulo.setBold(true);
            fuenteTitulo.setFontHeightInPoints((short) 14);
            fuenteTitulo.setColor(new XSSFColor(AZUL_NOCHE, null));
            titulo = libro.createCellStyle();
            titulo.setFont(fuenteTitulo);

            XSSFFont fuenteEncabezado = libro.createFont();
            fuenteEncabezado.setBold(true);
            fuenteEncabezado.setColor(new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, null));
            XSSFCellStyle enc = libro.createCellStyle();
            enc.setFont(fuenteEncabezado);
            enc.setFillForegroundColor(new XSSFColor(VERDE, null));
            enc.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            enc.setAlignment(HorizontalAlignment.CENTER);
            bordes(enc);
            encabezado = enc;

            XSSFFont negrita = libro.createFont();
            negrita.setBold(true);
            XSSFCellStyle et = libro.createCellStyle();
            et.setFont(negrita);
            et.setFillForegroundColor(new XSSFColor(MENTA, null));
            et.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            etiqueta = et;

            texto = libro.createCellStyle();
            bordes(texto);
            entero = libro.createCellStyle();
            entero.setDataFormat(libro.createDataFormat().getFormat("0"));
            bordes(entero);
            decimal = libro.createCellStyle();
            decimal.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));
            bordes(decimal);
        }

        private static void bordes(CellStyle s) {
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
        }
    }
}
