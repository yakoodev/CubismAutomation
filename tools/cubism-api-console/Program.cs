using System.Text;

var builder = WebApplication.CreateBuilder(args);
var logFilePath = builder.Configuration["CubismApi:LogFilePath"];
if (string.IsNullOrWhiteSpace(logFilePath))
{
    logFilePath = Path.Combine(builder.Environment.ContentRootPath, "logs", "api-calls.log");
}
var callLogger = new ApiCallLogger(logFilePath);

var app = builder.Build();
var proxy = new CubismProxy(
    builder.Configuration["CubismApi:BaseUrl"] ?? "http://127.0.0.1:18080",
    builder.Configuration["CubismApi:Token"] ?? ""
);
var catalog = ApiCatalog.Default();

app.MapGet("/", () => Results.Content(Html.Page, "text/html; charset=utf-8"));
app.MapGet("/api/catalog", () => Results.Json(catalog));

app.MapPost("/api/call", async (ProxyRequest request) =>
{
    if (string.IsNullOrWhiteSpace(request.Path))
    {
        return Results.BadRequest(new { ok = false, error = "path_required" });
    }

    var startedAt = DateTimeOffset.UtcNow;
    try
    {
        var response = await proxy.CallAsync(request.Method, request.Path, request.Body, request.TokenOverride);
        var callId = callLogger.LogSuccess(startedAt, request, response.StatusCode, response.Content);
        return Results.Json(new
        {
            ok = true,
            call_id = callId,
            log_file = callLogger.LogFilePath,
            request = new { request.Method, request.Path, request.Body, request.TokenOverride },
            response = new { response.StatusCode, response.Content }
        });
    }
    catch (Exception ex)
    {
        var callId = callLogger.LogError(startedAt, request, ex);
        return Results.Json(new
        {
            ok = false,
            call_id = callId,
            log_file = callLogger.LogFilePath,
            error = "proxy_call_failed",
            message = ex.Message,
            request = new { request.Method, request.Path, request.Body, request.TokenOverride }
        }, statusCode: 502);
    }
});

app.Run("http://127.0.0.1:51888");

record ProxyRequest(string Method, string Path, string? Body, string? TokenOverride);

sealed class CubismProxy(string baseUrl, string token)
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(20) };
    private readonly string _baseUrl = baseUrl.TrimEnd('/');
    private readonly string _token = token.Trim();

    public async Task<(int StatusCode, string Content)> CallAsync(string? method, string path, string? body, string? tokenOverride)
    {
        var verb = string.IsNullOrWhiteSpace(method) ? "GET" : method.Trim().ToUpperInvariant();
        using var req = new HttpRequestMessage(new HttpMethod(verb), _baseUrl + path);

        var effectiveToken = string.IsNullOrWhiteSpace(tokenOverride) ? _token : tokenOverride.Trim();
        if (!string.IsNullOrEmpty(effectiveToken))
        {
            req.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", effectiveToken);
        }

        if (verb != "GET" && body is not null)
        {
            req.Content = new StringContent(body, Encoding.UTF8, "application/json");
        }

        using var res = await _http.SendAsync(req);
        var text = await res.Content.ReadAsStringAsync();
        return ((int)res.StatusCode, text);
    }
}

sealed class ApiCallLogger
{
    private readonly string _logPath;
    private readonly object _sync = new();
    private long _seq;

    public ApiCallLogger(string logPath)
    {
        _logPath = Path.GetFullPath(logPath);
        var dir = Path.GetDirectoryName(_logPath);
        if (!string.IsNullOrWhiteSpace(dir))
        {
            Directory.CreateDirectory(dir);
        }
    }

    public string LogFilePath => _logPath;

    public string LogSuccess(DateTimeOffset startedAt, ProxyRequest request, int statusCode, string responseBody)
    {
        var callId = NextCallId(startedAt);
        var durationMs = (long)(DateTimeOffset.UtcNow - startedAt).TotalMilliseconds;
        WriteBlock(callId, [
            $"ts_utc: {DateTimeOffset.UtcNow:O}",
            $"status: success",
            $"duration_ms: {durationMs}",
            $"request.method: {SafeOneLine(request.Method)}",
            $"request.path: {SafeOneLine(request.Path)}",
            $"request.token_override: {MaskToken(request.TokenOverride)}",
            $"request.body: {SafeBody(request.Body)}",
            $"response.status_code: {statusCode}",
            $"response.body: {SafeBody(responseBody)}"
        ]);
        return callId;
    }

    public string LogError(DateTimeOffset startedAt, ProxyRequest request, Exception ex)
    {
        var callId = NextCallId(startedAt);
        var durationMs = (long)(DateTimeOffset.UtcNow - startedAt).TotalMilliseconds;
        WriteBlock(callId, [
            $"ts_utc: {DateTimeOffset.UtcNow:O}",
            $"status: error",
            $"duration_ms: {durationMs}",
            $"request.method: {SafeOneLine(request.Method)}",
            $"request.path: {SafeOneLine(request.Path)}",
            $"request.token_override: {MaskToken(request.TokenOverride)}",
            $"request.body: {SafeBody(request.Body)}",
            $"error.type: {ex.GetType().FullName}",
            $"error.message: {SafeOneLine(ex.Message)}",
            $"error.stack: {SafeBody(ex.ToString())}"
        ]);
        return callId;
    }

