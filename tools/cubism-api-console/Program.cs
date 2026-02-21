using System.Text;

var builder = WebApplication.CreateBuilder(args);

var app = builder.Build();
var proxy = new CubismProxy(
    builder.Configuration["CubismApi:BaseUrl"] ?? "http://127.0.0.1:18080",
    builder.Configuration["CubismApi:Token"] ?? ""
);

app.MapGet("/", () => Results.Content(Html.Page, "text/html; charset=utf-8"));

app.MapPost("/api/call", async (ProxyRequest request) =>
{
    if (string.IsNullOrWhiteSpace(request.Path))
    {
        return Results.BadRequest(new { ok = false, error = "path_required" });
    }

    var response = await proxy.CallAsync(request.Method, request.Path, request.Body);
    return Results.Json(new
    {
        ok = true,
        request = new { request.Method, request.Path, request.Body },
        response = new { response.StatusCode, response.Content }
    });
});

app.Run("http://127.0.0.1:51888");

record ProxyRequest(string Method, string Path, string? Body);

sealed class CubismProxy(string baseUrl, string token)
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(10) };
    private readonly string _baseUrl = baseUrl.TrimEnd('/');
    private readonly string _token = token.Trim();

    public async Task<(int StatusCode, string Content)> CallAsync(string? method, string path, string? body)
    {
        var verb = string.IsNullOrWhiteSpace(method) ? "GET" : method.Trim().ToUpperInvariant();
        using var req = new HttpRequestMessage(new HttpMethod(verb), _baseUrl + path);

        if (!string.IsNullOrEmpty(_token))
        {
            req.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", _token);
        }

        if (verb != "GET" && body != null)
        {
            req.Content = new StringContent(body, Encoding.UTF8, "application/json");
        }

        using var res = await _http.SendAsync(req);
        var text = await res.Content.ReadAsStringAsync();
        return ((int)res.StatusCode, text);
    }
}

static class Html
{
    public const string Page = """
<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Cubism API Console</title>
  <style>
    :root { --bg:#f2f4f7; --card:#fff; --ink:#101828; --muted:#475467; --line:#d0d5dd; --blue:#175cd3; }
    body { margin:0; font-family:Segoe UI, sans-serif; background:linear-gradient(180deg,#eef4ff,#f8fafc); color:var(--ink); }
    .wrap { max-width:980px; margin:24px auto; padding:0 16px; }
    .card { background:var(--card); border:1px solid var(--line); border-radius:12px; padding:16px; box-shadow:0 2px 10px rgba(16,24,40,.06); }
    h1 { margin:0 0 12px; font-size:24px; }
    .row { display:flex; gap:8px; flex-wrap:wrap; margin:8px 0; }
    button { border:1px solid #b2ccff; background:#e8f1ff; color:#0b4aba; border-radius:8px; padding:8px 12px; cursor:pointer; }
    button:hover { background:#dbe8ff; }
    input,textarea { width:100%; border:1px solid var(--line); border-radius:8px; padding:8px; font:inherit; }
    textarea { min-height:90px; }
    .small { color:var(--muted); font-size:13px; margin:6px 0 0; }
    pre { background:#0f172a; color:#e2e8f0; border-radius:10px; padding:12px; min-height:220px; overflow:auto; }
  </style>
</head>
<body>
  <div class="wrap">
    <div class="card">
      <h1>Cubism API Console</h1>
      <div class="small">UI для ручной проверки endpoints и команд.</div>

      <div class="row">
        <button onclick="callApi('GET','/health')">GET /health</button>
        <button onclick="callApi('GET','/version')">GET /version</button>
        <button onclick="callApi('GET','/state')">GET /state</button>
        <button onclick="callApi('GET','/state/project')">GET /state/project</button>
        <button onclick="callApi('GET','/state/document')">GET /state/document</button>
        <button onclick="callApi('GET','/state/selection')">GET /state/selection</button>
      </div>

      <div class="row">
        <button onclick="sendCmd('cubism.zoom_in')">zoom_in</button>
        <button onclick="sendCmd('cubism.zoom_out')">zoom_out</button>
        <button onclick="sendCmd('cubism.zoom_reset')">zoom_reset</button>
        <button onclick="sendCmd('cubism.undo')">undo</button>
        <button onclick="sendCmd('cubism.redo')">redo</button>
      </div>

      <div class="row">
        <input id="path" value="/command" />
      </div>
      <div class="row">
        <textarea id="body">{ "command": "cubism.zoom_in" }</textarea>
      </div>
      <div class="row">
        <button onclick="customPost()">POST custom</button>
      </div>

      <pre id="out">Ready.</pre>
    </div>
  </div>

  <script>
    async function callApi(method, path, body=null) {
      const res = await fetch('/api/call', {
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body: JSON.stringify({ method, path, body })
      });
      const json = await res.json();
      document.getElementById('out').textContent = JSON.stringify(json, null, 2);
    }
    function sendCmd(cmd){
      callApi('POST','/command', JSON.stringify({ command: cmd }));
    }
    function customPost(){
      callApi('POST', document.getElementById('path').value, document.getElementById('body').value);
    }
  </script>
</body>
</html>
""";
}
