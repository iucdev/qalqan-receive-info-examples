// QalqanReceiveInfoFromMIS / SendEvents — передача пакета событий МИС.
// Файл самодостаточен: DTO запроса (ZippedEventsInBase64 = Base64(gzip(JSON пакета событий))), DTO ответа и заполненный пример.
// Формируется только бизнес-нагрузка <data> (содержимое requestData / responseData конверта ШЭП);
// сам конверт SendMessage, requestInfo и транспортная подпись — стандартная часть ШЭП и здесь не рассматриваются.
// versionHash в примере — заглушка: реальное значение вычисляет МИС (при несовпадении сервер отвечает Status = InvalidHash).
// Паспорт сервиса: https://sb.egov.kz/services/passport/ORGAM-S-9847
using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Xml;
using System.Xml.Serialization;

namespace Qalqan.ReceiveInfoFromMis.Examples.SendEvents
{
    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"><ZippedEventsInBase64>…</ZippedEventsInBase64></data>
    // =====================================================================================

    [XmlRoot("data", Namespace = "")]
    public class IntegraRequest
    {
        [XmlAttribute("type", Namespace = "http://www.w3.org/2001/XMLSchema-instance")]
        public string XsiType { get; set; } = "q1:IntegraRequest";

        /// <summary>Пространство имён типов сервиса из его XSD (businessData.xsd).</summary>
        [XmlAttribute("q1", Namespace = "http://www.w3.org/2000/xmlns/")]
        public string Q1Namespace { get; set; } = "http://integrations.e-qazyna.kz";

        /// <summary>Base64( gzip( JSON пакета событий ) ), см. <see cref="MisEventsPacker"/>.</summary>
        public string ZippedEventsInBase64 { get; set; } = string.Empty;

        private static readonly XmlSerializer Serializer = new XmlSerializer(typeof(IntegraRequest));

        public static IntegraRequest FromEvents(MisEventsPayload payload) =>
            new IntegraRequest { ZippedEventsInBase64 = MisEventsPacker.Pack(payload) };

        /// <summary>XML элемента &lt;data&gt; — помещается в requestData конверта ШЭП.</summary>
        public string ToXml()
        {
            var sb = new StringBuilder();
            using (var w = XmlWriter.Create(sb, new XmlWriterSettings { OmitXmlDeclaration = true, Indent = true }))
                Serializer.Serialize(w, this);
            return sb.ToString();
        }
    }

    /// <summary>
    /// Пакет событий МИС. В XML передаётся как JSON, сжатый gzip и закодированный Base64
    /// (элемент <c>ZippedEventsInBase64</c>), см. <see cref="MisEventsPacker"/>.
    /// </summary>
    public class MisEventsPayload
    {
        [JsonPropertyName("events")]
        public List<MisEvent> Events { get; set; } = new List<MisEvent>();
    }

    /// <summary>Одно событие МИС.</summary>
    public class MisEvent
    {
        /// <summary>Тип сущности (числовой код <see cref="MisEntityType"/>).</summary>
        [JsonPropertyName("entityType")]
        public MisEntityType EntityType { get; set; }

        /// <summary>Момент события: unix-время в секундах (допускается дробная часть).</summary>
        [JsonPropertyName("timestamp")]
        public double Timestamp { get; set; }

        /// <summary>Хэш версии события (hex, 64 символа). Вычисляется МИС; сервер отвечает <c>InvalidHash</c>, если он не сходится.</summary>
        [JsonPropertyName("versionHash")]
        public string VersionHash { get; set; } = string.Empty;

        /// <summary>Идентификатор медицинской организации.</summary>
        [JsonPropertyName("moId")]
        public long MoId { get; set; }

        /// <summary>БИН медицинской организации.</summary>
        [JsonPropertyName("organizationBin")]
        public string OrganizationBin { get; set; } = string.Empty;

        /// <summary>БИН владельца МИС (отправителя).</summary>
        [JsonPropertyName("misBin")]
        public string MisBin { get; set; } = string.Empty;

