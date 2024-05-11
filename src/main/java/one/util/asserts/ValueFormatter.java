package one.util.asserts;

@FunctionalInterface
public interface ValueFormatter {
  /**
   * @param value to format
   * @return string representation of the value
   */
  String format(Object value);
}
