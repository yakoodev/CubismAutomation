import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

public class PatchMethodStart {
  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      throw new IllegalArgumentException("Usage: PatchMethodStart <class-file> <methodName> <methodDesc>");
    }

    Path classFile = Paths.get(args[0]);
    String methodName = args[1];
    String methodDesc = args[2];

    byte[] bytes = Files.readAllBytes(classFile);
    ClassFile cf = ClassFile.of();
    ClassModel model = cf.parse(bytes);

    AtomicBoolean found = new AtomicBoolean(false);

    CodeTransform patchAtStart = CodeTransform.ofStateful(() -> new CodeTransform() {
      private boolean injected;
      @Override
      public void accept(CodeBuilder cb, CodeElement ce) {
        if (!injected) {
          injected = true;
          injectDesktopMarker(cb);
        }
        cb.with(ce);
      }
    });

    ClassTransform transform = ClassTransform.transformingMethodBodies(
      method -> {
        boolean hit = method.methodName().equalsString(methodName) && method.methodType().equalsString(methodDesc);
        if (hit) found.set(true);
        return hit;
      },
      patchAtStart
    );

    byte[] patched = cf.transformClass(model, transform);
    if (!found.get()) {
      throw new IllegalStateException("Method not found: " + methodName + methodDesc);
    }

    Files.write(classFile, patched);
    System.out.println("Patched: " + classFile + " -> " + methodName + methodDesc);
  }

  private static void injectDesktopMarker(CodeBuilder cb) {
    ClassDesc cdString = ClassDesc.of("java.lang.String");
    ClassDesc cdSystem = ClassDesc.of("java.lang.System");
    ClassDesc cdFile = ClassDesc.of("java.io.File");
    ClassDesc cdSb = ClassDesc.of("java.lang.StringBuilder");
    ClassDesc cdApiServer = ClassDesc.of("com.live2d.cubism.patch.CubismApiServer");

    MethodTypeDesc mtdSbInit = MethodTypeDesc.ofDescriptor("()V");
    MethodTypeDesc mtdFileInit = MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V");
    MethodTypeDesc mtdGetEnv = MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Ljava/lang/String;");
    MethodTypeDesc mtdSbAppend = MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    MethodTypeDesc mtdSbToString = MethodTypeDesc.ofDescriptor("()Ljava/lang/String;");
    MethodTypeDesc mtdCreateNewFile = MethodTypeDesc.ofDescriptor("()Z");
    MethodTypeDesc mtdGetApiServer = MethodTypeDesc.ofDescriptor("()Lcom/live2d/cubism/patch/CubismApiServer;");
    MethodTypeDesc mtdStartApiServer = MethodTypeDesc.ofDescriptor("()V");

    Label start = cb.newLabel();
    Label end = cb.newLabel();
    Label handler = cb.newLabel();
    Label done = cb.newLabel();

    int pathSlot = cb.allocateLocal(TypeKind.REFERENCE);
    int errSlot = cb.allocateLocal(TypeKind.REFERENCE);

    cb.exceptionCatchAll(start, end, handler);
    cb.labelBinding(start);

    cb.new_(cdSb)
      .dup()
      .invokespecial(cdSb, "<init>", mtdSbInit)
      .ldc("USERPROFILE")
      .invokestatic(cdSystem, "getenv", mtdGetEnv)
      .invokevirtual(cdSb, "append", mtdSbAppend)
      .getstatic(cdFile, "separator", cdString)
      .invokevirtual(cdSb, "append", mtdSbAppend)
      .ldc("Desktop")
      .invokevirtual(cdSb, "append", mtdSbAppend)
      .getstatic(cdFile, "separator", cdString)
      .invokevirtual(cdSb, "append", mtdSbAppend)
      .ldc("cubism_patched_ok.txt")
      .invokevirtual(cdSb, "append", mtdSbAppend)
      .invokevirtual(cdSb, "toString", mtdSbToString)
      .astore(pathSlot)
      .new_(cdFile)
      .dup()
      .aload(pathSlot)
      .invokespecial(cdFile, "<init>", mtdFileInit)
      .invokevirtual(cdFile, "createNewFile", mtdCreateNewFile)
      .pop()
      .invokestatic(cdApiServer, "getInstance", mtdGetApiServer)
      .invokevirtual(cdApiServer, "start", mtdStartApiServer);

    cb.labelBinding(end);
    cb.goto_(done);

    cb.labelBinding(handler);
    cb.astore(errSlot);

    cb.labelBinding(done);
  }
}
