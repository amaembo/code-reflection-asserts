package one.util.asserts;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class DefaultValueFormatterTest {
  @Test
  public void testNumbers() {
    assertEquals("0", format(0));
    assertEquals("2147483647", format(Integer.MAX_VALUE));
    assertEquals("-2147483648", format(Integer.MIN_VALUE));
    assertEquals("0.0F", format(0F));
    assertEquals("0.0", format(0.0));
    assertEquals("Double.NEGATIVE_INFINITY", format(Double.NEGATIVE_INFINITY));
    assertEquals("Double.POSITIVE_INFINITY", format(Double.POSITIVE_INFINITY));
    assertEquals("Double.NaN", format(Double.NaN));
    assertEquals("Float.NEGATIVE_INFINITY", format(Float.NEGATIVE_INFINITY));
    assertEquals("Float.POSITIVE_INFINITY", format(Float.POSITIVE_INFINITY));
    assertEquals("Float.NaN", format(Float.NaN));
  }
  
  @Test
  public void testBooleans() {
    assertEquals("true", format(true));
    assertEquals("false", format(false));
  }
  
  @Test
  public void testObjects() {
    assertEquals("null", format(null));
    assertEquals("hello", format(new Object() {
      @Override
      public String toString() {
        return "hello";
      }
    }));
  }
  
  @Test
  public void testChars() {
    assertEquals("'a'", format('a'));
    assertEquals("'\\''", format('\''));
  }
  
  @Test
  public void testStrings() {
    assertEquals("\"\"", format(""));
    assertEquals("\"hello\"", format("hello"));
    assertEquals("\"\\u0001\"", format("\u0001"));
    assertEquals("\"Hello\\n\\rWorld!\"", format("Hello\n\rWorld!"));
    assertEquals("\"\\\\\\f\\t\\b\\'\\\"\"", format("\\\f\t\b'\""));
  }
  
  @Test
  public void testMap() {
    assertEquals("[]", format(Map.of()));
    assertEquals("[1=two]", format(Map.of(1, "two")));
    assertEquals("[1=two, 2=four]", format(new TreeMap<>(Map.of(1, "two", 2, "four"))));
  }
  
  @Test
  public void testArrays() {
    assertEquals("[]", format(new Object[0]));
    assertEquals("[null]", format(new Object[1]));
    assertEquals("[1, 2, 3]", format(new int[] {1,2,3}));
    assertEquals("[true, false]", format(new boolean[]{true, false}));
    assertEquals("[1, 2]", format(new byte[]{1, 2}));
    assertEquals("[1, 2]", format(new short[]{1, 2}));
    assertEquals("[1L, 2L]", format(new long[]{1L, 2L}));
    assertEquals("[1.0F, 2.0F]", format(new float[]{1.0F, 2.0F}));
    assertEquals("[1.0, 2.0]", format(new double[]{1.0, 2.0}));
    assertEquals("['a', 'b']", format(new char[]{'a', 'b' }));
  }

  @Test
  public void testAbbreviation() {
    assertEquals("\"Very long string! Very long string! Very long string! Very long string! Very long string! Very long...\"",
            format("Very long string! Very long string! Very long string! Very long string! Very long string! Very long string! Very long string! Very long string! Very long string!"));
    assertEquals("[\"element\", \"element\", \"element\", \"element\", \"element\", \"element\", \"element\", \"element\", \"element\", \"element\", ...]", 
            format(List.of("element", "element", "element", "element", "element", "element", "element", "element", "element", "element", "element", "element", "element", "element", "element", "element", "element")));
    assertEquals("VeryveryveryverylongobjecttoString|VeryveryveryverylongobjecttoString|VeryveryveryverylongobjecttoSt...", format(new Object() {
      @Override
      public String toString() {
        return "VeryveryveryverylongobjecttoString|VeryveryveryverylongobjecttoString|VeryveryveryverylongobjecttoString|VeryveryveryverylongobjecttoString";
      }
    }));
  }

  private static String format(Object object) {
    return DefaultValueFormatter.DEFAULT.format(object);
  }
}
