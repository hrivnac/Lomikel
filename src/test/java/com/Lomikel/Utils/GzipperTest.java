package com.Lomikel.Utils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/** Standalone regression tests; run with assertions enabled as well as disabled. */
public class GzipperTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void malformedInputReturnsEmpty() {
    for (byte[] input : new byte[][] {new byte[0], {0x1f, (byte) 0x8b}, {1, 2, 3}}) {
      check(Arrays.equals(new byte[0], Gzipper.unzip(input)), "malformed gzip must return empty bytes");
    }
  }

  private static void shortMagicIsNotZipped() {
    check(!Gzipper.isZipped(new byte[0]), "empty input");
    check(!Gzipper.isZipped(new byte[] {0x1f}), "one-byte magic prefix");
    check(Gzipper.isZipped(new byte[] {(byte) GZIPInputStream.GZIP_MAGIC,
                                      (byte) (GZIPInputStream.GZIP_MAGIC >> 8)}), "gzip magic");
    check(!Gzipper.isZipped(new byte[] {0x1f, 0}), "incorrect second magic byte");
  }

  private static void roundTrip() {
    byte[] large = new byte[5 * 1024 * 1024];
    Arrays.fill(large, (byte) 'x');
    for (byte[] input : new byte[][] {new byte[0], "hello world".getBytes(StandardCharsets.UTF_8), large}) {
      byte[] compressed = Gzipper.zip(input);
      check(Gzipper.isZipped(compressed), "zip must produce gzip");
      check(Arrays.equals(input, Gzipper.unzip(compressed)), "round trip");
      check(Gzipper.unzip(Arrays.copyOf(compressed, compressed.length - 1)).length == 0,
            "truncated gzip must return empty bytes, not partial output");
    }
  }

  public static void main(String[] args) {
    malformedInputReturnsEmpty();
    shortMagicIsNotZipped();
    roundTrip();
    System.out.println("GzipperTest passed");
  }
}
