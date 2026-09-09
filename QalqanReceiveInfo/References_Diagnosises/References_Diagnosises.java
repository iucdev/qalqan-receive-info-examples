package qalqan.receiveinfo.examples.references_diagnosises;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * QalqanReceiveInfo / References_Diagnosises — постраничное получение справочника.
 * Файл самодостаточен: DTO запроса, DTO ответа и заполненный пример.
 * Формируется только бизнес-нагрузка <data> (содержимое requestData / responseData конверта ШЭП);
 * сам конверт SendMessage, requestInfo и транспортная подпись — стандартная часть ШЭП и здесь не рассматриваются.
 * Паспорт сервиса: https://sb.egov.kz/services/passport/ORGAM-S-1170
 */
public final class References_Diagnosises {

    private References_Diagnosises() {
    }

    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"> с RequestType = References_Diagnosises
    // =====================================================================================

    public static final class IntegraRequest {
        public static final String REQUEST_TYPE = "References_Diagnosises";
        public final ReferencesRequest References_DiagnosisesRequest;

        public IntegraRequest(ReferencesRequest References_DiagnosisesRequest) {
            this.References_DiagnosisesRequest = References_DiagnosisesRequest;
        }

        /** XML элемента {@code <data>} — помещается в requestData конверта ШЭП. */
        public String toDataXml() {
            Document doc = Xml.newDocument();
            Element data = doc.createElement("data");
            data.setAttribute("xmlns:q1", "http://integrations.gosreestr.kz");
            data.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", "q1:IntegraRequest");
            doc.appendChild(data);
            Xml.text(data, "RequestType", REQUEST_TYPE);
            Element body = Xml.child(data, "References_DiagnosisesRequest");
            References_DiagnosisesRequest.writeTo(body);
            return Xml.toString(doc, true);
        }
    }

    // =====================================================================================
    // ОТВЕТ: <data xsi:type="q1:IntegraResponse">
    // =====================================================================================

    public static final class IntegraResponse {
        public final ResponseInfo responseInfo;
        /** Блок результата (есть только при statusCode = 200), иначе null. */
        public final ReferencePage result;

        public IntegraResponse(ResponseInfo responseInfo, ReferencePage result) {
            this.responseInfo = responseInfo;
            this.result = result;
        }

        public boolean isSuccess() {
            return responseInfo.isSuccess();
        }

        /** Разбор элемента {@code <data>} из responseData. */
        public static IntegraResponse parse(String dataXml) {
            Element data;
            try {
                data = Xml.parse(dataXml).getDocumentElement();
            } catch (Exception e) {
                throw new IllegalArgumentException("Ответ не является XML", e);
            }
            ResponseInfo info = ResponseInfo.parse(Xml.directChild(data, "ResponseInfo"));
            Element payload = Xml.directChild(data, "References_DiagnosisesResponse");
            return new IntegraResponse(info, payload == null ? null : ReferencePage.parse(payload));
        }
    }

    /** Служебная часть ответа: тип запроса, код (200/400/500/501) и сообщение. */
    public static final class ResponseInfo {
        public final String requestType;
        /** 200 — успех; 400 — ошибка запроса; 500 — внутренняя ошибка; 501 — не реализовано / отправитель не сопоставлен с МИС. */
        public final int statusCode;
        public final String message;

        public ResponseInfo(String requestType, int statusCode, String message) {
            this.requestType = requestType;
            this.statusCode = statusCode;
            this.message = message;
        }

        public boolean isSuccess() {
            return statusCode == 200;
        }

        static ResponseInfo parse(Element el) {
            if (el == null) {
                return new ResponseInfo(null, 0, null);
            }
            Integer code = Xml.childInt(el, "StatusCode");
            return new ResponseInfo(Xml.childText(el, "RequestType"), code == null ? 0 : code, Xml.childText(el, "Message"));
        }

        @Override
        public String toString() {
            return "ResponseInfo{requestType=" + requestType + ", statusCode=" + statusCode + ", message=" + message + "}";
        }
    }

    // =====================================================================================
    // DTO
    // =====================================================================================

