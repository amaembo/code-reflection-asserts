package one.util.asserts;

public final class DefaultAssertionFormatter implements AssertionFormatter {
  private final ValueFormatter valueFormatter;
  private final Decompiler decompiler;

  static final AssertionFormatter DEFAULT = new DefaultAssertionFormatter(DefaultValueFormatter.DEFAULT, Decompiler.DEFAULT);

  DefaultAssertionFormatter(ValueFormatter valueFormatter, Decompiler decompiler) {
    this.valueFormatter = valueFormatter;
    this.decompiler = decompiler;
  }

  @Override
  public String formatAssertion(Node node) {
    StringBuilder sb = new StringBuilder();
    format(sb, node);
    return sb.toString();
  }
  
  private void format(StringBuilder sb, Node node) {
    node.children().forEach(c -> format(sb, c));
    switch (node) {
      case Node.ExceptionNode exceptionNode -> sb.append(decompiler.opText(node.op()))
              .append(" -> throws ")
              .append(valueFormatter.format(exceptionNode.throwable())).append("\n");
      case Node.UnsupportedNode _ -> sb.append("Unsupported node: ")
              .append(decompiler.opText(node.op())).append(" (")
              .append(node.op().getClass()).append(")\n");
      case Node.ValueNode valueNode -> {
        if (!valueNode.isTrivial()) {
          sb.append(decompiler.opText(node.op())).append(" -> ")
                  .append(valueFormatter.format(valueNode.value())).append("\n");
        }
      }
    }
  }
}
