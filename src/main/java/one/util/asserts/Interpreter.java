package one.util.asserts;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.code.*;
import java.lang.reflect.code.op.CoreOp;
import java.lang.reflect.code.op.ExtendedOp;
import java.lang.reflect.code.type.ArrayType;
import java.lang.reflect.code.type.JavaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.stream.IntStream;

import static one.util.asserts.Node.*;

/**
 * Expression interpreter 
 */
final class Interpreter {
  private final Map<Value, Object> capturedValues;
  private final MethodHandles.Lookup lookup;

  public Interpreter(Map<Value, Object> capturedValues, MethodHandles.Lookup lookup) {
    this.capturedValues = capturedValues;
    this.lookup = lookup;
  }

  static Node buildModel(Quoted quoted) {
    Op op = quoted.op();
    Map<Value, Object> capturedValues = quoted.capturedValues();
    if (op instanceof CoreOp.LambdaOp lambdaOp) {
      Body body = lambdaOp.body();
      List<Block> blocks = body.blocks();
      if (blocks.size() != 1) {
        return new UnsupportedNode(op, List.of());
      }
      Block block = blocks.getFirst();
      List<Op> list = block.children().stream().filter(CoreOp.ReturnOp.class::isInstance).toList();
      if (list.size() != 1) {
        return new UnsupportedNode(op, List.of());
      }
      return new Interpreter(capturedValues, MethodHandles.lookup()).buildModel(list.getFirst());
    }
    return new UnsupportedNode(op, List.of());
  }

  static class ThisOp extends Op {
    protected ThisOp() {
      super("this", List.of());
    }

    @Override
    public Op transform(CopyContext cc, OpTransformer ot) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TypeElement resultType() {
      throw new UnsupportedOperationException();
    }
  }

  private Node buildModel(Value value) {
    return switch (value) {
      case Op.Result result -> buildModel(result.op());
      case Block.Parameter parameter -> {
        if (capturedValues.containsKey(parameter)) {
          yield new ValueNode(new ThisOp(), capturedValues.get(parameter), List.of());
        }
        throw new UnsupportedOperationException(value + ":" + value.getClass());  
      }
    };
  }

