// QalqanReceiveInfo / References_Operations — постраничное получение справочника.
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

namespace Qalqan.ReceiveInfo.Examples.References_Operations
{
    // =====================================================================================
    // ЗАПРОС: <data xsi:type="q1:IntegraRequest"> с RequestType = References_Operations
    // =====================================================================================

    [XmlRoot("data", Namespace = "")]
    public class IntegraRequest
    {
        [XmlAttribute("type", Namespace = "http://www.w3.org/2001/XMLSchema-instance")]
        public string XsiType { get; set; } = "q1:IntegraRequest";

        [XmlAttribute("q1", Namespace = "http://www.w3.org/2000/xmlns/")]
        public string Q1Namespace { get; set; } = "http://integrations.gosreestr.kz";

        /// <summary>Тип операции. Имя блока данных ниже строго привязано к нему.</summary>
        public string RequestType { get; set; } = "References_Operations";

        public ReferencesRequest? References_OperationsRequest { get; set; }

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

    /// <summary>Постраничный запрос справочника (References_Services / References_Drugs / References_Diagnosises / References_Operations).</summary>
    public class ReferencesRequest
    {
        /// <summary>Размер страницы (по умолчанию на сервере — 10).</summary>
        public int? PageSize { get; set; }
        public bool ShouldSerializePageSize() => PageSize.HasValue;

        /// <summary>Индекс страницы, начиная с 0.</summary>
        public int? PageIndex { get; set; }
        public bool ShouldSerializePageIndex() => PageIndex.HasValue;

        public ReferencesRequest() { }

        public ReferencesRequest(int pageSize, int pageIndex)
        {
            PageSize = pageSize;
            PageIndex = pageIndex;
        }
    }

    public class ReferencesOperationsResponse : ReferencePageBase
    {
        [XmlArray("Items")]
        [XmlArrayItem("OperationsReferenceItem")]
        public List<OperationsReferenceItem> Items { get; set; } = new List<OperationsReferenceItem>();
    }

    /// <summary>Общие поля страницы справочника.</summary>
    public abstract class ReferencePageBase
    {
        public int PageSize { get; set; }
        /// <summary>Индекс страницы, начиная с 0.</summary>
        public int PageIndex { get; set; }
        public int PagesCount { get; set; }
    }

    /// <summary>Элемент справочника операций МКБ-9.</summary>
    public class OperationsReferenceItem
    {
        public string? Code { get; set; }
        public int QalqanCode { get; set; }
        public string? NameRu { get; set; }
        public string? NameKz { get; set; }
    }

    // =====================================================================================
    // ОТВЕТ: <data xsi:type="q1:IntegraResponse">
    // =====================================================================================

    [XmlRoot("data", Namespace = "")]
    public class IntegraResponse
    {
        public ResponseInfo ResponseInfo { get; set; } = new ResponseInfo();

    public ReferencesOperationsResponse? References_OperationsResponse { get; set; }

    /// <summary>Блок результата (есть только при StatusCode = 200).</summary>
    public ReferencesOperationsResponse? Result => References_OperationsResponse;

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
            References_OperationsRequest = new ReferencesRequest { PageSize = 5, PageIndex = 1 }   // PageIndex с 0
        };

        /// <summary>Пример ответа сервиса (элемент &lt;data&gt; из responseData).</summary>
        public const string ResponseXml = @"<data xmlns:q1=""http://integrations.gosreestr.kz"" xmlns:xsi=""http://www.w3.org/2001/XMLSchema-instance"" xsi:type=""q1:IntegraResponse"">
  <ResponseInfo>
    <RequestType>References_Operations</RequestType>
    <StatusCode>200</StatusCode>
    <Message>OK</Message>
  </ResponseInfo>
  <References_OperationsResponse>
    <Items>
      <OperationsReferenceItem>
        <Code>16.08</Code>
        <QalqanCode>1608</QalqanCode>
        <NameRu>Другие операции на глазнице</NameRu>
        <NameKz>Көз ұясындағы басқа операциялар</NameKz>
      </OperationsReferenceItem>
    </Items>
    <PageSize>5</PageSize>
    <PageIndex>1</PageIndex>
    <PagesCount>735</PagesCount>
  </References_OperationsResponse>
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
                Console.WriteLine($"PageIndex={r.PageIndex} PageSize={r.PageSize} PagesCount={r.PagesCount} Items={r.Items.Count}");
                foreach (var i in r.Items) Console.WriteLine($"  {i.Code}");
            }
        }
    }
}
