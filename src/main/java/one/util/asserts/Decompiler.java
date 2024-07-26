package one.util.asserts;

import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.code.*;
import java.lang.reflect.code.op.CoreOp;
import java.lang.reflect.code.op.ExtendedOp;
import java.lang.reflect.code.op.OpFactory;
import java.lang.reflect.code.type.ArrayType;
import java.lang.reflect.code.type.ClassType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Decompiler to produce Java source expression from the {@link Op} or {@link Value}.
 * It should not be necessarily totally correct Java expression. E.g., parts could be omitted if necessary
 * It's required to support only the ops supported by the interpreter.
 */
final class Decompiler {
  enum Precedence {
    LITERAL,
    DEREFERENCE,
    POSTFIX,
    UNARY,
    MULTIPLICATIVE,
    ADDITIVE,
    SHIFT,
    RELATIONAL,
    EQUALITY,
    BITWISE_AND,
    BITWISE_XOR,
    BITWISE_OR,
    LOGICAL_AND,
    LOGICAL_OR,
    TERNARY,
    ASSIGNMENT,
    PARENTHESES;

    static Precedence fromOp(Op op) {
      return switch (op) {
        case ExtendedOp.JavaConditionalExpressionOp _ -> TERNARY;
        case ExtendedOp.JavaConditionalAndOp _ -> LOGICAL_AND;
        case ExtendedOp.JavaConditionalOrOp _ -> LOGICAL_OR;
        case CoreOp.AddOp _, CoreOp.SubOp _ -> ADDITIVE;
        case CoreOp.MulOp _, CoreOp.DivOp _, CoreOp.ModOp _ -> MULTIPLICATIVE;
        case CoreOp.AshrOp _, CoreOp.LshrOp _, CoreOp.LshlOp _ -> SHIFT;
        case CoreOp.EqOp _, CoreOp.NeqOp _ -> EQUALITY;
        case CoreOp.BinaryTestOp _, CoreOp.InstanceOfOp _ -> RELATIONAL;
        case CoreOp.AndOp _ -> BITWISE_AND;
        case CoreOp.OrOp _ -> BITWISE_OR;
        case CoreOp.XorOp _ -> BITWISE_XOR;
        case CoreOp.UnaryOp _, CoreOp.ConvOp _, CoreOp.CastOp _ -> UNARY;
        case CoreOp.ArrayAccessOp _, CoreOp.InvokeOp _, CoreOp.FieldAccessOp _,
             CoreOp.ArrayLengthOp _ -> DEREFERENCE;
        default -> LITERAL;
      };
    }
  }

  static final Decompiler DEFAULT = new Decompiler(DefaultValueFormatter.DEFAULT);

  private static final Map<String, String> ops =
          Map.ofEntries(
                  Map.entry("eq", "=="),
                  Map.entry("neq", "!="),
                  Map.entry("lt", "<"),
                  Map.entry("gt", ">"),
                  Map.entry("le", "<="),
                  Map.entry("ge", ">="),
                  Map.entry("neg", "-"),
                  Map.entry("not", "!"),
                  Map.entry("mul", "*"),
                  Map.entry("and", "&"),
                  Map.entry("or", "|"),
                  Map.entry("add", "+"),
                  Map.entry("lshr", ">>>"),
                  Map.entry("ashr", ">>"),
                  Map.entry("lshl", "<<"),
                  Map.entry("sub", "-"),
                  Map.entry("xor", "^"),
                  Map.entry("div", "/"),
                  Map.entry("mod", "%"),
                  Map.entry("java.cand", "&&"),
                  Map.entry("java.cor", "||")
          );

  private final ValueFormatter formatter;

  private Decompiler(ValueFormatter formatter) {
    this.formatter = formatter;
  }

  /**
   * @param value value to format
   * @return formatted value
   */
  String valueText(Value value) {
    return valueText(value, Precedence.PARENTHESES);
  }

  /**
   * @param value      value to format
   * @param precedence outer operation precedence
   * @return formatted value
   */
  String valueText(Value value, Precedence precedence) {
    return switch (value) {
      case Op.Result result -> opText(result.op(), precedence);
      case Block.Parameter _ -> "this";
    };
  }

