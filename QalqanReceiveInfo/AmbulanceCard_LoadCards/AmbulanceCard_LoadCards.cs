// QalqanReceiveInfo / AmbulanceCard_LoadCards — карты вызова скорой помощи (актив / госпитализация).
// Файл самодостаточен: DTO запроса, DTO ответа и заполненный пример.
// Формируется только бизнес-нагрузка <data> (содержимое requestData / responseData конверта ШЭП);
// сам конверт SendMessage, requestInfo и транспортная подпись — стандартная часть ШЭП и здесь не рассматриваются.
// Паспорт сервиса: https://sb.egov.kz/services/passport/ORGAM-S-1170
using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Text;
using System.Xml;
using System.Xml.Serialization;

namespace Qalqan.ReceiveInfo.Examples.AmbulanceCard_LoadCards
{
    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"> с RequestType = AmbulanceCard_LoadCards
    // =====================================================================================

    [XmlRoot("data", Namespace = "")]
    public class IntegraRequest
    {
        [XmlAttribute("type", Namespace = "http://www.w3.org/2001/XMLSchema-instance")]
        public string XsiType { get; set; } = "q1:IntegraRequest";

        [XmlAttribute("q1", Namespace = "http://www.w3.org/2000/xmlns/")]
        public string Q1Namespace { get; set; } = "http://integrations.gosreestr.kz";

        /// <summary>Тип операции. Имя блока данных ниже строго привязано к нему.</summary>
        public string RequestType { get; set; } = "AmbulanceCard_LoadCards";

        public LoadAmbulanceCardsRequest? AmbulanceCard_LoadCardsRequest { get; set; }

        private static readonly XmlSerializer Serializer = new XmlSerializer(typeof(IntegraRequest));

        /// <summary>XML элемента &lt;data&gt; — помещается в requestData конверта ШЭП.</summary>
        public string ToXml()
        {
            var sb = new StringBuilder();
            using (var w = XmlWriter.Create(sb, new XmlWriterSettings { OmitXmlDeclaration = true, Indent = true }))
                Serializer.Serialize(w, this);
            return sb.ToString();
        }

        public static IntegraRequest Parse(string dataXml)
        {
            using (var r = new StringReader(dataXml)) return (IntegraRequest)Serializer.Deserialize(r)!;
        }
    }

    /// <summary>Пакет карт вызова скорой помощи (AmbulanceCard_LoadCards / AmbulanceCard_LoadOrUpdateCards). Каждая карта — полный снимок: при обновлении непереданные поля обнуляются, MedicalSupplies заменяются целиком.</summary>
    public class LoadAmbulanceCardsRequest
    {
        [XmlArray("List")]
        [XmlArrayItem("AmbulanceCardRequest")]
        public List<AmbulanceCardRequest> List { get; set; } = new List<AmbulanceCardRequest>();
    }

    /// <summary>Карта вызова скорой помощи: актив (ActiveRegistration) или госпитализация (Hospitalization). Даты и время передаются строками. Обязательны ExternalId, ReceiveTypeData, ReceiveDate.</summary>
    public class AmbulanceCardRequest
    {
        /// <summary>Идентификатор.</summary>
        public long? ReceiveId { get; set; }
        public bool ShouldSerializeReceiveId() => ReceiveId.HasValue;

        /// <summary>Идентификатор внешней системы (обязателен).</summary>
        public string? ExternalId { get; set; }

        /// <summary>Идентификатор направившей организации (СУР).</summary>
        public long? SenderMoId { get; set; }
        public bool ShouldSerializeSenderMoId() => SenderMoId.HasValue;

        /// <summary>ИИН.</summary>
        public string? Iin { get; set; }

        /// <summary>Фамилия.</summary>
        public string? LastName { get; set; }

        /// <summary>Имя.</summary>
        public string? FirstName { get; set; }

        /// <summary>Отчество.</summary>
        public string? SecondName { get; set; }

        /// <summary>Возраст.</summary>
        public int? Age { get; set; }
        public bool ShouldSerializeAge() => Age.HasValue;

        /// <summary>Пол: 2 — женский, 3 — мужской.</summary>
        public int? Gender { get; set; }
        public bool ShouldSerializeGender() => Gender.HasValue;

        /// <summary>Дата рождения (строкой, yyyy-MM-dd).</summary>
        public string? BirthDate { get; set; }

