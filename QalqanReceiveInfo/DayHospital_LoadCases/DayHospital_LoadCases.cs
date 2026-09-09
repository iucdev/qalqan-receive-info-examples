// QalqanReceiveInfo / DayHospital_LoadCases — случаи дневного стационара.
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

namespace Qalqan.ReceiveInfo.Examples.DayHospital_LoadCases
{
    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"> с RequestType = DayHospital_LoadCases
    // =====================================================================================

    [XmlRoot("data", Namespace = "")]
    public class IntegraRequest
    {
        [XmlAttribute("type", Namespace = "http://www.w3.org/2001/XMLSchema-instance")]
        public string XsiType { get; set; } = "q1:IntegraRequest";

        [XmlAttribute("q1", Namespace = "http://www.w3.org/2000/xmlns/")]
        public string Q1Namespace { get; set; } = "http://integrations.gosreestr.kz";

        /// <summary>Тип операции. Имя блока данных ниже строго привязано к нему.</summary>
        public string RequestType { get; set; } = "DayHospital_LoadCases";

        public LoadDayHospitalCasesRequest? DayHospital_LoadCasesRequest { get; set; }

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

    /// <summary>Пакет случаев дневного стационара (DayHospital_LoadCases / DayHospital_LoadOrUpdateCases).</summary>
    public class LoadDayHospitalCasesRequest
    {
        [XmlArray("List")]
        [XmlArrayItem("DayHospitalCaseRequest")]
        public List<DayHospitalCaseRequest> List { get; set; } = new List<DayHospitalCaseRequest>();
    }

    /// <summary>Пролеченный случай дневного стационара.</summary>
    public class DayHospitalCaseRequest
    {
        /// <summary>Идентификатор случая в системе-источнике (обязателен).</summary>
        public long CaseId { get; set; }

        /// <summary>Организация (МО).</summary>
        public PartyBinRequest Organization { get; set; } = new PartyBinRequest();

        /// <summary>SurId отделения (обязателен).</summary>
        public long DepartmentSurId { get; set; }

        /// <summary>Врач.</summary>
        public PartyIinRequest Doctor { get; set; } = new PartyIinRequest();

        /// <summary>Принадлежность дневного стационара: 100 — при поликлинике, 200 — при стационаре.</summary>
        public DayHospitalType DayHospitalType { get; set; }

        /// <summary>Лечение на дому.</summary>
        public bool AtHome { get; set; }

        /// <summary>Номер карты.</summary>
        public string? CardNumber { get; set; }

        /// <summary>Дата госпитализации (yyyy-MM-dd).</summary>
        [XmlIgnore]
        public DateTime AdmissionDate { get; set; }

        [XmlElement("AdmissionDate")]
        public string AdmissionDateXml
        {
            get => XmlDateFormats.FormatDate(AdmissionDate);
            set => AdmissionDate = XmlDateFormats.ParseDate(value);
        }

        /// <summary>Дата выписки (yyyy-MM-dd).</summary>
        [XmlIgnore]
        public DateTime DischargeDate { get; set; }

        [XmlElement("DischargeDate")]
        public string DischargeDateXml
        {
            get => XmlDateFormats.FormatDate(DischargeDate);
            set => DischargeDate = XmlDateFormats.ParseDate(value);
        }

        /// <summary>Исход лечения: 100, 200, 300, 400, 500, 600, 2000 (см. DictionaryCodes.TreatmentOutcome).</summary>
        public int TreatmentOutcomeCode { get; set; }

        /// <summary>Заключительный диагноз (МКБ-10). Обязателен.</summary>
        public string FinalDiagnosisCode { get; set; } = string.Empty;

        /// <summary>Основная операция (опционально).</summary>
        public CasePrimaryOperationRequest? PrimaryOperation { get; set; }

        /// <summary>Дополнительные операции.</summary>
        [XmlArray("AdditionalOperations")]
        [XmlArrayItem("AdditionalOperationItemRequest")]
        public List<AdditionalOperationItemRequest> AdditionalOperations { get; set; } = new List<AdditionalOperationItemRequest>();

        /// <summary>Услуги.</summary>
        [XmlArray("Services")]
        [XmlArrayItem("DayHospitalServiceItemRequest")]
        public List<DayHospitalServiceItemRequest> Services { get; set; } = new List<DayHospitalServiceItemRequest>();

        /// <summary>Препараты.</summary>
        [XmlArray("Drugs")]
        [XmlArrayItem("DayHospitalDrugItemRequest")]
        public List<DayHospitalDrugItemRequest> Drugs { get; set; } = new List<DayHospitalDrugItemRequest>();

        /// <summary>Платная услуга (опционально).</summary>
        public PaidServiceRequest? PaidService { get; set; }

        /// <summary>Пациент (обязателен).</summary>
        public PatientRequest Patient { get; set; } = new PatientRequest();

        /// <summary>Источник финансирования (обязателен).</summary>
        public FinanceSource BgFinanceSource { get; set; }

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

    /// <summary>Принадлежность дневного стационара.</summary>
    public enum DayHospitalType
    {
        /// <summary>При поликлинике.</summary>
        [XmlEnum("100")] AtPolyclinic = 100,
        /// <summary>При стационаре.</summary>
        [XmlEnum("200")] AtHospital = 200
    }

