# Qalqan — примеры запросов и ответов ШЭП-сервисов (C#, Java, XML)

Примеры бизнес-нагрузки `<data>` для ШЭП-сервисов информационной системы Qalqan (ОСМС). Для каждого эндпоинта —
папка с тремя самодостаточными файлами:

| Файл | Содержимое |
|---|---|
| `<Endpoint>.cs` | DTO запроса и ответа (`XmlSerializer`), заполненный пример запроса, пример XML ответа и его разбор |
| `<Endpoint>.java` | То же на Java 11+ без внешних зависимостей (DOM) |
| `<Endpoint>.xml` | Готовый XML: элемент `<data>` запроса (в `requestData`) и элемент `<data>` ответа (в `responseData`) |

Транспорт ШЭП (конверт `SendMessage`, `requestInfo`, транспортная подпись, адрес приёма) в примерах не рассматривается:
файлы описывают только то, что помещается внутрь `requestData` и приходит внутри `responseData`.

## Как использовать

Это не библиотека и не NuGet/Maven-пакет — каждый файл самодостаточен и копируется в проект как есть.

1. Откройте папку нужного сервиса и эндпоинта, например `QalqanReceiveInfo/Services_LoadOrUpdateServices/`.
2. Скопируйте в свой проект файл `.cs` или `.java`. Внешних зависимостей нет: Java — только JDK 11+, C# — `System.Xml.Serialization`
   (для `QalqanReceiveInfoFromMIS` дополнительно `System.Text.Json`, входит в .NET 6+).
3. Заполните DTO своими данными по образцу `Example.Request()` (`request()` в Java) и вызовите `ToXml()` (`toDataXml()`).
   Результат — элемент `<data>`; вставьте его целиком в `requestData` вашего ШЭП-конверта `SendMessage`.
   Конверт, `requestInfo`, транспортную подпись и отправку выполняет ваш ШЭП-клиент.
4. Из ответа ШЭП возьмите элемент `<data>` внутри `responseData` и передайте в `IntegraResponse.Parse()` (`parse()`):
   дальше доступны `ResponseInfo.StatusCode`, счётчики `Total` / `Inserted` / `Updated` / `Unchanged` и список `Errors` по записям
   (для справочников — страница `Items`, для скрининга — статус).
5. Файл `.xml` в той же папке показывает готовый результат шагов 3–4: что именно должно лежать в `requestData`
   и что придёт в `responseData`. Его можно использовать как эталон при отладке без кода.

## QalqanReceiveInfo

Паспорт сервиса: <https://sb.egov.kz/services/passport/ORGAM-S-1170>.
Один сервис, операция определяется полем `RequestType` внутри `<data xsi:type="q1:IntegraRequest">`; рядом лежит блок
данных с именем `<RequestType>Request` (исключение: `DayHospital_LoadOrUpdateRequest`, без «Cases»).