        /// <summary>Социальный статус (соц. положение, место работы).</summary>
        public string? SocialStatus { get; set; }

        /// <summary>Дополнительная информация по пациенту.</summary>
        public string? PatientInfo { get; set; }

        /// <summary>Идентификатор организации прикрепления (СУР).</summary>
        public long? ReceiverMoId { get; set; }
        public bool ShouldSerializeReceiverMoId() => ReceiverMoId.HasValue;

        /// <summary>Идентификатор поликлиники (СУР) по месту вызова.</summary>
        public long? ReceiverTerMoId { get; set; }
        public bool ShouldSerializeReceiverTerMoId() => ReceiverTerMoId.HasValue;

        /// <summary>Идентификатор пациента (РПН).</summary>
        public long? PersonRpnId { get; set; }
        public bool ShouldSerializePersonRpnId() => PersonRpnId.HasValue;

        /// <summary>ID участка (РПН).</summary>
        public long? TerritoryServiceId { get; set; }
        public bool ShouldSerializeTerritoryServiceId() => TerritoryServiceId.HasValue;

        /// <summary>Номер участка (РПН).</summary>
        public int? TerritoryServiceNumber { get; set; }
        public bool ShouldSerializeTerritoryServiceNumber() => TerritoryServiceNumber.HasValue;

        /// <summary>Номер вызова.</summary>
        public string? CallNumber { get; set; }

        /// <summary>Глобальный номер вызова.</summary>
        public string? GlobalCallNumber { get; set; }

        /// <summary>Приоритет / уровень срочности.</summary>
        public string? Priority { get; set; }

        /// <summary>Повод вызова.</summary>
        public string? Reason { get; set; }

        /// <summary>Описание повода вызова.</summary>
        public string? DescReason { get; set; }

        /// <summary>Адрес вызова.</summary>
        public string? EmergencyCallAddress { get; set; }

        /// <summary>Кто вызвал.</summary>
        public string? Caller { get; set; }

        /// <summary>Контактный телефон.</summary>
        public string? Phone { get; set; }

        /// <summary>Код места вызова.</summary>
        public string? Place { get; set; }

        /// <summary>Описание места вызова.</summary>
        public string? DescPlace { get; set; }

        /// <summary>Профиль вызова.</summary>
        public string? Profile { get; set; }

        /// <summary>Описание профиля вызова.</summary>
        public string? DescProfile { get; set; }

        /// <summary>Информация о вызове.</summary>
        public string? Info { get; set; }

        /// <summary>Код результата вызова.</summary>
        public string? Result { get; set; }

        /// <summary>Описание результата вызова.</summary>
        public string? DescResult { get; set; }

        /// <summary>Время приёма вызова (ISO 8601).</summary>
        public string? CallTime { get; set; }

        /// <summary>Время передачи вызова бригаде СП.</summary>
        public string? TransferTime { get; set; }

        /// <summary>Время выезда бригады СП.</summary>
        public string? DepartureTime { get; set; }

        /// <summary>Время начала госпитализации.</summary>
        public string? ArrivalTime { get; set; }

        /// <summary>Время прибытия в стационар.</summary>
        public string? HospitalTime { get; set; }

        /// <summary>Время прибытия в стационар (факт).</summary>
        public string? ArrivalHospitalTime { get; set; }

        /// <summary>SUR-код места госпитализации.</summary>
        public long? HospiMoId { get; set; }
        public bool ShouldSerializeHospiMoId() => HospiMoId.HasValue;

        /// <summary>Код диагноза по МКБ-10.</summary>
        public string? Diagnosis { get; set; }

        /// <summary>Артериальное давление верхнее.</summary>
        public string? GemoDynamicsADtop { get; set; }

        /// <summary>Артериальное давление нижнее.</summary>
        public string? GemoDynamicsADbottom { get; set; }

        /// <summary>Частота дыхания.</summary>
        public string? GemoDynamicsChD { get; set; }

        /// <summary>Частота сердечных сокращений.</summary>
        public string? GemoDynamicsChSS { get; set; }

        /// <summary>Температура.</summary>
        public string? GemoDynamicsTmp { get; set; }

        /// <summary>Объём оказанной помощи и диагностические исследования бригады.</summary>
        [XmlArray("MedicalSupplies")]
        [XmlArrayItem("MedicalSuppliesRequest")]
        public List<MedicalSuppliesRequest>? MedicalSupplies { get; set; }

