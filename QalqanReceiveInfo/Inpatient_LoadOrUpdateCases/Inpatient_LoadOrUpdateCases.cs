// QalqanReceiveInfo / Inpatient_LoadOrUpdateCases — случаи круглосуточного стационара.
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

namespace Qalqan.ReceiveInfo.Examples.Inpatient_LoadOrUpdateCases
{
    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"> с RequestType = Inpatient_LoadOrUpdateCases
    // =====================================================================================

    [XmlRoot("data", Namespace = "")]
    public class IntegraRequest
    {
        [XmlAttribute("type", Namespace = "http://www.w3.org/2001/XMLSchema-instance")]
        public string XsiType { get; set; } = "q1:IntegraRequest";

        [XmlAttribute("q1", Namespace = "http://www.w3.org/2000/xmlns/")]
        public string Q1Namespace { get; set; } = "http://integrations.gosreestr.kz";

        /// <summary>Тип операции. Имя блока данных ниже строго привязано к нему.</summary>
        public string RequestType { get; set; } = "Inpatient_LoadOrUpdateCases";

        public LoadInpatientCasesRequest? Inpatient_LoadOrUpdateCasesRequest { get; set; }

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

    /// <summary>Пакет случаев круглосуточного стационара (Inpatient_LoadCases / Inpatient_LoadOrUpdateCases).</summary>
    public class LoadInpatientCasesRequest
    {
        [XmlArray("List")]
        [XmlArrayItem("InpatientCaseRequest")]
        public List<InpatientCaseRequest> List { get; set; } = new List<InpatientCaseRequest>();
    }

    /// <summary>Пролеченный случай круглосуточного стационара.</summary>
    public class InpatientCaseRequest
    {
        public long CaseId { get; set; }
        public PartyBinRequest Organization { get; set; } = new PartyBinRequest();
        public long DepartmentSurId { get; set; }
        public PartyIinRequest Doctor { get; set; } = new PartyIinRequest();
        public PatientRequest Patient { get; set; } = new PatientRequest();

        /// <summary>Признак первичной госпитализации.</summary>
        public bool IsPrimaryHospitalization { get; set; }

        public string? CardNumber { get; set; }

        /// <summary>Код профиля койки (справочник, например 15000).</summary>
        public int BedProfileCode { get; set; }

        /// <summary>Тип госпитализации: 100 плановая, 200/300/400 экстренная (см. DictionaryCodes.HospitalizationType).</summary>
        public int HospitalizationTypeCode { get; set; }

        [XmlIgnore]
        public DateTime AdmissionDate { get; set; }

        [XmlElement("AdmissionDate")]
        public string AdmissionDateXml
        {
            get => XmlDateFormats.FormatDate(AdmissionDate);
            set => AdmissionDate = XmlDateFormats.ParseDate(value);
        }

        [XmlIgnore]
        public DateTime DischargeDate { get; set; }

        [XmlElement("DischargeDate")]
        public string DischargeDateXml
        {
            get => XmlDateFormats.FormatDate(DischargeDate);
            set => DischargeDate = XmlDateFormats.ParseDate(value);
        }

        /// <summary>Идентификатор направления.</summary>
        public long? ReferralId { get; set; }
        public bool ShouldSerializeReferralId() => ReferralId.HasValue;

        /// <summary>Основной диагноз по направлению (МКБ-10).</summary>
        public string? ReferralPrimaryDiagnosisCode { get; set; }

        /// <summary>Предварительный основной диагноз.</summary>
        public string? PreliminaryPrimaryDiagnosisCode { get; set; }

        /// <summary>Основной заключительный диагноз (МКБ-10). Обязателен.</summary>
        public string PrimaryFinalDiagnosisCode { get; set; } = string.Empty;

        /// <summary>Осложняющие заключительные диагнозы. Элементы — &lt;string&gt;.</summary>
        [XmlArray("ComplicatingFinalDiagnosisCode")]
        [XmlArrayItem("string")]
        public List<string> ComplicatingFinalDiagnosisCode { get; set; } = new List<string>();

        /// <summary>Уточняющие заключительные диагнозы.</summary>
        [XmlArray("ClarifyingFinalDiagnosisCodes")]
        [XmlArrayItem("string")]
        public List<string> ClarifyingFinalDiagnosisCodes { get; set; } = new List<string>();

        /// <summary>Сопутствующие заключительные диагнозы.</summary>
        [XmlArray("ConcomitantFinalDiagnosisCodes")]
        [XmlArrayItem("string")]
        public List<string> ConcomitantFinalDiagnosisCodes { get; set; } = new List<string>();

