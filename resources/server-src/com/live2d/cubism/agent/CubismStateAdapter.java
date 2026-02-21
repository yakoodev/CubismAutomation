package com.live2d.cubism.agent;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

public final class CubismStateAdapter {
  private CubismStateAdapter() {}

  public static String stateAllJson() {
    try {
      Snapshot s = snapshot();
      return "{\"ok\":true,\"timestamp\":\"" + esc(Instant.now().toString()) + "\"," +
        "\"project\":" + toJson(s.project) + "," +
        "\"document\":" + toJson(s.document) + "," +
        "\"selection\":" + toJson(s.selection) + "}\n";
    } catch (Throwable t) {
      return "{\"ok\":false,\"error\":\"state_failed\",\"message\":\"" + esc(t.toString()) + "\"}\n";
    }
  }

  public static String stateProjectJson() {
    try {
      Snapshot s = snapshot();
      return "{\"ok\":true,\"project\":" + toJson(s.project) + "}\n";
    } catch (Throwable t) {
      return "{\"ok\":false,\"error\":\"state_failed\",\"message\":\"" + esc(t.toString()) + "\"}\n";
    }
  }

  public static String stateDocumentJson() {
    try {
      Snapshot s = snapshot();
      return "{\"ok\":true,\"document\":" + toJson(s.document) + "}\n";
    } catch (Throwable t) {
      return "{\"ok\":false,\"error\":\"state_failed\",\"message\":\"" + esc(t.toString()) + "\"}\n";
    }
  }

  public static String stateSelectionJson() {
    try {
      Snapshot s = snapshot();
      return "{\"ok\":true,\"selection\":" + toJson(s.selection) + "}\n";
    } catch (Throwable t) {
      return "{\"ok\":false,\"error\":\"state_failed\",\"message\":\"" + esc(t.toString()) + "\"}\n";
    }
  }

  private static Snapshot snapshot() throws Exception {
    return onEdt(() -> {
      Object appCtrl = CubismCommandAdapter.getAppCtrlForAgent();
      Object project = invokeNoArg(appCtrl, "getCurrentProject");
      Object doc = invokeNoArg(appCtrl, "getCurrentDoc");
      Object viewCtx = invokeNoArg(appCtrl, "getCurrentViewContext");
      State projectState = describeTarget(project, "project");
      State docState = describeTarget(doc, "document");
      State selectionState = describeSelection(doc, viewCtx);
      return new Snapshot(projectState, docState, selectionState);
    });
  }

  private static State describeSelection(Object doc, Object viewCtx) {
    Object selectionObj = firstNonNull(
      invokeNoArgSafe(doc, "getSelection"),
      invokeNoArgSafe(doc, "getSelector"),
      invokeNoArgSafe(viewCtx, "getSelection"),
      invokeNoArgSafe(viewCtx, "getSelector")
    );
    Object selected = firstNonNull(
      invokeNoArgSafe(selectionObj, "getSelectedObjects"),
      invokeNoArgSafe(doc, "getSelectedObjects"),
      invokeNoArgSafe(viewCtx, "getSelectedObjects")
    );
    Integer count = countOf(selected);
    if (count == null) {
      count = firstNonNullInt(
        intFromMethod(selectionObj, "getSelectedCount"),
        intFromMethod(selectionObj, "getSelectionCount"),
        intFromMethod(doc, "getSelectedCount"),
        intFromMethod(doc, "getSelectionCount")
      );
    }

    StringBuilder extra = new StringBuilder();
    extra.append("\"selectionObjectClass\":").append(q(className(selectionObj))).append(",");
    extra.append("\"selectedContainerClass\":").append(q(className(selected))).append(",");
    extra.append("\"count\":").append(count == null ? "null" : count.toString());
    return new State("selection", selectionObj != null || selected != null, className(selectionObj), extra.toString());
  }

  private static State describeTarget(Object obj, String kind) {
    StringBuilder extra = new StringBuilder();
    extra.append("\"toString\":").append(q(obj == null ? null : safeToString(obj)));
    return new State(kind, obj != null, className(obj), extra.toString());
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

  private static Integer intFromMethod(Object target, String methodName) {
    try {
      Object v = invokeNoArg(target, methodName);
      if (v instanceof Number n) {
        return n.intValue();
      }
      return null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Integer countOf(Object container) {
    if (container == null) {
      return null;
    }
    if (container instanceof Collection<?> c) {
      return c.size();
    }
    if (container.getClass().isArray()) {
      return Array.getLength(container);
    }
    return null;
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

  private static Integer firstNonNullInt(Integer... values) {
    for (Integer v : values) {
      if (v != null) {
        return v;
      }
    }
    return null;
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

  private static String toJson(State s) {
    return "{\"kind\":" + q(s.kind) + "," +
      "\"present\":" + s.present + "," +
      "\"className\":" + q(s.className) + "," +
      s.extraJson + "}";
  }

  private static String className(Object obj) {
    return obj == null ? null : obj.getClass().getName();
  }

  private static String safeToString(Object obj) {
    try {
      return String.valueOf(obj);
    } catch (Throwable t) {
      return "<toString failed: " + t.getClass().getSimpleName() + ">";
    }
  }

  private static String q(String s) {
    return s == null ? "null" : "\"" + esc(s) + "\"";
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

  private record State(String kind, boolean present, String className, String extraJson) {}
  private record Snapshot(State project, State document, State selection) {}
}