    /// <summary>Услуга в случае дневного стационара.</summary>
    public class DayHospitalServiceItemRequest
    {
        public string ServiceCode { get; set; } = string.Empty;
        public int Quantity { get; set; }

        /// <summary>Дата услуги (yyyy-MM-dd).</summary>
        [XmlIgnore]
        public DateTime Date { get; set; }

        [XmlElement("Date")]
        public string DateXml
        {
            get => XmlDateFormats.FormatDate(Date);
            set => Date = XmlDateFormats.ParseDate(value);
        }
    }

    /// <summary>Препарат в случае дневного стационара.</summary>
    public class DayHospitalDrugItemRequest
    {
        public string DrugCode { get; set; } = string.Empty;

        /// <summary>Регистрационный номер препарата.</summary>
        public string? RegistrationNumber { get; set; }

        /// <summary>Количество.</summary>
        public decimal Quantity { get; set; }

        /// <summary>Фактическая стоимость, тенге.</summary>
        public decimal? ActualCostTg { get; set; }
        public bool ShouldSerializeActualCostTg() => ActualCostTg.HasValue;
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

    public class LoadDayHospitalCasesResponse : LoadResultBase
    {
        [XmlArray("Errors")]
        [XmlArrayItem("DayHospitalCaseErrorDto")]
        public List<DayHospitalCaseError> Errors { get; set; } = new List<DayHospitalCaseError>();
    }

    /// <summary>Общий результат загрузки пакета (случаи / услуги).</summary>
    public abstract class LoadResultBase
    {
        public int Total { get; set; }
        public int Inserted { get; set; }
        public int Updated { get; set; }
        public int Unchanged { get; set; }
    }

    public class DayHospitalCaseError : RecordErrorBase
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

    public LoadDayHospitalCasesResponse? DayHospital_LoadCasesResponse { get; set; }

    /// <summary>Блок результата (есть только при StatusCode = 200).</summary>
    public LoadDayHospitalCasesResponse? Result => DayHospital_LoadCasesResponse;

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
            DayHospital_LoadCasesRequest = new LoadDayHospitalCasesRequest
            {
                List =
                {
                    new DayHospitalCaseRequest
                    {
                        CaseId = 1005,
                        Organization = new PartyBinRequest { Bin = "990140000001", OrgSurId = 100001 },
                        DepartmentSurId = 200000000000002,
                        Doctor = new PartyIinRequest { DoctorIin = "900101300001" },
                        DayHospitalType = DayHospitalType.AtPolyclinic,          // 100
                        AtHome = false,
                        CardNumber = "K-2026-0001",
                        AdmissionDate = new DateTime(2026, 4, 1),
                        DischargeDate = new DateTime(2026, 4, 10),
                        TreatmentOutcomeCode = 100,                              // не указано
                        FinalDiagnosisCode = "J06.9",
                        PrimaryOperation = new CasePrimaryOperationRequest
                        {
                            Code = "16.08",
                            Date = new DateTimeOffset(2026, 4, 3, 10, 30, 0, TimeSpan.FromHours(5)),
                            OperationComplications = { new OperationComplicationItemRequest { ComplicationCode = 300, ComplicationType = ComplicationType.General } }
                        },
                        AdditionalOperations =
                        {
                            new AdditionalOperationItemRequest
                            {
                                OperationCode = "16.09",
                                OperationDate = new DateTimeOffset(2026, 4, 4, 9, 0, 0, TimeSpan.FromHours(5)),
                                OperationComplications = { new OperationComplicationItemRequest { ComplicationCode = 400, ComplicationType = ComplicationType.Local } }
                            }
                        },
                        Services = { new DayHospitalServiceItemRequest { ServiceCode = "SRV-001", Quantity = 2, Date = new DateTime(2026, 4, 2) } },
                        Drugs = { new DayHospitalDrugItemRequest { DrugCode = "DRG-100", RegistrationNumber = "RN-55512", Quantity = 3.5m, ActualCostTg = 1250.00m } },
                        PaidService = new PaidServiceRequest { PaymentSource = PaymentSource.Patient, AmountTg = 5000.00m },
                        Patient = new PatientRequest { PatientIin = "950101300003", RpnId = 123456789, BirthDate = new DateTime(1995, 1, 1), Sex = PatientSex.Female },
                        BgFinanceSource = FinanceSource.Other,                   // 2
                        IdentificationMethod = new IdentificationMethodRequest { Type = IdentificationMethodType.Biometric }
                    }
                }
            }
        };

        /// <summary>Пример ответа сервиса (элемент &lt;data&gt; из responseData).</summary>
        public const string ResponseXml = @"<data xmlns:q1=""http://integrations.gosreestr.kz"" xmlns:xsi=""http://www.w3.org/2001/XMLSchema-instance"" xsi:type=""q1:IntegraResponse"">
  <ResponseInfo>
    <RequestType>DayHospital_LoadCases</RequestType>
    <StatusCode>200</StatusCode>
    <Message>OK</Message>
  </ResponseInfo>
  <DayHospital_LoadCasesResponse>
    <Total>1</Total>
    <Inserted>1</Inserted>
    <Updated>0</Updated>
    <Unchanged>0</Unchanged>
    <Errors></Errors>
  </DayHospital_LoadCasesResponse>
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
