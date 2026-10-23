package com.Lomikel.Utils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Offline regression tests for StringFile; run main with assertions enabled or disabled. */
public class StringFileTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void unicodeText() throws Exception {
    Path file = Files.createTempFile("stringfile-unicode-", ".txt");
    try {
      Files.write(file, "café 雪\nnaïve".getBytes(StandardCharsets.UTF_8));
      StringFile text = new StringFile(file.toFile());
      check("café 雪\nnaïve\n".equals(text.content()), "UTF-8 text was corrupted: " + text.content());
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private static void malformedUtf8Replacement() throws Exception {
    Path file = Files.createTempFile("stringfile-invalid-", ".txt");
    try {
      Files.write(file, new byte[]{(byte) 0xff, '\n'});
      check("\ufffd\n".equals(new StringFile(file.toFile()).content()),
            "malformed UTF-8 must decode with a replacement character");
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private static void newlineCompatibility() throws Exception {
    Path file = Files.createTempFile("stringfile-lines-", ".txt");
    String[] sources = {"", "first", "first\n", "first\rsecond\r\nthird\n\nlast"};
    String[] expected = {"", "first\n", "first\n", "first\nsecond\nthird\n\nlast\n"};
    try {
      for (int i = 0; i < sources.length; i++) {
        Files.write(file, sources[i].getBytes(StandardCharsets.UTF_8));
        check(expected[i].equals(new StringFile(file.toFile()).content()),
              "line normalization changed for " + sources[i]);
      }
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private static void missingFile() throws Exception {
    Path file = Files.createTempFile("stringfile-missing-", ".txt");
    Files.delete(file);
    StringFile text = new StringFile(file.toFile());
    check(text.content() == null, "missing file must have null content");
    check("".equals(text.toString()), "missing file must stringify as empty");
  }

  private static void unreadableDirectory() throws Exception {
    Path dir = Files.createTempDirectory("stringfile-unreadable-");
    try {
      try {
        new StringFile(dir.toFile());
        throw new AssertionError("directory must not be read as a file");
      } catch (LomikelException expected) {
        check(expected.getCause() instanceof java.io.IOException, "I/O failure cause lost");
      }
    } finally {
      Files.delete(dir);
    }
  }

  public static void main(String[] args) throws Exception {
    unicodeText();
    malformedUtf8Replacement();
    newlineCompatibility();
    missingFile();
    unreadableDirectory();
    System.out.println("StringFileTest OK");
  }
}