    private string NextCallId(DateTimeOffset startedAt)
    {
        var seq = Interlocked.Increment(ref _seq);
        return $"{startedAt:yyyyMMddHHmmssfff}-{seq:D6}";
    }

    private void WriteBlock(string callId, IEnumerable<string> lines)
    {
        var sb = new StringBuilder();
        sb.AppendLine($"=== call_id: {callId} ===");
        foreach (var line in lines)
        {
            sb.AppendLine(line);
        }
        sb.AppendLine();

        lock (_sync)
        {
            File.AppendAllText(_logPath, sb.ToString(), Encoding.UTF8);
        }
    }

    private static string SafeOneLine(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return "<empty>";
        }
        return value.Replace("\r", "\\r").Replace("\n", "\\n");
    }

    private static string SafeBody(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return "<empty>";
        }
        return value.TrimEnd().Replace("\r", "\\r").Replace("\n", "\\n");
    }

    private static string MaskToken(string? token)
    {
        if (string.IsNullOrWhiteSpace(token))
        {
            return "<empty>";
        }
        var trimmed = token.Trim();
        if (trimmed.Length <= 8)
        {
            return "***";
        }
        return $"{trimmed[..4]}...{trimmed[^4..]}";
    }
}

record ApiCatalog(List<ApiGroup> Groups)
{
    public static ApiCatalog Default()
    {
        return new ApiCatalog([
            new ApiGroup("state", "State and Health", [
                new ApiAction("GET /health", "GET", "/health"),
                new ApiAction("GET /version", "GET", "/version"),
                new ApiAction("GET /state", "GET", "/state"),
                new ApiAction("GET /state/project", "GET", "/state/project"),
                new ApiAction("GET /state/document", "GET", "/state/document"),
                new ApiAction("GET /state/selection", "GET", "/state/selection")
            ]),
            new ApiGroup("startup", "Startup Flow", [
                new ApiAction(
                    "POST /startup/prepare",
                    "POST",
                    "/startup/prepare",
                    """
                    {
                      "license_mode": "free",
                      "create_new_model": true,
                      "wait_timeout_ms": 30000
                    }
                    """
                )
            ]),
            new ApiGroup("commands", "Commands", [
                new ApiAction("zoom_in", "POST", "/command", """{"command":"cubism.zoom_in"}"""),
                new ApiAction("zoom_out", "POST", "/command", """{"command":"cubism.zoom_out"}"""),
                new ApiAction("zoom_reset", "POST", "/command", """{"command":"cubism.zoom_reset"}"""),
                new ApiAction("undo", "POST", "/command", """{"command":"cubism.undo"}"""),
                new ApiAction("redo", "POST", "/command", """{"command":"cubism.redo"}""")
            ]),
            new ApiGroup("mesh_read", "Mesh Read API", [
                new ApiAction("GET /mesh/list", "GET", "/mesh/list"),
                new ApiAction("GET /mesh/active", "GET", "/mesh/active"),
                new ApiAction("GET /mesh/state", "GET", "/mesh/state")
            ]),
            new ApiGroup("mesh_write", "Mesh Write API", [
                new ApiAction("POST /mesh/select (by name)", "POST", "/mesh/select", """{"mesh_name":"MeshName"}"""),
                new ApiAction("POST /mesh/rename", "POST", "/mesh/rename", """{"mesh_name":"MeshName","new_name":"MeshRenamed"}"""),
                new ApiAction("POST /mesh/visibility", "POST", "/mesh/visibility", """{"mesh_name":"MeshName","visible":true}"""),
                new ApiAction("POST /mesh/lock", "POST", "/mesh/lock", """{"mesh_name":"MeshName","locked":true}""")
            ]),
            new ApiGroup("mesh_ops", "Mesh Edit Operations", [
                new ApiAction(
                    "POST /mesh/ops dry-run",
                    "POST",
                    "/mesh/ops",
                    """
                    {
                      "validate_only": true,
                      "operations": [
                        {"op":"auto_mesh"},
                        {"op":"divide"},
                        {"op":"connect"},
                        {"op":"reset_shape"},
                        {"op":"fit_contour"}
                      ]
                    }
                    """
                ),
                new ApiAction(
                    "POST /mesh/ops execute",
                    "POST",
                    "/mesh/ops",
                    """
                    {
                      "validate_only": false,
                      "operations": [
                        {"op":"auto_mesh"},
                        {"op":"divide"},
                        {"op":"connect"},
                        {"op":"reset_shape"},
                        {"op":"fit_contour"}
                      ]
                    }
                    """
                )
            ])
        ]);
    }
}

