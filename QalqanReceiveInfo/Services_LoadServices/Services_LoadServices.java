package qalqan.receiveinfo.examples.services_loadservices;

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
 * QalqanReceiveInfo / Services_LoadServices — медицинские услуги.
 * Файл самодостаточен: DTO запроса, DTO ответа и заполненный пример.
 * Формируется только бизнес-нагрузка <data> (содержимое requestData / responseData конверта ШЭП);
 * сам конверт SendMessage, requestInfo и транспортная подпись — стандартная часть ШЭП и здесь не рассматриваются.
 * Паспорт сервиса: https://sb.egov.kz/services/passport/ORGAM-S-1170
 */
public final class Services_LoadServices {

    private Services_LoadServices() {
    }

    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"> с RequestType = Services_LoadServices
    // =====================================================================================

    public static final class IntegraRequest {
        public static final String REQUEST_TYPE = "Services_LoadServices";
        public final LoadServicesRequest Services_LoadServicesRequest;

        public IntegraRequest(LoadServicesRequest Services_LoadServicesRequest) {
            this.Services_LoadServicesRequest = Services_LoadServicesRequest;
        }

        /** XML элемента {@code <data>} — помещается в requestData конверта ШЭП. */
        public String toDataXml() {
            Document doc = Xml.newDocument();
            Element data = doc.createElement("data");
            data.setAttribute("xmlns:q1", "http://integrations.gosreestr.kz");
            data.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", "q1:IntegraRequest");
            doc.appendChild(data);
            Xml.text(data, "RequestType", REQUEST_TYPE);
            Element body = Xml.child(data, "Services_LoadServicesRequest");
            Services_LoadServicesRequest.writeTo(body);
            return Xml.toString(doc, true);
        }
    }

    // =====================================================================================
    // ОТВЕТ: <data xsi:type="q1:IntegraResponse">
    // =====================================================================================

    public static final class IntegraResponse {
        public final ResponseInfo responseInfo;
        /** Блок результата (есть только при statusCode = 200), иначе null. */
        public final LoadResult result;

        public IntegraResponse(ResponseInfo responseInfo, LoadResult result) {
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
            Element payload = Xml.directChild(data, "Services_LoadServicesResponse");
            return new IntegraResponse(info, payload == null ? null : LoadResult.parse(payload));
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

    /** Пакет медицинских услуг (Services_LoadServices / Services_LoadOrUpdateServices). */
    public static class LoadServicesRequest implements XmlWritable {
        public List<ServiceItemRequest> list = new ArrayList<>();

        public LoadServicesRequest add(ServiceItemRequest item) {
            list.add(item);
            return this;
        }

        @Override
        public void writeTo(Element e) {
            Xml.list(e, "List", "ServiceItemRequest", list);
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

    /** Единичная медицинская услуга. */
    public static class ServiceItemRequest implements XmlWritable {
        /** Уникальный идентификатор услуги в системе-источнике (обязателен). */
        public long serviceId;
        /** Дата/время оказания услуги (обязательно). */
        public OffsetDateTime serviceAt;
        /** Заказчик услуги (обязателен, должен содержать orgSurId). */
        public PartyBinRequest customer;
        /** Исполнитель услуги (обязателен, должен содержать orgSurId). */
        public ExecutorBinRequest executor;
        /** Врач (обязателен всегда, кроме услуг класса B). */
        public PartyIinRequest doctor;
        /** Пациент (обязателен). */
        public PatientRequest patient;
        /** Сведения о беременности пациентки. */
        public PatientPregnancyInfoRequest patientPregnancyInfo;
        /** Категория пациента (справочник ЕПС). Обязателен. */
        public PersonCategory personCategory;
        /** Источник финансирования (обязателен). */
        public FinanceSource epsFinanceSource;
        /** Данные направления (опционально). */
        public ReferralRequest referral;
        /** Повод обращения — строковый код ЕПС (см. {@link DictionaryCodes.VisitReason}). Обязателен. */
        public String visitReason;
        /** Место оказания услуги — строковый код ЕПС (см. {@link DictionaryCodes.ServicePlace}). Обязателен. */
        public String servicePlace;
        /** Код услуги по тарификатору (обязателен). */
        public String serviceCode;
        /** Код диагноза МКБ-10 (обязателен). */
        public String diagnosisCode;
        /** Признаки платной услуги. */
        public PaidServiceRequest paidService;
        /** Идентификаторы скринингов ЕПС ({@code <ScreeningIds><string>..</string></ScreeningIds>}). */
        public List<String> screeningIds;
        /** Информация об удалении услуги. */
        public ServiceDeletionInfo deletionInfo;
        /** Метод идентификации пациента (опционально). */
        public IdentificationMethodRequest identificationMethod;

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "ServiceId", serviceId);
            Xml.text(e, "ServiceAt", serviceAt);
            Xml.object(e, "Customer", customer);
            Xml.object(e, "Executor", executor);
            Xml.object(e, "Doctor", doctor);
            Xml.object(e, "Patient", patient);
            Xml.object(e, "PatientPregnancyInfo", patientPregnancyInfo);
            Xml.text(e, "PersonCategory", personCategory);
            Xml.text(e, "EpsFinanceSource", epsFinanceSource);
            Xml.object(e, "Referral", referral);
            Xml.text(e, "VisitReason", visitReason);
            Xml.text(e, "ServicePlace", servicePlace);
            Xml.text(e, "ServiceCode", serviceCode);
            Xml.text(e, "DiagnosisCode", diagnosisCode);
            Xml.object(e, "PaidService", paidService);
            Xml.strings(e, "ScreeningIds", screeningIds);
            Xml.object(e, "DeletionInfo", deletionInfo);
            Xml.object(e, "IdentificationMethod", identificationMethod);
        }
    }

    /** Информация об удалении услуги. */
    public static class ServiceDeletionInfo implements XmlWritable {
        /** Момент, когда услуга помечена удалённой. */
        public OffsetDateTime deletedAt;

        public ServiceDeletionInfo() {
        }

        public ServiceDeletionInfo(OffsetDateTime deletedAt) {
            this.deletedAt = deletedAt;
        }

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "DeletedAt", deletedAt);
        }
    }