        /// <summary>АД верхнее после терапии.</summary>
        public string? GemoDynamicsTherapyADtop { get; set; }

        /// <summary>АД нижнее после терапии.</summary>
        public string? GemoDynamicsTherapyADbottom { get; set; }

        /// <summary>Частота дыхания после терапии.</summary>
        public string? GemoDynamicsTherapyChD { get; set; }

        /// <summary>ЧСС после терапии.</summary>
        public string? GemoDynamicsTherapyChSS { get; set; }

        /// <summary>Температура после терапии.</summary>
        public string? GemoDynamicsTherapyTmp { get; set; }

        /// <summary>Номер бригады СП.</summary>
        public string? BrigadeNumber { get; set; }

        /// <summary>Станция бригады.</summary>
        public string? BrigadeSMP { get; set; }

        /// <summary>Гос. номер машины СП.</summary>
        public string? BrigadeCarNumber { get; set; }

        /// <summary>Старший бригады — код (АДИС).</summary>
        public string? BrigadePersonnelCode { get; set; }

        /// <summary>Старший бригады — ФИО.</summary>
        public string? BrigadePersonnelName { get; set; }

        /// <summary>Место работы.</summary>
        public string? Workplace { get; set; }

        /// <summary>Отказ от осмотра / помощи / госпитализации.</summary>
        public bool? IsRefusalAssistance { get; set; }
        public bool ShouldSerializeIsRefusalAssistance() => IsRefusalAssistance.HasValue;

        /// <summary>Текст отказа.</summary>
        public string? RefusingText { get; set; }

        /// <summary>Сопутствующие диагнозы (МКБ-10).</summary>
        [XmlArray("AssociatedDiseases")]
        [XmlArrayItem("string")]
        public List<string>? AssociatedDiseases { get; set; }

        /// <summary>Вид травматизма.</summary>
        public int? InjuryType { get; set; }
        public bool ShouldSerializeInjuryType() => InjuryType.HasValue;

        /// <summary>Алкоголь.</summary>
        public bool? IsAlcohol { get; set; }
        public bool ShouldSerializeIsAlcohol() => IsAlcohol.HasValue;

        /// <summary>Километраж, км.</summary>
        public int? Mileage { get; set; }
        public bool ShouldSerializeMileage() => Mileage.HasValue;

        /// <summary>Дата и время прибытия бригады на вызов.</summary>
        public string? ServiceDate { get; set; }

        /// <summary>Жалобы.</summary>
        public string? Claim { get; set; }

        /// <summary>Анамнез настоящего заболевания.</summary>
        public string? HistoryIllness { get; set; }

        /// <summary>Анамнез жизни.</summary>
        public string? HistoryCycle { get; set; }

        /// <summary>Общее состояние.</summary>
        public string? GeneralState { get; set; }

        /// <summary>Сознание.</summary>
        public string? Sense { get; set; }

        /// <summary>Зрачки.</summary>
        public string? Pupils { get; set; }

        /// <summary>Реакция на свет.</summary>
        public string? LightSensitive { get; set; }

        /// <summary>Кожные покровы.</summary>
        [XmlArray("SkinIrritations")]
        [XmlArrayItem("string")]
        public List<string>? SkinIrritations { get; set; }

        /// <summary>Тоны сердца.</summary>
        [XmlArray("CardioTones")]
        [XmlArrayItem("string")]
        public List<string>? CardioTones { get; set; }

        /// <summary>Пульс.</summary>
        [XmlArray("CardioPulse")]
        [XmlArrayItem("string")]
        public List<string>? CardioPulse { get; set; }

        /// <summary>Шумы сердца.</summary>
        public string? CardioSound { get; set; }

        /// <summary>Экскурсия грудной клетки.</summary>
        public string? RespiratorySystem { get; set; }

        /// <summary>Дыхание.</summary>
        [XmlArray("RespiratoryBreath")]
        [XmlArrayItem("string")]
        public List<string>? RespiratoryBreath { get; set; }

        /// <summary>Поведение.</summary>
        [XmlArray("Behaviours")]
        [XmlArrayItem("string")]
        public List<string>? Behaviours { get; set; }

        /// <summary>Хрипы.</summary>
        public string? RespiratoryRales { get; set; }

        /// <summary>Одышка.</summary>
        public string? ShortnessBreath { get; set; }

