package com.Lomikel.Utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Offline regression tests; run main with assertions enabled or disabled. */
public class StringResourceTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void missingResource() throws Exception {
    try {
      new StringResource("missing-string-resource-unique-73f89.txt");
      throw new AssertionError("Missing resource must throw LomikelException");
    } catch (LomikelException expected) {
      check(expected.getMessage().contains("missing-string-resource-unique-73f89.txt"),
            "Exception must identify the resource");
    }
  }

  /** A valid stream whose available() remains zero until it is read. */
  private static class DelayedStream extends ByteArrayInputStream {
    boolean closed;
    DelayedStream(byte[] bytes) { super(bytes); }
    @Override public int available() { return 0; }
    @Override public void close() throws IOException { closed = true; super.close(); }
  }

  private static class BrokenStream extends InputStream {
    boolean closed;
    @Override public int read() throws IOException { throw new IOException("read failure"); }
    @Override public void close() { closed = true; }
  }

  private static class Marker {}
  private static class ResourceLoader extends ClassLoader {
    final InputStream stream;
    ResourceLoader(InputStream stream) { super(StringResourceTest.class.getClassLoader()); this.stream = stream; }
    @Override public InputStream getResourceAsStream(String name) { return stream; }
    Class<?> marker() throws IOException {
      String path = Marker.class.getName().replace('.', '/') + ".class";
      byte[] bytes;
      try (InputStream in = getParent().getResourceAsStream(path)) {
        bytes = in.readAllBytes();
      }
      return defineClass(Marker.class.getName(), bytes, 0, bytes.length);
    }
  }

  private static StringResource resourceFrom(InputStream stream) throws Exception {
    return new StringResource("test-resource", new ResourceLoader(stream).marker());
  }

  private static void utf8AndFileRoundTrip() throws Exception {
    DelayedStream stream = new DelayedStream("café 雪\nsecond".getBytes(StandardCharsets.UTF_8));
    StringResource resource = resourceFrom(stream);
    check(stream.closed, "Input stream must close after successful read");
    check("café 雪\nsecond\n".equals(resource.content()), "Read until EOF and decode UTF-8");
    Path file = Files.createTempFile("string-resource-", ".txt");
    try {
      resource.toFile(file.toString());
      check(resource.content().equals(Files.readString(file, StandardCharsets.UTF_8)), "Write UTF-8 content");
    } finally { Files.deleteIfExists(file); }
  }

  private static void closesOnReadFailure() throws Exception {
    BrokenStream stream = new BrokenStream();
    try {
      resourceFrom(stream);
      throw new AssertionError("Read failure must throw LomikelException");
    } catch (LomikelException expected) {
      check(expected.getCause() instanceof IOException, "Preserve read failure cause");
      check(stream.closed, "Close stream on read failure");
    }
  }

  private static void reportsWriteFailure() throws Exception {
    StringResource resource = resourceFrom(new DelayedStream("ok".getBytes(StandardCharsets.UTF_8)));
    Path missingParent = Files.createTempDirectory("string-resource-parent-");
    Files.delete(missingParent);
    try {
      resource.toFile(missingParent.resolve("child").toString());
      throw new AssertionError("Write failure must throw LomikelException");
    } catch (LomikelException expected) {
      check(expected.getCause() instanceof IOException, "Preserve write failure cause");
    }
  }

  private static void reportsInvalidPath() throws Exception {
    StringResource resource = resourceFrom(new DelayedStream("ok".getBytes(StandardCharsets.UTF_8)));
    try {
      resource.toFile("invalid" + (char) 0 + "path");
      throw new AssertionError("Invalid path must throw LomikelException");
    } catch (LomikelException expected) {
      check(expected.getCause() != null, "Preserve invalid path cause");
    }
  }

  public static void main(String[] args) throws Exception {
    missingResource();
    utf8AndFileRoundTrip();
    closesOnReadFailure();
    reportsWriteFailure();
    reportsInvalidPath();
    System.out.println("StringResourceTest OK");
  }
}
