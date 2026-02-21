package com.live2d.cubism.agent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

public final class CubismCommandAdapter {
  private static final Pattern COMMAND_FIELD = Pattern.compile("\"command\"\\s*:\\s*\"([^\"]+)\"");
  private static final Map<String, CommandSpec> REGISTRY = buildRegistry();
  private static final Set<String> ALLOW_SET = parseCsvSet(System.getenv("CUBISM_AGENT_ALLOW_COMMANDS"));
  private static final Set<String> DENY_SET = parseCsvSet(System.getenv("CUBISM_AGENT_DENY_COMMANDS"));

  private CubismCommandAdapter() {}

  public static CommandResponse execute(String body) {
    try {
      String command = parseCommand(body);
      if (command == null || command.isBlank()) {
        return CommandResponse.badRequest("missing command");
      }
      CommandSpec spec = REGISTRY.get(command);
      if (spec == null) {
        return CommandResponse.badRequest("unsupported command: " + command);
      }
      if (!isCommandAllowed(command)) {
        return CommandResponse.forbidden("command denied by policy: " + command);
      }

      invokeOnEdt(spec);
      return CommandResponse.ok("{\"ok\":true,\"command\":\"" + escape(command) + "\",\"status\":\"executed\"}\n");
    } catch (Throwable t) {
      return CommandResponse.error(
        "{\"ok\":false,\"error\":\"command_failed\",\"message\":\"" + escape(t.toString()) + "\"}\n"
      );
    }
  }

  public static String supportedCommandsJson() {
    List<String> commands = REGISTRY.keySet().stream()
      .filter(CubismCommandAdapter::isCommandAllowed)
      .sorted()
      .toList();

    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < commands.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append('"').append(escape(commands.get(i))).append('"');
    }
    sb.append(']');
    return sb.toString();
  }

  private static Map<String, CommandSpec> buildRegistry() {
    Map<String, CommandSpec> map = new HashMap<>();
    map.put("cubism.zoom_in", CommandSpec.noArg("command_zoomIn"));
    map.put("cubism.zoom_out", CommandSpec.noArg("command_zoomOut"));
    map.put("cubism.zoom_reset", CommandSpec.noArg("command_setScaleOneByOne"));
    map.put("cubism.undo", CommandSpec.withCurrentDoc("command_undo"));
    map.put("cubism.redo", CommandSpec.withCurrentDoc("command_redo"));
    return map;
  }

  private static String parseCommand(String body) {
    if (body == null) {
      return null;
    }
    Matcher m = COMMAND_FIELD.matcher(body);
    if (m.find()) {
      return m.group(1).trim();
    }
    String trimmed = body.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static void invokeOnEdt(CommandSpec spec) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      invokeDirect(spec);
      return;
    }
    AtomicReference<Exception> failure = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      try {
        invokeDirect(spec);
      } catch (Exception ex) {
        failure.set(ex);
      }
    });
    if (failure.get() != null) {
      throw failure.get();
    }
  }

  private static void invokeDirect(CommandSpec spec) throws Exception {
    Object appCtrl = getAppCtrlForAgent();
    if (spec.usesCurrentDoc) {
      Object currentDoc = appCtrl.getClass().getMethod("getCurrentDoc").invoke(appCtrl);
      if (currentDoc == null) {
        throw new IllegalStateException("current document is null");
      }
      Method target = findOneArgMethod(appCtrl.getClass(), spec.methodName, currentDoc.getClass());
      if (target == null) {
        throw new NoSuchMethodException(spec.methodName + "(IDocument)");
      }
      target.invoke(appCtrl, currentDoc);
      return;
    }
    Method target = appCtrl.getClass().getMethod(spec.methodName);
    target.invoke(appCtrl);
  }

  static Object getAppCtrlForAgent() throws Exception {
    Class<?> appClass = Class.forName("com.live2d.cubism.CECubismEditorApp");
    Field singleton = appClass.getField("a");
    Object app = singleton.get(null);
    Method getCtrl = appClass.getMethod("a");
    Object appCtrl = getCtrl.invoke(app);
    if (appCtrl == null) {
      throw new IllegalStateException("CEAppCtrl is null");
    }
    return appCtrl;
  }

  private static boolean isCommandAllowed(String command) {
    if (DENY_SET.contains(command)) {
      return false;
    }
    return ALLOW_SET.isEmpty() || ALLOW_SET.contains(command);
  }

  private static Set<String> parseCsvSet(String csv) {
    if (csv == null || csv.isBlank()) {
      return Collections.emptySet();
    }
    Set<String> out = new HashSet<>();
    for (String part : csv.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        out.add(trimmed);
      }
    }
    return out;
  }

  private static Method findOneArgMethod(Class<?> clazz, String methodName, Class<?> argType) {
    for (Method m : clazz.getMethods()) {
      if (!m.getName().equals(methodName) || m.getParameterCount() != 1) {
        continue;
      }
      Class<?> p = m.getParameterTypes()[0];
      if (p.isAssignableFrom(argType)) {
        return m;
      }
    }
    return null;
  }

  private static String escape(String value) {
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

  private record CommandSpec(String methodName, boolean usesCurrentDoc) {
    static CommandSpec noArg(String methodName) {
      return new CommandSpec(methodName, false);
    }

    static CommandSpec withCurrentDoc(String methodName) {
      return new CommandSpec(methodName, true);
    }
  }

  public record CommandResponse(int status, String json) {
    static CommandResponse ok(String json) {
      return new CommandResponse(200, json);
    }

    static CommandResponse badRequest(String message) {
      return new CommandResponse(
        400,
        "{\"ok\":false,\"error\":\"bad_request\",\"message\":\"" + escape(message) + "\"}\n"
      );
    }

    static CommandResponse forbidden(String message) {
      return new CommandResponse(
        403,
        "{\"ok\":false,\"error\":\"forbidden\",\"message\":\"" + escape(message) + "\"}\n"
      );
    }

    static CommandResponse error(String json) {
      return new CommandResponse(500, json);
    }
  }
}