record ApiGroup(string Id, string Title, List<ApiAction> Actions);
record ApiAction(string Label, string Method, string Path, string? Body = null);

static class Html
{
    public const string Page = """
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Cubism API Console</title>
  <style>
    :root { --bg:#f4f7fb; --card:#ffffff; --ink:#111827; --muted:#475569; --line:#cbd5e1; --accent:#0f766e; --accent-soft:#ccfbf1; --out:#0f172a; --outink:#e2e8f0; }
    * { box-sizing:border-box; }
    body { margin:0; font-family:Consolas, "Liberation Mono", Menlo, monospace; background:radial-gradient(circle at top left,#dbeafe,#f8fafc 42%,#f1f5f9); color:var(--ink); }
    .wrap { max-width:1100px; margin:18px auto; padding:0 14px; }
    .card { background:var(--card); border:1px solid var(--line); border-radius:14px; padding:14px; box-shadow:0 8px 24px rgba(15,23,42,.08); }
    h1 { margin:0; font-size:22px; }
    h2 { margin:16px 0 8px; font-size:16px; }
    .small { color:var(--muted); font-size:12px; margin-top:6px; }
    .grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(260px, 1fr)); gap:10px; }
    .group { border:1px solid var(--line); border-radius:12px; padding:10px; background:#f8fafc; }
    .group-title { margin:0 0 8px; font-size:14px; color:#0f172a; }
    .row { display:flex; gap:8px; flex-wrap:wrap; align-items:center; margin:8px 0; }
    button { border:1px solid #5eead4; background:var(--accent-soft); color:#134e4a; border-radius:8px; padding:7px 10px; cursor:pointer; font:inherit; }
    button:hover { background:#99f6e4; }
    input, textarea, select { width:100%; border:1px solid var(--line); border-radius:8px; padding:8px; font:inherit; background:#fff; }
    input, select { max-width:340px; }
    textarea { min-height:170px; white-space:pre; }
    pre { background:var(--out); color:var(--outink); border-radius:12px; padding:12px; min-height:220px; overflow:auto; }
  </style>
</head>
<body>
  <div class="wrap">
    <div class="card">
      <h1>Cubism API Console</h1>
      <div class="small">Preset-driven test UI. Add new test actions by editing ApiCatalog.Default() in Program.cs.</div>

      <h2>Connection</h2>
      <div class="row">
        <input id="token" placeholder="Bearer token override (optional)" />
      </div>

      <h2>Presets</h2>
      <div id="presetGroups" class="grid"></div>

      <h2>Custom Request</h2>
      <div class="row">
        <select id="method">
          <option>GET</option>
          <option>POST</option>
          <option>PUT</option>
          <option>PATCH</option>
          <option>DELETE</option>
        </select>
        <input id="path" value="/health" />
      </div>
      <div class="row">
        <textarea id="body"></textarea>
      </div>
      <div class="row">
        <button onclick="customCall()">Send Request</button>
      </div>

      <pre id="out">Ready.</pre>
    </div>
  </div>

  <script>
    let catalog = { groups: [] };

    function setRequestFields(action) {
      document.getElementById('method').value = action.method || 'GET';
      document.getElementById('path').value = action.path || '/health';
      document.getElementById('body').value = action.body || '';
    }

    async function callApi(method, path, body = null) {
      const tokenOverride = document.getElementById('token').value || null;
      const res = await fetch('/api/call', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ method, path, body, tokenOverride })
      });
      const json = await res.json();
      document.getElementById('out').textContent = JSON.stringify(json, null, 2);
    }

    async function runPreset(groupIndex, actionIndex) {
      const action = catalog.groups[groupIndex].actions[actionIndex];
      setRequestFields(action);
      const method = (action.method || 'GET').toUpperCase();
      const body = method === 'GET' ? null : (action.body || '');
      await callApi(method, action.path, body);
    }

    async function customCall() {
      const method = document.getElementById('method').value.toUpperCase();
      const path = document.getElementById('path').value;
      const bodyText = document.getElementById('body').value;
      const body = method === 'GET' ? null : bodyText;
      await callApi(method, path, body);
    }

    function renderCatalog() {
      const root = document.getElementById('presetGroups');
      root.innerHTML = '';
      for (let g = 0; g < catalog.groups.length; g++) {
        const group = catalog.groups[g];
        const card = document.createElement('div');
        card.className = 'group';
        const title = document.createElement('h3');
        title.className = 'group-title';
        title.textContent = group.title;
        card.appendChild(title);

        const row = document.createElement('div');
        row.className = 'row';
        for (let a = 0; a < group.actions.length; a++) {
          const action = group.actions[a];
          const btn = document.createElement('button');
          btn.textContent = action.label;
          btn.onclick = () => runPreset(g, a);
          row.appendChild(btn);
        }
        card.appendChild(row);
        root.appendChild(card);
      }
    }

    async function init() {
      const res = await fetch('/api/catalog');
      catalog = await res.json();
      renderCatalog();
    }

    init();
  </script>
</body>
</html>
""";
}