        /// <summary>Неврологический статус.</summary>
        public string? NeurologicalEvaluation { get; set; }

        /// <summary>Глазные яблоки.</summary>
        [XmlArray("Eyeballs")]
        [XmlArrayItem("string")]
        public List<string>? Eyeballs { get; set; }

        /// <summary>Нервы.</summary>
        [XmlArray("Nerves")]
        [XmlArrayItem("string")]
        public List<string>? Nerves { get; set; }

        /// <summary>Сухожильные рефлексы.</summary>
        [XmlArray("TendonReflexes")]
        [XmlArrayItem("string")]
        public List<string>? TendonReflexes { get; set; }

        /// <summary>Двигательная сфера.</summary>
        [XmlArray("MotorAreas")]
        [XmlArrayItem("string")]
        public List<string>? MotorAreas { get; set; }

        /// <summary>Болевая чувствительность.</summary>
        [XmlArray("PainSensitivity")]
        [XmlArrayItem("string")]
        public List<string>? PainSensitivity { get; set; }

        /// <summary>Афазия.</summary>
        public string? Aphasia { get; set; }

        /// <summary>Синдромы.</summary>
        [XmlArray("Syndrome")]
        [XmlArrayItem("string")]
        public List<string>? Syndrome { get; set; }

        /// <summary>Зев.</summary>
        [XmlArray("Throat")]
        [XmlArrayItem("string")]
        public List<string>? Throat { get; set; }

        /// <summary>Миндалины.</summary>
        public string? Tonsil { get; set; }

        /// <summary>Язык обложен налётом.</summary>
        public bool? IsCoatedWithBloom { get; set; }
        public bool ShouldSerializeIsCoatedWithBloom() => IsCoatedWithBloom.HasValue;

        /// <summary>Живот.</summary>
        [XmlArray("Stomach")]
        [XmlArrayItem("string")]
        public List<string>? Stomach { get; set; }

        /// <summary>Симптомы.</summary>
        [XmlArray("Symptoms")]
        [XmlArrayItem("string")]
        public List<string>? Symptoms { get; set; }

        /// <summary>Печень.</summary>
        [XmlArray("Liver")]
        [XmlArrayItem("string")]
        public List<string>? Liver { get; set; }

        /// <summary>Мочеполовая система.</summary>
        [XmlArray("UrinarySystem")]
        [XmlArrayItem("string")]
        public List<string>? UrinarySystem { get; set; }

        /// <summary>Менструальный цикл: 1 — без нарушений, 2 — нарушения.</summary>
        public int? MenstrualCycle { get; set; }
        public bool ShouldSerializeMenstrualCycle() => MenstrualCycle.HasValue;

        /// <summary>Периферические отёки.</summary>
        public string? PeripheralEdema { get; set; }

        /// <summary>Сахар в крови, ммоль/л.</summary>
        public string? BloodSugar { get; set; }

        /// <summary>Результаты лечения.</summary>
        public string? TreatmentResult { get; set; }

        /// <summary>Инструментальные методы диагностики.</summary>
        public string? DiagnosticMethods { get; set; }

        /// <summary>Лечебные мероприятия.</summary>
        public string? Treatment { get; set; }

        /// <summary>Расход.</summary>
        public string? Consumption { get; set; }

        /// <summary>Тип данных: ActiveRegistration — актив, Hospitalization — госпитализация (обязателен).</summary>
        public string? ReceiveTypeData { get; set; }

        /// <summary>Дата и время передачи данных (обязательна; при LoadOrUpdate более старая версия игнорируется — StaleVersion).</summary>
        public string? ReceiveDate { get; set; }

        /// <summary>Тип вызова.</summary>
        public string? CallType { get; set; }

        /// <summary>Описание типа вызова.</summary>
        public string? DescCallType { get; set; }

        /// <summary>Признак повторного вызова СП.</summary>
        public bool? IsRepeat { get; set; }
        public bool ShouldSerializeIsRepeat() => IsRepeat.HasValue;

        /// <summary>Триаж: 1 — зелёный, 2 — жёлтый, 3 — красный.</summary>
        public int? Triage { get; set; }
        public bool ShouldSerializeTriage() => Triage.HasValue;

        /// <summary>Признак реанимационности.</summary>
        public bool? IsReanimation { get; set; }
        public bool ShouldSerializeIsReanimation() => IsReanimation.HasValue;

