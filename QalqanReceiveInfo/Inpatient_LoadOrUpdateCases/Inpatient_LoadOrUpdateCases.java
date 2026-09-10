package qalqan.receiveinfo.examples.inpatient_loadorupdatecases;

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
 * QalqanReceiveInfo / Inpatient_LoadOrUpdateCases — случаи круглосуточного стационара.
 * Файл самодостаточен: DTO запроса, DTO ответа и заполненный пример.
 * Формируется только бизнес-нагрузка <data> (содержимое requestData / responseData конверта ШЭП);
 * сам конверт SendMessage, requestInfo и транспортная подпись — стандартная часть ШЭП и здесь не рассматриваются.
 * Паспорт сервиса: https://sb.egov.kz/services/passport/ORGAM-S-1170
 */
public final class Inpatient_LoadOrUpdateCases {

    private Inpatient_LoadOrUpdateCases() {
    }

    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"> с RequestType = Inpatient_LoadOrUpdateCases
    // =====================================================================================

    public static final class IntegraRequest {
        public static final String REQUEST_TYPE = "Inpatient_LoadOrUpdateCases";
        public final LoadInpatientCasesRequest Inpatient_LoadOrUpdateCasesRequest;

        public IntegraRequest(LoadInpatientCasesRequest Inpatient_LoadOrUpdateCasesRequest) {
            this.Inpatient_LoadOrUpdateCasesRequest = Inpatient_LoadOrUpdateCasesRequest;
        }

