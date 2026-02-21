public class BootstrapAndWait {
  public static void main(String[] args) throws Exception {
    Class<?> c = Class.forName("com.live2d.cubism.patch.CubismBootstrap");
    c.getMethod("bootstrap").invoke(null);
    Thread.sleep(20000L);
  }
}
