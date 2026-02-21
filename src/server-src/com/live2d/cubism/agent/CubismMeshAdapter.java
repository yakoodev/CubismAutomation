package com.live2d.cubism.agent;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

public final class CubismMeshAdapter {
  private static final Pattern STRING_FIELD = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"([^\"]*)\"");
  private static final Pattern BOOL_FIELD = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
  private static final Pattern OPS_FIELD = Pattern.compile("\"operations\"\\s*:\\s*\\[(.*)]", Pattern.DOTALL);

  private CubismMeshAdapter() {}

  public static ApiResponse meshList() {
    try {
      return onEdt(() -> {
        DocContext ctx = docContext();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }
        List<MeshSnapshot> meshes = readMeshes(ctx.doc, ctx.activeMesh);
        return ok("{\"ok\":true,\"timestamp\":\"" + esc(Instant.now().toString()) + "\",\"meshes\":" + meshesJson(meshes) + "}\n");
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  public static ApiResponse meshActive() {
    try {
      return onEdt(() -> {
        DocContext ctx = docContext();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }
        Object active = ctx.activeMesh;
        if (active == null) {
          return error(409, "no_selected_mesh", "active mesh is null");
        }
        MeshSnapshot mesh = snapshot(active, true);
        return ok("{\"ok\":true,\"active_mesh\":" + meshJson(mesh) + "}\n");
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  public static ApiResponse meshState() {
    try {
      return onEdt(() -> {
        DocContext ctx = docContext();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }
        List<MeshSnapshot> meshes = readMeshes(ctx.doc, ctx.activeMesh);
        return ok(
          "{\"ok\":true,\"mesh_edit_mode\":" + boolOrNull(isMeshEditMode(ctx.appCtrl, ctx.doc)) +
          ",\"active_mesh\":" + meshJson(ctx.activeMesh == null ? null : snapshot(ctx.activeMesh, true)) +
          ",\"meshes\":" + meshesJson(meshes) + "}\n"
        );
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  public static ApiResponse meshSelect(String body) {
    try {
      return onEdt(() -> {
        DocContext ctx = docContext();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }

        MeshRef ref = parseMeshRef(body);
        Object target = resolveTargetMesh(ctx, ref);
        if (target == null) {
          return error(409, "no_selected_mesh", "target mesh not found");
        }

        if (!selectMesh(ctx, target)) {
          return error(400, "unsupported_action", "select mesh not supported");
        }
        MeshSnapshot selected = snapshot(target, true);
        return ok("{\"ok\":true,\"action\":\"select\",\"mesh\":" + meshJson(selected) + "}\n");
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  public static ApiResponse meshRename(String body) {
    try {
      return onEdt(() -> {
        DocContext ctx = docContext();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }
        String newName = parseString(body, "new_name");
        if (newName == null || newName.isBlank()) {
          return error(400, "invalid_request", "new_name is required");
        }
        Object target = resolveTargetMesh(ctx, parseMeshRef(body));
        if (target == null) {
          return error(409, "no_selected_mesh", "target mesh not found");
        }
        boolean renamed = setStringProp(target, newName, "setName", "setMeshName", "rename");
        if (!renamed) {
          return error(400, "unsupported_action", "rename mesh not supported");
        }
        return ok("{\"ok\":true,\"action\":\"rename\",\"mesh\":" + meshJson(snapshot(target, false)) + "}\n");
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  public static ApiResponse meshVisibility(String body) {
    try {
      return onEdt(() -> {
        DocContext ctx = docContext();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }
        Boolean value = parseBool(body, "visible");
        if (value == null) {
          return error(400, "invalid_request", "visible is required");
        }
        Object target = resolveTargetMesh(ctx, parseMeshRef(body));
        if (target == null) {
          return error(409, "no_selected_mesh", "target mesh not found");
        }

        boolean applied = setBoolProp(target, value, "setVisible", "setMeshVisible");
        if (!applied) {
          applied = setBoolProp(target, !value, "setHidden");
        }
        if (!applied) {
          return error(400, "unsupported_action", "visibility not supported");
        }
        return ok("{\"ok\":true,\"action\":\"visibility\",\"mesh\":" + meshJson(snapshot(target, false)) + "}\n");
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  public static ApiResponse meshLock(String body) {
    return meshBoolMutation(body, "locked", "lock", "setLocked", "setLock", "setMeshLocked");
  }

  public static ApiResponse meshOps(String body) {
    try {
      return onEdt(() -> {
        DocContext ctx = docContext();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }

        Boolean validateOnly = parseBool(body, "validate_only");
        boolean dryRun = validateOnly != null && validateOnly;
        List<String> rawOps = parseOperationObjects(body);
        if (rawOps.isEmpty()) {
          return error(400, "invalid_request", "operations[] is required");
        }

        Boolean modeOk = isMeshEditMode(ctx.appCtrl, ctx.doc);
        if (Boolean.FALSE.equals(modeOk)) {
          return error(409, "guardrail_violation", "mesh operations are not allowed in current document mode");
        }
        MeshRef defaultRef = parseMeshRef(body);

        List<String> reports = new ArrayList<>();
        int executed = 0;
        for (int i = 0; i < rawOps.size(); i++) {
          String opObj = rawOps.get(i);
          String op = normalizeOpName(parseString(opObj, "op"));
          MeshRef ref = parseMeshRef(opObj);
          if (ref.isEmpty()) {
            ref = defaultRef;
          }
          if (op == null) {
            reports.add(opReport(i, null, "error", "invalid_request", "missing op"));
            continue;
          }

          if (!ref.isEmpty()) {
            Object target = resolveTargetMesh(ctx, ref);
            if (target == null) {
              reports.add(opReport(i, op, "error", "unsupported_action", "mesh not found"));
              continue;
            }
            if (!dryRun && !selectMesh(ctx, target)) {
              reports.add(opReport(i, op, "error", "unsupported_action", "select mesh not supported"));
              continue;
            }
          }

          List<String> methods = opMethodCandidates(op);
          if (methods.isEmpty()) {
            reports.add(opReport(i, op, "error", "unsupported_action", "unsupported op"));
            continue;
          }
          if (dryRun) {
            reports.add(opReport(i, op, "validated", null, null));
            continue;
          }
          boolean invoked = invokeAction(ctx, methods);
          if (!invoked) {
            reports.add(opReport(i, op, "error", "unsupported_action", "operation not supported by current Cubism build"));
            continue;
          }
          executed++;
          reports.add(opReport(i, op, "executed", null, null));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":true,\"validate_only\":").append(dryRun);
        sb.append(",\"executed\":").append(executed);
        sb.append(",\"results\":[");
        for (int i = 0; i < reports.size(); i++) {
          if (i > 0) {
            sb.append(',');
          }
          sb.append(reports.get(i));
        }
        sb.append("]}\n");
        return ok(sb.toString());
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  private static ApiResponse meshBoolMutation(String body, String field, String action, String... methodCandidates) {
    try {
      return onEdt(() -> {
        DocContext ctx = docContext();
        if (ctx.doc == null) {
          return error(409, "no_document", "current document is null");
        }
        Boolean value = parseBool(body, field);
        if (value == null) {
          return error(400, "invalid_request", field + " is required");
        }
        Object target = resolveTargetMesh(ctx, parseMeshRef(body));
        if (target == null) {
          return error(409, "no_selected_mesh", "target mesh not found");
        }

        boolean applied;
        if ("setHidden".equals(methodCandidates[methodCandidates.length - 1])) {
          applied = setBoolProp(target, !value, methodCandidates);
        } else {
          applied = setBoolProp(target, value, methodCandidates);
        }
        if (!applied) {
          return error(400, "unsupported_action", action + " not supported");
        }
        return ok("{\"ok\":true,\"action\":\"" + action + "\",\"mesh\":" + meshJson(snapshot(target, false)) + "}\n");
      });
    } catch (Throwable t) {
      return error(500, "operation_failed", t.toString());
    }
  }

  private static List<MeshSnapshot> readMeshes(Object doc, Object activeMesh) {
    List<Object> meshObjects = discoverMeshObjects(doc, null, activeMesh);
    List<MeshSnapshot> out = new ArrayList<>(meshObjects.size());
    for (Object mesh : meshObjects) {
      out.add(snapshot(mesh, sameMesh(mesh, activeMesh)));
    }
    return out;
  }

  private static MeshSnapshot snapshot(Object mesh, boolean active) {
    if (mesh == null) {
      return null;
    }
    String id = firstNonBlank(
      asString(invokeNoArgSafe(mesh, "getId")),
      asString(invokeNoArgSafe(mesh, "getMeshId")),
      asString(invokeNoArgSafe(mesh, "getUID"))
    );
    String name = firstNonBlank(
      asString(invokeNoArgSafe(mesh, "getName")),
      asString(invokeNoArgSafe(mesh, "getMeshName")),
      asString(invokeNoArgSafe(mesh, "getDisplayName"))
    );
    Boolean visible = firstNonNullBool(
      asBool(invokeNoArgSafe(mesh, "isVisible")),
      asBool(invokeNoArgSafe(mesh, "getVisible")),
      invert(asBool(invokeNoArgSafe(mesh, "isHidden")))
    );
    Boolean locked = firstNonNullBool(
      asBool(invokeNoArgSafe(mesh, "isLocked")),
      asBool(invokeNoArgSafe(mesh, "getLocked")),
      asBool(invokeNoArgSafe(mesh, "isLock"))
    );
    return new MeshSnapshot(id, name, visible, locked, mesh.getClass().getName(), active);
  }

  private static Object resolveTargetMesh(DocContext ctx, MeshRef ref) {
    if (ref == null || ref.isEmpty()) {
      return ctx.activeMesh;
    }
    Object found = findMesh(ctx.doc, ctx.viewCtx, ref, ctx.activeMesh);
    if (found != null) {
      return found;
    }
    found = tryResolveMeshViaSelectors(ctx, ref);
    if (found != null) {
      return found;
    }
    if (ref.matchesSnapshot(snapshot(ctx.activeMesh, true))) {
      return ctx.activeMesh;
    }
    return null;
  }

  private static Object tryResolveMeshViaSelectors(DocContext ctx, MeshRef ref) {
    List<Object> targets = List.of(ctx.doc, ctx.viewCtx, ctx.appCtrl);

    Object byId = queryAny(
      targets,
      new String[]{"getMeshById", "getArtMeshById", "findMeshById", "findArtMeshById", "getDrawableById", "findDrawableById"},
      ref.meshId
    );
    if (isMeshObject(byId)) {
      return byId;
    }

    Object byName = queryAny(
      targets,
      new String[]{"getMeshByName", "getArtMeshByName", "findMeshByName", "findArtMeshByName", "getDrawableByName", "findDrawableByName"},
      ref.meshName
    );
    if (isMeshObject(byName)) {
      return byName;
    }

    if (ref.meshId != null && !ref.meshId.isBlank()) {
      invokeAny(
        targets,
        new String[]{"selectMeshById", "setActiveMeshById", "setCurrentMeshById", "command_selectMeshById", "command_selectMesh"},
        new Object[][]{{ref.meshId}, {ctx.doc, ref.meshId}}
      );
    }
    if (ref.meshName != null && !ref.meshName.isBlank()) {
      invokeAny(
        targets,
        new String[]{"selectMeshByName", "setActiveMeshByName", "setCurrentMeshByName", "command_selectMeshByName", "command_selectMesh"},
        new Object[][]{{ref.meshName}, {ctx.doc, ref.meshName}}
      );
    }

    Object active = firstNonNull(
      invokeNoArgSafe(ctx.doc, "getActiveMesh"),
      invokeNoArgSafe(ctx.doc, "getCurrentMesh"),
      invokeNoArgSafe(ctx.doc, "getSelectedMesh"),
      invokeNoArgSafe(ctx.viewCtx, "getActiveMesh"),
      invokeNoArgSafe(ctx.viewCtx, "getSelectedMesh"),
      findActiveFromSelection(ctx.doc, ctx.viewCtx)
    );
    if (isMeshObject(active) && ref.matchesSnapshot(snapshot(active, true))) {
      return active;
    }

    return null;
  }

  private static Object findMesh(Object doc, MeshRef ref) {
    return findMesh(doc, null, ref, null);
  }

  private static Object findMesh(Object doc, Object viewCtx, MeshRef ref, Object activeMesh) {
    if (ref == null || ref.isEmpty()) {
      return null;
    }
    for (Object mesh : discoverMeshObjects(doc, viewCtx, activeMesh)) {
      MeshSnapshot snap = snapshot(mesh, false);
      if (ref.matchesSnapshot(snap)) {
        return mesh;
      }
    }
    return null;
  }

  private static List<Object> discoverMeshObjects(Object doc, Object viewCtx, Object activeMesh) {
    LinkedHashMap<String, Object> unique = new LinkedHashMap<>();
    IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();

    // Strong candidates first.
    addMeshesFromCandidate(unique, visited, invokeNoArgSafe(doc, "getMeshes"));
    addMeshesFromCandidate(unique, visited, invokeNoArgSafe(doc, "getMeshList"));
    addMeshesFromCandidate(unique, visited, invokeNoArgSafe(doc, "getArtMeshes"));
    addMeshesFromCandidate(unique, visited, invokeNoArgSafe(doc, "getDrawableMeshes"));
    addMeshesFromCandidate(unique, visited, invokeNoArgSafe(doc, "getSelectedObjects"));

    if (viewCtx != null) {
      addMeshesFromCandidate(unique, visited, invokeNoArgSafe(viewCtx, "getMeshes"));
      addMeshesFromCandidate(unique, visited, invokeNoArgSafe(viewCtx, "getMeshList"));
      addMeshesFromCandidate(unique, visited, invokeNoArgSafe(viewCtx, "getDrawableMeshes"));
      addMeshesFromCandidate(unique, visited, invokeNoArgSafe(viewCtx, "getSelectedObjects"));
    }

    // Reflective fallback for Cubism builds with different method names.
    addMeshesFromNoArgMethods(unique, visited, doc);
    if (viewCtx != null) {
      addMeshesFromNoArgMethods(unique, visited, viewCtx);
    }

    if (activeMesh != null && isMeshObject(activeMesh)) {
      addMesh(unique, activeMesh);
    }

    return new ArrayList<>(unique.values());
  }

  private static void addMeshesFromNoArgMethods(LinkedHashMap<String, Object> unique, IdentityHashMap<Object, Boolean> visited, Object target) {
    if (target == null) {
      return;
    }
    for (Method method : target.getClass().getMethods()) {
      if (method.getParameterCount() != 0) {
        continue;
      }
      String name = method.getName().toLowerCase();
      if (!(name.contains("mesh") || name.contains("drawable") || name.contains("selection"))) {
        continue;
      }
      Object result = invokeNoArgSafe(target, method.getName());
      addMeshesFromCandidate(unique, visited, result);
    }
  }

  private static void addMeshesFromCandidate(LinkedHashMap<String, Object> unique, IdentityHashMap<Object, Boolean> visited, Object candidate) {
    for (Object obj : flattenDeep(candidate, 4, visited)) {
      if (isMeshObject(obj)) {
        addMesh(unique, obj);
      }
    }
  }

  private static void addMesh(LinkedHashMap<String, Object> unique, Object mesh) {
    if (mesh == null) {
      return;
    }
    MeshSnapshot snap = snapshot(mesh, false);
    String key = meshKey(snap, mesh);
    unique.putIfAbsent(key, mesh);
  }

  private static String meshKey(MeshSnapshot snap, Object mesh) {
    if (snap != null && snap.id != null && !snap.id.isBlank()) {
      return "id:" + snap.id;
    }
    if (snap != null && snap.name != null && !snap.name.isBlank()) {
      return "name:" + snap.name;
    }
    return "obj:" + System.identityHashCode(mesh);
  }

  private static boolean isMeshObject(Object obj) {
    if (obj == null) {
      return false;
    }
    String className = obj.getClass().getName().toLowerCase();
    if (className.contains("artmesh")) {
      return true;
    }
    if (className.contains("mesh") && !className.contains("selector") && !className.contains("editmode")) {
      return true;
    }
    return false;
  }

  private static boolean selectMesh(DocContext ctx, Object mesh) {
    if (mesh == null) {
      return false;
    }
    if (sameMesh(ctx.activeMesh, mesh)) {
      return true;
    }

    Object selector = firstNonNull(
      invokeNoArgSafe(ctx.doc, "getSelection"),
      invokeNoArgSafe(ctx.doc, "getSelector"),
      invokeNoArgSafe(ctx.viewCtx, "getSelection"),
      invokeNoArgSafe(ctx.viewCtx, "getSelector")
    );

    return invokeAny(
      List.of(ctx.doc, selector, ctx.viewCtx, ctx.appCtrl),
      new String[]{"setActiveMesh", "setCurrentMesh", "setSelectedMesh", "select", "setSelection", "setSelectedObject", "command_selectMesh"},
      new Object[][]{
        {mesh},
        {ctx.doc, mesh}
      }
    );
  }

  private static boolean invokeAction(DocContext ctx, List<String> methods) {
    return invokeAny(
      List.of(ctx.appCtrl, ctx.doc, ctx.viewCtx),
      methods.toArray(new String[0]),
      new Object[][]{
        {},
        {ctx.doc},
        {ctx.activeMesh},
        {ctx.doc, ctx.activeMesh}
      }
    );
  }

  private static List<String> opMethodCandidates(String op) {
    return switch (op) {
      case "auto_mesh" -> List.of("command_autoMesh", "command_createAutoMesh", "command_generateMesh");
      case "divide" -> List.of("command_divideMesh", "command_meshDivide");
      case "connect" -> List.of("command_connectMesh", "command_meshConnect");
      case "reset_shape" -> List.of("command_resetMeshShape", "command_meshResetShape", "command_resetShape");
      case "fit_contour" -> List.of("command_fitMeshContour", "command_meshFitContour", "command_fitContour");
      default -> List.of();
    };
  }

  private static String normalizeOpName(String op) {
    if (op == null) {
      return null;
    }
    String normalized = op.trim().toLowerCase().replace('-', '_');
    return switch (normalized) {
      case "automesh", "auto_mesh", "auto" -> "auto_mesh";
      case "divide", "mesh_divide" -> "divide";
      case "connect", "mesh_connect" -> "connect";
      case "resetshape", "reset_shape", "mesh_reset_shape" -> "reset_shape";
      case "fitcontour", "fit_contour", "mesh_fit_contour" -> "fit_contour";
      default -> normalized;
    };
  }

  private static Boolean isMeshEditMode(Object appCtrl, Object doc) {
    Object mode = firstNonNull(
      invokeNoArgSafe(appCtrl, "getEditMode"),
      invokeNoArgSafe(doc, "getEditMode"),
      invokeNoArgSafe(appCtrl, "getCurrentMode"),
      invokeNoArgSafe(doc, "getCurrentMode")
    );
    String modeText = mode == null ? null : mode.toString().toLowerCase();
    if (modeText != null) {
      if (modeText.contains("mesh")) {
        return true;
      }
      if (modeText.contains("anim") || modeText.contains("timeline")) {
        return false;
      }
    }
    Boolean explicit = firstNonNullBool(
      asBool(invokeNoArgSafe(appCtrl, "isMeshEditMode")),
      asBool(invokeNoArgSafe(doc, "isMeshEditMode"))
    );
    return explicit;
  }

  private static DocContext docContext() throws Exception {
    Object appCtrl = CubismCommandAdapter.getAppCtrlForAgent();
    Object doc = invokeNoArgSafe(appCtrl, "getCurrentDoc");
    Object viewCtx = invokeNoArgSafe(appCtrl, "getCurrentViewContext");
    Object activeMesh = firstNonNull(
      invokeNoArgSafe(doc, "getActiveMesh"),
      invokeNoArgSafe(doc, "getCurrentMesh"),
      invokeNoArgSafe(doc, "getSelectedMesh"),
      invokeNoArgSafe(viewCtx, "getActiveMesh"),
      invokeNoArgSafe(viewCtx, "getSelectedMesh")
    );
    if (activeMesh == null) {
      activeMesh = findActiveFromSelection(doc, viewCtx);
    }
    return new DocContext(appCtrl, doc, viewCtx, activeMesh);
  }

  private static Object findActiveFromSelection(Object doc, Object viewCtx) {
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
    for (Object obj : flatten(selected)) {
      if (obj.getClass().getName().toLowerCase().contains("mesh")) {
        return obj;
      }
    }
    return null;
  }

  private static List<Object> flatten(Object container) {
    List<Object> out = new ArrayList<>();
    if (container == null) {
      return out;
    }
    if (container instanceof Collection<?> coll) {
      out.addAll(coll);
      return out;
    }
    if (container.getClass().isArray()) {
      int len = Array.getLength(container);
      for (int i = 0; i < len; i++) {
        out.add(Array.get(container, i));
      }
      return out;
    }
    out.add(container);
    return out;
  }

  private static List<Object> flattenDeep(Object container, int depth, IdentityHashMap<Object, Boolean> visited) {
    List<Object> out = new ArrayList<>();
    flattenDeep(container, depth, visited, out);
    return out;
  }

  private static void flattenDeep(Object value, int depth, IdentityHashMap<Object, Boolean> visited, List<Object> out) {
    if (value == null || depth < 0) {
      return;
    }
    if (visited.put(value, Boolean.TRUE) != null) {
      return;
    }

    if (value instanceof Collection<?> coll) {
      for (Object item : coll) {
        flattenDeep(item, depth - 1, visited, out);
      }
      return;
    }
    if (value instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        flattenDeep(item, depth - 1, visited, out);
      }
      return;
    }
    if (value instanceof Map<?, ?> map) {
      for (Object item : map.values()) {
        flattenDeep(item, depth - 1, visited, out);
      }
      return;
    }
    if (value.getClass().isArray()) {
      int len = Array.getLength(value);
      for (int i = 0; i < len; i++) {
        flattenDeep(Array.get(value, i), depth - 1, visited, out);
      }
      return;
    }

    out.add(value);
  }

  private static boolean setStringProp(Object target, String value, String... methodCandidates) {
    return invokeAny(List.of(target), methodCandidates, new Object[][]{{value}});
  }

  private static boolean setBoolProp(Object target, boolean value, String... methodCandidates) {
    return invokeAny(List.of(target), methodCandidates, new Object[][]{{value}});
  }

  private static boolean invokeAny(List<Object> targets, String[] methodCandidates, Object[][] argVariants) {
    for (Object target : targets) {
      if (target == null) {
        continue;
      }
      for (String methodName : methodCandidates) {
        for (Object[] args : argVariants) {
          if (invokeIfCompatible(target, methodName, args)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static Object queryAny(List<Object> targets, String[] methodCandidates, String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    for (Object target : targets) {
      if (target == null) {
        continue;
      }
      for (String methodName : methodCandidates) {
        Object result = invokeWithCompatibleArgs(target, methodName, new Object[]{value});
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  private static boolean invokeIfCompatible(Object target, String methodName, Object[] args) {
    for (Method method : target.getClass().getMethods()) {
      if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
        continue;
      }
      if (!isCompatible(method.getParameterTypes(), args)) {
        continue;
      }
      try {
        method.invoke(target, args);
        return true;
      } catch (Throwable ignored) {
        // try another candidate
      }
    }
    return false;
  }

  private static Object invokeWithCompatibleArgs(Object target, String methodName, Object[] args) {
    for (Method method : target.getClass().getMethods()) {
      if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
        continue;
      }
      if (!isCompatible(method.getParameterTypes(), args)) {
        continue;
      }
      try {
        return method.invoke(target, args);
      } catch (Throwable ignored) {
        // try another candidate
      }
    }
    return null;
  }

  private static boolean isCompatible(Class<?>[] paramTypes, Object[] args) {
    for (int i = 0; i < paramTypes.length; i++) {
      Object arg = args[i];
      if (arg == null) {
        if (paramTypes[i].isPrimitive()) {
          return false;
        }
        continue;
      }
      Class<?> p = wrapPrimitive(paramTypes[i]);
      if (!p.isAssignableFrom(arg.getClass())) {
        return false;
      }
    }
    return true;
  }

  private static Class<?> wrapPrimitive(Class<?> p) {
    if (!p.isPrimitive()) {
      return p;
    }
    if (p == boolean.class) {
      return Boolean.class;
    }
    if (p == int.class) {
      return Integer.class;
    }
    if (p == long.class) {
      return Long.class;
    }
    if (p == double.class) {
      return Double.class;
    }
    if (p == float.class) {
      return Float.class;
    }
    if (p == short.class) {
      return Short.class;
    }
    if (p == byte.class) {
      return Byte.class;
    }
    if (p == char.class) {
      return Character.class;
    }
    return p;
  }

  private static String opReport(int index, String op, String status, String errorCode, String message) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"index\":").append(index);
    sb.append(",\"op\":").append(q(op));
    sb.append(",\"status\":").append(q(status));
    if (errorCode != null) {
      sb.append(",\"error\":").append(q(errorCode));
    }
    if (message != null) {
      sb.append(",\"message\":").append(q(message));
    }
    sb.append('}');
    return sb.toString();
  }

  private static List<String> parseOperationObjects(String body) {
    if (body == null || body.isBlank()) {
      return List.of();
    }
    Matcher matcher = OPS_FIELD.matcher(body);
    if (!matcher.find()) {
      return List.of();
    }
    String content = matcher.group(1);
    List<String> out = new ArrayList<>();
    int depth = 0;
    int start = -1;
    for (int i = 0; i < content.length(); i++) {
      char c = content.charAt(i);
      if (c == '{') {
        if (depth == 0) {
          start = i;
        }
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0 && start >= 0) {
          out.add(content.substring(start, i + 1));
          start = -1;
        }
      }
    }
    return out;
  }

  private static MeshRef parseMeshRef(String body) {
    String id = firstNonBlank(
      parseString(body, "mesh_id"),
      parseString(body, "id"),
      parseString(body, "target_id")
    );
    String name = firstNonBlank(
      parseString(body, "mesh_name"),
      parseString(body, "name"),
      parseString(body, "target_name")
    );
    return new MeshRef(id, name);
  }

  private static String parseString(String body, String key) {
    if (body == null) {
      return null;
    }
    Matcher m = STRING_FIELD.matcher(body);
    while (m.find()) {
      if (key.equals(m.group(1))) {
        return m.group(2);
      }
    }
    return null;
  }

  private static Boolean parseBool(String body, String key) {
    if (body == null) {
      return null;
    }
    Matcher m = BOOL_FIELD.matcher(body);
    while (m.find()) {
      if (key.equals(m.group(1))) {
        return Boolean.parseBoolean(m.group(2));
      }
    }
    return null;
  }

  private static Object invokeNoArgSafe(Object target, String methodName) {
    try {
      if (target == null) {
        return null;
      }
      Method m = target.getClass().getMethod(methodName);
      return m.invoke(target);
    } catch (Throwable ignored) {
      return null;
    }
  }

  @SafeVarargs
  private static <T> T firstNonNull(T... values) {
    for (T value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static Boolean firstNonNullBool(Boolean... values) {
    for (Boolean value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static Boolean invert(Boolean value) {
    return value == null ? null : !value;
  }

  private static boolean sameMesh(Object left, Object right) {
    if (left == null || right == null) {
      return false;
    }
    if (left == right) {
      return true;
    }
    MeshSnapshot a = snapshot(left, false);
    MeshSnapshot b = snapshot(right, false);
    if (a.id != null && b.id != null) {
      return a.id.equals(b.id);
    }
    if (a.name != null && b.name != null) {
      return a.name.equals(b.name);
    }
    return false;
  }

  private static Boolean asBool(Object value) {
    return value instanceof Boolean b ? b : null;
  }

  private static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
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
    return new ApiResponse(
      status,
      "{\"ok\":false,\"error\":\"" + esc(code) + "\",\"message\":\"" + esc(message) + "\"}\n"
    );
  }

  private static String meshesJson(List<MeshSnapshot> meshes) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < meshes.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(meshJson(meshes.get(i)));
    }
    sb.append(']');
    return sb.toString();
  }

  private static String meshJson(MeshSnapshot mesh) {
    if (mesh == null) {
      return "null";
    }
    return "{\"id\":" + q(mesh.id) +
      ",\"name\":" + q(mesh.name) +
      ",\"visible\":" + boolOrNull(mesh.visible) +
      ",\"locked\":" + boolOrNull(mesh.locked) +
      ",\"className\":" + q(mesh.className) +
      ",\"active\":" + mesh.active +
      "}";
  }

  private static String boolOrNull(Boolean value) {
    return value == null ? "null" : value.toString();
  }

  private static String q(String value) {
    return value == null ? "null" : "\"" + esc(value) + "\"";
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

  private record DocContext(Object appCtrl, Object doc, Object viewCtx, Object activeMesh) {}

  private record MeshRef(String meshId, String meshName) {
    boolean isEmpty() {
      return (meshId == null || meshId.isBlank()) && (meshName == null || meshName.isBlank());
    }

    boolean matches(String id, String name) {
      if (meshId != null && !meshId.isBlank() && id != null && meshId.equals(id)) {
        return true;
      }
      return meshName != null && !meshName.isBlank() && name != null && meshName.equals(name);
    }

    boolean matchesSnapshot(MeshSnapshot snap) {
      if (snap == null) {
        return false;
      }
      return matches(snap.id, snap.name);
    }
  }

  private record MeshSnapshot(String id, String name, Boolean visible, Boolean locked, String className, boolean active) {}

  public record ApiResponse(int status, String json) {}
}
