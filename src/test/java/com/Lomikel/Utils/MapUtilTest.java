package com.Lomikel.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Offline regression tests for value ordering. */
public class MapUtilTest {
  private static void checkOrder(Map<String, Integer> actual, String... expected) {
    if (!new ArrayList<>(actual.keySet()).equals(Arrays.asList(expected))) {
      throw new AssertionError("expected " + Arrays.toString(expected) + ", got " + actual);
    }
  }

  public static void main(String[] args) {
    Map<String, Integer> values = new LinkedHashMap<>();
    values.put("high", 3);
    values.put("missing", null);
    values.put("low", 1);
    values.put("alsoMissing", null);
    values.put("alsoLow", 1);
    checkOrder(MapUtil.sortByValue(values), "missing", "alsoMissing", "low", "alsoLow", "high");
    checkOrder(MapUtil.sortByValue(values, false), "missing", "alsoMissing", "low", "alsoLow", "high");
    checkOrder(MapUtil.sortByValue(values, true), "high", "low", "alsoLow", "missing", "alsoMissing");
    checkOrder(values, "high", "missing", "low", "alsoMissing", "alsoLow");

    Map<String, Integer> noNulls = new LinkedHashMap<>();
    noNulls.put("two", 2);
    noNulls.put("one", 1);
    noNulls.put("twoAgain", 2);
    checkOrder(MapUtil.sortByValue(noNulls), "one", "two", "twoAgain");
    checkOrder(MapUtil.sortByValue(noNulls, true), "two", "twoAgain", "one");
    checkOrder(MapUtil.sortByValue(new LinkedHashMap<String, Integer>()));
    System.out.println("PASS MapUtilTest");
  }
}
