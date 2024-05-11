package one.util.asserts;

import java.util.List;

import static one.util.asserts.RefAsserts.assertTrue;

public final class Experiment {

  public record Point(int x, int y) {
    public static final int XXX = 5;
    
    void test() {
      assertTrue(() -> x > y);
    }
  }
  
  private static class X {
    private int y;
  }


  public static void main(String[] args) {
    //assertTrue(() -> (true != true || 6 + 5 > 5 * 10 || 1.0 / 2.0 == 0.25 * 2.0) && !(0.0f >= 1.0f || (2 ^ 3) == 1) && false);
    Point p = new Point(1, 2);
    X x = new X();
    x.y = 10;
    int a = 5;
    int b = 6;
    double d = Math.random();
    String expected = "Hello";
    String actual = "Goodbye";
//    assertTrue(() -> expected.equals(actual));
    List<String> list = List.of("Java", "is", "cool");
    int[] arr = {1,2,3,4};
    assertTrue(() -> new int[5][10].length == 101);
//    assertTrue(() -> arr['\0'] == list.size());
    assertTrue(() -> list.size() > 5);
    assertTrue(() -> list.contains("Kotlin"));
//    assertTrue(() -> x.y != 10);
//    p.test();
//    assertTrue(() -> p.x > Point.XXX);//Broken in Babylon
    assertTrue(() -> d < Double.POSITIVE_INFINITY);
//    assertTrue(() -> d > 0.5);
//    assertTrue(() -> p == null);
    assertTrue(() -> a > Integer.MAX_VALUE);
  }
}
