package com.live2d.cubism.agent;

import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.awt.event.KeyEvent;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;

public final class StartupWindowAutomator {
  private static final String WORD_NEW_RU = "\u043d\u043e\u0432\u0430\u044f";
  private static final String WORD_NEW_JP = "\u65b0\u898f";

  private static final List<String> LICENSE_WINDOW_HINTS = List.of(
    "license", "licence", "activation", "pro", "free", "cubism"
  );
  private static final List<String> STARTUP_WINDOW_HINTS = List.of(
    "start", "startup", "welcome", "recent", "open", "new", "cubism"
  );

  private static final List<String> FREE_BUTTON_HINTS = List.of("free", "\u7121\u6599", "\u0431\u0435\u0441\u043f\u043b\u0430\u0442");
  private static final List<String> PRO_BUTTON_HINTS = List.of("pro", "\u30d7\u30ed");
  private static final List<String> NEW_BUTTON_HINTS = List.of("new", WORD_NEW_RU, WORD_NEW_JP);
  private static final List<String> CONFIRM_BUTTON_HINTS = List.of(
    "ok", "start", "continue", "next", "use", "apply", "select"
  );
  private static final List<String> POST_LICENSE_BUTTON_HINTS = List.of(
    "ok", "continue", "next", "start", "close", "agree", "accept", "skip", "later"
  );

  private StartupWindowAutomator() {}

  public static Result handleLicenseDialog(String licenseMode, long timeoutMs) {
    long deadline = System.currentTimeMillis() + Math.max(1000L, timeoutMs);
    String mode = "pro".equalsIgnoreCase(licenseMode) ? "pro" : "free";
    while (System.currentTimeMillis() < deadline) {
      try {
        ActionOutcome o = runOnEdt(() -> tryHandleLicenseDialog(mode));
        if (o.handled) {
          return new Result(true, "handled", o.details);
        }
      } catch (Exception ex) {
        return new Result(false, "error", ex.toString());
      }

      sleep(180L);
    }
    return new Result(false, "not_found", snapshotWindows());
  }

  public static Result handleStartupDialog(boolean createNewModel, long timeoutMs) {
    if (!createNewModel) {
      return new Result(true, "skipped", "create_new_model=false");
    }
    long deadline = System.currentTimeMillis() + Math.max(1000L, timeoutMs);
    while (System.currentTimeMillis() < deadline) {
      try {
        ActionOutcome o = runOnEdt(StartupWindowAutomator::tryHandleStartupDialog);
        if (o.handled) {
          return new Result(true, "handled", o.details);
        }
      } catch (Exception ex) {
        return new Result(false, "error", ex.toString());
      }
      sleep(180L);
    }
    return new Result(false, "not_found", snapshotWindows());
  }

  public static Result handlePostLicenseDialog(long timeoutMs) {
    long deadline = System.currentTimeMillis() + Math.max(800L, timeoutMs);
    while (System.currentTimeMillis() < deadline) {
      try {
        ActionOutcome o = runOnEdt(StartupWindowAutomator::tryHandlePostLicenseDialog);
        if (o.handled) {
          return new Result(true, "handled", o.details);
        }
      } catch (Exception ex) {
        return new Result(false, "error", ex.toString());
      }
      sleep(150L);
    }
    return new Result(true, "skipped", "not_found");
  }

  public static Result forceCreateNewModel(long timeoutMs) {
    long deadline = System.currentTimeMillis() + Math.max(1200L, timeoutMs);
    while (System.currentTimeMillis() < deadline) {
      try {
        ActionOutcome startupOutcome = runOnEdt(StartupWindowAutomator::tryHandleStartupDialog);
        if (startupOutcome.handled) {
          return new Result(true, "startup_dialog_clicked", startupOutcome.details);
        }
      } catch (Exception ignored) {
        // continue to keyboard fallback
      }

      if (keyboardGlobalNewModelFallback()) {
        return new Result(true, "keyboard_ctrl_n", "focused_main_window=true");
      }
      sleep(180L);
    }
    return new Result(false, "not_handled", snapshotWindows());
  }