        /// <summary>Планируемое время прибытия в стационар.</summary>
        public string? ArrivalHospitalPlanTime { get; set; }
    }

    /// <summary>Объём оказанной помощи / диагностическое исследование бригады (элемент MedicalSupplies).</summary>
    public class MedicalSuppliesRequest
    {
        /// <summary>Код.</summary>
        public string? Code { get; set; }

        /// <summary>Название.</summary>
        public string? Name { get; set; }

        /// <summary>Единица измерения.</summary>
        public string? Measure { get; set; }

        /// <summary>Количество.</summary>
        public int? Quantity { get; set; }
        public bool ShouldSerializeQuantity() => Quantity.HasValue;

        /// <summary>Дополнительная информация.</summary>
        public string? Info { get; set; }
    }

    /// <summary>Результат загрузки пакета карт вызова СП.</summary>
    public class LoadAmbulanceCardsResponse : LoadResultBase
    {
        [XmlArray("Errors")]
        [XmlArrayItem("AmbulanceCardErrorDto")]
        public List<AmbulanceCardError> Errors { get; set; } = new List<AmbulanceCardError>();
    }

    /// <summary>Общий результат загрузки пакета (случаи / услуги).</summary>
    public abstract class LoadResultBase
    {
        public int Total { get; set; }
        public int Inserted { get; set; }
        public int Updated { get; set; }
        public int Unchanged { get; set; }
    }

    /// <summary>Ошибка или предупреждение по одной карте пакета.</summary>
    public class AmbulanceCardError
    {
        /// <summary>Индекс элемента в List (с 0); -1 если не определён.</summary>
        public int RecIndex { get; set; } = -1;
        public string? ExternalId { get; set; }
        public string? ReceiveTypeData { get; set; }
        public string? ReceiveDate { get; set; }
        /// <summary>InvalidRequest — не прошла валидацию (не сохранена); DuplicateCard — уже существует (LoadCards, не сохранена); StaleVersion — в базе более поздний ReceiveDate (LoadOrUpdateCards, проигнорирована); PeriodClosed — карта старше срока блокировки по CallTime, обновление запрещено.</summary>
        public string Code { get; set; } = string.Empty;
        public string Message { get; set; } = string.Empty;
    }

    // =====================================================================================
    // ОТВЕТ: <data xsi:type="q1:IntegraResponse">
    // =====================================================================================

    [XmlRoot("data", Namespace = "")]
    public class IntegraResponse
    {
        public ResponseInfo ResponseInfo { get; set; } = new ResponseInfo();

    public LoadAmbulanceCardsResponse? AmbulanceCard_LoadCardsResponse { get; set; }

    /// <summary>Блок результата (есть только при StatusCode = 200).</summary>
    public LoadAmbulanceCardsResponse? Result => AmbulanceCard_LoadCardsResponse;

        public bool IsSuccess => ResponseInfo.IsSuccess;

        private static readonly XmlSerializer Serializer = new XmlSerializer(typeof(IntegraResponse));

        /// <summary>Разбор элемента &lt;data&gt; из responseData (атрибуты игнорируются).</summary>
        public static IntegraResponse Parse(string dataXml)
        {
            var doc = new XmlDocument();
            doc.LoadXml(dataXml);
            var data = (XmlElement)doc.DocumentElement!.CloneNode(true);
            data.Attributes.RemoveAll();
            using (var r = new StringReader(data.OuterXml)) return (IntegraResponse)Serializer.Deserialize(r)!;
        }
    }

    /// <summary>Служебная часть ответа: тип запроса, код (200/400/500/501) и сообщение.</summary>
    public class ResponseInfo
    {
        [XmlElement("RequestType", IsNullable = true)]
        public string? RequestType { get; set; }
        /// <summary>200 — успех; 400 — ошибка запроса; 500 — внутренняя ошибка; 501 — не реализовано / отправитель не сопоставлен с МИС.</summary>
        public int StatusCode { get; set; }
        public string? Message { get; set; }
        public bool IsSuccess => StatusCode == 200;
    }

    // =====================================================================================
    // ПРИМЕР
    // =====================================================================================

