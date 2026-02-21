package com.live2d.cubism.agent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CubismJobRunner {
  private static final Pattern STRING_FIELD = Pattern.compile("\"([a-zA-Z0-9_\\-]+)\"\\s*:\\s*\"([^\"]*)\"");
  private static final Pattern NUMBER_FIELD = Pattern.compile("\"([a-zA-Z0-9_\\-]+)\"\\s*:\\s*(-?\\d+)");
  private static final AtomicLong SEQ = new AtomicLong(0);
  private static final ConcurrentHashMap<String, JobRecord> JOBS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, String> IDEM = new ConcurrentHashMap<>();
  private static final Object STORE_LOCK = new Object();
  private static final String STORE_PATH_PROP = "cubism.agent.jobs.store";
  private static final String TTL_MS_PROP = "cubism.agent.jobs.ttl.ms";
  private static final String MAX_JOBS_PROP = "cubism.agent.jobs.max";
  private static final String DEFAULT_STORE_PATH = "temp/jobs-store.tsv";
  private static final long DEFAULT_TTL_MS = 86_400_000L;
  private static final int DEFAULT_MAX_JOBS = 2000;
  private static volatile boolean initialized = false;
  private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "cubism-agent-jobs");
    t.setDaemon(true);
    return t;
  });

  private CubismJobRunner() {}

  public static void initialize() {
    ensureInitialized();
  }

  public static ApiResponse submit(String body, String idempotencyKey) {
    ensureInitialized();
    String action = parseString(body, "action");
    if (action == null || action.isBlank()) {
      action = "noop";
    }
    long timeoutMs = parseLong(body, "timeout_ms", 120_000L);
    if (timeoutMs < 1000L) {
      timeoutMs = 1000L;
    }
    if (timeoutMs > 3_600_000L) {
      timeoutMs = 3_600_000L;
    }

    String idem = normalizeKey(idempotencyKey);
    if (idem != null) {
      String existing = IDEM.get(idem);
      if (existing != null) {
        JobRecord r = JOBS.get(existing);
        if (r != null) {
          return new ApiResponse(
            200,
            "{\"ok\":true,\"idempotent_reused\":true,\"job\":" + jobJson(r, true) + "}\n"
          );
        }
      }
    }

    String id = "job-" + Instant.now().toEpochMilli() + "-" + SEQ.incrementAndGet();
    JobRecord rec = new JobRecord(id, action, body == null ? "" : body, idem, timeoutMs);
    JOBS.put(id, rec);
    if (idem != null) {
      IDEM.putIfAbsent(idem, id);
    }
    persistSnapshot();

    WORKER.execute(() -> runJob(rec));
    return new ApiResponse(202, "{\"ok\":true,\"job\":" + jobJson(rec, true) + "}\n");
  }

  public static ApiResponse getJob(String id) {
    ensureInitialized();
    JobRecord rec = JOBS.get(id);
    if (rec == null) {
      return new ApiResponse(404, "{\"ok\":false,\"error\":\"job_not_found\"}\n");
    }
    return new ApiResponse(200, "{\"ok\":true,\"job\":" + jobJson(rec, true) + "}\n");
  }

  public static ApiResponse cancelJob(String id) {
    ensureInitialized();
    JobRecord rec = JOBS.get(id);
    if (rec == null) {
      return new ApiResponse(404, "{\"ok\":false,\"error\":\"job_not_found\"}\n");
    }
    synchronized (rec) {
      if ("done".equals(rec.status) || "failed".equals(rec.status) || "canceled".equals(rec.status)) {
        return new ApiResponse(
          409,
          "{\"ok\":false,\"error\":\"job_cannot_cancel\",\"status\":\"" + esc(rec.status) + "\"}\n"
        );
      }
      rec.cancelRequested = true;
      if ("queued".equals(rec.status)) {
        rec.status = "canceled";
        rec.finishedAt = Instant.now().toString();
      }
    }
    persistSnapshot();
    return new ApiResponse(202, "{\"ok\":true,\"job\":" + jobJson(rec, true) + "}\n");
  }

  public static ApiResponse listJobs() {
    ensureInitialized();
    List<JobRecord> rows = new ArrayList<>(JOBS.values());
    rows.sort((a, b) -> b.id.compareTo(a.id));
    StringBuilder sb = new StringBuilder();
    sb.append("{\"ok\":true,\"count\":").append(rows.size()).append(",\"jobs\":[");
    int limit = Math.min(100, rows.size());
    for (int i = 0; i < limit; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(jobJson(rows.get(i), false));
    }
    sb.append("]}\n");
    return new ApiResponse(200, sb.toString());
  }

  private static void runJob(JobRecord rec) {
    synchronized (rec) {
      if ("canceled".equals(rec.status) || rec.cancelRequested) {
        rec.status = "canceled";
        rec.finishedAt = Instant.now().toString();
        persistSnapshot();
        return;
      }
      rec.status = "running";
      rec.startedAt = Instant.now().toString();
    }
    persistSnapshot();
    long started = System.currentTimeMillis();
    try {
      JobActionResult result = runAction(rec);
      long elapsed = System.currentTimeMillis() - started;
      synchronized (rec) {
        rec.elapsedMs = elapsed;
        if (rec.cancelRequested) {
          rec.status = "canceled";
          rec.error = "canceled";
        } else if (elapsed > rec.timeoutMs) {
          rec.status = "failed";
          rec.error = "timeout_exceeded";
        } else if (result.ok) {
          rec.status = "done";
          rec.result = result.json;
        } else {
          rec.status = "failed";
          rec.error = result.error == null ? "job_failed" : result.error;
          rec.result = result.json;
        }
        rec.finishedAt = Instant.now().toString();
      }
      persistSnapshot();
    } catch (Throwable t) {
      synchronized (rec) {
        rec.status = "failed";
        rec.error = "job_exception";
        rec.result = "{\"ok\":false,\"error\":\"job_exception\",\"message\":\"" + esc(String.valueOf(t)) + "\"}";
        rec.elapsedMs = System.currentTimeMillis() - started;
        rec.finishedAt = Instant.now().toString();
      }
      persistSnapshot();
    }
  }

  private static void ensureInitialized() {
    if (initialized) {
      return;
    }
    synchronized (STORE_LOCK) {
      if (initialized) {
        return;
      }
      loadSnapshotLocked();
      initialized = true;
    }
  }

  private static void loadSnapshotLocked() {
    Path store = resolveStorePath();
    int loaded = 0;
    int reconciled = 0;
    if (!Files.exists(store)) {
      System.out.println("[jobs] no snapshot at " + store.toAbsolutePath());
      return;
    }
    try {
      List<String> lines = Files.readAllLines(store, StandardCharsets.UTF_8);
      for (String line : lines) {
        JobRecord rec = parseJobLine(line);
        if (rec == null || rec.id == null || rec.id.isBlank()) {
          continue;
        }
        if ("queued".equals(rec.status) || "running".equals(rec.status)) {
          rec.status = "failed";
          rec.error = "recovered_interrupted";
          rec.finishedAt = Instant.now().toString();
          reconciled++;
        }
        JOBS.put(rec.id, rec);
        if (rec.idempotencyKey != null && !rec.idempotencyKey.isBlank()) {
          IDEM.putIfAbsent(rec.idempotencyKey, rec.id);
        }
        loaded++;
        bumpSeq(rec.id);
      }
      cleanupExpiredAndOverflowLocked();
      persistSnapshotLocked();
      System.out.println(
        "[jobs] recovery loaded=" + loaded
          + " reconciled=" + reconciled
          + " active=" + JOBS.size()
          + " store=" + store.toAbsolutePath()
      );
    } catch (Exception e) {
      System.out.println("[jobs] recovery failed: " + e);
    }
  }

  private static void persistSnapshot() {
    synchronized (STORE_LOCK) {
      if (!initialized) {
        return;
      }
      persistSnapshotLocked();
    }
  }

  private static void persistSnapshotLocked() {
    try {
      cleanupExpiredAndOverflowLocked();
      Path store = resolveStorePath();
      Path parent = store.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      List<JobRecord> rows = new ArrayList<>(JOBS.values());
      rows.sort((a, b) -> a.id.compareTo(b.id));
      List<String> lines = new ArrayList<>(rows.size());
      for (JobRecord row : rows) {
        lines.add(formatJobLine(row));
      }
      Path tmp = store.resolveSibling(store.getFileName().toString() + ".tmp");
      Files.write(tmp, lines, StandardCharsets.UTF_8);
      Files.move(tmp, store, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (Exception e) {
      System.out.println("[jobs] persist failed: " + e);
    }
  }

  private static void cleanupExpiredAndOverflowLocked() {
    long ttlMs = readLongProperty(TTL_MS_PROP, DEFAULT_TTL_MS);
    int maxJobs = (int) readLongProperty(MAX_JOBS_PROP, DEFAULT_MAX_JOBS);
    long now = System.currentTimeMillis();

    List<JobRecord> rows = new ArrayList<>(JOBS.values());
    for (JobRecord rec : rows) {
      if (!isTerminal(rec.status) || rec.finishedAt == null) {
        continue;
      }
      long finished = parseInstantMillis(rec.finishedAt);
      if (finished > 0 && (now - finished) > ttlMs) {
        JOBS.remove(rec.id);
      }
    }

    rows = new ArrayList<>(JOBS.values());
    if (rows.size() > maxJobs) {
      rows.sort((a, b) -> Long.compare(recordSortKey(a), recordSortKey(b)));
      int removeCount = rows.size() - maxJobs;
      for (int i = 0; i < rows.size() && removeCount > 0; i++) {
        JobRecord rec = rows.get(i);
        if (!isTerminal(rec.status)) {
          continue;
        }
        JOBS.remove(rec.id);
        removeCount--;
      }
    }
    IDEM.entrySet().removeIf(e -> !JOBS.containsKey(e.getValue()));
  }

  private static String formatJobLine(JobRecord rec) {
    synchronized (rec) {
      return enc(rec.id)
        + "|" + enc(rec.action)
        + "|" + enc(rec.status)
        + "|" + enc(rec.idempotencyKey)
        + "|" + rec.cancelRequested
        + "|" + rec.timeoutMs
        + "|" + enc(rec.createdAt)
        + "|" + enc(rec.startedAt)
        + "|" + enc(rec.finishedAt)
        + "|" + rec.elapsedMs
        + "|" + enc(rec.error)
        + "|" + enc(rec.result)
        + "|" + enc(rec.requestBody);
    }
  }

  private static JobRecord parseJobLine(String line) {
    if (line == null || line.isBlank()) {
      return null;
    }
    String[] p = line.split("\\|", -1);
    if (p.length < 13) {
      return null;
    }
    String id = dec(p[0]);
    String action = dec(p[1]);
    String status = dec(p[2]);
    String idem = dec(p[3]);
    boolean cancelRequested = Boolean.parseBoolean(p[4]);
    long timeoutMs = parseLongSafe(p[5], 120_000L);
    String createdAt = dec(p[6]);
    String startedAt = dec(p[7]);
    String finishedAt = dec(p[8]);
    long elapsedMs = parseLongSafe(p[9], 0L);
    String error = dec(p[10]);
    String result = dec(p[11]);
    String requestBody = dec(p[12]);
    return JobRecord.fromPersisted(
      id, action, requestBody, idem, timeoutMs, createdAt, status, startedAt, finishedAt, error, result, elapsedMs, cancelRequested
    );
  }

  private static String enc(String value) {
    if (value == null) {
      return "-";
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String dec(String value) {
    if (value == null || value.isBlank() || "-".equals(value)) {
      return null;
    }
    try {
      return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static long parseLongSafe(String raw, long fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static long parseInstantMillis(String instant) {
    try {
      return Instant.parse(instant).toEpochMilli();
    } catch (Exception e) {
      return -1L;
    }
  }

  private static long recordSortKey(JobRecord rec) {
    long finished = parseInstantMillis(rec.finishedAt);
    if (finished > 0) {
      return finished;
    }
    long created = parseInstantMillis(rec.createdAt);
    return created > 0 ? created : Long.MAX_VALUE;
  }

  private static long readLongProperty(String key, long fallback) {
    String raw = System.getProperty(key);
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static Path resolveStorePath() {
    String raw = System.getProperty(STORE_PATH_PROP);
    if (raw == null || raw.isBlank()) {
      raw = DEFAULT_STORE_PATH;
    }
    return Paths.get(raw).toAbsolutePath().normalize();
  }

  private static void bumpSeq(String id) {
    if (id == null) {
      return;
    }
    int idx = id.lastIndexOf('-');
    if (idx < 0 || idx >= id.length() - 1) {
      return;
    }
    long candidate = parseLongSafe(id.substring(idx + 1), -1L);
    if (candidate < 0) {
      return;
    }
    SEQ.updateAndGet(prev -> Math.max(prev, candidate));
  }

  private static boolean isTerminal(String status) {
    return "done".equals(status) || "failed".equals(status) || "canceled".equals(status);
  }

  private static JobActionResult runAction(JobRecord rec) {
    if (rec.cancelRequested) {
      return JobActionResult.fail("canceled", "{\"ok\":false,\"error\":\"canceled\"}");
    }
    String action = rec.action == null ? "" : rec.action.trim().toLowerCase();
    return switch (action) {
      case "noop" -> JobActionResult.ok("{\"ok\":true,\"action\":\"noop\"}");
      case "sleep" -> runSleepAction(rec);
      case "project_open" -> {
        CubismProjectAdapter.ApiResponse r = CubismProjectAdapter.projectOpen(rec.requestBody);
        if (r.status() >= 200 && r.status() < 300) {
          yield JobActionResult.ok(r.json().trim());
        }
        yield JobActionResult.fail("project_open_failed", r.json().trim());
      }
      default -> JobActionResult.fail("invalid_action", "{\"ok\":false,\"error\":\"invalid_action\",\"action\":\"" + esc(action) + "\"}");
    };
  }

  private static JobActionResult runSleepAction(JobRecord rec) {
    long sleepMs = parseLong(rec.requestBody, "sleep_ms", 3000L);
    if (sleepMs < 0L) {
      sleepMs = 0L;
    }
    long remaining = sleepMs;
    while (remaining > 0) {
      if (rec.cancelRequested) {
        return JobActionResult.fail("canceled", "{\"ok\":false,\"error\":\"canceled\"}");
      }
      long step = Math.min(200L, remaining);
      try {
        Thread.sleep(step);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return JobActionResult.fail("interrupted", "{\"ok\":false,\"error\":\"interrupted\"}");
      }
      remaining -= step;
    }
    return JobActionResult.ok("{\"ok\":true,\"action\":\"sleep\",\"slept_ms\":" + sleepMs + "}");
  }

  private static String jobJson(JobRecord rec, boolean includeBody) {
    synchronized (rec) {
      StringBuilder sb = new StringBuilder();
      sb.append('{');
      sb.append("\"id\":\"").append(esc(rec.id)).append("\"");
      sb.append(",\"action\":\"").append(esc(rec.action)).append("\"");
      sb.append(",\"status\":\"").append(esc(rec.status)).append("\"");
      sb.append(",\"idempotency_key\":").append(rec.idempotencyKey == null ? "null" : "\"" + esc(rec.idempotencyKey) + "\"");
      sb.append(",\"cancel_requested\":").append(rec.cancelRequested);
      sb.append(",\"timeout_ms\":").append(rec.timeoutMs);
      sb.append(",\"created_at\":\"").append(esc(rec.createdAt)).append("\"");
      sb.append(",\"started_at\":").append(rec.startedAt == null ? "null" : "\"" + esc(rec.startedAt) + "\"");
      sb.append(",\"finished_at\":").append(rec.finishedAt == null ? "null" : "\"" + esc(rec.finishedAt) + "\"");
      sb.append(",\"elapsed_ms\":").append(rec.elapsedMs);
      sb.append(",\"error\":").append(rec.error == null ? "null" : "\"" + esc(rec.error) + "\"");
      sb.append(",\"result\":").append(rec.result == null ? "null" : rec.result);
      if (includeBody) {
        sb.append(",\"request_body\":\"").append(esc(rec.requestBody)).append("\"");
      }
      sb.append('}');
      return sb.toString();
    }
  }

  private static String normalizeKey(String raw) {
    if (raw == null) {
      return null;
    }
    String t = raw.trim();
    return t.isEmpty() ? null : t;
  }

  private static String parseString(String body, String field) {
    if (body == null) {
      return null;
    }
    Matcher m = STRING_FIELD.matcher(body);
    while (m.find()) {
      if (field.equals(m.group(1))) {
        return m.group(2);
      }
    }
    return null;
  }

  private static long parseLong(String body, String field, long fallback) {
    if (body == null) {
      return fallback;
    }
    Matcher m = NUMBER_FIELD.matcher(body);
    while (m.find()) {
      if (field.equals(m.group(1))) {
        try {
          return Long.parseLong(m.group(2));
        } catch (NumberFormatException ignored) {
          return fallback;
        }
      }
    }
    return fallback;
  }

  private static String esc(String value) {
    if (value == null) {
      return "";
    }
    return value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\r", "\\r")
      .replace("\n", "\\n")
      .replace("\t", "\\t");
  }

  public record ApiResponse(int status, String json) {}

  private record JobActionResult(boolean ok, String error, String json) {
    static JobActionResult ok(String json) {
      return new JobActionResult(true, null, json);
    }
    static JobActionResult fail(String error, String json) {
      return new JobActionResult(false, error, json);
    }
  }

  private static final class JobRecord {
    final String id;
    final String action;
    final String requestBody;
    final String idempotencyKey;
    final long timeoutMs;
    final String createdAt;
    String status;
    String startedAt;
    String finishedAt;
    String error;
    String result;
    long elapsedMs;
    boolean cancelRequested;

    JobRecord(String id, String action, String requestBody, String idempotencyKey, long timeoutMs) {
      this(id, action, requestBody, idempotencyKey, timeoutMs, Instant.now().toString());
      this.status = "queued";
      this.elapsedMs = 0L;
      this.cancelRequested = false;
    }

    JobRecord(String id, String action, String requestBody, String idempotencyKey, long timeoutMs, String createdAt) {
      this.id = id;
      this.action = action;
      this.requestBody = requestBody;
      this.idempotencyKey = idempotencyKey;
      this.timeoutMs = timeoutMs;
      this.createdAt = createdAt == null ? Instant.now().toString() : createdAt;
    }

    static JobRecord fromPersisted(
      String id,
      String action,
      String requestBody,
      String idempotencyKey,
      long timeoutMs,
      String createdAt,
      String status,
      String startedAt,
      String finishedAt,
      String error,
      String result,
      long elapsedMs,
      boolean cancelRequested
    ) {
      JobRecord rec = new JobRecord(id, action, requestBody, idempotencyKey, timeoutMs, createdAt);
      rec.status = (status == null || status.isBlank()) ? "failed" : status;
      rec.startedAt = startedAt;
      rec.finishedAt = finishedAt;
      rec.error = error;
      rec.result = result;
      rec.elapsedMs = Math.max(0L, elapsedMs);
      rec.cancelRequested = cancelRequested;
      return rec;
    }
  }
}