  private static ActionOutcome tryHandleLicenseDialog(String mode) {
    for (Window window : Window.getWindows()) {
      if (window == null || !window.isShowing()) {
        continue;
      }

      List<AbstractButton> buttons = collectButtons(window);
      if (buttons.isEmpty()) {
        continue;
      }

      // License dialog can be titled just "Start"; prefer control-based detection.
      boolean titleMatch = isCandidateWindow(window, LICENSE_WINDOW_HINTS);
      List<String> modeHints = "pro".equals(mode) ? PRO_BUTTON_HINTS : FREE_BUTTON_HINTS;
      AbstractButton modeButton = findButtonByHints(buttons, modeHints);
      if (!titleMatch && modeButton == null) {
        continue;
      }

      if (modeButton != null && modeButton.isEnabled()) {
        click(modeButton);
      }

      AbstractButton confirm = findButtonByHints(buttons, CONFIRM_BUTTON_HINTS);
      if (confirm != null && confirm.isEnabled()) {
        click(confirm);
        return new ActionOutcome(
          true,
          "window=" + safeTitle(window) + ";clicked=" + text(confirm) + ";buttons=" + buttonTexts(buttons)
        );
      }

      if (modeButton != null && modeButton.isEnabled()) {
        click(modeButton);
        return new ActionOutcome(
          true,
          "window=" + safeTitle(window) + ";clicked_mode_only=" + text(modeButton) + ";buttons=" + buttonTexts(buttons)
        );
      }

      // Fallback for custom-drawn dialogs (no Swing buttons exposed)
      if (titleMatch && keyboardLicenseFallback(window, mode)) {
        return new ActionOutcome(true, "window=" + safeTitle(window) + ";keyboard_fallback=true");
      }
    }
    return ActionOutcome.notHandled();
  }

  private static ActionOutcome tryHandleStartupDialog() {
    for (Window window : Window.getWindows()) {
      if (!isCandidateWindow(window, STARTUP_WINDOW_HINTS)) {
        continue;
      }

      List<AbstractButton> buttons = collectButtons(window);
      if (buttons.isEmpty()) {
        continue;
      }

      AbstractButton newButton = findButtonByHints(buttons, NEW_BUTTON_HINTS);
      if (newButton != null && newButton.isEnabled()) {
        click(newButton);
        return new ActionOutcome(
          true,
          "window=" + safeTitle(window) + ";clicked=" + text(newButton) + ";buttons=" + buttonTexts(buttons)
        );
      }

      if (keyboardStartupFallback(window)) {
        return new ActionOutcome(true, "window=" + safeTitle(window) + ";keyboard_fallback=true");
      }
    }
    return ActionOutcome.notHandled();
  }

  private static ActionOutcome tryHandlePostLicenseDialog() {
    for (Window window : Window.getWindows()) {
      if (window == null || !window.isShowing()) {
        continue;
      }
      if (!(window instanceof java.awt.Dialog)) {
        continue;
      }

      List<AbstractButton> buttons = collectButtons(window);
      AbstractButton confirm = findButtonByHints(buttons, POST_LICENSE_BUTTON_HINTS);
      if (confirm != null && confirm.isEnabled()) {
        click(confirm);
        return new ActionOutcome(
          true,
          "window=" + safeTitle(window) + ";clicked=" + text(confirm) + ";buttons=" + buttonTexts(buttons)
        );
      }

      if (keyboardGenericConfirmFallback(window)) {
        return new ActionOutcome(true, "window=" + safeTitle(window) + ";keyboard_fallback=true");
      }
    }
    return ActionOutcome.notHandled();
  }

