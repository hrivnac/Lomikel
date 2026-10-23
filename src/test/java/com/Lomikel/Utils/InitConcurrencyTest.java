package com.Lomikel.Utils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Offline regression probe: the real notifier is never loaded or invoked. */
public class InitConcurrencyTest {
  private static final String PACKAGE = "com.Lomikel.Utils";

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void remove(Path path) throws IOException {
    if (!Files.exists(path)) return;
    try (java.util.stream.Stream<Path> files = Files.walk(path)) {
      for (Path file : (Iterable<Path>) files.sorted(java.util.Comparator.reverseOrder())::iterator)
        Files.delete(file);
    }
  }

  public static void main(String[] args) throws Exception {
    Path source = Paths.get(args.length == 0 ? "src/java/com/Lomikel/Utils/Init.java" : args[0]);
    Path temp = Files.createTempDirectory("init-offline-");
    try {
      Path fakeSource = temp.resolve("com/Lomikel/Utils");
      Files.createDirectories(fakeSource);
      Path notifier = fakeSource.resolve("NotifierURL.java");
      Path info = fakeSource.resolve("Info.java");
      Files.write(notifier, Arrays.asList(
          "package com.Lomikel.Utils;",
          "import java.util.concurrent.*;",
          "import java.util.concurrent.atomic.*;",
          "public class NotifierURL {",
          "  public static final AtomicInteger calls = new AtomicInteger();",
          "  public static final CountDownLatch entered = new CountDownLatch(1);",
          "  public static final CountDownLatch release = new CountDownLatch(1);",
          "  public static void notify(String message, String source, String version) {",
          "    calls.incrementAndGet(); entered.countDown();",
          "    try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError(\"timeout\"); }",
          "    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }",
          "  }",
          "}"), StandardCharsets.UTF_8);
      Files.write(info, Arrays.asList("package com.Lomikel.Utils;",
          "public class Info { public static String release() { return \"offline\"; } }"),
          StandardCharsets.UTF_8);
      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      check(compiler != null, "JDK compiler required");
      check(compiler.run(null, null, null, "-d", temp.toString(), "-classpath",
          System.getProperty("java.class.path"), "-sourcepath", temp.toString(),
          source.toString(), notifier.toString(), info.toString()) == 0, "isolated compilation failed");

      // Child-first only for these three classes: never resolve the production notifier.
      try (URLClassLoader loader = new URLClassLoader(new URL[]{temp.toUri().toURL()},
                                                     InitConcurrencyTest.class.getClassLoader()) {
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
          synchronized (getClassLoadingLock(name)) {
            if (name.equals(PACKAGE + ".Init") || name.equals(PACKAGE + ".Info") ||
                name.equals(PACKAGE + ".NotifierURL")) {
              Class<?> found = findLoadedClass(name);
              if (found == null) found = findClass(name);
              if (resolve) resolveClass(found);
              return found;
            }
            return super.loadClass(name, resolve);
          }
        }
      }) {
        Class<?> init = loader.loadClass(PACKAGE + ".Init");
        Class<?> fake = loader.loadClass(PACKAGE + ".NotifierURL");
        Method start = init.getMethod("init", String.class, boolean.class, boolean.class);
        AtomicInteger calls = (AtomicInteger) fake.getField("calls").get(null);
        CountDownLatch entered = (CountDownLatch) fake.getField("entered").get(null);
        CountDownLatch release = (CountDownLatch) fake.getField("release").get(null);
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        Runnable run = () -> {
          try { start.invoke(null, "offline", false, true); }
          catch (Throwable e) { error.compareAndSet(null, e); }
        };
        Thread first = new Thread(run, "init-first");
        Thread second = new Thread(run, "init-second");
        first.start();
        try {
          check(entered.await(3, TimeUnit.SECONDS), "first initializer never entered fake notifier");
          second.start();
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
          while (second.isAlive() && second.getState() != Thread.State.BLOCKED &&
                 calls.get() == 1 && System.nanoTime() < deadline) Thread.sleep(1);
          check(second.getState() == Thread.State.BLOCKED && calls.get() == 1,
                "concurrent initializer entered notification: " + calls.get());
        } finally {
          release.countDown();
          first.join(4000);
          second.join(4000);
        }
        check(!first.isAlive() && !second.isAlive(), "initializers did not terminate");
        check(error.get() == null, "initializer threw " + error.get());
        check(calls.get() == 1, "notification ran more than once: " + calls.get());
        check(init.getField("_initialised").getBoolean(null), "initialization flag not set");
        start.invoke(null, "offline", false, true);
        check(calls.get() == 1, "subsequent initialization notified again");
      }
      System.out.println("PASS InitConcurrencyTest (offline)");
    } finally {
      remove(temp);
    }
  }
}
