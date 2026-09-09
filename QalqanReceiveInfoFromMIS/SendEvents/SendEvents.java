package qalqan.receiveinfofrommis.examples.sendevents;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
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
 * QalqanReceiveInfoFromMIS / SendEvents — передача пакета событий МИС.
 * Файл самодостаточен: DTO запроса (ZippedEventsInBase64 = Base64(gzip(JSON пакета событий))), DTO ответа и заполненный пример.
 * Формируется только бизнес-нагрузка <data> (содержимое requestData / responseData конверта ШЭП);
 * сам конверт SendMessage, requestInfo и транспортная подпись — стандартная часть ШЭП и здесь не рассматриваются.
 * versionHash в примере — заглушка: реальное значение вычисляет МИС (при несовпадении сервер отвечает Status = InvalidHash).
 * Паспорт сервиса: https://sb.egov.kz/services/passport/ORGAM-S-9847
 */
public final class SendEvents {

    private SendEvents() {
    }

    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"><ZippedEventsInBase64>…</ZippedEventsInBase64></data>
    // =====================================================================================

    public static final class IntegraRequest {
        /** Пространство имён типов сервиса из его XSD (businessData.xsd). */
        public static final String INTEGRA_NS = "http://integrations.e-qazyna.kz";

        /** Base64( gzip( JSON пакета событий ) ), см. {@link MisEventsPacker}. */
        public final String zippedEventsInBase64;

        public IntegraRequest(String zippedEventsInBase64) {
            this.zippedEventsInBase64 = zippedEventsInBase64;
        }

        public static IntegraRequest fromEvents(List<MisEvent> events) {
            return new IntegraRequest(MisEventsPacker.pack(events));
        }

        /** XML элемента {@code <data>} — помещается в requestData конверта ШЭП. */
        public String toDataXml() {
            Document doc = Xml.newDocument();
            Element data = doc.createElement("data");
            data.setAttribute("xmlns:q1", INTEGRA_NS);
            data.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", "q1:IntegraRequest");
            doc.appendChild(data);
            Xml.text(data, "ZippedEventsInBase64", zippedEventsInBase64);
            return Xml.toString(doc, true);
        }
    }

    /** Одно событие МИС (элемент массива {@code events} пакета QalqanReceiveInfoFromMIS). */
    public static class MisEvent {
        /** Тип сущности (числовой код). */
        public MisEntityType entityType;
        /** Момент события: unix-время в секундах (допускается дробная часть). */
        public double timestamp;
        /** Хэш версии события (hex, 64 символа). Вычисляется МИС; сервер отвечает {@code InvalidHash}, если он не сходится. */
        public String versionHash;
        /** Идентификатор медицинской организации. */
        public long moId;
        /** БИН медицинской организации. */
        public String organizationBin;
        /** БИН владельца МИС (отправителя). */
        public String misBin;
        /**
         * Тело события — JSON-строка (сериализованный объект события; состав полей зависит от {@code eventType}).
         * Общие поля: {@code eventId}, {@code eventType}, {@code misId}, {@code organizationId}, {@code organizationBin}, {@code patientInformation}.
         * Удобно формировать через {@link Json#write(Object)}.
         */
        public String data;

