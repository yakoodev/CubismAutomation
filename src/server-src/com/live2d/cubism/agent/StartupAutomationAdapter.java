package com.live2d.cubism.agent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

public final class StartupAutomationAdapter {
  private static final Pattern LICENSE_MODE = Pattern.compile("\"license_mode\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern CREATE_NEW_MODEL = Pattern.compile("\"create_new_model\"\\s*:\\s*(true|false)");
  private static final Pattern WAIT_TIMEOUT_MS = Pattern.compile("\"wait_timeout_ms\"\\s*:\\s*(\\d+)");

  private StartupAutomationAdapter() {}

  public static String prepare(String body) {
    try {
      Request req = parseRequest(body);
      long start = System.currentTimeMillis();

      Object appCtrl = waitForAppCtrl(req.waitTimeoutMs);
      if (appCtrl == null) {
        return error("startup_timeout", "CEAppCtrl not ready within timeout");
      }

      StringBuilder steps = new StringBuilder();
      steps.append("[");
      steps.append("{\"step\":\"wait_app_ready\",\"ok\":true}");

      long windowStepTimeout = Math.max(1000L, req.waitTimeoutMs / 3);
      StartupWindowAutomator.Result license = StartupWindowAutomator.handleLicenseDialog(req.licenseMode, windowStepTimeout);
      steps.append(",{\"step\":\"license_dialog\",\"ok\":").append(license.ok())
        .append(",\"status\":\"").append(esc(license.status()))
        .append("\",\"mode\":\"").append(esc(req.licenseMode))
        .append("\",\"details\":\"").append(esc(license.details())).append("\"}");

      StartupWindowAutomator.Result startup = StartupWindowAutomator.handleStartupDialog(req.createNewModel, windowStepTimeout);
      steps.append(",{\"step\":\"startup_dialog\",\"ok\":").append(startup.ok())
        .append(",\"status\":\"").append(esc(startup.status()))
        .append("\",\"details\":\"").append(esc(startup.details())).append("\"}");

      if (req.createNewModel && !"handled".equals(startup.status())) {
        invokeNoArgOnEdt(appCtrl, "command_newModel");
        steps.append(",{\"step\":\"create_new_model\",\"ok\":true,\"status\":\"api_command_newModel\"}");
      } else if (req.createNewModel) {
        steps.append(",{\"step\":\"create_new_model\",\"ok\":true,\"status\":\"startup_dialog_new_clicked\"}");
      } else {
        steps.append(",{\"step\":\"create_new_model\",\"ok\":true,\"status\":\"disabled\"}");
      }
      steps.append("]");

      long elapsed = System.currentTimeMillis() - start;
      return "{\"ok\":true,\"flow\":\"startup_prepare\",\"license_mode\":\"" + esc(req.licenseMode) +
        "\",\"elapsed_ms\":" + elapsed + ",\"steps\":" + steps + "}\n";
    } catch (Throwable t) {
      return error("startup_failed", t.toString());
    }
  }

  private static Object waitForAppCtrl(long timeoutMs) throws Exception {
    long deadline = System.currentTimeMillis() + Math.max(2000L, timeoutMs);
    while (System.currentTimeMillis() < deadline) {
      try {
        Object appCtrl = CubismCommandAdapter.getAppCtrlForAgent();
        if (appCtrl != null) {
          return appCtrl;
        }
      } catch (Throwable ignored) {
        // app not ready yet
      }
      Thread.sleep(200L);
    }
    return null;
  }

  private static void invokeNoArgOnEdt(Object target, String methodName) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      invokeNoArg(target, methodName);
      return;
    }

    AtomicReference<Exception> error = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      try {
        invokeNoArg(target, methodName);
      } catch (Exception ex) {
        error.set(ex);
      }
    });
    if (error.get() != null) {
      throw error.get();
    }
  }

  private static void invokeNoArg(Object target, String methodName) throws Exception {
    Method m = target.getClass().getMethod(methodName);
    m.invoke(target);
  }

  private static Request parseRequest(String body) {
    String licenseMode = "free";
    boolean createNewModel = true;
    long waitTimeoutMs = 30000L;

    if (body != null && !body.isBlank()) {
      Matcher m1 = LICENSE_MODE.matcher(body);
      if (m1.find()) {
        String candidate = m1.group(1).trim().toLowerCase();
        if ("free".equals(candidate) || "pro".equals(candidate)) {
          licenseMode = candidate;
        }
      }

      Matcher m2 = CREATE_NEW_MODEL.matcher(body);
      if (m2.find()) {
        createNewModel = Boolean.parseBoolean(m2.group(1));
      }

      Matcher m3 = WAIT_TIMEOUT_MS.matcher(body);
      if (m3.find()) {
        try {
          waitTimeoutMs = Long.parseLong(m3.group(1));
        } catch (NumberFormatException ignored) {
          // keep default
        }
      }
    }

    return new Request(licenseMode, createNewModel, waitTimeoutMs);
  }

  private static String error(String code, String message) {
    return "{\"ok\":false,\"error\":\"" + esc(code) + "\",\"message\":\"" + esc(message) + "\"}\n";
  }

  private static String esc(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(value.length() + 16);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\\' -> sb.append("\\\\");
        case '"' -> sb.append("\\\"");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  private record Request(String licenseMode, boolean createNewModel, long waitTimeoutMs) {}
}