        /// <summary>
        /// Тело события — JSON-строка (сериализованный объект события; состав полей зависит от <c>eventType</c>).
        /// Обязательные общие поля: <c>eventId</c>, <c>eventType</c>, <c>misId</c>, <c>organizationId</c>, <c>organizationBin</c>, <c>patientInformation</c>.
        /// </summary>
        [JsonPropertyName("data")]
        public string Data { get; set; } = string.Empty;
    }

    /// <summary>Тип сущности (события) МИС — поле <c>entityType</c> события QalqanReceiveInfoFromMIS. Передаётся числом.</summary>
    public enum MisEntityType
    {
        MedicationAssignmentExecution = 0,
        BloodTransfusion = 1,
        ReceptionAppeal = 2,
        Assessment = 3,
        HospitalTransfer = 4,
        MedicalAppointment = 5,
        RehabilitationCard = 6,
        DiseaseManagementRegistration = 7,
        PreoperativeSummary = 8,
        MonitoringFertileWoman = 9,
        RecordAppointment = 10,
        HospitalizationRefusal = 11,
        NewbornReanimation = 12,
        PatientPlacement = 13,
        Disability = 14,
        AmbulanceCall = 15,
        DispensaryRegistration = 16,
        MedicationPrescription = 17,
        ReferralBg = 18,
        ProvisionCare = 19,
        TreatmentPlanning = 20,
        CardioPulmonaryReanimation = 21,
        IllTreatment = 22,
        RehabCase = 23,
        PregnancyRegistration = 24,
        MedicationAssignment = 25,
        NewbornHistory = 26,
        ReferralKdu = 27,
        Active = 28,
        Surgery = 29,
        MonitoringPregnantWoman = 30,
        MedicalWithdrawal = 31,
        BloodProductRecord = 32,
        MedicationRecipe = 33,
        Hemodialysis = 34,
        DispensaryDeregistration = 35,
        BirthRegistration = 36,
        DiseaseManagementMonitoring = 37,
        DeathConclusion = 38,
        HouseCall = 39,
        Hospitalization = 40,
        SelfMonitoringDiseaseManagement = 41,
        NurseConsultation = 42,
        CompletionCourseTreatment = 43,
        Anesthesia = 44,
        AssignmentKdu = 45,
        PregnancyDeregistration = 46,
        DiseaseManagementExclusion = 47,
        Vaccination = 48,
        NurseCare = 49,
        InpatientMedicalExamination = 50,
        Discharge = 51,
        BrainDeathConfirmation = 52,
        MedicalCommissionConclusion = 53,
        DynamicMonitoring = 54,
        BiologicalDeathConfirmation = 55,
        Service = 56,
        DeathFactEstablishment = 57,
        DonorFunction = 58,
        Screening = 59,
        NppiVaccination = 60,
        DeliveryHistory = 61,
        AmbulanceCard = 62,
        SubmitActualCosts = 63
    }

    /// <summary>Тип действия над сущностью.</summary>
    public enum MisActionType
    {
        Create = 0,
        Update = 1,
        Remove = 2
    }

    /// <summary>Упаковка пакета событий: JSON → gzip → Base64 (и обратно).</summary>
    public static class MisEventsPacker
    {
        private static readonly JsonSerializerOptions JsonOptions = new JsonSerializerOptions
        {
            Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
            PropertyNameCaseInsensitive = true
        };

        /// <summary>Сериализует пакет в JSON (без отступов).</summary>
        public static string ToJson(MisEventsPayload payload) => JsonSerializer.Serialize(payload, JsonOptions);

        /// <summary>Сериализует произвольный объект тела события в JSON-строку для поля <see cref="MisEvent.Data"/>.</summary>
        public static string DataToJson(object eventBody) => JsonSerializer.Serialize(eventBody, JsonOptions);

        /// <summary>JSON → gzip → Base64.</summary>
        public static string Pack(MisEventsPayload payload) => PackJson(ToJson(payload));

