package one.util.asserts;

import java.lang.reflect.code.Block;
import java.lang.reflect.code.Body;
import java.lang.reflect.code.Op;
import java.lang.reflect.code.op.CoreOp;
import java.lang.reflect.code.type.ArrayType;
import java.lang.reflect.code.type.JavaType;
import java.util.List;
import java.util.Optional;

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

  static CoreOp.LambdaOp extractLambda(CoreOp.QuotedOp quoted) {
    List<Body> bodies = quoted.bodies();
    if (bodies.size() == 1) {
      List<Block> blocks = bodies.getFirst().blocks();
      if (blocks.size() == 1) {
        List<Op> ops = blocks.getFirst().ops();
        if (!ops.isEmpty()) {
          if (ops.getFirst() instanceof CoreOp.LambdaOp lambdaOp) {
            return lambdaOp;
          }
        }
      }
    }
    return null;
  }
  
  static CoreOp.InvokeOp extractMethodReference(CoreOp.QuotedOp quoted) {
    return Optional.ofNullable(extractLambda(quoted)).flatMap(CoreOp.LambdaOp::methodReference).orElse(null);
  }
}