  private static boolean keyboardLicenseFallback(Window window, String mode) {
    try {
      focusWindow(window);
      Robot r = new Robot();
      r.setAutoDelay(80);

      // Try basic navigation:
      // 1) move to license choice controls
      for (int i = 0; i < 3; i++) {
        tap(r, KeyEvent.VK_TAB);
      }

      // 2) pick mode
      if ("free".equals(mode)) {
        tap(r, KeyEvent.VK_LEFT);
      } else {
        tap(r, KeyEvent.VK_RIGHT);
      }
      tap(r, KeyEvent.VK_SPACE);

      // 3) confirm
      tap(r, KeyEvent.VK_ENTER);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static boolean keyboardStartupFallback(Window window) {
    try {
      focusWindow(window);
      Robot r = new Robot();
      r.setAutoDelay(80);

      // Try "N" shortcut for New, then Enter.
      tap(r, KeyEvent.VK_N);
      tap(r, KeyEvent.VK_ENTER);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static boolean keyboardGenericConfirmFallback(Window window) {
    try {
      focusWindow(window);
      Robot r = new Robot();
      r.setAutoDelay(80);
      tap(r, KeyEvent.VK_ENTER);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static boolean keyboardGlobalNewModelFallback() {
    try {
      Frame target = findMainFrame();
      if (target == null) {
        return false;
      }
      focusWindow(target);
      Robot r = new Robot();
      r.setAutoDelay(80);
      r.keyPress(KeyEvent.VK_CONTROL);
      tap(r, KeyEvent.VK_N);
      r.keyRelease(KeyEvent.VK_CONTROL);
      tap(r, KeyEvent.VK_ENTER);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static Frame findMainFrame() {
    Frame best = null;
    for (Frame frame : Frame.getFrames()) {
      if (frame == null || !frame.isShowing()) {
        continue;
      }
      String title = normalize(frame.getTitle());
      if (title.contains("live2d") || title.contains("cubism")) {
        return frame;
      }
      if (best == null) {
        best = frame;
      }
    }
    return best;
  }

  private static void focusWindow(Window window) {
    try {
      runOnEdt(() -> {
        window.toFront();
        window.requestFocus();
        return null;
      });
      sleep(120L);
    } catch (Exception ignored) {
      // best effort
    }
  }

  private static void tap(Robot r, int key) {
    r.keyPress(key);
    r.keyRelease(key);
  }

  private static boolean isCandidateWindow(Window w, List<String> hints) {
    if (w == null || !w.isShowing()) {
      return false;
    }
    String t = normalize(safeTitle(w));
    if (t.isEmpty()) {
      return false;
    }
    for (String hint : hints) {
      if (t.contains(normalize(hint))) {
        return true;
      }
    }
    return false;
  }

  private static String safeTitle(Window w) {
    try {
      if (w instanceof java.awt.Frame f) {
        return nullToEmpty(f.getTitle());
      }
      if (w instanceof java.awt.Dialog d) {
        return nullToEmpty(d.getTitle());
      }
      return nullToEmpty(w.getName());
    } catch (Throwable ignored) {
      return "";
    }
  }

  private static List<AbstractButton> collectButtons(Component root) {
    List<AbstractButton> out = new ArrayList<>();
    collectButtonsRec(root, out);
    return out;
  }

  private static void collectButtonsRec(Component c, List<AbstractButton> out) {
    if (c == null) {
      return;
    }
    if (c instanceof AbstractButton b) {
      out.add(b);
    }
    if (c instanceof Container ct) {
      for (Component child : ct.getComponents()) {
        collectButtonsRec(child, out);
      }
    }
  }

  private static AbstractButton findButtonByHints(List<AbstractButton> buttons, List<String> hints) {
    for (AbstractButton b : buttons) {
      String txt = normalize(text(b));
      if (txt.isEmpty()) {
        continue;
      }
      for (String hint : hints) {
        if (txt.contains(normalize(hint))) {
          return b;
        }
      }
    }
    return null;
  }

  private static String text(AbstractButton b) {
    String t = b.getText();
    if (t == null || t.isBlank()) {
      t = b.getActionCommand();
    }
    return nullToEmpty(t);
  }

  private static void click(AbstractButton b) {
    if (!b.isEnabled()) {
      return;
    }
    b.requestFocusInWindow();
    b.doClick();
  }

  private static String snapshotWindows() {
    try {
      return runOnEdt(() -> {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (Window w : Window.getWindows()) {
          if (!w.isShowing()) {
            continue;
          }
          if (!first) {
            sb.append(", ");
          }
          first = false;
          List<AbstractButton> buttons = collectButtons(w);
          sb.append("{title=").append(safeTitle(w)).append(",buttons=").append(buttonTexts(buttons)).append("}");
        }
        sb.append("]");
        return sb.toString();
      });
    } catch (Exception ex) {
      return "snapshot_error:" + ex;
    }
  }

  private static <T> T runOnEdt(ThrowingSupplier<T> action) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return action.get();
    }
    AtomicReference<T> out = new AtomicReference<>();
    AtomicReference<Exception> err = new AtomicReference<>();
    try {
      SwingUtilities.invokeAndWait(() -> {
        try {
          out.set(action.get());
        } catch (Exception e) {
          err.set(e);
        }
      });
    } catch (InterruptedException | InvocationTargetException e) {
      throw new Exception(e);
    }
    if (err.get() != null) {
      throw err.get();
    }
    return out.get();
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private static String normalize(String s) {
    return nullToEmpty(s).toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String buttonTexts(List<AbstractButton> buttons) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    boolean first = true;
    for (AbstractButton b : buttons) {
      String t = text(b);
      if (t.isBlank()) {
        continue;
      }
      if (!first) {
        sb.append(", ");
      }
      first = false;
      sb.append(t);
    }
    sb.append("]");
    return sb.toString();
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  private record ActionOutcome(boolean handled, String details) {
    static ActionOutcome notHandled() {
      return new ActionOutcome(false, "");
    }
  }

  public record Result(boolean ok, String status, String details) {}
}