        /** XML элемента {@code <data>} — помещается в requestData конверта ШЭП. */
        public String toDataXml() {
            Document doc = Xml.newDocument();
            Element data = doc.createElement("data");
            data.setAttribute("xmlns:q1", "http://integrations.gosreestr.kz");
            data.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", "q1:IntegraRequest");
            doc.appendChild(data);
            Xml.text(data, "RequestType", REQUEST_TYPE);
            Element body = Xml.child(data, "Inpatient_LoadOrUpdateCasesRequest");
            Inpatient_LoadOrUpdateCasesRequest.writeTo(body);
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
            Element payload = Xml.directChild(data, "Inpatient_LoadOrUpdateCasesResponse");
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

    /** Пакет случаев круглосуточного стационара (Inpatient_LoadCases / Inpatient_LoadOrUpdateCases). */
    public static class LoadInpatientCasesRequest implements XmlWritable {
        public List<InpatientCaseRequest> list = new ArrayList<>();

        public LoadInpatientCasesRequest add(InpatientCaseRequest item) {
            list.add(item);
            return this;
        }

        @Override
        public void writeTo(Element e) {
            Xml.list(e, "List", "InpatientCaseRequest", list);
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

    /** Пролеченный случай круглосуточного стационара. */
    public static class InpatientCaseRequest implements XmlWritable {
        public long caseId;
        public PartyBinRequest organization;
        public long departmentSurId;
        public PartyIinRequest doctor;
        public PatientRequest patient;
        /** Признак первичной госпитализации. */
        public boolean isPrimaryHospitalization;
        public String cardNumber;
        /** Код профиля койки (справочник). */
        public int bedProfileCode;
        /** Тип госпитализации (см. {@link DictionaryCodes.HospitalizationType}). */
        public int hospitalizationTypeCode;
        public LocalDate admissionDate;
        public LocalDate dischargeDate;
        /** Идентификатор направления. */
        public Long referralId;
        /** Основной диагноз по направлению (МКБ-10). */
        public String referralPrimaryDiagnosisCode;
        /** Предварительный основной диагноз. */
        public String preliminaryPrimaryDiagnosisCode;
        /** Основной заключительный диагноз (МКБ-10). Обязателен. */
        public String primaryFinalDiagnosisCode;
        /** Осложняющие заключительные диагнозы (элементы {@code <string>}). */
        public List<String> complicatingFinalDiagnosisCode = new ArrayList<>();
        public List<String> clarifyingFinalDiagnosisCodes = new ArrayList<>();
        public List<String> concomitantFinalDiagnosisCodes = new ArrayList<>();
        public CasePrimaryOperationRequest primaryOperation;
        public List<AdditionalOperationItemRequest> additionalOperations = new ArrayList<>();
        public List<InpatientServiceItemRequest> services = new ArrayList<>();
        public List<InpatientDrugItemRequest> drugs = new ArrayList<>();
        public PaidServiceRequest paidService;
        /** Исход пребывания (см. {@link DictionaryCodes.StayOutcome}). */
        public int stayOutcomeCode;
        /** Исход лечения (см. {@link DictionaryCodes.TreatmentOutcome}). */
        public int treatmentOutcomeCode;
        /** Онкологический блок. */
        public List<OncologyCaseInfo> oncologyCases = new ArrayList<>();
        /** Акушерский блок (роды). */
        public MaternityCaseInfo maternityCase;
        /** Блок новорождённого. */
        public NewbornCaseInfo newbornCase;
        public FinanceSource bgFinanceSource;
        public IdentificationMethodRequest identificationMethod;

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "CaseId", caseId);
            Xml.object(e, "Organization", organization);
            Xml.text(e, "DepartmentSurId", departmentSurId);
            Xml.object(e, "Doctor", doctor);
            Xml.object(e, "Patient", patient);
            Xml.text(e, "IsPrimaryHospitalization", isPrimaryHospitalization);
            Xml.text(e, "CardNumber", cardNumber);
            Xml.text(e, "BedProfileCode", bedProfileCode);
            Xml.text(e, "HospitalizationTypeCode", hospitalizationTypeCode);
            Xml.text(e, "AdmissionDate", admissionDate);
            Xml.text(e, "DischargeDate", dischargeDate);
            Xml.text(e, "ReferralId", referralId);
            Xml.text(e, "ReferralPrimaryDiagnosisCode", referralPrimaryDiagnosisCode);
            Xml.text(e, "PreliminaryPrimaryDiagnosisCode", preliminaryPrimaryDiagnosisCode);
            Xml.text(e, "PrimaryFinalDiagnosisCode", primaryFinalDiagnosisCode);
            Xml.strings(e, "ComplicatingFinalDiagnosisCode", complicatingFinalDiagnosisCode);
            Xml.strings(e, "ClarifyingFinalDiagnosisCodes", clarifyingFinalDiagnosisCodes);
            Xml.strings(e, "ConcomitantFinalDiagnosisCodes", concomitantFinalDiagnosisCodes);
            Xml.object(e, "PrimaryOperation", primaryOperation);
            Xml.list(e, "AdditionalOperations", "AdditionalOperationItemRequest", additionalOperations);
            Xml.list(e, "Services", "InpatientServiceItemRequest", services);
            Xml.list(e, "Drugs", "InpatientDrugItemRequest", drugs);
            Xml.object(e, "PaidService", paidService);
            Xml.text(e, "StayOutcomeCode", stayOutcomeCode);
            Xml.text(e, "TreatmentOutcomeCode", treatmentOutcomeCode);
            Xml.list(e, "OncologyCases", "OncologyCaseInfo", oncologyCases);
            Xml.object(e, "MaternityCase", maternityCase);
            Xml.object(e, "NewbornCase", newbornCase);
            Xml.text(e, "BgFinanceSource", bgFinanceSource);
            Xml.object(e, "IdentificationMethod", identificationMethod);
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

    /** Онкологические сведения по случаю. */
    public static class OncologyCaseInfo implements XmlWritable {
        public boolean isOncologyDiagnosedFirstTime;
        public OncologyTreatmentMethod treatmentMethod;
        /** Текст схемы лечения (сохраняется как есть). */
        public String treatmentScheme;

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "IsOncologyDiagnosedFirstTime", isOncologyDiagnosedFirstTime);
            Xml.text(e, "TreatmentMethod", treatmentMethod);
            Xml.text(e, "TreatmentScheme", treatmentScheme);
        }
    }

    /** Метод лечения онкологического случая. Передаётся в XML числовым кодом. */
    public enum OncologyTreatmentMethod implements XmlCoded {
        /** С химиотерапией (индукция/консолидация). */
        WITH_CHEMOTHERAPY_INDUCTION_CONSOLIDATION(1),
        /** С химиотерапией. */
        WITH_CHEMOTHERAPY(2),
        /** Без химиотерапии. */
        WITHOUT_CHEMOTHERAPY(3);

        private final int code;

        OncologyTreatmentMethod(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return code;
        }

        public static OncologyTreatmentMethod fromCode(int code) {
            for (OncologyTreatmentMethod v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            throw new IllegalArgumentException("Unknown OncologyTreatmentMethod code: " + code);
        }
    }

    /** Сведения по новорождённому. */
    public static class NewbornCaseInfo implements XmlWritable {
        public NewbornBirthData birthData = new NewbornBirthData();

        public NewbornCaseInfo() {
        }

        public NewbornCaseInfo(int gestationWeekAtDelivery, int newbornWeightAtBirthGrams) {
            this.birthData.gestationWeekAtDelivery = gestationWeekAtDelivery;
            this.birthData.newbornWeightAtBirthGrams = newbornWeightAtBirthGrams;
        }

        @Override
        public void writeTo(Element e) {
            Xml.object(e, "BirthData", birthData);
        }
    }

    /** Данные при рождении. */
    public static class NewbornBirthData implements XmlWritable {
        /** Срок беременности на момент родов (недели). */
        public int gestationWeekAtDelivery;
        /** Вес при рождении (граммы). */
        public int newbornWeightAtBirthGrams;

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "GestationWeekAtDelivery", gestationWeekAtDelivery);
            Xml.text(e, "NewbornWeightAtBirthGrams", newbornWeightAtBirthGrams);
        }
    }