    /** Данные направления. */
    public static class ReferralRequest implements XmlWritable {
        /** Идентификатор направления ЕПС. Обязателен. */
        public String epsReferralId;
        /** Идентификатор направления в МИС. */
        public Long referralId;
        /** Дата/время направления. Обязательна. */
        public OffsetDateTime referralAt;
        /** Код услуги направления по тарификатору. Обязателен. */
        public String referralServiceCode;
        /** Код диагноза направления (МКБ-10). Обязателен. */
        public String referralDiagnosisCode;
        /** Повод обращения (код ЕПС). Обязателен. */
        public String referralVisitReason;

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "EpsReferralId", epsReferralId);
            Xml.text(e, "ReferralId", referralId);
            Xml.text(e, "ReferralAt", referralAt);
            Xml.text(e, "ReferralServiceCode", referralServiceCode);
            Xml.text(e, "ReferralDiagnosisCode", referralDiagnosisCode);
            Xml.text(e, "ReferralVisitReason", referralVisitReason);
        }
    }

    /** Категория пациента (справочник ЕПС). Передаётся в XML числовым кодом. */
    public enum PersonCategory implements XmlCoded {
        /** Не определено. */
        UNDEFINED(0),
        /** Гражданин РК. */
        KAZAKHSTAN_CITIZEN(1),
        /** Кандас. */
        KANDAS(2),
        /** Трудовой мигрант. */
        LABOR_MIGRANT(3),
        /** Иностранец с заболеванием, представляющим опасность для окружающих. */
        FOREIGNER_WITH_DANGEROUS_INFECTIOUS_DISEASE(4),
        /** Мертворожденный ребёнок. */
        STILLBORN_CHILD(5),
        /** Ранняя неонатальная смерть (до 28 дней). */
        EARLY_NEONATAL_DEATH_UP_TO_28_DAYS(6),
        /** Лицо без определённого места жительства. */
        HOMELESS_PERSON(7),
        /** Неустановленное лицо. */
        UNIDENTIFIED_PERSON(8),
        /** Постоянно проживающий иностранец / лицо без гражданства. */
        PERMANENT_RESIDENT_FOREIGNER_OR_STATELESS_PERSON(9),
        /** Недоношенный плод. */
        PREMATURE_FETUS(10),
        /** Временно пребывающий иностранец (приложение 21). */
        TEMPORARILY_STAYING_FOREIGNER_BY_APPENDIX_21(11);

        private final int code;

        PersonCategory(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return code;
        }

        public static PersonCategory fromCode(int code) {
            for (PersonCategory v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            throw new IllegalArgumentException("Unknown PersonCategory code: " + code);
        }
    }

    /** Пациент. Допускается ИИН и/или RpnId; дата рождения и пол обязательны. */
    public static class PatientRequest implements XmlWritable {
        /** ИИН пациента (12 цифр). Может отсутствовать, если указан rpnId. */
        public String patientIin;
        /** Идентификатор пациента в РПН. Может использоваться вместо ИИН. */
        public Long rpnId;
        /** Дата рождения (yyyy-MM-dd). */
        public LocalDate birthDate;
        /** Пол. */
        public PatientSex sex;

        public PatientRequest() {
        }

        public PatientRequest(String patientIin, Long rpnId, LocalDate birthDate, PatientSex sex) {
            this.patientIin = patientIin;
            this.rpnId = rpnId;
            this.birthDate = birthDate;
            this.sex = sex;
        }

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "PatientIin", patientIin);
            Xml.text(e, "RpnId", rpnId);
            Xml.text(e, "BirthDate", birthDate);
            Xml.text(e, "Sex", sex);
        }
    }

    /** Пол пациента. Передаётся в XML числовым кодом. */
    public enum PatientSex implements XmlCoded {
        /** Женский. */
        FEMALE(0),
        /** Мужской. */
        MALE(1),
        /** Не определено. */
        UNSPECIFIED(2);

        private final int code;

        PatientSex(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return code;
        }

        public static PatientSex fromCode(int code) {
            for (PatientSex v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            throw new IllegalArgumentException("Unknown PatientSex code: " + code);
        }
    }

    /** Сведения о беременности пациентки (влияют на оплату услуги). */
    public static class PatientPregnancyInfoRequest implements XmlWritable {
        /** Дата постановки на учёт по беременности. */
        public OffsetDateTime registeredAt;
        /** Срок беременности, недели. */
        public int gestationalAgeWeeks;

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "RegisteredAt", registeredAt);
            Xml.text(e, "GestationalAgeWeeks", gestationalAgeWeeks);
        }
    }

    /** Врач. */
    public static class PartyIinRequest implements XmlWritable {
        /** ИИН врача (12 цифр). */
        public String doctorIin;

        public PartyIinRequest() {
        }

        public PartyIinRequest(String doctorIin) {
            this.doctorIin = doctorIin;
        }

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "DoctorIin", doctorIin);
        }
    }

    /** Организация: БИН (12 цифр) и/или SurId в реестре. */
    public static class PartyBinRequest implements XmlWritable {
        /** БИН организации (12 цифр). Опционален, если указан orgSurId. */
        public String bin;
        /** Идентификатор организации в реестре (SurId). Обязателен. */
        public long orgSurId;

        public PartyBinRequest() {
        }

        public PartyBinRequest(String bin, long orgSurId) {
            this.bin = bin;
            this.orgSurId = orgSurId;
        }

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "Bin", bin);
            Xml.text(e, "OrgSurId", orgSurId);
        }
    }

    /** Признаки платной услуги. Если объект задан — amountTg должен быть больше 0. */
    public static class PaidServiceRequest implements XmlWritable {
        public PaymentSource paymentSource;
        /** Сумма оплаты, тенге. */
        public BigDecimal amountTg;

        public PaidServiceRequest() {
        }

        public PaidServiceRequest(PaymentSource paymentSource, BigDecimal amountTg) {
            this.paymentSource = paymentSource;
            this.amountTg = amountTg;
        }

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "PaymentSource", paymentSource);
            Xml.text(e, "AmountTg", amountTg);
        }
    }

    /** Источник оплаты платной услуги. Передаётся в XML числовым кодом. */
    public enum PaymentSource implements XmlCoded {
        /** Пациент. */
        PATIENT(1),
        /** ДМС. */
        DMS(2),
        /** Другое. */
        OTHER(3);

        private final int code;

        PaymentSource(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return code;
        }

        public static PaymentSource fromCode(int code) {
            for (PaymentSource v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            throw new IllegalArgumentException("Unknown PaymentSource code: " + code);
        }
    }

    /** Метод идентификации пациента. */
    public static class IdentificationMethodRequest implements XmlWritable {
        public IdentificationMethodType type;

        public IdentificationMethodRequest() {
        }

        public IdentificationMethodRequest(IdentificationMethodType type) {
            this.type = type;
        }

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "Type", type);
        }
    }

    /** Метод идентификации пациента. Передаётся в XML числовым кодом. */
    public enum IdentificationMethodType implements XmlCoded {
        /** Биометрическая идентификация. */
        BIOMETRIC(1),
        /** Код доступа к цифровым документам. */
        DIGITAL_DOCS_ACCESS_CODE(2),
        /** Другое. */
        OTHER(3),
        /** Не производилась. */
        NOT_PERFORMED(4);

        private final int code;

        IdentificationMethodType(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return code;
        }

        public static IdentificationMethodType fromCode(int code) {
            for (IdentificationMethodType v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            throw new IllegalArgumentException("Unknown IdentificationMethodType code: " + code);
        }
    }

    /** Источник финансирования (BgFinanceSource / EpsFinanceSource). Передаётся в XML числовым кодом. */
    public enum FinanceSource implements XmlCoded {
        /** ГОБМП. */
        GOBMP(0),
        /** ОСМС. */
        OSMS(1),
        /** Другое. */
        OTHER(2);

        private final int code;

        FinanceSource(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return code;
        }

        public static FinanceSource fromCode(int code) {
            for (FinanceSource v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            throw new IllegalArgumentException("Unknown FinanceSource code: " + code);
        }
    }

    /** Организация-исполнитель услуги. */
    public static class ExecutorBinRequest implements XmlWritable {
        /** БИН организации-исполнителя (12 цифр). */
        public String bin;
        /** Идентификатор организации-исполнителя в реестре (SurId). Обязателен. */
        public long orgSurId;
        /** Идентификатор отделения (SurId). Имя элемента именно {@code DepartmentSurid}. */
        public Long departmentSurid;

        public ExecutorBinRequest() {
        }

        public ExecutorBinRequest(String bin, long orgSurId, Long departmentSurid) {
            this.bin = bin;
            this.orgSurId = orgSurId;
            this.departmentSurid = departmentSurid;
        }

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "Bin", bin);
            Xml.text(e, "OrgSurId", orgSurId);
            Xml.text(e, "DepartmentSurid", departmentSurid);
        }
    }

    /**
     * Результат загрузки пакета (случаи дневного/круглосуточного стационара, приёмного покоя, услуги):
     * счётчики и список ошибок по записям.
     */
    public static final class LoadResult {
        public final int total;
        public final int inserted;
        public final int updated;
        public final int unchanged;
        public final List<RecordError> errors;

        public LoadResult(int total, int inserted, int updated, int unchanged, List<RecordError> errors) {
            this.total = total;
            this.inserted = inserted;
            this.updated = updated;
            this.unchanged = unchanged;
            this.errors = Collections.unmodifiableList(errors);
        }

        /** Разбирает элемент {@code *_LoadCasesResponse} / {@code *_LoadServicesResponse}. */
        public static LoadResult parse(Element el) {
            if (el == null) {
                return null;
            }
            List<RecordError> errors = new ArrayList<>();
            Element errorsEl = Xml.directChild(el, "Errors");
            if (errorsEl != null) {
                NodeList children = errorsEl.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node n = children.item(i);
                    if (n instanceof Element) {
                        errors.add(RecordError.parse((Element) n));
                    }
                }
            }
            return new LoadResult(
                    intOrZero(Xml.childInt(el, "Total")),
                    intOrZero(Xml.childInt(el, "Inserted")),
                    intOrZero(Xml.childInt(el, "Updated")),
                    intOrZero(Xml.childInt(el, "Unchanged")),
                    errors);
        }

        private static int intOrZero(Integer v) {
            return v == null ? 0 : v;
        }

        @Override
        public String toString() {
            return "LoadResult{total=" + total + ", inserted=" + inserted + ", updated=" + updated
                    + ", unchanged=" + unchanged + ", errors=" + errors.size() + "}";
        }
    }

    /**
     * Ошибка по одной записи пакета ({@code DayHospitalCaseErrorDto}, {@code InpatientCaseErrorDto},
     * {@code ReceptionRoomCaseErrorDto}, {@code ServiceErrorDto}). Общие поля вынесены, остальные доступны в {@link #fields}.
     */
    public static final class RecordError {
        /** Порядковый номер записи в исходном запросе (с 0); -1 если не определён. */
        public final int recIndex;
        /** Идентификатор записи: CaseId / AdmissionId / ServiceId — в зависимости от типа пакета. */
        public final Long recordId;
        public final String patientIin;
        public final Long patientRpnId;
        public final String code;
        public final String message;
        /** Все дочерние элементы ошибки как есть (имя -> текст). */
        public final Map<String, String> fields;

        public RecordError(int recIndex, Long recordId, String patientIin, Long patientRpnId, String code, String message, Map<String, String> fields) {
            this.recIndex = recIndex;
            this.recordId = recordId;
            this.patientIin = patientIin;
            this.patientRpnId = patientRpnId;
            this.code = code;
            this.message = message;
            this.fields = Collections.unmodifiableMap(fields);
        }

        public static RecordError parse(Element el) {
            Map<String, String> fields = new LinkedHashMap<>();
            NodeList children = el.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node n = children.item(i);
                if (n instanceof Element) {
                    Element c = (Element) n;
                    fields.put(Xml.localName(c), Xml.childText(el, Xml.localName(c)));
                }
            }
            Long recordId = Xml.childLong(el, "CaseId");
            if (recordId == null) {
                recordId = Xml.childLong(el, "AdmissionId");
            }
            if (recordId == null) {
                recordId = Xml.childLong(el, "ServiceId");
            }
            Integer recIndex = Xml.childInt(el, "RecIndex");
            return new RecordError(
                    recIndex == null ? -1 : recIndex,
                    recordId,
                    Xml.childText(el, "PatientIin"),
                    Xml.childLong(el, "PatientRpnId"),
                    Xml.childText(el, "Code"),
                    Xml.childText(el, "Message"),
                    fields);
        }

        @Override
        public String toString() {
            return "RecordError{recIndex=" + recIndex + ", recordId=" + recordId + ", code=" + code + ", message=" + message + "}";
        }
    }

    // =====================================================================================
    // ПРИМЕР
    // =====================================================================================

    /** Заполненный запрос. */
    public static IntegraRequest request() {
        ServiceItemRequest s = new ServiceItemRequest();
        s.serviceId = 9005L;
        s.serviceAt = OffsetDateTime.of(2026, 4, 10, 10, 30, 0, 0, ZoneOffset.UTC);
        s.customer = new PartyBinRequest("990140000001", 100001L);
        s.executor = new ExecutorBinRequest("990140000001", 100001L, 200000000000001L);
        s.doctor = new PartyIinRequest("900101300001");
        s.patient = new PatientRequest("950101300003", null, LocalDate.of(1995, 1, 1), PatientSex.MALE);
        s.personCategory = PersonCategory.KAZAKHSTAN_CITIZEN;           // 1
        s.epsFinanceSource = FinanceSource.OSMS;                         // 1
        s.visitReason = "17";                                            // скрининг (код ЕПС)
        s.servicePlace = "П";                                            // в поликлинике (код ЕПС)
        s.serviceCode = "B01.047.001";
        s.diagnosisCode = "J06.9";
        ReferralRequest r = new ReferralRequest();
        r.epsReferralId = "1234567890123456789012345678901234567890";
        r.referralId = 12345L;
        r.referralAt = OffsetDateTime.of(2026, 4, 9, 9, 0, 0, 0, ZoneOffset.UTC);
        r.referralServiceCode = "B01.047.001";
        r.referralDiagnosisCode = "J06.9";
        r.referralVisitReason = "20";                                    // антенатальное наблюдение
        s.referral = r;
        return new IntegraRequest(new LoadServicesRequest().add(s));
    }

    /** Пример ответа сервиса (элемент {@code <data>} из responseData). */
    public static final String RESPONSE_XML =
            "<data xmlns:q1=\"http://integrations.gosreestr.kz\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"q1:IntegraResponse\">\n"
            + "  <ResponseInfo>\n"
            + "    <RequestType>Services_LoadServices</RequestType>\n"
            + "    <StatusCode>200</StatusCode>\n"
            + "    <Message>OK</Message>\n"
            + "  </ResponseInfo>\n"
            + "  <Services_LoadServicesResponse>\n"
            + "    <Total>1</Total>\n"
            + "    <Inserted>1</Inserted>\n"
            + "    <Updated>0</Updated>\n"
            + "    <Unchanged>0</Unchanged>\n"
            + "    <Errors></Errors>\n"
            + "  </Services_LoadServicesResponse>\n"
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