        public CasePrimaryOperationRequest? PrimaryOperation { get; set; }

        [XmlArray("AdditionalOperations")]
        [XmlArrayItem("AdditionalOperationItemRequest")]
        public List<AdditionalOperationItemRequest> AdditionalOperations { get; set; } = new List<AdditionalOperationItemRequest>();

        [XmlArray("Services")]
        [XmlArrayItem("InpatientServiceItemRequest")]
        public List<InpatientServiceItemRequest> Services { get; set; } = new List<InpatientServiceItemRequest>();

        [XmlArray("Drugs")]
        [XmlArrayItem("InpatientDrugItemRequest")]
        public List<InpatientDrugItemRequest> Drugs { get; set; } = new List<InpatientDrugItemRequest>();

        public PaidServiceRequest? PaidService { get; set; }

        /// <summary>Исход пребывания: 200 выписан, 300 переведён, 400 умер, 500 самовольный уход.</summary>
        public int StayOutcomeCode { get; set; }

        /// <summary>Исход лечения (см. DictionaryCodes.TreatmentOutcome).</summary>
        public int TreatmentOutcomeCode { get; set; }

        /// <summary>Онкологический блок.</summary>
        [XmlArray("OncologyCases")]
        [XmlArrayItem("OncologyCaseInfo")]
        public List<OncologyCaseInfo> OncologyCases { get; set; } = new List<OncologyCaseInfo>();

        /// <summary>Акушерский блок (роды).</summary>
        public MaternityCaseInfo? MaternityCase { get; set; }

        /// <summary>Блок новорождённого.</summary>
        public NewbornCaseInfo? NewbornCase { get; set; }

        public FinanceSource BgFinanceSource { get; set; }

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

    /// <summary>Онкологические сведения по случаю.</summary>
    public class OncologyCaseInfo
    {
        public bool IsOncologyDiagnosedFirstTime { get; set; }
        public OncologyTreatmentMethod TreatmentMethod { get; set; }
        /// <summary>Текст схемы лечения (сохраняется как есть).</summary>
        public string TreatmentScheme { get; set; } = string.Empty;
    }

    /// <summary>Метод лечения онкологического случая.</summary>
    public enum OncologyTreatmentMethod
    {
        /// <summary>С химиотерапией (индукция/консолидация).</summary>
        [XmlEnum("1")] WithChemotherapyInductionConsolidation = 1,
        /// <summary>С химиотерапией.</summary>
        [XmlEnum("2")] WithChemotherapy = 2,
        /// <summary>Без химиотерапии.</summary>
        [XmlEnum("3")] WithoutChemotherapy = 3
    }

    /// <summary>Сведения по новорождённому.</summary>
    public class NewbornCaseInfo
    {
        public NewbornBirthData BirthData { get; set; } = new NewbornBirthData();
    }

    public class NewbornBirthData
    {
        public int GestationWeekAtDelivery { get; set; }
        public int NewbornWeightAtBirthGrams { get; set; }
    }

    /// <summary>Акушерский блок по случаю.</summary>
    public class MaternityCaseInfo
    {
        public bool IsRepeatedHospitalizationForDelivery { get; set; }
        public int GestationWeekAtDelivery { get; set; }
        public int FetusesCount { get; set; }

        [XmlArray("Fetuses")]
        [XmlArrayItem("NewbornFetusInfo")]
        public List<NewbornFetusInfo> Fetuses { get; set; } = new List<NewbornFetusInfo>();

        [XmlArray("DeliveryComplicationDiagnosisCodes")]
        [XmlArrayItem("string")]
        public List<string> DeliveryComplicationDiagnosisCodes { get; set; } = new List<string>();

        /// <summary>Уровень кровопотери (обязателен для O67.8).</summary>
        public MaternityCaseBloodLoss? BloodLoss { get; set; }
        public bool ShouldSerializeBloodLoss() => BloodLoss.HasValue;

        /// <summary>Степень активности (обязателен для O98.0).</summary>
        public MaternityCaseInfectionActivity? InfectionActivity { get; set; }
        public bool ShouldSerializeInfectionActivity() => InfectionActivity.HasValue;

        /// <summary>Уровень гемоглобина (обязателен для O99.0).</summary>
        public MaternityCaseHemoglobinLevel? HemoglobinLevel { get; set; }
        public bool ShouldSerializeHemoglobinLevel() => HemoglobinLevel.HasValue;
    }

    /// <summary>Данные по одному плоду/новорождённому.</summary>
    public class NewbornFetusInfo
    {
        public int FetusNumber { get; set; }