| Эндпоинт | Назначение |
|---|---|
| [DayHospital_LoadCases](QalqanReceiveInfo/DayHospital_LoadCases/) | Случаи дневного стационара (только новые) |
| [DayHospital_LoadOrUpdateCases](QalqanReceiveInfo/DayHospital_LoadOrUpdateCases/) | Случаи дневного стационара (вставка/обновление) |
| [Inpatient_LoadCases](QalqanReceiveInfo/Inpatient_LoadCases/) | Случаи круглосуточного стационара |
| [Inpatient_LoadOrUpdateCases](QalqanReceiveInfo/Inpatient_LoadOrUpdateCases/) | То же, вставка/обновление |
| [Reception_LoadCases](QalqanReceiveInfo/Reception_LoadCases/) | Обращения приёмного покоя |
| [Reception_LoadOrUpdateCases](QalqanReceiveInfo/Reception_LoadOrUpdateCases/) | То же, вставка/обновление |
| [Services_LoadServices](QalqanReceiveInfo/Services_LoadServices/) | Медицинские услуги (только новые) |
| [Services_LoadOrUpdateServices](QalqanReceiveInfo/Services_LoadOrUpdateServices/) | То же, вставка/обновление |
| [References_Services](QalqanReceiveInfo/References_Services/) | Справочник услуг (тарификатор), постранично |
| [References_Drugs](QalqanReceiveInfo/References_Drugs/) | Справочник лекарственных средств |
| [References_Diagnosises](QalqanReceiveInfo/References_Diagnosises/) | Справочник диагнозов МКБ-10 |
| [References_Operations](QalqanReceiveInfo/References_Operations/) | Справочник операций МКБ-9 |
| [Screenings_Complete](QalqanReceiveInfo/Screenings_Complete/) | Завершение скрининга (ответ без тела результата) |
| [Screenings_Status](QalqanReceiveInfo/Screenings_Status/) | Статус скрининга |
| [AmbulanceCard_LoadCards](QalqanReceiveInfo/AmbulanceCard_LoadCards/) | Карты вызова скорой помощи: актив / госпитализация (только новые) |
| [AmbulanceCard_LoadOrUpdateCards](QalqanReceiveInfo/AmbulanceCard_LoadOrUpdateCards/) | То же, вставка/обновление по `ExternalId` |

`Load*` — только вставка новых записей, `LoadOrUpdate*` — вставка или обновление по идентификатору записи МИС
(`CaseId` / `AdmissionId` / `ServiceId` / `ExternalId`). Карта вызова СП передаётся полным снимком: при обновлении
непереданные поля обнуляются, `MedicalSupplies` заменяются целиком; версия с более старым `ReceiveDate` игнорируется
(ошибка `StaleVersion`), карта старше срока блокировки по `CallTime` не обновляется (`PeriodClosed`).

Правила формата (совпадают с .NET `XmlSerializer` на стороне сервиса): даты `yyyy-MM-dd`; дата/время ISO 8601 со
смещением (`2026-04-03T10:30:00+05:00`); перечисления **числовыми кодами** (`<Sex>1</Sex>`, `<BgFinanceSource>1</BgFinanceSource>`);
списки — обёртка плюс элементы с именем типа (`<Services><InpatientServiceItemRequest>…`), списки строк — `<string>`.
Значения кодов приведены в комментариях к перечислениям внутри файлов.

Ответ: `<data xsi:type="q1:IntegraResponse">` с `ResponseInfo` (`RequestType`, `StatusCode`, `Message`) и блоком
`<RequestType>Response` при успехе. `StatusCode`: 200 — успех; 400 — ошибка запроса (неизвестный тип, нет модели, валидация);
500 — внутренняя ошибка; 501 — не реализовано / отправитель не сопоставлен с МИС. Ошибки по отдельным записям пакета
не делают ответ неуспешным: они перечислены в `Errors` с `RecIndex`, идентификатором записи, `Code`, `Message`.

## QalqanReceiveInfoFromMIS

Паспорт сервиса: <https://sb.egov.kz/services/passport/ORGAM-S-9847>.

| Эндпоинт | Назначение |
|---|---|
| [SendEvents](QalqanReceiveInfoFromMIS/SendEvents/) | Пакет событий МИС: `<ZippedEventsInBase64>` = Base64(gzip(JSON `{ "events": [...] }`)) |

Событие: `entityType` (числовой код типа сущности, перечисление в файлах), `timestamp` (unix-время в секундах),
`versionHash` (hex, вычисляет МИС; при несовпадении ответ `Status = InvalidHash`), `moId`, `organizationBin`, `misBin`,
`data` — JSON тела события в виде строки (`eventId`, `eventType`, `misId`, `organizationId`, `organizationBin`,
`patientInformation` и поля конкретного типа события). Ответ: `IsSuccess`, `ErrorMessage`, `Status` (`Success` | `InvalidHash` | `Error`).

Все идентификаторы в примерах вымышленные (БИН `990140000001`, `OrgSurId 100001`, ИИН вида `9001013000xx`).

## Лицензия

MIT — см. [LICENSE](LICENSE).