    /** Постраничный запрос справочника (References_*). */
    public static class ReferencesRequest implements XmlWritable {
        /** Размер страницы (по умолчанию на сервере — 10). */
        public Integer pageSize;
        /** Индекс страницы, начиная с 0. */
        public Integer pageIndex;

        public ReferencesRequest() {
        }

        public ReferencesRequest(Integer pageSize, Integer pageIndex) {
            this.pageSize = pageSize;
            this.pageIndex = pageIndex;
        }

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "PageSize", pageSize);
            Xml.text(e, "PageIndex", pageIndex);
        }
    }

    /** Объект, умеющий записывать свои поля дочерними элементами в переданный элемент. */
    public interface XmlWritable {
        void writeTo(Element parent);
    }

    /**
     * Небольшой набор DOM-утилит: построение и чтение XML без внешних зависимостей.
     * Форматы значений совпадают с ожиданиями сервера: дата — {@code yyyy-MM-dd},
     * дата/время — ISO 8601 со смещением ({@code 2026-04-03T10:30:00+05:00}), enum — числовой код, boolean — {@code true/false}.
     */
    public static final class Xml {

        /** Дата/время: секунды + необязательные доли + смещение (например {@code +05:00} или {@code Z}). */
        public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        public static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

        private Xml() {
        }

        // ---------- построение ----------

        public static Document newDocument() {
            try {
                DocumentBuilderFactory f = secureFactory();
                return f.newDocumentBuilder().newDocument();
            } catch (Exception e) {
                throw new IllegalStateException("Cannot create DOM document", e);
            }
        }

        /** Создаёт дочерний элемент без пространства имён. */
        public static Element child(Element parent, String name) {
            Element el = parent.getOwnerDocument().createElement(name);
            parent.appendChild(el);
            return el;
        }

        /** Текстовый дочерний элемент; при {@code null} значении элемент не создаётся. */
        public static Element text(Element parent, String name, String value) {
            if (value == null) {
                return null;
            }
            Element el = child(parent, name);
            el.setTextContent(value);
            return el;
        }

        public static Element text(Element parent, String name, Long value) {
            return value == null ? null : text(parent, name, Long.toString(value));
        }

        public static Element text(Element parent, String name, Integer value) {
            return value == null ? null : text(parent, name, Integer.toString(value));
        }

        public static Element text(Element parent, String name, long value) {
            return text(parent, name, Long.toString(value));
        }

        public static Element text(Element parent, String name, int value) {
            return text(parent, name, Integer.toString(value));
        }

        public static Element text(Element parent, String name, Boolean value) {
            return value == null ? null : text(parent, name, value ? "true" : "false");
        }

        public static Element text(Element parent, String name, BigDecimal value) {
            return value == null ? null : text(parent, name, value.toPlainString());
        }

        public static Element text(Element parent, String name, LocalDate value) {
            return value == null ? null : text(parent, name, DATE.format(value));
        }

        public static Element text(Element parent, String name, OffsetDateTime value) {
            return value == null ? null : text(parent, name, DATE_TIME.format(value));
        }

        /** Enum с числовым кодом. */
        public static Element text(Element parent, String name, XmlCoded value) {
            return value == null ? null : text(parent, name, Integer.toString(value.code()));
        }

        /**
         * Список объектов: {@code <wrapper><item>...</item><item>...</item></wrapper>}.
         * При {@code null} списке элемент не создаётся; при пустом — создаётся пустой wrapper (как делает .NET XmlSerializer).
         */
        public static Element list(Element parent, String wrapper, String itemName, List<? extends XmlWritable> items) {
            if (items == null) {
                return null;
            }
            Element w = child(parent, wrapper);
            for (XmlWritable item : items) {
                item.writeTo(child(w, itemName));
            }
            return w;
        }

        /** Список строк: {@code <wrapper><string>..</string></wrapper>} — так .NET сериализует List&lt;string&gt;. */
        public static Element strings(Element parent, String wrapper, List<String> items) {
            if (items == null) {
                return null;
            }
            Element w = child(parent, wrapper);
            for (String s : items) {
                text(w, "string", s);
            }
            return w;
        }

        /** Вложенный объект; при {@code null} элемент не создаётся. */
        public static Element object(Element parent, String name, XmlWritable value) {
            if (value == null) {
                return null;
            }
            Element el = child(parent, name);
            value.writeTo(el);
            return el;
        }

        // ---------- сериализация / разбор ----------

        public static String toString(Node node, boolean indent) {
            try {
                TransformerFactory tf = TransformerFactory.newInstance();
                Transformer t = tf.newTransformer();
                t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                if (indent) {
                    t.setOutputProperty(OutputKeys.INDENT, "yes");
                    t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                }
                StringWriter sw = new StringWriter();
                t.transform(new DOMSource(node), new StreamResult(sw));
                return sw.toString();
            } catch (Exception e) {
                throw new IllegalStateException("Cannot serialize XML", e);
            }
        }

        public static Document parse(String xml) throws Exception {
            DocumentBuilder b = secureFactory().newDocumentBuilder();
            return b.parse(new InputSource(new StringReader(xml)));
        }

        /** Первый элемент с данным локальным именем в поддереве (глубина — любая), либо {@code null}. */
        public static Element firstByLocalName(Node scope, String localName) {
            NodeList children = scope.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node n = children.item(i);
                if (n instanceof Element) {
                    Element el = (Element) n;
                    if (localName.equals(el.getLocalName() != null ? el.getLocalName() : el.getNodeName())) {
                        return el;
                    }
                    Element nested = firstByLocalName(el, localName);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
            return null;
        }

        /** Прямой дочерний элемент по имени, либо {@code null}. */
        public static Element directChild(Element parent, String name) {
            if (parent == null) {
                return null;
            }
            NodeList children = parent.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node n = children.item(i);
                if (n instanceof Element && name.equals(localName((Element) n))) {
                    return (Element) n;
                }
            }
            return null;
        }

        /** Текст прямого дочернего элемента, либо {@code null} (в т.ч. для xsi:nil). */
        public static String childText(Element parent, String name) {
            Element el = directChild(parent, name);
            if (el == null) {
                return null;
            }
            if ("true".equals(el.getAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "nil"))) {
                return null;
            }
            return el.getTextContent();
        }

        public static Integer childInt(Element parent, String name) {
            String s = childText(parent, name);
            return s == null || s.trim().isEmpty() ? null : Integer.valueOf(s.trim());
        }

        public static Long childLong(Element parent, String name) {
            String s = childText(parent, name);
            return s == null || s.trim().isEmpty() ? null : Long.valueOf(s.trim());
        }

        public static Boolean childBoolean(Element parent, String name) {
            String s = childText(parent, name);
            return s == null || s.trim().isEmpty() ? null : Boolean.valueOf(s.trim());
        }

        public static OffsetDateTime childDateTime(Element parent, String name) {
            String s = childText(parent, name);
            return s == null || s.trim().isEmpty() ? null : OffsetDateTime.parse(s.trim(), DATE_TIME);
        }

        public static String localName(Element el) {
            return el.getLocalName() != null ? el.getLocalName() : el.getNodeName();
        }

        private static DocumentBuilderFactory secureFactory() throws Exception {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            return f;
        }
    }

    /** Перечисление, передаваемое в XML числовым кодом. */
    public interface XmlCoded {
        int code();
    }

    /** Страница справочника (References_*Response). */
    public static final class ReferencePage {
        public final int pageSize;
        /** Индекс страницы, начиная с 0. */
        public final int pageIndex;
        public final int pagesCount;
        public final List<ReferenceItem> items;

        public ReferencePage(int pageSize, int pageIndex, int pagesCount, List<ReferenceItem> items) {
            this.pageSize = pageSize;
            this.pageIndex = pageIndex;
            this.pagesCount = pagesCount;
            this.items = Collections.unmodifiableList(items);
        }

        public static ReferencePage parse(Element el) {
            if (el == null) {
                return null;
            }
            List<ReferenceItem> items = new ArrayList<>();
            Element itemsEl = Xml.directChild(el, "Items");
            if (itemsEl != null) {
                NodeList children = itemsEl.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node n = children.item(i);
                    if (n instanceof Element) {
                        items.add(ReferenceItem.parse((Element) n));
                    }
                }
            }
            return new ReferencePage(
                    intOrZero(Xml.childInt(el, "PageSize")),
                    intOrZero(Xml.childInt(el, "PageIndex")),
                    intOrZero(Xml.childInt(el, "PagesCount")),
                    items);
        }

        private static int intOrZero(Integer v) {
            return v == null ? 0 : v;
        }

        @Override
        public String toString() {
            return "ReferencePage{pageIndex=" + pageIndex + ", pageSize=" + pageSize + ", pagesCount=" + pagesCount + ", items=" + items.size() + "}";
        }
    }

    /**
     * Элемент справочника. Набор заполненных полей зависит от справочника:
     * услуги — code, nameRu, nameKz; лекарства — code, name, unitProperty; диагнозы/операции — code, qalqanCode, nameRu, nameKz.
     */
    public static final class ReferenceItem {
        public final String code;
        public final String nameRu;
        public final String nameKz;
        /** Только для лекарственных средств. */
        public final String name;
        /** Только для лекарственных средств (единица измерения). */
        public final String unitProperty;
        /** Внутренний код Qalqan (диагнозы, операции). */
        public final Integer qalqanCode;

        public ReferenceItem(String code, String nameRu, String nameKz, String name, String unitProperty, Integer qalqanCode) {
            this.code = code;
            this.nameRu = nameRu;
            this.nameKz = nameKz;
            this.name = name;
            this.unitProperty = unitProperty;
            this.qalqanCode = qalqanCode;
        }

        public static ReferenceItem parse(Element el) {
            return new ReferenceItem(
                    Xml.childText(el, "Code"),
                    Xml.childText(el, "NameRu"),
                    Xml.childText(el, "NameKz"),
                    Xml.childText(el, "Name"),
                    Xml.childText(el, "UnitProperty"),
                    Xml.childInt(el, "QalqanCode"));
        }

        /** Название на русском для любого справочника. */
        public String displayName() {
            return nameRu != null ? nameRu : name;
        }

        @Override
        public String toString() {
            return code + " — " + displayName();
        }
    }

    // =====================================================================================
    // ПРИМЕР
    // =====================================================================================

    /** Заполненный запрос. */
    public static IntegraRequest request() {
        return new IntegraRequest(new ReferencesRequest(5, 1)); // PageIndex с 0
    }

    /** Пример ответа сервиса (элемент {@code <data>} из responseData). */
    public static final String RESPONSE_XML =
            "<data xmlns:q1=\"http://integrations.gosreestr.kz\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"q1:IntegraResponse\">\n"
            + "  <ResponseInfo>\n"
            + "    <RequestType>References_Diagnosises</RequestType>\n"
            + "    <StatusCode>200</StatusCode>\n"
            + "    <Message>OK</Message>\n"
            + "  </ResponseInfo>\n"
            + "  <References_DiagnosisesResponse>\n"
            + "    <Items>\n"
            + "      <DiagnosisesReferenceItem>\n"
            + "        <Code>J06.9</Code>\n"
            + "        <QalqanCode>4821</QalqanCode>\n"
            + "        <NameRu>Острая инфекция верхних дыхательных путей неуточненная</NameRu>\n"
            + "        <NameKz>Жоғарғы тыныс жолдарының нақтыланбаған жедел инфекциясы</NameKz>\n"
            + "      </DiagnosisesReferenceItem>\n"
            + "    </Items>\n"
            + "    <PageSize>5</PageSize>\n"
            + "    <PageIndex>1</PageIndex>\n"
            + "    <PagesCount>2917</PagesCount>\n"
            + "  </References_DiagnosisesResponse>\n"
            + "</data>";

    public static IntegraResponse response() {
        return IntegraResponse.parse(RESPONSE_XML);
    }

    public static void main(String[] args) {
        System.out.println("----- request <data> -----");
        System.out.println(request().toDataXml());
        IntegraResponse response = response();
        System.out.println("----- response -----");
        System.out.println(response.responseInfo);
        if (response.result != null) {
            System.out.println(response.result);
        }
    }
}
