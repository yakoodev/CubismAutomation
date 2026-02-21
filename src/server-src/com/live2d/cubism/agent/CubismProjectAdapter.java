package com.live2d.cubism.agent;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

public final class CubismProjectAdapter {
  private static final Pattern STRING_FIELD = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"([^\"]*)\"");
  private static final Pattern BOOL_FIELD = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);

  private CubismProjectAdapter() {}

  public static ApiResponse projectOpen(String body) {
    try {
      return onEdt(() -> {
        Request req = parseRequest(body);
        if (req.path == null || req.path.isBlank()) {
          return error(400, "invalid_path", "path is required");
        }
        Path target;
        try {
          target = Paths.get(req.path).toAbsolutePath().normalize();
        } catch (Throwable t) {
          return error(400, "invalid_path", "path is invalid");
        }

        File file = target.toFile();
        if (!file.exists()) {
          return error(404, "not_found", "file does not exist");
        }
        if (!file.isFile()) {
          return error(400, "invalid_path", "path must be a file");
        }
        if (!isSupportedExtension(file.getName())) {
          return error(400, "unsupported_extension", "supported: .cmo3, .can3, .cmox, .model3.json");
        }

        Object appCtrl = CubismCommandAdapter.getAppCtrlForAgent();
        Object beforeDoc = invokeNoArgSafe(appCtrl, "getCurrentDoc");
        boolean openInvoked = invokeOpen(appCtrl, file, req.closeCurrentFirst);
        if (!openInvoked) {
          return error(400, "unsupported_action", "open command is not available");
        }

        Object afterDoc = invokeNoArgSafe(appCtrl, "getCurrentDoc");
        if (afterDoc == null) {
          return error(409, "no_effect", "open did not produce current document");
        }
        Object afterFile = invokeNoArgSafe(afterDoc, "getFile");
        String openedPath = afterFile instanceof File f ? f.getAbsolutePath() : null;
        if (openedPath != null && !openedPath.isBlank()) {
          Path openedFilePath = Paths.get(openedPath).toAbsolutePath().normalize();
          if (!openedFilePath.equals(target)) {
            // keep non-fatal; some flows open derived file/project wrapper
            return ok(
              "{\"ok\":true,\"action\":\"project_open\",\"warning\":\"opened_path_mismatch\",\"requested_path\":\"" + esc(target.toString()) +
              "\",\"opened_path\":\"" + esc(openedFilePath.toString()) + "\",\"document_class\":\"" + esc(afterDoc.getClass().getName()) + "\"}\n"
            );
          }
        } else if (beforeDoc == afterDoc) {
          return error(409, "no_effect", "document did not change and opened file is not exposed");
        }

        return ok(
          "{\"ok\":true,\"action\":\"project_open\",\"requested_path\":\"" + esc(target.toString()) +
          "\",\"opened_path\":\"" + esc(openedPath) + "\",\"document_class\":\"" + esc(afterDoc.getClass().getName()) + "\"}\n"
        );
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  private static boolean invokeOpen(Object appCtrl, File file, boolean closeCurrentFirst) {
    if (appCtrl == null) {
      return false;
    }
    try {
      if (closeCurrentFirst) {
        invokeNoArgSafe(appCtrl, "command_closeAll");
      }
      Method m = appCtrl.getClass().getMethod("command_open", File.class, boolean.class);
      m.invoke(appCtrl, file, false);
      return true;
    } catch (Throwable ignored) {
      // fallback candidates
    }
    return invokeAny(
      appCtrl,
      new String[]{"command_open", "command_open_targetFile"},
      new Object[][]{
        {file, false},
        {file},
        {buildTargetMap(file)}
      }
    );
  }

  private static java.util.Map<Object, Object> buildTargetMap(File file) {
    java.util.HashMap<Object, Object> map = new java.util.HashMap<>();
    map.put("targetFile", file);
    map.put("path", file.getAbsolutePath());
    return map;
  }

  private static boolean invokeAny(Object target, String[] methods, Object[][] args) {
    for (String name : methods) {
      for (Object[] callArgs : args) {
        if (tryInvoke(target, name, callArgs)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean tryInvoke(Object target, String methodName, Object[] args) {
    for (Method m : target.getClass().getMethods()) {
      if (!m.getName().equals(methodName) || m.getParameterCount() != args.length) {
        continue;
      }
      Class<?>[] types = m.getParameterTypes();
      Object[] converted = new Object[args.length];
      boolean ok = true;
      for (int i = 0; i < args.length; i++) {
        Object c = convertArg(args[i], types[i]);
        if (c == ArgFail.VALUE) {
          ok = false;
          break;
        }
        converted[i] = c;
      }
      if (!ok) {
        continue;
      }
      try {
        m.invoke(target, converted);
        return true;
      } catch (Throwable ignored) {
        // continue
      }
    }
    return false;
  }

  private static Object convertArg(Object value, Class<?> type) {
    if (value == null) {
      return type.isPrimitive() ? ArgFail.VALUE : null;
    }
    if (type.isInstance(value)) {
      return value;
    }
    if ((type == boolean.class || type == Boolean.class) && value instanceof Boolean b) {
      return b;
    }
    if (type == String.class) {
      return String.valueOf(value);
    }
    return ArgFail.VALUE;
  }

  private enum ArgFail { VALUE }

  private static boolean isSupportedExtension(String name) {
    if (name == null) {
      return false;
    }
    String n = name.toLowerCase(Locale.ROOT);
    return n.endsWith(".cmo3") || n.endsWith(".can3") || n.endsWith(".cmox") || n.endsWith(".model3.json");
  }

  private static Request parseRequest(String body) {
    String path = firstNonBlank(
      parseString(body, "path"),
      parseString(body, "file_path"),
      parseString(body, "target_path")
    );
    Boolean closeCurrent = parseBool(body, "close_current_first");
    return new Request(path, Boolean.TRUE.equals(closeCurrent));
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

  private static Boolean parseBool(String body, String field) {
    if (body == null) {
      return null;
    }
    Matcher m = BOOL_FIELD.matcher(body);
    while (m.find()) {
      if (field.equals(m.group(1))) {
        return Boolean.parseBoolean(m.group(2));
      }
    }
    return null;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }

  private static Object invokeNoArgSafe(Object target, String methodName) {
    try {
      return invokeNoArg(target, methodName);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Object invokeNoArg(Object target, String methodName) throws Exception {
    if (target == null) {
      return null;
    }
    Method m = target.getClass().getMethod(methodName);
    return m.invoke(target);
  }

  private static <T> T onEdt(Callable<T> action) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return action.call();
    }
    AtomicReference<T> value = new AtomicReference<>();
    AtomicReference<Exception> error = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      try {
        value.set(action.call());
      } catch (Exception e) {
        error.set(e);
      }
    });
    if (error.get() != null) {
      throw error.get();
    }
    return value.get();
  }

  private static ApiResponse ok(String json) {
    return new ApiResponse(200, json);
  }

  private static ApiResponse error(int status, String code, String message) {
    return new ApiResponse(status, "{\"ok\":false,\"error\":\"" + esc(code) + "\",\"message\":\"" + esc(message) + "\"}\n");
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

  public record ApiResponse(int status, String json) {}

  private record Request(String path, boolean closeCurrentFirst) {}
}
