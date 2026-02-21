package com.live2d.cubism.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerBootstrap {
  private static final String AGENT_VERSION = "0.1.0-mvp";
  private static final String AUTH_MODE = envOrDefault("CUBISM_AGENT_AUTH_MODE", "off").trim().toLowerCase();
  private static final String AUTH_TOKEN = envOrDefault("CUBISM_AGENT_TOKEN", "");
  private static final String LOG_FILE = envOrDefault(
    "CUBISM_AGENT_LOG_FILE",
    System.getProperty("user.home") + File.separator + "cubism-agent-api.log"
  );
  private static final int LOG_BODY_LIMIT = 4000;
  private static final Pattern COMMAND_FIELD = Pattern.compile("\"command\"\\s*:\\s*\"([^\"]+)\"");
  private static final AtomicBoolean STARTED = new AtomicBoolean(false);
  private static final AtomicLong REQUEST_SEQ = new AtomicLong(0);
  private static final AtomicLong STARTED_AT_MS = new AtomicLong(0);
  private static final AtomicLong TOTAL_REQUESTS = new AtomicLong(0);
  private static final AtomicLong TOTAL_2XX = new AtomicLong(0);
  private static final AtomicLong TOTAL_4XX = new AtomicLong(0);
  private static final AtomicLong TOTAL_5XX = new AtomicLong(0);
  private static final ConcurrentHashMap<String, AtomicLong> PATH_COUNTS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, AtomicLong> COMMAND_COUNTS = new ConcurrentHashMap<>();
  private static volatile HttpServer server;

  private ServerBootstrap() {}

  public static synchronized void start() {
    if (STARTED.get()) {
      return;
    }
    try {
      String host = envOrDefault("CUBISM_AGENT_HOST", "127.0.0.1");
      int port = parsePort(envOrDefault("CUBISM_AGENT_PORT", "18080"), 18080);
      CubismJobRunner.initialize();
      HttpServer created = HttpServer.create(new InetSocketAddress(host, port), 0);
      created.createContext("/hello", ex -> writeText(ex, 200, "hello world\n"));
      created.createContext("/health", ex -> writeText(ex, 200, "ok\n"));
      created.createContext("/version", ServerBootstrap::handleVersion);
      created.createContext("/command", ServerBootstrap::handleCommand);
      created.createContext("/metrics", ServerBootstrap::handleMetrics);
      created.createContext("/jobs", ServerBootstrap::handleJobs);
      created.createContext("/startup/prepare", ServerBootstrap::handleStartupPrepare);
      created.createContext("/state", ServerBootstrap::handleStateAll);
      created.createContext("/state/project", ServerBootstrap::handleStateProject);
      created.createContext("/state/document", ServerBootstrap::handleStateDocument);
      created.createContext("/state/selection", ServerBootstrap::handleStateSelection);
      created.createContext("/state/ui", ServerBootstrap::handleStateUi);
      created.createContext("/parameters", ServerBootstrap::handleParametersList);
      created.createContext("/parameters/state", ServerBootstrap::handleParametersState);
      created.createContext("/parameters/set", ServerBootstrap::handleParametersSet);
      created.createContext("/deformers", ServerBootstrap::handleDeformersList);
      created.createContext("/deformers/state", ServerBootstrap::handleDeformersState);
      created.createContext("/deformers/select", ServerBootstrap::handleDeformersSelect);
      created.createContext("/deformers/rename", ServerBootstrap::handleDeformersRename);
      created.createContext("/project/open", ServerBootstrap::handleProjectOpen);
      created.createContext("/mesh/list", ServerBootstrap::handleMeshList);
      created.createContext("/mesh/active", ServerBootstrap::handleMeshActive);
      created.createContext("/mesh/state", ServerBootstrap::handleMeshState);
      created.createContext("/mesh/select", ServerBootstrap::handleMeshSelect);
      created.createContext("/mesh/rename", ServerBootstrap::handleMeshRename);
      created.createContext("/mesh/visibility", ServerBootstrap::handleMeshVisibility);
      created.createContext("/mesh/lock", ServerBootstrap::handleMeshLock);
      created.createContext("/mesh/ops", ServerBootstrap::handleMeshOps);
      created.createContext("/mesh/points", ServerBootstrap::handleMeshPoints);
      created.createContext("/mesh/points/add", ServerBootstrap::handleMeshPointAdd);
      created.createContext("/mesh/points/remove", ServerBootstrap::handleMeshPointRemove);
      created.createContext("/mesh/auto_generate", ServerBootstrap::handleMeshAutoGenerate);
      created.createContext("/mesh/screenshot", ServerBootstrap::handleMeshScreenshot);
      created.createContext("/screenshot/current", ServerBootstrap::handleScreenshotCurrent);
      created.setExecutor(Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cubism-agent-http");
        t.setDaemon(true);
        return t;
      }));
      created.start();
      server = created;
      STARTED_AT_MS.set(System.currentTimeMillis());
      STARTED.set(true);
    } catch (Throwable ignored) {
      // fail-safe: server bootstrap must not break host application
    }
  }

  public static synchronized void stop() {
    HttpServer current = server;
    if (current != null) {
      current.stop(0);
      server = null;
    }
    STARTED.set(false);
  }

  private static String envOrDefault(String key, String fallback) {
    String value = System.getenv(key);
    return (value == null || value.isBlank()) ? fallback : value;
  }

  private static int parsePort(String value, int fallback) {
    try {
      int parsed = Integer.parseInt(value);
      return (parsed > 0 && parsed <= 65535) ? parsed : fallback;
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static void handleVersion(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    writeJson(
      exchange,
      200,
      "{\"ok\":true,\"agent\":\"cubism-agent-server\",\"version\":\"" + AGENT_VERSION +
      "\",\"commands\":" + CubismCommandAdapter.supportedCommandsJson() + "}\n"
    );
  }

  private static void handleCommand(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }

    String body = readBody(exchange.getRequestBody());
    exchange.setAttribute("requestBody", body);
    String command = parseCommandField(body);
    if (command != null && !command.isBlank()) {
      incrementCounter(COMMAND_COUNTS, command);
    }
    CubismCommandAdapter.CommandResponse result = CubismCommandAdapter.execute(body);
    writeJson(exchange, result.status(), result.json());
  }

  private static void handleMetrics(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    long now = System.currentTimeMillis();
    long startedAt = STARTED_AT_MS.get();
    long uptimeMs = startedAt <= 0 ? 0 : Math.max(0, now - startedAt);
    String json =
      "{\"ok\":true" +
      ",\"agent\":\"cubism-agent-server\"" +
      ",\"version\":\"" + AGENT_VERSION + "\"" +
      ",\"uptime_ms\":" + uptimeMs +
      ",\"requests\":{\"total\":" + TOTAL_REQUESTS.get() +
      ",\"status_2xx\":" + TOTAL_2XX.get() +
      ",\"status_4xx\":" + TOTAL_4XX.get() +
      ",\"status_5xx\":" + TOTAL_5XX.get() + "}" +
      ",\"paths\":" + countersJson(PATH_COUNTS) +
      ",\"commands\":" + countersJson(COMMAND_COUNTS) + "}\n";
    writeJson(exchange, 200, json);
  }

  private static void handleJobs(HttpExchange exchange) throws IOException {
    if (!ensureAuthorized(exchange)) {
      return;
    }
    String path = exchange.getRequestURI() == null ? "" : exchange.getRequestURI().getPath();
    String method = exchange.getRequestMethod() == null ? "" : exchange.getRequestMethod().toUpperCase();

    if ("/jobs".equals(path) || "/jobs/".equals(path)) {
      if ("POST".equals(method)) {
        String body = readBody(exchange.getRequestBody());
        exchange.setAttribute("requestBody", body);
        String idem = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        CubismJobRunner.ApiResponse resp = CubismJobRunner.submit(body, idem);
        writeJson(exchange, resp.status(), resp.json());
        return;
      }
      if ("GET".equals(method)) {
        CubismJobRunner.ApiResponse resp = CubismJobRunner.listJobs();
        writeJson(exchange, resp.status(), resp.json());
        return;
      }
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }

    if (path.startsWith("/jobs/")) {
      String tail = path.substring("/jobs/".length());
      if (tail.endsWith("/cancel")) {
        if (!"POST".equals(method)) {
          writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
          return;
        }
        String id = tail.substring(0, tail.length() - "/cancel".length());
        CubismJobRunner.ApiResponse resp = CubismJobRunner.cancelJob(id);
        writeJson(exchange, resp.status(), resp.json());
        return;
      }
      if (!"GET".equals(method)) {
        writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
        return;
      }
      CubismJobRunner.ApiResponse resp = CubismJobRunner.getJob(tail);
      writeJson(exchange, resp.status(), resp.json());
      return;
    }

    writeJson(exchange, 404, "{\"ok\":false,\"error\":\"not_found\"}\n");
  }

  private static void handleStateAll(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    writeJson(exchange, 200, CubismStateAdapter.stateAllJson());
  }

  private static void handleStateProject(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    writeJson(exchange, 200, CubismStateAdapter.stateProjectJson());
  }

  private static void handleStateDocument(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    writeJson(exchange, 200, CubismStateAdapter.stateDocumentJson());
  }

  private static void handleStateSelection(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    writeJson(exchange, 200, CubismStateAdapter.stateSelectionJson());
  }

  private static void handleStateUi(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    writeJson(exchange, 200, CubismStateAdapter.stateUiJson());
  }

  private static void handleParametersList(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    CubismParameterAdapter.ApiResponse response = CubismParameterAdapter.parametersList();
    writeJson(exchange, response.status(), response.json());
  }

  private static void handleParametersState(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    CubismParameterAdapter.ApiResponse response = CubismParameterAdapter.parametersState();
    writeJson(exchange, response.status(), response.json());
  }

  private static void handleParametersSet(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    String body = readBody(exchange.getRequestBody());
    exchange.setAttribute("requestBody", body);
    CubismParameterAdapter.ApiResponse response = CubismParameterAdapter.parametersSet(body);
    writeJson(exchange, response.status(), response.json());
  }

  private static void handleDeformersList(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    CubismDeformerAdapter.ApiResponse response = CubismDeformerAdapter.deformersList();
    writeJson(exchange, response.status(), response.json());
  }

  private static void handleDeformersState(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    CubismDeformerAdapter.ApiResponse response = CubismDeformerAdapter.deformersState();
    writeJson(exchange, response.status(), response.json());
  }

  private static void handleDeformersSelect(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    String body = readBody(exchange.getRequestBody());
    exchange.setAttribute("requestBody", body);
    CubismDeformerAdapter.ApiResponse response = CubismDeformerAdapter.deformerSelect(body);
    writeJson(exchange, response.status(), response.json());
  }

  private static void handleDeformersRename(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    String body = readBody(exchange.getRequestBody());
    exchange.setAttribute("requestBody", body);
    CubismDeformerAdapter.ApiResponse response = CubismDeformerAdapter.deformerRename(body);
    writeJson(exchange, response.status(), response.json());
  }

  private static void handleProjectOpen(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    String body = readBody(exchange.getRequestBody());
    exchange.setAttribute("requestBody", body);
    CubismProjectAdapter.ApiResponse response = CubismProjectAdapter.projectOpen(body);
    writeJson(exchange, response.status(), response.json());
  }

  private static void handleStartupPrepare(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    String body = readBody(exchange.getRequestBody());
    exchange.setAttribute("requestBody", body);
    String json = StartupAutomationAdapter.prepare(body);
    writeJson(exchange, json.startsWith("{\"ok\":true") ? 200 : 500, json);
  }

  private static void handleMeshList(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    CubismMeshAdapter.ApiResponse response = CubismMeshAdapter.meshList();
    writeJson(exchange, response.status(), response.json());
  }

  private static void handleMeshActive(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    CubismMeshAdapter.ApiResponse response = CubismMeshAdapter.meshActive();
    writeJson(exchange, response.status(), response.json());
  }

  private static void handleMeshState(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    CubismMeshAdapter.ApiResponse response = CubismMeshAdapter.meshState();
    writeJson(exchange, response.status(), response.json());
  }

  private static void handleMeshSelect(HttpExchange exchange) throws IOException {
    handleMeshPost(exchange, CubismMeshAdapter::meshSelect);
  }

  private static void handleMeshRename(HttpExchange exchange) throws IOException {
    handleMeshPost(exchange, CubismMeshAdapter::meshRename);
  }

  private static void handleMeshVisibility(HttpExchange exchange) throws IOException {
    handleMeshPost(exchange, CubismMeshAdapter::meshVisibility);
  }

  private static void handleMeshLock(HttpExchange exchange) throws IOException {
    handleMeshPost(exchange, CubismMeshAdapter::meshLock);
  }

  private static void handleMeshOps(HttpExchange exchange) throws IOException {
    handleMeshPost(exchange, CubismMeshAdapter::meshOps);
  }

  private static void handleMeshPoints(HttpExchange exchange) throws IOException {
    if (!ensureAuthorized(exchange)) {
      return;
    }
    if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      String selector = selectorPayloadFromQuery(exchange);
      CubismMeshAdapter.ApiResponse response = CubismMeshAdapter.meshPointsGet(selector);
      writeJson(exchange, response.status(), response.json());
      return;
    }
    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      String body = readBody(exchange.getRequestBody());
      exchange.setAttribute("requestBody", body);
      CubismMeshAdapter.ApiResponse response = CubismMeshAdapter.meshPointsSet(body);
      writeJson(exchange, response.status(), response.json());
      return;
    }
    writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
  }

  private static void handleMeshAutoGenerate(HttpExchange exchange) throws IOException {
    handleMeshPost(exchange, CubismMeshAdapter::meshAutoGenerate);
  }

  private static void handleMeshPointAdd(HttpExchange exchange) throws IOException {
    handleMeshPost(exchange, CubismMeshAdapter::meshPointAdd);
  }

  private static void handleMeshPointRemove(HttpExchange exchange) throws IOException {
    handleMeshPost(exchange, CubismMeshAdapter::meshPointRemove);
  }

  private static void handleMeshScreenshot(HttpExchange exchange) throws IOException {
    if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      if (!ensureAuthorized(exchange)) {
        return;
      }
      String selector = selectorPayloadFromQuery(exchange);
      exchange.setAttribute("requestBody", selector);
      CubismMeshAdapter.ApiResponse response = CubismMeshAdapter.meshScreenshot(selector);
      writeJson(exchange, response.status(), response.json());
      return;
    }
    handleMeshPost(exchange, CubismMeshAdapter::meshScreenshot);
  }

  private static void handleScreenshotCurrent(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    String selector = selectorPayloadFromQuery(exchange);
    exchange.setAttribute("requestBody", selector);
    boolean workspaceOnly = boolQueryParam(exchange, "workspace_only", true);
    CubismMeshAdapter.PngResponse response = CubismMeshAdapter.screenshotCurrent(selector, workspaceOnly);
    if (response.bytes() != null) {
      writeBinary(exchange, response.status(), "image/png", response.bytes());
      return;
    }
    writeJson(exchange, response.status(), response.errorJson() == null ? "{\"ok\":false,\"error\":\"capture_failed\"}\n" : response.errorJson());
  }

  private static void handleMeshPost(HttpExchange exchange, MeshPostHandler handler) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}\n");
      return;
    }
    if (!ensureAuthorized(exchange)) {
      return;
    }
    String body = readBody(exchange.getRequestBody());
    exchange.setAttribute("requestBody", body);
    CubismMeshAdapter.ApiResponse response = handler.apply(body);
    writeJson(exchange, response.status(), response.json());
  }

  private static boolean ensureAuthorized(HttpExchange exchange) throws IOException {
    if ("off".equals(AUTH_MODE) || "disabled".equals(AUTH_MODE) || "none".equals(AUTH_MODE)) {
      return true;
    }

    if (AUTH_TOKEN.isBlank()) {
      writeJson(exchange, 503, "{\"ok\":false,\"error\":\"auth_misconfigured\",\"message\":\"set CUBISM_AGENT_TOKEN\"}\n");
      return false;
    }

    String provided = extractToken(exchange);
    if (AUTH_TOKEN.equals(provided)) {
      return true;
    }

    exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
    writeJson(exchange, 401, "{\"ok\":false,\"error\":\"unauthorized\"}\n");
    return false;
  }

  private static String extractToken(HttpExchange exchange) {
    String auth = exchange.getRequestHeaders().getFirst("Authorization");
    if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return auth.substring(7).trim();
    }
    String headerToken = exchange.getRequestHeaders().getFirst("X-Api-Token");
    if (headerToken != null) {
      return headerToken.trim();
    }
    return "";
  }

  private static String readBody(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    int read;
    while ((read = is.read(buf)) != -1) {
      baos.write(buf, 0, read);
    }
    return baos.toString(StandardCharsets.UTF_8);
  }

  private static String selectorPayloadFromQuery(HttpExchange exchange) {
    String raw = exchange.getRequestURI() == null ? null : exchange.getRequestURI().getRawQuery();
    if (raw == null || raw.isBlank()) {
      return "{}";
    }
    String meshId = null;
    String meshName = null;
    for (String pair : raw.split("&")) {
      int pos = pair.indexOf('=');
      String key = pos >= 0 ? pair.substring(0, pos) : pair;
      String value = pos >= 0 ? pair.substring(pos + 1) : "";
      String decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8);
      String decodedValue = URLDecoder.decode(value, StandardCharsets.UTF_8);
      if ("mesh_id".equals(decodedKey) || "id".equals(decodedKey) || "target_id".equals(decodedKey)) {
        meshId = decodedValue;
      } else if ("mesh_name".equals(decodedKey) || "name".equals(decodedKey) || "target_name".equals(decodedKey)) {
        meshName = decodedValue;
      }
    }
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    boolean has = false;
    if (meshId != null && !meshId.isBlank()) {
      sb.append("\"mesh_id\":\"").append(meshId.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
      has = true;
    }
    if (meshName != null && !meshName.isBlank()) {
      if (has) {
        sb.append(',');
      }
      sb.append("\"mesh_name\":\"").append(meshName.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
      has = true;
    }
    sb.append('}');
    return sb.toString();
  }

  private static boolean boolQueryParam(HttpExchange exchange, String key, boolean fallback) {
    String raw = exchange.getRequestURI() == null ? null : exchange.getRequestURI().getRawQuery();
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    for (String pair : raw.split("&")) {
      int pos = pair.indexOf('=');
      String k = pos >= 0 ? pair.substring(0, pos) : pair;
      if (!key.equals(URLDecoder.decode(k, StandardCharsets.UTF_8))) {
        continue;
      }
      String v = pos >= 0 ? pair.substring(pos + 1) : "";
      String d = URLDecoder.decode(v, StandardCharsets.UTF_8).trim().toLowerCase();
      if ("1".equals(d) || "true".equals(d) || "yes".equals(d) || "on".equals(d)) {
        return true;
      }
      if ("0".equals(d) || "false".equals(d) || "no".equals(d) || "off".equals(d)) {
        return false;
      }
    }
    return fallback;
  }

  private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
    logExchange(exchange, statusCode, body);
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(statusCode, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    } finally {
      exchange.close();
    }
  }

  private static void writeText(HttpExchange exchange, int statusCode, String body) throws IOException {
    logExchange(exchange, statusCode, body);
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    exchange.sendResponseHeaders(statusCode, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    } finally {
      exchange.close();
    }
  }

  private static void writeBinary(HttpExchange exchange, int statusCode, String contentType, byte[] body) throws IOException {
    String safeType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    logExchange(exchange, statusCode, "<binary " + (body == null ? 0 : body.length) + " bytes; " + safeType + ">");
    byte[] bytes = body == null ? new byte[0] : body;
    exchange.getResponseHeaders().set("Content-Type", safeType);
    exchange.sendResponseHeaders(statusCode, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    } finally {
      exchange.close();
    }
  }

  private static void logExchange(HttpExchange exchange, int statusCode, String responseBody) {
    try {
      long requestId = REQUEST_SEQ.incrementAndGet();

      Object reqBodyAttr = exchange.getAttribute("requestBody");
      String requestBody = reqBodyAttr instanceof String ? (String) reqBodyAttr : "";
      String method = exchange.getRequestMethod();
      String path = exchange.getRequestURI() != null ? exchange.getRequestURI().toString() : "";
      String pathOnly = exchange.getRequestURI() != null ? exchange.getRequestURI().getPath() : "";
      String remote = exchange.getRemoteAddress() != null ? exchange.getRemoteAddress().toString() : "";

      TOTAL_REQUESTS.incrementAndGet();
      if (statusCode >= 200 && statusCode < 300) {
        TOTAL_2XX.incrementAndGet();
      } else if (statusCode >= 400 && statusCode < 500) {
        TOTAL_4XX.incrementAndGet();
      } else if (statusCode >= 500) {
        TOTAL_5XX.incrementAndGet();
      }
      incrementCounter(PATH_COUNTS, pathOnly == null || pathOnly.isBlank() ? "<unknown>" : pathOnly);

      String line =
        Instant.now() +
        " request_id=" + requestId +
        " remote=" + sanitize(remote) +
        " method=" + sanitize(method) +
        " path=" + sanitize(path) +
        " status=" + statusCode +
        " request_body=" + sanitize(limit(requestBody)) +
        " response_body=" + sanitize(limit(responseBody));

      appendLogLine(line);
    } catch (Throwable ignored) {
      // Logging must stay fail-safe.
    }
  }

  private static void appendLogLine(String line) {
    try {
      Path path = Paths.get(LOG_FILE);
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      synchronized (ServerBootstrap.class) {
        Files.writeString(
          path,
          line + System.lineSeparator(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND
        );
      }
    } catch (Throwable ignored) {
      // Logging must stay fail-safe.
    }
  }

  private static String limit(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    if (value.length() <= LOG_BODY_LIMIT) {
      return value;
    }
    return value.substring(0, LOG_BODY_LIMIT) + "...(truncated)";
  }

  private static String sanitize(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    return value
      .replace("\\", "\\\\")
      .replace("\r", "\\r")
      .replace("\n", "\\n");
  }

  private static void incrementCounter(ConcurrentHashMap<String, AtomicLong> counters, String key) {
    counters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
  }

  private static String countersJson(Map<String, AtomicLong> counters) {
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    boolean first = true;
    for (Map.Entry<String, AtomicLong> e : counters.entrySet()) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      sb.append("\"").append(escJsonKey(e.getKey())).append("\":").append(e.getValue().get());
    }
    sb.append('}');
    return sb.toString();
  }

  private static String parseCommandField(String body) {
    if (body == null) {
      return null;
    }
    Matcher m = COMMAND_FIELD.matcher(body);
    if (!m.find()) {
      return null;
    }
    return m.group(1).trim();
  }

  private static String escJsonKey(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  @FunctionalInterface
  private interface MeshPostHandler {
    CubismMeshAdapter.ApiResponse apply(String body);
  }
}