        /** Представление события в виде упорядоченной карты для JSON-сериализации. */
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entityType", entityType == null ? null : entityType.code());
            m.put("timestamp", timestamp);
            m.put("versionHash", versionHash);
            m.put("moId", moId);
            m.put("organizationBin", organizationBin);
            m.put("misBin", misBin);
            m.put("data", data);
            return m;
        }
    }

    /** Тип сущности (события) МИС — поле {@code entityType} события QalqanReceiveInfoFromMIS. Передаётся числом. */
    public enum MisEntityType {
        MEDICATION_ASSIGNMENT_EXECUTION(0),
        BLOOD_TRANSFUSION(1),
        RECEPTION_APPEAL(2),
        ASSESSMENT(3),
        HOSPITAL_TRANSFER(4),
        MEDICAL_APPOINTMENT(5),
        REHABILITATION_CARD(6),
        DISEASE_MANAGEMENT_REGISTRATION(7),
        PREOPERATIVE_SUMMARY(8),
        MONITORING_FERTILE_WOMAN(9),
        RECORD_APPOINTMENT(10),
        HOSPITALIZATION_REFUSAL(11),
        NEWBORN_REANIMATION(12),
        PATIENT_PLACEMENT(13),
        DISABILITY(14),
        AMBULANCE_CALL(15),
        DISPENSARY_REGISTRATION(16),
        MEDICATION_PRESCRIPTION(17),
        REFERRAL_BG(18),
        PROVISION_CARE(19),
        TREATMENT_PLANNING(20),
        CARDIO_PULMONARY_REANIMATION(21),
        ILL_TREATMENT(22),
        REHAB_CASE(23),
        PREGNANCY_REGISTRATION(24),
        MEDICATION_ASSIGNMENT(25),
        NEWBORN_HISTORY(26),
        REFERRAL_KDU(27),
        ACTIVE(28),
        SURGERY(29),
        MONITORING_PREGNANT_WOMAN(30),
        MEDICAL_WITHDRAWAL(31),
        BLOOD_PRODUCT_RECORD(32),
        MEDICATION_RECIPE(33),
        HEMODIALYSIS(34),
        DISPENSARY_DEREGISTRATION(35),
        BIRTH_REGISTRATION(36),
        DISEASE_MANAGEMENT_MONITORING(37),
        DEATH_CONCLUSION(38),
        HOUSE_CALL(39),
        HOSPITALIZATION(40),
        SELF_MONITORING_DISEASE_MANAGEMENT(41),
        NURSE_CONSULTATION(42),
        COMPLETION_COURSE_TREATMENT(43),
        ANESTHESIA(44),
        ASSIGNMENT_KDU(45),
        PREGNANCY_DEREGISTRATION(46),
        DISEASE_MANAGEMENT_EXCLUSION(47),
        VACCINATION(48),
        NURSE_CARE(49),
        INPATIENT_MEDICAL_EXAMINATION(50),
        DISCHARGE(51),
        BRAIN_DEATH_CONFIRMATION(52),
        MEDICAL_COMMISSION_CONCLUSION(53),
        DYNAMIC_MONITORING(54),
        BIOLOGICAL_DEATH_CONFIRMATION(55),
        SERVICE(56),
        DEATH_FACT_ESTABLISHMENT(57),
        DONOR_FUNCTION(58),
        SCREENING(59),
        NPPI_VACCINATION(60),
        DELIVERY_HISTORY(61),
        AMBULANCE_CARD(62),
        SUBMIT_ACTUAL_COSTS(63);

        private final int code;

        MisEntityType(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static MisEntityType fromCode(int code) {
            for (MisEntityType v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            throw new IllegalArgumentException("Unknown MisEntityType code: " + code);
        }
    }

    /** Тип действия над сущностью. */
    public enum MisActionType {
        CREATE(0),
        UPDATE(1),
        REMOVE(2);

        private final int code;

        MisActionType(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    /**
     * Минимальный JSON-сериализатор без внешних зависимостей: {@link Map} (объект, порядок ключей сохраняется),
     * {@link Collection} / массивы (массив), {@link String}, {@link Number}, {@link Boolean}, {@code null}, enum с кодом.
     */
    public static final class Json {

        private Json() {
        }

        public static String write(Object value) {
            StringBuilder sb = new StringBuilder();
            write(value, sb);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        private static void write(Object v, StringBuilder sb) {
            if (v == null) {
                sb.append("null");
            } else if (v instanceof String) {
                string((String) v, sb);
            } else if (v instanceof Boolean) {
                sb.append(((Boolean) v) ? "true" : "false");
            } else if (v instanceof Double || v instanceof Float) {
                double d = ((Number) v).doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    sb.append("null");
                } else {
                    sb.append(new BigDecimal(Double.toString(d)).toPlainString());
                }
            } else if (v instanceof Number) {
                sb.append(v.toString());
            } else if (v instanceof MisEntityType) {
                sb.append(((MisEntityType) v).code());
            } else if (v instanceof MisActionType) {
                sb.append(((MisActionType) v).code());
            } else if (v instanceof Enum) {
                string(((Enum<?>) v).name(), sb);
            } else if (v instanceof Map) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<Object, Object> e : ((Map<Object, Object>) v).entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    string(String.valueOf(e.getKey()), sb);
                    sb.append(':');
                    write(e.getValue(), sb);
                }
                sb.append('}');
            } else if (v instanceof Collection) {
                sb.append('[');
                boolean first = true;
                for (Object item : (Collection<Object>) v) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    write(item, sb);
                }
                sb.append(']');
            } else if (v instanceof Object[]) {
                sb.append('[');
                Object[] arr = (Object[]) v;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    write(arr[i], sb);
                }
                sb.append(']');
            } else if (v instanceof MisEvent) {
                write(((MisEvent) v).toMap(), sb);
            } else {
                string(v.toString(), sb);
            }
        }

        private static void string(String s, StringBuilder sb) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    case '\b': sb.append("\\b"); break;
                    case '\f': sb.append("\\f"); break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }
            sb.append('"');
        }
    }

    /** Упаковка пакета событий: JSON → gzip → Base64 (и обратно). */
    public static final class MisEventsPacker {

        private MisEventsPacker() {
        }

        /** JSON пакета: {@code {"events":[...]}}. */
        public static String toJson(List<MisEvent> events) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("events", events);
            return Json.write(root);
        }

        /** JSON → gzip → Base64. */
        public static String pack(List<MisEvent> events) {
            return packJson(toJson(events));
        }

        public static String packJson(String json) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                    gzip.write(json.getBytes(StandardCharsets.UTF_8));
                }
                return Base64.getEncoder().encodeToString(out.toByteArray());
            } catch (IOException e) {
                throw new IllegalStateException("gzip failed", e);
            }
        }

        /** Base64 → gunzip → JSON. */
        public static String unpackJson(String base64) {
            try {
                byte[] bytes = Base64.getMimeDecoder().decode(base64.trim());
                try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("gunzip failed", e);
            }
        }
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

    /** Объект, умеющий записывать свои поля дочерними элементами в переданный элемент. */
    public interface XmlWritable {
        void writeTo(Element parent);
    }

    /** Перечисление, передаваемое в XML числовым кодом. */
    public interface XmlCoded {
        int code();
    }

    // =====================================================================================
    // ОТВЕТ: <data xsi:type="q1:IntegraResponse">
    // =====================================================================================

    public static final class IntegraResponse {
        /** Статус обработки: Success, InvalidHash (не сошёлся versionHash), Error. */
        public final boolean isSuccess;
        public final String errorMessage;
        public final String status;

        public IntegraResponse(boolean isSuccess, String errorMessage, String status) {
            this.isSuccess = isSuccess;
            this.errorMessage = errorMessage;
            this.status = status;
        }

        public static IntegraResponse parse(String dataXml) {
            Element data;
            try {
                data = Xml.parse(dataXml).getDocumentElement();
            } catch (Exception e) {
                throw new IllegalArgumentException("Ответ не является XML", e);
            }
            Boolean ok = Xml.childBoolean(data, "IsSuccess");
            return new IntegraResponse(ok != null && ok, Xml.childText(data, "ErrorMessage"), Xml.childText(data, "Status"));
        }

        @Override
        public String toString() {
            return "IntegraResponse{isSuccess=" + isSuccess + ", status=" + status + ", errorMessage=" + errorMessage + "}";
        }
    }

    // =====================================================================================
    // ПРИМЕР (идентификаторы вымышленные; versionHash — заглушка)
    // =====================================================================================

    private static final String ORG_BIN = "990140000001";
    private static final long MO_ID = 100001L;
    private static final String PATIENT_IIN = "950101300003";
    private static final String PATIENT_RPN_ID = "123456789";
    private static final String DOCTOR_IIN = "900101300001";
    private static final String HASH_PLACEHOLDER = "0000000000000000000000000000000000000000000000000000000000000000";

    /** Событие «Выписка из стационара» (entityType 51, eventType DISCHARGE). */
    public static MisEvent dischargeEvent() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", "05ef2486-d9e0-4ec6-9b25-a75bc629d13e");
        body.put("eventType", "DISCHARGE");
        body.put("misId", "10");
        body.put("organizationId", Long.toString(MO_ID));
        body.put("organizationBin", ORG_BIN);
        body.put("hospitalizationId", "6800000000000001");
        body.put("bedDays", 10);
        body.put("dischargeDateTime", "2026-07-15T17:00:00.000Z");
        body.put("outcome", "200");
        body.put("treatmentOutcome", "200");
        body.put("inPatientPayType", "200");
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("mkb10", "N73.3");
        diag.put("diagKind", "400");
        diag.put("diagType", "300");
        body.put("diagList", Collections.singletonList(diag));
        body.put("dischargeNewborn", Collections.emptyList());
        body.put("epicrisis", Collections.emptyList());
        body.put("surgeryList", Collections.emptyList());
        body.put("patientInformation", patient());
        Map<String, Object> signatures = new LinkedHashMap<>();
        signatures.put("signerInformation", new LinkedHashMap<String, Object>());
        body.put("signatures", signatures);

        MisEvent e = new MisEvent();
        e.entityType = MisEntityType.DISCHARGE;
        e.timestamp = 1784160231.378;
        e.versionHash = HASH_PLACEHOLDER;
        e.moId = MO_ID;
        e.organizationBin = ORG_BIN;
        e.misBin = ORG_BIN;
        e.data = Json.write(body);
        return e;
    }
    /** Событие «Актив» (entityType 28, eventType ACTIVE). */
    public static MisEvent activeEvent() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", "56427533-b3eb-441e-9d4d-aa83d71b2e04");
        body.put("eventType", "ACTIVE");
        body.put("misId", 10);
        body.put("organizationId", MO_ID);
        body.put("organizationBin", ORG_BIN);
        body.put("patientInformation", patient());
        body.put("signature", null);
        body.put("senderOrganizationId", Long.toString(MO_ID));
        body.put("senderOrganizationBin", ORG_BIN);
        body.put("senderDoctorId", "0");
        body.put("senderDoctorIin", null);
        body.put("receiverOrganizationId", Long.toString(MO_ID));
        body.put("receiverOrganizationBin", ORG_BIN);
        body.put("receiverDoctorId", "45500000000000001");
        body.put("receiverDoctorIin", DOCTOR_IIN);
        body.put("activeStatusId", 200);
        body.put("changeDateTime", "2026-03-05T09:15:47.150Z");
        body.put("regPostId", 45500000000000002L);
        body.put("regPostIin", "900101300002");
        body.put("rejectReason", null);
        body.put("sicks", Collections.emptyList());
        body.put("activeDeliveryStatusId", null);
        body.put("receiveDate", "2026-03-06T06:50:00.000Z");
        body.put("outcomeId", null);
        body.put("referralServiceExecId", null);
        body.put("visitType", "4");
        body.put("dischargeId", null);

        MisEvent e = new MisEvent();
        e.entityType = MisEntityType.ACTIVE;
        e.timestamp = 1772787347.150;
        e.versionHash = HASH_PLACEHOLDER;
        e.moId = MO_ID;
        e.organizationBin = ORG_BIN;
        e.misBin = ORG_BIN;
        e.data = Json.write(body);
        return e;
    }
    private static Map<String, Object> patient() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("iin", PATIENT_IIN);
        p.put("rpnId", PATIENT_RPN_ID);
        return p;
    }

    /** Заполненный запрос: пакет из двух событий (DISCHARGE и ACTIVE). */
    public static IntegraRequest request() {
        return IntegraRequest.fromEvents(Arrays.asList(dischargeEvent(), activeEvent()));
    }

    /** Пример успешного ответа сервиса (элемент {@code <data>} из responseData). */
    public static final String RESPONSE_XML =
            "<data xmlns:q1=\"http://integrations.e-qazyna.kz\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"q1:IntegraResponse\">\n"
            + "  <IsSuccess>true</IsSuccess>\n"
            + "  <Status>Success</Status>\n"
            + "</data>";

    /** Пример ответа при несовпадении versionHash. */
    public static final String RESPONSE_XML_INVALID_HASH =
            "<data xmlns:q1=\"http://integrations.e-qazyna.kz\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"q1:IntegraResponse\">\n"
            + "  <IsSuccess>false</IsSuccess>\n"
            + "  <ErrorMessage>versionHash события 05ef2486-d9e0-4ec6-9b25-a75bc629d13e не совпадает</ErrorMessage>\n"
            + "  <Status>InvalidHash</Status>\n"
            + "</data>";

    public static IntegraResponse response() {
        return IntegraResponse.parse(RESPONSE_XML);
    }

    public static void main(String[] args) {
        IntegraRequest request = request();
        System.out.println("----- request <data> -----");
        System.out.println(request.toDataXml());
        System.out.println("----- распакованный JSON пакета событий -----");
        System.out.println(MisEventsPacker.unpackJson(request.zippedEventsInBase64));
        System.out.println("----- response -----");
        System.out.println(response());
        System.out.println(IntegraResponse.parse(RESPONSE_XML_INVALID_HASH));
    }
}