        [XmlIgnore]
        public DateTimeOffset BirthDateTime { get; set; }

        [XmlElement("BirthDateTime")]
        public string BirthDateTimeXml
        {
            get => XmlDateFormats.FormatDateTime(BirthDateTime);
            set => BirthDateTime = XmlDateFormats.ParseDateTime(value);
        }

        public int HeightCm { get; set; }
        public PatientSex Sex { get; set; }
        public int NewbornWeightGrams { get; set; }
    }

    /// <summary>Степень активности инфекции (обязателен для диагноза O98.0).</summary>
    public enum MaternityCaseInfectionActivity
    {
        /// <summary>Неактивный.</summary>
        [XmlEnum("1")] Inactive = 1,
        /// <summary>Активная / сомнительная активность.</summary>
        [XmlEnum("2")] ActiveOrDubious = 2
    }

    /// <summary>Уровень гемоглобина (обязателен для диагноза O99.0).</summary>
    public enum MaternityCaseHemoglobinLevel
    {
        /// <summary>69 и ниже.</summary>
        [XmlEnum("1")] HbUpTo69 = 1,
        /// <summary>70 - 109.</summary>
        [XmlEnum("2")] Hb70To109 = 2
    }

    /// <summary>Уровень кровопотери (обязателен для диагноза O67.8).</summary>
    public enum MaternityCaseBloodLoss
    {
        /// <summary>1 литр и менее.</summary>
        [XmlEnum("1")] UpTo1Liter = 1,
        /// <summary>Более 1 литра.</summary>
        [XmlEnum("2")] MoreThan1Liter = 2
    }

    public class InpatientServiceItemRequest
    {
        public string ServiceCode { get; set; } = string.Empty;
        public int Quantity { get; set; }

        [XmlIgnore]
        public DateTime Date { get; set; }

        [XmlElement("Date")]
        public string DateXml
        {
            get => XmlDateFormats.FormatDate(Date);
            set => Date = XmlDateFormats.ParseDate(value);
        }
    }

    public class InpatientDrugItemRequest
    {
        public string DrugCode { get; set; } = string.Empty;
        public string? RegistrationNumber { get; set; }
        public decimal Quantity { get; set; }
        public decimal? ActualCostTg { get; set; }
        public bool ShouldSerializeActualCostTg() => ActualCostTg.HasValue;
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

    /// <summary>Основная операция случая (код + дата/время). Поля опциональны, но задаются вместе.</summary>
    public class CasePrimaryOperationRequest
    {
        /// <summary>Код основной операции (МКБ-9).</summary>
        public string? Code { get; set; }

        /// <summary>Дата/время основной операции.</summary>
        [XmlIgnore]
        public DateTimeOffset? Date { get; set; }

        [XmlElement("Date")]
        public string? DateXml
        {
            get => XmlDateFormats.FormatDateTime(Date);
            set => Date = XmlDateFormats.ParseNullableDateTime(value);
        }

        /// <summary>Осложнения операции.</summary>
        [XmlArray("OperationComplications")]
        [XmlArrayItem("OperationComplicationItemRequest")]
        public List<OperationComplicationItemRequest> OperationComplications { get; set; } = new List<OperationComplicationItemRequest>();
    }

    /// <summary>Осложнение операции.</summary>
    public class OperationComplicationItemRequest
    {
        /// <summary>Код осложнения из справочника (300, 400, 500 ...).</summary>
        public int ComplicationCode { get; set; }

        /// <summary>Тип осложнения: 100 — общее, 200 — местное.</summary>
        public ComplicationType ComplicationType { get; set; }
    }

    /// <summary>Тип осложнения операции.</summary>
    public enum ComplicationType
    {
        /// <summary>Общее.</summary>
        [XmlEnum("100")] General = 100,
        /// <summary>Местное.</summary>
        [XmlEnum("200")] Local = 200
    }

    /// <summary>Дополнительная операция: код, дата и осложнения.</summary>
    public class AdditionalOperationItemRequest
    {
        /// <summary>Код операции (МКБ-9). Обязателен.</summary>
        public string OperationCode { get; set; } = string.Empty;

        /// <summary>Дата/время операции. Обязательна.</summary>
        [XmlIgnore]
        public DateTimeOffset OperationDate { get; set; }

        [XmlElement("OperationDate")]
        public string OperationDateXml
        {
            get => XmlDateFormats.FormatDateTime(OperationDate);
            set => OperationDate = XmlDateFormats.ParseDateTime(value);
        }

