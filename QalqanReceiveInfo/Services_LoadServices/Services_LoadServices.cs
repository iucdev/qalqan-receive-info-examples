// QalqanReceiveInfo / Services_LoadServices — медицинские услуги.
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

namespace Qalqan.ReceiveInfo.Examples.Services_LoadServices
{
    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"> с RequestType = Services_LoadServices
    // =====================================================================================

    [XmlRoot("data", Namespace = "")]
    public class IntegraRequest
    {
        [XmlAttribute("type", Namespace = "http://www.w3.org/2001/XMLSchema-instance")]
        public string XsiType { get; set; } = "q1:IntegraRequest";

        [XmlAttribute("q1", Namespace = "http://www.w3.org/2000/xmlns/")]
        public string Q1Namespace { get; set; } = "http://integrations.gosreestr.kz";

        /// <summary>Тип операции. Имя блока данных ниже строго привязано к нему.</summary>
        public string RequestType { get; set; } = "Services_LoadServices";

        public LoadServicesRequest? Services_LoadServicesRequest { get; set; }

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

    /// <summary>Пакет медицинских услуг (Services_LoadServices / Services_LoadOrUpdateServices).</summary>
    public class LoadServicesRequest
    {
        [XmlArray("List")]
        [XmlArrayItem("ServiceItemRequest")]
        public List<ServiceItemRequest> List { get; set; } = new List<ServiceItemRequest>();
    }

    /// <summary>Единичная медицинская услуга.</summary>
    public class ServiceItemRequest
    {
        /// <summary>Уникальный идентификатор услуги в системе-источнике (обязателен).</summary>
        public long ServiceId { get; set; }

        /// <summary>Дата/время оказания услуги (обязательно).</summary>
        [XmlIgnore]
        public DateTimeOffset ServiceAt { get; set; }

        [XmlElement("ServiceAt")]
        public string ServiceAtXml
        {
            get => XmlDateFormats.FormatDateTime(ServiceAt);
            set => ServiceAt = XmlDateFormats.ParseDateTime(value);
        }

        /// <summary>Заказчик услуги (обязателен, должен содержать OrgSurId).</summary>
        public PartyBinRequest Customer { get; set; } = new PartyBinRequest();

        /// <summary>Исполнитель услуги (обязателен, должен содержать OrgSurId).</summary>
        public ExecutorBinRequest Executor { get; set; } = new ExecutorBinRequest();

        /// <summary>Врач (обязателен всегда, кроме услуг класса B).</summary>
        public PartyIinRequest? Doctor { get; set; }

        /// <summary>Пациент (обязателен).</summary>
        public PatientRequest Patient { get; set; } = new PatientRequest();

        /// <summary>Сведения о беременности пациентки (влияют на оплату).</summary>
        public PatientPregnancyInfoRequest? PatientPregnancyInfo { get; set; }

        /// <summary>Категория пациента (справочник ЕПС). Обязателен.</summary>
        public PersonCategory PersonCategory { get; set; }

        /// <summary>Источник финансирования (обязателен).</summary>
        public FinanceSource EpsFinanceSource { get; set; }

        /// <summary>Данные направления (опционально).</summary>
        public ReferralRequest? Referral { get; set; }

        /// <summary>Повод обращения — строковый код ЕПС, например "17" (скрининг). Обязателен.</summary>
        public string VisitReason { get; set; } = string.Empty;

        /// <summary>Место оказания услуги — строковый код ЕПС, например "П" (в поликлинике). Обязателен.</summary>
        public string ServicePlace { get; set; } = string.Empty;

        /// <summary>Код услуги по тарификатору (обязателен).</summary>
        public string ServiceCode { get; set; } = string.Empty;

        /// <summary>Код диагноза МКБ-10 (обязателен).</summary>
        public string DiagnosisCode { get; set; } = string.Empty;

        /// <summary>Признаки платной услуги (если задано — услуга считается платной).</summary>
        public PaidServiceRequest? PaidService { get; set; }

        /// <summary>Идентификаторы скринингов ЕПС (для отложенной оплаты услуг скрининга).</summary>
        [XmlArray("ScreeningIds")]
        [XmlArrayItem("string")]
        public List<string>? ScreeningIds { get; set; }

        /// <summary>Информация об удалении услуги.</summary>
        public ServiceDeletionInfo? DeletionInfo { get; set; }

        /// <summary>Метод идентификации пациента (опционально).</summary>
        public IdentificationMethodRequest? IdentificationMethod { get; set; }
    }

