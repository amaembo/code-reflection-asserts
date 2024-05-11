package one.util.asserts;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.code.Block;
import java.lang.reflect.code.Op;
import java.lang.reflect.code.TypeElement;
import java.lang.reflect.code.Value;
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
    return switch (value) {
      case Op.Result result -> opText(result.op());
      case Block.Parameter _ -> "this";
    };
  }

  String opText(Op op) {
    return switch (op) {
      case CoreOps.VarOp varOp -> varOp.varName();
      case CoreOps.VarAccessOp.VarLoadOp load -> valueText(load.operands().getFirst());
      case CoreOps.InvokeOp inv -> {
        List<Value> operands = inv.operands();
        if (inv.hasReceiver()) {
          yield valueText(operands.getFirst()) + "." + inv.invokeDescriptor().name() + "("
                  + operands.stream().skip(1).map(this::valueText)
                  .collect(Collectors.joining(", ")) + ")";
        }
        String methodName;
        try {
          Executable method = inv.invokeDescriptor().resolveToMember(MethodHandles.publicLookup());
          methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        } catch (ReflectiveOperationException e) {
          methodName = formatTypeName(inv.invokeDescriptor().refType()) + "." + inv.invokeDescriptor().name();
        }
        yield methodName + "(" + operands.stream().map(this::valueText)
                .collect(Collectors.joining(",")) + ")";
      }
      case CoreOps.FieldAccessOp.FieldLoadOp load -> {
        List<Value> operands = load.operands();
        if (!operands.isEmpty()) {
          yield valueText(operands.getFirst()) + "." + load.fieldDescriptor().name();
        }
        try {
          Field field = load.fieldDescriptor().resolveToMember(MethodHandles.publicLookup());
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
      case CoreOps.ArrayLengthOp _ -> valueText(op.operands().getFirst()) + ".length";
      case CoreOps.ArrayAccessOp.ArrayLoadOp _ ->
              valueText(op.operands().getFirst()) + "[" + valueText(op.operands().getLast()) + "]";
      case CoreOps.BinaryTestOp _, CoreOps.BinaryOp _ ->
              valueText(op.operands().get(0)) + " " + opSymbol(op) + " " + valueText(op.operands().get(1));
      case CoreOps.UnaryOp _ -> opSymbol(op) + valueText(op.operands().getFirst());
      case CoreOps.ReturnOp _ -> "return " + valueText(op.operands().getFirst());
      case CoreOps.YieldOp _ -> valueText(op.operands().getFirst());
      case CoreOps.ConstantOp c -> formatter.format(c.value());
      case Interpreter.ThisOp _ -> "this";
      case ExtendedOps.JavaConditionalOp cand ->
              cand.children().stream().map(body -> opText(body.entryBlock().terminatingOp()))
                      .collect(Collectors.joining(" " + opSymbol(cand) + " "));
      default -> op.toText() + ":" + op.getClass();
    };
  }

  private static String formatTypeName(TypeElement typeElement) {
    TypeDefinition typeDefinition = typeElement.toTypeDefinition();
    String identifier = typeDefinition.identifier();
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
