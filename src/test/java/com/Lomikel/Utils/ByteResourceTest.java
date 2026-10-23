package com.Lomikel.Utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Standalone, offline ByteResource regressions; run with java -ea. */
public class ByteResourceTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static class ResourceLoader extends ClassLoader {
    final InputStream stream;
    ResourceLoader(InputStream stream) { super(ByteResourceTest.class.getClassLoader()); this.stream = stream; }
    @Override public InputStream getResourceAsStream(String name) { return stream; }
    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!name.equals("com.Lomikel.Utils.ByteResource") &&
          !name.equals("com.Lomikel.Utils.Info")) return super.loadClass(name, resolve);
      synchronized (getClassLoadingLock(name)) {
        Class<?> result = findLoadedClass(name);
        if (result == null) {
          try (InputStream source = ByteResourceTest.class.getResourceAsStream(name.substring(name.lastIndexOf('.') + 1) + ".class")) {
            if (source == null) throw new ClassNotFoundException(name);
            byte[] bytes = source.readAllBytes();
            result = defineClass(name, bytes, 0, bytes.length);
          } catch (IOException e) { throw new ClassNotFoundException(name, e); }
        }
        if (resolve) resolveClass(result);
        return result;
      }
    }
  }

  private static Object resource(InputStream stream) throws Exception {
    Class<?> type = new ResourceLoader(stream).loadClass("com.Lomikel.Utils.ByteResource");
    Constructor<?> constructor = type.getConstructor(String.class);
    return constructor.newInstance("injected-resource");
  }

  private static void missingResource() throws Exception {
    Path destination = Files.createTempFile("byteresource-existing-", ".bin");
    byte[] original = {17, 0, 42};
    try {
      Files.write(destination, original);
      try {
        Object result = resource(null);
        result.getClass().getMethod("toFile", String.class).invoke(result, destination.toString());
        throw new AssertionError("missing resource must fail before toFile can truncate destination");
      } catch (InvocationTargetException e) {
        check(e.getCause() instanceof IllegalArgumentException,
              "missing resource must throw IllegalArgumentException: " + e.getCause());
      }
      check(Arrays.equals(original, Files.readAllBytes(destination)),
            "missing resource must not truncate an existing destination");
    } finally { Files.deleteIfExists(destination); }
  }

  private static void bufferedBinaryAndClose() throws Exception {
    byte[] bytes = new byte[16391];
    for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
    final boolean[] closed = {false};
    final int[] blockReads = {0};
    InputStream input = new ByteArrayInputStream(bytes) {
      @Override public int read(byte[] buffer, int offset, int length) {
        blockReads[0]++;
        return super.read(buffer, offset, length);
      }
      @Override public void close() { closed[0] = true; }
    };
    Object resource = resource(input);
    Path path = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), "byteresource-", ".bin");
    try {
      Method toFile = resource.getClass().getMethod("toFile", String.class);
      toFile.invoke(resource, path.toString());
      check(Arrays.equals(bytes, Files.readAllBytes(path)), "binary bytes changed");
      check(closed[0], "input not closed on success");
      check(blockReads[0] > 0, "input was not read in blocks");
    } finally { Files.deleteIfExists(path); }
  }

  private static void failedReadThrowsAndPreservesDestination() throws Exception {
    final boolean[] closed = {false};
    InputStream input = new InputStream() {
      private int calls;
      @Override public int read() throws IOException {
        if (calls++ == 0) return 'x';
        throw new IOException("injected failure");
      }
      @Override public int read(byte[] b, int off, int len) throws IOException {
        if (calls++ == 0) { b[off] = 'x'; return 1; }
        throw new IOException("injected failure");
      }
      @Override public void close() { closed[0] = true; }
    };
    Path destination = Files.createTempFile("byteresource-existing-", ".bin");
    byte[] original = {17, 0, 42};
    try {
      Files.write(destination, original);
      try {
        Object result = resource(input);
        result.getClass().getMethod("toFile", String.class).invoke(result, destination.toString());
        throw new AssertionError("partial read must fail before toFile can truncate destination");
      } catch (InvocationTargetException e) {
        check(e.getCause() instanceof UncheckedIOException,
              "read failure must throw UncheckedIOException: " + e.getCause());
        check(e.getCause().getCause() instanceof IOException,
              "original I/O failure must be preserved");
      }
      check(Arrays.equals(original, Files.readAllBytes(destination)),
            "partial read failure truncated existing destination");
      check(closed[0], "input not closed on failure");
    } finally { Files.deleteIfExists(destination); }
  }

  private static void defaultCharsetCompatibility() throws Exception {
    byte[] bytes = {(byte) 0xe9, (byte) 0xff};
    Object resource = resource(new ByteArrayInputStream(bytes));
    check(new String(bytes, Charset.defaultCharset()).equals(resource.toString()),
          "toString must retain the platform default charset");
  }

  private static void outputFailureDoesNotThrow() throws Exception {
    Object resource = resource(new ByteArrayInputStream(new byte[] {42}));
    Path dir = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "byteresource-directory-");
    try {
      resource.getClass().getMethod("toFile", String.class).invoke(resource, dir.toString()); // cannot open a directory as an output stream
      check(Files.isDirectory(dir), "failed write altered destination");
    } finally { Files.delete(dir); }
  }

  public static void main(String[] args) throws Exception {
    switch (args[0]) {
      case "missingResource": missingResource(); break;
      case "bufferedBinaryAndClose": bufferedBinaryAndClose(); break;
      case "failedReadThrowsAndPreservesDestination": failedReadThrowsAndPreservesDestination(); break;
      case "defaultCharsetCompatibility": defaultCharsetCompatibility(); break;
      case "outputFailureDoesNotThrow": outputFailureDoesNotThrow(); break;
      default: throw new IllegalArgumentException(args[0]);
    }
    System.out.println("PASS " + args[0]);
  }
}
