package one.util.asserts;

import java.util.*;
import java.util.function.IntFunction;

final class DefaultValueFormatter implements ValueFormatter {
  static final DefaultValueFormatter DEFAULT = new DefaultValueFormatter(100);
  
  private final int lengthHint;

  DefaultValueFormatter(int lengthHint) {
    this.lengthHint = lengthHint;
  }

  public String format(Object object) {
    StringBuilder sb = new StringBuilder();
    formatValue(sb, object);
    return sb.toString();
  }

  private void formatValue(StringBuilder sb, Object object) {
    switch (object) {
      case null -> sb.append("null");
      case Integer _, Short _, Byte _, Boolean _ -> sb.append(object);
      case Character c -> {
        if (c < ' ') {
          sb.append("'\\").append(Integer.toOctalString(c)).append("'");
        } else {
          sb.append("'").append(c).append("'");
        }
      }
      case Long _ -> sb.append(object).append("L");
      case Float f -> sb.append(f.isNaN() ? "Float.NaN" :
              f.isInfinite() ? f > 0 ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY" :
                      object + "F");
      case Double d -> sb.append(d.isNaN() ? "Double.NaN" :
              d.isInfinite() ? d > 0 ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY" :
                      d.toString());
      case String s -> sb.append('"').append(abbreviate(s, Math.max(10, 100 - sb.length()))).append('"'); // TODO: translate escapes
      case Map<?,?> m -> formatValue(sb, m.entrySet());
      case Object[] arr -> formatValue(sb, Arrays.asList(arr));
      case Collection<?> c -> {
        boolean first = true;
        sb.append("[");
        for (Object o : c) {
          if (!first) sb.append(", ");
          if (sb.length() > lengthHint) {
            sb.append("...");
            break;
          }
          formatValue(sb, o);
          first = false;
        }
        sb.append("]");
      }
      case boolean[] arr -> formatValue(sb, asList(arr.length, i -> arr[i]));
      case byte[] arr -> formatValue(sb, asList(arr.length, i -> arr[i]));
      case int[] arr -> formatValue(sb, asList(arr.length, i -> arr[i]));
      case short[] arr -> formatValue(sb, asList(arr.length, i -> arr[i]));
      case long[] arr -> formatValue(sb, asList(arr.length, i -> arr[i]));
      case float[] arr -> formatValue(sb, asList(arr.length, i -> arr[i]));
      case double[] arr -> formatValue(sb, asList(arr.length, i -> arr[i]));
      case char[] arr -> formatValue(sb, asList(arr.length, i -> arr[i]));
      default -> sb.append(abbreviate(String.valueOf(object), Math.max(10, lengthHint - sb.length())));
    }
  }
  
  private static <E> List<E> asList(int size, IntFunction<E> elementFunction) {
    return new AbstractList<>() {
      @Override
      public E get(int index) {
        return elementFunction.apply(index);
      }

      @Override
      public int size() {
        return size;
      }
    };
  }

  private static String abbreviate(String str, int maxLength) {
    int length = str.length();
    return length > maxLength ? str.substring(0, maxLength) + "..." : str;
  }
}
