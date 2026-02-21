public class InvokePrivateStart {
  public static void main(String[] args) throws Exception {
    Class<?> c = Class.forName("com.live2d.cubism.patch.CubismBootstrap");
    java.lang.reflect.Method m = c.getDeclaredMethod("startExternalServer");
    m.setAccessible(true);
    m.invoke(null);
    Thread.sleep(20000L);
  }
}
