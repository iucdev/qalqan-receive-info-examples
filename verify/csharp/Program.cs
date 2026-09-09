using System.Reflection;

// Запускает Example.Run() всех примеров. Режим "xml" печатает только request <data> с маркерами (для сборки *.xml).
var mode = args.Length > 0 ? args[0] : "run";
var examples = Assembly.GetExecutingAssembly().GetTypes()
    .Where(t => t.IsClass && t.IsAbstract && t.IsSealed && t.Name == "Example")
    .OrderBy(t => t.FullName)
    .ToList();

var failed = 0;
foreach (var type in examples)
{
    var endpoint = type.Namespace!.Split('.').Last();
    try
    {
        if (mode == "xml")
        {
            var request = type.GetMethod("Request", BindingFlags.Public | BindingFlags.Static)!.Invoke(null, null)!;
            var xml = (string)request.GetType().GetMethod("ToXml")!.Invoke(request, null)!;
            Console.WriteLine($"@@BEGIN {endpoint}");
            Console.WriteLine(xml);
            Console.WriteLine("@@END");
        }
        else
        {
            Console.WriteLine($"===== {type.Namespace} =====");
            type.GetMethod("Run", BindingFlags.Public | BindingFlags.Static)!.Invoke(null, null);
            Console.WriteLine();
        }
    }
    catch (Exception ex)
    {
        failed++;
        Console.Error.WriteLine($"FAILED {type.FullName}: {(ex as TargetInvocationException)?.InnerException ?? ex}");
    }
}
Console.Error.WriteLine($"examples: {examples.Count}, failed: {failed}");
return failed == 0 ? 0 : 1;
