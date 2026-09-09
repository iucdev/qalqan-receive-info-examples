// QalqanReceiveInfo / Screenings_Status — статус скрининга.
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

namespace Qalqan.ReceiveInfo.Examples.Screenings_Status
{
    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"> с RequestType = Screenings_Status
    // =====================================================================================

    [XmlRoot("data", Namespace = "")]
    public class IntegraRequest
    {
        [XmlAttribute("type", Namespace = "http://www.w3.org/2001/XMLSchema-instance")]
        public string XsiType { get; set; } = "q1:IntegraRequest";

        [XmlAttribute("q1", Namespace = "http://www.w3.org/2000/xmlns/")]
        public string Q1Namespace { get; set; } = "http://integrations.gosreestr.kz";

        /// <summary>Тип операции. Имя блока данных ниже строго привязано к нему.</summary>
        public string RequestType { get; set; } = "Screenings_Status";

        public ScreeningsStatusRequest? Screenings_StatusRequest { get; set; }

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

    /// <summary>Запрос статуса скрининга (Screenings_Status).</summary>
    public class ScreeningsStatusRequest
    {
        public string ScreeningId { get; set; } = string.Empty;

        public ScreeningsStatusRequest() { }

        public ScreeningsStatusRequest(string screeningId)
        {
            ScreeningId = screeningId;
        }
    }

    /// <summary>Статус скрининга.</summary>
    public class ScreeningStatusResponse
    {
        public int SystemId { get; set; }
        public string? ScreeningId { get; set; }
        public bool Completed { get; set; }

        [XmlIgnore]
        public DateTimeOffset? CompletedAt { get; set; }

        [XmlElement("CompletedAt", IsNullable = true)]
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

    public ScreeningStatusResponse? Screenings_StatusResponse { get; set; }

    /// <summary>Блок результата (есть только при StatusCode = 200).</summary>
    public ScreeningStatusResponse? Result => Screenings_StatusResponse;

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
            Screenings_StatusRequest = new ScreeningsStatusRequest { ScreeningId = "SCR-2026-001" }
        };

        /// <summary>Пример ответа сервиса (элемент &lt;data&gt; из responseData).</summary>
        public const string ResponseXml = @"<data xmlns:q1=""http://integrations.gosreestr.kz"" xmlns:xsi=""http://www.w3.org/2001/XMLSchema-instance"" xsi:type=""q1:IntegraResponse"">
  <ResponseInfo>
    <RequestType>Screenings_Status</RequestType>
    <StatusCode>200</StatusCode>
    <Message>OK</Message>
  </ResponseInfo>
  <Screenings_StatusResponse>
    <SystemId>10</SystemId>
    <ScreeningId>SCR-2026-001</ScreeningId>
    <Completed>true</Completed>
    <CompletedAt>2026-04-10T10:30:00Z</CompletedAt>
  </Screenings_StatusResponse>
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
            if (r != null) Console.WriteLine($"ScreeningId={r.ScreeningId} Completed={r.Completed} CompletedAt={r.CompletedAt}");
        }
    }
}