    public static class Example
    {
        /// <summary>Заполненный запрос.</summary>
        public static IntegraRequest Request() => new IntegraRequest
        {
            AmbulanceCard_LoadCardsRequest = new LoadAmbulanceCardsRequest
            {
                List =
                {
                    new AmbulanceCardRequest
                    {
                        ReceiveId = 100001,
                        ExternalId = "TEST-AMB-20260903-0001",
                        SenderMoId = 10001,
                        Iin = "990101300123",
                        LastName = "Иванов",
                        FirstName = "Иван",
                        SecondName = "Иванович",
                        Age = 27,
                        Gender = 3,
                        BirthDate = "1999-01-01",
                        SocialStatus = "Работающий",
                        PatientInfo = "Тестовый пациент",
                        ReceiverMoId = 20001,
                        ReceiverTerMoId = 20002,
                        PersonRpnId = 300001,
                        TerritoryServiceId = 400001,
                        TerritoryServiceNumber = 15,
                        CallNumber = "CALL-000001",
                        GlobalCallNumber = "GLOBAL-CALL-20260903-000001",
                        Priority = "1",
                        Reason = "Повышенная температура",
                        DescReason = "Температура 39 градусов, слабость",
                        EmergencyCallAddress = "г. Астана, район Есиль, ул. Тестовая, д. 10, кв. 25",
                        Caller = "Пациент",
                        Phone = "77001234567",
                        Place = "1",
                        DescPlace = "Квартира",
                        Profile = "THERAPY",
                        DescProfile = "Терапевтический профиль",
                        Info = "Тестовый вызов скорой медицинской помощи",
                        Result = "1",
                        DescResult = "Госпитализирован",
                        CallTime = "2026-09-03T09:30:00+05:00",
                        TransferTime = "2026-09-03T09:32:00+05:00",
                        DepartureTime = "2026-09-03T09:35:00+05:00",
                        ArrivalTime = "2026-09-03T09:50:00+05:00",
                        HospitalTime = "2026-09-03T10:20:00+05:00",
                        ArrivalHospitalTime = "2026-09-03T10:20:00+05:00",
                        HospiMoId = 50001,
                        Diagnosis = "J06.9",
                        GemoDynamicsADtop = "120",
                        GemoDynamicsADbottom = "80",
                        GemoDynamicsChD = "18",
                        GemoDynamicsChSS = "85",
                        GemoDynamicsTmp = "39.0",
                        MedicalSupplies = new List<MedicalSuppliesRequest>
                        {
                            new MedicalSuppliesRequest { Code = "MED-001", Name = "Парацетамол", Measure = "таблетка", Quantity = 1, Info = "500 мг" },
                            new MedicalSuppliesRequest { Code = "DIAG-001", Name = "Измерение артериального давления", Measure = "процедура", Quantity = 1, Info = "АД 120/80 мм рт. ст." },
                        },
                        GemoDynamicsTherapyADtop = "118",
                        GemoDynamicsTherapyADbottom = "78",
                        GemoDynamicsTherapyChD = "17",
                        GemoDynamicsTherapyChSS = "80",
                        GemoDynamicsTherapyTmp = "38.2",
                        BrigadeNumber = "BR-101",
                        BrigadeSMP = "Станция скорой медицинской помощи №1",
                        BrigadeCarNumber = "123ABC01",
                        BrigadePersonnelCode = "DOC-1001",
                        BrigadePersonnelName = "Петров Петр Петрович",
                        Workplace = "ТОО Тестовая организация",
                        IsRefusalAssistance = false,
                        RefusingText = "",
                        AssociatedDiseases = new List<string> { "I10", "J30.9" },
                        InjuryType = 0,
                        IsAlcohol = false,
                        Mileage = 15,
                        ServiceDate = "2026-09-03T09:50:00+05:00",
                        Claim = "Высокая температура, слабость, головная боль",
                        HistoryIllness = "Заболел около двух дней назад. Температура повысилась до 39 градусов.",
                        HistoryCycle = "Хронические заболевания отрицает.",
                        GeneralState = "Средней степени тяжести",
                        Sense = "Сознание ясное",
                        Pupils = "Равные",
                        LightSensitive = "Реакция сохранена",
                        SkinIrritations = new List<string> { "Кожные покровы чистые", "Обычной окраски" },
                        CardioTones = new List<string> { "Ясные", "Ритмичные" },
                        CardioPulse = new List<string> { "Ритмичный", "Удовлетворительного наполнения" },
                        CardioSound = "Патологических шумов нет",
                        RespiratorySystem = "Грудная клетка симметричная",
                        RespiratoryBreath = new List<string> { "Везикулярное", "Проводится во все отделы" },
                        Behaviours = new List<string> { "Спокойное", "Адекватное" },
                        RespiratoryRales = "Хрипов нет",
                        ShortnessBreath = "Нет",
                        NeurologicalEvaluation = "Очаговой неврологической симптоматики нет",
                        Eyeballs = new List<string> { "Движения сохранены", "Симметричные" },
                        Nerves = new List<string> { "Черепные нервы без патологии" },
                        TendonReflexes = new List<string> { "Живые", "Симметричные" },
                        MotorAreas = new List<string> { "Движения сохранены", "Парезов нет" },
                        PainSensitivity = new List<string> { "Сохранена" },
                        Aphasia = "Нет",
                        Syndrome = new List<string> { "Интоксикационный синдром" },
                        Throat = new List<string> { "Гиперемирован" },
                        Tonsil = "Умеренно увеличены",
                        IsCoatedWithBloom = false,
                        Stomach = new List<string> { "Мягкий", "Безболезненный" },
                        Symptoms = new List<string> { "Головная боль", "Слабость", "Лихорадка" },
                        Liver = new List<string> { "Не увеличена", "Безболезненная" },
                        UrinarySystem = new List<string> { "Мочеиспускание свободное", "Безболезненное" },
                        MenstrualCycle = 1,
                        PeripheralEdema = "Нет",
                        BloodSugar = "5.2",
                        TreatmentResult = "Состояние улучшилось, температура снизилась",
                        DiagnosticMethods = "Термометрия, пульсоксиметрия, измерение АД",
                        Treatment = "Парацетамол 500 мг, симптоматическая терапия",
                        Consumption = "Парацетамол — 1 таблетка",
                        ReceiveTypeData = "Hospitalization",
                        ReceiveDate = "2026-09-03T10:30:00+05:00",
                        CallType = "1",
                        DescCallType = "Первичный вызов",
                        IsRepeat = false,
                        Triage = 2,
                        IsReanimation = false,
                        ArrivalHospitalPlanTime = "2026-09-03T10:30:00+05:00"
                    }
                }
            }
        };

