package com.live2d.cubism.agent;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

public final class CubismDeformerAdapter {
  private static final Pattern STRING_FIELD = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"([^\"]*)\"");

  private CubismDeformerAdapter() {}

  public static ApiResponse deformersList() {
    try {
      return onEdt(() -> {
        Ctx ctx = context();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }
        if (!ctx.modelingDocument) {
          return error(409, "guardrail_violation", "deformer api supports modeling document only");
        }
        if (ctx.modelSource == null) {
          return error(409, "no_model_source", "model source is null");
        }
        List<DeformerInfo> list = readDeformers(ctx, null);
        return ok("{\"ok\":true,\"timestamp\":\"" + esc(Instant.now().toString()) + "\",\"count\":" + list.size() + ",\"deformers\":" + deformersJson(list) + "}\n");
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  public static ApiResponse deformersState() {
    try {
      return onEdt(() -> {
        Ctx ctx = context();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }
        if (!ctx.modelingDocument) {
          return error(409, "guardrail_violation", "deformer api supports modeling document only");
        }
        if (ctx.modelSource == null) {
          return error(409, "no_model_source", "model source is null");
        }
        Object active = activeDeformer(ctx);
        DeformerInfo activeInfo = active == null ? null : snapshot(active, true);
        List<DeformerInfo> list = readDeformers(ctx, active);
        return ok("{\"ok\":true,\"count\":" + list.size() + ",\"active_deformer\":" + deformerJson(activeInfo) + ",\"deformers\":" + deformersJson(list) + "}\n");
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  public static ApiResponse deformerSelect(String body) {
    try {
      return onEdt(() -> {
        Ctx ctx = context();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }
        if (!ctx.modelingDocument) {
          return error(409, "guardrail_violation", "deformer api supports modeling document only");
        }
        if (ctx.modelSource == null) {
          return error(409, "no_model_source", "model source is null");
        }
        DeformerRef ref = parseRef(body);
        Object target = findDeformer(ctx, ref);
        if (target == null) {
          return error(404, "not_found", "deformer not found");
        }
        boolean selected = select(ctx, target);
        if (!selected) {
          return error(400, "unsupported_action", "select deformer not supported");
        }
        Object active = activeDeformer(ctx);
        if (active == null || !same(active, target)) {
          return error(409, "no_effect", "select request did not become active selection");
        }
        return ok("{\"ok\":true,\"action\":\"select\",\"deformer\":" + deformerJson(snapshot(active, true)) + "}\n");
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  public static ApiResponse deformerRename(String body) {
    try {
      return onEdt(() -> {
        Ctx ctx = context();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }
        if (!ctx.modelingDocument) {
          return error(409, "guardrail_violation", "deformer api supports modeling document only");
        }
        if (ctx.modelSource == null) {
          return error(409, "no_model_source", "model source is null");
        }
        String newName = parseString(body, "new_name");
        if (newName == null || newName.isBlank()) {
          return error(400, "invalid_request", "new_name is required");
        }
        DeformerRef ref = parseRef(body);
        Object target = findDeformer(ctx, ref);
        if (target == null) {
          return error(404, "not_found", "deformer not found");
        }
        boolean renamed = invokeAny(List.of(target), new String[]{"setLocalName", "setName"}, new Object[][]{{newName}});
        if (!renamed) {
          return error(400, "unsupported_action", "rename deformer not supported");
        }
        String after = asString(invokeNoArgSafe(target, "getLocalName"));
        if (after == null || !after.equals(newName)) {
          return error(409, "no_effect", "rename request did not apply");
        }
        return ok("{\"ok\":true,\"action\":\"rename\",\"deformer\":" + deformerJson(snapshot(target, same(activeDeformer(ctx), target))) + "}\n");
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  private static Ctx context() throws Exception {
    Object appCtrl = CubismCommandAdapter.getAppCtrlForAgent();
    Object doc = invokeNoArgSafe(appCtrl, "getCurrentDoc");
    Object viewCtx = invokeNoArgSafe(appCtrl, "getCurrentViewContext");
    Object modelSource = firstNonNull(invokeNoArgSafe(doc, "getModelSource"), invokeNoArgSafe(doc, "get_modelSource"));
    String className = className(doc);
    boolean modelingDocument = className != null && className.toLowerCase().contains("modeling");
    return new Ctx(appCtrl, doc, viewCtx, modelSource, modelingDocument);
  }

  private static List<DeformerInfo> readDeformers(Ctx ctx, Object active) {
    List<Object> list = asObjectList(invokeNoArgSafe(ctx.modelSource, "getAllDeformers"));
    List<DeformerInfo> out = new ArrayList<>();
    for (Object d : list) {
      out.add(snapshot(d, same(d, active)));
    }
    return out;
  }

  private static Object activeDeformer(Ctx ctx) {
    Object sel = firstNonNull(
      invokeNoArgSafe(ctx.doc, "getSelection"),
      invokeNoArgSafe(ctx.doc, "getSelector"),
      invokeNoArgSafe(ctx.viewCtx, "getSelection"),
      invokeNoArgSafe(ctx.viewCtx, "getSelector")
    );
    Object selected = firstNonNull(
      invokeNoArgSafe(sel, "getSelectedObjects"),
      invokeNoArgSafe(ctx.doc, "getSelectedObjects"),
      invokeNoArgSafe(ctx.viewCtx, "getSelectedObjects")
    );
    for (Object item : asObjectList(selected)) {
      if (isDeformer(item)) {
        return item;
      }
    }
    Object single = firstNonNull(
      invokeNoArgSafe(sel, "getSelectedObject"),
      invokeNoArgSafe(ctx.doc, "getSelectedObject"),
      invokeNoArgSafe(ctx.viewCtx, "getSelectedObject")
    );
    return isDeformer(single) ? single : null;
  }

  private static Object findDeformer(Ctx ctx, DeformerRef ref) {
    Object active = activeDeformer(ctx);
    if (matches(ref, active)) {
      return active;
    }
    for (Object d : asObjectList(invokeNoArgSafe(ctx.modelSource, "getAllDeformers"))) {
      if (matches(ref, d)) {
        return d;
      }
    }
    return null;
  }

  private static boolean matches(DeformerRef ref, Object deformer) {
    if (ref == null || deformer == null) {
      return false;
    }
    DeformerInfo info = snapshot(deformer, false);
    if (ref.id != null && !ref.id.isBlank() && ref.id.equals(info.id)) {
      return true;
    }
    return ref.name != null && !ref.name.isBlank() && ref.name.equals(info.name);
  }

  private static boolean select(Ctx ctx, Object target) {
    Object selector = firstNonNull(
      invokeNoArgSafe(ctx.doc, "getSelection"),
      invokeNoArgSafe(ctx.doc, "getSelector"),
      invokeNoArgSafe(ctx.viewCtx, "getSelection"),
      invokeNoArgSafe(ctx.viewCtx, "getSelector")
    );
    return invokeAny(
      List.of(ctx.doc, selector, ctx.viewCtx, ctx.appCtrl),
      new String[]{"setSelectedObject", "setSelection", "select", "setSelectedObjects"},
      new Object[][]{
        {target},
        {new Object[]{target}},
        {ctx.doc, target}
      }
    );
  }

  private static DeformerInfo snapshot(Object d, boolean active) {
    String id = idToString(invokeNoArgSafe(d, "getId"));
    String name = asString(invokeNoArgSafe(d, "getLocalName"));
    String type = asString(invokeNoArgSafe(d, "getTypeName"));
    Boolean visible = asBool(invokeNoArgSafe(d, "isVisible"));
    Boolean locked = asBool(invokeNoArgSafe(d, "isLocked"));
    String targetId = idToString(invokeNoArgSafe(d, "getTargetDeformerId"));
    Integer keyforms = countOf(invokeNoArgSafe(d, "getKeyforms"));
    return new DeformerInfo(id, name, type, visible, locked, targetId, keyforms, className(d), active);
  }

  private static String deformersJson(List<DeformerInfo> list) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(deformerJson(list.get(i)));
    }
    sb.append(']');
    return sb.toString();
  }

  private static String deformerJson(DeformerInfo d) {
    if (d == null) {
      return "null";
    }
    return "{\"id\":" + q(d.id) +
      ",\"name\":" + q(d.name) +
      ",\"type\":" + q(d.type) +
      ",\"visible\":" + b(d.visible) +
      ",\"locked\":" + b(d.locked) +
      ",\"target_deformer_id\":" + q(d.targetDeformerId) +
      ",\"keyform_count\":" + i(d.keyformCount) +
      ",\"class_name\":" + q(d.className) +
      ",\"active\":" + d.active + "}";
  }

  private static DeformerRef parseRef(String body) {
    String id = firstNonBlank(parseString(body, "deformer_id"), parseString(body, "id"));
    String name = firstNonBlank(parseString(body, "deformer_name"), parseString(body, "name"));
    return new DeformerRef(id, name);
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

  private static ApiResponse ok(String json) {
    return new ApiResponse(200, json);
  }

  private static ApiResponse error(int status, String code, String message) {
    return new ApiResponse(status, "{\"ok\":false,\"error\":\"" + esc(code) + "\",\"message\":\"" + esc(message) + "\"}\n");
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
    for (Method method : type.getMethods()) {
      if (!method.getName().equals(methodName)) {
        continue;
      }
      if (method.getParameterCount() != args.length) {
        continue;
      }
      Object[] converted = new Object[args.length];
      Class<?>[] p = method.getParameterTypes();
      boolean ok = true;
      for (int i = 0; i < args.length; i++) {
        Object c = convert(args[i], p[i]);
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
        method.invoke(target, converted);
        return true;
      } catch (Throwable ignored) {
        // continue
      }
    }
    return false;
  }

  private static Object convert(Object value, Class<?> type) {
    if (value == null) {
      return type.isPrimitive() ? ArgFail.VALUE : null;
    }
    if (type.isInstance(value)) {
      return value;
    }
    if ((type == boolean.class || type == Boolean.class) && value instanceof Boolean b) {
      return b;
    }
    if ((type == int.class || type == Integer.class) && value instanceof Number n) {
      return n.intValue();
    }
    if ((type == float.class || type == Float.class) && value instanceof Number n) {
      return n.floatValue();
    }
    if ((type == double.class || type == Double.class) && value instanceof Number n) {
      return n.doubleValue();
    }
    if (type == String.class) {
      return String.valueOf(value);
    }
    if (type.isArray() && value.getClass().isArray()) {
      return value;
    }
    return ArgFail.VALUE;
  }

  private enum ArgFail { VALUE }

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

  private static Integer countOf(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Collection<?> c) {
      return c.size();
    }
    if (value.getClass().isArray()) {
      return Array.getLength(value);
    }
    return null;
  }

  private static boolean isDeformer(Object value) {
    String cls = className(value);
    return cls != null && cls.toLowerCase().contains("deformer");
  }

  private static boolean same(Object a, Object b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    String aid = idToString(invokeNoArgSafe(a, "getId"));
    String bid = idToString(invokeNoArgSafe(b, "getId"));
    if (aid != null && bid != null && aid.equals(bid)) {
      return true;
    }
    Object ag = invokeNoArgSafe(a, "getGuid");
    Object bg = invokeNoArgSafe(b, "getGuid");
    return ag != null && bg != null && ag.equals(bg);
  }

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
    String raw = asString(idObj);
    if (raw == null) {
      return null;
    }
    int idx = raw.lastIndexOf(':');
    if (idx >= 0 && idx + 1 < raw.length()) {
      return raw.substring(idx + 1).trim();
    }
    return raw.trim();
  }

  private static String className(Object value) {
    return value == null ? null : value.getClass().getName();
  }

  private static String asString(Object value) {
    if (value == null) {
      return null;
    }
    String s = String.valueOf(value);
    return s.isBlank() ? null : s;
  }

  private static Boolean asBool(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    if (value == null) {
      return null;
    }
    String s = String.valueOf(value).trim().toLowerCase();
    if ("true".equals(s) || "1".equals(s)) {
      return true;
    }
    if ("false".equals(s) || "0".equals(s)) {
      return false;
    }
    return null;
  }

  private static String q(String s) {
    return s == null ? "null" : "\"" + esc(s) + "\"";
  }

  private static String b(Boolean v) {
    return v == null ? "null" : v.toString();
  }

  private static String i(Integer v) {
    return v == null ? "null" : String.valueOf(v);
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

  private record Ctx(Object appCtrl, Object doc, Object viewCtx, Object modelSource, boolean modelingDocument) {}
  private record DeformerRef(String id, String name) {}
  private record DeformerInfo(
    String id,
    String name,
    String type,
    Boolean visible,
    Boolean locked,
    String targetDeformerId,
    Integer keyformCount,
    String className,
    boolean active
  ) {}
}