  Node buildModel(Op op) {
    // TODO: initialized arrays
    // TODO: new instance
    // TODO: switch expression
    // TODO: lambda?
    // TODO: method ref?
    return switch (op) {
      case CoreOp.ReturnOp _, CoreOp.YieldOp _ -> buildModel(op.operands().getFirst());
      case CoreOp.ConstantOp c -> new ValueNode(c, c.value(), List.of());
      case CoreOp.FieldAccessOp.FieldLoadOp load -> {
        VarHandle field;
        try {
          field = load.fieldDescriptor().resolveToHandle(lookup);
        } catch (ReflectiveOperationException e) {
          throw new RuntimeException(e);
        }
        List<Value> operands = load.operands();
        if (!operands.isEmpty()) {
          Node qualifier = buildModel(operands.getFirst());
          if (!(qualifier instanceof ValueNode valNode)) {
            yield qualifier.derivedFailure(op);
          }
          Object value = field.get(valNode.value());
          yield new ValueNode(load, value, List.of(qualifier));
        }
        Object value = field.get();
        yield new ValueNode(load, value, List.of());
      }
      case CoreOp.InvokeOp inv -> {
        MethodHandle method;
        try {
          method = inv.invokeDescriptor().resolveToHandle(lookup);
        } catch (ReflectiveOperationException e) {
          throw new RuntimeException(e);
        }
        List<Value> operands = inv.operands();
        List<Node> operandNodes = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        for (Value operand : operands) {
          Node node = buildModel(operand);
          operandNodes.add(node);
          if (!(node instanceof ValueNode valNode)) {
            yield node.derivedFailure(op, operandNodes);
          }
          arguments.add(valNode.value());
        }
        Object methodResult;
        try {
          methodResult = method.invokeWithArguments(arguments);
        } catch (Throwable e) {
          yield new ExceptionNode(inv, e, operandNodes);
        }
        yield new ValueNode(inv, methodResult, operandNodes);
      }
      case CoreOp.ArrayAccessOp.ArrayLoadOp _ -> {
        Node array = buildModel(op.operands().getFirst());
        if (!(array instanceof ValueNode arrayVal)) yield array.derivedFailure(op);
        Node index = buildModel(op.operands().getLast());
        Integer idx = intValue(index);
        if (idx == null) yield index.derivedFailure(op, List.of(array, index));
        yield new ValueNode(op, Array.get(arrayVal.value(), idx), List.of(array, index));
      }
      case CoreOp.ArrayLengthOp _ -> {
        Node array = buildModel(op.operands().getFirst());
        if (!(array instanceof ValueNode arrayVal)) yield array.derivedFailure(op);
        yield new ValueNode(op, Array.getLength(arrayVal.value()), List.of(array));
      }
      case CoreOp.VarAccessOp.VarLoadOp load -> {
        Value value = load.operands().getFirst();
        Object obj = capturedValues.get(value);
        if (obj instanceof CoreOp.Var<?> var) {
          yield new ValueNode(load, var.value(), List.of());
        }
        yield new UnsupportedNode(load, List.of());
      }
      case CoreOp.BinaryOp mathOp -> {
        Node left = buildModel(mathOp.operands().getFirst());
        Node right = buildModel(mathOp.operands().getLast());
        if (!(left instanceof ValueNode leftValNode)) yield left.derivedFailure(op);
        List<Node> children = List.of(left, right);
        if (!(right instanceof ValueNode rightValNode)) yield right.derivedFailure(op, children);
        Object leftVal = leftValNode.value();
        Object rightVal = rightValNode.value();
        yield switch (mathOp) {
          case CoreOp.AddOp _ ->
                  fromValue(mathOp, doMath(leftVal, rightVal, Integer::sum, Long::sum, Float::sum, Double::sum), children);
          case CoreOp.SubOp _ ->
                  fromValue(mathOp, doMath(leftVal, rightVal, (a, b) -> a - b, (a, b) -> a - b, (a, b) -> a - b, (a, b) -> a - b), children);
          case CoreOp.MulOp _ ->
                  fromValue(mathOp, doMath(leftVal, rightVal, (a, b) -> a * b, (a, b) -> a * b, (a, b) -> a * b, (a, b) -> a * b), children);
          case CoreOp.DivOp _ ->
                  fromValue(mathOp, doMath(leftVal, rightVal, (a, b) -> a / b, (a, b) -> a / b, (a, b) -> a / b, (a, b) -> a / b), children);
          case CoreOp.ModOp _ ->
                  fromValue(mathOp, doMath(leftVal, rightVal, (a, b) -> a % b, (a, b) -> a % b, (a, b) -> a % b, (a, b) -> a % b), children);
          case CoreOp.AndOp _ ->
                  fromValue(mathOp, doMath(leftVal, rightVal, (a, b) -> a & b, (a, b) -> a & b, null, null), children);
          case CoreOp.OrOp _ ->
                  fromValue(mathOp, doMath(leftVal, rightVal, (a, b) -> a | b, (a, b) -> a | b, null, null), children);
          case CoreOp.XorOp _ ->
                  fromValue(mathOp, doMath(leftVal, rightVal, (a, b) -> a ^ b, (a, b) -> a ^ b, null, null), children);
          case CoreOp.AshrOp _ -> {
            Object result = null;
            if (rightVal instanceof Number amount) {
              if (leftVal instanceof Integer operand) {
                result = operand >> amount.intValue();
              } else if (leftVal instanceof Long operand) {
                result = operand >> amount.longValue();
              }
            }
            yield fromValue(mathOp, result, children);
          }
          case CoreOp.LshrOp _ -> {
            Object result = null;
            if (rightVal instanceof Number amount) {
              if (leftVal instanceof Integer operand) {
                result = operand >>> amount.intValue();
              } else if (leftVal instanceof Long operand) {
                result = operand >>> amount.longValue();
              }
            }
            yield fromValue(mathOp, result, children);
          }
          case CoreOp.LshlOp _ -> {
            Object result = null;
            if (rightVal instanceof Number amount) {
              if (leftVal instanceof Integer operand) {
                result = operand << amount.intValue();
              } else if (leftVal instanceof Long operand) {
                result = operand << amount.longValue();
              }
            }
            yield fromValue(mathOp, result, children);
          }
        };
      }
      case CoreOp.BinaryTestOp testOp -> {
        Node left = buildModel(testOp.operands().getFirst());
        Node right = buildModel(testOp.operands().getLast());
        if (!(left instanceof ValueNode leftValNode)) yield left.derivedFailure(op);
        List<Node> children = List.of(left, right);
        if (!(right instanceof ValueNode rightValNode)) yield right.derivedFailure(op, children);
        Object leftVal = leftValNode.value();
        Object rightVal = rightValNode.value();
        yield switch (testOp) {
          case CoreOp.EqOp _ -> new ValueNode(testOp, leftVal.equals(rightVal), children);
          case CoreOp.NeqOp _ -> new ValueNode(testOp, !leftVal.equals(rightVal), children);
          case CoreOp.LtOp _ ->
                  fromValue(testOp, compTest(leftVal, rightVal, (a, b) -> a < b, (a, b) -> a < b), children);
          case CoreOp.LeOp _ ->
                  fromValue(testOp, compTest(leftVal, rightVal, (a, b) -> a <= b, (a, b) -> a <= b), children);
          case CoreOp.GtOp _ ->
                  fromValue(testOp, compTest(leftVal, rightVal, (a, b) -> a > b, (a, b) -> a > b), children);
          case CoreOp.GeOp _ ->
                  fromValue(testOp, compTest(leftVal, rightVal, (a, b) -> a >= b, (a, b) -> a >= b), children);
        };
      }
      case CoreOp.NewOp newOp -> {
        TypeElement resultType = newOp.resultType();
        if (resultType instanceof ArrayType arrayType) {
          List<Node> childNodes = new ArrayList<>();
          IntStream.Builder dims = IntStream.builder();
          for (Value value : newOp.operands()) {
            // TODO: initialized arrays
            Node node = buildModel(value);
            childNodes.add(node);
            Integer dim = intValue(node);
            if (dim == null) yield node.derivedFailure(newOp, childNodes);
            dims.add(dim);
          }
          Class<?> cls;
          try {
            cls = toClass(Util.deepComponentType(arrayType));
          } catch (RuntimeException e) {
            yield new ExceptionNode(newOp, e, childNodes);
          }
          yield new ValueNode(newOp, Array.newInstance(cls, dims.build().toArray()), childNodes);
        }
        List<Value> operands = newOp.operands();
        List<Node> operandNodes = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        for (Value operand : operands) {
          Node node = buildModel(operand);
          operandNodes.add(node);
          if (!(node instanceof ValueNode valNode)) {
            yield node.derivedFailure(op, operandNodes);
          }
          arguments.add(valNode.value());
        }
        try {
          Class<?>[] argTypes = newOp.constructorType().parameterTypes().stream()
                  .map(this::toClass).toArray(Class[]::new);
          Class<?> objType = toClass(newOp.type());
          Object result = objType.getConstructor(argTypes).newInstance(arguments.toArray());
          yield new ValueNode(newOp, result, operandNodes);
        } catch (RuntimeException | NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
          yield new ExceptionNode(newOp, e, operandNodes);
        }
      }
      case CoreOp.NotOp n -> {
        Node operand = buildModel(n.operands().getFirst());
        if (operand instanceof ValueNode valNode && valNode.value() instanceof Boolean val) {
          yield new ValueNode(op, !val, List.of(operand));
        } else {
          yield operand.derivedFailure(n);
        }
      }
      case CoreOp.NegOp n -> {
        Node operand = buildModel(n.operands().getFirst());
        if (!(operand instanceof ValueNode valNode)) yield operand.derivedFailure(n);
        yield switch (valNode.value()) {
          case Integer i -> new ValueNode(n, -i, List.of(operand));
          case Long l -> new ValueNode(n, -l, List.of(operand));
          case Float f -> new ValueNode(n, -f, List.of(operand));
          case Double d -> new ValueNode(n, -d, List.of(operand));
          default -> operand.derivedFailure(n);
        };
      }
      case CoreOp.ConvOp conv -> {
        Node operand = buildModel(conv.operands().getFirst());
        if (!(operand instanceof ValueNode valNode)) {
          yield operand.derivedFailure(conv);
        }
        Object result = convert(conv.resultType(), valNode.value());
        yield fromValue(conv, result, List.of(operand));
      }
      case ExtendedOp.JavaConditionalExpressionOp ternary -> {
        List<Body> children = ternary.children();
        if (children.size() != 3) {
          yield new UnsupportedNode(ternary, List.of());
        }
        Node condition = buildModel(children.getFirst().entryBlock().terminatingOp());
        if (!(condition instanceof ValueNode condValNode) || !(condValNode.value() instanceof Boolean cond)) {
          yield condition.derivedFailure(ternary, List.of(condition));
        }
        Node branch = buildModel(children.get(cond ? 1 : 2).entryBlock().terminatingOp());
        if (!(branch instanceof ValueNode thenValNode)) {
          yield branch.derivedFailure(ternary, List.of(condition, branch));
        }
        yield new ValueNode(ternary, thenValNode.value(), List.of(condition, branch));
      }
      case ExtendedOp.JavaConditionalOp cond -> {
        boolean isAnd = op instanceof ExtendedOp.JavaConditionalAndOp;
        boolean value = isAnd;
        List<Node> nodes = new ArrayList<>();
        for (Body child : cond.children()) {
          Op term = child.entryBlock().terminatingOp();
          Node node = buildModel(term);
          nodes.add(node);
          if (!(node instanceof ValueNode valNode) || !(valNode.value() instanceof Boolean next)) {
            yield node.derivedFailure(cond, nodes);
          }
          value = next;
          if (next != isAnd) {
            break;
          }
        }
        yield new ValueNode(cond, value, nodes);
      }
      case CoreOp.InstanceOfOp instanceOf -> {
        Node operand = buildModel(instanceOf.operands().getFirst());
        if (!(operand instanceof ValueNode valNode)) {
          yield operand.derivedFailure(instanceOf);
        }
        if (!(instanceOf.type() instanceof JavaType javaType)) {
          yield new UnsupportedNode(instanceOf, List.of(operand));
        }
        Class<?> aClass;
        try {
          aClass = javaType.toNominalDescriptor().resolveConstantDesc(lookup);
        } catch (ReflectiveOperationException e) {
          yield new ExceptionNode(instanceOf, e, List.of(operand));
        }
        yield new ValueNode(instanceOf, aClass.isInstance(valNode.value()), List.of(operand));
      }
      case CoreOp.CastOp castOp -> {
        Node operand = buildModel(castOp.operands().getFirst());
        if (!(operand instanceof ValueNode valNode)) {
          yield operand.derivedFailure(castOp);
        }
        if (!(castOp.type() instanceof JavaType javaType)) {
          yield new UnsupportedNode(castOp, List.of(operand));
        }
        Class<?> aClass;
        try {
          aClass = javaType.toNominalDescriptor().resolveConstantDesc(lookup);
        } catch (ReflectiveOperationException e) {
          yield new ExceptionNode(castOp, e, List.of(operand));
        }
        yield new ValueNode(castOp, aClass.cast(valNode.value()), List.of(operand));
      }
      // TODO: new instance
      // TODO: switch expression
      // TODO: lambda?
      // TODO: method ref?
      default -> new UnsupportedNode(op, List.of());
    };
  }
  
