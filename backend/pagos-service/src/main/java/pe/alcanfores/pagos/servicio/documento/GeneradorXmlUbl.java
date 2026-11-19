package pe.alcanfores.pagos.servicio.documento;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import pe.alcanfores.pagos.modelo.Comprobante;
import pe.alcanfores.pagos.modelo.DatosFacturacion;
import pe.alcanfores.pagos.modelo.TipoComprobante;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * XML UBL 2.1 (Invoice para boleta/factura, CreditNote para nota de crédito) con la estructura de SUNAT, SIN firma
 * digital: es un documento simulado para el proyecto académico y así lo dice el comentario del archivo.
 */
@Component
public class GeneradorXmlUbl {

    static final String NS_INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    static final String NS_CREDIT_NOTE = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
    static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String XMLNS = XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String PEN = "PEN";

    public static final String COMENTARIO = " DOCUMENTO SIMULADO · Proyecto académico UTP (Desarrollo Web Integrado). "
            + "Sin firma digital, no enviado a SUNAT y sin validez tributaria. ";

    public String generar(Comprobante c, Comprobante referencia) {
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setNamespaceAware(true);
            Document doc = fabrica.newDocumentBuilder().newDocument();
            doc.setXmlStandalone(true);
            doc.appendChild(doc.createComment(COMENTARIO));

            boolean nota = c.getTipo() == TipoComprobante.NOTA_CREDITO;
            Element raiz = doc.createElementNS(nota ? NS_CREDIT_NOTE : NS_INVOICE, nota ? "CreditNote" : "Invoice");
            raiz.setAttributeNS(XMLNS, "xmlns:cac", NS_CAC);
            raiz.setAttributeNS(XMLNS, "xmlns:cbc", NS_CBC);
            raiz.setAttributeNS(XMLNS, "xmlns:ext", NS_EXT);
            raiz.setAttributeNS(XMLNS, "xmlns:ds", NS_DS);
            doc.appendChild(raiz);

            Xml x = new Xml(doc);
            Element extension = x.hijo(x.hijo(x.ext(raiz, "UBLExtensions"), NS_EXT, "ext:UBLExtension"), NS_EXT, "ext:ExtensionContent");
            extension.appendChild(doc.createComment(" Aquí iría la firma digital (ds:Signature). Documento simulado: sin firma. "));

            x.cbc(raiz, "UBLVersionID", "2.1");
            x.atributo(x.cbc(raiz, "CustomizationID", "2.0"), "schemeAgencyName", "PE:SUNAT");
            x.cbc(raiz, "ID", c.serieNumero());
            x.cbc(raiz, "IssueDate", c.getFechaEmision().toLocalDate().toString());
            x.cbc(raiz, "IssueTime", c.getFechaEmision().toLocalTime().format(HORA));
            if (!nota) {
                Element tipo = x.cbc(raiz, "InvoiceTypeCode", c.getCodigoTipo());
                x.atributo(tipo, "listAgencyName", "PE:SUNAT");
                x.atributo(tipo, "listName", "Tipo de Documento");
                x.atributo(tipo, "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01");
                x.atributo(tipo, "listID", "0101");
                x.atributo(tipo, "name", "Tipo de Operacion");
            }
            x.atributo(x.cbc(raiz, "Note", c.getMontoLetras()), "languageLocaleID", "1000");
            Element moneda = x.cbc(raiz, "DocumentCurrencyCode", PEN);
            x.atributo(moneda, "listID", "ISO 4217 Alpha");
            x.atributo(moneda, "listAgencyName", "United Nations Economic Commission for Europe");
            if (!nota) {
                x.cbc(raiz, "LineCountNumeric", "1");
            }
            if (nota && referencia != null) {
                Element discrepancia = x.cac(raiz, "DiscrepancyResponse");
                x.cbc(discrepancia, "ReferenceID", referencia.serieNumero());
                x.cbc(discrepancia, "ResponseCode", c.getCodigoMotivo());
                x.cbc(discrepancia, "Description", c.getMotivo());
                Element documento = x.cac(x.cac(raiz, "BillingReference"), "InvoiceDocumentReference");
                x.cbc(documento, "ID", referencia.serieNumero());
                x.cbc(documento, "DocumentTypeCode", referencia.getCodigoTipo());
            }

            Element emisor = x.cac(x.cac(raiz, "AccountingSupplierParty"), "Party");
            x.atributo(x.cbc(x.cac(emisor, "PartyIdentification"), "ID", c.getEmisorRuc()), "schemeID", DatosFacturacion.DOC_RUC);
            x.cbc(x.cac(emisor, "PartyName"), "Name", "Complejo Deportivo Los Alcanfores");
            Element legalEmisor = x.cac(emisor, "PartyLegalEntity");
            x.cbc(legalEmisor, "RegistrationName", c.getEmisorRazonSocial());
            Element direccionEmisor = x.cac(legalEmisor, "RegistrationAddress");
            x.cbc(direccionEmisor, "AddressTypeCode", "0000");
            x.cbc(x.cac(direccionEmisor, "AddressLine"), "Line", c.getEmisorDireccion());

            Element adquirente = x.cac(x.cac(raiz, "AccountingCustomerParty"), "Party");
            x.atributo(x.cbc(x.cac(adquirente, "PartyIdentification"), "ID", c.getClienteNumDoc()), "schemeID", c.getClienteTipoDoc());
            Element legalAdquirente = x.cac(adquirente, "PartyLegalEntity");
            x.cbc(legalAdquirente, "RegistrationName", c.getClienteNombre());
            if (c.getClienteDireccion() != null && !c.getClienteDireccion().isBlank()) {
                x.cbc(x.cac(x.cac(legalAdquirente, "RegistrationAddress"), "AddressLine"), "Line", c.getClienteDireccion());
            }
            if (!nota) {
                Element terminos = x.cac(raiz, "PaymentTerms");
                x.cbc(terminos, "ID", "FormaPago");
                x.cbc(terminos, "PaymentMeansID", "Contado");
            }

            impuestos(x, raiz, c.getOpGravada(), c.getIgv(), false);
            Element totales = x.cac(raiz, "LegalMonetaryTotal");
            if (!nota) {
                x.monto(totales, "LineExtensionAmount", c.getOpGravada());
                x.monto(totales, "TaxInclusiveAmount", c.getTotal());
            }
            x.monto(totales, "PayableAmount", c.getTotal());

            Element linea = x.cac(raiz, nota ? "CreditNoteLine" : "InvoiceLine");
            x.cbc(linea, "ID", "1");
            x.atributo(x.cbc(linea, nota ? "CreditedQuantity" : "InvoicedQuantity", "1"), "unitCode", "ZZ");
            x.monto(linea, "LineExtensionAmount", c.getOpGravada());
            Element precioReferencial = x.cac(x.cac(linea, "PricingReference"), "AlternativeConditionPrice");
            x.monto(precioReferencial, "PriceAmount", c.getTotal());
            x.cbc(precioReferencial, "PriceTypeCode", "01");
            impuestos(x, linea, c.getOpGravada(), c.getIgv(), true);
            Element item = x.cac(linea, "Item");
            x.cbc(item, "Description", c.getDescripcion());
            x.cbc(x.cac(item, "SellersItemIdentification"), "ID", "ALQ-CANCHA");
            x.monto(x.cac(linea, "Price"), "PriceAmount", c.getOpGravada());

            return escribir(doc);
        } catch (ParserConfigurationException | TransformerException e) {
            throw new IllegalStateException("No se pudo generar el XML UBL", e);
        }
    }

    private void impuestos(Xml x, Element padre, BigDecimal base, BigDecimal igv, boolean conPorcentaje) {
        Element total = x.cac(padre, "TaxTotal");
        x.monto(total, "TaxAmount", igv);
        Element subtotal = x.cac(total, "TaxSubtotal");
        x.monto(subtotal, "TaxableAmount", base);
        x.monto(subtotal, "TaxAmount", igv);
        Element categoria = x.cac(subtotal, "TaxCategory");
        if (conPorcentaje) {
            x.cbc(categoria, "Percent", "18.00");
            x.cbc(categoria, "TaxExemptionReasonCode", "10");
        }
        Element esquema = x.cac(categoria, "TaxScheme");
        x.cbc(esquema, "ID", "1000");
        x.cbc(esquema, "Name", "IGV");
        x.cbc(esquema, "TaxTypeCode", "VAT");
    }

    private static String escribir(Document doc) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter salida = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(salida));
        return salida.toString();
    }

    /** Ayudante para crear elementos cac/cbc sin repetir espacios de nombres. */
    private record Xml(Document doc) {

        Element hijo(Element padre, String ns, String nombre) {
            Element e = doc.createElementNS(ns, nombre);
            padre.appendChild(e);
            return e;
        }

        Element ext(Element padre, String nombre) {
            return hijo(padre, NS_EXT, "ext:" + nombre);
        }

        Element cac(Element padre, String nombre) {
            return hijo(padre, NS_CAC, "cac:" + nombre);
        }

        Element cbc(Element padre, String nombre, String valor) {
            Element e = hijo(padre, NS_CBC, "cbc:" + nombre);
            e.setTextContent(valor == null ? "" : valor);
            return e;
        }

        void monto(Element padre, String nombre, BigDecimal valor) {
            atributo(cbc(padre, nombre, valor.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()), "currencyID", PEN);
        }

        Element atributo(Element e, String nombre, String valor) {
            e.setAttribute(nombre, valor);
            return e;
        }
    }
}
