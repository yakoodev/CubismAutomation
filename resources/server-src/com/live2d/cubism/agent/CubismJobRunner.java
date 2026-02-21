package com.live2d.cubism.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "cubism-agent-jobs");
    t.setDaemon(true);
    return t;
  });

  private CubismJobRunner() {}

  public static ApiResponse submit(String body, String idempotencyKey) {
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

    WORKER.execute(() -> runJob(rec));
    return new ApiResponse(202, "{\"ok\":true,\"job\":" + jobJson(rec, true) + "}\n");
  }

  public static ApiResponse getJob(String id) {
    JobRecord rec = JOBS.get(id);
    if (rec == null) {
      return new ApiResponse(404, "{\"ok\":false,\"error\":\"job_not_found\"}\n");
    }
    return new ApiResponse(200, "{\"ok\":true,\"job\":" + jobJson(rec, true) + "}\n");
  }

  public static ApiResponse cancelJob(String id) {
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
    return new ApiResponse(202, "{\"ok\":true,\"job\":" + jobJson(rec, true) + "}\n");
  }

  public static ApiResponse listJobs() {
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
        return;
      }
      rec.status = "running";
      rec.startedAt = Instant.now().toString();
    }
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
    } catch (Throwable t) {
      synchronized (rec) {
        rec.status = "failed";
        rec.error = "job_exception";
        rec.result = "{\"ok\":false,\"error\":\"job_exception\",\"message\":\"" + esc(String.valueOf(t)) + "\"}";
        rec.elapsedMs = System.currentTimeMillis() - started;
        rec.finishedAt = Instant.now().toString();
      }
    }
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
      this.id = id;
      this.action = action;
      this.requestBody = requestBody;
      this.idempotencyKey = idempotencyKey;
      this.timeoutMs = timeoutMs;
      this.createdAt = Instant.now().toString();
      this.status = "queued";
      this.elapsedMs = 0L;
      this.cancelRequested = false;
    }
  }
}
