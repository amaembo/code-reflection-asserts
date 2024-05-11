package one.util.asserts;

import java.lang.reflect.code.Quoted;
import java.util.Objects;

import static one.util.asserts.Node.*;

public final class RefAsserts {
  public static void assertTrue(AssertionCondition condition) {
    assertTrue(null, condition);
  }

  public static void assertTrue(String message, AssertionCondition condition) {
    Quoted quoted = condition.quoted();
    Node model = Interpreter.buildModel(quoted);
    if (model instanceof UnsupportedNode) {
      // Fallback
      if (condition.getAsBoolean()) {
        return;
      }
      throw new AssertionError(message);
    }
    if (model instanceof ValueNode valueNode && Boolean.TRUE.equals(valueNode.value())) return;
    String formatted = DefaultAssertionFormatter.DEFAULT.formatAssertion(model);
    throw new AssertionError(Objects.requireNonNullElse(message, "failed") + "\n" + formatted);
  }
}