        public static string PackJson(string json)
        {
            using (var output = new MemoryStream())
            {
                using (var gzip = new GZipStream(output, CompressionLevel.Optimal, leaveOpen: true))
                {
                    var bytes = Encoding.UTF8.GetBytes(json);
                    gzip.Write(bytes, 0, bytes.Length);
                }
                return Convert.ToBase64String(output.ToArray());
            }
        }

        /// <summary>Base64 → gunzip → JSON.</summary>
        public static string UnpackJson(string base64)
        {
            var bytes = Convert.FromBase64String(base64.Trim());
            using (var input = new MemoryStream(bytes))
            using (var gzip = new GZipStream(input, CompressionMode.Decompress))
            using (var reader = new StreamReader(gzip, Encoding.UTF8))
            {
                return reader.ReadToEnd();
            }
        }

        public static MisEventsPayload Unpack(string base64) =>
            JsonSerializer.Deserialize<MisEventsPayload>(UnpackJson(base64), JsonOptions) ?? new MisEventsPayload();
    }

    // =====================================================================================
    // ОТВЕТ: <data xsi:type="q1:IntegraResponse">
    // =====================================================================================

    public enum ResponseStatus
    {
        Success,
        /// <summary>Хэш версии (versionHash) хотя бы одного события не сошёлся.</summary>
        InvalidHash,
        Error
    }

    [XmlRoot("data", Namespace = "")]
    public class IntegraResponse
    {
        public bool IsSuccess { get; set; }

        [XmlElement(IsNullable = true)]
        public string? ErrorMessage { get; set; }

        public ResponseStatus Status { get; set; }

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

    // =====================================================================================
    // ПРИМЕР
    // =====================================================================================

    public static class Example
    {
        private const string OrgBin = "990140000001";
        private const long MoId = 100001;
        private const string PatientIin = "950101300003";
        private const long PatientRpnId = 123456789;
        private const string DoctorIin = "900101300001";

        /// <summary>Событие «Выписка из стационара» (entityType 51, eventType DISCHARGE).</summary>
        public static MisEvent DischargeEvent()
        {
            var body = new Dictionary<string, object?>
            {
                ["eventId"] = "05ef2486-d9e0-4ec6-9b25-a75bc629d13e",
                ["eventType"] = "DISCHARGE",
                ["misId"] = "10",
                ["organizationId"] = MoId.ToString(),
                ["organizationBin"] = OrgBin,
                ["hospitalizationId"] = "6800000000000001",
                ["bedDays"] = 10,
                ["dischargeDateTime"] = "2026-07-15T17:00:00.000Z",
                ["outcome"] = "200",
                ["treatmentOutcome"] = "200",
                ["inPatientPayType"] = "200",
                ["diagList"] = new[] { new Dictionary<string, object?> { ["mkb10"] = "N73.3", ["diagKind"] = "400", ["diagType"] = "300" } },
                ["dischargeNewborn"] = Array.Empty<object>(),
                ["epicrisis"] = Array.Empty<object>(),
                ["surgeryList"] = Array.Empty<object>(),
                ["patientInformation"] = new Dictionary<string, object?> { ["iin"] = PatientIin, ["rpnId"] = PatientRpnId.ToString() },
                ["signatures"] = new Dictionary<string, object?> { ["signerInformation"] = new Dictionary<string, object?>() }
            };
            return new MisEvent
            {
                EntityType = MisEntityType.Discharge,
                Timestamp = 1784160231.378,
                VersionHash = "0000000000000000000000000000000000000000000000000000000000000000",
                MoId = MoId,
                OrganizationBin = OrgBin,
                MisBin = OrgBin,
                Data = MisEventsPacker.DataToJson(body)
            };
        }