    /// <summary>
    /// Форматы дат в XML сервиса:
    /// даты без времени — <c>yyyy-MM-dd</c>, дата/время — ISO 8601 со смещением (<c>2026-04-03T10:30:00+05:00</c>).
    /// </summary>
    public static class XmlDateFormats
    {
        public const string Date = "yyyy-MM-dd";
        public const string DateTimeOffset = "yyyy-MM-dd'T'HH:mm:ss.FFFFFFFK";

        public static string FormatDate(DateTime value) =>
            value.ToString(Date, CultureInfo.InvariantCulture);

        public static DateTime ParseDate(string value) =>
            DateTime.ParseExact(value, Date, CultureInfo.InvariantCulture, DateTimeStyles.None);

        public static string FormatDateTime(DateTimeOffset value) =>
            value.ToString(DateTimeOffset, CultureInfo.InvariantCulture);

        public static DateTimeOffset ParseDateTime(string value) =>
            System.DateTimeOffset.Parse(value, CultureInfo.InvariantCulture, DateTimeStyles.RoundtripKind);

        public static string? FormatDateTime(DateTimeOffset? value) =>
            value.HasValue ? FormatDateTime(value.Value) : null;

        public static DateTimeOffset? ParseNullableDateTime(string? value) =>
            string.IsNullOrWhiteSpace(value) ? (DateTimeOffset?)null : ParseDateTime(value!);
    }

    /// <summary>Информация об удалении услуги.</summary>
    public class ServiceDeletionInfo
    {
        /// <summary>Момент, когда услуга помечена удалённой.</summary>
        [XmlIgnore]
        public DateTimeOffset DeletedAt { get; set; }

        [XmlElement("DeletedAt")]
        public string DeletedAtXml
        {
            get => XmlDateFormats.FormatDateTime(DeletedAt);
            set => DeletedAt = XmlDateFormats.ParseDateTime(value);
        }
    }

    /// <summary>Данные направления.</summary>
    public class ReferralRequest
    {
        /// <summary>Идентификатор направления ЕПС. Обязателен.</summary>
        public string EpsReferralId { get; set; } = string.Empty;

        /// <summary>Идентификатор направления в МИС.</summary>
        public long? ReferralId { get; set; }
        public bool ShouldSerializeReferralId() => ReferralId.HasValue;

        /// <summary>Дата/время направления. Обязательна.</summary>
        [XmlIgnore]
        public DateTimeOffset ReferralAt { get; set; }

        [XmlElement("ReferralAt")]
        public string ReferralAtXml
        {
            get => XmlDateFormats.FormatDateTime(ReferralAt);
            set => ReferralAt = XmlDateFormats.ParseDateTime(value);
        }

        /// <summary>Код услуги направления по тарификатору. Обязателен.</summary>
        public string ReferralServiceCode { get; set; } = string.Empty;

        /// <summary>Код диагноза направления (МКБ-10). Обязателен.</summary>
        public string ReferralDiagnosisCode { get; set; } = string.Empty;

        /// <summary>Повод обращения (код ЕПС). Обязателен.</summary>
        public string ReferralVisitReason { get; set; } = string.Empty;
    }

    /// <summary>Категория пациента (справочник ЕПС).</summary>
    public enum PersonCategory
    {
        [XmlEnum("0")] Undefined = 0,
        /// <summary>Гражданин РК.</summary>
        [XmlEnum("1")] KazakhstanCitizen = 1,
        /// <summary>Кандас.</summary>
        [XmlEnum("2")] Kandas = 2,
        /// <summary>Трудовой мигрант.</summary>
        [XmlEnum("3")] LaborMigrant = 3,
        /// <summary>Иностранец с заболеванием, представляющим опасность для окружающих.</summary>
        [XmlEnum("4")] ForeignerWithDangerousInfectiousDisease = 4,
        /// <summary>Мертворожденный ребёнок.</summary>
        [XmlEnum("5")] StillbornChild = 5,
        /// <summary>Ранняя неонатальная смерть (до 28 дней).</summary>
        [XmlEnum("6")] EarlyNeonatalDeathUpTo28Days = 6,
        /// <summary>Лицо без определённого места жительства.</summary>
        [XmlEnum("7")] HomelessPerson = 7,
        /// <summary>Неустановленное лицо.</summary>
        [XmlEnum("8")] UnidentifiedPerson = 8,
        /// <summary>Постоянно проживающий иностранец / лицо без гражданства.</summary>
        [XmlEnum("9")] PermanentResidentForeignerOrStatelessPerson = 9,
        /// <summary>Недоношенный плод.</summary>
        [XmlEnum("10")] PrematureFetus = 10,
        /// <summary>Временно пребывающий иностранец (приложение 21).</summary>
        [XmlEnum("11")] TemporarilyStayingForeignerByAppendix21 = 11
    }

