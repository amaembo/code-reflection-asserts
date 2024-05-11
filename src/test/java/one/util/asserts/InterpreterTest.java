package one.util.asserts;

import org.junit.jupiter.api.Test;

import java.lang.reflect.code.Quoted;
import java.util.List;

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
            3 * 2 / 4 -> 1
            3 * 2 / 4 >= 5 -> false
            """);
  }

  @Test
  public void testBitwiseIntMath() {
    doTest(() -> (0xFF & 0x123 | 0x3210) == (20 ^ 10), """
            255 & 291 -> 35
            255 & 291 | 12816 -> 12851
            20 ^ 10 -> 30
            255 & 291 | 12816 == 20 ^ 10 -> false
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
            3 * 2 / 0 -> throws java.lang.ArithmeticException: / by zero
            3 * 2 / 0 >= 5 -> throws java.lang.ArithmeticException: / by zero
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
    doTest(() -> 2.0 + 2 == 4, "(double)2 -> 2.0\n2.0 + (double)2 -> 4.0\n(double)4 -> 4.0\n2.0 + (double)2 == (double)4 -> true\n");
  }

  @Test
  public void testArrayCreation() {
    doTest(() -> new int[10][5].length == 10, """
            new int[10][5] -> [[0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], ...]
            new int[10][5].length -> 10
            new int[10][5].length == 10 -> true
            """);
  }
  
  @Test
  public void testList() {
    doTest(() -> List.of("a", "b", "c", "d").contains("e"), "List.of(\"a\",\"b\",\"c\",\"d\") -> [\"a\", \"b\", \"c\", \"d\"]\n" +
            "List.of(\"a\",\"b\",\"c\",\"d\").contains(\"e\") -> false\n");
  }

  private void doTest(AssertionCondition condition, String expected) {
    Quoted quoted = condition.quoted();
    Node model = Interpreter.buildModel(quoted);
    assertEquals(expected, DefaultAssertionFormatter.DEFAULT.formatAssertion(model));
  }
}