        /// <summary>Событие «Актив» (entityType 28, eventType ACTIVE).</summary>
        public static MisEvent ActiveEvent()
        {
            var body = new Dictionary<string, object?>
            {
                ["eventId"] = "56427533-b3eb-441e-9d4d-aa83d71b2e04",
                ["eventType"] = "ACTIVE",
                ["misId"] = 10,
                ["organizationId"] = MoId,
                ["organizationBin"] = OrgBin,
                ["patientInformation"] = new Dictionary<string, object?> { ["iin"] = PatientIin, ["rpnId"] = PatientRpnId.ToString() },
                ["signature"] = null,
                ["senderOrganizationId"] = MoId.ToString(),
                ["senderOrganizationBin"] = OrgBin,
                ["senderDoctorId"] = "0",
                ["senderDoctorIin"] = null,
                ["receiverOrganizationId"] = MoId.ToString(),
                ["receiverOrganizationBin"] = OrgBin,
                ["receiverDoctorId"] = "45500000000000001",
                ["receiverDoctorIin"] = DoctorIin,
                ["activeStatusId"] = 200,
                ["changeDateTime"] = "2026-03-05T09:15:47.150Z",
                ["regPostId"] = 45500000000000002,
                ["regPostIin"] = "900101300002",
                ["rejectReason"] = null,
                ["sicks"] = Array.Empty<object>(),
                ["activeDeliveryStatusId"] = null,
                ["receiveDate"] = "2026-03-06T06:50:00.000Z",
                ["outcomeId"] = null,
                ["referralServiceExecId"] = null,
                ["visitType"] = "4",
                ["dischargeId"] = null
            };
            return new MisEvent
            {
                EntityType = MisEntityType.Active,
                Timestamp = 1772787347.150,
                VersionHash = "0000000000000000000000000000000000000000000000000000000000000000",
                MoId = MoId,
                OrganizationBin = OrgBin,
                MisBin = OrgBin,
                Data = MisEventsPacker.DataToJson(body)
            };
        }

        /// <summary>Заполненный запрос: пакет из двух событий (DISCHARGE и ACTIVE).</summary>
        public static IntegraRequest Request() =>
            IntegraRequest.FromEvents(new MisEventsPayload { Events = { DischargeEvent(), ActiveEvent() } });

        /// <summary>Пример успешного ответа сервиса (элемент &lt;data&gt; из responseData).</summary>
        public const string ResponseXml = @"<data xmlns:q1=""http://integrations.e-qazyna.kz"" xmlns:xsi=""http://www.w3.org/2001/XMLSchema-instance"" xsi:type=""q1:IntegraResponse"">
      <IsSuccess>true</IsSuccess>
      <Status>Success</Status>
    </data>";

        /// <summary>Пример ответа при несовпадении versionHash.</summary>
        public const string ResponseXmlInvalidHash = @"<data xmlns:q1=""http://integrations.e-qazyna.kz"" xmlns:xsi=""http://www.w3.org/2001/XMLSchema-instance"" xsi:type=""q1:IntegraResponse"">
      <IsSuccess>false</IsSuccess>
      <ErrorMessage>versionHash события 05ef2486-d9e0-4ec6-9b25-a75bc629d13e не совпадает</ErrorMessage>
      <Status>InvalidHash</Status>
    </data>";

        public static IntegraResponse Response() => IntegraResponse.Parse(ResponseXml);

        public static void Run()
        {
            var request = Request();
            Console.WriteLine("----- request <data> -----");
            Console.WriteLine(request.ToXml());
            Console.WriteLine("----- распакованный JSON пакета событий -----");
            Console.WriteLine(MisEventsPacker.UnpackJson(request.ZippedEventsInBase64));
            var response = Response();
            Console.WriteLine("----- response -----");
            Console.WriteLine($"IsSuccess={response.IsSuccess} Status={response.Status} ErrorMessage={response.ErrorMessage}");
            var bad = IntegraResponse.Parse(ResponseXmlInvalidHash);
            Console.WriteLine($"IsSuccess={bad.IsSuccess} Status={bad.Status} ErrorMessage={bad.ErrorMessage}");
        }
    }
}
