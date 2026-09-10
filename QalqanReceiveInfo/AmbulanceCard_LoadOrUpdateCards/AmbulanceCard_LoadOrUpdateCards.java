package qalqan.receiveinfo.examples.ambulancecard_loadorupdatecards;

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
 * QalqanReceiveInfo / AmbulanceCard_LoadOrUpdateCards — карты вызова скорой помощи (актив / госпитализация).
 * Файл самодостаточен: DTO запроса, DTO ответа и заполненный пример.
 * Формируется только бизнес-нагрузка <data> (содержимое requestData / responseData конверта ШЭП);
 * сам конверт SendMessage, requestInfo и транспортная подпись — стандартная часть ШЭП и здесь не рассматриваются.
 * Паспорт сервиса: https://sb.egov.kz/services/passport/ORGAM-S-1170
 */
public final class AmbulanceCard_LoadOrUpdateCards {

    private AmbulanceCard_LoadOrUpdateCards() {
    }

    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"> с RequestType = AmbulanceCard_LoadOrUpdateCards
    // =====================================================================================

    public static final class IntegraRequest {
        public static final String REQUEST_TYPE = "AmbulanceCard_LoadOrUpdateCards";
        public final LoadAmbulanceCardsRequest AmbulanceCard_LoadOrUpdateCardsRequest;

        public IntegraRequest(LoadAmbulanceCardsRequest AmbulanceCard_LoadOrUpdateCardsRequest) {
            this.AmbulanceCard_LoadOrUpdateCardsRequest = AmbulanceCard_LoadOrUpdateCardsRequest;
        }

