public class InvokeBootstrap {
  public static void main(String[] args) throws Exception {
    Class<?> c = Class.forName("com.live2d.cubism.patch.CubismBootstrap");
    c.getMethod("bootstrap").invoke(null);
    System.out.println("bootstrap invoked");
  }
}
