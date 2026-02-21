package com.live2d.cubism.agent;

import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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
        "\"selection\":" + toJson(s.selection) + "," +
        "\"ui\":" + toJson(s.ui) + "}\n";
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

  public static String stateUiJson() {
    try {
      Snapshot s = snapshot();
      return "{\"ok\":true,\"ui\":" + toJson(s.ui) + "}\n";
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
      State uiState = describeUi(doc);
      return new Snapshot(projectState, docState, selectionState, uiState);
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

  private static State describeUi(Object doc) {
    Window focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    Window targetWindow = firstNonNull(
      focusedWindow,
      activeWindow,
      findCubismWindow(true),
      findCubismWindow(false)
    );
    Component workspace = targetWindow == null ? null : findWorkspaceComponent(targetWindow);

    StringBuilder extra = new StringBuilder();
    extra.append("\"documentPresent\":").append(doc != null);
    extra.append(",\"focusedWindowClass\":").append(q(className(focusedWindow)));
    extra.append(",\"focusedWindowTitle\":").append(q(windowTitle(focusedWindow)));
    extra.append(",\"activeWindowClass\":").append(q(className(activeWindow)));
    extra.append(",\"activeWindowTitle\":").append(q(windowTitle(activeWindow)));
    extra.append(",\"captureWindowClass\":").append(q(className(targetWindow)));
    extra.append(",\"captureWindowTitle\":").append(q(windowTitle(targetWindow)));
    extra.append(",\"captureWindowBounds\":").append(rectJson(componentBoundsOnScreen(targetWindow)));
    extra.append(",\"workspaceClass\":").append(q(className(workspace)));
    extra.append(",\"workspaceBounds\":").append(rectJson(componentBoundsOnScreen(workspace)));
    extra.append(",\"showingWindowsCount\":").append(showingWindows().size());
    return new State("ui", targetWindow != null, className(targetWindow), extra.toString());
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

  private static List<Window> showingWindows() {
    List<Window> out = new ArrayList<>();
    for (Window w : Window.getWindows()) {
      if (w != null && w.isShowing() && w.getWidth() > 120 && w.getHeight() > 120) {
        out.add(w);
      }
    }
    return out;
  }

  private static Window findCubismWindow(boolean strict) {
    List<Window> windows = showingWindows();
    if (!strict) {
      return windows.stream()
        .max(Comparator.comparingInt(w -> w.getWidth() * w.getHeight()))
        .orElse(null);
    }
    return windows.stream()
      .filter(CubismStateAdapter::looksLikeCubismWindow)
      .max(Comparator.comparingInt(w -> w.getWidth() * w.getHeight()))
      .orElse(null);
  }

  private static boolean looksLikeCubismWindow(Window w) {
    String title = windowTitle(w);
    String className = className(w);
    String t = title == null ? "" : title.toLowerCase();
    String c = className == null ? "" : className.toLowerCase();
    return c.contains("cubism") || c.contains("live2d") || t.contains("cubism") || t.contains("live2d");
  }

  private static Component findWorkspaceComponent(Window window) {
    Component best = null;
    int bestScore = -1;
    for (Component c : allDescendants(window)) {
      if (c == null || !c.isShowing() || c.getWidth() < 80 || c.getHeight() < 80) {
        continue;
      }
      int area = c.getWidth() * c.getHeight();
      String n = c.getClass().getName().toLowerCase();
      int score = area;
      if (n.contains("canvas") || n.contains("view") || n.contains("editor") || n.contains("gl")) {
        score += 10_000_000;
      }
      if (score > bestScore) {
        bestScore = score;
        best = c;
      }
    }
    return best == null ? window : best;
  }

  private static List<Component> allDescendants(Container root) {
    List<Component> out = new ArrayList<>();
    List<Container> queue = new ArrayList<>();
    queue.add(root);
    for (int i = 0; i < queue.size(); i++) {
      Container current = queue.get(i);
      for (Component child : current.getComponents()) {
        out.add(child);
        if (child instanceof Container next) {
          queue.add(next);
        }
      }
    }
    return out;
  }

  private static Rectangle componentBoundsOnScreen(Component c) {
    if (c == null || c.getWidth() <= 0 || c.getHeight() <= 0) {
      return null;
    }
    try {
      Point p = c.getLocationOnScreen();
      return new Rectangle(p.x, p.y, c.getWidth(), c.getHeight());
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static String rectJson(Rectangle r) {
    if (r == null) {
      return "null";
    }
    return "{\"x\":" + r.x + ",\"y\":" + r.y + ",\"width\":" + r.width + ",\"height\":" + r.height + "}";
  }

  private static String windowTitle(Window w) {
    if (w instanceof Frame f) {
      return f.getTitle();
    }
    return null;
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
  private record Snapshot(State project, State document, State selection, State ui) {}
}