        /** XML элемента {@code <data>} — помещается в requestData конверта ШЭП. */
        public String toDataXml() {
            Document doc = Xml.newDocument();
            Element data = doc.createElement("data");
            data.setAttribute("xmlns:q1", "http://integrations.gosreestr.kz");
            data.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", "q1:IntegraRequest");
            doc.appendChild(data);
            Xml.text(data, "RequestType", REQUEST_TYPE);
            Element body = Xml.child(data, "AmbulanceCard_LoadOrUpdateCardsRequest");
            AmbulanceCard_LoadOrUpdateCardsRequest.writeTo(body);
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
            Element payload = Xml.directChild(data, "AmbulanceCard_LoadOrUpdateCardsResponse");
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

    /** Пакет карт вызова скорой помощи (AmbulanceCard_LoadCards / AmbulanceCard_LoadOrUpdateCards). Каждая карта — полный снимок: при обновлении непереданные поля обнуляются, MedicalSupplies заменяются целиком. */
    public static class LoadAmbulanceCardsRequest implements XmlWritable {
        public List<AmbulanceCardRequest> list = new ArrayList<>();

        public LoadAmbulanceCardsRequest add(AmbulanceCardRequest item) {
            list.add(item);
            return this;
        }

        @Override
        public void writeTo(Element e) {
            Xml.list(e, "List", "AmbulanceCardRequest", list);
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

    /** Карта вызова скорой помощи: актив (ActiveRegistration) или госпитализация (Hospitalization). Даты и время передаются строками. Обязательны externalId, receiveTypeData, receiveDate. */
    public static class AmbulanceCardRequest implements XmlWritable {
        /** Идентификатор. */
        public Long receiveId;
        /** Идентификатор внешней системы (обязателен). */
        public String externalId;
        /** Идентификатор направившей организации (СУР). */
        public Long senderMoId;
        /** ИИН. */
        public String iin;
        /** Фамилия. */
        public String lastName;
        /** Имя. */
        public String firstName;
        /** Отчество. */
        public String secondName;
        /** Возраст. */
        public Integer age;
        /** Пол: 2 — женский, 3 — мужской. */
        public Integer gender;
        /** Дата рождения (строкой, yyyy-MM-dd). */
        public String birthDate;
        /** Социальный статус (соц. положение, место работы). */
        public String socialStatus;
        /** Дополнительная информация по пациенту. */
        public String patientInfo;
        /** Идентификатор организации прикрепления (СУР). */
        public Long receiverMoId;
        /** Идентификатор поликлиники (СУР) по месту вызова. */
        public Long receiverTerMoId;
        /** Идентификатор пациента (РПН). */
        public Long personRpnId;
        /** ID участка (РПН). */
        public Long territoryServiceId;
        /** Номер участка (РПН). */
        public Integer territoryServiceNumber;
        /** Номер вызова. */
        public String callNumber;
        /** Глобальный номер вызова. */
        public String globalCallNumber;
        /** Приоритет / уровень срочности. */
        public String priority;
        /** Повод вызова. */
        public String reason;
        /** Описание повода вызова. */
        public String descReason;
        /** Адрес вызова. */
        public String emergencyCallAddress;
        /** Кто вызвал. */
        public String caller;
        /** Контактный телефон. */
        public String phone;
        /** Код места вызова. */
        public String place;
        /** Описание места вызова. */
        public String descPlace;
        /** Профиль вызова. */
        public String profile;
        /** Описание профиля вызова. */
        public String descProfile;
        /** Информация о вызове. */
        public String info;
        /** Код результата вызова. */
        public String result;
        /** Описание результата вызова. */
        public String descResult;
        /** Время приёма вызова (ISO 8601). */
        public String callTime;
        /** Время передачи вызова бригаде СП. */
        public String transferTime;
        /** Время выезда бригады СП. */
        public String departureTime;
        /** Время начала госпитализации. */
        public String arrivalTime;
        /** Время прибытия в стационар. */
        public String hospitalTime;
        /** Время прибытия в стационар (факт). */
        public String arrivalHospitalTime;
        /** SUR-код места госпитализации. */
        public Long hospiMoId;
        /** Код диагноза по МКБ-10. */
        public String diagnosis;
        /** Артериальное давление верхнее. */
        public String gemoDynamicsADtop;
        /** Артериальное давление нижнее. */
        public String gemoDynamicsADbottom;
        /** Частота дыхания. */
        public String gemoDynamicsChD;
        /** Частота сердечных сокращений. */
        public String gemoDynamicsChSS;
        /** Температура. */
        public String gemoDynamicsTmp;
        /** Объём оказанной помощи и диагностические исследования бригады. */
        public List<MedicalSuppliesRequest> medicalSupplies = new ArrayList<>();
        /** АД верхнее после терапии. */
        public String gemoDynamicsTherapyADtop;
        /** АД нижнее после терапии. */
        public String gemoDynamicsTherapyADbottom;
        /** Частота дыхания после терапии. */
        public String gemoDynamicsTherapyChD;
        /** ЧСС после терапии. */
        public String gemoDynamicsTherapyChSS;
        /** Температура после терапии. */
        public String gemoDynamicsTherapyTmp;
        /** Номер бригады СП. */
        public String brigadeNumber;
        /** Станция бригады. */
        public String brigadeSMP;
        /** Гос. номер машины СП. */
        public String brigadeCarNumber;
        /** Старший бригады — код (АДИС). */
        public String brigadePersonnelCode;
        /** Старший бригады — ФИО. */
        public String brigadePersonnelName;
        /** Место работы. */
        public String workplace;
        /** Отказ от осмотра / помощи / госпитализации. */
        public Boolean isRefusalAssistance;
        /** Текст отказа. */
        public String refusingText;
        /** Сопутствующие диагнозы (МКБ-10). */
        public List<String> associatedDiseases;
        /** Вид травматизма. */
        public Integer injuryType;
        /** Алкоголь. */
        public Boolean isAlcohol;
        /** Километраж, км. */
        public Integer mileage;
        /** Дата и время прибытия бригады на вызов. */
        public String serviceDate;
        /** Жалобы. */
        public String claim;
        /** Анамнез настоящего заболевания. */
        public String historyIllness;
        /** Анамнез жизни. */
        public String historyCycle;
        /** Общее состояние. */
        public String generalState;
        /** Сознание. */
        public String sense;
        /** Зрачки. */
        public String pupils;
        /** Реакция на свет. */
        public String lightSensitive;
        /** Кожные покровы. */
        public List<String> skinIrritations;
        /** Тоны сердца. */
        public List<String> cardioTones;
        /** Пульс. */
        public List<String> cardioPulse;
        /** Шумы сердца. */
        public String cardioSound;
        /** Экскурсия грудной клетки. */
        public String respiratorySystem;
        /** Дыхание. */
        public List<String> respiratoryBreath;
        /** Поведение. */
        public List<String> behaviours;
        /** Хрипы. */
        public String respiratoryRales;
        /** Одышка. */
        public String shortnessBreath;
        /** Неврологический статус. */
        public String neurologicalEvaluation;
        /** Глазные яблоки. */
        public List<String> eyeballs;
        /** Нервы. */
        public List<String> nerves;
        /** Сухожильные рефлексы. */
        public List<String> tendonReflexes;
        /** Двигательная сфера. */
        public List<String> motorAreas;
        /** Болевая чувствительность. */
        public List<String> painSensitivity;
        /** Афазия. */
        public String aphasia;
        /** Синдромы. */
        public List<String> syndrome;
        /** Зев. */
        public List<String> throat;
        /** Миндалины. */
        public String tonsil;
        /** Язык обложен налётом. */
        public Boolean isCoatedWithBloom;
        /** Живот. */
        public List<String> stomach;
        /** Симптомы. */
        public List<String> symptoms;
        /** Печень. */
        public List<String> liver;
        /** Мочеполовая система. */
        public List<String> urinarySystem;
        /** Менструальный цикл: 1 — без нарушений, 2 — нарушения. */
        public Integer menstrualCycle;
        /** Периферические отёки. */
        public String peripheralEdema;
        /** Сахар в крови, ммоль/л. */
        public String bloodSugar;
        /** Результаты лечения. */
        public String treatmentResult;
        /** Инструментальные методы диагностики. */
        public String diagnosticMethods;
        /** Лечебные мероприятия. */
        public String treatment;
        /** Расход. */
        public String consumption;
        /** Тип данных: ActiveRegistration — актив, Hospitalization — госпитализация (обязателен). */
        public String receiveTypeData;
        /** Дата и время передачи данных (обязательна; при LoadOrUpdate более старая версия игнорируется — StaleVersion). */
        public String receiveDate;
        /** Тип вызова. */
        public String callType;
        /** Описание типа вызова. */
        public String descCallType;
        /** Признак повторного вызова СП. */
        public Boolean isRepeat;
        /** Триаж: 1 — зелёный, 2 — жёлтый, 3 — красный. */
        public Integer triage;
        /** Признак реанимационности. */
        public Boolean isReanimation;
        /** Планируемое время прибытия в стационар. */
        public String arrivalHospitalPlanTime;

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "ReceiveId", receiveId);
            Xml.text(e, "ExternalId", externalId);
            Xml.text(e, "SenderMoId", senderMoId);
            Xml.text(e, "Iin", iin);
            Xml.text(e, "LastName", lastName);
            Xml.text(e, "FirstName", firstName);
            Xml.text(e, "SecondName", secondName);
            Xml.text(e, "Age", age);
            Xml.text(e, "Gender", gender);
            Xml.text(e, "BirthDate", birthDate);
            Xml.text(e, "SocialStatus", socialStatus);
            Xml.text(e, "PatientInfo", patientInfo);
            Xml.text(e, "ReceiverMoId", receiverMoId);
            Xml.text(e, "ReceiverTerMoId", receiverTerMoId);
            Xml.text(e, "PersonRpnId", personRpnId);
            Xml.text(e, "TerritoryServiceId", territoryServiceId);
            Xml.text(e, "TerritoryServiceNumber", territoryServiceNumber);
            Xml.text(e, "CallNumber", callNumber);
            Xml.text(e, "GlobalCallNumber", globalCallNumber);
            Xml.text(e, "Priority", priority);
            Xml.text(e, "Reason", reason);
            Xml.text(e, "DescReason", descReason);
            Xml.text(e, "EmergencyCallAddress", emergencyCallAddress);
            Xml.text(e, "Caller", caller);
            Xml.text(e, "Phone", phone);
            Xml.text(e, "Place", place);
            Xml.text(e, "DescPlace", descPlace);
            Xml.text(e, "Profile", profile);
            Xml.text(e, "DescProfile", descProfile);
            Xml.text(e, "Info", info);
            Xml.text(e, "Result", result);
            Xml.text(e, "DescResult", descResult);
            Xml.text(e, "CallTime", callTime);
            Xml.text(e, "TransferTime", transferTime);
            Xml.text(e, "DepartureTime", departureTime);
            Xml.text(e, "ArrivalTime", arrivalTime);
            Xml.text(e, "HospitalTime", hospitalTime);
            Xml.text(e, "ArrivalHospitalTime", arrivalHospitalTime);
            Xml.text(e, "HospiMoId", hospiMoId);
            Xml.text(e, "Diagnosis", diagnosis);
            Xml.text(e, "GemoDynamicsADtop", gemoDynamicsADtop);
            Xml.text(e, "GemoDynamicsADbottom", gemoDynamicsADbottom);
            Xml.text(e, "GemoDynamicsChD", gemoDynamicsChD);
            Xml.text(e, "GemoDynamicsChSS", gemoDynamicsChSS);
            Xml.text(e, "GemoDynamicsTmp", gemoDynamicsTmp);
            Xml.list(e, "MedicalSupplies", "MedicalSuppliesRequest", medicalSupplies);
            Xml.text(e, "GemoDynamicsTherapyADtop", gemoDynamicsTherapyADtop);
            Xml.text(e, "GemoDynamicsTherapyADbottom", gemoDynamicsTherapyADbottom);
            Xml.text(e, "GemoDynamicsTherapyChD", gemoDynamicsTherapyChD);
            Xml.text(e, "GemoDynamicsTherapyChSS", gemoDynamicsTherapyChSS);
            Xml.text(e, "GemoDynamicsTherapyTmp", gemoDynamicsTherapyTmp);
            Xml.text(e, "BrigadeNumber", brigadeNumber);
            Xml.text(e, "BrigadeSMP", brigadeSMP);
            Xml.text(e, "BrigadeCarNumber", brigadeCarNumber);
            Xml.text(e, "BrigadePersonnelCode", brigadePersonnelCode);
            Xml.text(e, "BrigadePersonnelName", brigadePersonnelName);
            Xml.text(e, "Workplace", workplace);
            Xml.text(e, "IsRefusalAssistance", isRefusalAssistance);
            Xml.text(e, "RefusingText", refusingText);
            Xml.strings(e, "AssociatedDiseases", associatedDiseases);
            Xml.text(e, "InjuryType", injuryType);
            Xml.text(e, "IsAlcohol", isAlcohol);
            Xml.text(e, "Mileage", mileage);
            Xml.text(e, "ServiceDate", serviceDate);
            Xml.text(e, "Claim", claim);
            Xml.text(e, "HistoryIllness", historyIllness);
            Xml.text(e, "HistoryCycle", historyCycle);
            Xml.text(e, "GeneralState", generalState);
            Xml.text(e, "Sense", sense);
            Xml.text(e, "Pupils", pupils);
            Xml.text(e, "LightSensitive", lightSensitive);
            Xml.strings(e, "SkinIrritations", skinIrritations);
            Xml.strings(e, "CardioTones", cardioTones);
            Xml.strings(e, "CardioPulse", cardioPulse);
            Xml.text(e, "CardioSound", cardioSound);
            Xml.text(e, "RespiratorySystem", respiratorySystem);
            Xml.strings(e, "RespiratoryBreath", respiratoryBreath);
            Xml.strings(e, "Behaviours", behaviours);
            Xml.text(e, "RespiratoryRales", respiratoryRales);
            Xml.text(e, "ShortnessBreath", shortnessBreath);
            Xml.text(e, "NeurologicalEvaluation", neurologicalEvaluation);
            Xml.strings(e, "Eyeballs", eyeballs);
            Xml.strings(e, "Nerves", nerves);
            Xml.strings(e, "TendonReflexes", tendonReflexes);
            Xml.strings(e, "MotorAreas", motorAreas);
            Xml.strings(e, "PainSensitivity", painSensitivity);
            Xml.text(e, "Aphasia", aphasia);
            Xml.strings(e, "Syndrome", syndrome);
            Xml.strings(e, "Throat", throat);
            Xml.text(e, "Tonsil", tonsil);
            Xml.text(e, "IsCoatedWithBloom", isCoatedWithBloom);
            Xml.strings(e, "Stomach", stomach);
            Xml.strings(e, "Symptoms", symptoms);
            Xml.strings(e, "Liver", liver);
            Xml.strings(e, "UrinarySystem", urinarySystem);
            Xml.text(e, "MenstrualCycle", menstrualCycle);
            Xml.text(e, "PeripheralEdema", peripheralEdema);
            Xml.text(e, "BloodSugar", bloodSugar);
            Xml.text(e, "TreatmentResult", treatmentResult);
            Xml.text(e, "DiagnosticMethods", diagnosticMethods);
            Xml.text(e, "Treatment", treatment);
            Xml.text(e, "Consumption", consumption);
            Xml.text(e, "ReceiveTypeData", receiveTypeData);
            Xml.text(e, "ReceiveDate", receiveDate);
            Xml.text(e, "CallType", callType);
            Xml.text(e, "DescCallType", descCallType);
            Xml.text(e, "IsRepeat", isRepeat);
            Xml.text(e, "Triage", triage);
            Xml.text(e, "IsReanimation", isReanimation);
            Xml.text(e, "ArrivalHospitalPlanTime", arrivalHospitalPlanTime);
        }
    }

    /** Объём оказанной помощи / диагностическое исследование бригады (элемент MedicalSupplies). */
    public static class MedicalSuppliesRequest implements XmlWritable {
        /** Код. */
        public String code;
        /** Название. */
        public String name;
        /** Единица измерения. */
        public String measure;
        /** Количество. */
        public Integer quantity;
        /** Дополнительная информация. */
        public String info;

        @Override
        public void writeTo(Element e) {
            Xml.text(e, "Code", code);
            Xml.text(e, "Name", name);
            Xml.text(e, "Measure", measure);
            Xml.text(e, "Quantity", quantity);
            Xml.text(e, "Info", info);
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
        AmbulanceCardRequest c = new AmbulanceCardRequest();
        c.receiveId = 100001L;
        c.externalId = "TEST-AMB-20260903-0001";
        c.senderMoId = 10001L;
        c.iin = "990101300123";
        c.lastName = "Иванов";
        c.firstName = "Иван";
        c.secondName = "Иванович";
        c.age = 27;
        c.gender = 3;
        c.birthDate = "1999-01-01";
        c.socialStatus = "Работающий";
        c.patientInfo = "Тестовый пациент";
        c.receiverMoId = 20001L;
        c.receiverTerMoId = 20002L;
        c.personRpnId = 300001L;
        c.territoryServiceId = 400001L;
        c.territoryServiceNumber = 15;
        c.callNumber = "CALL-000001";
        c.globalCallNumber = "GLOBAL-CALL-20260903-000001";
        c.priority = "1";
        c.reason = "Повышенная температура";
        c.descReason = "Температура 39 градусов, слабость";
        c.emergencyCallAddress = "г. Астана, район Есиль, ул. Тестовая, д. 10, кв. 25";
        c.caller = "Пациент";
        c.phone = "77001234567";
        c.place = "1";
        c.descPlace = "Квартира";
        c.profile = "THERAPY";
        c.descProfile = "Терапевтический профиль";
        c.info = "Тестовый вызов скорой медицинской помощи";
        c.result = "1";
        c.descResult = "Госпитализирован";
        c.callTime = "2026-09-03T09:30:00+05:00";
        c.transferTime = "2026-09-03T09:32:00+05:00";
        c.departureTime = "2026-09-03T09:35:00+05:00";
        c.arrivalTime = "2026-09-03T09:50:00+05:00";
        c.hospitalTime = "2026-09-03T10:20:00+05:00";
        c.arrivalHospitalTime = "2026-09-03T10:20:00+05:00";
        c.hospiMoId = 50001L;
        c.diagnosis = "J06.9";
        c.gemoDynamicsADtop = "120";
        c.gemoDynamicsADbottom = "80";
        c.gemoDynamicsChD = "18";
        c.gemoDynamicsChSS = "85";
        c.gemoDynamicsTmp = "39.0";
        {
            MedicalSuppliesRequest m = new MedicalSuppliesRequest();
            m.code = "MED-001";
            m.name = "Парацетамол";
            m.measure = "таблетка";
            m.quantity = 1;
            m.info = "500 мг";
            c.medicalSupplies.add(m);
        }
        {
            MedicalSuppliesRequest m = new MedicalSuppliesRequest();
            m.code = "DIAG-001";
            m.name = "Измерение артериального давления";
            m.measure = "процедура";
            m.quantity = 1;
            m.info = "АД 120/80 мм рт. ст.";
            c.medicalSupplies.add(m);
        }
        c.gemoDynamicsTherapyADtop = "118";
        c.gemoDynamicsTherapyADbottom = "78";
        c.gemoDynamicsTherapyChD = "17";
        c.gemoDynamicsTherapyChSS = "80";
        c.gemoDynamicsTherapyTmp = "38.2";
        c.brigadeNumber = "BR-101";
        c.brigadeSMP = "Станция скорой медицинской помощи №1";
        c.brigadeCarNumber = "123ABC01";
        c.brigadePersonnelCode = "DOC-1001";
        c.brigadePersonnelName = "Петров Петр Петрович";
        c.workplace = "ТОО Тестовая организация";
        c.isRefusalAssistance = false;
        c.refusingText = "";
        c.associatedDiseases = Arrays.asList("I10", "J30.9");
        c.injuryType = 0;
        c.isAlcohol = false;
        c.mileage = 15;
        c.serviceDate = "2026-09-03T09:50:00+05:00";
        c.claim = "Высокая температура, слабость, головная боль";
        c.historyIllness = "Заболел около двух дней назад. Температура повысилась до 39 градусов.";
        c.historyCycle = "Хронические заболевания отрицает.";
        c.generalState = "Средней степени тяжести";
        c.sense = "Сознание ясное";
        c.pupils = "Равные";
        c.lightSensitive = "Реакция сохранена";
        c.skinIrritations = Arrays.asList("Кожные покровы чистые", "Обычной окраски");
        c.cardioTones = Arrays.asList("Ясные", "Ритмичные");
        c.cardioPulse = Arrays.asList("Ритмичный", "Удовлетворительного наполнения");
        c.cardioSound = "Патологических шумов нет";
        c.respiratorySystem = "Грудная клетка симметричная";
        c.respiratoryBreath = Arrays.asList("Везикулярное", "Проводится во все отделы");
        c.behaviours = Arrays.asList("Спокойное", "Адекватное");
        c.respiratoryRales = "Хрипов нет";
        c.shortnessBreath = "Нет";
        c.neurologicalEvaluation = "Очаговой неврологической симптоматики нет";
        c.eyeballs = Arrays.asList("Движения сохранены", "Симметричные");
        c.nerves = Arrays.asList("Черепные нервы без патологии");
        c.tendonReflexes = Arrays.asList("Живые", "Симметричные");
        c.motorAreas = Arrays.asList("Движения сохранены", "Парезов нет");
        c.painSensitivity = Arrays.asList("Сохранена");
        c.aphasia = "Нет";
        c.syndrome = Arrays.asList("Интоксикационный синдром");
        c.throat = Arrays.asList("Гиперемирован");
        c.tonsil = "Умеренно увеличены";
        c.isCoatedWithBloom = false;
        c.stomach = Arrays.asList("Мягкий", "Безболезненный");
        c.symptoms = Arrays.asList("Головная боль", "Слабость", "Лихорадка");
        c.liver = Arrays.asList("Не увеличена", "Безболезненная");
        c.urinarySystem = Arrays.asList("Мочеиспускание свободное", "Безболезненное");
        c.menstrualCycle = 1;
        c.peripheralEdema = "Нет";
        c.bloodSugar = "5.2";
        c.treatmentResult = "Состояние улучшилось, температура снизилась";
        c.diagnosticMethods = "Термометрия, пульсоксиметрия, измерение АД";
        c.treatment = "Парацетамол 500 мг, симптоматическая терапия";
        c.consumption = "Парацетамол — 1 таблетка";
        c.receiveTypeData = "Hospitalization";
        c.receiveDate = "2026-09-03T10:30:00+05:00";
        c.callType = "1";
        c.descCallType = "Первичный вызов";
        c.isRepeat = false;
        c.triage = 2;
        c.isReanimation = false;
        c.arrivalHospitalPlanTime = "2026-09-03T10:30:00+05:00";
        return new IntegraRequest(new LoadAmbulanceCardsRequest().add(c));
    }

    /** Пример ответа сервиса (элемент {@code <data>} из responseData). */
    public static final String RESPONSE_XML =
            "<data xmlns:q1=\"http://integrations.gosreestr.kz\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"q1:IntegraResponse\">\n"
            + "  <ResponseInfo>\n"
            + "    <RequestType>AmbulanceCard_LoadOrUpdateCards</RequestType>\n"
            + "    <StatusCode>200</StatusCode>\n"
            + "    <Message>OK</Message>\n"
            + "  </ResponseInfo>\n"
            + "  <AmbulanceCard_LoadOrUpdateCardsResponse>\n"
            + "    <Total>1</Total>\n"
            + "    <Inserted>0</Inserted>\n"
            + "    <Updated>1</Updated>\n"
            + "    <Unchanged>0</Unchanged>\n"
            + "    <Errors></Errors>\n"
            + "  </AmbulanceCard_LoadOrUpdateCardsResponse>\n"
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
