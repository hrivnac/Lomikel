package com.JHTools.Utils;

// Java
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** <code>Interval</code> represents interval of {@link String}
  * with usual interval operations.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Interval implements Comparable<Interval> {

  /** Selftest. */
  public static void main(String[] args) {
    String[] testArray = {"abc..xyz", "xyz..def", "abc..uvw", "abcd..uvw", "zzz..zzz"};
    List<Interval> testList = Interval.createList(testArray);
    Collections.sort(testList);
    System.out.println("sorted:");
    for (Interval i : testList) {
      System.out.println("  " + i);
      }
    Interval i1;
    Interval i2;
    Interval i3;
    i1 = new Interval("abc..xyz");
    i2 = new Interval("def..xyz");
    System.out.println("min: " + min(i1, i2)); 
    System.out.println("max: " + max(i1, i2));
    i1 = new Interval("abc..ijk");
    i2 = new Interval("def..xyz");
    i3 = new Interval("uvw..xyz");
    System.out.println("overlaps("       + i1 + "," + i2 + "): " + i1.overlaps(i2));
    System.out.println("overlaps("       + i1 + "," + i3 + "): " + i1.overlaps(i3));
    System.out.println("union("          + i1 + "," + i2 + "): " + i1.union(  i2));
    System.out.println("union("          + i1 + "," + i3 + "): " + i1.union(  i3));
    System.out.println("intersection("   + i1 + "," + i2 + "): " + i1.intersection(  i2));
    System.out.println("intersection("   + i1 + "," + i3 + "): " + i1.intersection(  i3));
    }

  /** Create from two {@link String}s.
    * The interval start will be respresented by the smaller
    * {@link String}, the interval end will be respresented
    * by the bigger {@link String}.
    * @param s1 The first {@link String}.
    * @param s2 The first {@link String}. */
  public Interval(String s1, String s2) {
    if (s1.compareTo(s2) < 0) {
      lo = s1;
      hi = s2;
      }
    else {
      lo = s2;
      hi = s1;
      }
    expandHash();      
    }
    
  /** Create from one {@link String}.
    * If input {@link String} contains <em>..</em> separator,
    * it will be used to extract start and end {@link String}s.
    * Otherwise start and end will be identical.
    * @param s The input {@link String}. */
  public Interval(String s) {
    String l;
    String h;
    int i = s.indexOf("..");
    if (i < 0) {
      l = s;
      h = s;
      }
    else {
      l = s.substring(0    , i       );
      h = s.substring(i + 2, s.length());
      }
    if (l.compareTo(h) < 0) {
      lo = l;
      hi = h;
      }
    else {
      lo = h;
      hi = l;
      }
    expandHash();      
    }
    
  /** Add minimal and maximal value after key, if key contanins hash symbol
    * (which means, that it represents a sequence of keys). */
  private void expandHash() {
    if (lo.substring(lo.length() - 1, lo.length()).equals("#")) {
      lo = lo + "000000000";
      }
    if (hi.substring(hi.length() - 1, hi.length()).equals("#")) {
      hi = hi + Integer.MAX_VALUE;
      }
    }
  
  /** Create {@link List} of intervals.
    * @param s The array of {@link Strings} to be converted.
    * @return  The created {@link List} of intervals. */ 
  public static List<Interval> createList(String[] s) {
    List<Interval> list = new ArrayList<Interval>();
    for (String i : s) {
      list.add(new Interval(i));
      }
    return list;
    }
    
  /** Give the smaller (= first) interval of two.
    * @param i1 The first interval.
    * @param i2 The second interval.
    * @return   The smaller of two intervales. */
  public static Interval min(Interval i1, Interval i2) {
    if (i1.compareTo(i2) >= 0) {
      return i2;
      }
    else {
      return i1;
      }
    }

  /** Give the bigger (= second) interval of two.
    * @param i1 The first interval.
    * @param i2 The second interval.
    * @return   The bigger of two intervales. */
  public static Interval max(Interval i1, Interval i2) {
    if (i1.compareTo(i2) >= 0) {
      return i1;
      }
    else {
      return i2;
      }
    }

  /** Tell whether two intervals overlap (even on the border).
    * @param other The other interval.
    * @return      Whether two intervals overlap. */
  public boolean overlaps(Interval other) {
    if (hi.compareTo(other.lo) <= 0 || lo.compareTo(other.hi) >= 0) {
      return false;
      }
    return true;
    }
    
  /** Give union of two intervals.
    * @param other The other interval.
    * @return      The union interval. <tt>null</tt> if intervals
    *               don't overlap. */  
  public Interval union(Interval other) {
    if (overlaps(other)) {
      return new Interval(min(this, other).lo, max(this, other).hi);
      }
    return null;
    }
    
  /** Give intersection of two intervals.
    * @param other The other interval.
    * @return      The intersection interval. <tt>null</tt> if intervals
    *               don't overlap. */  
  public Interval intersection(Interval other) {
    if (overlaps(other)) {
      return new Interval(min(this, other).hi, max(this, other).lo);
      }
    return null;
    }

  @Override
  public int compareTo(Interval other) {
    if (this.equals(other)) {
      return 0;
      }
    if (lo.equals(other.lo)) {
      return hi.compareTo(other.hi);
      }
    else {
      return lo.compareTo(other.lo);
      }
    }

  @Override
  public boolean equals(Object other) {
    if (other instanceof Interval && lo.equals(((Interval)other).lo) && hi.equals(((Interval)other).hi)) {
      return true;
      }
    return false;
    }
    
  @Override
  public int hashCode() {
    return lo.hashCode() + hi.hashCode();
    }
    
  @Override
  public String toString() {
    return lo + ".." + hi;
    }

  public String lo;
  
  public String hi;

  }
