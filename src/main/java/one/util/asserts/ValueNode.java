package one.util.asserts;

import java.lang.reflect.code.Op;
import java.lang.reflect.code.op.CoreOps;
import java.util.List;

sealed interface Node {
  Op op();

  List<Node> children();

  default Node derivedFailure(Op op) {
    return derivedFailure(op, List.of(this));
  }

  default Node derivedFailure(Op op, List<Node> children) {
    return new UnsupportedNode(op, children);
  }

  record UnsupportedNode(Op op, List<Node> children) implements Node {
  }

  record ValueNode(Op op, Object value, List<Node> children) implements Node {
    boolean isTrivial() {
      return children.isEmpty() && op instanceof CoreOps.ConstantOp ||
              op instanceof CoreOps.NegOp && children.size() == 1 &&
                      children.getFirst() instanceof ValueNode child &&
                      child.isTrivial();
    }
  }

  record ExceptionNode(Op op, Throwable throwable, List<Node> children) implements Node {
    @Override
    public Node derivedFailure(Op op, List<Node> children) {
      return new ExceptionNode(op, throwable, children);
    }
  }
}
