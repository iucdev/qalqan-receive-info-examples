// QalqanReceiveInfo / Screenings_Complete — завершение скрининга.
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

namespace Qalqan.ReceiveInfo.Examples.Screenings_Complete
{
    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"> с RequestType = Screenings_Complete
    // =====================================================================================

    [XmlRoot("data", Namespace = "")]
    public class IntegraRequest
    {
        [XmlAttribute("type", Namespace = "http://www.w3.org/2001/XMLSchema-instance")]
        public string XsiType { get; set; } = "q1:IntegraRequest";

        [XmlAttribute("q1", Namespace = "http://www.w3.org/2000/xmlns/")]
        public string Q1Namespace { get; set; } = "http://integrations.gosreestr.kz";

        /// <summary>Тип операции. Имя блока данных ниже строго привязано к нему.</summary>
        public string RequestType { get; set; } = "Screenings_Complete";

        public CompleteScreeningRequest? Screenings_CompleteRequest { get; set; }

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

    /// <summary>Завершение скрининга (Screenings_Complete).</summary>
    public class CompleteScreeningRequest
    {
        /// <summary>Идентификатор скрининга (обязателен).</summary>
        public string ScreeningId { get; set; } = string.Empty;

        /// <summary>Целевая группа.</summary>
        public int? ScreeningGroupId { get; set; }
        public bool ShouldSerializeScreeningGroupId() => ScreeningGroupId.HasValue;

        /// <summary>Заключительный диагноз (МКБ-10).</summary>
        public string? DiagnosisCode { get; set; }

        /// <summary>Причина завершения (1..8, см. DictionaryCodes.ScreeningCompletionReason).</summary>
        public int? CompletionReason { get; set; }
        public bool ShouldSerializeCompletionReason() => CompletionReason.HasValue;

        /// <summary>Дата начала скрининга.</summary>
        [XmlIgnore]
        public DateTimeOffset? StartedAt { get; set; }

        [XmlElement("StartedAt")]
        public string? StartedAtXml
        {
            get => XmlDateFormats.FormatDateTime(StartedAt);
            set => StartedAt = XmlDateFormats.ParseNullableDateTime(value);
        }

        /// <summary>Дата завершения скрининга.</summary>
        [XmlIgnore]
        public DateTimeOffset? CompletedAt { get; set; }

        [XmlElement("CompletedAt")]
        public string? CompletedAtXml
        {
            get => XmlDateFormats.FormatDateTime(CompletedAt);
            set => CompletedAt = XmlDateFormats.ParseNullableDateTime(value);
        }
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

    // =====================================================================================
    // ОТВЕТ: <data xsi:type="q1:IntegraResponse">
    // =====================================================================================

    [XmlRoot("data", Namespace = "")]
    public class IntegraResponse
    {
        public ResponseInfo ResponseInfo { get; set; } = new ResponseInfo();

    // У этой операции тела ответа нет — только ResponseInfo.

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
            Screenings_CompleteRequest = new CompleteScreeningRequest
            {
                ScreeningId = "SCR-2026-001",
                ScreeningGroupId = 1,
                DiagnosisCode = "J06.9",
                CompletionReason = 1,                                            // завершение скрининга
                StartedAt = new DateTimeOffset(2026, 4, 10, 9, 0, 0, TimeSpan.Zero),
                CompletedAt = new DateTimeOffset(2026, 4, 10, 10, 30, 0, TimeSpan.Zero)
            }
        };

        /// <summary>Пример ответа сервиса (элемент &lt;data&gt; из responseData).</summary>
        public const string ResponseXml = @"<data xmlns:q1=""http://integrations.gosreestr.kz"" xmlns:xsi=""http://www.w3.org/2001/XMLSchema-instance"" xsi:type=""q1:IntegraResponse"">
  <ResponseInfo>
    <RequestType>Screenings_Complete</RequestType>
    <StatusCode>200</StatusCode>
    <Message>OK</Message>
  </ResponseInfo>
</data>";

        public static IntegraResponse Response() => IntegraResponse.Parse(ResponseXml);

        public static void Run()
        {
            Console.WriteLine("----- request <data> -----");
            Console.WriteLine(Request().ToXml());
            var response = Response();
            Console.WriteLine("----- response -----");
            Console.WriteLine($"RequestType={response.ResponseInfo.RequestType} StatusCode={response.ResponseInfo.StatusCode} Message={response.ResponseInfo.Message}");

        }
    }
}
