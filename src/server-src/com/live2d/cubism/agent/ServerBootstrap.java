package com.live2d.cubism.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ServerBootstrap {
  private static final String AGENT_VERSION = "0.1.0-mvp";
  private static final String AUTH_MODE = envOrDefault("CUBISM_AGENT_AUTH_MODE", "off").trim().toLowerCase();
  private static final String AUTH_TOKEN = envOrDefault("CUBISM_AGENT_TOKEN", "");
  private static final String LOG_FILE = envOrDefault(
    "CUBISM_AGENT_LOG_FILE",
    System.getProperty("user.home") + File.separator + "cubism-agent-api.log"
  );
  private static final int LOG_BODY_LIMIT = 4000;
  private static final AtomicBoolean STARTED = new AtomicBoolean(false);
  private static final AtomicLong REQUEST_SEQ = new AtomicLong(0);
  private static volatile HttpServer server;

  private ServerBootstrap() {}

  public static synchronized void start() {
    if (STARTED.get()) {
      return;
    }
    try {
      String host = envOrDefault("CUBISM_AGENT_HOST", "127.0.0.1");
      int port = parsePort(envOrDefault("CUBISM_AGENT_PORT", "18080"), 18080);
      HttpServer created = HttpServer.create(new InetSocketAddress(host, port), 0);
      created.createContext("/hello", ex -> writeText(ex, 200, "hello world\n"));
      created.createContext("/health", ex -> writeText(ex, 200, "ok\n"));
      created.createContext("/version", ServerBootstrap::handleVersion);
      created.createContext("/command", ServerBootstrap::handleCommand);
      created.createContext("/startup/prepare", ServerBootstrap::handleStartupPrepare);
      created.createContext("/state", ServerBootstrap::handleStateAll);
      created.createContext("/state/project", ServerBootstrap::handleStateProject);
      created.createContext("/state/document", ServerBootstrap::handleStateDocument);
      created.createContext("/state/selection", ServerBootstrap::handleStateSelection);
      created.createContext("/mesh/list", ServerBootstrap::handleMeshList);
      created.createContext("/mesh/active", ServerBootstrap::handleMeshActive);
      created.createContext("/mesh/state", ServerBootstrap::handleMeshState);
      created.createContext("/mesh/select", ServerBootstrap::handleMeshSelect);
      created.createContext("/mesh/rename", ServerBootstrap::handleMeshRename);
      created.createContext("/mesh/visibility", ServerBootstrap::handleMeshVisibility);
      created.createContext("/mesh/lock", ServerBootstrap::handleMeshLock);
      created.createContext("/mesh/ops", ServerBootstrap::handleMeshOps);
      created.setExecutor(Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cubism-agent-http");
        t.setDaemon(true);
        return t;
      }));
      created.start();
      server = created;
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
    CubismCommandAdapter.CommandResponse result = CubismCommandAdapter.execute(body);
    writeJson(exchange, result.status(), result.json());
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

  private static void logExchange(HttpExchange exchange, int statusCode, String responseBody) {
    try {
      long requestId = REQUEST_SEQ.incrementAndGet();

      Object reqBodyAttr = exchange.getAttribute("requestBody");
      String requestBody = reqBodyAttr instanceof String ? (String) reqBodyAttr : "";
      String method = exchange.getRequestMethod();
      String path = exchange.getRequestURI() != null ? exchange.getRequestURI().toString() : "";
      String remote = exchange.getRemoteAddress() != null ? exchange.getRemoteAddress().toString() : "";

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

  @FunctionalInterface
  private interface MeshPostHandler {
    CubismMeshAdapter.ApiResponse apply(String body);
  }
}
