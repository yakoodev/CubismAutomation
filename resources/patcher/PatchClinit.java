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

public class PatchClinit {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Usage: PatchClinit <class-file>");
    }

    Path classFile = Paths.get(args[0]);
    byte[] bytes = Files.readAllBytes(classFile);

    ClassFile cf = ClassFile.of();
    ClassModel model = cf.parse(bytes);

    boolean hasClinit = model.methods().stream().anyMatch(m ->
      m.methodName().equalsString("<clinit>") && m.methodType().equalsString("()V")
    );

    byte[] patched;
    if (hasClinit) {
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
        method -> method.methodName().equalsString("<clinit>") && method.methodType().equalsString("()V"),
        patchAtStart
      );
      patched = cf.transformClass(model, transform);
    } else {
      ClassTransform transform = ClassTransform.endHandler(cb ->
        cb.withMethodBody("<clinit>", MethodTypeDesc.ofDescriptor("()V"), ClassFile.ACC_STATIC, code -> {
          injectDesktopMarker(code);
          code.return_();
        })
      );
      patched = cf.transformClass(model, transform);
    }

    Files.write(classFile, patched);
    System.out.println("Patched: " + classFile + " (existing clinit=" + hasClinit + ")");
  }

  private static void injectDesktopMarker(CodeBuilder cb) {
    ClassDesc cdString = ClassDesc.of("java.lang.String");
    ClassDesc cdSystem = ClassDesc.of("java.lang.System");
    ClassDesc cdFile = ClassDesc.of("java.io.File");
    ClassDesc cdSb = ClassDesc.of("java.lang.StringBuilder");

    MethodTypeDesc mtdSbInit = MethodTypeDesc.ofDescriptor("()V");
    MethodTypeDesc mtdFileInit = MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V");
    MethodTypeDesc mtdGetEnv = MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Ljava/lang/String;");
    MethodTypeDesc mtdSbAppend = MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    MethodTypeDesc mtdSbToString = MethodTypeDesc.ofDescriptor("()Ljava/lang/String;");
    MethodTypeDesc mtdCreateNewFile = MethodTypeDesc.ofDescriptor("()Z");

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
      .pop();

    cb.labelBinding(end);
    cb.goto_(done);

    cb.labelBinding(handler);
    cb.astore(errSlot);

    cb.labelBinding(done);
  }
}
