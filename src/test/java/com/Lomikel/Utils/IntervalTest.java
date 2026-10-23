package com.Lomikel.Utils;

/** Offline regression checks for inclusive String intervals. */
public class IntervalTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void touchingBordersOverlap() {
    Interval left = new Interval("a", "b");
    Interval right = new Interval("b", "c");
    check(left.overlaps(right), "right border should overlap");
    check(right.overlaps(left), "left border should overlap");
    check(left.overlaps(new Interval("b")), "singleton at border should overlap");
    check(new Interval("b").overlaps(new Interval("b")), "identical singletons should overlap");
    check(!left.overlaps(new Interval("c", "d")), "disjoint intervals must not overlap");
  }

  private static void unionUsesIndependentBounds() {
    Interval outer = new Interval("a", "z");
    Interval inner = new Interval("b", "c");
    check(outer.union(inner).equals(outer), "contained union lost upper bound");
    check(inner.union(outer).equals(outer), "contained union depends on operand order");
    check(new Interval("a", "c").union(new Interval("b", "z")).equals(outer),
          "crossing union lost an outer bound");
    check(new Interval("a", "b").union(new Interval("b", "c")).equals(new Interval("a", "c")),
          "touching union should include both intervals");
    check(new Interval("a", "b").union(new Interval("d", "e")) == null,
          "disjoint union must remain null");
  }

  private static void intersectionUsesIndependentBounds() {
    Interval outer = new Interval("a", "z");
    Interval inner = new Interval("b", "c");
    check(outer.intersection(inner).equals(inner), "contained intersection lost upper bound");
    check(inner.intersection(outer).equals(inner), "contained intersection depends on operand order");
    check(new Interval("a", "c").intersection(new Interval("b", "z")).equals(new Interval("b", "c")),
          "crossing intersection has wrong bounds");
    check(new Interval("a", "b").intersection(new Interval("b", "c")).equals(new Interval("b")),
          "touching intersection must be a singleton");
    check(new Interval("a", "b").intersection(new Interval("d", "e")) == null,
          "disjoint intersection must remain null");
  }

  private static void emptyEndpoints() {
    Interval empty = new Interval("");
    check(empty.equals(new Interval("", "")), "empty singleton differs by constructor");
    check(new Interval("..z").equals(new Interval("", "z")), "empty lower endpoint is not parsed");
    check(new Interval("a..").equals(new Interval("", "a")), "empty upper endpoint is not sorted");
    check(new Interval("..").equals(empty), "both empty endpoints are not parsed");
    check(empty.overlaps(new Interval("", "a")), "empty border must overlap inclusively");
    check(empty.intersection(new Interval("", "a")).equals(empty), "empty singleton intersection");
    check(empty.union(new Interval("", "a")).equals(new Interval("", "a")), "empty singleton union");
    check(new Interval("", "#").lo.equals(""), "empty lower endpoint with hash upper bound");
    check(new Interval("#").equals(new Interval("#000000000", "#" + Integer.MAX_VALUE)),
          "hash expansion must remain compatible");
  }

  public static void main(String[] args) {
    switch (args[0]) {
      case "touchingBordersOverlap": touchingBordersOverlap(); break;
      case "unionUsesIndependentBounds": unionUsesIndependentBounds(); break;
      case "intersectionUsesIndependentBounds": intersectionUsesIndependentBounds(); break;
      case "emptyEndpoints": emptyEndpoints(); break;
      default: throw new IllegalArgumentException(args[0]);
    }
    System.out.println("PASS " + args[0]);
  }
}
