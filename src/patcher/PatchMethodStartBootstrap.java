import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

public class PatchMethodStartBootstrap {
  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      throw new IllegalArgumentException("Usage: PatchMethodStartBootstrap <class-file> <methodName> <methodDesc>");
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
          injectBootstrapCall(cb);
        }
        cb.with(ce);
      }
    });

    ClassTransform transform = ClassTransform.transformingMethodBodies(
      method -> {
        boolean hit = method.methodName().equalsString(methodName) && method.methodType().equalsString(methodDesc);
        if (hit) {
          found.set(true);
        }
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

  private static void injectBootstrapCall(CodeBuilder cb) {
    ClassDesc bootstrap = ClassDesc.of("com.live2d.cubism.patch.CubismBootstrap");
    MethodTypeDesc bootstrapSig = MethodTypeDesc.ofDescriptor("()V");
    cb.invokestatic(bootstrap, "bootstrap", bootstrapSig);
  }
}