    /** Акушерский блок по случаю (роды). */
    public static class MaternityCaseInfo implements XmlWritable {
        public boolean isRepeatedHospitalizationForDelivery;
        public int gestationWeekAtDelivery;
        public int fetusesCount;
        public List<NewbornFetusInfo> fetuses = new ArrayList<>();
        public List<String> deliveryComplicationDiagnosisCodes = new ArrayList<>();
        /** Обязателен для диагноза O67.8. */
        public MaternityCaseBloodLoss bloodLoss;
        /** Обязателен для диагноза O98.0. */
        public MaternityCaseInfectionActivity infectionActivity;
        /** Обязателен для диагноза O99.0. */
        public MaternityCaseHemoglobinLevel hemoglobinLevel;

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "IsRepeatedHospitalizationForDelivery", isRepeatedHospitalizationForDelivery);
            Xml.text(e, "GestationWeekAtDelivery", gestationWeekAtDelivery);
            Xml.text(e, "FetusesCount", fetusesCount);
            Xml.list(e, "Fetuses", "NewbornFetusInfo", fetuses);
            Xml.strings(e, "DeliveryComplicationDiagnosisCodes", deliveryComplicationDiagnosisCodes);
            Xml.text(e, "BloodLoss", bloodLoss);
            Xml.text(e, "InfectionActivity", infectionActivity);
            Xml.text(e, "HemoglobinLevel", hemoglobinLevel);
        }
    }

    /** Данные по одному плоду/новорождённому при родах. */
    public static class NewbornFetusInfo implements XmlWritable {
        /** Номер плода (с 1). */
        public int fetusNumber;
        public OffsetDateTime birthDateTime;
        public int heightCm;
        public PatientSex sex;
        public int newbornWeightGrams;

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "FetusNumber", fetusNumber);
            Xml.text(e, "BirthDateTime", birthDateTime);
            Xml.text(e, "HeightCm", heightCm);
            Xml.text(e, "Sex", sex);
            Xml.text(e, "NewbornWeightGrams", newbornWeightGrams);
        }
    }

    /** Степень активности инфекции (обязателен для диагноза O98.0). Передаётся в XML числовым кодом. */
    public enum MaternityCaseInfectionActivity implements XmlCoded {
        /** Неактивный. */
        INACTIVE(1),
        /** Активная / сомнительная активность. */
        ACTIVE_OR_DUBIOUS(2);

        private final int code;

        MaternityCaseInfectionActivity(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return code;
        }

        public static MaternityCaseInfectionActivity fromCode(int code) {
            for (MaternityCaseInfectionActivity v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            throw new IllegalArgumentException("Unknown MaternityCaseInfectionActivity code: " + code);
        }
    }

    /** Уровень гемоглобина (обязателен для диагноза O99.0). Передаётся в XML числовым кодом. */
    public enum MaternityCaseHemoglobinLevel implements XmlCoded {
        /** 69 и ниже. */
        HB_UP_TO_69(1),
        /** 70 - 109. */
        HB_70_TO_109(2);

        private final int code;

        MaternityCaseHemoglobinLevel(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return code;
        }

        public static MaternityCaseHemoglobinLevel fromCode(int code) {
            for (MaternityCaseHemoglobinLevel v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            throw new IllegalArgumentException("Unknown MaternityCaseHemoglobinLevel code: " + code);
        }
    }

    /** Уровень кровопотери (обязателен для диагноза O67.8). Передаётся в XML числовым кодом. */
    public enum MaternityCaseBloodLoss implements XmlCoded {
        /** 1 литр и менее. */
        UP_TO_1_LITER(1),
        /** Более 1 литра. */
        MORE_THAN_1_LITER(2);

        private final int code;

        MaternityCaseBloodLoss(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return code;
        }

        public static MaternityCaseBloodLoss fromCode(int code) {
            for (MaternityCaseBloodLoss v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            throw new IllegalArgumentException("Unknown MaternityCaseBloodLoss code: " + code);
        }
    }

    /** Услуга в случае круглосуточного стационара. */
    public static class InpatientServiceItemRequest implements XmlWritable {
        public String serviceCode;
        public int quantity;
        public LocalDate date;

        public InpatientServiceItemRequest() {
        }

        public InpatientServiceItemRequest(String serviceCode, int quantity, LocalDate date) {
            this.serviceCode = serviceCode;
            this.quantity = quantity;
            this.date = date;
        }

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "ServiceCode", serviceCode);
            Xml.text(e, "Quantity", quantity);
            Xml.text(e, "Date", date);
        }
    }

    /** Препарат в случае круглосуточного стационара. */
    public static class InpatientDrugItemRequest implements XmlWritable {
        public String drugCode;
        public String registrationNumber;
        public BigDecimal quantity;
        public BigDecimal actualCostTg;

        public InpatientDrugItemRequest() {
        }

        public InpatientDrugItemRequest(String drugCode, String registrationNumber, BigDecimal quantity, BigDecimal actualCostTg) {
            this.drugCode = drugCode;
            this.registrationNumber = registrationNumber;
            this.quantity = quantity;
            this.actualCostTg = actualCostTg;
        }

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "DrugCode", drugCode);
            Xml.text(e, "RegistrationNumber", registrationNumber);
            Xml.text(e, "Quantity", quantity);
            Xml.text(e, "ActualCostTg", actualCostTg);
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

    /** Основная операция случая (код + дата/время). Поля опциональны, но задаются вместе. */
    public static class CasePrimaryOperationRequest implements XmlWritable {
        /** Код основной операции (МКБ-9). */
        public String code;
        /** Дата/время основной операции. */
        public OffsetDateTime date;
        /** Осложнения операции. */
        public List<OperationComplicationItemRequest> operationComplications = new ArrayList<>();

        public CasePrimaryOperationRequest() {
        }

        public CasePrimaryOperationRequest(String code, OffsetDateTime date) {
            this.code = code;
            this.date = date;
        }

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "Code", code);
            Xml.text(e, "Date", date);
            Xml.list(e, "OperationComplications", "OperationComplicationItemRequest", operationComplications);
        }
    }

    /** Осложнение операции. */
    public static class OperationComplicationItemRequest implements XmlWritable {
        /** Код осложнения из справочника (300, 400, 500 ...). */
        public int complicationCode;
        /** Тип осложнения: 100 — общее, 200 — местное. */
        public ComplicationType complicationType;

        public OperationComplicationItemRequest() {
        }

        public OperationComplicationItemRequest(int complicationCode, ComplicationType complicationType) {
            this.complicationCode = complicationCode;
            this.complicationType = complicationType;
        }

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "ComplicationCode", complicationCode);
            Xml.text(e, "ComplicationType", complicationType);
        }
    }

    /** Тип осложнения операции. Передаётся в XML числовым кодом. */
    public enum ComplicationType implements XmlCoded {
        /** Общее. */
        GENERAL(100),
        /** Местное. */
        LOCAL(200);

        private final int code;

        ComplicationType(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return code;
        }

        public static ComplicationType fromCode(int code) {
            for (ComplicationType v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            throw new IllegalArgumentException("Unknown ComplicationType code: " + code);
        }
    }

    /** Дополнительная операция: код, дата и осложнения. */
    public static class AdditionalOperationItemRequest implements XmlWritable {
        /** Код операции (МКБ-9). Обязателен. */
        public String operationCode;
        /** Дата/время операции. Обязательна. */
        public OffsetDateTime operationDate;
        public List<OperationComplicationItemRequest> operationComplications = new ArrayList<>();

        public AdditionalOperationItemRequest() {
        }

        public AdditionalOperationItemRequest(String operationCode, OffsetDateTime operationDate) {
            this.operationCode = operationCode;
            this.operationDate = operationDate;
        }

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "OperationCode", operationCode);
            Xml.text(e, "OperationDate", operationDate);
            Xml.list(e, "OperationComplications", "OperationComplicationItemRequest", operationComplications);
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
        /** Внешний идентификатор записи (ExternalId у карт вызова СП). */
        public final String externalId;
        public final String patientIin;
        public final Long patientRpnId;
        public final String code;
        public final String message;
        /** Все дочерние элементы ошибки как есть (имя -> текст). */
        public final Map<String, String> fields;

        public RecordError(int recIndex, Long recordId, String externalId, String patientIin, Long patientRpnId, String code, String message, Map<String, String> fields) {
            this.recIndex = recIndex;
            this.recordId = recordId;
            this.externalId = externalId;
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
                    Xml.childText(el, "ExternalId"),
                    Xml.childText(el, "PatientIin"),
                    Xml.childLong(el, "PatientRpnId"),
                    Xml.childText(el, "Code"),
                    Xml.childText(el, "Message"),
                    fields);
        }

        @Override
        public String toString() {
            return "RecordError{recIndex=" + recIndex + ", recordId=" + recordId + ", externalId=" + externalId + ", code=" + code + ", message=" + message + "}";
        }
    }

    // =====================================================================================
    // ПРИМЕР
    // =====================================================================================

    /** Заполненный запрос. */
    public static IntegraRequest request() {
        InpatientCaseRequest c = new InpatientCaseRequest();
        c.caseId = 4L;
        c.organization = new PartyBinRequest("990140000001", 100001L);
        c.departmentSurId = 200000000000001L;
        c.doctor = new PartyIinRequest("900101300001");
        c.patient = new PatientRequest(null, null, LocalDate.of(2025, 12, 2), PatientSex.MALE); // новорождённый без ИИН
        c.isPrimaryHospitalization = true;
        c.cardNumber = "6841-1";
        c.bedProfileCode = 15000;
        c.hospitalizationTypeCode = 400;                                 // экстренно свыше 24 ч
        c.admissionDate = LocalDate.of(2025, 12, 4);
        c.dischargeDate = LocalDate.of(2025, 12, 8);
        c.primaryFinalDiagnosisCode = "P59.8";
        c.services.addAll(Arrays.asList(
                new InpatientServiceItemRequest("D99.590.019", 1, LocalDate.of(2025, 12, 3)),
                new InpatientServiceItemRequest("D99.590.019", 1, LocalDate.of(2025, 12, 4)),
                new InpatientServiceItemRequest("B03.435.002", 1, LocalDate.of(2025, 12, 5)),
                new InpatientServiceItemRequest("B03.398.002", 1, LocalDate.of(2025, 12, 5)),
                new InpatientServiceItemRequest("B02.114.002", 1, LocalDate.of(2025, 12, 6))));
        c.stayOutcomeCode = 200;                                         // выписан
        c.treatmentOutcomeCode = 200;                                    // выздоровление
        c.newbornCase = new NewbornCaseInfo(40, 4500);
        c.bgFinanceSource = FinanceSource.GOBMP;                         // 0
        return new IntegraRequest(new LoadInpatientCasesRequest().add(c));
    }

    /** Пример ответа сервиса (элемент {@code <data>} из responseData). */
    public static final String RESPONSE_XML =
            "<data xmlns:q1=\"http://integrations.gosreestr.kz\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"q1:IntegraResponse\">\n"
            + "  <ResponseInfo>\n"
            + "    <RequestType>Inpatient_LoadOrUpdateCases</RequestType>\n"
            + "    <StatusCode>200</StatusCode>\n"
            + "    <Message>OK</Message>\n"
            + "  </ResponseInfo>\n"
            + "  <Inpatient_LoadOrUpdateCasesResponse>\n"
            + "    <Total>1</Total>\n"
            + "    <Inserted>0</Inserted>\n"
            + "    <Updated>1</Updated>\n"
            + "    <Unchanged>0</Unchanged>\n"
            + "    <Errors></Errors>\n"
            + "  </Inpatient_LoadOrUpdateCasesResponse>\n"
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
