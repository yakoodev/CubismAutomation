package com.live2d.cubism.agent;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

public final class CubismParameterAdapter {
  private static final Pattern STRING_FIELD = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"([^\"]*)\"");
  private static final Pattern NUMBER_FIELD = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
  private static final Pattern UPDATES_FIELD = Pattern.compile("\"updates\"\\s*:\\s*\\[(.*)]", Pattern.DOTALL);
  private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{[^{}]*}");
  private static final double VERIFY_EPSILON = 0.001;

  private CubismParameterAdapter() {}

  public static ApiResponse parametersList() {
    try {
      return onEdt(() -> {
        Ctx ctx = context();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }
        if (!ctx.modelingDocument) {
          return error(409, "guardrail_violation", "parameters api supports modeling document only");
        }
        if (ctx.modelSource == null) {
          return error(409, "no_model_source", "model source is null");
        }
        List<ParamInfo> params = readParameterInfos(ctx);
        return ok("{\"ok\":true,\"timestamp\":\"" + esc(Instant.now().toString()) + "\",\"count\":" + params.size() + ",\"parameters\":" + paramsJson(params) + "}\n");
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  public static ApiResponse parametersState() {
    try {
      return onEdt(() -> {
        Ctx ctx = context();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }
        if (!ctx.modelingDocument) {
          return error(409, "guardrail_violation", "parameters api supports modeling document only");
        }
        if (ctx.modelSource == null) {
          return error(409, "no_model_source", "model source is null");
        }
        List<ParamInfo> params = readParameterInfos(ctx);
        int withValues = 0;
        for (ParamInfo p : params) {
          if (p.value != null) {
            withValues++;
          }
        }
        return ok(
          "{\"ok\":true,\"count\":" + params.size() +
          ",\"values_ready\":" + withValues +
          ",\"instance_ready\":" + (ctx.parameterSet != null) +
          ",\"parameters\":" + paramsJson(params) + "}\n"
        );
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  public static ApiResponse parametersSet(String body) {
    try {
      return onEdt(() -> {
        Ctx ctx = context();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }
        if (!ctx.modelingDocument) {
          return error(409, "guardrail_violation", "parameters api supports modeling document only");
        }
        if (ctx.modelSource == null) {
          return error(409, "no_model_source", "model source is null");
        }
        if (ctx.parameterSet == null) {
          return error(409, "guardrail_violation", "parameter instance is not ready");
        }

        List<ParamUpdate> updates = parseUpdates(body);
        if (updates.isEmpty()) {
          return error(400, "invalid_request", "id/value or updates[] is required");
        }

        List<ParamInfo> infos = readParameterInfos(ctx);
        Map<String, ParamInfo> byId = new LinkedHashMap<>();
        for (ParamInfo p : infos) {
          byId.put(p.id, p);
        }

        List<String> itemJson = new ArrayList<>();
        boolean anyFailure = false;
        boolean anyNoEffect = false;
        for (ParamUpdate upd : updates) {
          if (upd.id == null || upd.id.isBlank() || upd.value == null) {
            anyFailure = true;
            itemJson.add("{\"id\":" + q(upd.id) + ",\"ok\":false,\"error\":\"invalid_request\",\"message\":\"id/value required\"}");
            continue;
          }
          ParamInfo current = byId.get(upd.id);
          if (current == null) {
            anyFailure = true;
            itemJson.add("{\"id\":" + q(upd.id) + ",\"ok\":false,\"error\":\"not_found\",\"message\":\"parameter not found\"}");
            continue;
          }
          if (current.minValue != null && upd.value < current.minValue - VERIFY_EPSILON) {
            anyFailure = true;
            itemJson.add(
              "{\"id\":" + q(upd.id) + ",\"ok\":false,\"error\":\"out_of_range\",\"expected_min\":" + n(current.minValue) +
              ",\"got\":" + n(upd.value) + "}"
            );
            continue;
          }
          if (current.maxValue != null && upd.value > current.maxValue + VERIFY_EPSILON) {
            anyFailure = true;
            itemJson.add(
              "{\"id\":" + q(upd.id) + ",\"ok\":false,\"error\":\"out_of_range\",\"expected_max\":" + n(current.maxValue) +
              ",\"got\":" + n(upd.value) + "}"
            );
            continue;
          }

          boolean applied = setParameterValue(ctx.parameterSet, upd.id, upd.value.floatValue());
          if (!applied) {
            anyFailure = true;
            itemJson.add("{\"id\":" + q(upd.id) + ",\"ok\":false,\"error\":\"unsupported_action\",\"message\":\"setValue failed\"}");
            continue;
          }

          Double after = readParameterValue(ctx.parameterSet, upd.id);
          if (after == null || Math.abs(after - upd.value) > VERIFY_EPSILON) {
            anyNoEffect = true;
            itemJson.add(
              "{\"id\":" + q(upd.id) + ",\"ok\":false,\"error\":\"no_effect\",\"requested\":" + n(upd.value) +
              ",\"actual\":" + n(after) + "}"
            );
            continue;
          }

          itemJson.add("{\"id\":" + q(upd.id) + ",\"ok\":true,\"value\":" + n(after) + "}");
        }

        int status = 200;
        String errorCode = null;
        if (anyFailure) {
          status = 400;
          errorCode = "partial_failed";
        }
        if (anyNoEffect) {
          status = 409;
          errorCode = "no_effect";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":").append(!(anyFailure || anyNoEffect));
        if (errorCode != null) {
          sb.append(",\"error\":").append(q(errorCode));
        }
        sb.append(",\"updated_count\":").append(updates.size());
        sb.append(",\"results\":[");
        for (int i = 0; i < itemJson.size(); i++) {
          if (i > 0) {
            sb.append(',');
          }
          sb.append(itemJson.get(i));
        }
        sb.append("]}\n");
        return new ApiResponse(status, sb.toString());
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  private static Ctx context() throws Exception {
    Object appCtrl = CubismCommandAdapter.getAppCtrlForAgent();
    Object doc = invokeNoArgSafe(appCtrl, "getCurrentDoc");
    Object modelSource = firstNonNull(
      invokeNoArgSafe(doc, "getModelSource"),
      invokeNoArgSafe(doc, "get_modelSource")
    );
    Object currentModel = invokeNoArgSafe(modelSource, "getCurrentInstance");
    Object parameterSet = invokeNoArgSafe(currentModel, "getParameterSet");
    String className = className(doc);
    boolean modelingDocument = className != null && className.toLowerCase().contains("modeling");
    return new Ctx(appCtrl, doc, modelSource, currentModel, parameterSet, modelingDocument);
  }

  private static List<ParamInfo> readParameterInfos(Ctx ctx) {
    List<Object> sources = asObjectList(invokeChain(ctx.modelSource, "getParameterSourceSet", "getSources"));
    Map<String, Double> valuesById = readParameterValues(ctx.parameterSet);
    List<ParamInfo> out = new ArrayList<>();
    for (Object source : sources) {
      String id = idToString(invokeNoArgSafe(source, "getId"));
      String name = asString(invokeNoArgSafe(source, "getName"));
      String description = asString(invokeNoArgSafe(source, "getDescription"));
      Double min = asDouble(invokeNoArgSafe(source, "getMinValue"));
      Double max = asDouble(invokeNoArgSafe(source, "getMaxValue"));
      Double def = asDouble(invokeNoArgSafe(source, "getDefaultValue"));
      Boolean repeat = asBool(invokeNoArgSafe(source, "isRepeat"));
      String type = safeToString(invokeNoArgSafe(source, "getParamType"));
      Double value = valuesById.get(id);
      out.add(new ParamInfo(id, name, description, min, max, def, repeat, type, value));
    }
    return out;
  }

  private static Map<String, Double> readParameterValues(Object parameterSet) {
    Map<String, Double> out = new LinkedHashMap<>();
    if (parameterSet == null) {
      return out;
    }
    List<Object> params = asObjectList(invokeNoArgSafe(parameterSet, "getParameters"));
    for (Object param : params) {
      String id = idToString(invokeNoArgSafe(param, "getId"));
      Double value = asDouble(invokeNoArgSafe(param, "getValue"));
      if (id != null && !id.isBlank()) {
        out.put(id, value);
      }
    }
    return out;
  }

  private static Double readParameterValue(Object parameterSet, String id) {
    if (parameterSet == null || id == null || id.isBlank()) {
      return null;
    }
    List<Object> params = asObjectList(invokeNoArgSafe(parameterSet, "getParameters"));
    for (Object param : params) {
      String pid = idToString(invokeNoArgSafe(param, "getId"));
      if (id.equals(pid)) {
        return asDouble(invokeNoArgSafe(param, "getValue"));
      }
    }
    return null;
  }

  private static boolean setParameterValue(Object parameterSet, String id, float value) {
    if (parameterSet == null || id == null || id.isBlank()) {
      return false;
    }
    List<Object> params = asObjectList(invokeNoArgSafe(parameterSet, "getParameters"));
    for (Object param : params) {
      String pid = idToString(invokeNoArgSafe(param, "getId"));
      if (!id.equals(pid)) {
        continue;
      }
      return invokeAny(
        List.of(param),
        new String[]{"setValue"},
        new Object[][]{{value}, {(double) value}, {(int) value}}
      );
    }
    return false;
  }

  private static List<ParamUpdate> parseUpdates(String body) {
    List<ParamUpdate> out = new ArrayList<>();
    if (body == null) {
      return out;
    }
    Matcher updates = UPDATES_FIELD.matcher(body);
    if (updates.find()) {
      String inner = updates.group(1);
      Matcher objMatcher = OBJECT_PATTERN.matcher(inner);
      while (objMatcher.find()) {
        String obj = objMatcher.group();
        String id = firstNonBlank(parseString(obj, "id"), parseString(obj, "parameter_id"), parseString(obj, "param_id"));
        Double value = firstNonNull(parseNumber(obj, "value"), parseNumber(obj, "current_value"));
        out.add(new ParamUpdate(id, value));
      }
    }
    if (!out.isEmpty()) {
      return out;
    }
    String id = firstNonBlank(parseString(body, "id"), parseString(body, "parameter_id"), parseString(body, "param_id"));
    Double value = firstNonNull(parseNumber(body, "value"), parseNumber(body, "current_value"));
    if (id != null || value != null) {
      out.add(new ParamUpdate(id, value));
    }
    return out;
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

  private static Double parseNumber(String body, String field) {
    if (body == null) {
      return null;
    }
    Matcher m = NUMBER_FIELD.matcher(body);
    while (m.find()) {
      if (field.equals(m.group(1))) {
        try {
          return Double.parseDouble(m.group(2));
        } catch (NumberFormatException ignored) {
          return null;
        }
      }
    }
    return null;
  }

  private static String paramsJson(List<ParamInfo> params) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < params.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      ParamInfo p = params.get(i);
      sb.append('{');
      sb.append("\"id\":").append(q(p.id));
      sb.append(",\"name\":").append(q(p.name));
      sb.append(",\"description\":").append(q(p.description));
      sb.append(",\"param_type\":").append(q(p.paramType));
      sb.append(",\"repeat\":").append(p.repeat == null ? "null" : p.repeat.toString());
      sb.append(",\"min\":").append(n(p.minValue));
      sb.append(",\"max\":").append(n(p.maxValue));
      sb.append(",\"default\":").append(n(p.defaultValue));
      sb.append(",\"value\":").append(n(p.value));
      sb.append('}');
    }
    sb.append(']');
    return sb.toString();
  }

  private static ApiResponse ok(String json) {
    return new ApiResponse(200, json);
  }

  private static ApiResponse error(int status, String code, String message) {
    return new ApiResponse(status, "{\"ok\":false,\"error\":\"" + esc(code) + "\",\"message\":\"" + esc(message) + "\"}\n");
  }

  private static Object invokeChain(Object root, String firstMethod, String secondMethod) {
    Object first = invokeNoArgSafe(root, firstMethod);
    return invokeNoArgSafe(first, secondMethod);
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

  private static boolean invokeAny(List<Object> targets, String[] methodNames, Object[][] argSets) {
    for (Object target : targets) {
      if (target == null) {
        continue;
      }
      Class<?> type = target.getClass();
      for (String methodName : methodNames) {
        for (Object[] args : argSets) {
          if (tryInvoke(type, target, methodName, args)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean tryInvoke(Class<?> type, Object target, String methodName, Object[] args) {
    Method[] methods = type.getMethods();
    for (Method method : methods) {
      if (!method.getName().equals(methodName)) {
        continue;
      }
      Class<?>[] params = method.getParameterTypes();
      if (params.length != args.length) {
        continue;
      }
      Object[] converted = new Object[args.length];
      boolean ok = true;
      for (int i = 0; i < args.length; i++) {
        Object cv = convertArg(args[i], params[i]);
        if (cv == ArgFail.VALUE) {
          ok = false;
          break;
        }
        converted[i] = cv;
      }
      if (!ok) {
        continue;
      }
      try {
        method.invoke(target, converted);
        return true;
      } catch (Throwable ignored) {
        // keep trying candidates
      }
    }
    return false;
  }

  private static Object convertArg(Object value, Class<?> paramType) {
    if (value == null) {
      return paramType.isPrimitive() ? ArgFail.VALUE : null;
    }
    if (paramType.isInstance(value)) {
      return value;
    }
    if ((paramType == float.class || paramType == Float.class) && value instanceof Number n) {
      return n.floatValue();
    }
    if ((paramType == double.class || paramType == Double.class) && value instanceof Number n) {
      return n.doubleValue();
    }
    if ((paramType == int.class || paramType == Integer.class) && value instanceof Number n) {
      return n.intValue();
    }
    if ((paramType == long.class || paramType == Long.class) && value instanceof Number n) {
      return n.longValue();
    }
    if (paramType == String.class) {
      return String.valueOf(value);
    }
    return ArgFail.VALUE;
  }

  private enum ArgFail { VALUE }

  @SafeVarargs
  private static <T> T firstNonNull(T... values) {
    for (T v : values) {
      if (v != null) {
        return v;
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

  private static String idToString(Object idObj) {
    if (idObj == null) {
      return null;
    }
    String shortId = asString(invokeNoArgSafe(idObj, "getShortIdString"));
    if (shortId != null && !shortId.isBlank()) {
      return shortId;
    }
    String raw = safeToString(idObj);
    if (raw == null) {
      return null;
    }
    int idx = raw.lastIndexOf(':');
    if (idx >= 0 && idx + 1 < raw.length()) {
      return raw.substring(idx + 1).trim();
    }
    return raw.trim();
  }

  private static List<Object> asObjectList(Object value) {
    List<Object> out = new ArrayList<>();
    if (value == null) {
      return out;
    }
    if (value instanceof Collection<?> c) {
      out.addAll(c);
      return out;
    }
    if (value.getClass().isArray()) {
      int len = Array.getLength(value);
      for (int i = 0; i < len; i++) {
        out.add(Array.get(value, i));
      }
    }
    return out;
  }

  private static String asString(Object value) {
    if (value == null) {
      return null;
    }
    String s = String.valueOf(value);
    return s.isBlank() ? null : s;
  }

  private static Double asDouble(Object value) {
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    if (value == null) {
      return null;
    }
    try {
      return Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static Boolean asBool(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    if (value == null) {
      return null;
    }
    String s = String.valueOf(value).trim().toLowerCase();
    if ("true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s)) {
      return true;
    }
    if ("false".equals(s) || "0".equals(s) || "no".equals(s) || "off".equals(s)) {
      return false;
    }
    return null;
  }

  private static String className(Object obj) {
    return obj == null ? null : obj.getClass().getName();
  }

  private static String safeToString(Object obj) {
    if (obj == null) {
      return null;
    }
    try {
      return String.valueOf(obj);
    } catch (Throwable t) {
      return "<toString failed: " + t.getClass().getSimpleName() + ">";
    }
  }

  private static String q(String s) {
    return s == null ? "null" : "\"" + esc(s) + "\"";
  }

  private static String n(Double value) {
    return value == null ? "null" : String.valueOf(value);
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

  public record ApiResponse(int status, String json) {}

  private record ParamInfo(
    String id,
    String name,
    String description,
    Double minValue,
    Double maxValue,
    Double defaultValue,
    Boolean repeat,
    String paramType,
    Double value
  ) {}

  private record ParamUpdate(String id, Double value) {}

  private record Ctx(
    Object appCtrl,
    Object doc,
    Object modelSource,
    Object currentModel,
    Object parameterSet,
    boolean modelingDocument
  ) {}
}