  private Class<?> toClass(TypeElement typeElement) {
    if (!(typeElement instanceof JavaType javaType)) {
      throw new UnsupportedOperationException("Not a Java type: " + typeElement);
    }
    try {
      return javaType.toNominalDescriptor().resolveConstantDesc(lookup);
    }
    catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private Object convert(TypeElement typeElement, Object value) {
    return switch (value) {
      case Integer i -> typeElement == JavaType.BYTE ? (byte) (int) i
              : typeElement == JavaType.SHORT ? (short) (int) i
              : typeElement == JavaType.CHAR ? (char) (int) i
              : typeElement == JavaType.LONG ? (long) (int) i
              : typeElement == JavaType.FLOAT ? (float) (int) i
              : typeElement == JavaType.DOUBLE ? (Object) (double) (int) i
              : null;
      case Long l -> typeElement == JavaType.BYTE ? (byte) (long) l
              : typeElement == JavaType.SHORT ? (short) (long) l
              : typeElement == JavaType.CHAR ? (char) (long) l
              : typeElement == JavaType.INT ? (int) (long) l
              : typeElement == JavaType.FLOAT ? (float) (long) l
              : typeElement == JavaType.DOUBLE ? (Object) (double) (long) l
              : null;

      case Short s -> typeElement == JavaType.BYTE ? (byte) (short) s
              : typeElement == JavaType.INT ? (int) (short) s
              : typeElement == JavaType.CHAR ? (char) (short) s
              : typeElement == JavaType.LONG ? (long) (short) s
              : typeElement == JavaType.FLOAT ? (float) (short) s
              : typeElement == JavaType.DOUBLE ? (Object) (double) (short) s
              : null;

      case Byte b -> typeElement == JavaType.SHORT ? (short) (byte) b
              : typeElement == JavaType.INT ? (int) (byte) b
              : typeElement == JavaType.CHAR ? (char) (byte) b
              : typeElement == JavaType.LONG ? (long) (byte) b
              : typeElement == JavaType.FLOAT ? (float) (byte) b
              : typeElement == JavaType.DOUBLE ? (Object) (double) (byte) b
              : null;

      case Character c -> typeElement == JavaType.BYTE ? (byte) (char) c
              : typeElement == JavaType.SHORT ? (short) (char) c
              : typeElement == JavaType.INT ? (int) (char) c
              : typeElement == JavaType.LONG ? (long) (char) c
              : typeElement == JavaType.FLOAT ? (float) (char) c
              : typeElement == JavaType.DOUBLE ? (Object) (double) (char) c
              : null;

      case Float f -> typeElement == JavaType.BYTE ? (byte) (float) f
              : typeElement == JavaType.SHORT ? (short) (float) f
              : typeElement == JavaType.INT ? (int) (float) f
              : typeElement == JavaType.CHAR ? (char) (float) f
              : typeElement == JavaType.LONG ? (long) (float) f
              : typeElement == JavaType.DOUBLE ? (Object) (double) (float) f
              : null;

      case Double d -> typeElement == JavaType.BYTE ? (byte) (double) d
              : typeElement == JavaType.SHORT ? (short) (double) d
              : typeElement == JavaType.INT ? (int) (double) d
              : typeElement == JavaType.CHAR ? (char) (double) d
              : typeElement == JavaType.LONG ? (long) (double) d
              : typeElement == JavaType.FLOAT ? (Object) (float) (double) d
              : null;
      default -> null;
    };
  }

  private static Integer intValue(Node node) {
    return switch (node) {
      case ValueNode(_, Integer i, _) -> i;
      case ValueNode(_, Character c, _) -> (int) c;
      default -> null;
    };
  }

  private static Node fromValue(Op op, Object value, List<Node> children) {
    return switch (value) {
      case null -> new UnsupportedNode(op, children);
      case Throwable throwable -> new ExceptionNode(op, throwable, children);
      default -> new ValueNode(op, value, children);
    };
  }

  private static Object doMath(
          Object left, Object right,
          IntBinaryOperator intOp,
          LongBinaryOperator longOp,
          FloatBinaryOperator floatOp,
          DoubleBinaryOperator doubleOp
  ) {
    try {
      if (left instanceof Integer i1 && right instanceof Integer i2 && intOp != null) {
        return intOp.applyAsInt(i1, i2);
      }
      if (left instanceof Long l1 && right instanceof Long l2 && longOp != null) {
        return longOp.applyAsLong(l1, l2);
      }
      if (left instanceof Float f1 && right instanceof Float f2 && floatOp != null) {
        return floatOp.apply(f1, f2);
      }
      if (left instanceof Double d1 && right instanceof Double d2 && doubleOp != null) {
        return doubleOp.applyAsDouble(d1, d2);
      }
    }
    catch (ArithmeticException ex) {
      return ex;
    }
    return null;
  }

  private static Boolean compTest(
          Object left, Object right,
          BiLongPredicate longFn,
          BiDoublePredicate doubleFn
  ) {
    if ((left instanceof Float || left instanceof Double) &&
            (right instanceof Float || right instanceof Double)) {
      return doubleFn.test(((Number) left).doubleValue(), ((Number) right).doubleValue());
    }
    if ((left instanceof Integer || left instanceof Long) &&
            (right instanceof Integer || right instanceof Long)) {
      return longFn.test(((Number) left).longValue(), ((Number) right).longValue());
    }
    return null;
  }

  @FunctionalInterface
  private interface BiLongPredicate {
    boolean test(long left, long right);
  }

  @FunctionalInterface
  private interface BiDoublePredicate {
    boolean test(double left, double right);
  }

  @FunctionalInterface
  private interface FloatBinaryOperator {
    float apply(float left, float right);
  }

}
