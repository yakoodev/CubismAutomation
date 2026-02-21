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
app.MapGet("/api/screenshot/current", async (HttpContext ctx) =>
{
    var query = ctx.Request.QueryString.HasValue ? ctx.Request.QueryString.Value : "";
    var startedAt = DateTimeOffset.UtcNow;
    try
    {
        var response = await proxy.GetBinaryAsync("/screenshot/current" + query, null);
        var callId = callLogger.LogSuccess(
            startedAt,
            new ProxyRequest("GET", "/screenshot/current" + query, null, null),
            response.StatusCode,
            $"<binary {response.Bytes.Length} bytes; {response.ContentType}>"
        );
        ctx.Response.Headers["X-Call-Id"] = callId;
        if (response.StatusCode >= 200 && response.StatusCode < 300 && response.ContentType.StartsWith("image/", StringComparison.OrdinalIgnoreCase))
        {
            return Results.File(response.Bytes, response.ContentType);
        }
        var text = Encoding.UTF8.GetString(response.Bytes);
        return Results.Content(text, response.ContentType, Encoding.UTF8, response.StatusCode);
    }
    catch (Exception ex)
    {
        var callId = callLogger.LogError(startedAt, new ProxyRequest("GET", "/screenshot/current" + query, null, null), ex);
        ctx.Response.Headers["X-Call-Id"] = callId;
        return Results.Json(new { ok = false, call_id = callId, error = "screenshot_proxy_failed", message = ex.Message }, statusCode: 502);
    }
});

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

    public async Task<(int StatusCode, string ContentType, byte[] Bytes)> GetBinaryAsync(string path, string? tokenOverride)
    {
        using var req = new HttpRequestMessage(HttpMethod.Get, _baseUrl + path);
        var effectiveToken = string.IsNullOrWhiteSpace(tokenOverride) ? _token : tokenOverride.Trim();
        if (!string.IsNullOrEmpty(effectiveToken))
        {
            req.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", effectiveToken);
        }

        using var res = await _http.SendAsync(req);
        var bytes = await res.Content.ReadAsByteArrayAsync();
        var contentType = res.Content.Headers.ContentType?.ToString() ?? "application/octet-stream";
        return ((int)res.StatusCode, contentType, bytes);
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
                new ApiAction("GET /metrics", "GET", "/metrics"),
                new ApiAction("GET /state", "GET", "/state"),
                new ApiAction("GET /state/project", "GET", "/state/project"),
                new ApiAction("GET /state/document", "GET", "/state/document"),
                new ApiAction("GET /state/selection", "GET", "/state/selection"),
                new ApiAction("GET /state/ui", "GET", "/state/ui")
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
                new ApiAction("POST /mesh/select (by id)", "POST", "/mesh/select", """{"mesh_id":"ArtMesh78"}"""),
                new ApiAction("POST /mesh/rename", "POST", "/mesh/rename", """{"mesh_name":"MeshName","new_name":"MeshRenamed"}"""),
                new ApiAction("POST /mesh/visibility", "POST", "/mesh/visibility", """{"mesh_name":"MeshName","visible":true}"""),
                new ApiAction("POST /mesh/lock", "POST", "/mesh/lock", """{"mesh_name":"MeshName","locked":true}""")
            ]),
            new ApiGroup("mesh_points", "Mesh Points API", [
                new ApiAction("GET /mesh/points", "GET", "/mesh/points?mesh_id=ArtMesh78"),
                new ApiAction(
                    "POST /mesh/points",
                    "POST",
                    "/mesh/points",
                    """
                    {
                      "mesh_id": "ArtMesh78",
                      "points": [
                        {"x": 0.0, "y": 0.0},
                        {"x": 12.0, "y": 0.0},
                        {"x": 12.0, "y": 12.0},
                        {"x": 0.0, "y": 12.0}
                      ]
                    }
                    """
                )
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
            ]),
            new ApiGroup("mesh_capture", "Mesh Auto/Capture API", [
                new ApiAction("POST /mesh/auto_generate (dry-run)", "POST", "/mesh/auto_generate", """{"mesh_id":"ArtMesh78","validate_only":true}"""),
                new ApiAction("POST /mesh/auto_generate (execute)", "POST", "/mesh/auto_generate", """{"mesh_id":"ArtMesh78","validate_only":false}"""),
                new ApiAction("GET /mesh/screenshot", "GET", "/mesh/screenshot?mesh_id=ArtMesh78")
            ]),
            new ApiGroup("parameters", "Parameter API", [
                new ApiAction("GET /parameters", "GET", "/parameters"),
                new ApiAction("GET /parameters/state", "GET", "/parameters/state"),
                new ApiAction("POST /parameters/set", "POST", "/parameters/set", """{"id":"ParamAngleX","value":10.0}"""),
                new ApiAction(
                    "POST /parameters/set (batch)",
                    "POST",
                    "/parameters/set",
                    """
                    {
                      "updates": [
                        {"id":"ParamAngleX","value":5.0},
                        {"id":"ParamAngleY","value":-5.0}
                      ]
                    }
                    """
                )
            ]),
            new ApiGroup("deformers", "Deformer API", [
                new ApiAction("GET /deformers", "GET", "/deformers"),
                new ApiAction("GET /deformers/state", "GET", "/deformers/state"),
                new ApiAction("POST /deformers/select", "POST", "/deformers/select", """{"deformer_id":"WarpDeformer1"}"""),
                new ApiAction("POST /deformers/rename", "POST", "/deformers/rename", """{"deformer_id":"WarpDeformer1","new_name":"WarpDeformer1_Renamed"}""")
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
    .mesh-editor { min-height:200px; }
    .label { font-size:12px; color:var(--muted); min-width:88px; }
    .pill { display:inline-block; border:1px solid var(--line); border-radius:999px; padding:2px 8px; font-size:12px; color:#0f172a; background:#f8fafc; }
    .shot { max-width:100%; border:1px solid var(--line); border-radius:10px; }
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
      <div class="row">
        <span id="lastCallMeta" class="pill">call: -</span>
      </div>

      <h2>Mesh Lab</h2>
      <div class="group">
        <div class="row">
          <button onclick="refreshMeshList()">Refresh Mesh List</button>
          <button onclick="loadActiveMesh()">Load Active Mesh</button>
          <span id="meshStats" class="pill">meshes: -</span>
        </div>

        <div class="row">
          <span class="label">Mesh</span>
          <select id="meshSelect"></select>
          <input id="meshIdManual" placeholder="Or enter mesh_id manually (e.g. ArtMesh78)" />
          <label class="pill"><input id="useActiveMesh" type="checkbox" checked /> use active mesh for ops</label>
        </div>

        <div class="row">
          <button onclick="meshSelectById()">Select Mesh</button>
          <button onclick="meshGetPoints()">Get Points</button>
          <button onclick="meshRevertPoints()">Revert Points</button>
          <button onclick="meshSetActiveFromSelection()">Set Active From Selection</button>
        </div>

        <div class="row">
          <span class="label">Point Edit</span>
          <input id="pointIndex" type="number" value="0" />
          <input id="pointDx" type="number" value="1" step="0.1" />
          <input id="pointDy" type="number" value="0" step="0.1" />
          <button onclick="meshNudgePoint()">Nudge Point</button>
        </div>

        <div class="row">
          <button onclick="meshApplyPointsFromEditor()">Apply Points JSON</button>
          <button onclick="meshAutoGenerate(true)">Auto Mesh Dry-Run</button>
          <button onclick="meshAutoGenerate(false)">Auto Mesh Execute</button>
          <button onclick="meshScreenshot()">Current Screenshot</button>
          <label class="pill"><input id="workspaceOnly" type="checkbox" checked /> workspace only</label>
        </div>

        <div class="row">
          <textarea id="meshPointsEditor" class="mesh-editor" placeholder='Points JSON. Expected: [{"x":123.0,"y":456.0}, ...]'></textarea>
        </div>
        <div class="row">
          <a id="shotLink" href="#" target="_blank">screenshot link</a>
        </div>
        <div class="row">
          <img id="shotPreview" class="shot" alt="screenshot preview" />
        </div>
        <div class="small" id="meshInfo">No mesh data loaded yet.</div>
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
    let meshList = [];
    let loadedPoints = [];
    let originalPointsByMeshId = {};
    let currentMeshId = '';
    let loadedMeshId = '';

    function setRequestFields(action) {
      document.getElementById('method').value = action.method || 'GET';
      document.getElementById('path').value = action.path || '/health';
      document.getElementById('body').value = action.body || '';
    }

    function selectedMeshId() {
      const manual = (document.getElementById('meshIdManual').value || '').trim();
      if (manual) return manual;
      const selected = (document.getElementById('meshSelect').value || '').trim();
      if (selected) return selected;
      return currentMeshId || '';
    }

    async function getActiveMeshId() {
      const result = await callApi('GET', '/mesh/active', null);
      const payload = parseProxyPayload(result);
      const active = payload && payload.active_mesh ? payload.active_mesh : null;
      if (active && active.id) {
        currentMeshId = active.id;
        document.getElementById('meshIdManual').value = active.id;
        const select = document.getElementById('meshSelect');
        if (select) {
          select.value = active.id;
        }
        return active.id;
      }
      return '';
    }

    async function resolveMeshIdForOps() {
      const useActive = !!document.getElementById('useActiveMesh').checked;
      if (!useActive) {
        if (loadedMeshId) return loadedMeshId;
        return selectedMeshId();
      }
      return await getActiveMeshId();
    }

    function setMeshInfo(text) {
      document.getElementById('meshInfo').textContent = text;
    }

    function tryParseJson(text) {
      try {
        return JSON.parse(text);
      } catch {
        return null;
      }
    }

    function setLastCallMeta(result) {
      const callId = result && result.call_id ? result.call_id : '-';
      const file = result && result.log_file ? result.log_file : '-';
      document.getElementById('lastCallMeta').textContent = `call: ${callId} | log: ${file}`;
    }

    async function callApi(method, path, body = null) {
      const tokenOverride = document.getElementById('token').value || null;
      const res = await fetch('/api/call', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ method, path, body, tokenOverride })
      });
      const json = await res.json();
      setLastCallMeta(json);
      document.getElementById('out').textContent = JSON.stringify(json, null, 2);
      return json;
    }

    function parseProxyPayload(result) {
      if (!result || !result.response) return null;
      const raw = result.response.content || '';
      const parsed = tryParseJson(raw);
      return parsed || raw;
    }

    function updateMeshSelect() {
      const select = document.getElementById('meshSelect');
      select.innerHTML = '';
      for (const mesh of meshList) {
        const opt = document.createElement('option');
        opt.value = mesh.id || '';
        const namePart = mesh.name ? ` | ${mesh.name}` : '';
        const activePart = mesh.active ? ' *active' : '';
        opt.textContent = `${mesh.id || '<no-id>'}${namePart}${activePart}`;
        select.appendChild(opt);
      }
      if (currentMeshId) {
        select.value = currentMeshId;
      }
    }

    async function refreshMeshList() {
      const result = await callApi('GET', '/mesh/list', null);
      const payload = parseProxyPayload(result);
      if (!payload || !payload.meshes) {
        setMeshInfo('Failed to read mesh list from response.');
        return;
      }
      meshList = payload.meshes || [];
      const active = meshList.find(m => m.active);
      if (active && active.id) {
        currentMeshId = active.id;
        document.getElementById('meshIdManual').value = active.id;
      }
      updateMeshSelect();
      document.getElementById('meshStats').textContent = `meshes: ${meshList.length}`;
      setMeshInfo(`Mesh list loaded. Active: ${active ? (active.id || '<unknown>') : 'none'}.`);
    }

    async function loadActiveMesh() {
      const result = await callApi('GET', '/mesh/active', null);
      const payload = parseProxyPayload(result);
      const active = payload && payload.active_mesh ? payload.active_mesh : null;
      if (!active || !active.id) {
        setMeshInfo('No active mesh returned by API.');
        return;
      }
      currentMeshId = active.id;
      document.getElementById('meshIdManual').value = active.id;
      if (!meshList.length) {
        await refreshMeshList();
      } else {
        updateMeshSelect();
      }
      setMeshInfo(`Active mesh: ${active.id}`);
    }

    async function meshSelectById() {
      const meshId = selectedMeshId();
      if (!meshId) {
        setMeshInfo('Select or enter mesh_id first.');
        return;
      }
      const result = await callApi('POST', '/mesh/select', JSON.stringify({ mesh_id: meshId }));
      const payload = parseProxyPayload(result);
      if (payload && payload.mesh && payload.mesh.id) {
        currentMeshId = payload.mesh.id;
        document.getElementById('meshIdManual').value = currentMeshId;
        updateMeshSelect();
      }
      setMeshInfo(`Select mesh request sent for: ${meshId}`);
    }

    async function meshSetActiveFromSelection() {
      const meshId = selectedMeshId();
      if (!meshId) {
        setMeshInfo('Select or enter mesh_id first.');
        return;
      }
      const result = await callApi('POST', '/mesh/select', JSON.stringify({ mesh_id: meshId }));
      const payload = parseProxyPayload(result);
      if (payload && payload.ok) {
        currentMeshId = meshId;
      }
      setMeshInfo(`Set active requested for ${meshId}.`);
      await refreshMeshList();
    }

    async function meshGetPoints() {
      const meshId = await resolveMeshIdForOps();
      if (!meshId) {
        setMeshInfo('No target mesh resolved.');
        return;
      }
      const result = await callApi('GET', `/mesh/points?mesh_id=${encodeURIComponent(meshId)}`, null);
      const payload = parseProxyPayload(result);
      if (!payload || !Array.isArray(payload.points)) {
        setMeshInfo('Points response has no points[] array.');
        return;
      }
      currentMeshId = meshId;
      loadedMeshId = meshId;
      document.getElementById('meshIdManual').value = meshId;
      const select = document.getElementById('meshSelect');
      if (select) {
        select.value = meshId;
      }
      loadedPoints = payload.points.map(p => ({ x: Number(p.x), y: Number(p.y) }));
      if (!originalPointsByMeshId[meshId]) {
        originalPointsByMeshId[meshId] = loadedPoints.map(p => ({ x: p.x, y: p.y }));
      }
      document.getElementById('meshPointsEditor').value = JSON.stringify(loadedPoints, null, 2);
      setMeshInfo(`Loaded ${loadedPoints.length} points for ${meshId}.`);
    }

    function readPointsFromEditor() {
      const txt = document.getElementById('meshPointsEditor').value || '';
      const parsed = tryParseJson(txt);
      if (!parsed) return null;
      const arr = Array.isArray(parsed) ? parsed : (Array.isArray(parsed.points) ? parsed.points : null);
      if (!arr) return null;
      const points = [];
      for (const p of arr) {
        const x = Number(p.x);
        const y = Number(p.y);
        if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
        points.push({ x, y });
      }
      return points;
    }

    async function meshApplyPointsFromEditor() {
      const meshId = await resolveMeshIdForOps();
      if (!meshId) {
        setMeshInfo('No target mesh resolved.');
        return;
      }
      const stateResult = await callApi('GET', '/mesh/state', null);
      const statePayload = parseProxyPayload(stateResult);
      if (!statePayload || statePayload.mesh_edit_mode !== true) {
        setMeshInfo('Mesh edit mode is OFF. Double-click selected mesh in Cubism, then retry Apply.');
        return;
      }
      const points = readPointsFromEditor();
      if (!points || !points.length) {
        setMeshInfo('Invalid points JSON in editor.');
        return;
      }
      if (points.length < 2) {
        setMeshInfo('Too few points. Provide full points list from Get Points.');
        return;
      }
      const body = JSON.stringify({ mesh_id: meshId, points });
      await callApi('POST', '/mesh/points', body);
      loadedPoints = points.map(p => ({ x: p.x, y: p.y }));
      loadedMeshId = meshId;
      setMeshInfo(`Applied ${points.length} points to ${meshId}.`);
    }

    async function meshNudgePoint() {
      const points = readPointsFromEditor();
      const meshId = await resolveMeshIdForOps();
      if (!meshId) {
        setMeshInfo('No target mesh resolved.');
        return;
      }
      if (!points || !points.length) {
        setMeshInfo('Load points first or provide valid points JSON.');
        return;
      }
      const idx = Number(document.getElementById('pointIndex').value);
      const dx = Number(document.getElementById('pointDx').value);
      const dy = Number(document.getElementById('pointDy').value);
      if (!Number.isInteger(idx) || idx < 0 || idx >= points.length) {
        setMeshInfo(`pointIndex out of range. Expected 0..${points.length - 1}`);
        return;
      }
      points[idx].x += dx;
      points[idx].y += dy;
      document.getElementById('meshPointsEditor').value = JSON.stringify(points, null, 2);
      await meshApplyPointsFromEditor();
      setMeshInfo(`Point #${idx} nudged by dx=${dx}, dy=${dy}.`);
    }

    async function meshRevertPoints() {
      const meshId = await resolveMeshIdForOps();
      const orig = meshId ? originalPointsByMeshId[meshId] : null;
      if (!meshId || !orig || !orig.length) {
        setMeshInfo('No original snapshot to revert. Load points first.');
        return;
      }
      document.getElementById('meshPointsEditor').value = JSON.stringify(orig, null, 2);
      await meshApplyPointsFromEditor();
      setMeshInfo(`Reverted points for ${meshId}.`);
    }

    async function meshAutoGenerate(validateOnly) {
      const meshId = await resolveMeshIdForOps();
      if (!meshId) {
        setMeshInfo('No target mesh resolved.');
        return;
      }
      const body = JSON.stringify({ mesh_id: meshId, validate_only: !!validateOnly });
      await callApi('POST', '/mesh/auto_generate', body);
      setMeshInfo(`Auto mesh ${validateOnly ? 'dry-run' : 'execute'} sent for ${meshId}.`);
    }

    async function meshScreenshot() {
      const meshId = await resolveMeshIdForOps();
      if (!meshId) {
        setMeshInfo('No target mesh resolved.');
        return;
      }
      const workspaceOnly = !!document.getElementById('workspaceOnly').checked;
      const route = `/api/screenshot/current?mesh_id=${encodeURIComponent(meshId)}&workspace_only=${workspaceOnly ? 'true' : 'false'}`;
      const imgUrl = `${route}&_t=${Date.now()}`;
      document.getElementById('shotLink').href = imgUrl;
      document.getElementById('shotLink').textContent = route;
      document.getElementById('shotPreview').src = imgUrl;
      setMeshInfo(`Screenshot loaded for ${meshId} (${workspaceOnly ? 'workspace' : 'full window'}).`);
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
      await refreshMeshList();
    }

    init();
  </script>
</body>
</html>
""";
}
