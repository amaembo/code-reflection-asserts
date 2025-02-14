package one.util.asserts;

import org.junit.jupiter.api.Test;

import java.lang.reflect.code.Quoted;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class InterpreterTest {
  @Test
  public void testIntMath() {
    doTest(() -> 2 + 2 * 2 == 6, """
            2 * 2 -> 4
            2 + 2 * 2 -> 6
            2 + 2 * 2 == 6 -> true
            """);
    doTest(() -> Integer.MAX_VALUE % 10 - 2 >= 5, """
            Integer.MAX_VALUE -> 2147483647
            Integer.MAX_VALUE % 10 -> 7
            Integer.MAX_VALUE % 10 - 2 -> 5
            Integer.MAX_VALUE % 10 - 2 >= 5 -> true
            """);
    doTest(() -> 3 * 2 / 4 >= 5, """
            3 * 2 -> 6
            (3 * 2) / 4 -> 1
            (3 * 2) / 4 >= 5 -> false
            """);
  }
  
  @Test
  public void testNegative() {
    doTest(() -> -2 -2 == -4, """
            -2 - 2 -> -4
            -2 - 2 == -4 -> true
            """);
    doTest(() -> -(2 + 2) == -4, """
            2 + 2 -> 4
            -(2 + 2) -> -4
            -(2 + 2) == -4 -> true
            """);
  }
  
  @Test
  public void testShift() {
    int x = 123;
    doTest(() -> x >> 2 == 61, """
            x -> 123
            x >> 2 -> 30
            x >> 2 == 61 -> false
            """);
    doTest(() -> x >> 32 == 61, """
            x -> 123
            x >> 32 -> 123
            x >> 32 == 61 -> false
            """);
    doTest(() -> (long)x >> 32 == 61, """
            x -> 123
            (long)x -> 123L
            (long)x >> 32 -> 0L
            (long)x >> 32 == (long)61 -> false
            """);
  }

  @Test
  public void testBitwiseIntMath() {
    doTest(() -> (0xFF & 0x123 | 0x3210) == (20 ^ 10), """
            255 & 291 -> 35
            255 & 291 | 12816 -> 12851
            20 ^ 10 -> 30
            (255 & 291 | 12816) == (20 ^ 10) -> false
            """);
  }

  @Test
  public void testDoubleMath() {
    doTest(() -> 0.1 + 0.2 == 0.3, """
            0.1 + 0.2 -> 0.30000000000000004
            0.1 + 0.2 == 0.3 -> false
            """);
    doTest(() -> 0.1 - 0.2 == -0.1, """
            0.1 - 0.2 -> -0.1
            0.1 - 0.2 == -0.1 -> true
            """);
    doTest(() -> 0.1 * 0.2 == 0.01, """
            0.1 * 0.2 -> 0.020000000000000004
            0.1 * 0.2 == 0.01 -> false
            """);
    doTest(() -> 0.1 / 0.2 == 0.5, """
            0.1 / 0.2 -> 0.5
            0.1 / 0.2 == 0.5 -> true
            """);
    doTest(() -> 0.1 % 0.2 == 0.1, """
            0.1 % 0.2 -> 0.1
            0.1 % 0.2 == 0.1 -> true
            """);
  }

  @Test
  public void testFloatMath() {
    doTest(() -> 0.1F + 0.2F == 0.3F, """
            0.1F + 0.2F -> 0.3F
            0.1F + 0.2F == 0.3F -> true
            """);
    doTest(() -> 0.1F - 0.2F == -0.1F, """
            0.1F - 0.2F -> -0.1F
            0.1F - 0.2F == -0.1F -> true
            """);
    doTest(() -> 0.1F * 0.2F == 0.01F, """
            0.1F * 0.2F -> 0.020000001F
            0.1F * 0.2F == 0.01F -> false
            """);
    doTest(() -> 0.1F / 0.2F == 0.5F, """
            0.1F / 0.2F -> 0.5F
            0.1F / 0.2F == 0.5F -> true
            """);
    doTest(() -> 0.1F % 0.2F == 0.1F, """
            0.1F % 0.2F -> 0.1F
            0.1F % 0.2F == 0.1F -> true
            """);
  }

  @Test
  public void testDivisionByZero() {
    doTest(() -> 3 * 2 / 0 >= 5, """
            3 * 2 -> 6
            (3 * 2) / 0 -> throws java.lang.ArithmeticException: / by zero
            (3 * 2) / 0 >= 5 -> throws java.lang.ArithmeticException: / by zero
            """);
  }

  @Test
  public void testArrayAccess() {
    int[] x = {1, 2, 3};
    doTest(() -> x[1] == x.length, """
            x -> [1, 2, 3]
            x[1] -> 2
            x -> [1, 2, 3]
            x.length -> 3
            x[1] == x.length -> false
            """);
  }
  
  @Test
  public void testPrimitiveWidening() {
    doTest(() -> 2.0 + 2 == 4, """
            2.0 + (double)2 -> 4.0
            2.0 + (double)2 == (double)4 -> true
            """);
  }

  @Test
  public void testArrayCreation() {
    doTest(() -> new int[10][5].length == 10, """
            new int[10][5] -> [[0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], ...]
            new int[10][5].length -> 10
            new int[10][5].length == 10 -> true
            """);
  }

  static final class Point {
    public final int x;
    public final int y;

    Point(int x, int y) {
      this.x = x;
      this.y = y;
    }
    
    void check() {
      doTest(() -> x == y, """
              this -> [1; 3]
              this.x -> 1
              this -> [1; 3]
              this.y -> 3
              this.x == this.y -> false
              """);
    }

    @Override
    public String toString() {
      return "[" + x + "; " + y + "]";
    }
  }
  
  @Test
  public void testFieldAccess() {
    Point point = new Point(1, 3);
    doTest(() -> point.x == point.y - 2, """
            point -> [1; 3]
            point.x -> 1
            point -> [1; 3]
            point.y -> 3
            point.y - 2 -> 1
            point.x == point.y - 2 -> true
            """);
    point.check();
  }
  
  @Test
  public void testList() {
    doTest(() -> List.of("a", "b", "c", "d").contains("e"), "List.of(\"a\",\"b\",\"c\",\"d\") -> [\"a\", \"b\", \"c\", \"d\"]\n" +
            "List.of(\"a\",\"b\",\"c\",\"d\").contains(\"e\") -> false\n");
  }
  
  @Test
  public void testConditionalAndOr() {
    doTest(() -> 2 < 3 && 4 > 5, """
            2 < 3 -> true
            4 > 5 -> false
            2 < 3 && 4 > 5 -> false
            """);
    doTest(() -> 2 < 3 || 4 > 5, """
            2 < 3 -> true
            2 < 3 || 4 > 5 -> true
            """);
  }
  
  @Test
  public void testInstanceOf() {
    Object obj = "Hello";
    doTest(() -> obj instanceof String, """
            obj -> "Hello"
            obj instanceof String -> true
            """);
  }
  
  @Test
  public void testCast() {
    Object obj = "Hello";
    doTest(() -> ((String)obj).length() == 5, """
            obj -> "Hello"
            (String)obj -> "Hello"
            ((String)obj).length() -> 5
            ((String)obj).length() == 5 -> true
            """);
    record LocalClass(int x) {}
    Object obj2 = new LocalClass(1);
    doTest(() -> ((LocalClass) obj2).x() == 1, """
            obj2 -> LocalClass[x=1]
            (LocalClass)obj2 -> LocalClass[x=1]
            ((LocalClass)obj2).x() -> 1
            ((LocalClass)obj2).x() == 1 -> true
            """);
  }
  
  @Test
  public void testTernary() {
    int a = 2;
    int b = 3;
    doTest(() -> (a > b ? "xyz" : "ab").length() == 3, """
            a -> 2
            b -> 3
            a > b -> false
            a > b ? "xyz" : "ab" -> "ab"
            (a > b ? "xyz" : "ab").length() -> 2
            (a > b ? "xyz" : "ab").length() == 3 -> false
            """);
    doTest(() -> (a < b ? "xyz" : "ab").length() == 3, """
            a -> 2
            b -> 3
            a < b -> true
            a < b ? "xyz" : "ab" -> "xyz"
            (a < b ? "xyz" : "ab").length() -> 3
            (a < b ? "xyz" : "ab").length() == 3 -> true
            """);
    doTest(() -> (a < 1 / 0 ? "xyz" : "ab").length() == 3, """
            a -> 2
            1 / 0 -> throws java.lang.ArithmeticException: / by zero
            a < 1 / 0 -> throws java.lang.ArithmeticException: / by zero
            a < 1 / 0 ? "xyz" : "ab" -> throws java.lang.ArithmeticException: / by zero
            (a < 1 / 0 ? "xyz" : "ab").length() -> throws java.lang.ArithmeticException: / by zero
            (a < 1 / 0 ? "xyz" : "ab").length() == 3 -> throws java.lang.ArithmeticException: / by zero
            """);
  }
  
  @Test
  public void testNewExpression() {
    doTest(() -> new String(new char[5]).hashCode() == 10, """
            new char[5] -> ['\\u0000', '\\u0000', '\\u0000', '\\u0000', '\\u0000']
            new String(new char[5]) -> "\\u0000\\u0000\\u0000\\u0000\\u0000"
            new String(new char[5]).hashCode() -> 0
            new String(new char[5]).hashCode() == 10 -> false
            """);
  }
  
  @Test
  public void testNewObjectArray() {
    doTest(() -> new String[5].length == 5, """
            new String[5] -> [null, null, null, null, null]
            new String[5].length -> 5
            new String[5].length == 5 -> true
            """);
  }
  
  interface Fn extends Function<String, Integer> {
    int base(String value);

    @Override
    default Integer apply(String s) {
      return base(s) * 2;
    }
  }
  
  @Test
  public void testMethodRef() {
    doTest(() -> Optional.of("").filter(String::isEmpty).isPresent(), """
            Optional.of("") -> Optional[]
            Optional.of("").filter(String::isEmpty) -> Optional[]
            Optional.of("").filter(String::isEmpty).isPresent() -> true
            """);
    doTest(() -> Optional.of("xyz").filter(String::isEmpty).isPresent(), """
            Optional.of("xyz") -> Optional[xyz]
            Optional.of("xyz").filter(String::isEmpty) -> Optional.empty
            Optional.of("xyz").filter(String::isEmpty).isPresent() -> false
            """);
    doTest(() -> Optional.of("xyz").map(String::length).isPresent(), """
            Optional.of("xyz") -> Optional[xyz]
            Optional.of("xyz").map(String::length) -> Optional[3]
            Optional.of("xyz").map(String::length).isPresent() -> true
            """);
    // TODO: try to keep cast in decompilation
    doTest(() -> Optional.of("xyz").map((Fn)String::length).isPresent(), """
            Optional.of("xyz") -> Optional[xyz]
            Optional.of("xyz").map(String::length) -> Optional[6]
            Optional.of("xyz").map(String::length).isPresent() -> true
            """);
  }
  
  @Test
  public void testUnsupported() {
    doTest(() -> {
      int x = 10;
      return x > 2;
    }, """
            Unsupported node: x (class java.lang.reflect.code.op.CoreOp$VarAccessOp$VarLoadOp)
            Unsupported node: x > 2 (class java.lang.reflect.code.op.CoreOp$GtOp)
            """);
  }

  private static void doTest(AssertionCondition condition, String expected) {
    Quoted quoted = condition.quoted();
    Node model = Interpreter.buildModel(quoted);
    assertEquals(expected, DefaultAssertionFormatter.DEFAULT.formatAssertion(model));
  }
}
