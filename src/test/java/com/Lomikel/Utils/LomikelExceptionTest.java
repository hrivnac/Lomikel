package com.Lomikel.Utils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/** Offline regression for native exception trace formatting and stream routing. */
public class LomikelExceptionTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static String nativeTrace(Exception exception) {
    StringWriter output = new StringWriter();
    exception.printStackTrace(new PrintWriter(output));
    return output.toString();
  }

  private static void nestedCauses() {
    IllegalArgumentException root = new IllegalArgumentException("root-marker");
    IllegalStateException middle = new IllegalStateException("middle-marker", root);
    LomikelException outer = new LomikelException("outer-marker", middle);
    String trace = outer.stackTrace();
    check(trace.equals(nativeTrace(outer)), "stackTrace should match the native trace exactly");
    check(trace.equals(LomikelException.stackTrace2String(outer)), "static trace differs");
    check(occurrences(trace, "Caused by: ") == 2, "nested causes printed more than once");
    check(occurrences(trace, "root-marker") == 1, "root cause printed more than once");
    check(occurrences(trace, "middle-marker") == 1, "middle cause printed more than once");
    check(LomikelException.stackTrace2String(middle).equals(nativeTrace(middle)),
          "ordinary exceptions should retain native traces");
  }

  private static int occurrences(String text, String needle) {
    int count = 0;
    for (int from = 0; (from = text.indexOf(needle, from)) >= 0; from += needle.length()) count++;
    return count;
  }

  private static void nativePrinting() throws Exception {
    LomikelException exception = new LomikelException("outer", new IllegalArgumentException("inner"));
    String expected = nativeTrace(exception);
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
         PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
      System.setOut(out);
      System.setErr(err);
      exception.printStackTrace();
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
    check(stdout.size() == 0, "native no-arg printStackTrace wrote to stdout");
    check(stderr.toString(StandardCharsets.UTF_8).equals(expected),
          "native no-arg printStackTrace must write one complete trace to stderr");

    ByteArrayOutputStream streamBytes = new ByteArrayOutputStream();
    exception.printStackTrace(new PrintStream(streamBytes, true, StandardCharsets.UTF_8));
    check(streamBytes.toString(StandardCharsets.UTF_8).equals(expected), "PrintStream overload changed");
    StringWriter writer = new StringWriter();
    exception.printStackTrace(new PrintWriter(writer));
    check(writer.toString().equals(expected), "PrintWriter overload changed");
  }

  public static void main(String[] args) throws Exception {
    nestedCauses();
    nativePrinting();
    System.out.println("LomikelExceptionTest passed");
  }
}