        /// <summary>Осложнения операции.</summary>
        [XmlArray("OperationComplications")]
        [XmlArrayItem("OperationComplicationItemRequest")]
        public List<OperationComplicationItemRequest> OperationComplications { get; set; } = new List<OperationComplicationItemRequest>();
    }

    public class LoadInpatientCasesResponse : LoadResultBase
    {
        [XmlArray("Errors")]
        [XmlArrayItem("InpatientCaseErrorDto")]
        public List<InpatientCaseError> Errors { get; set; } = new List<InpatientCaseError>();
    }

    /// <summary>Общий результат загрузки пакета (случаи / услуги).</summary>
    public abstract class LoadResultBase
    {
        public int Total { get; set; }
        public int Inserted { get; set; }
        public int Updated { get; set; }
        public int Unchanged { get; set; }
    }

    public class InpatientCaseError : RecordErrorBase
    {
        public long CaseId { get; set; }
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

    // =====================================================================================
    // ОТВЕТ: <data xsi:type="q1:IntegraResponse">
    // =====================================================================================

    [XmlRoot("data", Namespace = "")]
    public class IntegraResponse
    {
        public ResponseInfo ResponseInfo { get; set; } = new ResponseInfo();

    public LoadInpatientCasesResponse? Inpatient_LoadOrUpdateCasesResponse { get; set; }

    /// <summary>Блок результата (есть только при StatusCode = 200).</summary>
    public LoadInpatientCasesResponse? Result => Inpatient_LoadOrUpdateCasesResponse;

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
            Inpatient_LoadOrUpdateCasesRequest = new LoadInpatientCasesRequest
            {
                List =
                {
                    new InpatientCaseRequest
                    {
                        CaseId = 4,
                        Organization = new PartyBinRequest { Bin = "990140000001", OrgSurId = 100001 },
                        DepartmentSurId = 200000000000001,
                        Doctor = new PartyIinRequest { DoctorIin = "900101300001" },
                        Patient = new PatientRequest { BirthDate = new DateTime(2025, 12, 2), Sex = PatientSex.Male }, // новорождённый без ИИН
                        IsPrimaryHospitalization = true,
                        CardNumber = "6841-1",
                        BedProfileCode = 15000,
                        HospitalizationTypeCode = 400,                           // экстренно свыше 24 ч
                        AdmissionDate = new DateTime(2025, 12, 4),
                        DischargeDate = new DateTime(2025, 12, 8),
                        PrimaryFinalDiagnosisCode = "P59.8",
                        Services =
                        {
                            new InpatientServiceItemRequest { ServiceCode = "D99.590.019", Quantity = 1, Date = new DateTime(2025, 12, 3) },
                            new InpatientServiceItemRequest { ServiceCode = "D99.590.019", Quantity = 1, Date = new DateTime(2025, 12, 4) },
                            new InpatientServiceItemRequest { ServiceCode = "B03.435.002", Quantity = 1, Date = new DateTime(2025, 12, 5) },
                            new InpatientServiceItemRequest { ServiceCode = "B03.398.002", Quantity = 1, Date = new DateTime(2025, 12, 5) },
                            new InpatientServiceItemRequest { ServiceCode = "B02.114.002", Quantity = 1, Date = new DateTime(2025, 12, 6) },
                        },
                        StayOutcomeCode = 200,                                   // выписан
                        TreatmentOutcomeCode = 200,                              // выздоровление
                        NewbornCase = new NewbornCaseInfo { BirthData = new NewbornBirthData { GestationWeekAtDelivery = 40, NewbornWeightAtBirthGrams = 4500 } },
                        BgFinanceSource = FinanceSource.Gobmp                    // 0
                    }
                }
            }
        };

        /// <summary>Пример ответа сервиса (элемент &lt;data&gt; из responseData).</summary>
        public const string ResponseXml = @"<data xmlns:q1=""http://integrations.gosreestr.kz"" xmlns:xsi=""http://www.w3.org/2001/XMLSchema-instance"" xsi:type=""q1:IntegraResponse"">
  <ResponseInfo>
    <RequestType>Inpatient_LoadOrUpdateCases</RequestType>
    <StatusCode>200</StatusCode>
    <Message>OK</Message>
  </ResponseInfo>
  <Inpatient_LoadOrUpdateCasesResponse>
    <Total>1</Total>
    <Inserted>0</Inserted>
    <Updated>1</Updated>
    <Unchanged>0</Unchanged>
    <Errors></Errors>
  </Inpatient_LoadOrUpdateCasesResponse>
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
