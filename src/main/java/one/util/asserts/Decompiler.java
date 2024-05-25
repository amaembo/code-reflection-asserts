package one.util.asserts;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.code.*;
import java.lang.reflect.code.op.CoreOps;
import java.lang.reflect.code.op.ExtendedOps;
import java.lang.reflect.code.op.OpDeclaration;
import java.lang.reflect.code.type.TypeDefinition;
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
        case ExtendedOps.JavaConditionalExpressionOp _ -> TERNARY;
        case ExtendedOps.JavaConditionalAndOp _ -> LOGICAL_AND;
        case ExtendedOps.JavaConditionalOrOp _ -> LOGICAL_OR;
        case CoreOps.AddOp _, CoreOps.SubOp _ -> ADDITIVE;
        case CoreOps.MulOp _, CoreOps.DivOp _, CoreOps.ModOp _ -> MULTIPLICATIVE;
        case CoreOps.AshrOp _, CoreOps.LshrOp _, CoreOps.LshlOp _ -> SHIFT;
        case CoreOps.EqOp _, CoreOps.NeqOp _ -> EQUALITY;
        case CoreOps.BinaryTestOp _, CoreOps.InstanceOfOp _ -> RELATIONAL;
        case CoreOps.AndOp _ -> BITWISE_AND;
        case CoreOps.OrOp _ -> BITWISE_OR;
        case CoreOps.XorOp _ -> BITWISE_XOR;
        case CoreOps.UnaryOp _, CoreOps.ConvOp _, CoreOps.CastOp _ -> UNARY;
        case CoreOps.ArrayAccessOp _, CoreOps.InvokeOp _, CoreOps.FieldAccessOp _,
             CoreOps.ArrayLengthOp _ -> DEREFERENCE;
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
      case CoreOps.VarOp varOp -> varOp.varName();
      case CoreOps.VarAccessOp.VarLoadOp load -> valueText(load.operands().getFirst());
      case CoreOps.InvokeOp inv -> {
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
      case CoreOps.ConvOp conv -> "(" + conv.resultType().toString() + ")" + valueText(conv.operands().getFirst(), precedence);
      case CoreOps.FieldAccessOp.FieldLoadOp load -> {
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
      case CoreOps.NewOp newOp -> {
        TypeDefinition typeDefinition = newOp.resultType().toTypeDefinition();
        String identifier = typeDefinition.identifier();
        if (identifier.startsWith("[")) {
          yield "new " + typeDefinition.arguments().getFirst().toString() +
                  newOp.operands().stream().map(v -> "[" + valueText(v) + "]").collect(Collectors.joining());
        }
        // TODO: constructor
        yield op.toText() + ":" + op.getClass();
      }
      case CoreOps.ArrayLengthOp _ -> valueText(op.operands().getFirst(), precedence) + ".length";
      case CoreOps.ArrayAccessOp.ArrayLoadOp _ ->
              valueText(op.operands().getFirst(), precedence) + "[" + valueText(op.operands().getLast()) + "]";
      case CoreOps.BinaryTestOp _, CoreOps.BinaryOp _ ->
              valueText(op.operands().get(0), precedence) + " " + opSymbol(op) + " " + valueText(op.operands().get(1), precedence);
      case CoreOps.UnaryOp _ -> opSymbol(op) + valueText(op.operands().getFirst(), precedence);
      case CoreOps.ReturnOp _ -> "return " + valueText(op.operands().getFirst());
      case CoreOps.YieldOp _ -> valueText(op.operands().getFirst());
      case CoreOps.ConstantOp c -> formatter.format(c.value());
      case CoreOps.CastOp cast -> "(" + formatTypeName(cast.type()) + ")" + valueText(op.operands().getFirst(), precedence);
      case CoreOps.InstanceOfOp instanceOf ->
              valueText(op.operands().getFirst(), precedence) +" instanceof " + formatTypeName(instanceOf.type());
      case Interpreter.ThisOp _ -> "this";
      case ExtendedOps.JavaConditionalOp cand ->
              cand.children().stream().map(body -> opText(body.entryBlock().terminatingOp(), precedence))
                      .collect(Collectors.joining(" " + opSymbol(cand) + " "));
      case ExtendedOps.JavaConditionalExpressionOp ternary -> {
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
    TypeDefinition typeDefinition = typeElement.toTypeDefinition();
    String identifier = typeDefinition.identifier();
    try {
      return MethodHandles.lookup().findClass(identifier).getSimpleName();
    } catch (ClassNotFoundException | IllegalAccessException _) {
      
    }
    int dotPos = identifier.lastIndexOf('.');
    if (dotPos > -1) {
      return identifier.substring(dotPos + 1);
    }
    return identifier;
  }

  private static String opSymbol(Op op) {
    String name = op.getClass().getAnnotation(OpDeclaration.class).value();
    return ops.getOrDefault(name, name);
  }
}
