package com.live2d.cubism.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerBootstrap {
  private static final String AGENT_VERSION = "0.1.0-mvp";
  private static final String AUTH_MODE = envOrDefault("CUBISM_AGENT_AUTH_MODE", "off").trim().toLowerCase();
  private static final String AUTH_TOKEN = envOrDefault("CUBISM_AGENT_TOKEN", "");
  private static final AtomicBoolean STARTED = new AtomicBoolean(false);
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
      created.createContext("/state", ServerBootstrap::handleStateAll);
      created.createContext("/state/project", ServerBootstrap::handleStateProject);
      created.createContext("/state/document", ServerBootstrap::handleStateDocument);
      created.createContext("/state/selection", ServerBootstrap::handleStateSelection);
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
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    exchange.sendResponseHeaders(statusCode, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    } finally {
      exchange.close();
    }
  }
}