    /// <summary>Пациент. Допускается ИИН и/или RpnId.</summary>
    public class PatientRequest
    {
        /// <summary>ИИН пациента (12 цифр). Может отсутствовать, если указан RpnId.</summary>
        public string? PatientIin { get; set; }

        /// <summary>Идентификатор пациента в РПН (RpnId). Может использоваться вместо ИИН.</summary>
        public long? RpnId { get; set; }
        public bool ShouldSerializeRpnId() => RpnId.HasValue;

        /// <summary>Дата рождения (обязательна). В XML — yyyy-MM-dd.</summary>
        [XmlIgnore]
        public DateTime BirthDate { get; set; }

        [XmlElement("BirthDate")]
        public string BirthDateXml
        {
            get => XmlDateFormats.FormatDate(BirthDate);
            set => BirthDate = XmlDateFormats.ParseDate(value);
        }

        /// <summary>Пол пациента (обязателен).</summary>
        public PatientSex Sex { get; set; }
    }

    /// <summary>Пол пациента.</summary>
    public enum PatientSex
    {
        /// <summary>Женский.</summary>
        [XmlEnum("0")] Female = 0,
        /// <summary>Мужской.</summary>
        [XmlEnum("1")] Male = 1,
        /// <summary>Не определено.</summary>
        [XmlEnum("2")] Unspecified = 2
    }

    /// <summary>Сведения о беременности пациентки.</summary>
    public class PatientPregnancyInfoRequest
    {
        /// <summary>Дата постановки на учёт по беременности.</summary>
        [XmlIgnore]
        public DateTimeOffset RegisteredAt { get; set; }

        [XmlElement("RegisteredAt")]
        public string RegisteredAtXml
        {
            get => XmlDateFormats.FormatDateTime(RegisteredAt);
            set => RegisteredAt = XmlDateFormats.ParseDateTime(value);
        }

        /// <summary>Срок беременности, недели.</summary>
        public int GestationalAgeWeeks { get; set; }
    }

    /// <summary>Врач.</summary>
    public class PartyIinRequest
    {
        /// <summary>ИИН врача (12 цифр).</summary>
        public string? DoctorIin { get; set; }
    }

    /// <summary>Организация: БИН (12 цифр) и/или SurId в реестре.</summary>
    public class PartyBinRequest
    {
        /// <summary>БИН организации (12 цифр). Опционален, если указан OrgSurId.</summary>
        public string? Bin { get; set; }

        /// <summary>Идентификатор организации в реестре (SurId). Обязателен.</summary>
        public long OrgSurId { get; set; }
    }

    /// <summary>Признаки платной услуги. Если объект задан — AmountTg должен быть больше 0.</summary>
    public class PaidServiceRequest
    {
        /// <summary>Источник оплаты.</summary>
        public PaymentSource? PaymentSource { get; set; }
        public bool ShouldSerializePaymentSource() => PaymentSource.HasValue;

        /// <summary>Сумма оплаты, тенге.</summary>
        public decimal? AmountTg { get; set; }
        public bool ShouldSerializeAmountTg() => AmountTg.HasValue;
    }

    /// <summary>Источник оплаты платной услуги.</summary>
    public enum PaymentSource
    {
        /// <summary>Пациент.</summary>
        [XmlEnum("1")] Patient = 1,
        /// <summary>ДМС.</summary>
        [XmlEnum("2")] Dms = 2,
        /// <summary>Другое.</summary>
        [XmlEnum("3")] Other = 3
    }

    /// <summary>Метод идентификации пациента.</summary>
    public class IdentificationMethodRequest
    {
        /// <summary>Тип метода идентификации.</summary>
        public IdentificationMethodType Type { get; set; }
    }

    /// <summary>Метод идентификации пациента.</summary>
    public enum IdentificationMethodType
    {
        /// <summary>Биометрическая идентификация.</summary>
        [XmlEnum("1")] Biometric = 1,
        /// <summary>Код доступа к цифровым документам.</summary>
        [XmlEnum("2")] DigitalDocsAccessCode = 2,
        /// <summary>Другое.</summary>
        [XmlEnum("3")] Other = 3,
        /// <summary>Не производилась.</summary>
        [XmlEnum("4")] NotPerformed = 4
    }

    /// <summary>Источник финансирования (BgFinanceSource / EpsFinanceSource).</summary>
    public enum FinanceSource
    {
        /// <summary>ГОБМП.</summary>
        [XmlEnum("0")] Gobmp = 0,
        /// <summary>ОСМС.</summary>
        [XmlEnum("1")] Osms = 1,
        /// <summary>Другое.</summary>
        [XmlEnum("2")] Other = 2
    }

