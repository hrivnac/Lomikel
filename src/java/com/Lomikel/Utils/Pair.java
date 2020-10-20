package com.Lomikel.Utils;

/** <code>Pair</code> represents a pair of values.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Pair<FIRST  extends Comparable<FIRST>,
                  SECOND extends Comparable<SECOND>> implements Comparable<Pair<FIRST, SECOND>> {

 private Pair(FIRST first, SECOND second) {
   _first = first;
   _second = second;
   }

  public static <FIRST  extends Comparable<FIRST>,
                 SECOND extends Comparable<SECOND>> Pair<FIRST, SECOND> of(FIRST first, SECOND second) {
    return new Pair<>(first, second);
    }

  @Override
  public int compareTo(Pair<FIRST, SECOND> o) {
    int cmp = compare(_first, o._first);
    return cmp == 0 ? compare(_second, o._second) : cmp;
    }


  private <T extends Comparable<T>> int compare(T o1, T o2) {
    if (o1 == null) {
      if (o2 == null) {
        return 0;
        }
      else {
        return -1;
        }
      }
    else if (o2 == null) {
      return +1;
      }
    else {
      return o1.compareTo(o2);
      }
    }

  @Override
  public int hashCode() {
    return 31 * hashcode(_first) + hashcode(_second);
    }
  
  private static int hashcode(Object o) {
    return o == null ? 0 : o.hashCode();
    }
  
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Pair)) {
      return false;
      }
    if (this == obj) {
      return true;
      }
    return equal(_first,  ((Pair) obj)._first )
        && equal(_second, ((Pair) obj)._second);
    }
  
  private boolean equal(Object o1, Object o2) {
    return o1 == null ? o2 == null : (o1 == o2 || o1.equals(o2));
    }

  @Override
  public String toString() {
    return "(" + _first + ", " + _second + ')';
    }
         
  public final FIRST  _first;
  public final SECOND _second;

  }