  /**
   * @param op operation to decompile
   * @return operation text
   */
  String opText(Op op) {
    Precedence precedence = Precedence.fromOp(op);
    return switch (op) {
      case CoreOp.VarOp varOp -> varOp.varName();
      case CoreOp.VarAccessOp.VarLoadOp load -> valueText(load.operands().getFirst());
      case CoreOp.InvokeOp inv -> {
        List<Value> operands = inv.operands();
        if (inv.hasReceiver()) {
          yield valueText(operands.getFirst(), precedence) + "." + inv.invokeDescriptor().name() + "("
                  + operands.stream().skip(1).map(this::valueText)
                  .collect(Collectors.joining(", ")) + ")";
        }
        String methodName;
        try {
          Executable method = inv.invokeDescriptor().resolveToMember(MethodHandles.lookup());
          methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        } catch (ReflectiveOperationException e) {
          methodName = formatTypeName(inv.invokeDescriptor().refType()) + "." + inv.invokeDescriptor().name();
        }
        yield methodName + "(" + operands.stream().map(this::valueText)
                .collect(Collectors.joining(",")) + ")";
      }
      case CoreOp.ConvOp conv -> "(" + conv.resultType().toString() + ")" + valueText(conv.operands().getFirst(), precedence);
      case CoreOp.FieldAccessOp.FieldLoadOp load -> {
        List<Value> operands = load.operands();
        if (!operands.isEmpty()) {
          yield valueText(operands.getFirst(), precedence) + "." + load.fieldDescriptor().name();
        }
        try {
          Field field = load.fieldDescriptor().resolveToMember(MethodHandles.lookup());
          yield field.getDeclaringClass().getSimpleName() + "." + field.getName();
        } catch (ReflectiveOperationException e) {
          yield formatTypeName(load.fieldDescriptor().refType()) + "." + load.fieldDescriptor().name();
        }
      }
      case CoreOp.NewOp newOp -> {
        TypeElement resultType = newOp.resultType();
        // TODO: initialized arrays
        if (resultType instanceof ArrayType arrayType) {
          yield "new " + formatTypeName(Util.deepComponentType(arrayType)) +
                  newOp.operands().stream().map(v -> "[" + valueText(v) + "]").collect(Collectors.joining());
        }
        String operands = "(" + newOp.operands().stream().map(this::valueText)
                .collect(Collectors.joining(",")) + ")";
        yield "new " + formatTypeName(newOp.type()) + operands;
      }
      case CoreOp.ArrayLengthOp _ -> valueText(op.operands().getFirst(), precedence) + ".length";
      case CoreOp.ArrayAccessOp.ArrayLoadOp _ ->
              valueText(op.operands().getFirst(), precedence) + "[" + valueText(op.operands().getLast()) + "]";
      case CoreOp.BinaryTestOp _, CoreOp.BinaryOp _ ->
              valueText(op.operands().get(0), precedence) + " " + opSymbol(op) + " " + valueText(op.operands().get(1), precedence);
      case CoreOp.UnaryOp _ -> opSymbol(op) + valueText(op.operands().getFirst(), precedence);
      case CoreOp.ReturnOp _ -> "return " + valueText(op.operands().getFirst());
      case CoreOp.YieldOp _ -> valueText(op.operands().getFirst());
      case CoreOp.ConstantOp c -> formatter.format(c.value());
      case CoreOp.CastOp cast -> "(" + formatTypeName(cast.type()) + ")" + valueText(op.operands().getFirst(), precedence);
      case CoreOp.InstanceOfOp instanceOf ->
              valueText(op.operands().getFirst(), precedence) +" instanceof " + formatTypeName(instanceOf.type());
      case Interpreter.ThisOp _ -> "this";
      case CoreOp.QuotedOp quoted -> {
        // TODO: lambdas; instance-bound MR; static method MR; constructor MR
        CoreOp.InvokeOp invokeOp = Util.extractMethodReference(quoted);
        if (invokeOp != null) {
          String type = formatTypeName(invokeOp.invokeDescriptor().refType());
          String name = invokeOp.invokeDescriptor().name();
          yield type + "::" + name;
        }
        yield op.toText() + ":" + op.getClass();
      }
      case ExtendedOp.JavaConditionalOp cand ->
              cand.children().stream().map(body -> opText(body.entryBlock().terminatingOp(), precedence))
                      .collect(Collectors.joining(" " + opSymbol(cand) + " "));
      case ExtendedOp.JavaConditionalExpressionOp ternary -> {
        List<Body> children = ternary.children();
        yield opText(children.get(0).entryBlock().terminatingOp(), precedence) + " ? " +
                opText(children.get(1).entryBlock().terminatingOp(), precedence) + " : " +
                opText(children.get(2).entryBlock().terminatingOp(), precedence);
      }
      default -> op.toText() + ":" + op.getClass();
    };
  }

  private String opText(Op op, Precedence outerPrecedence) {
    String rawText = opText(op);
    Precedence innerPrecedence = Precedence.fromOp(op);
    int cmp = innerPrecedence.compareTo(outerPrecedence);
    return cmp < 0 || cmp == 0 && innerPrecedence == Precedence.DEREFERENCE ? rawText : "(" + rawText + ")";
  }

  private static String formatTypeName(TypeElement typeElement) {
    if (typeElement instanceof ClassType classType) {
      ClassDesc descriptor = classType.toNominalDescriptor();
      try {
        return descriptor.resolveConstantDesc(MethodHandles.lookup()).getSimpleName();
      } catch (ReflectiveOperationException _) {
      }
      return descriptor.displayName();
    }
    TypeElement.ExternalizedTypeElement typeDefinition = typeElement.externalize();
    String identifier = typeDefinition.identifier();
    int dotPos = identifier.lastIndexOf('.');
    if (dotPos > -1) {
      return identifier.substring(dotPos + 1);
    }
    return identifier;
  }

  private static String opSymbol(Op op) {
    String name = op.getClass().getAnnotation(OpFactory.OpDeclaration.class).value();
    return ops.getOrDefault(name, name);
  }
}
