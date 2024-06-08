package one.util.asserts;

import java.lang.reflect.code.type.ArrayType;
import java.lang.reflect.code.type.JavaType;

final class Util {
  static JavaType deepComponentType(ArrayType arrayType) {
    while(true) {
      JavaType componentType = arrayType.componentType();
      if (componentType instanceof ArrayType arr) {
        arrayType = arr;
      } else {
        return componentType;
      }
    }
  }
}
