package qalqan.receiveinfo.examples.screenings_complete;

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
 * QalqanReceiveInfo / Screenings_Complete — завершение скрининга.
 * Файл самодостаточен: DTO запроса, DTO ответа и заполненный пример.
 * Формируется только бизнес-нагрузка <data> (содержимое requestData / responseData конверта ШЭП);
 * сам конверт SendMessage, requestInfo и транспортная подпись — стандартная часть ШЭП и здесь не рассматриваются.
 * Паспорт сервиса: https://sb.egov.kz/services/passport/ORGAM-S-1170
 */
public final class Screenings_Complete {

    private Screenings_Complete() {
    }

    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"> с RequestType = Screenings_Complete
    // =====================================================================================

    public static final class IntegraRequest {
        public static final String REQUEST_TYPE = "Screenings_Complete";
        public final CompleteScreeningRequest Screenings_CompleteRequest;

        public IntegraRequest(CompleteScreeningRequest Screenings_CompleteRequest) {
            this.Screenings_CompleteRequest = Screenings_CompleteRequest;
        }

        /** XML элемента {@code <data>} — помещается в requestData конверта ШЭП. */
        public String toDataXml() {
            Document doc = Xml.newDocument();
            Element data = doc.createElement("data");
            data.setAttribute("xmlns:q1", "http://integrations.gosreestr.kz");
            data.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", "q1:IntegraRequest");
            doc.appendChild(data);
            Xml.text(data, "RequestType", REQUEST_TYPE);
            Element body = Xml.child(data, "Screenings_CompleteRequest");
            Screenings_CompleteRequest.writeTo(body);
            return Xml.toString(doc, true);
        }
    }

    // =====================================================================================
    // ОТВЕТ: <data xsi:type="q1:IntegraResponse">
    // =====================================================================================

    public static final class IntegraResponse {
        public final ResponseInfo responseInfo;
        // У этой операции тела ответа нет — только responseInfo.

        public IntegraResponse(ResponseInfo responseInfo) {
            this.responseInfo = responseInfo;
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
            return new IntegraResponse(info);
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

    /** Завершение скрининга (Screenings_Complete). */
    public static class CompleteScreeningRequest implements XmlWritable {
        /** Идентификатор скрининга (обязателен). */
        public String screeningId;
        /** Целевая группа. */
        public Integer screeningGroupId;
        /** Заключительный диагноз (МКБ-10). */
        public String diagnosisCode;
        /** Причина завершения (см. {@link DictionaryCodes.ScreeningCompletionReason}). */
        public Integer completionReason;
        public OffsetDateTime startedAt;
        public OffsetDateTime completedAt;

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "ScreeningId", screeningId);
            Xml.text(e, "ScreeningGroupId", screeningGroupId);
            Xml.text(e, "DiagnosisCode", diagnosisCode);
            Xml.text(e, "CompletionReason", completionReason);
            Xml.text(e, "StartedAt", startedAt);
            Xml.text(e, "CompletedAt", completedAt);
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

    // =====================================================================================
    // ПРИМЕР
    // =====================================================================================

    /** Заполненный запрос. */
    public static IntegraRequest request() {
        CompleteScreeningRequest r = new CompleteScreeningRequest();
        r.screeningId = "SCR-2026-001";
        r.screeningGroupId = 1;
        r.diagnosisCode = "J06.9";
        r.completionReason = 1;                                          // завершение скрининга
        r.startedAt = OffsetDateTime.of(2026, 4, 10, 9, 0, 0, 0, ZoneOffset.UTC);
        r.completedAt = OffsetDateTime.of(2026, 4, 10, 10, 30, 0, 0, ZoneOffset.UTC);
        return new IntegraRequest(r);
    }

    /** Пример ответа сервиса (элемент {@code <data>} из responseData). */
    public static final String RESPONSE_XML =
            "<data xmlns:q1=\"http://integrations.gosreestr.kz\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"q1:IntegraResponse\">\n"
            + "  <ResponseInfo>\n"
            + "    <RequestType>Screenings_Complete</RequestType>\n"
            + "    <StatusCode>200</StatusCode>\n"
            + "    <Message>OK</Message>\n"
            + "  </ResponseInfo>\n"
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

    }
}
