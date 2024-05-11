package one.util.asserts;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class RefAssertsTest {
  @Test
  public void testPasses() {
    RefAsserts.assertTrue(() -> 2 + 2 == 4);
    RefAsserts.assertTrue("Message", () -> 2 + 2 == 4);
  }
  
  @Test
  public void testFails() {
    AssertionError error = assertThrows(AssertionError.class, () -> RefAsserts.assertTrue(() -> 2 + 2 == 5));
    assertEquals("""
            failed
            2 + 2 -> 4
            2 + 2 == 5 -> false
            """, error.getMessage());
    AssertionError errorWithMessage = assertThrows(AssertionError.class, () -> RefAsserts.assertTrue("Message", () -> 2 + 2 == 5));
    assertEquals("""
            Message
            2 + 2 -> 4
            2 + 2 == 5 -> false
            """, errorWithMessage.getMessage());
  }
}