        /// <summary>Пример ответа сервиса (элемент &lt;data&gt; из responseData).</summary>
        public const string ResponseXml = @"<data xmlns:q1=""http://integrations.gosreestr.kz"" xmlns:xsi=""http://www.w3.org/2001/XMLSchema-instance"" xsi:type=""q1:IntegraResponse"">
  <ResponseInfo>
    <RequestType>AmbulanceCard_LoadCards</RequestType>
    <StatusCode>200</StatusCode>
    <Message>OK</Message>
  </ResponseInfo>
  <AmbulanceCard_LoadCardsResponse>
    <Total>1</Total>
    <Inserted>0</Inserted>
    <Updated>0</Updated>
    <Unchanged>1</Unchanged>
    <Errors>
      <AmbulanceCardErrorDto>
        <RecIndex>0</RecIndex>
        <ExternalId>TEST-AMB-20260903-0001</ExternalId>
        <ReceiveTypeData>Hospitalization</ReceiveTypeData>
        <ReceiveDate>2026-09-03T10:30:00+05:00</ReceiveDate>
        <Code>DuplicateCard</Code>
        <Message>Карта с ExternalId TEST-AMB-20260903-0001 уже существует; для обновления используйте AmbulanceCard_LoadOrUpdateCards</Message>
      </AmbulanceCardErrorDto>
    </Errors>
  </AmbulanceCard_LoadCardsResponse>
</data>";

        public static IntegraResponse Response() => IntegraResponse.Parse(ResponseXml);

        public static void Run()
        {
            Console.WriteLine("----- request <data> -----");
            Console.WriteLine(Request().ToXml());
            var response = Response();
            Console.WriteLine("----- response -----");
            Console.WriteLine($"RequestType={response.ResponseInfo.RequestType} StatusCode={response.ResponseInfo.StatusCode} Message={response.ResponseInfo.Message}");
            var r = response.Result;
            if (r != null)
            {
                Console.WriteLine($"Total={r.Total} Inserted={r.Inserted} Updated={r.Updated} Unchanged={r.Unchanged} Errors={r.Errors.Count}");
                foreach (var e in r.Errors) Console.WriteLine($"  [{e.RecIndex}] {e.Code}: {e.Message}");
            }
        }
    }
}