    /// <summary>Организация-исполнитель услуги.</summary>
    public class ExecutorBinRequest
    {
        /// <summary>БИН организации-исполнителя (12 цифр).</summary>
        public string? Bin { get; set; }

        /// <summary>Идентификатор организации-исполнителя в реестре (SurId). Обязателен.</summary>
        public long OrgSurId { get; set; }

        /// <summary>Идентификатор отделения (SurId). Имя элемента именно <c>DepartmentSurid</c>.</summary>
        public long? DepartmentSurid { get; set; }
        public bool ShouldSerializeDepartmentSurid() => DepartmentSurid.HasValue;
    }

    public class LoadServicesResponse : LoadResultBase
    {
        [XmlArray("Errors")]
        [XmlArrayItem("ServiceErrorDto")]
        public List<ServiceError> Errors { get; set; } = new List<ServiceError>();
    }

    public class ServiceError : RecordErrorBase
    {
        public long ServiceId { get; set; }
    }

    /// <summary>Ошибка по одной записи пакета.</summary>
    public abstract class RecordErrorBase
    {
        /// <summary>Порядковый номер записи в исходном запросе (с 0); -1 если не определён.</summary>
        public int RecIndex { get; set; } = -1;
        public string? PatientIin { get; set; }
        [XmlElement(IsNullable = true)]
        public long? PatientRpnId { get; set; }
        public string Code { get; set; } = string.Empty;
        public string Message { get; set; } = string.Empty;
    }

    /// <summary>Общий результат загрузки пакета (случаи / услуги).</summary>
    public abstract class LoadResultBase
    {
        public int Total { get; set; }
        public int Inserted { get; set; }
        public int Updated { get; set; }
        public int Unchanged { get; set; }
    }

    // =====================================================================================
    // ОТВЕТ: <data xsi:type="q1:IntegraResponse">
    // =====================================================================================

    [XmlRoot("data", Namespace = "")]
    public class IntegraResponse
    {
        public ResponseInfo ResponseInfo { get; set; } = new ResponseInfo();

    public LoadServicesResponse? Services_LoadServicesResponse { get; set; }

    /// <summary>Блок результата (есть только при StatusCode = 200).</summary>
    public LoadServicesResponse? Result => Services_LoadServicesResponse;

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
            Services_LoadServicesRequest = new LoadServicesRequest
            {
                List =
                {
                    new ServiceItemRequest
                    {
                        ServiceId = 9005,
                        ServiceAt = new DateTimeOffset(2026, 4, 10, 10, 30, 0, TimeSpan.Zero),
                        Customer = new PartyBinRequest { Bin = "990140000001", OrgSurId = 100001 },
                        Executor = new ExecutorBinRequest { Bin = "990140000001", OrgSurId = 100001, DepartmentSurid = 200000000000001 },
                        Doctor = new PartyIinRequest { DoctorIin = "900101300001" },
                        Patient = new PatientRequest { PatientIin = "950101300003", BirthDate = new DateTime(1995, 1, 1), Sex = PatientSex.Male },
                        PersonCategory = PersonCategory.KazakhstanCitizen,       // 1
                        EpsFinanceSource = FinanceSource.Osms,                   // 1
                        VisitReason = "17",                                      // скрининг (код ЕПС)
                        ServicePlace = "П",                                      // в поликлинике (код ЕПС)
                        ServiceCode = "B01.047.001",
                        DiagnosisCode = "J06.9",
                        Referral = new ReferralRequest
                        {
                            EpsReferralId = "1234567890123456789012345678901234567890",
                            ReferralId = 12345,
                            ReferralAt = new DateTimeOffset(2026, 4, 9, 9, 0, 0, TimeSpan.Zero),
                            ReferralServiceCode = "B01.047.001",
                            ReferralDiagnosisCode = "J06.9",
                            ReferralVisitReason = "20"                           // антенатальное наблюдение
                        }
                    }
                }
            }
        };

        /// <summary>Пример ответа сервиса (элемент &lt;data&gt; из responseData).</summary>
        public const string ResponseXml = @"<data xmlns:q1=""http://integrations.gosreestr.kz"" xmlns:xsi=""http://www.w3.org/2001/XMLSchema-instance"" xsi:type=""q1:IntegraResponse"">
  <ResponseInfo>
    <RequestType>Services_LoadServices</RequestType>
    <StatusCode>200</StatusCode>
    <Message>OK</Message>
  </ResponseInfo>
  <Services_LoadServicesResponse>
    <Total>1</Total>
    <Inserted>1</Inserted>
    <Updated>0</Updated>
    <Unchanged>0</Unchanged>
    <Errors></Errors>
  </Services_LoadServicesResponse>
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
