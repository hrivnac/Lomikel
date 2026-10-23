package com.Lomikel.Utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/** Offline regression tests for Coding's encoding and malformed input behavior. */
public class CodingTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void utf8RoundTrip() {
    String value = "café 雪 😀";
    String encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    check(encoded.equals(Coding.encode(value)), "encode must use UTF-8, not the platform charset");
    check(value.equals(Coding.decode(encoded)), "decode must use UTF-8, not the platform charset");
    check(value.equals(Coding.decode(Coding.encode(value))), "Unicode roundtrip changed");
  }

  private static void malformedSerialization() throws Exception {
    try {
      Coding.deserialize("not*base64");
      throw new AssertionError("Expected IOException for malformed Base64 serialization");
    } catch (IOException expected) {
      check(expected.getCause() instanceof IllegalArgumentException, "Base64 cause lost");
    }
    check(Coding.spltKey("not*base64") == null, "malformed compact key must return null");
    check(Coding.spltKey("!!!!") == null, "malformed compact key must return null");
    check(Coding.spltKey(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3})) == null,
          "non-serialization bytes must return null");
  }

  private static void wrongSerializedType() throws Exception {
    String notKeys = Coding.serialize("not a key array");
    check(Coding.spltKey(notKeys) == null, "non-array compact key must return null");
  }

  private static void validKeys() throws Exception {
    String[] keys = {"alpha", "café 雪", ""};
    check(Arrays.equals(keys, Coding.spltKey(Coding.compactKey(keys))), "valid key array changed");
    check(Arrays.equals(keys, Coding.spltKey(Coding.serialize(keys))), "legacy serialized key array changed");
  }

  public static void main(String[] args) throws Exception {
    utf8RoundTrip();
    malformedSerialization();
    wrongSerializedType();
    validKeys();
    System.out.println("CodingTest passed");
  }
}
